package com.elhanko.hyperiongrabber.ng.mobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportPreferenceBinding;
import com.elhanko.hyperiongrabber.ng.common.util.Preferences;

import java.lang.reflect.Field;

/** Presents the application settings as a single preference list. */
public class SettingsActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_TOAST_KEY = "extra_show_toast_key";
    public static final int EXTRA_SHOW_TOAST_SETUP_REQUIRED_FOR_QUICK_TILE = 1;

    /** Updates a preference summary and validates preferences with integer values. */
    private static final Preference.OnPreferenceChangeListener
            sBindPreferenceSummaryToValueListener = (preference, value) -> {
        final int prefResourceID = getResourceId(
                preference.getKey(), com.elhanko.hyperiongrabber.ng.common.R.string.class);

        if (prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_port
                || prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_reconnect_delay
                || prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_priority
                || prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_x_led
                || prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_y_led
                || prefResourceID == com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_framerate) {
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
                    com.elhanko.hyperiongrabber.ng.common.R.xml.pref_general, rootKey);
            setHasOptionsMenu(true);

            Preference discoveryPreference = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_discover_server));
            if (discoveryPreference != null) {
                discoveryPreference.setOnPreferenceClickListener(preference -> {
                    startActivity(new Intent(requireContext(), ServerDiscoveryActivity.class));
                    return true;
                });
            }

            bindConnectionSummaries();
            bindFlatBufferTransportPreferences();
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_priority)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_framerate)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_reconnect_delay)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_x_led)));
            bindPreferenceSummaryToValue(findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_y_led)));
        }

        @Override
        public void onResume() {
            super.onResume();
            bindConnectionSummaries();
            updateFlatBufferTransportPreferences();
        }

        private void bindConnectionSummaries() {
            Preference host = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_host));
            Preference port = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_port));
            if (host != null) {
                bindPreferenceSummaryToValue(host);
            }
            if (port != null) {
                bindPreferenceSummaryToValue(port);
            }
        }

        /**
         * Binds a non-persistent checkbox to the stable string transport preference.
         *
         * <p>A running grabber retains its service-reported transport. This control only selects
         * the transport for its next start.</p>
         */
        private void bindFlatBufferTransportPreferences() {
            CheckBoxPreference transportControl = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string
                            .pref_key_flatbuffer_transport_control));
            Preference flatBufferPort = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_flatbuffer_port));
            if (transportControl != null) {
                transportControl.setPersistent(false);
                transportControl.setOnPreferenceChangeListener((preference, value) -> {
                    boolean enabled = Boolean.TRUE.equals(value);
                    new Preferences(requireContext()).putString(
                            com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_transport,
                            HyperionTransportPreferenceBinding.persistedValue(enabled));
                    transportControl.setChecked(enabled);
                    updateFlatBufferTransportPreferences();
                    return false;
                });
            }
            if (flatBufferPort != null) {
                flatBufferPort.setOnPreferenceChangeListener((preference, value) -> {
                    String port = value == null ? null : value.toString();
                    if (!HyperionTransportPreferenceBinding.isValidPort(port)) {
                        Toast.makeText(requireContext(),
                                com.elhanko.hyperiongrabber.ng.common.R.string
                                        .pref_error_invalid_flatbuffer_port,
                                Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    preference.setSummary(port);
                    return true;
                });
            }
            updateFlatBufferTransportPreferences();
        }

        /** Refreshes only the presentation; it never repairs or migrates stored preferences. */
        private void updateFlatBufferTransportPreferences() {
            CheckBoxPreference transportControl = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string
                            .pref_key_flatbuffer_transport_control));
            Preference flatBufferPort = findPreference(getString(
                    com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_flatbuffer_port));
            Preferences preferences = new Preferences(requireContext());
            boolean enabled = HyperionTransportPreferenceBinding.isFlatBufferEnabled(
                    preferences.getString(
                            com.elhanko.hyperiongrabber.ng.common.R.string.pref_key_transport,
                            null));

            if (transportControl != null) {
                transportControl.setChecked(enabled);
                transportControl.setSummary(enabled
                        ? com.elhanko.hyperiongrabber.ng.common.R.string
                                .pref_summary_flatbuffer_transport_enabled
                        : com.elhanko.hyperiongrabber.ng.common.R.string
                                .pref_summary_flatbuffer_transport_disabled);
            }
            if (flatBufferPort != null) {
                flatBufferPort.setVisible(enabled);
                String port = HyperionTransportPreferenceBinding.flatBufferPortOrDefault(
                        preferences.getString(
                                com.elhanko.hyperiongrabber.ng.common.R.string
                                        .pref_key_flatbuffer_port,
                                null));
                flatBufferPort.setSummary(port);
            }
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
