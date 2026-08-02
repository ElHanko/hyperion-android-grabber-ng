package com.elhanko.hyperiongrabber.ng.common.discovery;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class DiscoverySelectionTest {
    @Test
    public void explicitSelectionWritesHostAndPortInOneStoreCall() throws Exception {
        HyperionServerStore serverStore = new HyperionServerStore();
        serverStore.put(new HyperionDiscoveryBackend.ResolvedService(
                new HyperionDiscoveryBackend.ServiceReference("one", "Hyperion"),
                InetAddress.getByName("192.0.2.40"),
                25000,
                "id".getBytes(StandardCharsets.UTF_8),
                null));
        RecordingStore preferences = new RecordingStore("manual.example", 19445);

        DiscoverySelection.save(serverStore.snapshot().get(0), preferences);

        assertEquals(1, preferences.calls);
        assertEquals("192.0.2.40", preferences.host);
        assertEquals(25000, preferences.port);
    }

    @Test
    public void unsuccessfulSearchDoesNotChangeManualConfiguration() {
        RecordingStore preferences = new RecordingStore("manual.example", 19445);
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = new HyperionDiscoveryController(
                backend,
                new EmptyListener(),
                Runnable::run);

        controller.start();
        backend.callback.onStartFailed(1, 0);

        assertEquals(0, preferences.calls);
        assertEquals("manual.example", preferences.host);
        assertEquals(19445, preferences.port);
    }

    private static final class RecordingStore implements HostPortStore {
        private String host;
        private int port;
        private int calls;

        private RecordingStore(String host, int port) {
            this.host = host;
            this.port = port;
        }

        @Override
        public void putHostAndPort(String host, int port) {
            calls++;
            this.host = host;
            this.port = port;
        }
    }

    private static final class FakeBackend implements HyperionDiscoveryBackend {
        private DiscoveryCallback callback;

        @Override
        public void start(long generation, DiscoveryCallback callback) {
            this.callback = callback;
        }

        @Override
        public void resolve(long generation, ServiceReference service, ResolveCallback callback) {
        }

        @Override
        public void stop(long generation) {
        }

        @Override
        public boolean isRunning() {
            return callback != null;
        }
    }

    private static final class EmptyListener implements HyperionDiscoveryController.Listener {
        @Override
        public void onSearchStarted() {
        }

        @Override
        public void onResultsChanged(java.util.List<DiscoveredHyperionServer> servers) {
        }

        @Override
        public void onSearchFailed(int errorCode) {
        }

        @Override
        public void onSearchStopped(java.util.List<DiscoveredHyperionServer> servers) {
        }
    }
}
