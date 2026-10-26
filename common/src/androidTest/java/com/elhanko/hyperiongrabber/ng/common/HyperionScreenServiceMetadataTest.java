package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.platform.app.InstrumentationRegistry;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionThread;
import com.elhanko.hyperiongrabber.ng.common.util.HyperionGrabberOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Tests real metadata methods and broadcasts, not framework service startup or stopSelfResult. */
public class HyperionScreenServiceMetadataTest {
    private final BlockingQueue<Intent> statuses = new LinkedBlockingQueue<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            statuses.add(new Intent(intent));
        }
    };
    private Context context;
    private LocalBroadcastManager broadcasts;
    private HyperionScreenService service;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        service = onMain(() -> new MetadataService(context));
        broadcasts = LocalBroadcastManager.getInstance(context);
        broadcasts.registerReceiver(receiver,
                new IntentFilter(HyperionScreenService.BROADCAST_FILTER));
    }

    @After
    public void tearDown() {
        broadcasts.unregisterReceiver(receiver);
    }

    @Test
    public void repeatedStatusBroadcastsPreserveTheErrorEventId() throws Exception {
        setError(service, "Capture initialization failed");
        Intent first = status(service);
        Intent repeated = status(service);

        assertEquals("Capture initialization failed",
                repeated.getStringExtra(HyperionScreenService.BROADCAST_ERROR));
        assertEquals(errorId(first), errorId(repeated));
    }

    @Test
    public void assigningTheSameErrorDoesNotCreateAnotherEvent() throws Exception {
        setError(service, "Network unavailable");
        String first = errorId(status(service));
        setError(service, "Network unavailable");

        assertEquals(first, errorId(status(service)));
    }

    @Test
    public void errorEndClearsBothFieldsAndAllowsTheSameMessageToRecur() throws Exception {
        setError(service, "Network unavailable");
        String first = errorId(status(service));
        setError(service, null);
        Intent recovered = status(service);
        assertNull(recovered.getStringExtra(HyperionScreenService.BROADCAST_ERROR));
        assertNull(recovered.getStringExtra(HyperionScreenService.BROADCAST_ERROR_ID));

        setError(service, "Network unavailable");
        assertNotEquals(first, errorId(status(service)));
    }

    @Test
    public void differentErrorReplacesThePreviousIdentity() throws Exception {
        setError(service, "Network unavailable");
        String first = errorId(status(service));
        setError(service, "Capture initialization failed");
        Intent replacement = status(service);

        assertEquals("Capture initialization failed",
                replacement.getStringExtra(HyperionScreenService.BROADCAST_ERROR));
        assertNotEquals(first, errorId(replacement));
    }

    @Test
    public void newServiceInstanceCreatesANewEventForTheSameMessage() throws Exception {
        setError(service, "Capture initialization failed");
        String first = errorId(status(service));
        HyperionScreenService replacement = onMain(() -> new MetadataService(context));
        setError(replacement, "Capture initialization failed");

        assertNotEquals(first, errorId(status(replacement)));
    }

    @Test
    public void startCompletionMetadataDoesNotReplaceTheErrorIdentity() throws Exception {
        setError(service, "Capture initialization failed");
        String first = errorId(status(service));
        Field completion = HyperionScreenService.class.getDeclaredField("mCompletedStartRequestId");
        completion.setAccessible(true);
        onMain(() -> {
            completion.set(service, "request-b");
            return null;
        });
        Intent result = status(service);

        assertEquals("request-b", result.getStringExtra(
                HyperionScreenService.BROADCAST_COMPLETED_START_REQUEST_ID));
        assertEquals(first, errorId(result));
    }

    @Test
    public void sessionStatusSurvivesMissingTransportAndPauseButEndsOnStop() throws Exception {
        Field owner = HyperionScreenService.class.getDeclaredField("sSessionOwner");
        Field encoderField = HyperionScreenService.class.getDeclaredField("mHyperionEncoder");
        Field stopField = HyperionScreenService.class.getDeclaredField("mStopRequested");
        owner.setAccessible(true);
        encoderField.setAccessible(true);
        stopField.setAccessible(true);
        Object previousOwner = onMain(() -> owner.get(null));
        CountDownLatch stopped = new CountDownLatch(1);
        HyperionScreenEncoder encoder = onMain(() -> new HyperionScreenEncoder(new NoNetworkListener(),
                null, 16, 16, 160, new HyperionGrabberOptions(16, 16, 30, false),
                10_000, () -> { }));
        try {
            // Install states for the real status predicate, without simulating service startup.
            onMain(() -> {
                owner.set(null, service);
                encoderField.set(service, encoder);
                encoder.setCapturing(true);
                return null;
            });
            Intent disconnected = status(service);
            assertTrue(disconnected.getBooleanExtra(
                    HyperionScreenService.BROADCAST_SESSION_ACTIVE, false));
            assertFalse(disconnected.getBooleanExtra(HyperionScreenService.BROADCAST_TAG, true));

            onMain(() -> {
                encoder.setCapturing(false);
                return null;
            });
            Intent paused = status(service);
            assertTrue(paused.getBooleanExtra(
                    HyperionScreenService.BROADCAST_SESSION_ACTIVE, false));
            assertFalse(paused.getBooleanExtra(HyperionScreenService.BROADCAST_TAG, true));

            onMain(() -> {
                ((AtomicBoolean) stopField.get(service)).set(true);
                return null;
            });
            assertFalse(status(service).getBooleanExtra(
                    HyperionScreenService.BROADCAST_SESSION_ACTIVE, true));
        } finally {
            try {
                encoder.stopRecording(null, stopped::countDown);
                assertTrue("Capture cleanup did not finish", stopped.await(5, TimeUnit.SECONDS));
            } finally {
                onMain(() -> {
                    owner.set(null, previousOwner);
                    encoderField.set(service, null);
                    return null;
                });
            }
        }
    }

    @Test
    public void untaggedStartDoesNotRequireADeadline() throws Exception {
        assertFalse(expired(new Intent().putExtra(
                HyperionScreenService.EXTRA_START_REQUEST_DEADLINE, 0L)));
    }

    @Test
    public void taggedStartRejectsMissingZeroAndNegativeDeadlines() throws Exception {
        Intent request = new Intent().putExtra(
                HyperionScreenService.EXTRA_START_REQUEST_ID, "request-a");
        assertTrue(expired(request));
        request.putExtra(HyperionScreenService.EXTRA_START_REQUEST_DEADLINE, 0L);
        assertTrue(expired(request));
        request.putExtra(HyperionScreenService.EXTRA_START_REQUEST_DEADLINE, -1L);
        assertTrue(expired(request));
    }

    @Test
    public void taggedStartAcceptsAFutureDeadline() throws Exception {
        Intent request = new Intent()
                .putExtra(HyperionScreenService.EXTRA_START_REQUEST_ID, "request-a")
                .putExtra(HyperionScreenService.EXTRA_START_REQUEST_DEADLINE, Long.MAX_VALUE);
        assertFalse(expired(request));
    }

    private static void setError(HyperionScreenService target, String error) throws Exception {
        Method setter = HyperionScreenService.class.getDeclaredMethod("setStartError", String.class);
        setter.setAccessible(true);
        onMain(() -> {
            setter.invoke(target, (Object) error);
            return null;
        });
    }

    private Intent status(HyperionScreenService target) throws Exception {
        Method notify = HyperionScreenService.class.getDeclaredMethod("notifyActivity");
        notify.setAccessible(true);
        onMain(() -> {
            notify.invoke(target);
            return null;
        });
        Intent result = statuses.poll(2, TimeUnit.SECONDS);
        assertNotNull("No status broadcast received", result);
        return result;
    }

    private static String errorId(Intent status) {
        String id = status.getStringExtra(HyperionScreenService.BROADCAST_ERROR_ID);
        assertNotNull("A failure must have an event ID", id);
        assertFalse(id.isEmpty());
        return id;
    }

    private boolean expired(Intent intent) throws Exception {
        Method check = HyperionScreenService.class.getDeclaredMethod(
                "isStartRequestExpired", Intent.class);
        check.setAccessible(true);
        return onMain(() -> (Boolean) check.invoke(service, intent));
    }

    private static <T> T onMain(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(task);
        return task.get();
    }

    private static final class MetadataService extends HyperionScreenService {
        private final Context context;

        MetadataService(Context context) {
            this.context = context;
        }

        @Override
        public Context getApplicationContext() {
            return context.getApplicationContext();
        }
    }

    private static final class NoNetworkListener implements HyperionThread.HyperionThreadListener {
        @Override
        public void sendFrame(byte[] data, int width, int height) { }

        @Override
        public void clear() { }

        @Override
        public void disconnect() { }

        @Override
        public void sendStatus(boolean isGrabbing) { }
    }
}
