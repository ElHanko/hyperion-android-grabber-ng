package com.elhanko.hyperiongrabber.ng.common.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Android DNS-SD backend for the Hyperion Protocol Buffers service only. */
public final class AndroidNsdDiscoveryBackend implements HyperionDiscoveryBackend {
    public static final String PROTO_SERVER_SERVICE_TYPE = "_hyperiond-protobuf._tcp.";

    private final NsdManager nsdManager;
    private final WifiManager wifiManager;
    private final Handler mainHandler;
    private Session session;

    public AndroidNsdDiscoveryBackend(@NonNull Context context) {
        Context applicationContext = context.getApplicationContext();
        nsdManager = (NsdManager) applicationContext.getSystemService(Context.NSD_SERVICE);
        wifiManager = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        if (nsdManager == null) {
            throw new IllegalStateException("Android network service discovery is unavailable");
        }
    }

    @Override
    public synchronized void start(long generation, @NonNull DiscoveryCallback callback) {
        if (session != null) {
            throw new IllegalStateException("A discovery session is already active");
        }

        Session newSession = new Session(generation, callback);
        session = newSession;
        try {
            acquireMulticastLock(newSession);
            nsdManager.discoverServices(
                    PROTO_SERVER_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    newSession.discoveryListener);
        } catch (RuntimeException error) {
            releaseMulticastLock(newSession);
            session = null;
            throw error;
        }
    }

    @Override
    public synchronized void resolve(
            long generation,
            @NonNull ServiceReference service,
            @NonNull ResolveCallback callback) {
        Session current = session;
        if (current == null || current.generation != generation || current.stopRequested) {
            callback.onResolveFailed(generation, service, NsdManager.FAILURE_INTERNAL_ERROR);
            return;
        }

        NsdServiceInfo serviceInfo = current.services.get(service.getDiscoveryKey());
        if (serviceInfo == null) {
            callback.onResolveFailed(generation, service, NsdManager.FAILURE_INTERNAL_ERROR);
            return;
        }

        try {
            nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo failedService, int errorCode) {
                    mainHandler.post(() -> callback.onResolveFailed(generation, service, errorCode));
                }

                @Override
                public void onServiceResolved(NsdServiceInfo resolvedService) {
                    Map<String, byte[]> attributes = resolvedService.getAttributes();
                    byte[] id = copyAttribute(attributes, "id");
                    byte[] version = copyAttribute(attributes, "version");
                    ResolvedService resolved = new ResolvedService(
                            service,
                            resolvedService.getHost(),
                            resolvedService.getPort(),
                            id,
                            version);
                    mainHandler.post(() -> callback.onResolved(generation, resolved));
                }
            });
        } catch (RuntimeException error) {
            callback.onResolveFailed(generation, service, NsdManager.FAILURE_INTERNAL_ERROR);
        }
    }

    @Override
    public synchronized void stop(long generation) {
        Session current = session;
        if (current == null || current.generation != generation || current.stopRequested) {
            return;
        }

        current.stopRequested = true;
        releaseMulticastLock(current);
        requestPlatformStop(current);
    }

    private void requestPlatformStop(Session current) {
        try {
            nsdManager.stopServiceDiscovery(current.discoveryListener);
        } catch (RuntimeException error) {
            if (current.started) {
                finishSession(current);
                mainHandler.post(() -> current.callback.onStopFailed(
                        current.generation,
                        NsdManager.FAILURE_INTERNAL_ERROR));
            }
        }
    }

    @Override
    public synchronized boolean isRunning() {
        return session != null && !session.stopRequested;
    }

    private void acquireMulticastLock(Session target) {
        if (wifiManager == null) {
            return;
        }
        WifiManager.MulticastLock lock =
                wifiManager.createMulticastLock("HyperionGrabberNG.Discovery");
        lock.setReferenceCounted(false);
        lock.acquire();
        target.multicastLock = lock;
    }

    private static void releaseMulticastLock(Session target) {
        WifiManager.MulticastLock lock = target.multicastLock;
        target.multicastLock = null;
        if (lock != null && lock.isHeld()) {
            try {
                lock.release();
            } catch (RuntimeException ignored) {
                // The lock is non-reference-counted and release remains best-effort on OEM builds.
            }
        }
    }

    private synchronized void finishSession(Session target) {
        releaseMulticastLock(target);
        target.services.clear();
        if (session == target) {
            session = null;
        }
    }

    private static String discoveryKey(NsdServiceInfo serviceInfo) {
        return serviceInfo.getServiceType().toLowerCase(Locale.ROOT)
                + '\u0000'
                + serviceInfo.getServiceName().toLowerCase(Locale.ROOT);
    }

    private static boolean isProtoServer(NsdServiceInfo serviceInfo) {
        String type = serviceInfo.getServiceType();
        if (type == null) {
            return false;
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".local.")) {
            normalized = normalized.substring(0, normalized.length() - ".local.".length()) + ".";
        } else if (!normalized.endsWith(".")) {
            normalized += ".";
        }
        return PROTO_SERVER_SERVICE_TYPE.equals(normalized);
    }

    @Nullable
    private static byte[] copyAttribute(Map<String, byte[]> attributes, String key) {
        if (attributes == null) {
            return null;
        }
        byte[] value = attributes.get(key);
        return value == null ? null : value.clone();
    }

    private final class Session {
        private final long generation;
        private final DiscoveryCallback callback;
        private final Map<String, NsdServiceInfo> services = new HashMap<>();
        private final NsdManager.DiscoveryListener discoveryListener;
        private WifiManager.MulticastLock multicastLock;
        private boolean started;
        private boolean stopRequested;

        private Session(long generation, DiscoveryCallback callback) {
            this.generation = generation;
            this.callback = callback;
            discoveryListener = new NsdManager.DiscoveryListener() {
                @Override
                public void onDiscoveryStarted(String serviceType) {
                    mainHandler.post(() -> {
                        boolean shouldStop;
                        synchronized (AndroidNsdDiscoveryBackend.this) {
                            if (session != Session.this) {
                                return;
                            }
                            started = true;
                            shouldStop = stopRequested;
                        }
                        if (shouldStop) {
                            requestPlatformStop(Session.this);
                        } else {
                            callback.onStarted(generation);
                        }
                    });
                }

                @Override
                public void onServiceFound(NsdServiceInfo serviceInfo) {
                    if (!isProtoServer(serviceInfo)) {
                        return;
                    }
                    String key = discoveryKey(serviceInfo);
                    ServiceReference reference =
                            new ServiceReference(key, serviceInfo.getServiceName());
                    synchronized (AndroidNsdDiscoveryBackend.this) {
                        if (session != Session.this || stopRequested) {
                            return;
                        }
                        services.put(key, serviceInfo);
                    }
                    mainHandler.post(() -> callback.onServiceFound(generation, reference));
                }

                @Override
                public void onServiceLost(NsdServiceInfo serviceInfo) {
                    if (!isProtoServer(serviceInfo)) {
                        return;
                    }
                    String key = discoveryKey(serviceInfo);
                    ServiceReference reference =
                            new ServiceReference(key, serviceInfo.getServiceName());
                    synchronized (AndroidNsdDiscoveryBackend.this) {
                        services.remove(key);
                    }
                    mainHandler.post(() -> callback.onServiceLost(generation, reference));
                }

                @Override
                public void onDiscoveryStopped(String serviceType) {
                    finishSession(Session.this);
                    mainHandler.post(() -> callback.onStopped(generation));
                }

                @Override
                public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                    finishSession(Session.this);
                    mainHandler.post(() -> callback.onStartFailed(generation, errorCode));
                }

                @Override
                public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                    finishSession(Session.this);
                    mainHandler.post(() -> callback.onStopFailed(generation, errorCode));
                }
            };
        }
    }
}
