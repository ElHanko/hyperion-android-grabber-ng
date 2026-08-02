package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.util.Arrays;

/** Small platform boundary used by the discovery state machine and its offline tests. */
public interface HyperionDiscoveryBackend {
    void start(long generation, @NonNull DiscoveryCallback callback);

    void resolve(
            long generation,
            @NonNull ServiceReference service,
            @NonNull ResolveCallback callback);

    void stop(long generation);

    boolean isRunning();

    interface DiscoveryCallback {
        void onStarted(long generation);

        void onServiceFound(long generation, @NonNull ServiceReference service);

        void onServiceLost(long generation, @NonNull ServiceReference service);

        void onStartFailed(long generation, int errorCode);

        void onStopFailed(long generation, int errorCode);

        void onStopped(long generation);
    }

    interface ResolveCallback {
        void onResolved(long generation, @NonNull ResolvedService service);

        void onResolveFailed(long generation, @NonNull ServiceReference service, int errorCode);
    }

    final class ServiceReference {
        private final String discoveryKey;
        private final String serviceName;

        public ServiceReference(@NonNull String discoveryKey, @NonNull String serviceName) {
            this.discoveryKey = discoveryKey;
            this.serviceName = serviceName;
        }

        @NonNull
        public String getDiscoveryKey() {
            return discoveryKey;
        }

        @NonNull
        public String getServiceName() {
            return serviceName;
        }
    }

    final class ResolvedService {
        private final ServiceReference reference;
        private final InetAddress host;
        private final int port;
        private final byte[] hyperionId;
        private final byte[] hyperionVersion;

        public ResolvedService(
                @NonNull ServiceReference reference,
                @Nullable InetAddress host,
                int port,
                @Nullable byte[] hyperionId,
                @Nullable byte[] hyperionVersion) {
            this.reference = reference;
            this.host = host;
            this.port = port;
            this.hyperionId = copy(hyperionId);
            this.hyperionVersion = copy(hyperionVersion);
        }

        @NonNull
        public ServiceReference getReference() {
            return reference;
        }

        @Nullable
        public InetAddress getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        @Nullable
        public byte[] getHyperionId() {
            return copy(hyperionId);
        }

        @Nullable
        public byte[] getHyperionVersion() {
            return copy(hyperionVersion);
        }

        private static byte[] copy(byte[] source) {
            return source == null ? null : Arrays.copyOf(source, source.length);
        }
    }
}
