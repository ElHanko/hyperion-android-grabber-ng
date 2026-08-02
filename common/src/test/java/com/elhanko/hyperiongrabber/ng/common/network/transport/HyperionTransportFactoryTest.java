package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HyperionTransportFactoryTest {
    @Test
    public void nullTypeUsesProtobufWithoutProbingAnotherTransport() throws Exception {
        try (HyperionTransportTestServer server = new HyperionTransportTestServer(
                HyperionTransportTestServer.Connection::waitForClientClose);
                HyperionTransport transport = new HyperionTransportFactory().create(
                        null, server.config(null))) {
            assertTrue(transport instanceof ProtobufHyperionTransport);
        }
    }

    @Test
    public void failedProtobufConnectionRemainsIOExceptionAndDoesNotTryFlatBuffer()
            throws Exception {
        HyperionTransportConfig config;
        try (ServerSocket reserved = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            config = new HyperionTransportConfig(
                    reserved.getInetAddress().getHostAddress(),
                    reserved.getLocalPort(),
                    HyperionTransportTestServer.PRIORITY,
                    null,
                    200,
                    200);
        }

        assertThrows(IOException.class,
                () -> new HyperionTransportFactory().create(
                        HyperionTransportType.PROTOBUF, config));
    }

    @Test
    public void flatBufferServerFailureUsesOneConnectionWithoutProtobufFallback()
            throws Exception {
        try (CountingFlatBufferFailureServer server =
                     new CountingFlatBufferFailureServer(FailureMode.SERVER_ERROR)) {
            assertThrows(HyperionServerException.class,
                    () -> new HyperionTransportFactory().create(
                            HyperionTransportType.FLATBUFFER, server.config(300)));
            assertEquals(1, server.awaitConnectionCount());
        }
    }

    @Test
    public void wrongFlatBufferRegistrationUsesOneConnectionWithoutFallback()
            throws Exception {
        try (CountingFlatBufferFailureServer server =
                     new CountingFlatBufferFailureServer(FailureMode.WRONG_REGISTRATION)) {
            assertThrows(HyperionProtocolException.class,
                    () -> new HyperionTransportFactory().create(
                            HyperionTransportType.FLATBUFFER, server.config(300)));
            assertEquals(1, server.awaitConnectionCount());
        }
    }

    @Test
    public void flatBufferTimeoutUsesOneConnectionWithoutFallback() throws Exception {
        try (CountingFlatBufferFailureServer server =
                     new CountingFlatBufferFailureServer(FailureMode.TIMEOUT)) {
            assertThrows(HyperionTimeoutException.class,
                    () -> new HyperionTransportFactory().create(
                            HyperionTransportType.FLATBUFFER, server.config(100)));
            assertEquals(1, server.awaitConnectionCount());
        }
    }

    @Test
    public void factoryHasNoStateOrActiveTransportField() {
        Field[] fields = HyperionTransportFactory.class.getDeclaredFields();
        assertEquals(0, fields.length);
    }

    @Test
    public void factoryApiAcceptsOnlyExplicitTypeAndSingleConfiguration()
            throws Exception {
        Class<?>[] parameters = HyperionTransportFactory.class
                .getDeclaredMethod(
                        "create", HyperionTransportType.class, HyperionTransportConfig.class)
                .getParameterTypes();
        assertEquals(2, parameters.length);
        assertEquals(HyperionTransportType.class, parameters[0]);
        assertEquals(HyperionTransportConfig.class, parameters[1]);
    }

    @Test
    public void twoFactoryCallsCreateIndependentTransports() throws Exception {
        try (HyperionTransportTestServer firstServer = new HyperionTransportTestServer(
                HyperionTransportTestServer.Connection::waitForClientClose);
                HyperionTransportTestServer secondServer = new HyperionTransportTestServer(
                        HyperionTransportTestServer.Connection::waitForClientClose)) {
            HyperionTransportFactory factory = new HyperionTransportFactory();
            HyperionTransport first = factory.create(
                    HyperionTransportType.PROTOBUF, firstServer.config(null));
            HyperionTransport second = factory.create(
                    HyperionTransportType.PROTOBUF, secondServer.config(null));
            try {
                assertTrue(first.isConnected());
                assertTrue(second.isConnected());
                first.close();
                assertFalse(first.isConnected());
                assertTrue(second.isConnected());
            } finally {
                first.close();
                second.close();
            }
        }
    }

    @Test
    public void nullConfigurationIsRejectedBeforeConnectionSelection() {
        assertThrows(NullPointerException.class,
                () -> new HyperionTransportFactory().create(
                        HyperionTransportType.PROTOBUF, null));
    }

    private enum FailureMode {
        SERVER_ERROR,
        WRONG_REGISTRATION,
        TIMEOUT
    }

    private static final class CountingFlatBufferFailureServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<Integer> connections;

        CountingFlatBufferFailureServer(FailureMode mode) throws IOException {
            serverSocket = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
            executor = Executors.newSingleThreadExecutor();
            connections = executor.submit(() -> {
                int count = 0;
                try (Socket first = serverSocket.accept()) {
                    count++;
                    first.setSoTimeout(2_000);
                    HyperionTransportTestServer.Connection connection =
                            new HyperionTransportTestServer.Connection(first);
                    connection.expectFlatBufferRegister();
                    switch (mode) {
                        case SERVER_ERROR:
                            connection.writeFlatBufferReply("registration rejected", -1, -1);
                            break;
                        case WRONG_REGISTRATION:
                            connection.writeFlatBufferReply(null, -1, 151);
                            connection.expectFlatBufferRegister();
                            connection.writeFlatBufferReply(null, -1, 151);
                            break;
                        case TIMEOUT:
                            connection.waitForClientClose();
                            break;
                        default:
                            throw new AssertionError(mode);
                    }
                }

                serverSocket.setSoTimeout(200);
                try (Socket ignored = serverSocket.accept()) {
                    count++;
                } catch (SocketTimeoutException expected) {
                    // No second connection is the required no-fallback result.
                }
                return count;
            });
        }

        HyperionTransportConfig config(int readTimeoutMs) {
            return new HyperionTransportConfig(
                    serverSocket.getInetAddress().getHostAddress(),
                    serverSocket.getLocalPort(),
                    HyperionTransportTestServer.PRIORITY,
                    HyperionTransportTestServer.ORIGIN,
                    HyperionTransportTestServer.CONNECT_TIMEOUT_MS,
                    readTimeoutMs);
        }

        int awaitConnectionCount() throws Exception {
            try {
                return connections.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new AssertionError(cause);
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdownNow();
        }
    }
}
