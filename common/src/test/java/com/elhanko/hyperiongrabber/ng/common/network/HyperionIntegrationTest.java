package com.elhanko.hyperiongrabber.ng.common.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionReply;

import org.junit.Assume;
import org.junit.Test;

/**
 * Explicitly enabled, non-blocking integration check for a real Hyperion server.
 *
 * <p>The regular unit test run never contacts a server unless
 * {@code HYPERION_INTEGRATION_TESTS=1}. Missing configuration, an unreachable server, and an
 * integration failure are reported as a skipped test so they cannot fail the normal build.</p>
 */
public class HyperionIntegrationTest {
    private static final String ENABLE_ENV = "HYPERION_INTEGRATION_TESTS";
    private static final String HOST_ENV = "HYPERION_TEST_HOST";
    private static final String PORT_ENV = "HYPERION_TEST_PORT";
    private static final String PRIORITY_ENV = "HYPERION_TEST_PRIORITY";

    private static final int REQUIRED_PRIORITY = 199;
    private static final int REQUEST_DURATION_MS = 500;
    private static final int CONNECT_TIMEOUT_MS = 1_000;
    private static final int READ_TIMEOUT_MS = 2_000;
    private static final byte[] TEST_IMAGE_RGB = {
            0, 0, 0,
            0, 0, 0,
            0, 0, 0,
            0, 0, 0
    };

    @Test
    public void sendsShortColorAndRgbImageToOptInServer() {
        Assume.assumeTrue(
                "Real Hyperion integration test is not enabled",
                "1".equals(System.getenv(ENABLE_ENV)));

        String host = requiredEnvironment(HOST_ENV);
        int port = requiredIntegerEnvironment(PORT_ENV);
        int priority = requiredIntegerEnvironment(PRIORITY_ENV);
        Assume.assumeTrue("Hyperion integration port must be between 1 and 65535",
                port >= 1 && port <= 65_535);
        Assume.assumeTrue("Hyperion integration priority must be exactly 199",
                priority == REQUIRED_PRIORITY);

        Hyperion client = null;
        Throwable testFailure = null;
        Throwable cleanupFailure = null;
        boolean reachable = false;
        boolean priorityCleared = false;
        HyperionReply colorReply = null;
        HyperionReply imageReply = null;

        try {
            client = new Hyperion(host, port, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
            reachable = true;

            colorReply = client.sendRequest(
                    Hyperion.setColorRequest(0x000000, priority, REQUEST_DURATION_MS));
            assertSuccessfulReply("COLOR", colorReply);

            imageReply = client.sendRequest(
                    Hyperion.setImageRequest(
                            TEST_IMAGE_RGB,
                            2,
                            2,
                            priority,
                            REQUEST_DURATION_MS));
            assertSuccessfulReply("IMAGE", imageReply);
        } catch (Throwable failure) {
            testFailure = failure;
        } finally {
            if (client != null) {
                try {
                    HyperionReply clearReply = client.sendRequest(
                            Hyperion.clearRequest(priority));
                    assertSuccessfulReply("CLEAR", clearReply);
                    priorityCleared = true;
                } catch (Throwable failure) {
                    cleanupFailure = failure;
                } finally {
                    try {
                        client.close();
                    } catch (Throwable failure) {
                        if (cleanupFailure == null) {
                            cleanupFailure = failure;
                        } else {
                            cleanupFailure.addSuppressed(failure);
                        }
                    }
                }
            }

            System.out.printf(
                    "Hyperion integration: reachable=%s port=%d requests=COLOR,IMAGE,CLEAR "
                            + "colorReply=%s imageReply=%s priorityCleared=%s%n",
                    reachable,
                    port,
                    describeReply(colorReply),
                    describeReply(imageReply),
                    priorityCleared);
        }

        if (testFailure != null && cleanupFailure != null) {
            testFailure.addSuppressed(cleanupFailure);
        } else if (testFailure == null) {
            testFailure = cleanupFailure;
        }

        Assume.assumeNoException(
                "Optional Hyperion integration test did not complete successfully",
                testFailure);
        assertTrue("Test priority was not cleared", priorityCleared);
    }

    private static void assertSuccessfulReply(String request, HyperionReply reply) {
        assertEquals(request + " reply type", HyperionReply.Type.REPLY, reply.getType());
        assertTrue(request + " reply must contain success", reply.hasSuccess());
        assertTrue(request + " reply must be successful", reply.getSuccess());
    }

    private static String describeReply(HyperionReply reply) {
        if (reply == null) {
            return "none";
        }
        return reply.getType() + "/success=" + reply.getSuccess();
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        Assume.assumeTrue(name + " must be set", value != null && !value.trim().isEmpty());
        return value.trim();
    }

    private static int requiredIntegerEnvironment(String name) {
        String value = requiredEnvironment(name);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            Assume.assumeTrue(name + " must be an integer", false);
            return -1;
        }
    }
}
