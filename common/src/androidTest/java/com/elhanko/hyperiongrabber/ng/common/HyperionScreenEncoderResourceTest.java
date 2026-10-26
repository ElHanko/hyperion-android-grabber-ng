package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionThread;
import com.elhanko.hyperiongrabber.ng.common.util.HyperionGrabberOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses native image buffers without requesting a MediaProjection or starting a service. */
public class HyperionScreenEncoderResourceTest {
    private final RecordingListener listener = new RecordingListener();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private HyperionScreenEncoder encoder;
    private ImageReader reader;
    private ImageReader.OnImageAvailableListener callback;

    @Before
    public void setUp() throws Exception {
        encoder = new HyperionScreenEncoder(listener, null, 16, 16, 160,
                new HyperionGrabberOptions(16, 16, 30, false), 10_000, () -> { });
        reader = ImageReader.newInstance(16, 16, PixelFormat.RGBA_8888, 1);
        Field field = HyperionScreenEncoder.class.getDeclaredField("imageAvailableListener");
        field.setAccessible(true);
        callback = (ImageReader.OnImageAvailableListener) field.get(encoder);
        setFrameInterval(Double.NEGATIVE_INFINITY);
        encoder.setCapturing(true);
    }

    @After
    public void tearDown() throws Exception {
        try {
            if (encoder != null) {
                stopEncoder();
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @Test
    public void processedFrameReleasesTheAcquiredImage() {
        queueFrame();
        callback.onImageAvailable(reader);

        assertEquals(1, listener.frames.get());
        assertEquals(16, listener.width);
        assertEquals(16, listener.height);
        assertEquals(16 * 16 * 3, listener.pixels.length);
        assertArrayEquals(new byte[] {17, 34, 51, 17, 34, 51},
                Arrays.copyOf(listener.pixels, 6));
        assertNoAcquiredOrQueuedImage();
    }

    @Test
    public void processingExceptionStillReleasesTheAcquiredImage() {
        listener.failFrame = true;
        queueFrame();
        callback.onImageAvailable(reader);

        assertEquals("The injected failure must actually be reached", 1, listener.frames.get());
        assertNoAcquiredOrQueuedImage();
    }

    @Test
    public void rateLimitedFrameIsReleasedWithoutProcessing() throws Exception {
        setFrameInterval(Double.POSITIVE_INFINITY);
        queueFrame();
        callback.onImageAvailable(reader);

        assertEquals(0, listener.frames.get());
        assertNoAcquiredOrQueuedImage();
    }

    @Test
    public void emptyCallbacksLeaveTheReaderUsableForTheNextFrame() {
        callback.onImageAvailable(reader);
        callback.onImageAvailable(reader);
        assertEquals(0, listener.frames.get());
        assertNoAcquiredOrQueuedImage();

        queueFrame();
        callback.onImageAvailable(reader);
        assertEquals(1, listener.frames.get());
        assertNoAcquiredOrQueuedImage();
    }

    @Test
    public void callbackAfterStopDoesNotAcquireItsQueuedImage() throws Exception {
        queueFrame();
        stopEncoder();
        callback.onImageAvailable(reader);

        assertFalse(encoder.isCapturing());
        assertEquals(0, listener.frames.get());
        try (Image untouched = reader.acquireNextImage()) {
            assertNotNull("A stopped callback must leave the queued image untouched", untouched);
        }
    }

    @Test
    public void stopDrainsAnInFlightFrameBeforeClosingItsReader() throws Exception {
        Field field = HyperionScreenEncoder.class.getDeclaredField("mImageReader");
        field.setAccessible(true);
        field.set(encoder, reader);
        listener.frameEntered = new CountDownLatch(1);
        listener.allowFrameReturn = new CountDownLatch(1);
        queueFrame();
        try {
            assertTrue(encoder.mHandler.post(() -> callback.onImageAvailable(reader)));
            assertTrue("Frame processing did not start",
                    listener.frameEntered.await(5, TimeUnit.SECONDS));
            encoder.stopRecording(null, stopped::countDown);

            assertTrue("The reader must remain usable while its frame is in flight",
                    reader.getSurface().isValid());
        } finally {
            listener.allowFrameReturn.countDown();
        }
        assertTrue("Capture cleanup did not finish", stopped.await(5, TimeUnit.SECONDS));
        assertEquals(1, listener.frames.get());
        assertThrows(IllegalStateException.class, reader::acquireNextImage);
    }

    @Test
    public void repeatedStopReleasesOwnedReaderAndCleanupOnlyOnce() throws Exception {
        Field field = HyperionScreenEncoder.class.getDeclaredField("mImageReader");
        field.setAccessible(true);
        field.set(encoder, reader);
        AtomicInteger releases = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        Runnable completion = () -> {
            completions.incrementAndGet();
            stopped.countDown();
        };

        encoder.stopRecording(releases::incrementAndGet, completion);
        encoder.stopRecording(releases::incrementAndGet, completion);
        assertTrue("Capture cleanup did not finish", stopped.await(5, TimeUnit.SECONDS));
        Thread handlerThread = encoder.mHandler.getLooper().getThread();
        handlerThread.join(5_000);
        encoder.stopRecording(releases::incrementAndGet, completion);

        assertFalse(encoder.isCapturing());
        assertFalse("Handler thread did not terminate", handlerThread.isAlive());
        assertEquals(1, releases.get());
        assertEquals(1, completions.get());
        assertEquals(1, listener.clears.get());
        assertEquals(1, listener.disconnects.get());
        assertThrows(IllegalStateException.class, reader::acquireNextImage);
    }

    private void queueFrame() {
        Canvas canvas = reader.getSurface().lockCanvas(null);
        try {
            canvas.drawColor(Color.rgb(17, 34, 51));
        } finally {
            reader.getSurface().unlockCanvasAndPost(canvas);
        }
    }

    private void assertNoAcquiredOrQueuedImage() {
        // With maxImages=1, an unclosed image makes this acquire throw, even for an empty queue.
        try (Image unexpected = reader.acquireNextImage()) {
            assertNull("The callback must consume and release its frame", unexpected);
        }
    }

    private void setFrameInterval(double interval) throws Exception {
        // Select processing/discard deterministically, without sleeping for a frame interval.
        Field field = callback.getClass().getDeclaredField("min_nano_time");
        field.setAccessible(true);
        field.setDouble(callback, interval);
    }

    private void stopEncoder() throws InterruptedException {
        encoder.stopRecording(null, stopped::countDown);
        assertTrue("Capture cleanup did not finish", stopped.await(5, TimeUnit.SECONDS));
    }

    private static final class RecordingListener implements HyperionThread.HyperionThreadListener {
        final AtomicInteger frames = new AtomicInteger();
        final AtomicInteger clears = new AtomicInteger();
        final AtomicInteger disconnects = new AtomicInteger();
        boolean failFrame;
        CountDownLatch frameEntered;
        CountDownLatch allowFrameReturn;
        byte[] pixels;
        int width;
        int height;

        @Override
        public void sendFrame(byte[] data, int width, int height) {
            frames.incrementAndGet();
            if (frameEntered != null) {
                frameEntered.countDown();
                try {
                    allowFrameReturn.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
            if (failFrame) {
                throw new IllegalStateException("Injected frame-processing failure");
            }
            pixels = data.clone();
            this.width = width;
            this.height = height;
        }

        @Override
        public void clear() {
            clears.incrementAndGet();
        }

        @Override
        public void disconnect() {
            disconnects.incrementAndGet();
        }

        @Override
        public void sendStatus(boolean isGrabbing) {
            // No service or network is involved in these native resource tests.
        }
    }
}
