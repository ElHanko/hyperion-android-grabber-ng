package com.elhanko.hyperiongrabber.ng.common.network;

import com.elhanko.hyperiongrabber.ng.common.HyperionScreenService;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransport;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportConfig;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportFactory;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportType;

import java.io.IOException;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Owns one selected Hyperion transport and its single reconnect lifecycle. */
public class HyperionThread extends Thread {
    static final int FRAME_DURATION = -1;

    private final Object lifecycleLock = new Object();
    private final HyperionScreenService.HyperionThreadBroadcaster sender;
    private final HyperionTransportType transportType;
    private final HyperionTransportConfig transportConfig;
    private final long reconnectDelayMs;
    private final TransportCreator transportCreator;

    private volatile boolean reconnectEnabled;
    private volatile boolean hasConnected;
    private volatile boolean stopRequested;
    private volatile HyperionTransport activeTransport;

    private final HyperionThreadListener receiver = new HyperionThreadListener() {
        @Override
        public void sendFrame(byte[] data, int width, int height) {
            if (stopRequested) {
                return;
            }
            HyperionTransport transport = activeTransport;
            if (transport == null) {
                return;
            }
            try {
                // Do not gate on isConnected(): FlatBuffer Clear deliberately becomes not ready,
                // and the next image operation performs its bounded lazy re-registration.
                transport.setImage(data, width, height, FRAME_DURATION);
            } catch (IOException failure) {
                handleTransportFailure(transport, failure);
            }
        }

        @Override
        public void clear() {
            HyperionTransport transport = activeTransport;
            if (transport == null) {
                return;
            }
            try {
                transport.clear();
            } catch (IOException failure) {
                handleTransportFailure(transport, failure);
            }
        }

        @Override
        public void disconnect() {
            shutdown();
        }

        @Override
        public void sendStatus(boolean isGrabbing) {
            sender.onReceiveStatus(isGrabbing);
        }
    };

    public HyperionThread(
            HyperionScreenService.HyperionThreadBroadcaster listener,
            HyperionTransportType transportType,
            HyperionTransportConfig transportConfig,
            boolean reconnect,
            int reconnectDelaySeconds) {
        this(
                listener,
                transportType,
                transportConfig,
                reconnect,
                reconnectDelayMillis(reconnectDelaySeconds),
                new HyperionTransportFactory()::create);
    }

    HyperionThread(
            HyperionScreenService.HyperionThreadBroadcaster listener,
            HyperionTransportType transportType,
            HyperionTransportConfig transportConfig,
            boolean reconnect,
            long reconnectDelayMs,
            TransportCreator transportCreator) {
        if (reconnectDelayMs < 0) {
            throw new IllegalArgumentException("Reconnect delay must not be negative");
        }
        sender = Objects.requireNonNull(listener, "listener");
        this.transportType = transportType == null
                ? HyperionTransportType.DEFAULT
                : transportType;
        this.transportConfig = Objects.requireNonNull(transportConfig, "transportConfig");
        reconnectEnabled = reconnect;
        this.reconnectDelayMs = reconnectDelayMs;
        this.transportCreator = Objects.requireNonNull(transportCreator, "transportCreator");
    }

    public HyperionThreadListener getReceiver() {
        return receiver;
    }

    @Override
    public void run() {
        boolean reconnectAttempt = false;

        while (!stopRequested) {
            if (reconnectAttempt && !awaitReconnectDelay()) {
                return;
            }
            if (stopRequested) {
                return;
            }

            final HyperionTransport candidate;
            try {
                candidate = transportCreator.create(transportType, transportConfig);
                if (candidate == null || !candidate.isConnected()) {
                    closeQuietly(candidate, null);
                    throw new SocketException(
                            transportType.displayName() + " transport was not ready after creation");
                }
            } catch (IOException failure) {
                if (stopRequested) {
                    return;
                }
                sender.onConnectionError(transportType.displayName(), failure);
                if (!hasConnected || !reconnectEnabled) {
                    return;
                }
                reconnectAttempt = true;
                continue;
            }

            synchronized (lifecycleLock) {
                if (stopRequested) {
                    closeQuietly(candidate, null);
                    return;
                }
                activeTransport = candidate;
                hasConnected = true;
                reconnectAttempt = false;
                sender.onConnected(transportType.displayName());

                while (!stopRequested && activeTransport == candidate) {
                    try {
                        lifecycleLock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        stopRequested = true;
                        reconnectEnabled = false;
                    }
                }
                if (stopRequested) {
                    return;
                }
                reconnectAttempt = true;
            }
        }
    }

    /** Prevents any future connection while allowing the encoder to clear before disconnect. */
    public void preventReconnect() {
        synchronized (lifecycleLock) {
            reconnectEnabled = false;
            stopRequested = true;
            lifecycleLock.notifyAll();
        }
    }

    /** Stops reconnect and closes the currently published transport, if any. */
    public void shutdown() {
        HyperionTransport transport;
        synchronized (lifecycleLock) {
            reconnectEnabled = false;
            stopRequested = true;
            transport = activeTransport;
            activeTransport = null;
            lifecycleLock.notifyAll();
        }
        closeQuietly(transport, null);
    }

    private void handleTransportFailure(
            HyperionTransport failedTransport, IOException failure) {
        synchronized (lifecycleLock) {
            if (stopRequested || activeTransport != failedTransport) {
                return;
            }
            activeTransport = null;
            closeQuietly(failedTransport, failure);
            lifecycleLock.notifyAll();
        }
        sender.onConnectionError(transportType.displayName(), failure);
    }

    private boolean awaitReconnectDelay() {
        long remainingNanos = TimeUnit.MILLISECONDS.toNanos(reconnectDelayMs);
        long deadline = System.nanoTime() + remainingNanos;

        synchronized (lifecycleLock) {
            while (!stopRequested && reconnectEnabled && remainingNanos > 0) {
                try {
                    TimeUnit.NANOSECONDS.timedWait(lifecycleLock, remainingNanos);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    reconnectEnabled = false;
                    stopRequested = true;
                    return false;
                }
                remainingNanos = deadline - System.nanoTime();
            }
            return !stopRequested && reconnectEnabled;
        }
    }

    private static long reconnectDelayMillis(int seconds) {
        if (seconds < 0) {
            throw new IllegalArgumentException("Reconnect delay must not be negative");
        }
        return TimeUnit.SECONDS.toMillis(seconds);
    }

    private static void closeQuietly(HyperionTransport transport, IOException failure) {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } catch (IOException closeFailure) {
            if (failure != null) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    interface TransportCreator {
        HyperionTransport create(
                HyperionTransportType type, HyperionTransportConfig config) throws IOException;
    }

    public interface HyperionThreadListener {
        void sendFrame(byte[] data, int width, int height);

        void clear();

        void disconnect();

        void sendStatus(boolean isGrabbing);
    }
}
