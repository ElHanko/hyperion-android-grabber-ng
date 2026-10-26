package com.elhanko.hyperiongrabber.ng.common;

import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionThread;
import com.elhanko.hyperiongrabber.ng.common.util.BorderProcessor;
import com.elhanko.hyperiongrabber.ng.common.util.HyperionGrabberOptions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class HyperionScreenEncoderBase {
    static final boolean DEBUG = false;
    private static final String TAG = "ScreenEncoderBase";
    private final int CLEAR_COMMAND_DELAY_MS = 100;
    private final int INIT_ORIENTATION;
    private final int mShutdownTimeoutMs;
    private final Handler mDisconnectHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean mDisconnectStarted = new AtomicBoolean();
    private final CountDownLatch mDisconnectFinished = new CountDownLatch(1);

    final boolean mRemoveBorders = false; // enables detecting borders for standard grabbing - disabled for now
    final boolean mAvgColor;
    final int mWidthScaled;
    final int mFrameRate;
    final int mHeightScaled;
    final int mDensity;
    final AtomicBoolean mStopRequested = new AtomicBoolean();
    volatile boolean mIsCapturing = false;
    int mCurrentOrientation;

    BorderProcessor mBorderProcessor;
    Handler mHandler;
    MediaProjection mMediaProjection;
    HyperionThread.HyperionThreadListener mListener;
    private final Runnable disconnectRunner = () -> new Thread(this::disconnectOnce).start();

    HyperionScreenEncoderBase(final HyperionThread.HyperionThreadListener listener,
                              final MediaProjection projection, int width, int height,
                              final int density, HyperionGrabberOptions options,
                              final int shutdownTimeoutMs) {
        if (DEBUG) Log.d(TAG, "Encoder starting");

        mListener = listener;
        mMediaProjection = projection;
        mDensity = density;
        mFrameRate = options.getFrameRate();
        mAvgColor = options.useAverageColor();
        mShutdownTimeoutMs = shutdownTimeoutMs;

        int blackThreshold = options.getBlackThreshold();
        mBorderProcessor = new BorderProcessor(blackThreshold);

        mCurrentOrientation = INIT_ORIENTATION = width > height ? Configuration.ORIENTATION_LANDSCAPE :
                Configuration.ORIENTATION_PORTRAIT;

        if (DEBUG) {
            Log.d(TAG, "Density: " + String.valueOf(mDensity));
            Log.d(TAG, "Frame Rate: " + String.valueOf(mFrameRate));
            Log.d(TAG, "Original Width: " + String.valueOf(width));
            Log.d(TAG, "Original Height: " + String.valueOf(height));
            Log.d(TAG, "Average Color Only: " + String.valueOf(mAvgColor));
            Log.d(TAG, "Black Pixel Threshold: " + String.valueOf(blackThreshold));
        }

        // find the common divisor for width & height best fit for the LED count (defined in options)
        int divisor = options.findDivisor(width, height);

        // set the scaled width & height based upon the found divisor
        mHeightScaled = (height / divisor);
        mWidthScaled = (width / divisor);

        if (DEBUG) {
            Log.d(TAG, "Common Divisor: " + String.valueOf(divisor));
            Log.d(TAG, "Scaled Width: " + String.valueOf(mWidthScaled));
            Log.d(TAG, "Scaled Height: " + String.valueOf(mHeightScaled));
        }

        final HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new Handler(thread.getLooper());

        if (DEBUG) Log.d(TAG, "Encoder ready");
    }

    private Runnable clearAndDisconnectRunner = new Runnable() {
        public void run() {
            if (DEBUG) Log.d(TAG, "Clearing LEDs and disconnecting");
            try {
                if (!mDisconnectStarted.get()) {
                    mListener.clear();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not clear grabber priority", e);
            } finally {
                disconnectOnce();
            }
        }
    };

    private void disconnectOnce() {
        if (mDisconnectStarted.compareAndSet(false, true)) {
            try {
                mListener.disconnect();
            } catch (RuntimeException e) {
                Log.w(TAG, "Could not disconnect grabber", e);
            } finally {
                mDisconnectHandler.removeCallbacks(disconnectRunner);
                mDisconnectFinished.countDown();
            }
        }
    }

    private void startCleanupWorker(Runnable rejectedCleanup, Runnable onStopped) {
        new Thread(() -> {
            boolean interrupted = false;
            try {
                if (rejectedCleanup == null) {
                    clearAndDisconnectRunner.run();
                } else {
                    disconnectOnce();
                }
                Thread captureThread = mHandler.getLooper().getThread();
                // A rejected post can leave a callback running on the quitting looper.
                for (;;) {
                    try {
                        captureThread.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        Log.w(TAG, "Interrupted while waiting for capture thread to stop", e);
                    }
                }
                if (rejectedCleanup != null) {
                    rejectedCleanup.run();
                }
                for (;;) {
                    try {
                        mDisconnectFinished.await();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        Log.w(TAG, "Interrupted while waiting for disconnect to finish", e);
                    }
                }
                if (onStopped != null) {
                    onStopped.run();
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private Runnable clearLightsRunner = new Runnable() {
        public void run() {
            if (DEBUG) Log.d(TAG, "Clearing LEDs");
            try {
                Thread.sleep(CLEAR_COMMAND_DELAY_MS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!mStopRequested.get()) {
                mListener.clear();
            }
        }
    };

    public void clearLights() {
        if (!mStopRequested.get()) {
            new Thread(clearLightsRunner).start();
        }
    }

    void clearAndDisconnect(Runnable releaseResources, Runnable onStopped) {
        // Closing the socket can unblock a frame before the capture handler drains.
        if (!mDisconnectHandler.postDelayed(disconnectRunner, mShutdownTimeoutMs)) {
            Log.w(TAG, "Disconnect handler rejected timeout; starting disconnect worker");
            disconnectRunner.run();
        }
        // Keep worker startup and resource release in one accepted capture task.
        if (!mHandler.post(() -> {
            startCleanupWorker(null, onStopped);
            releaseResources.run();
        })) {
            Log.w(TAG, "Capture handler rejected cleanup; waiting on cleanup worker");
            startCleanupWorker(releaseResources, onStopped);
        }
    }

    public boolean isCapturing() {
        return !mStopRequested.get() && mIsCapturing;
    }

    public void setCapturing(boolean isCapturing) {
        mIsCapturing = isCapturing && !mStopRequested.get();
    }

    public void sendStatus() {
        if (mListener != null) {
            mListener.sendStatus(isCapturing());
        }
    }

    public void stopRecording() {
        throw new RuntimeException("Stub!");
    }

    public void resumeRecording() {
        throw new RuntimeException("Stub!");
    }

    int getGrabberWidth() {
        return INIT_ORIENTATION != mCurrentOrientation ? mHeightScaled : mWidthScaled;
    }

    int getGrabberHeight() {
        return INIT_ORIENTATION != mCurrentOrientation ? mWidthScaled : mHeightScaled;
    }

    public void setOrientation(int orientation) {
        mCurrentOrientation = orientation;
    }
}
