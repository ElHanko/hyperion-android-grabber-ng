package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransport;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportConfig;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportFactory;
import com.elhanko.hyperiongrabber.ng.common.network.transport.HyperionTransportType;

import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * Explicitly enabled integration check for a real Hyperion NG 2.2.1 FlatBuffer server.
 *
 * <p>The test never resolves a target or opens a socket unless
 * {@code HYPERION_FLATBUFFER_INTEGRATION_TEST=1}. Once enabled, invalid configuration and all
 * server failures are test failures. The selected factory type is always FlatBuffer; this test
 * deliberately has no Protocol Buffers fallback.</p>
 */
public class FlatBufferHyperionIntegrationTest {
    private static final int REQUEST_DURATION_MS = 250;
    private static final byte[] RGB24_IMAGE = {
            0x10, 0x20, 0x30,
            0x18, 0x28, 0x38,
            0x20, 0x30, 0x40,
            0x28, 0x38, 0x48
    };
    private static final byte[] RGB32_IMAGE = {
            0x10, 0x20, 0x30, 0x40,
            0x18, 0x28, 0x38, 0x48,
            0x20, 0x30, 0x40, 0x50,
            0x28, 0x38, 0x48, 0x58
    };

    @Test
    public void exercisesFactoryFlatBufferTransportAgainstOptInServer() throws Throwable {
        Map<String, String> environment = System.getenv();
        Assume.assumeTrue(
                "Real Hyperion FlatBuffer integration test is not enabled",
                FlatBufferIntegrationTestConfiguration.isEnabled(environment));

        FlatBufferIntegrationTestConfiguration integrationConfiguration =
                FlatBufferIntegrationTestConfiguration.fromEnvironment(environment);
        assertNotNull("Enabled integration test must have a configuration", integrationConfiguration);
        HyperionTransportConfig config = integrationConfiguration.transportConfig();
        HyperionTransport transport = null;
        Throwable primaryFailure = null;
        Throwable cleanupFailure = null;
        boolean registered = false;
        boolean colorAccepted = false;
        boolean rgb24Accepted = false;
        boolean rgb32Accepted = false;
        boolean clearAccepted = false;
        boolean reRegistered = false;
        boolean priorityCleared = false;

        try {
            transport = createTransportIfEnabled(
                    environment,
                    (type, transportConfig) -> new HyperionTransportFactory().create(
                            type, transportConfig));
            assertNotNull("Enabled integration test must create one FlatBuffer transport", transport);
            assertEquals("FlatBuffer (experimental)", transport.transportName());
            assertTrue("FlatBuffer register handshake must leave the transport ready",
                    transport.isConnected());
            registered = true;

            transport.setColor(0x102030, REQUEST_DURATION_MS);
            assertTrue("FlatBuffer transport must remain ready after COLOR", transport.isConnected());
            colorAccepted = true;

            transport.setImage(RGB24_IMAGE, 2, 2, REQUEST_DURATION_MS);
            assertTrue("FlatBuffer transport must remain ready after RGB24 IMAGE",
                    transport.isConnected());
            rgb24Accepted = true;

            transport.setImage(RGB32_IMAGE, 2, 2, REQUEST_DURATION_MS);
            assertTrue("FlatBuffer transport must remain ready after RGB32 IMAGE",
                    transport.isConnected());
            rgb32Accepted = true;

            transport.clear();
            assertFalse("Own-priority CLEAR must make the transport temporarily unready",
                    transport.isConnected());
            clearAccepted = true;

            transport.setColor(0x102030, REQUEST_DURATION_MS);
            assertTrue("FlatBuffer transport must re-register after CLEAR", transport.isConnected());
            reRegistered = true;
        } catch (Throwable failure) {
            primaryFailure = failure;
        } finally {
            if (transport != null) {
                try {
                    transport.clear();
                    priorityCleared = true;
                } catch (Throwable failure) {
                    cleanupFailure = failure;
                }
                try {
                    transport.close();
                } catch (Throwable failure) {
                    cleanupFailure = appendFailure(cleanupFailure, failure);
                }
            }

            System.out.printf(
                    "FlatBuffer integration: port=%d transport=%s register=%s color=%s "
                            + "rgb24=%s rgb32=%s clear=%s reRegister=%s priorityCleared=%s "
                            + "fallback=none%n",
                    config.port(),
                    HyperionTransportType.FLATBUFFER,
                    registered,
                    colorAccepted,
                    rgb24Accepted,
                    rgb32Accepted,
                    clearAccepted,
                    reRegistered,
                    priorityCleared);
        }

        if (primaryFailure != null) {
            if (cleanupFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
            throw primaryFailure;
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
        assertTrue("Integration test priority was not cleared", priorityCleared);
    }

    private static Throwable appendFailure(Throwable current, Throwable next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    static HyperionTransport createTransportIfEnabled(
            Map<String, String> environment, TransportCreator creator) throws IOException {
        FlatBufferIntegrationTestConfiguration configuration =
                FlatBufferIntegrationTestConfiguration.fromEnvironment(environment);
        if (configuration == null) {
            return null;
        }
        return creator.create(HyperionTransportType.FLATBUFFER, configuration.transportConfig());
    }

    interface TransportCreator {
        HyperionTransport create(HyperionTransportType type, HyperionTransportConfig config)
                throws IOException;
    }
}
