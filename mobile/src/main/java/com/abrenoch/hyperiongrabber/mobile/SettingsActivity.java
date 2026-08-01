package com.abrenoch.hyperiongrabber.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.lang.reflect.Field;

/** Presents the application settings as a single preference list. */
public class SettingsActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_TOAST_KEY = "extra_show_toast_key";
    public static final int EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE = 1;

    /** Updates a preference summary and validates preferences with integer values. */
    private static final Preference.OnPreferenceChangeListener
            sBindPreferenceSummaryToValueListener = (preference, value) -> {
        final int prefResourceID = getResourceId(
                preference.getKey(), com.abrenoch.hyperiongrabber.common.R.string.class);

        if (prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_port
                || prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_reconnect_delay
                || prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_priority
                || prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_x_led
                || prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_y_led
                || prefResourceID == com.abrenoch.hyperiongrabber.common.R.string.pref_key_framerate) {
            try {
                Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return false;
            }
        }

        preference.setSummary(value.toString());
        return true;
    };

    /** Returns the resource ID of the provided string. */
    public static int getResourceId(String resourceName, Class<?> resourceClass) {
        try {
            Field idField = resourceClass.getDeclaredField(resourceName);
            return idField.getInt(idField);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private static void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
        sBindPreferenceSummaryToValueListener.onPreferenceChange(
                preference,
                PreferenceManager.getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), "")
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new GeneralPreferenceFragment())
                .commit();

        Bundle extras = getIntent().getExtras();
        if (extras != null
                && extras.getInt(EXTRA_SHOW_TOAST_KEY)
                == EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE) {
            Toast.makeText(
                    getApplicationContext(),
                    R.string.quick_tile_toast_setup_required,
                    Toast.LENGTH_SHORT
            ).show();
        }

        setupActionBar();
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /** Shows the existing general preference resource through AndroidX Preferences. */
    public static class GeneralPreferenceFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(
                    com.abrenoch.hyperiongrabber.common.R.xml.pref_general, rootKey);
            setHasOptionsMenu(true);

            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_host)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_port)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_priority)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_framerate)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_reconnect_delay)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_x_led)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.abrenoch.hyperiongrabber.common.R.string.pref_key_y_led)));
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == android.R.id.home) {
                startActivity(new Intent(requireActivity(), SettingsActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
