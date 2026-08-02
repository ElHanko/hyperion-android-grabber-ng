package com.elhanko.hyperiongrabber.ng.common.network;

import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ClearRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ColorRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionReply;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.HyperionRequest;
import com.elhanko.hyperiongrabber.ng.common.HyperionProto.ImageRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public final class Hyperion implements Closeable {
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 1_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 2_000;
    public static final int MIN_PRIORITY = 100;
    public static final int MAX_PRIORITY = 199;

    /** Large enough for a 4K RGBA frame while bounding memory used for one request. */
    public static final int MAX_REQUEST_SIZE = 64 * 1024 * 1024;

    /** Replies contain only status metadata; one MiB leaves ample protocol headroom. */
    public static final int MAX_REPLY_SIZE = 1024 * 1024;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object exchangeLock = new Object();
    private final Object closeLock = new Object();
    private volatile boolean closed;

    public Hyperion(String address, int port) throws IOException {
        this(address, port, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public Hyperion(String address, int port, int connectTimeoutMs, int readTimeoutMs)
            throws IOException {
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }

        Socket newSocket = new Socket();
        DataInputStream newInput = null;
        DataOutputStream newOutput = null;
        try {
            newSocket.setTcpNoDelay(true);
            newSocket.connect(new InetSocketAddress(address, port), connectTimeoutMs);
            newSocket.setSoTimeout(readTimeoutMs);
            newInput = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            newOutput = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
        } catch (IOException | RuntimeException e) {
            try {
                newSocket.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }

        socket = newSocket;
        input = newInput;
        output = newOutput;
    }

    public boolean isConnected() {
        return !closed
                && socket.isConnected()
                && !socket.isClosed()
                && !socket.isInputShutdown()
                && !socket.isOutputShutdown();
    }

    public void disconnect() throws IOException {
        close();
    }

    @Override
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            socket.close();
        }
    }

    public void clear(int priority) throws IOException {
        sendRequest(clearRequest(priority));
    }

    public static HyperionRequest clearRequest(int priority) {
        validatePriority(priority);
        ClearRequest request = ClearRequest.newBuilder()
                .setPriority(priority)
                .build();

        return HyperionRequest.newBuilder()
                .setCommand(HyperionRequest.Command.CLEAR)
                .setExtension(ClearRequest.clearRequest, request)
                .build();
    }

    public void clearAll() throws IOException {
        sendRequest(clearAllRequest());
    }

    public static HyperionRequest clearAllRequest() {
        return HyperionRequest.newBuilder()
                .setCommand(HyperionRequest.Command.CLEARALL)
                .build();
    }

    public void setColor(int color, int priority) throws IOException {
        setColor(color, priority, -1);
    }

    public void setColor(int color, int priority, int durationMs) throws IOException {
        sendRequest(setColorRequest(color, priority, durationMs));
    }

    public static HyperionRequest setColorRequest(int color, int priority, int durationMs) {
        validatePriority(priority);
        ColorRequest request = ColorRequest.newBuilder()
                .setRgbColor(color)
                .setPriority(priority)
                .setDuration(durationMs)
                .build();

        return HyperionRequest.newBuilder()
                .setCommand(HyperionRequest.Command.COLOR)
                .setExtension(ColorRequest.colorRequest, request)
                .build();
    }

    public void setImage(byte[] data, int width, int height, int priority) throws IOException {
        setImage(data, width, height, priority, -1);
    }

    public void setImage(byte[] data, int width, int height, int priority, int durationMs)
            throws IOException {
        sendRequest(setImageRequest(data, width, height, priority, durationMs));
    }

    public static HyperionRequest setImageRequest(
            byte[] data, int width, int height, int priority, int durationMs) {
        validatePriority(priority);
        validateImage(data, width, height);
        ImageRequest request = ImageRequest.newBuilder()
                .setImagedata(ByteString.copyFrom(data))
                .setImagewidth(width)
                .setImageheight(height)
                .setPriority(priority)
                .setDuration(durationMs)
                .build();

        return HyperionRequest.newBuilder()
                .setCommand(HyperionRequest.Command.IMAGE)
                .setExtension(ImageRequest.imageRequest, request)
                .build();
    }

    public HyperionReply sendRequest(HyperionRequest request) throws IOException {
        byte[] payload = validateAndSerialize(request);

        synchronized (exchangeLock) {
            try {
                if (!isConnected()) {
                    throw new SocketException("Hyperion connection is closed");
                }

                output.writeInt(payload.length);
                output.write(payload);
                output.flush();
                return receiveReply();
            } catch (HyperionServerException e) {
                throw e;
            } catch (SocketTimeoutException e) {
                HyperionTimeoutException timeout = new HyperionTimeoutException(
                        "Timed out while waiting for a Hyperion reply", e);
                closeAfterFailure(timeout);
                throw timeout;
            } catch (IOException e) {
                closeAfterFailure(e);
                throw e;
            }
        }
    }

    private HyperionReply receiveReply() throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_REPLY_SIZE) {
            throw new HyperionProtocolException(
                    "Invalid Hyperion reply length: " + Integer.toUnsignedString(size));
        }

        byte[] data = new byte[size];
        input.readFully(data);

        final HyperionReply reply;
        try {
            reply = HyperionReply.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new HyperionProtocolException("Unable to parse Hyperion reply", e);
        }

        if (!reply.isInitialized()) {
            throw new HyperionProtocolException("Hyperion reply is missing required fields");
        }
        if (reply.getType() != HyperionReply.Type.REPLY) {
            throw new HyperionProtocolException(
                    "Unexpected Hyperion reply type: " + reply.getType());
        }
        if (!reply.hasSuccess()) {
            throw new HyperionProtocolException("Hyperion reply is missing the success field");
        }
        if (!reply.getSuccess()) {
            String message = reply.hasError() && !reply.getError().isEmpty()
                    ? reply.getError()
                    : "Hyperion rejected the request";
            throw new HyperionServerException(message);
        }
        return reply;
    }

    private static byte[] validateAndSerialize(HyperionRequest request) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (!request.isInitialized()) {
            throw new IllegalArgumentException("Hyperion request is missing required fields");
        }

        switch (request.getCommand()) {
            case COLOR:
                if (!request.hasExtension(ColorRequest.colorRequest)) {
                    throw new IllegalArgumentException("COLOR request has no ColorRequest payload");
                }
                validatePriority(request.getExtension(ColorRequest.colorRequest).getPriority());
                break;
            case IMAGE:
                if (!request.hasExtension(ImageRequest.imageRequest)) {
                    throw new IllegalArgumentException("IMAGE request has no ImageRequest payload");
                }
                ImageRequest image = request.getExtension(ImageRequest.imageRequest);
                validatePriority(image.getPriority());
                validateImage(
                        image.getImagedata().toByteArray(),
                        image.getImagewidth(),
                        image.getImageheight());
                break;
            case CLEAR:
                if (!request.hasExtension(ClearRequest.clearRequest)) {
                    throw new IllegalArgumentException("CLEAR request has no ClearRequest payload");
                }
                validatePriority(request.getExtension(ClearRequest.clearRequest).getPriority());
                break;
            case CLEARALL:
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported Hyperion command: " + request.getCommand());
        }

        int size = request.getSerializedSize();
        if (size <= 0 || size > MAX_REQUEST_SIZE) {
            throw new IllegalArgumentException("Invalid Hyperion request size: " + size);
        }
        return request.toByteArray();
    }

    private static void validatePriority(int priority) {
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "Priority must be between " + MIN_PRIORITY + " and " + MAX_PRIORITY);
        }
    }

    private static void validateImage(byte[] data, int width, int height) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image width and height must be positive");
        }

        long pixels = (long) width * height;
        long rgbSize = pixels * 3L;
        long rgbaSize = pixels * 4L;
        if (data.length != rgbSize && data.length != rgbaSize) {
            throw new IllegalArgumentException(
                    "Image data must contain exactly three or four bytes per pixel");
        }
    }

    private void closeAfterFailure(IOException failure) {
        try {
            close();
        } catch (IOException closeError) {
            failure.addSuppressed(closeError);
        }
    }
}
