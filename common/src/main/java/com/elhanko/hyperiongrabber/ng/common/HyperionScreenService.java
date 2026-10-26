package com.elhanko.hyperiongrabber.ng.common;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionThread;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionConnectionSelection;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportType;
import com.elhanko.hyperiongrabber.ng.common.util.HyperionGrabberOptions;
import com.elhanko.hyperiongrabber.ng.common.util.Preferences;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class HyperionScreenService extends Service {
    public static final String BROADCAST_ERROR = "SERVICE_ERROR";
    public static final String BROADCAST_TAG = "SERVICE_STATUS";
    public static final String BROADCAST_FILTER = "SERVICE_FILTER";
    public static final String BROADCAST_TRANSPORT = "SERVICE_TRANSPORT";
    private static final boolean DEBUG = false;
    private static final String TAG = "HyperionScreenService";

    private static final String BASE = "com.elhanko.hyperiongrabber.ng.service.";
    public static final String ACTION_START = BASE + "ACTION_START";
    public static final String ACTION_STOP = BASE + "ACTION_STOP";
    public static final String ACTION_EXIT = BASE + "ACTION_EXIT";
    public static final String GET_STATUS = BASE + "ACTION_STATUS";
    public static final String EXTRA_RESULT_CODE = BASE + "EXTRA_RESULT_CODE";
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_EXIT_INTENT_ID = 2;
    // Accessed on the main thread; retained until the previous cleanup has finished.
    private static HyperionScreenService sSessionOwner;

    private boolean RECONNECT = false;
    private boolean hasConnected = false;
    private final AtomicBoolean mStopRequested = new AtomicBoolean();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private MediaProjectionManager mMediaProjectionManager;
    private HyperionThread mHyperionThread;
    private MediaProjection _mediaProjection;
    private Runnable mReleaseProjection;
    private int mFrameRate;
    private int mShutdownTimeoutMs;
    private int mHorizontalLEDCount;
    private int mVerticalLEDCount;
    private boolean mSendAverageColor;
    private HyperionScreenEncoder mHyperionEncoder;
    private NotificationManager mNotificationManager;
    private String mStartError = null;
    private String mTransportName = HyperionTransportType.DEFAULT.displayName();

    HyperionThreadBroadcaster mReceiver = new HyperionThreadBroadcaster() {
        @Override
        public void onConnected(String transportName) {
            mMainHandler.post(() -> {
                if (mStopRequested.get()) {
                    return;
                }
                mTransportName = transportName;
                Log.d(TAG, "CONNECTED TO HYPERION INSTANCE USING " + transportName);
                hasConnected = true;
                mStartError = null;
                notifyActivity();
            });
        }

        @Override
        public void onConnectionError(String transportName, java.io.IOException error) {
            mMainHandler.post(() -> {
                if (mStopRequested.get()) {
                    return;
                }
                mTransportName = transportName;
                Log.e(TAG, "COULD NOT CONNECT TO HYPERION INSTANCE USING " + transportName);
                if (error != null) Log.e(TAG, error.getMessage(), error);
                if (mHyperionThread != null && mHyperionThread.hasActiveTransport()) {
                    notifyActivity();
                    return;
                }
                if (!hasConnected) {
                    mStartError = getResources().getString(R.string.error_server_unreachable);
                    haltStartup();
                } else if (RECONNECT) {
                    Log.e(TAG, "AUTOMATIC RECONNECT ENABLED FOR " + transportName
                            + ". CONNECTING ...");
                    notifyActivity();
                } else {
                    mStartError = getResources().getString(R.string.error_connection_lost);
                    stopScreenRecord();
                    stopSelf();
                }
            });
        }

        @Override
        public void onReceiveStatus(boolean isCapturing) {
            if (DEBUG) Log.v(TAG, "Received grabber status, notifying activity. Status: " +
                    String.valueOf(isCapturing));
            mMainHandler.post(() -> {
                if (!mStopRequested.get()) {
                    notifyActivity();
                }
            });
        }
    };

    BroadcastReceiver mEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_SCREEN_ON:
                    if (DEBUG) Log.v(TAG, "ACTION_SCREEN_ON intent received");
                    if (mHyperionEncoder != null && !isCapturing()) {
                        if (DEBUG) Log.v(TAG, "Encoder not grabbing, attempting to restart");
                        mHyperionEncoder.resumeRecording();
                    }
                    notifyActivity();
                break;
                case Intent.ACTION_SCREEN_OFF:
                    if (DEBUG) Log.v(TAG, "ACTION_SCREEN_OFF intent received");
                    if (mHyperionEncoder != null) {
                        if (DEBUG) Log.v(TAG, "Clearing current light data");
                        mHyperionEncoder.clearLights();
                    }
                break;
                case Intent.ACTION_CONFIGURATION_CHANGED:
                    if (DEBUG) Log.v(TAG, "ACTION_CONFIGURATION_CHANGED intent received");
                    if (mHyperionEncoder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (DEBUG) Log.v(TAG, "Configuration changed, checking orientation");
                        mHyperionEncoder.setOrientation(getResources().getConfiguration().orientation);
                    }
                break;
                case Intent.ACTION_SHUTDOWN:
                case Intent.ACTION_REBOOT:
                    if (DEBUG) Log.v(TAG, "ACTION_SHUTDOWN|ACTION_REBOOT intent received");
                    stopScreenRecord();
                break;
            }
        }
    };

    @Override
    public void onCreate() {
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        super.onCreate();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean prepared() {
        Preferences prefs = new Preferences(getBaseContext());
        try {
            String persistedTransport = prefs.getString(R.string.pref_key_transport, null);
            HyperionTransportType transportType =
                    HyperionTransportType.fromPersistedValue(persistedTransport);
            mTransportName = transportType.displayName();

            String protobufPort = null;
            String flatBufferPort = null;
            if (transportType == HyperionTransportType.FLATBUFFER) {
                flatBufferPort = prefs.getString(
                        R.string.pref_key_flatbuffer_port,
                        Integer.toString(getResources().getInteger(
                                R.integer.pref_default_flatbuffer_port)));
            } else {
                protobufPort = prefs.getString(R.string.pref_key_port, null);
            }

            String host = prefs.getString(R.string.pref_key_host, null);
            String priority = prefs.getString(
                    R.string.pref_key_priority,
                    Integer.toString(getResources().getInteger(R.integer.pref_default_priority)));
            HyperionConnectionSelection selection = HyperionConnectionSelection.resolve(
                    persistedTransport,
                    host,
                    protobufPort,
                    flatBufferPort,
                    priority,
                    1_000,
                    2_000);
            // Reuse the transport read timeout as the best-effort Clear budget.
            mShutdownTimeoutMs = selection.transportConfig().readTimeoutMs();

            mFrameRate = prefs.getInt(R.string.pref_key_framerate);
            mHorizontalLEDCount = prefs.getInt(R.string.pref_key_x_led);
            mVerticalLEDCount = prefs.getInt(R.string.pref_key_y_led);
            mSendAverageColor = prefs.getBoolean(R.string.pref_key_use_avg_color);
            RECONNECT = prefs.getBoolean(R.string.pref_key_reconnect);
            int delay = prefs.getInt(R.string.pref_key_reconnect_delay);
            if (mHorizontalLEDCount <= 0 || mVerticalLEDCount <= 0) {
                mStartError = getResources().getString(R.string.error_invalid_led_counts);
                return false;
            }

            mMediaProjectionManager = (MediaProjectionManager) getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE);
            if (mMediaProjectionManager == null) {
                Log.w(TAG, "Media projection manager unavailable");
                return false;
            }
            mHyperionThread = new HyperionThread(
                    mReceiver,
                    selection.transportType(),
                    selection.transportConfig(),
                    RECONNECT,
                    delay);
            mStartError = null;
            return true;
        } catch (RuntimeException invalidConfiguration) {
            Log.e(TAG, "INVALID " + mTransportName + " STARTUP CONFIGURATION",
                    invalidConfiguration);
            mStartError = getResources().getString(R.string.error_server_unreachable);
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.v(TAG, "Start command received");
        super.onStartCommand(intent, flags, startId);
        if (intent == null || intent.getAction() == null) {
            String nullItem = (intent == null ? "intent" : "action");
            if (DEBUG) Log.v(TAG, "Null " + nullItem + " provided to start command");
            if (mHyperionThread == null) {
                stopSelf();
            }
        } else  {
            final String action = intent.getAction();
            if (DEBUG) Log.v(TAG, "Start command action: " + String.valueOf(action));
            switch (action) {
                case ACTION_START:
                    try {
                        startForeground(NOTIFICATION_ID, getNotification());
                        if (mStopRequested.get()
                                || (sSessionOwner != null && sSessionOwner != this)) {
                            Log.w(TAG, "Previous capture session is still stopping; start rejected");
                            haltStartup();
                            break;
                        }
                        if (mHyperionThread != null) {
                            notifyActivity();
                            break;
                        }
                        sSessionOwner = this;
                        if (prepared() && startScreenRecord(intent)) {
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
                            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                            intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
                            intentFilter.addAction(Intent.ACTION_REBOOT);
                            intentFilter.addAction(Intent.ACTION_SHUTDOWN);

                            registerReceiver(mEventReceiver, intentFilter);
                            mHyperionThread.start();
                        } else {
                            haltStartup();
                        }
                    } catch (IllegalArgumentException | IllegalStateException
                             | SecurityException startupFailure) {
                        Log.e(TAG, "Could not start screen capture", startupFailure);
                        haltStartup();
                    }
                    break;
                case ACTION_STOP:
                case ACTION_EXIT:
                    stopScreenRecord();
                    stopSelf();
                    break;
                case GET_STATUS:
                    notifyActivity();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.v(TAG, "Ending service");

        try {
            unregisterReceiver(mEventReceiver);
        } catch (Exception e) {
            if (DEBUG) Log.v(TAG, "Wake receiver not registered");
        }

        stopScreenRecord();
        stopForeground(true);
        notifyActivity();

        super.onDestroy();
    }

    private void haltStartup() {
        stopScreenRecord();
        notifyActivity();
        stopSelf();
    }

    private Intent buildExitButton() {
        Intent notificationIntent = new Intent(this, this.getClass());
        notificationIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        notificationIntent.setAction(ACTION_EXIT);
        return notificationIntent;
    }

    public Notification getNotification() {
        HyperionNotification notification = new HyperionNotification(this, mNotificationManager);
        String label = getString(R.string.notification_exit_button);
        notification.setAction(NOTIFICATION_EXIT_INTENT_ID, label, buildExitButton());
        return notification.buildNotification();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean startScreenRecord(final Intent intent) {
        if (DEBUG) Log.v(TAG, "Start screen recorder");
        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        // get MediaProjection
        final MediaProjection projection = mMediaProjectionManager.getMediaProjection(resultCode, intent);
        if (projection != null) {
            _mediaProjection = projection;
            AtomicBoolean projectionStopped = new AtomicBoolean();
            MediaProjection.Callback callback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    projectionStopped.set(true);
                    if (_mediaProjection != projection || mStopRequested.get()) {
                        return;
                    }
                    stopScreenRecord();
                    stopSelf();
                }
            };
            mReleaseProjection = () -> {
                projection.unregisterCallback(callback);
                if (!projectionStopped.get()) {
                    try {
                        projection.stop();
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Could not stop media projection", e);
                    }
                }
            };
            projection.registerCallback(callback, mMainHandler);
        }
        WindowManager window = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (projection != null && window != null) {
            final DisplayMetrics metrics = new DisplayMetrics();
            Display display = window.getDefaultDisplay();
            if (display == null) {
                Log.w(TAG, "Default display unavailable");
                return false;
            }
            display.getRealMetrics(metrics);
            if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0
                    || metrics.densityDpi <= 0) {
                Log.w(TAG, "Invalid display metrics");
                return false;
            }
            final int density = metrics.densityDpi;
            HyperionGrabberOptions options = new HyperionGrabberOptions(mHorizontalLEDCount,
                    mVerticalLEDCount, mFrameRate, mSendAverageColor);
            if (DEBUG) Log.v(TAG, "Starting the recorder");
            mHyperionEncoder = new HyperionScreenEncoder(mHyperionThread.getReceiver(),
                    projection, metrics.widthPixels, metrics.heightPixels,
                    density, options, mShutdownTimeoutMs, () -> {
                        if (_mediaProjection == projection && !mStopRequested.get()) {
                            stopScreenRecord();
                            stopSelf();
                        }
                    });
            try {
                mHyperionEncoder.prepare();
            } catch (RuntimeException initializationFailure) {
                Log.e(TAG, "Could not initialize capture resources", initializationFailure);
                return false;
            }
            mHyperionEncoder.sendStatus();
            return true;
        }
        Log.w(TAG, "Media projection or window manager unavailable");
        return false;
    }

    private void stopScreenRecord() {
        if (DEBUG) Log.v(TAG, "Stop screen recorder");
        if (!mStopRequested.compareAndSet(false, true)) {
            return;
        }
        RECONNECT = false;
        if (mHyperionThread != null) {
            mHyperionThread.preventReconnect();
        }
        mNotificationManager.cancel(NOTIFICATION_ID);
        try {
            releaseResource();
        } finally {
            notifyActivity();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void releaseResource() {
        _mediaProjection = null;
        mMediaProjectionManager = null;
        HyperionScreenEncoder encoder = mHyperionEncoder;
        mHyperionEncoder = null;
        HyperionThread thread = mHyperionThread;
        mHyperionThread = null;
        Runnable releaseProjection = mReleaseProjection;
        mReleaseProjection = null;
        if (encoder != null) {
            encoder.stopRecording(releaseProjection, () -> finishCleanup(thread));
        } else {
            // The network thread starts only after the encoder has been assigned and prepared.
            try {
                if (thread != null) {
                    thread.shutdown();
                }
            } finally {
                try {
                    if (releaseProjection != null) {
                        releaseProjection.run();
                    }
                } finally {
                    finishCleanup(thread);
                }
            }
        }
    }

    private void finishCleanup(HyperionThread thread) {
        boolean interrupted = false;
        try {
            if (thread != null) {
                for (;;) {
                    try {
                        thread.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        Log.w(TAG, "Interrupted while waiting for network thread to stop", e);
                    }
                }
            }
            mMainHandler.post(() -> {
                if (sSessionOwner == this) {
                    sSessionOwner = null;
                }
            });
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    boolean isCapturing() {
        return mHyperionEncoder != null && mHyperionEncoder.isCapturing();
    }

    boolean isCommunicating() {
        return !mStopRequested.get() && isCapturing()
                && mHyperionThread != null && mHyperionThread.hasActiveTransport();
    }

    private void notifyActivity() {
        Intent intent = new Intent(BROADCAST_FILTER);
        intent.putExtra(BROADCAST_TAG, isCommunicating());
        intent.putExtra(BROADCAST_ERROR, mStartError);
        intent.putExtra(BROADCAST_TRANSPORT, mTransportName);
        if (DEBUG) {
            Log.v(TAG, "Sending status broadcast - communicating: " +
                    String.valueOf(isCommunicating()));
            if (mStartError != null) {
                Log.v(TAG, "Startup error: " + mStartError);
            }
        }
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    public interface HyperionThreadBroadcaster {
//        void onResponse(String response);
        void onConnected(String transportName);
        void onConnectionError(String transportName, java.io.IOException error);
        void onReceiveStatus(boolean isCapturing);
    }
}
