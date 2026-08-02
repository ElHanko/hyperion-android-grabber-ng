package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hyperionnet.Command;
import hyperionnet.Register;
import hyperionnet.Reply;
import hyperionnet.Request;

final class FlatBufferFakeServer implements AutoCloseable {
    static final int PRIORITY = 150;
    static final String ORIGIN = "Hyperion Android Grabber NG test";
    static final int CONNECT_TIMEOUT_MS = 1_000;
    static final int READ_TIMEOUT_MS = 1_000;

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Future<?> action;
    private volatile Socket acceptedSocket;

    FlatBufferFakeServer(ServerAction action) throws IOException {
        serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        executor = Executors.newSingleThreadExecutor();
        this.action = executor.submit(() -> {
            try (Socket socket = serverSocket.accept()) {
                acceptedSocket = socket;
                socket.setSoTimeout(2_000);
                action.run(new Connection(socket));
            }
            return null;
        });
    }

    int getPort() {
        return serverSocket.getLocalPort();
    }

    String getHost() {
        return serverSocket.getInetAddress().getHostAddress();
    }

    FlatBufferHyperionClient connect() throws IOException {
        return connect(CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    FlatBufferHyperionClient connect(int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        return new FlatBufferHyperionClient(
                getHost(),
                getPort(),
                PRIORITY,
                ORIGIN,
                connectTimeoutMs,
                readTimeoutMs);
    }

    @Override
    public void close() throws Exception {
        serverSocket.close();
        executor.shutdown();
        try {
            action.get(4, TimeUnit.SECONDS);
        } catch (TimeoutException failure) {
            Socket socket = acceptedSocket;
            if (socket != null) {
                socket.close();
            }
            throw new AssertionError("FlatBuffer fake-server action did not finish", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
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

    @FunctionalInterface
    interface ServerAction {
        void run(Connection connection) throws Exception;
    }

    static final class Connection {
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;

        Connection(Socket socket) throws IOException {
            this.socket = socket;
            input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        }

        Frame readFrame() throws IOException {
            byte[] header = new byte[4];
            input.readFully(header);
            long size = ((header[0] & 0xffL) << 24)
                    | ((header[1] & 0xffL) << 16)
                    | ((header[2] & 0xffL) << 8)
                    | (header[3] & 0xffL);
            assertTrue("Request payload length must be positive", size > 0);
            assertTrue(
                    "Request payload exceeds the local limit",
                    size <= FlatBufferHyperionClient.MAX_REQUEST_PAYLOAD_SIZE);
            byte[] payload = new byte[(int) size];
            input.readFully(payload);
            return new Frame(header, payload);
        }

        Request readRequest() throws IOException {
            return parseRequest(readFrame().payload);
        }

        Request expectRegister() throws IOException {
            Request request = readRequest();
            assertEquals(Command.Register, request.commandType());
            Register register = (Register) request.command(new Register());
            assertNotNull(register);
            assertEquals(ORIGIN, register.origin());
            assertEquals(PRIORITY, register.priority());
            return request;
        }

        void acceptRegistration() throws IOException {
            expectRegister();
            writeReply(null, -1, PRIORITY);
        }

        void writeReply(String error, int video, int registered) throws IOException {
            writeFrame(replyPayload(error, video, registered));
        }

        void writeEmptyErrorReply() throws IOException {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);
            int errorOffset = builder.createString("");
            int replyOffset = Reply.createReply(builder, errorOffset, -1, -1);
            Reply.finishReplyBuffer(builder, replyOffset);
            writeFrame(toByteArray(builder));
        }

        void writeFrame(byte[] payload) throws IOException {
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
        }

        void writeFragmentedFrame(
                byte[] payload, boolean fragmentHeader, boolean fragmentPayload)
                throws IOException {
            byte[] header = ByteBuffer.allocate(4)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(payload.length)
                    .array();
            if (fragmentHeader) {
                for (byte value : header) {
                    output.write(value);
                    output.flush();
                }
            } else {
                output.write(header);
            }
            if (fragmentPayload) {
                for (byte value : payload) {
                    output.write(value);
                    output.flush();
                }
            } else {
                output.write(payload);
            }
            output.flush();
        }

        void writeHeader(long unsignedLength) throws IOException {
            output.write((int) ((unsignedLength >>> 24) & 0xff));
            output.write((int) ((unsignedLength >>> 16) & 0xff));
            output.write((int) ((unsignedLength >>> 8) & 0xff));
            output.write((int) (unsignedLength & 0xff));
            output.flush();
        }

        void writeBytes(byte[] bytes) throws IOException {
            output.write(bytes);
            output.flush();
        }

        void shutdownOutput() throws IOException {
            socket.shutdownOutput();
        }

        void closeWithReset() throws IOException {
            socket.setSoLinger(true, 0);
            socket.close();
        }

        void setReadTimeout(int timeoutMs) throws SocketException {
            socket.setSoTimeout(timeoutMs);
        }

        int readOneByte() throws IOException {
            return input.read();
        }

        void waitForClientClose() throws IOException {
            try {
                while (input.read() != -1) {
                    // Drain until the client closes after its timeout or failure.
                }
            } catch (SocketException failure) {
                // A reset is also a completed close for lifecycle tests.
            }
        }

        Socket socket() {
            return socket;
        }
    }

    static byte[] replyPayload(String error, int video, int registered) {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int errorOffset = error == null ? 0 : builder.createString(error);
        int replyOffset = Reply.createReply(builder, errorOffset, video, registered);
        Reply.finishReplyBuffer(builder, replyOffset);
        return toByteArray(builder);
    }

    static Request parseRequest(byte[] payload) {
        return Request.getRootAsRequest(ByteBuffer.wrap(payload));
    }

    private static byte[] toByteArray(FlatBufferBuilder builder) {
        ByteBuffer buffer = builder.dataBuffer();
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        return payload;
    }

    static final class Frame {
        final byte[] header;
        final byte[] payload;

        Frame(byte[] header, byte[] payload) {
            this.header = header;
            this.payload = payload;
        }

        int bigEndianLength() {
            return ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getInt();
        }
    }
}
