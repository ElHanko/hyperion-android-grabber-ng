package com.elhanko.hyperiongrabber.ng.common.network.transport;

/** Immutable connection configuration shared by Hyperion transport constructors. */
public final class HyperionTransportConfig {
    private final String host;
    private final int port;
    private final int priority;
    private final String origin;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HyperionTransportConfig(
            String host,
            int port,
            int priority,
            String origin,
            int connectTimeoutMs,
            int readTimeoutMs) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be null or empty");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (priority < 100 || priority > 199) {
            throw new IllegalArgumentException("Priority must be between 100 and 199");
        }
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }

        this.host = host;
        this.port = port;
        this.priority = priority;
        this.origin = origin;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int priority() {
        return priority;
    }

    public String origin() {
        return origin;
    }

    public int connectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int readTimeoutMs() {
        return readTimeoutMs;
    }

    void requireFlatBufferOrigin() {
        if (origin == null || origin.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Origin must not be null or empty for FlatBuffer transport");
        }
    }
}
