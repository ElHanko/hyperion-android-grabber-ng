package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertTrue;

import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ClearRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ColorRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionReply;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ImageRequest;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.protobuf.ExtensionRegistryLite;

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

final class HyperionTransportTestServer implements AutoCloseable {
    static final int PRIORITY = 150;
    static final String ORIGIN = "Hyperion Android Grabber NG test";
    static final int CONNECT_TIMEOUT_MS = 1_000;
    static final int READ_TIMEOUT_MS = 500;

    private static final ExtensionRegistryLite PROTOBUF_EXTENSIONS = createRegistry();

    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final Future<?> action;
    private volatile Socket acceptedSocket;

    HyperionTransportTestServer(ServerAction action) throws IOException {
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

    HyperionTransportConfig config() {
        return config(ORIGIN, READ_TIMEOUT_MS);
    }

    HyperionTransportConfig config(String origin) {
        return config(origin, READ_TIMEOUT_MS);
    }

    HyperionTransportConfig config(String origin, int readTimeoutMs) {
        return new HyperionTransportConfig(
                serverSocket.getInetAddress().getHostAddress(),
                serverSocket.getLocalPort(),
                PRIORITY,
                origin,
                CONNECT_TIMEOUT_MS,
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
            throw new AssertionError("Transport fake-server action did not finish", failure);
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

        byte[] readFrame() throws IOException {
            int size = input.readInt();
            assertTrue("Request payload length must be positive", size > 0);
            byte[] payload = new byte[size];
            input.readFully(payload);
            return payload;
        }

        HyperionRequest readProtobufRequest() throws IOException {
            return HyperionRequest.parseFrom(readFrame(), PROTOBUF_EXTENSIONS);
        }

        Request readFlatBufferRequest() throws IOException {
            return Request.getRootAsRequest(ByteBuffer.wrap(readFrame()));
        }

        Register expectFlatBufferRegister() throws IOException {
            Request request = readFlatBufferRequest();
            if (request.commandType() != Command.Register) {
                throw new AssertionError("First FlatBuffer request was not Register");
            }
            Register register = (Register) request.command(new Register());
            if (register == null) {
                throw new AssertionError("Register payload is missing");
            }
            return register;
        }

        void acceptFlatBufferRegistration() throws IOException {
            expectFlatBufferRegister();
            writeFlatBufferReply(null, -1, PRIORITY);
        }

        void writeProtobufSuccess() throws IOException {
            writeProtobufReply(HyperionReply.newBuilder()
                    .setType(HyperionReply.Type.REPLY)
                    .setSuccess(true)
                    .build());
        }

        void writeProtobufError(String message) throws IOException {
            writeProtobufReply(HyperionReply.newBuilder()
                    .setType(HyperionReply.Type.REPLY)
                    .setSuccess(false)
                    .setError(message)
                    .build());
        }

        void writeProtobufReply(HyperionReply reply) throws IOException {
            writeFrame(reply.toByteArray());
        }

        void writeFlatBufferReply(String error, int video, int registered)
                throws IOException {
            FlatBufferBuilder builder = new FlatBufferBuilder(128);
            int errorOffset = error == null ? 0 : builder.createString(error);
            int replyOffset = Reply.createReply(builder, errorOffset, video, registered);
            Reply.finishReplyBuffer(builder, replyOffset);
            ByteBuffer buffer = builder.dataBuffer();
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);
            writeFrame(payload);
        }

        void writeFrame(byte[] payload) throws IOException {
            output.writeInt(payload.length);
            output.write(payload);
            output.flush();
        }

        void writeInvalidFrame(byte[] payload) throws IOException {
            writeFrame(payload);
        }

        void waitForClientClose() throws IOException {
            try {
                while (input.read() != -1) {
                    // Drain until the client closes after its timeout or failure.
                }
            } catch (SocketException failure) {
                // A reset also proves that the client closed the failed connection.
            }
        }

        Socket socket() {
            return socket;
        }
    }

    private static ExtensionRegistryLite createRegistry() {
        ExtensionRegistryLite registry = ExtensionRegistryLite.newInstance();
        registry.add(ColorRequest.colorRequest);
        registry.add(ImageRequest.imageRequest);
        registry.add(ClearRequest.clearRequest);
        return registry;
    }
}
