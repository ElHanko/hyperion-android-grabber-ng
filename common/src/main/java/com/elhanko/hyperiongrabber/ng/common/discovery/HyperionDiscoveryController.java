package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Owns one discovery generation and serializes all legacy NSD resolve requests. */
public final class HyperionDiscoveryController {
    public interface Listener {
        void onSearchStarted();

        void onResultsChanged(@NonNull List<DiscoveredHyperionServer> servers);

        void onSearchFailed(int errorCode);

        void onSearchStopped(@NonNull List<DiscoveredHyperionServer> servers);
    }

    public interface CallbackDispatcher {
        void dispatch(@NonNull Runnable callback);
    }

    private enum State {
        IDLE,
        STARTING,
        RUNNING,
        STOPPING,
        CLOSED
    }

    private final HyperionDiscoveryBackend backend;
    private final HyperionServerStore serverStore;
    private final CallbackDispatcher callbackDispatcher;
    private final Deque<HyperionDiscoveryBackend.ServiceReference> resolveQueue =
            new ArrayDeque<>();
    private final Set<String> queuedKeys = new HashSet<>();
    private final Set<String> foundKeys = new HashSet<>();
    private final HyperionDiscoveryBackend.DiscoveryCallback discoveryCallback =
            new BackendDiscoveryCallback();
    private final HyperionDiscoveryBackend.ResolveCallback resolveCallback =
            new BackendResolveCallback();

    private Listener listener;
    private State state = State.IDLE;
    private long generation;
    private HyperionDiscoveryBackend.ServiceReference resolving;

    public HyperionDiscoveryController(
            @NonNull HyperionDiscoveryBackend backend,
            @NonNull Listener listener,
            @NonNull CallbackDispatcher callbackDispatcher) {
        this(backend, listener, callbackDispatcher, new HyperionServerStore());
    }

    HyperionDiscoveryController(
            @NonNull HyperionDiscoveryBackend backend,
            @NonNull Listener listener,
            @NonNull CallbackDispatcher callbackDispatcher,
            @NonNull HyperionServerStore serverStore) {
        this.backend = backend;
        this.listener = listener;
        this.callbackDispatcher = callbackDispatcher;
        this.serverStore = serverStore;
    }

    public synchronized boolean start() {
        if (state != State.IDLE) {
            return false;
        }

        generation++;
        state = State.STARTING;
        resolving = null;
        resolveQueue.clear();
        queuedKeys.clear();
        foundKeys.clear();
        serverStore.clear();

        try {
            backend.start(generation, discoveryCallback);
        } catch (RuntimeException error) {
            state = State.IDLE;
            notifyFailure(0);
            notifyStopped();
        }
        return true;
    }

    public synchronized boolean stop() {
        if (state == State.IDLE || state == State.STOPPING || state == State.CLOSED) {
            return false;
        }

        state = State.STOPPING;
        clearResolveWork();
        try {
            backend.stop(generation);
        } catch (RuntimeException error) {
            state = State.IDLE;
            notifyFailure(0);
            notifyStopped();
        }
        return true;
    }

    public synchronized boolean isRunning() {
        return state == State.STARTING || state == State.RUNNING;
    }

    @NonNull
    public synchronized List<DiscoveredHyperionServer> getResults() {
        return serverStore.snapshot();
    }

    public synchronized void close() {
        if (state != State.CLOSED) {
            if (state != State.IDLE && state != State.STOPPING) {
                try {
                    backend.stop(generation);
                } catch (RuntimeException ignored) {
                    // The controller is closing and the platform backend already releases resources.
                }
            }
            clearResolveWork();
            state = State.CLOSED;
            listener = null;
        }
    }

    private synchronized void enqueue(HyperionDiscoveryBackend.ServiceReference service) {
        if (state != State.RUNNING || !foundKeys.contains(service.getDiscoveryKey())) {
            return;
        }
        String key = service.getDiscoveryKey();
        if ((resolving != null && resolving.getDiscoveryKey().equals(key)) || !queuedKeys.add(key)) {
            return;
        }
        resolveQueue.addLast(service);
        resolveNext();
    }

    private synchronized void resolveNext() {
        if (state != State.RUNNING || resolving != null) {
            return;
        }
        while (!resolveQueue.isEmpty()) {
            HyperionDiscoveryBackend.ServiceReference next = resolveQueue.removeFirst();
            queuedKeys.remove(next.getDiscoveryKey());
            if (!foundKeys.contains(next.getDiscoveryKey())) {
                continue;
            }
            resolving = next;
            try {
                backend.resolve(generation, next, resolveCallback);
            } catch (RuntimeException error) {
                resolving = null;
                continue;
            }
            return;
        }
    }

    private void removeQueued(String discoveryKey) {
        Iterator<HyperionDiscoveryBackend.ServiceReference> iterator = resolveQueue.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getDiscoveryKey().equals(discoveryKey)) {
                iterator.remove();
            }
        }
        queuedKeys.remove(discoveryKey);
    }

    private void clearResolveWork() {
        resolveQueue.clear();
        queuedKeys.clear();
        foundKeys.clear();
        resolving = null;
    }

    private boolean accepts(long callbackGeneration) {
        return callbackGeneration == generation && state != State.IDLE && state != State.CLOSED;
    }

    private void notifyStarted() {
        Listener current = listener;
        if (current != null) {
            callbackDispatcher.dispatch(() -> {
                Listener target;
                synchronized (HyperionDiscoveryController.this) {
                    target = listener;
                }
                if (target != null) {
                    target.onSearchStarted();
                }
            });
        }
    }

    private void notifyResultsChanged() {
        List<DiscoveredHyperionServer> snapshot = serverStore.snapshot();
        Listener current = listener;
        if (current != null) {
            callbackDispatcher.dispatch(() -> {
                Listener target;
                synchronized (HyperionDiscoveryController.this) {
                    target = listener;
                }
                if (target != null) {
                    target.onResultsChanged(snapshot);
                }
            });
        }
    }

    private void notifyFailure(int errorCode) {
        Listener current = listener;
        if (current != null) {
            callbackDispatcher.dispatch(() -> {
                Listener target;
                synchronized (HyperionDiscoveryController.this) {
                    target = listener;
                }
                if (target != null) {
                    target.onSearchFailed(errorCode);
                }
            });
        }
    }

    private void notifyStopped() {
        List<DiscoveredHyperionServer> snapshot = serverStore.snapshot();
        Listener current = listener;
        if (current != null) {
            callbackDispatcher.dispatch(() -> {
                Listener target;
                synchronized (HyperionDiscoveryController.this) {
                    target = listener;
                }
                if (target != null) {
                    target.onSearchStopped(snapshot);
                }
            });
        }
    }

    private final class BackendDiscoveryCallback implements HyperionDiscoveryBackend.DiscoveryCallback {
        @Override
        public synchronized void onStarted(long callbackGeneration) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration)) {
                    return;
                }
                if (state == State.STOPPING) {
                    backend.stop(callbackGeneration);
                    return;
                }
                state = State.RUNNING;
                notifyStarted();
            }
        }

        @Override
        public void onServiceFound(
                long callbackGeneration,
                @NonNull HyperionDiscoveryBackend.ServiceReference service) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration) || state != State.RUNNING) {
                    return;
                }
                foundKeys.add(service.getDiscoveryKey());
                enqueue(service);
            }
        }

        @Override
        public void onServiceLost(
                long callbackGeneration,
                @NonNull HyperionDiscoveryBackend.ServiceReference service) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration) || state != State.RUNNING) {
                    return;
                }
                String key = service.getDiscoveryKey();
                foundKeys.remove(key);
                removeQueued(key);
                if (serverStore.remove(key)) {
                    notifyResultsChanged();
                }
            }
        }

        @Override
        public void onStartFailed(long callbackGeneration, int errorCode) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration)) {
                    return;
                }
                clearResolveWork();
                state = State.IDLE;
                notifyFailure(errorCode);
                notifyStopped();
            }
        }

        @Override
        public void onStopFailed(long callbackGeneration, int errorCode) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration)) {
                    return;
                }
                clearResolveWork();
                state = State.IDLE;
                notifyFailure(errorCode);
                notifyStopped();
            }
        }

        @Override
        public void onStopped(long callbackGeneration) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration)) {
                    return;
                }
                clearResolveWork();
                state = State.IDLE;
                notifyStopped();
            }
        }
    }

    private final class BackendResolveCallback implements HyperionDiscoveryBackend.ResolveCallback {
        @Override
        public void onResolved(
                long callbackGeneration,
                @NonNull HyperionDiscoveryBackend.ResolvedService service) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration) || state != State.RUNNING) {
                    return;
                }
                String key = service.getReference().getDiscoveryKey();
                if (resolving == null || !resolving.getDiscoveryKey().equals(key)) {
                    return;
                }
                resolving = null;
                if (foundKeys.contains(key) && serverStore.put(service)) {
                    notifyResultsChanged();
                }
                resolveNext();
            }
        }

        @Override
        public void onResolveFailed(
                long callbackGeneration,
                @NonNull HyperionDiscoveryBackend.ServiceReference service,
                int errorCode) {
            synchronized (HyperionDiscoveryController.this) {
                if (!accepts(callbackGeneration) || state != State.RUNNING) {
                    return;
                }
                if (resolving != null
                        && resolving.getDiscoveryKey().equals(service.getDiscoveryKey())) {
                    resolving = null;
                }
                resolveNext();
            }
        }
    }
}
