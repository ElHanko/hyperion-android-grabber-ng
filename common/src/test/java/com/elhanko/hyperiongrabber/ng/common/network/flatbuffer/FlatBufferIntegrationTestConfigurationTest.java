package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline contract tests for the FlatBuffer real-server integration-test opt-in. */
public class FlatBufferIntegrationTestConfigurationTest {
    @Test
    public void disabledValuesDoNotEnableOrResolveConfiguration() {
        assertDisabled(null);
        assertDisabled("");
        assertDisabled("0");
        assertDisabled("true");
        assertDisabled("yes");
        assertDisabled("anything-else");
    }

    @Test
    public void exactOneEnablesTheTest() {
        Map<String, String> environment = enabledEnvironment();

        assertTrue(FlatBufferIntegrationTestConfiguration.isEnabled(environment));
    }

    @Test
    public void enabledTestRequiresHostWithHelpfulMessage() {
        Map<String, String> environment = enabledEnvironment();
        environment.remove(FlatBufferIntegrationTestConfiguration.HOST_ENV);

        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.HOST_ENV);
    }

    @Test
    public void enabledTestUsesSafeDefaultsAndFixedOrigin() {
        FlatBufferIntegrationTestConfiguration configuration =
                FlatBufferIntegrationTestConfiguration.fromEnvironment(enabledEnvironment());

        assertEquals(FlatBufferIntegrationTestConfiguration.DEFAULT_PORT,
                configuration.transportConfig().port());
        assertEquals(FlatBufferIntegrationTestConfiguration.DEFAULT_PRIORITY,
                configuration.transportConfig().priority());
        assertEquals(FlatBufferIntegrationTestConfiguration.DEFAULT_CONNECT_TIMEOUT_MS,
                configuration.transportConfig().connectTimeoutMs());
        assertEquals(FlatBufferIntegrationTestConfiguration.DEFAULT_READ_TIMEOUT_MS,
                configuration.transportConfig().readTimeoutMs());
        assertEquals(FlatBufferIntegrationTestConfiguration.ORIGIN,
                configuration.transportConfig().origin());
    }

    @Test
    public void invalidPortIsRejectedWithoutNumberFormatException() {
        Map<String, String> environment = enabledEnvironment();
        environment.put(FlatBufferIntegrationTestConfiguration.PORT_ENV, "invalid");
        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.PORT_ENV);

        environment.put(FlatBufferIntegrationTestConfiguration.PORT_ENV, "65536");
        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.PORT_ENV);
    }

    @Test
    public void invalidPriorityIsRejected() {
        Map<String, String> environment = enabledEnvironment();
        environment.put(FlatBufferIntegrationTestConfiguration.PRIORITY_ENV, "99");

        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.PRIORITY_ENV);
    }

    @Test
    public void invalidTimeoutIsRejected() {
        Map<String, String> environment = enabledEnvironment();
        environment.put(FlatBufferIntegrationTestConfiguration.CONNECT_TIMEOUT_ENV, "0");
        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.CONNECT_TIMEOUT_ENV);

        environment.remove(FlatBufferIntegrationTestConfiguration.CONNECT_TIMEOUT_ENV);
        environment.put(FlatBufferIntegrationTestConfiguration.READ_TIMEOUT_ENV, "not-a-number");
        assertInvalid(environment, FlatBufferIntegrationTestConfiguration.READ_TIMEOUT_ENV);
    }

    @Test
    public void flatBufferOptInDoesNotSetTheProtobufOptIn() {
        Map<String, String> environment = enabledEnvironment();

        assertFalse("FlatBuffer opt-in must not imply the ProtoServer opt-in",
                "1".equals(environment.get("HYPERION_INTEGRATION_TESTS")));
    }

    @Test
    public void disabledTestDoesNotCreateATransport() throws Exception {
        AtomicInteger creationCount = new AtomicInteger();

        assertNull(FlatBufferHyperionIntegrationTest.createTransportIfEnabled(
                new HashMap<>(),
                (type, config) -> {
                    creationCount.incrementAndGet();
                    return null;
                }));
        assertEquals("Disabled FlatBuffer test must not invoke a transport factory",
                0,
                creationCount.get());
    }

    private static void assertDisabled(String value) {
        Map<String, String> environment = new HashMap<>();
        if (value != null) {
            environment.put(FlatBufferIntegrationTestConfiguration.ENABLE_ENV, value);
        }

        assertFalse(FlatBufferIntegrationTestConfiguration.isEnabled(environment));
        assertNull("Disabled test configuration must not resolve host or port",
                FlatBufferIntegrationTestConfiguration.fromEnvironment(environment));
    }

    private static Map<String, String> enabledEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(FlatBufferIntegrationTestConfiguration.ENABLE_ENV, "1");
        environment.put(FlatBufferIntegrationTestConfiguration.HOST_ENV, "test.example.invalid");
        return environment;
    }

    private static void assertInvalid(Map<String, String> environment, String expectedName) {
        try {
            FlatBufferIntegrationTestConfiguration.fromEnvironment(environment);
            fail("Expected invalid integration-test configuration");
        } catch (IllegalArgumentException failure) {
            assertTrue(failure.getMessage().contains(expectedName));
        }
    }
}
