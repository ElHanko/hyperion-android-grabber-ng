package com.elhanko.hyperiongrabber.ng.common.network.transport;

/**
 * Maps the non-persistent settings control to the stable stored transport value.
 *
 * <p>This class does not read or write preferences. The caller decides when a deliberate user
 * action is committed.</p>
 */
public final class HyperionTransportPreferenceBinding {
    public static final int DEFAULT_FLATBUFFER_PORT =
            HyperionConnectionSelection.DEFAULT_FLATBUFFER_PORT;

    private HyperionTransportPreferenceBinding() {
    }

    /** Returns whether the persisted value represents an explicit FlatBuffer opt-in. */
    public static boolean isFlatBufferEnabled(String persistedTransport) {
        return HyperionTransportType.fromPersistedValue(persistedTransport)
                == HyperionTransportType.FLATBUFFER;
    }

    /** Returns the only value that the visible transport control may persist. */
    public static String persistedValue(boolean flatBufferEnabled) {
        return flatBufferEnabled
                ? HyperionTransportType.FLATBUFFER.persistedValue()
                : HyperionTransportType.PROTOBUF.persistedValue();
    }

    /** Uses the manual FlatBuffer default without storing it. */
    public static String flatBufferPortOrDefault(String persistedPort) {
        return persistedPort == null || persistedPort.trim().isEmpty()
                ? Integer.toString(DEFAULT_FLATBUFFER_PORT)
                : persistedPort;
    }

    /** Returns whether an entered FlatBuffer port is a decimal TCP port in the valid range. */
    public static boolean isValidPort(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65_535;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /** Parses a valid FlatBuffer port without applying a corrective default. */
    public static int requireValidPort(String value) {
        if (!isValidPort(value)) {
            throw new IllegalArgumentException("FlatBuffer port must be in range 1..65535");
        }
        return Integer.parseInt(value);
    }
}
