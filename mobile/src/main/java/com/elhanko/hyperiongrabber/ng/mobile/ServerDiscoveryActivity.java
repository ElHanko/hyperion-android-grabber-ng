package com.elhanko.hyperiongrabber.ng.mobile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoveredHyperionServer;
import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoveredServerAdapter;
import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoverySelection;
import com.elhanko.hyperiongrabber.ng.common.discovery.HyperionDiscovery;
import com.elhanko.hyperiongrabber.ng.common.util.Preferences;

import java.util.List;

/** Mobile settings screen for explicit Hyperion ProtoServer discovery. */
public final class ServerDiscoveryActivity extends AppCompatActivity
        implements HyperionDiscovery.Listener {
    private HyperionDiscovery discovery;
    private DiscoveredServerAdapter serverAdapter;
    private TextView status;
    private ProgressBar progress;
    private Button startButton;
    private boolean discoveryFailed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_discovery);
        setTitle(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_title);

        status = findViewById(R.id.discoveryStatus);
        progress = findViewById(R.id.discoveryProgress);
        startButton = findViewById(R.id.discoveryStartButton);
        ListView serverList = findViewById(R.id.discoveryServerList);
        serverAdapter = new DiscoveredServerAdapter(this);
        serverList.setAdapter(serverAdapter);
        serverList.setOnItemClickListener((parent, view, position, id) ->
                selectServer(serverAdapter.getItem(position)));
        startButton.setOnClickListener(view -> {
            if (discovery.isRunning()) {
                discovery.stop();
            } else if (discovery.start()) {
                discoveryFailed = false;
                showSearching();
            }
        });
        findViewById(R.id.discoveryManualButton).setOnClickListener(view -> {
            discovery.stop();
            finish();
        });
        discovery = new HyperionDiscovery(getApplicationContext(), this);
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
        status.setText(servers.isEmpty()
                ? com.elhanko.hyperiongrabber.ng.common.R.string.discovery_searching
                : com.elhanko.hyperiongrabber.ng.common.R.string.discovery_select_hint);
    }

    @Override
    public void onSearchFailed(int errorCode) {
        discoveryFailed = true;
        status.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_failed);
    }

    @Override
    public void onSearchStopped(@NonNull List<DiscoveredHyperionServer> servers) {
        progress.setVisibility(View.GONE);
        startButton.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_start);
        serverAdapter.replace(servers);
        if (discoveryFailed) {
            status.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_failed);
        } else if (servers.isEmpty()) {
            status.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_no_results);
        } else {
            status.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_select_hint);
        }
    }

    private void showSearching() {
        progress.setVisibility(View.VISIBLE);
        startButton.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_cancel);
        status.setText(com.elhanko.hyperiongrabber.ng.common.R.string.discovery_searching);
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
