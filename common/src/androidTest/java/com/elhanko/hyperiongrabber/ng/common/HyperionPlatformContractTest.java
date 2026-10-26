package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.preference.PreferenceManager;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/** Checks the common test host, not the TV APK's SDK values, launchers, or signing certificate. */
public class HyperionPlatformContractTest {
    private Context context;
    private SharedPreferences preferences;
    private String bootKey;
    private boolean hadBootPreference;
    private boolean previousBootValue;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        bootKey = context.getString(R.string.pref_key_boot);
        hadBootPreference = preferences.contains(bootKey);
        previousBootValue = preferences.getBoolean(bootKey, false);
    }

    @After
    public void restoreBootPreference() {
        SharedPreferences.Editor editor = preferences.edit();
        if (hadBootPreference) {
            editor.putBoolean(bootKey, previousBootValue);
        } else {
            editor.remove(bootKey);
        }
        assertTrue("Could not restore the test host's boot preference", editor.commit());
    }

    @Test
    public void captureServiceIsEnabledAndPrivate() throws Exception {
        ServiceInfo service = serviceInfo();
        assertTrue(service.enabled);
        assertFalse(service.exported);
    }

    @Test
    public void foregroundServicePermissionIsDeclared() throws Exception {
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertNotNull(info.requestedPermissions);
        assertTrue(Arrays.asList(info.requestedPermissions)
                .contains(Manifest.permission.FOREGROUND_SERVICE));
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    public void captureServiceDeclaresMediaProjectionType() throws Exception {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                serviceInfo().foregroundServiceType);
    }

    @Test
    public void bootActivityAndReceiverRemainPrivate() throws Exception {
        PackageManager packages = context.getPackageManager();
        assertFalse(packages.getActivityInfo(
                new ComponentName(context, BootActivity.class), 0).exported);
        assertFalse(packages.getReceiverInfo(
                new ComponentName(context, HyperionGrabberBootReceiver.class), 0).exported);
    }

    @Test
    public void disabledBootOptionDoesNotLaunchAnActivity() {
        setBoot(false);
        RecordingContext recording = new RecordingContext(context);
        new HyperionGrabberBootReceiver().onReceive(
                recording, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertNull(recording.launched);
    }

    @Test
    public void enabledBootOptionBuildsAnExplicitNewTaskPermissionEntry() {
        setBoot(true);
        RecordingContext recording = new RecordingContext(context);
        new HyperionGrabberBootReceiver().onReceive(
                recording, new Intent(Intent.ACTION_BOOT_COMPLETED));

        assertNotNull(recording.launched);
        assertEquals(1, recording.launches);
        assertEquals(new ComponentName(context, BootActivity.class),
                recording.launched.getComponent());
        int expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_HISTORY;
        assertEquals(expectedFlags, recording.launched.getFlags() & expectedFlags);
        assertFalse(recording.launched.hasExtra(HyperionScreenService.EXTRA_RESULT_CODE));
    }

    @Test
    public void unrelatedBroadcastDoesNotLaunchTheBootActivity() {
        setBoot(true);
        RecordingContext recording = new RecordingContext(context);
        new HyperionGrabberBootReceiver().onReceive(
                recording, new Intent(Intent.ACTION_SCREEN_ON));
        assertNull(recording.launched);
    }

    private ServiceInfo serviceInfo() throws PackageManager.NameNotFoundException {
        return context.getPackageManager().getServiceInfo(
                new ComponentName(context, HyperionScreenService.class), 0);
    }

    private void setBoot(boolean enabled) {
        assertTrue("Could not set the test host's boot preference",
                preferences.edit().putBoolean(bootKey, enabled).commit());
    }

    private static final class RecordingContext extends ContextWrapper {
        Intent launched;
        int launches;

        RecordingContext(Context base) {
            super(base);
        }

        @Override
        public void startActivity(Intent intent) {
            // Verify production intent construction without launching UI or claiming BAL permission.
            launches++;
            launched = new Intent(intent);
        }
    }
}
