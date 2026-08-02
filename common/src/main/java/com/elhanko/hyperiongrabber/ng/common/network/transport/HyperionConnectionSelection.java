package com.elhanko.hyperiongrabber.ng.common.network.transport;

/** Pure preference-to-runtime selection used before the Android service starts a connection. */
public final class HyperionConnectionSelection {
    public static final int DEFAULT_FLATBUFFER_PORT = 19_400;
    public static final String FLATBUFFER_ORIGIN = "Hyperion Android Grabber NG";

    private final HyperionTransportType transportType;
    private final HyperionTransportConfig transportConfig;

    private HyperionConnectionSelection(
            HyperionTransportType transportType, HyperionTransportConfig transportConfig) {
        this.transportType = transportType;
        this.transportConfig = transportConfig;
    }

    /**
     * Resolves raw stored values without reading or modifying preferences.
     *
     * <p>Only the selected transport's port is parsed. A missing FlatBuffer port uses the
     * internal manual default; a missing Protocol Buffers port remains invalid.</p>
     */
    public static HyperionConnectionSelection resolve(
            String persistedTransport,
            String host,
            String protobufPort,
            String flatBufferPort,
            String priority,
            int connectTimeoutMs,
            int readTimeoutMs) {
        HyperionTransportType type =
                HyperionTransportType.fromPersistedValue(persistedTransport);
        if ("0.0.0.0".equals(host)) {
            throw new IllegalArgumentException("Hyperion host must not be the unspecified address");
        }
        String selectedPort = type == HyperionTransportType.FLATBUFFER
                ? defaultFlatBufferPort(flatBufferPort)
                : protobufPort;
        int port = parseRequiredInt(selectedPort, "Selected Hyperion port");
        int parsedPriority = parseRequiredInt(priority, "Hyperion priority");
        String origin = type == HyperionTransportType.FLATBUFFER ? FLATBUFFER_ORIGIN : null;

        HyperionTransportConfig config = new HyperionTransportConfig(
                host,
                port,
                parsedPriority,
                origin,
                connectTimeoutMs,
                readTimeoutMs);
        return new HyperionConnectionSelection(type, config);
    }

    public HyperionTransportType transportType() {
        return transportType;
    }

    public HyperionTransportConfig transportConfig() {
        return transportConfig;
    }

    public String transportName() {
        return transportType.displayName();
    }

    private static String defaultFlatBufferPort(String value) {
        return value == null || value.trim().isEmpty()
                ? Integer.toString(DEFAULT_FLATBUFFER_PORT)
                : value;
    }

    private static int parseRequiredInt(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(label + " must be a decimal integer", failure);
        }
    }
}
