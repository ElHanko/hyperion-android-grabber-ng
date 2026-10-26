package com.elhanko.hyperiongrabber.ng.tv.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityOptionsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.elhanko.hyperiongrabber.ng.common.BootActivity;
import com.elhanko.hyperiongrabber.ng.common.HyperionScreenService;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportPreferenceBinding;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportStatus;
import com.elhanko.hyperiongrabber.ng.common.util.Preferences;
import com.elhanko.hyperiongrabber.ng.tv.R;

import java.util.UUID;

public class MainActivity extends LeanbackActivity implements ImageView.OnClickListener,
        ImageView.OnFocusChangeListener {
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    public static final int REQUEST_INITIAL_SETUP = 2;
    private static final String TAG = "DEBUG";
    private static final long START_REQUEST_TIMEOUT_MS = 10_000;
    private static final String STATE_PERMISSION_PENDING = "projection_permission_pending";
    private static final String STATE_START_REQUEST_ID = "start_request_id";
    private static final String STATE_START_REQUEST_DEADLINE = "start_request_deadline";
    // Accessed on the main thread; survives activity recreation in this process.
    private static String sStartRequestId;
    private static long sStartRequestDeadline;
    private boolean mRecorderRunning = false;
    private boolean mProjectionPermissionPending = false;
    private boolean mStatusReceiverRegistered = false;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Runnable mStartRequestTimeout;
    private String mActiveTransportName;
    private static MediaProjectionManager mMediaProjectionManager;

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean checked = intent.getBooleanExtra(HyperionScreenService.BROADCAST_TAG, false);
            mRecorderRunning = intent.getBooleanExtra(
                    HyperionScreenService.BROADCAST_SESSION_ACTIVE, false);
            if (sStartRequestId != null && sStartRequestId.equals(intent.getStringExtra(
                    HyperionScreenService.BROADCAST_COMPLETED_START_REQUEST_ID))) {
                clearStartRequest();
            }
            String transportName = intent.getStringExtra(HyperionScreenService.BROADCAST_TRANSPORT);
            if (HyperionTransportStatus.connectedUsing(transportName) != null) {
                mActiveTransportName = transportName;
            }
            String error = intent.getStringExtra(HyperionScreenService.BROADCAST_ERROR);
            if (error != null) {
                Toast.makeText(getBaseContext(),
                        HyperionTransportStatus.formatError(transportName, error),
                        Toast.LENGTH_SHORT).show();
            }
            setImageViews(checked, true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mProjectionPermissionPending = savedInstanceState.getBoolean(STATE_PERMISSION_PENDING);
            if (sStartRequestId == null) {
                sStartRequestId = savedInstanceState.getString(STATE_START_REQUEST_ID);
                sStartRequestDeadline = savedInstanceState.getLong(STATE_START_REQUEST_DEADLINE);
            }
        }
        if (sStartRequestId != null) {
            mProjectionPermissionPending = false;
        }
        scheduleStartRequestTimeout();

        if (!initIfConfigured()){
            startSetup();
        }


    }

    /** @return whether the activity was initialized */
    private boolean initIfConfigured() {
        // Do we have a valid server config?
        Preferences preferences = new Preferences(getApplicationContext());
        String host = preferences.getString(
                com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_host, null);
        boolean flatBufferEnabled = HyperionTransportPreferenceBinding.isFlatBufferEnabled(
                preferences.getString(
                        com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_transport,
                        null));
        String selectedPort = flatBufferEnabled
                ? HyperionTransportPreferenceBinding.flatBufferPortOrDefault(preferences.getString(
                        com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_flatbuffer_port,
                        null))
                : preferences.getString(
                        com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_port,
                        null);

        if (host == null || !HyperionTransportPreferenceBinding.isValidPort(selectedPort)){
            return false;
        }

        initActivity();
        return true;
    }

    private void startSetup() {
        // Start onboarding (setup)
        Intent intent = new Intent(this, NetworkScanActivity.class);
        intent.putExtra(NetworkScanActivity.EXTRA_INITIAL_SETUP, true);
        startActivityForResult(intent, REQUEST_INITIAL_SETUP);
    }

    // Prepare activity for display
    private void initActivity() {
        // assume the recorder is not running until we are notified otherwise
        mRecorderRunning = false;

        setContentView(R.layout.activity_main);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        mMediaProjectionManager = (MediaProjectionManager)
                                        getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        ImageView iv = findViewById(R.id.power_toggle);
        iv.setOnClickListener(this);
        iv.setOnFocusChangeListener(this);
        iv.setFocusable(true);
        iv.requestFocus();

        ImageButton ib = findViewById(R.id.settingsButton);
        ib.setOnClickListener(this);
        ib.setOnFocusChangeListener(this);
        ib.setFocusable(true);

        setImageViews(false, false);

        if (!mStatusReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    mMessageReceiver, new IntentFilter(HyperionScreenService.BROADCAST_FILTER));
            mStatusReceiverRegistered = true;
        }
        checkForInstance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleStartRequestTimeout();
        if (mStatusReceiverRegistered) {
            checkForInstance();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_PERMISSION_PENDING, mProjectionPermissionPending);
        outState.putString(STATE_START_REQUEST_ID, sStartRequestId);
        outState.putLong(STATE_START_REQUEST_DEADLINE, sStartRequestDeadline);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (mStartRequestTimeout != null) {
            mMainHandler.removeCallbacks(mStartRequestTimeout);
        }
        if (mStatusReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
            mStatusReceiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.power_toggle) {
            if (mProjectionPermissionPending || sStartRequestId != null
                    || HyperionScreenService.isSessionStopping()) {
                return;
            }
            // A started service may not have broadcast its session state yet.
            if (!mRecorderRunning && !isServiceRunning()) {
                mProjectionPermissionPending = true;
                try {
                    startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(),
                            REQUEST_MEDIA_PROJECTION);
                } catch (RuntimeException permissionFailure) {
                    mProjectionPermissionPending = false;
                    Log.e(TAG, "Could not request screen capture permission", permissionFailure);
                }
            } else {
                stopScreenRecorder();
            }
        } else if (view.getId() == R.id.settingsButton) {
            startSettings();
        }
    }

    @Override
    public void onFocusChange(View view, boolean focused) {
        int clr = Color.argb(255, 0, 0, 150);
        if (!focused) {
            clr = Color.argb(255, 0, 0, 0);
        }
        if (view.getId() == R.id.power_toggle) {
            ((ImageView) view).setColorFilter(clr);
        } else if (view.getId() == R.id.settingsButton) {
            ((ImageButton) view).setColorFilter(clr);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_INITIAL_SETUP){
            if (resultCode == RESULT_OK){
                if (!initIfConfigured()){
                    startSetup();
                }
            } else {
                finish();
            }

            return;
        }
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (!mProjectionPermissionPending) {
                return;
            }
            mProjectionPermissionPending = false;
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this,
                        com.elhanko.hyperiongrabber.ng.common.R.string.toast_must_give_permission,
                        Toast.LENGTH_SHORT).show();
                setImageViews(false, true);
                return;
            }
            if (data == null) {
                Log.e(TAG, "Screen capture permission returned no data");
                return;
            }
            Log.i(TAG, "Starting screen capture");
            startScreenRecorder(resultCode, (Intent) data.clone());
        }
    }

    private void startSettings() {
        stopScreenRecorder();
        Intent intent = new Intent(this, ManualSetupActivity.class);
        Bundle bundle =
                ActivityOptionsCompat.makeSceneTransitionAnimation(this)
                        .toBundle();
        startActivity(intent, bundle);
    }

    private void checkForInstance() {
        if (isServiceRunning()) {
            Intent intent = new Intent(this, HyperionScreenService.class);
            intent.setAction(HyperionScreenService.GET_STATUS);
            startService(intent);
        } else {
            mRecorderRunning = false;
            setImageViews(false, false);
        }
    }

    private void setImageViews(boolean running, boolean animated) {
        View rainbow = findViewById(R.id.sweepGradientView);
        TextView message = findViewById(R.id.grabberStartedText);
        String transportStatus = HyperionTransportStatus.connectedUsing(mActiveTransportName);
        message.setText(transportStatus == null
                ? getString(com.elhanko.hyperiongrabber.ng.common.R.string.message_service_started)
                : getString(com.elhanko.hyperiongrabber.ng.common.R.string
                        .message_service_started_with_transport, transportStatus));
        message.setContentDescription(message.getText());
        if (running) {
            if (animated){
                fadeView(rainbow, true);
                fadeView(message, true);
            } else {
                rainbow.setVisibility(View.VISIBLE);
                message.setVisibility(View.VISIBLE);
            }
        } else {
            if (animated){
                fadeView(rainbow, false);
                fadeView(message, false);
            } else {
                rainbow.setVisibility(View.INVISIBLE);
                message.setVisibility(View.INVISIBLE);
            }
        }
    }

    public void startScreenRecorder(int resultCode, Intent data) {
        if (sStartRequestId != null) {
            return;
        }
        sStartRequestId = UUID.randomUUID().toString();
        sStartRequestDeadline = SystemClock.elapsedRealtime() + START_REQUEST_TIMEOUT_MS;
        try {
            data.putExtra(HyperionScreenService.EXTRA_START_REQUEST_ID, sStartRequestId);
            data.putExtra(HyperionScreenService.EXTRA_START_REQUEST_DEADLINE,
                    sStartRequestDeadline);
            BootActivity.startScreenRecorder(this, resultCode, data);
            scheduleStartRequestTimeout();
        } catch (RuntimeException startupFailure) {
            clearStartRequest();
            Log.e(TAG, "Could not submit screen capture start request", startupFailure);
        }
    }

    private void scheduleStartRequestTimeout() {
        if (mStartRequestTimeout != null) {
            mMainHandler.removeCallbacks(mStartRequestTimeout);
        }
        final String requestId = sStartRequestId;
        if (requestId != null) {
            mStartRequestTimeout = () -> {
                if (requestId.equals(sStartRequestId)) {
                    clearStartRequest();
                    Log.w(TAG, "Screen capture start request timed out");
                    if (mStatusReceiverRegistered) {
                        checkForInstance();
                    }
                }
            };
            // Resuming or recreating the activity does not extend the original deadline.
            mMainHandler.postDelayed(mStartRequestTimeout,
                    Math.max(0, sStartRequestDeadline - SystemClock.elapsedRealtime()));
        }
    }

    private void clearStartRequest() {
        if (mStartRequestTimeout != null) {
            mMainHandler.removeCallbacks(mStartRequestTimeout);
            mStartRequestTimeout = null;
        }
        sStartRequestId = null;
        sStartRequestDeadline = 0;
    }

    public void stopScreenRecorder() {
        if (mRecorderRunning || isServiceRunning()) {
            Intent intent = new Intent(this, HyperionScreenService.class);
            intent.setAction(HyperionScreenService.ACTION_EXIT);
            startService(intent);
        }
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        assert manager != null;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (HyperionScreenService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void fadeView(View view, boolean visible){
        float alpha = visible ? 1f : 0f;
        int endVisibility = visible ? View.VISIBLE : View.INVISIBLE;
        view.setVisibility(View.VISIBLE);
        view.animate()
                .alpha(alpha)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.setVisibility(endVisibility);
                    }
                })
                .start();


    }
}
