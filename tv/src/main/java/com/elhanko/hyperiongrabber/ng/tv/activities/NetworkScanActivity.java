package com.elhanko.hyperiongrabber.ng.tv.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoveredHyperionServer;
import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoveredServerAdapter;
import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoverySelection;
import com.elhanko.hyperiongrabber.ng.common.discovery.HyperionDiscovery;
import com.elhanko.hyperiongrabber.ng.common.util.Preferences;
import com.elhanko.hyperiongrabber.ng.tv.R;
import com.elhanko.hyperiongrabber.ng.tv.databinding.ActivityNetworkScanBinding;

import java.util.List;

/** TV onboarding and settings screen for explicit Hyperion ProtoServer discovery. */
public class NetworkScanActivity extends LeanbackActivity implements HyperionDiscovery.Listener {
    public static final String EXTRA_INITIAL_SETUP = "extra_initial_setup";

    private boolean initialSetup;
    private boolean discoveryFailed;
    private HyperionDiscovery discovery;
    private DiscoveredServerAdapter serverAdapter;
    private Button startScanButton;
    private Button manualSetupButton;
    private ProgressBar progressBar;
    private TextView descriptionText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityNetworkScanBinding binding = ActivityNetworkScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setResult(RESULT_CANCELED);

        initialSetup = getIntent().getBooleanExtra(EXTRA_INITIAL_SETUP, false);
        startScanButton = binding.startScanButton;
        manualSetupButton = binding.manualSetupButton;
        progressBar = binding.progressBar;
        descriptionText = binding.scannerDescriptionText;
        ListView serverList = binding.serverList;
        serverAdapter = new DiscoveredServerAdapter(this);
        serverList.setAdapter(serverAdapter);
        serverList.setOnItemClickListener((parent, view, position, id) ->
                selectServer(serverAdapter.getItem(position)));
        discovery = new HyperionDiscovery(getApplicationContext(), this);
    }

    public void onClick(View view) {
        if (view.getId() == R.id.startScanButton) {
            if (discovery.isRunning()) {
                discovery.stop();
            } else if (discovery.start()) {
                discoveryFailed = false;
                showSearching();
            }
        } else if (view.getId() == R.id.manualSetupButton) {
            discovery.stop();
            if (initialSetup) {
                Intent intent = new Intent(this, ManualSetupActivity.class);
                startActivityForResult(intent, MainActivity.REQUEST_INITIAL_SETUP);
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.REQUEST_INITIAL_SETUP && resultCode == RESULT_OK) {
            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onStop() {
        if (discovery != null) {
            discovery.stop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (discovery != null) {
            discovery.close();
        }
        super.onDestroy();
    }

    @Override
    public void onSearchStarted() {
        showSearching();
    }

    @Override
    public void onResultsChanged(@NonNull List<DiscoveredHyperionServer> servers) {
        serverAdapter.replace(servers);
        descriptionText.setText(servers.isEmpty()
                ? com.elhanko.hyperiongrabber.ng.common.R.string.discovery_searching
                : com.elhanko.hyperiongrabber.ng.common.R.string.discovery_select_hint);
    }

    @Override
    public void onSearchFailed(int errorCode) {
        discoveryFailed = true;
        descriptionText.setText(
                com.elhanko.hyperiongrabber.ng.common.R.string.discovery_failed);
    }

    @Override
    public void onSearchStopped(@NonNull List<DiscoveredHyperionServer> servers) {
        progressBar.setVisibility(View.GONE);
        startScanButton.setText(
                com.elhanko.hyperiongrabber.ng.common.R.string.discovery_start);
        serverAdapter.replace(servers);
        if (discoveryFailed) {
            descriptionText.setText(
                    com.elhanko.hyperiongrabber.ng.common.R.string.discovery_failed);
        } else if (servers.isEmpty()) {
            descriptionText.setText(
                    com.elhanko.hyperiongrabber.ng.common.R.string.discovery_no_results);
        } else {
            descriptionText.setText(
                    com.elhanko.hyperiongrabber.ng.common.R.string.discovery_select_hint);
        }
    }

    private void showSearching() {
        progressBar.setVisibility(View.VISIBLE);
        startScanButton.setText(
                com.elhanko.hyperiongrabber.ng.common.R.string.discovery_cancel);
        descriptionText.setText(
                com.elhanko.hyperiongrabber.ng.common.R.string.discovery_searching);
    }

    private void selectServer(DiscoveredHyperionServer server) {
        DiscoverySelection.save(server, new Preferences(getApplicationContext()));
        Toast.makeText(
                this,
                com.elhanko.hyperiongrabber.ng.common.R.string.discovery_saved,
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
