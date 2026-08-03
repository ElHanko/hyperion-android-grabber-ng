package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportConfig;

import java.util.Map;

/** Immutable, environment-derived configuration for the opt-in FlatBuffer server test. */
final class FlatBufferIntegrationTestConfiguration {
    static final String ENABLE_ENV = "HYPERION_FLATBUFFER_INTEGRATION_TEST";
    static final String HOST_ENV = "HYPERION_FLATBUFFER_HOST";
    static final String PORT_ENV = "HYPERION_FLATBUFFER_PORT";
    static final String PRIORITY_ENV = "HYPERION_FLATBUFFER_PRIORITY";
    static final String CONNECT_TIMEOUT_ENV = "HYPERION_FLATBUFFER_CONNECT_TIMEOUT_MS";
    static final String READ_TIMEOUT_ENV = "HYPERION_FLATBUFFER_READ_TIMEOUT_MS";

    static final int DEFAULT_PORT = 19_400;
    static final int DEFAULT_PRIORITY = 190;
    static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    static final int DEFAULT_READ_TIMEOUT_MS = 3_000;
    static final String ORIGIN = "Hyperion Android Grabber NG integration test";

    private final HyperionTransportConfig transportConfig;

    private FlatBufferIntegrationTestConfiguration(HyperionTransportConfig transportConfig) {
        this.transportConfig = transportConfig;
    }

    static boolean isEnabled(Map<String, String> environment) {
        return "1".equals(environment.get(ENABLE_ENV));
    }

    /**
     * Parses an enabled test configuration without resolving a host or opening a connection.
     *
     * @return {@code null} when the exact opt-in value is absent
     * @throws IllegalArgumentException when an explicitly enabled configuration is invalid
     */
    static FlatBufferIntegrationTestConfiguration fromEnvironment(Map<String, String> environment) {
        if (!isEnabled(environment)) {
            return null;
        }

        String host = requiredValue(environment, HOST_ENV);
        int port = optionalInteger(environment, PORT_ENV, DEFAULT_PORT, 1, 65_535, "port");
        int priority = optionalInteger(
                environment, PRIORITY_ENV, DEFAULT_PRIORITY, 100, 199, "priority");
        int connectTimeoutMs = optionalInteger(
                environment,
                CONNECT_TIMEOUT_ENV,
                DEFAULT_CONNECT_TIMEOUT_MS,
                1,
                Integer.MAX_VALUE,
                "connect timeout");
        int readTimeoutMs = optionalInteger(
                environment,
                READ_TIMEOUT_ENV,
                DEFAULT_READ_TIMEOUT_MS,
                1,
                Integer.MAX_VALUE,
                "read timeout");

        return new FlatBufferIntegrationTestConfiguration(new HyperionTransportConfig(
                host,
                port,
                priority,
                ORIGIN,
                connectTimeoutMs,
                readTimeoutMs));
    }

    HyperionTransportConfig transportConfig() {
        return transportConfig;
    }

    private static String requiredValue(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must be set when " + ENABLE_ENV + "=1");
        }
        return value.trim();
    }

    private static int optionalInteger(
            Map<String, String> environment,
            String name,
            int defaultValue,
            int minimum,
            int maximum,
            String description) {
        String value = environment.get(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }

        final int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " must be an integer " + description, failure);
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(
                    name
                            + " must be a "
                            + description
                            + " between "
                            + minimum
                            + " and "
                            + maximum);
        }
        return parsed;
    }
}
