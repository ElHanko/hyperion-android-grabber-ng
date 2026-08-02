package com.elhanko.hyperiongrabber.ng.common.network;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ClearRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ColorRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionReply;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ImageRequest;
import com.google.protobuf.ExtensionRegistryLite;

import org.junit.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class HyperionTest {
    private static final int PRIORITY = 150;
    private static final ExtensionRegistryLite EXTENSIONS = createExtensionRegistry();

    @Test
    public void sendsColorRequestAndAcceptsSuccessReply() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            HyperionRequest request = readRequest(socket);
            assertEquals(HyperionRequest.Command.COLOR, request.getCommand());
            ColorRequest color = request.getExtension(ColorRequest.colorRequest);
            assertEquals(PRIORITY, color.getPriority());
            assertEquals(0x123456, color.getRgbColor());
            assertEquals(250, color.getDuration());
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            HyperionReply reply = client.sendRequest(
                    Hyperion.setColorRequest(0x123456, PRIORITY, 250));
            assertTrue(reply.getSuccess());
        }
    }

    @Test
    public void sendsRgbImageRequest() throws Exception {
        byte[] image = {1, 2, 3, 4, 5, 6};
        assertImageRequest(image, 2, 1);
    }

    @Test
    public void sendsRgbaImageRequest() throws Exception {
        byte[] image = {1, 2, 3, 4, 5, 6, 7, 8};
        assertImageRequest(image, 2, 1);
    }

    @Test
    public void sendsClearRequest() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            HyperionRequest request = readRequest(socket);
            assertEquals(HyperionRequest.Command.CLEAR, request.getCommand());
            assertEquals(PRIORITY,
                    request.getExtension(ClearRequest.clearRequest).getPriority());
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            client.clear(PRIORITY);
        }
    }

    @Test
    public void sendsClearAllRequest() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            HyperionRequest request = readRequest(socket);
            assertEquals(HyperionRequest.Command.CLEARALL, request.getCommand());
            assertFalse(request.hasExtension(ClearRequest.clearRequest));
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            client.clearAll();
        }
    }

    @Test
    public void writesBigEndianLengthHeader() throws Exception {
        HyperionRequest expected = Hyperion.clearAllRequest();
        try (FakeServer server = new FakeServer(socket -> {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            byte[] header = new byte[4];
            input.readFully(header);
            assertArrayEquals(
                    ByteBuffer.allocate(4)
                            .order(ByteOrder.BIG_ENDIAN)
                            .putInt(expected.getSerializedSize())
                            .array(),
                    header);
            byte[] payload = new byte[expected.getSerializedSize()];
            input.readFully(payload);
            assertEquals(expected, HyperionRequest.parseFrom(payload, EXTENSIONS));
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            client.sendRequest(expected);
        }
    }

    @Test
    public void readsFragmentedReplyHeader() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            byte[] payload = successReply().toByteArray();
            byte[] header = ByteBuffer.allocate(4).putInt(payload.length).array();
            for (byte value : header) {
                socket.getOutputStream().write(value);
                socket.getOutputStream().flush();
                Thread.sleep(10);
            }
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
        }); Hyperion client = server.connect()) {
            assertTrue(client.sendRequest(Hyperion.clearAllRequest()).getSuccess());
        }
    }

    @Test
    public void readsFragmentedReplyBody() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            byte[] payload = successReply().toByteArray();
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(payload.length);
            output.flush();
            for (byte value : payload) {
                output.write(value);
                output.flush();
                Thread.sleep(10);
            }
        }); Hyperion client = server.connect()) {
            assertTrue(client.sendRequest(Hyperion.clearAllRequest()).getSuccess());
        }
    }

    @Test
    public void reusesOneConnectionForMultipleRequests() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            assertEquals(HyperionRequest.Command.COLOR, readRequest(socket).getCommand());
            writeReply(socket, successReply());
            assertEquals(HyperionRequest.Command.CLEAR, readRequest(socket).getCommand());
            writeReply(socket, successReply());
            assertEquals(HyperionRequest.Command.CLEARALL, readRequest(socket).getCommand());
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            client.setColor(0x123456, PRIORITY);
            client.clear(PRIORITY);
            client.clearAll();
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void serializesConcurrentRequestReplyExchanges() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            for (int i = 0; i < 2; i++) {
                HyperionRequest request = readRequest(socket);
                assertTrue(request.getCommand() == HyperionRequest.Command.COLOR
                        || request.getCommand() == HyperionRequest.Command.CLEAR);
                writeReply(socket, successReply());
            }
        }); Hyperion client = server.connect()) {
            ExecutorService callers = Executors.newFixedThreadPool(2);
            try {
                Future<?> color = callers.submit(() -> {
                    client.setColor(0x123456, PRIORITY);
                    return null;
                });
                Future<?> clear = callers.submit(() -> {
                    client.clear(PRIORITY);
                    return null;
                });
                color.get(2, TimeUnit.SECONDS);
                clear.get(2, TimeUnit.SECONDS);
            } finally {
                callers.shutdownNow();
            }
        }
    }

    @Test
    public void exposesHyperionErrorReply() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            writeReply(socket, HyperionReply.newBuilder()
                    .setType(HyperionReply.Type.REPLY)
                    .setSuccess(false)
                    .setError("priority is busy")
                    .build());
        }); Hyperion client = server.connect()) {
            HyperionServerException error = assertThrows(
                    HyperionServerException.class,
                    () -> client.clearAll());
            assertEquals("priority is busy", error.getMessage());
            assertTrue(client.isConnected());
        }
    }

    @Test
    public void closesConnectionWhenServerClosesBeforeHeader() throws Exception {
        try (FakeServer server = new FakeServer(socket -> readRequest(socket));
                Hyperion client = server.connect()) {
            assertThrows(EOFException.class, client::clearAll);
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void closesConnectionWhenServerClosesInsideBody() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(10);
            output.write(1);
            output.flush();
        }); Hyperion client = server.connect()) {
            assertThrows(EOFException.class, client::clearAll);
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void reportsReadTimeoutAndClosesConnection() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            Thread.sleep(300);
        }); Hyperion client = server.connect(1_000, 50)) {
            assertThrows(HyperionTimeoutException.class, client::clearAll);
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void rejectsNegativeReplyLengthAndClosesConnection() throws Exception {
        assertInvalidReplyLength(-1);
    }

    @Test
    public void rejectsOversizedReplyLengthAndClosesConnection() throws Exception {
        assertInvalidReplyLength(Hyperion.MAX_REPLY_SIZE + 1);
    }

    @Test
    public void rejectsZeroReplyLengthAndClosesConnection() throws Exception {
        assertInvalidReplyLength(0);
    }

    @Test
    public void rejectsPriorityOutsideProtoRange() {
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.setColorRequest(0, Hyperion.MIN_PRIORITY - 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.clearRequest(Hyperion.MAX_PRIORITY + 1));
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.setImageRequest(new byte[3], 1, 1, 200, -1));
    }

    @Test
    public void rejectsWrongRgbAndRgbaDataLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.setImageRequest(new byte[11], 2, 2, PRIORITY, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.setImageRequest(new byte[13], 2, 2, PRIORITY, -1));
        assertThrows(IllegalArgumentException.class,
                () -> Hyperion.setImageRequest(new byte[0], 0, 1, PRIORITY, -1));
    }

    @Test
    public void closeIsIdempotent() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            while (socket.getInputStream().read() != -1) {
                // Wait for the client to close the connection.
            }
        })) {
            Hyperion client = server.connect();
            client.close();
            client.close();
            client.disconnect();
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void canCreateNewConnectionAfterAbortedConnection() throws Exception {
        try (FakeServer firstServer = new FakeServer(socket -> readRequest(socket));
                Hyperion firstClient = firstServer.connect()) {
            assertThrows(EOFException.class, firstClient::clearAll);
            assertFalse(firstClient.isConnected());
        }

        try (FakeServer secondServer = new FakeServer(socket -> {
            assertEquals(HyperionRequest.Command.CLEARALL, readRequest(socket).getCommand());
            writeReply(socket, successReply());
        }); Hyperion secondClient = secondServer.connect()) {
            secondClient.clearAll();
            assertTrue(secondClient.isConnected());
        }
    }

    @Test
    public void rejectsMalformedReplyAndClosesConnection() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(1);
            output.write(0x08);
            output.flush();
        }); Hyperion client = server.connect()) {
            assertThrows(HyperionProtocolException.class, client::clearAll);
            assertFalse(client.isConnected());
        }
    }

    @Test
    public void rejectsReplyWithoutSuccessAndClosesConnection() throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            writeReply(socket, HyperionReply.newBuilder()
                    .setType(HyperionReply.Type.REPLY)
                    .build());
        }); Hyperion client = server.connect()) {
            HyperionProtocolException error = assertThrows(
                    HyperionProtocolException.class,
                    client::clearAll);
            assertTrue(error.getMessage().contains("success"));
            assertFalse(client.isConnected());
        }
    }

    private static void assertImageRequest(byte[] data, int width, int height) throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            HyperionRequest request = readRequest(socket);
            assertEquals(HyperionRequest.Command.IMAGE, request.getCommand());
            ImageRequest image = request.getExtension(ImageRequest.imageRequest);
            assertEquals(PRIORITY, image.getPriority());
            assertEquals(width, image.getImagewidth());
            assertEquals(height, image.getImageheight());
            assertArrayEquals(data, image.getImagedata().toByteArray());
            writeReply(socket, successReply());
        }); Hyperion client = server.connect()) {
            client.setImage(data, width, height, PRIORITY);
        }
    }

    private static void assertInvalidReplyLength(int length) throws Exception {
        try (FakeServer server = new FakeServer(socket -> {
            readRequest(socket);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(length);
            output.flush();
        }); Hyperion client = server.connect()) {
            HyperionProtocolException error = assertThrows(
                    HyperionProtocolException.class,
                    client::clearAll);
            assertTrue(error.getMessage().contains("length"));
            assertFalse(client.isConnected());
        }
    }

    private static HyperionRequest readRequest(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        int length = input.readInt();
        assertTrue("Request length must be positive", length > 0);
        assertTrue("Request length exceeds transport limit", length <= Hyperion.MAX_REQUEST_SIZE);
        byte[] payload = new byte[length];
        input.readFully(payload);
        return HyperionRequest.parseFrom(payload, EXTENSIONS);
    }

    private static void writeReply(Socket socket, HyperionReply reply) throws Exception {
        byte[] payload = reply.toByteArray();
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    private static HyperionReply successReply() {
        return HyperionReply.newBuilder()
                .setType(HyperionReply.Type.REPLY)
                .setSuccess(true)
                .build();
    }

    private static ExtensionRegistryLite createExtensionRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        registry.add(ColorRequest.colorRequest);
        registry.add(ImageRequest.imageRequest);
        registry.add(ClearRequest.clearRequest);
        return registry;
    }

    @FunctionalInterface
    private interface ServerAction {
        void run(Socket socket) throws Exception;
    }

    private static final class FakeServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executor;
        private final Future<?> action;

        FakeServer(ServerAction action) throws Exception {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            executor = Executors.newSingleThreadExecutor();
            this.action = executor.submit(() -> {
                try (Socket socket = serverSocket.accept()) {
                    action.run(socket);
                }
                return null;
            });
        }

        Hyperion connect() throws Exception {
            return connect(Hyperion.DEFAULT_CONNECT_TIMEOUT_MS, Hyperion.DEFAULT_READ_TIMEOUT_MS);
        }

        Hyperion connect(int connectTimeoutMs, int readTimeoutMs) throws Exception {
            return new Hyperion(
                    serverSocket.getInetAddress().getHostAddress(),
                    serverSocket.getLocalPort(),
                    connectTimeoutMs,
                    readTimeoutMs);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            executor.shutdown();
            try {
                action.get(3, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new AssertionError(cause);
            } finally {
                executor.shutdownNow();
            }
        }
    }
}
