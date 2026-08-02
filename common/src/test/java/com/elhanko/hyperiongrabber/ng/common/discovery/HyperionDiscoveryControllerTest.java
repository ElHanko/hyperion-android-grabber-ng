package com.elhanko.hyperiongrabber.ng.common.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HyperionDiscoveryControllerTest {
    @Test
    public void resolvesExactlyOneServiceAtATimeAndContinuesAfterFailure() throws Exception {
        FakeBackend backend = new FakeBackend();
        RecordingListener listener = new RecordingListener();
        HyperionDiscoveryController controller = controller(backend, listener);
        controller.start();
        backend.started(1);

        backend.found(1, reference("one"));
        backend.found(1, reference("two"));
        backend.found(1, reference("three"));
        assertEquals(1, backend.resolveCalls);
        assertEquals(1, backend.maxConcurrentResolves);

        backend.failResolution(1);
        assertEquals(2, backend.resolveCalls);
        backend.completeResolution(1, "192.0.2.30", 19445, "two");
        assertEquals(3, backend.resolveCalls);
        backend.completeResolution(1, "192.0.2.31", 19445, "three");

        assertEquals(2, controller.getResults().size());
        assertEquals(1, backend.maxConcurrentResolves);
    }

    @Test
    public void duplicateFoundEventIsQueuedOnlyOnce() {
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = controller(backend, new RecordingListener());
        controller.start();
        backend.started(1);
        HyperionDiscoveryBackend.ServiceReference same = reference("same");

        backend.found(1, same);
        backend.found(1, same);

        assertEquals(1, backend.resolveCalls);
    }

    @Test
    public void serviceLostRemovesPendingAndResolvedResults() throws Exception {
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = controller(backend, new RecordingListener());
        controller.start();
        backend.started(1);
        HyperionDiscoveryBackend.ServiceReference first = reference("first");
        HyperionDiscoveryBackend.ServiceReference pending = reference("pending");
        backend.found(1, first);
        backend.found(1, pending);
        backend.completeResolution(1, "192.0.2.32", 19445, "id");
        backend.completeResolution(1, "192.0.2.33", 19445, "pending");
        assertEquals(2, controller.getResults().size());

        backend.lost(1, first);
        assertEquals(1, controller.getResults().size());
    }

    @Test
    public void stopClearsQueueAndIgnoresInFlightResolution() throws Exception {
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = controller(backend, new RecordingListener());
        controller.start();
        backend.started(1);
        backend.found(1, reference("one"));
        backend.found(1, reference("two"));

        assertTrue(controller.stop());
        assertEquals(1, backend.stopCalls);
        backend.completeResolution(1, "192.0.2.34", 19445, "one");
        assertEquals(1, backend.resolveCalls);
        assertTrue(controller.getResults().isEmpty());
        backend.stopped(1);
        assertFalse(controller.isRunning());
    }

    @Test
    public void callbackFromOldGenerationCannotChangeNewRun() throws Exception {
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = controller(backend, new RecordingListener());
        controller.start();
        backend.started(1);
        controller.stop();
        backend.stopped(1);

        assertTrue(controller.start());
        backend.started(2);
        backend.found(1, reference("old"));
        assertEquals(0, backend.resolveCalls);
        backend.found(2, reference("new"));
        backend.completeResolution(2, "192.0.2.35", 19445, "new");
        assertEquals(1, controller.getResults().size());
        assertEquals("new", controller.getResults().get(0).getHyperionId());
    }

    @Test
    public void repeatedStartAndStopAreSafe() {
        FakeBackend backend = new FakeBackend();
        HyperionDiscoveryController controller = controller(backend, new RecordingListener());

        assertTrue(controller.start());
        assertFalse(controller.start());
        assertEquals(1, backend.startCalls);
        assertTrue(controller.stop());
        assertFalse(controller.stop());
        assertEquals(1, backend.stopCalls);
    }

    @Test
    public void startAndStopFailuresBecomeTerminalCallbacks() {
        FakeBackend backend = new FakeBackend();
        RecordingListener listener = new RecordingListener();
        HyperionDiscoveryController controller = controller(backend, listener);
        controller.start();
        backend.startFailed(1, 4);
        assertEquals(1, listener.failures);
        assertEquals(1, listener.stops);
        assertFalse(controller.isRunning());

        controller.start();
        backend.started(2);
        controller.stop();
        backend.stopFailed(2, 0);
        assertEquals(2, listener.failures);
        assertEquals(2, listener.stops);
    }

    private static HyperionDiscoveryController controller(
            FakeBackend backend,
            RecordingListener listener) {
        return new HyperionDiscoveryController(backend, listener, Runnable::run);
    }

    private static HyperionDiscoveryBackend.ServiceReference reference(String key) {
        return new HyperionDiscoveryBackend.ServiceReference(key, "Server " + key);
    }

    private static final class RecordingListener implements HyperionDiscoveryController.Listener {
        private int starts;
        private int failures;
        private int stops;
        private List<DiscoveredHyperionServer> results = new ArrayList<>();

        @Override
        public void onSearchStarted() {
            starts++;
        }

        @Override
        public void onResultsChanged(List<DiscoveredHyperionServer> servers) {
            results = servers;
        }

        @Override
        public void onSearchFailed(int errorCode) {
            failures++;
        }

        @Override
        public void onSearchStopped(List<DiscoveredHyperionServer> servers) {
            stops++;
            results = servers;
        }
    }

    private static final class FakeBackend implements HyperionDiscoveryBackend {
        private DiscoveryCallback discoveryCallback;
        private ResolveCallback resolveCallback;
        private ServiceReference resolving;
        private long resolvingGeneration;
        private int startCalls;
        private int stopCalls;
        private int resolveCalls;
        private int concurrentResolves;
        private int maxConcurrentResolves;

        @Override
        public void start(long generation, DiscoveryCallback callback) {
            startCalls++;
            discoveryCallback = callback;
        }

        @Override
        public void resolve(long generation, ServiceReference service, ResolveCallback callback) {
            if (resolving != null) {
                throw new AssertionError("Resolve calls must be serialized");
            }
            resolveCalls++;
            concurrentResolves++;
            maxConcurrentResolves = Math.max(maxConcurrentResolves, concurrentResolves);
            resolvingGeneration = generation;
            resolving = service;
            resolveCallback = callback;
        }

        @Override
        public void stop(long generation) {
            stopCalls++;
        }

        @Override
        public boolean isRunning() {
            return discoveryCallback != null;
        }

        private void started(long generation) {
            discoveryCallback.onStarted(generation);
        }

        private void found(long generation, ServiceReference reference) {
            discoveryCallback.onServiceFound(generation, reference);
        }

        private void lost(long generation, ServiceReference reference) {
            discoveryCallback.onServiceLost(generation, reference);
        }

        private void startFailed(long generation, int error) {
            discoveryCallback.onStartFailed(generation, error);
        }

        private void stopFailed(long generation, int error) {
            discoveryCallback.onStopFailed(generation, error);
        }

        private void stopped(long generation) {
            discoveryCallback.onStopped(generation);
        }

        private void failResolution(int error) {
            ServiceReference failed = resolving;
            ResolveCallback callback = resolveCallback;
            long generation = resolvingGeneration;
            finishResolve();
            callback.onResolveFailed(generation, failed, error);
        }

        private void completeResolution(
                long generation, String host, int port, String id) throws Exception {
            ServiceReference completed = resolving;
            ResolveCallback callback = resolveCallback;
            finishResolve();
            callback.onResolved(generation, new ResolvedService(
                    completed,
                    InetAddress.getByName(host),
                    port,
                    id.getBytes(StandardCharsets.UTF_8),
                    null));
        }

        private void finishResolve() {
            resolving = null;
            resolveCallback = null;
            concurrentResolves--;
        }
    }
}
