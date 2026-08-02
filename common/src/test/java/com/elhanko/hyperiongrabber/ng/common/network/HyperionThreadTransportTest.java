package com.elhanko.hyperiongrabber.ng.common.network;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.HyperionScreenService;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransport;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportConfig;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportType;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class HyperionThreadTransportTest {
    private static final HyperionTransportConfig CONFIG = new HyperionTransportConfig(
            "hyperion.test", 19_400, 155, "Hyperion Android Grabber NG", 1_000, 2_000);

    @Test
    public void initialStartCreatesExactlySelectedTransportWithUnchangedConfiguration()
            throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("FlatBuffer (experimental)");
        RecordingCreator creator = new RecordingCreator(transport);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, false, 0, creator);
        try {
            thread.start();
            assertEquals("FlatBuffer (experimental)", broadcaster.awaitConnected());
            assertEquals(1, creator.calls.get());
            assertSame(HyperionTransportType.FLATBUFFER, creator.types.get(0));
            assertSame(CONFIG, creator.configs.get(0));
            assertEquals(0, broadcaster.errors.size());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void protobufSelectionCreatesOnlyProtobuf() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        RecordingCreator creator = new RecordingCreator(new FakeTransport("Protocol Buffers"));
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, false, 0, creator);
        try {
            thread.start();
            assertEquals("Protocol Buffers", broadcaster.awaitConnected());
            assertEquals(Collections.singletonList(HyperionTransportType.PROTOBUF), creator.types);
        } finally {
            stop(thread);
        }
    }

    @Test
    public void sendFrameDelegatesUnchangedImageAndDuration() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("Protocol Buffers");
        HyperionThread thread = thread(
                broadcaster,
                HyperionTransportType.PROTOBUF,
                false,
                0,
                new RecordingCreator(transport));
        try {
            thread.start();
            broadcaster.awaitConnected();
            byte[] pixels = {1, 2, 3, 4, 5, 6};
            thread.getReceiver().sendFrame(pixels, 2, 1);

            assertEquals(1, transport.imageCalls.get());
            assertArrayEquals(pixels, transport.lastImage);
            assertEquals(2, transport.lastWidth);
            assertEquals(1, transport.lastHeight);
            assertEquals(-1, transport.lastDuration);
        } finally {
            stop(thread);
        }
    }

    @Test
    public void clearDelegatesOnlyToActiveTransport() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("Protocol Buffers");
        HyperionThread thread = thread(
                broadcaster,
                HyperionTransportType.PROTOBUF,
                false,
                0,
                new RecordingCreator(transport));
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().clear();
            assertEquals(1, transport.clearCalls.get());
            assertEquals(0, transport.imageCalls.get());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void disconnectClosesTransportAndIsIdempotent() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("Protocol Buffers");
        HyperionThread thread = thread(
                broadcaster,
                HyperionTransportType.PROTOBUF,
                true,
                0,
                new RecordingCreator(transport));
        thread.start();
        broadcaster.awaitConnected();

        thread.getReceiver().disconnect();
        thread.getReceiver().disconnect();
        thread.join(2_000);

        assertFalse(thread.isAlive());
        assertEquals(1, transport.closeCalls.get());
        assertFalse(transport.isConnected());
    }

    @Test
    public void initialFailureReportsOriginalCategoryAndNeverTriesAnotherTransport()
            throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        HyperionTimeoutException timeout = new HyperionTimeoutException(
                "initial timeout", new java.net.SocketTimeoutException());
        RecordingCreator creator = new RecordingCreator(timeout);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, true, 0, creator);

        thread.start();
        ErrorEvent event = broadcaster.awaitError();
        thread.join(2_000);

        assertFalse(thread.isAlive());
        assertSame(timeout, event.failure);
        assertEquals("FlatBuffer (experimental)", event.transportName);
        assertEquals(1, creator.calls.get());
        assertEquals(Collections.singletonList(HyperionTransportType.FLATBUFFER), creator.types);
        assertEquals(0, broadcaster.connected.size());
    }

    @Test
    public void connectedFailureReconnectsOnceWithSameTypeAndConfiguration()
            throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("FlatBuffer (experimental)");
        failed.imageFailure = new HyperionProtocolException("broken frame");
        FakeTransport replacement = new FakeTransport("FlatBuffer (experimental)");
        RecordingCreator creator = new RecordingCreator(failed, replacement) {
            @Override
            public HyperionTransport create(
                    HyperionTransportType type, HyperionTransportConfig config)
                    throws IOException {
                if (calls.get() == 1) {
                    assertEquals("Failed transport must close before replacement", 1,
                            failed.closeCalls.get());
                }
                return super.create(type, config);
            }
        };
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, true, 0, creator);
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1);

            ErrorEvent error = broadcaster.awaitError();
            assertTrue(error.failure instanceof HyperionProtocolException);
            assertEquals("FlatBuffer (experimental)", broadcaster.awaitConnected());
            assertEquals(2, creator.calls.get());
            assertEquals(HyperionTransportType.FLATBUFFER, creator.types.get(1));
            assertSame(CONFIG, creator.configs.get(1));
            assertEquals(1, failed.closeCalls.get());
            assertEquals(0, replacement.imageCalls.get());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void simultaneousFailuresScheduleOnlyOneReconnect() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("Protocol Buffers");
        failed.imageFailure = new IOException("simultaneous failure");
        failed.imageBarrier = new CountDownLatch(2);
        FakeTransport replacement = new FakeTransport("Protocol Buffers");
        RecordingCreator creator = new RecordingCreator(failed, replacement);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, true, 0, creator);
        try {
            thread.start();
            broadcaster.awaitConnected();
            Thread first = frameCaller(thread);
            Thread second = frameCaller(thread);
            first.start();
            second.start();
            first.join(2_000);
            second.join(2_000);
            broadcaster.awaitError();
            broadcaster.awaitConnected();

            assertEquals(2, creator.calls.get());
            assertEquals(1, failed.closeCalls.get());
            assertEquals(1, broadcaster.errors.size());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void framesDuringReconnectAreDroppedWithoutBuffering() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("Protocol Buffers");
        failed.imageFailure = new IOException("lost");
        FakeTransport replacement = new FakeTransport("Protocol Buffers");
        BlockingReconnectCreator creator = new BlockingReconnectCreator(failed, replacement);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, true, 0, creator);
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1);
            broadcaster.awaitError();
            assertTrue(creator.reconnectEntered.await(2, TimeUnit.SECONDS));

            for (int index = 0; index < 100; index++) {
                thread.getReceiver().sendFrame(new byte[] {4, 5, 6}, 1, 1);
            }
            assertEquals(1, failed.imageCalls.get());
            assertEquals(0, replacement.imageCalls.get());

            creator.allowReconnect.countDown();
            broadcaster.awaitConnected();
            assertEquals(0, replacement.imageCalls.get());
            thread.getReceiver().sendFrame(new byte[] {7, 8, 9}, 1, 1);
            assertEquals(1, replacement.imageCalls.get());
        } finally {
            creator.allowReconnect.countDown();
            stop(thread);
        }
    }

    @Test
    public void disabledReconnectCreatesNoReplacement() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("Protocol Buffers");
        failed.imageFailure = new IOException("lost");
        RecordingCreator creator = new RecordingCreator(failed);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, false, 0, creator);

        thread.start();
        broadcaster.awaitConnected();
        thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1);
        broadcaster.awaitError();
        thread.join(2_000);

        assertFalse(thread.isAlive());
        assertEquals(1, creator.calls.get());
        assertEquals(1, failed.closeCalls.get());
    }

    @Test
    public void intentionalDisconnectNeverReconnects() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("FlatBuffer (experimental)");
        RecordingCreator creator = new RecordingCreator(transport);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, true, 0, creator);

        thread.start();
        broadcaster.awaitConnected();
        thread.getReceiver().disconnect();
        thread.join(2_000);

        assertEquals(1, creator.calls.get());
        assertEquals(0, broadcaster.errors.size());
        assertEquals(1, transport.closeCalls.get());
    }

    @Test
    public void preventReconnectAllowsClearBeforeIdempotentDisconnect() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("Protocol Buffers");
        RecordingCreator creator = new RecordingCreator(transport);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, true, 60_000, creator);

        thread.start();
        broadcaster.awaitConnected();
        thread.preventReconnect();
        thread.join(2_000);
        assertFalse(thread.isAlive());
        assertEquals(0, transport.closeCalls.get());

        thread.getReceiver().clear();
        thread.getReceiver().disconnect();
        thread.getReceiver().disconnect();
        assertEquals(1, transport.clearCalls.get());
        assertEquals(1, transport.closeCalls.get());
        assertEquals(1, creator.calls.get());
    }

    @Test
    public void stopWakesLongReconnectDelayAndPreventsCreation() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("Protocol Buffers");
        failed.imageFailure = new IOException("lost");
        RecordingCreator creator = new RecordingCreator(failed, new FakeTransport("unused"));
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, true, 60_000, creator);

        thread.start();
        broadcaster.awaitConnected();
        thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1);
        broadcaster.awaitError();
        thread.shutdown();
        thread.join(2_000);

        assertFalse(thread.isAlive());
        assertEquals(1, creator.calls.get());
    }

    @Test
    public void lateFactorySuccessAfterStopIsClosedAndNeverPublished() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport late = new FakeTransport("FlatBuffer (experimental)");
        BlockingInitialCreator creator = new BlockingInitialCreator(late);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, true, 0, creator);

        thread.start();
        assertTrue(creator.entered.await(2, TimeUnit.SECONDS));
        thread.shutdown();
        creator.allowReturn.countDown();
        thread.join(2_000);

        assertFalse(thread.isAlive());
        assertEquals(1, late.closeCalls.get());
        assertEquals(0, broadcaster.connected.size());
        assertEquals(0, broadcaster.errors.size());
    }

    @Test
    public void clearFailureUsesSameReconnectPolicyAndClosesFailedTransport()
            throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport failed = new FakeTransport("Protocol Buffers");
        failed.clearFailure = new HyperionServerException("clear rejected");
        FakeTransport replacement = new FakeTransport("Protocol Buffers");
        RecordingCreator creator = new RecordingCreator(failed, replacement);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.PROTOBUF, true, 0, creator);
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().clear();
            ErrorEvent error = broadcaster.awaitError();
            assertTrue(error.failure instanceof HyperionServerException);
            broadcaster.awaitConnected();
            assertEquals(2, creator.calls.get());
            assertEquals(1, failed.closeCalls.get());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void flatBufferClearReadinessIsHandledOnlyByTransport() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("FlatBuffer (experimental)");
        transport.clearMarksNotReady = true;
        RecordingCreator creator = new RecordingCreator(transport);
        HyperionThread thread = thread(
                broadcaster, HyperionTransportType.FLATBUFFER, true, 0, creator);
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().clear();
            assertFalse(transport.isConnected());
            thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1);
            assertEquals(1, transport.imageCalls.get());
            assertEquals(1, creator.calls.get());
            assertTrue(transport.isConnected());
        } finally {
            stop(thread);
        }
    }

    @Test
    public void statusDelegatesWithoutTransportSideEffects() throws Exception {
        FakeBroadcaster broadcaster = new FakeBroadcaster();
        FakeTransport transport = new FakeTransport("Protocol Buffers");
        HyperionThread thread = thread(
                broadcaster,
                HyperionTransportType.PROTOBUF,
                false,
                0,
                new RecordingCreator(transport));
        try {
            thread.start();
            broadcaster.awaitConnected();
            thread.getReceiver().sendStatus(true);
            assertTrue(broadcaster.lastStatus);
            assertEquals(0, transport.imageCalls.get());
            assertEquals(0, transport.clearCalls.get());
        } finally {
            stop(thread);
        }
    }

    private static HyperionThread thread(
            FakeBroadcaster broadcaster,
            HyperionTransportType type,
            boolean reconnect,
            long reconnectDelayMs,
            HyperionThread.TransportCreator creator) {
        return new HyperionThread(
                broadcaster, type, CONFIG, reconnect, reconnectDelayMs, creator);
    }

    private static Thread frameCaller(HyperionThread thread) {
        return new Thread(() -> thread.getReceiver().sendFrame(new byte[] {1, 2, 3}, 1, 1));
    }

    private static void stop(HyperionThread thread) throws InterruptedException {
        thread.shutdown();
        thread.join(2_000);
        assertFalse("HyperionThread did not stop", thread.isAlive());
    }

    private static final class FakeBroadcaster
            implements HyperionScreenService.HyperionThreadBroadcaster {
        final BlockingQueue<String> connectedQueue = new LinkedBlockingQueue<>();
        final BlockingQueue<ErrorEvent> errorQueue = new LinkedBlockingQueue<>();
        final List<String> connected = Collections.synchronizedList(new ArrayList<>());
        final List<ErrorEvent> errors = Collections.synchronizedList(new ArrayList<>());
        volatile boolean lastStatus;

        @Override
        public void onConnected(String transportName) {
            connected.add(transportName);
            connectedQueue.add(transportName);
        }

        @Override
        public void onConnectionError(String transportName, IOException error) {
            ErrorEvent event = new ErrorEvent(transportName, error);
            errors.add(event);
            errorQueue.add(event);
        }

        @Override
        public void onReceiveStatus(boolean isCapturing) {
            lastStatus = isCapturing;
        }

        String awaitConnected() throws InterruptedException {
            String result = connectedQueue.poll(2, TimeUnit.SECONDS);
            if (result == null) {
                throw new AssertionError("No connected callback received");
            }
            return result;
        }

        ErrorEvent awaitError() throws InterruptedException {
            ErrorEvent result = errorQueue.poll(2, TimeUnit.SECONDS);
            if (result == null) {
                throw new AssertionError("No connection-error callback received");
            }
            return result;
        }
    }

    private static final class ErrorEvent {
        final String transportName;
        final IOException failure;

        ErrorEvent(String transportName, IOException failure) {
            this.transportName = transportName;
            this.failure = failure;
        }
    }

    private static class RecordingCreator implements HyperionThread.TransportCreator {
        final AtomicInteger calls = new AtomicInteger();
        final List<HyperionTransportType> types =
                Collections.synchronizedList(new ArrayList<>());
        final List<HyperionTransportConfig> configs =
                Collections.synchronizedList(new ArrayList<>());
        private final Object[] outcomes;

        RecordingCreator(Object... outcomes) {
            this.outcomes = outcomes;
        }

        @Override
        public HyperionTransport create(
                HyperionTransportType type, HyperionTransportConfig config) throws IOException {
            int index = calls.getAndIncrement();
            types.add(type);
            configs.add(config);
            if (index >= outcomes.length) {
                throw new AssertionError("Unexpected transport creation " + (index + 1));
            }
            Object outcome = outcomes[index];
            if (outcome instanceof IOException) {
                throw (IOException) outcome;
            }
            return (HyperionTransport) outcome;
        }
    }

    private static final class BlockingReconnectCreator extends RecordingCreator {
        final CountDownLatch reconnectEntered = new CountDownLatch(1);
        final CountDownLatch allowReconnect = new CountDownLatch(1);

        BlockingReconnectCreator(HyperionTransport initial, HyperionTransport replacement) {
            super(initial, replacement);
        }

        @Override
        public HyperionTransport create(
                HyperionTransportType type, HyperionTransportConfig config) throws IOException {
            if (calls.get() == 1) {
                reconnectEntered.countDown();
                try {
                    if (!allowReconnect.await(2, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to release fake reconnect");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            return super.create(type, config);
        }
    }

    private static final class BlockingInitialCreator implements HyperionThread.TransportCreator {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch allowReturn = new CountDownLatch(1);
        final FakeTransport transport;

        BlockingInitialCreator(FakeTransport transport) {
            this.transport = transport;
        }

        @Override
        public HyperionTransport create(
                HyperionTransportType type, HyperionTransportConfig config) throws IOException {
            entered.countDown();
            try {
                if (!allowReturn.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to return fake transport");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            return transport;
        }
    }

    private static final class FakeTransport implements HyperionTransport {
        final String name;
        final AtomicInteger imageCalls = new AtomicInteger();
        final AtomicInteger clearCalls = new AtomicInteger();
        final AtomicInteger closeCalls = new AtomicInteger();
        volatile boolean connected = true;
        volatile boolean clearMarksNotReady;
        volatile IOException imageFailure;
        volatile IOException clearFailure;
        volatile CountDownLatch imageBarrier;
        volatile byte[] lastImage;
        volatile int lastWidth;
        volatile int lastHeight;
        volatile int lastDuration;

        FakeTransport(String name) {
            this.name = name;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void setColor(int rgbColor, int durationMs) {
            // Not used by the screen-grabber lifecycle.
        }

        @Override
        public void setImage(byte[] data, int width, int height, int durationMs)
                throws IOException {
            imageCalls.incrementAndGet();
            CountDownLatch barrier = imageBarrier;
            if (barrier != null) {
                barrier.countDown();
                try {
                    if (!barrier.await(2, TimeUnit.SECONDS)) {
                        throw new IOException("Fake image barrier timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }
            if (imageFailure != null) {
                throw imageFailure;
            }
            lastImage = data.clone();
            lastWidth = width;
            lastHeight = height;
            lastDuration = durationMs;
            connected = true;
        }

        @Override
        public void clear() throws IOException {
            clearCalls.incrementAndGet();
            if (clearFailure != null) {
                throw clearFailure;
            }
            if (clearMarksNotReady) {
                connected = false;
            }
        }

        @Override
        public String transportName() {
            return name;
        }

        @Override
        public void close() {
            if (connected || closeCalls.get() == 0) {
                closeCalls.incrementAndGet();
            }
            connected = false;
        }
    }
}
