package com.elhanko.hyperiongrabber.ng.common.network.flatbuffer;

import com.elhanko.hyperiongrabber.ng.common.network.HyperionProtocolException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionServerException;
import com.elhanko.hyperiongrabber.ng.common.network.HyperionTimeoutException;
import com.google.flatbuffers.FlatBufferBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import hyperionnet.Clear;
import hyperionnet.Color;
import hyperionnet.Command;
import hyperionnet.Image;
import hyperionnet.ImageType;
import hyperionnet.RawImage;
import hyperionnet.Register;
import hyperionnet.Reply;
import hyperionnet.Request;

/**
 * Isolated experimental client for the Hyperion NG 2.2.1 FlatBuffer protocol.
 *
 * <p>This class is deliberately not connected to the application transport lifecycle. A client
 * becomes ready only after the server acknowledges its registration priority. Every public
 * operation serializes one framed request/reply exchange. Reconnect policy belongs to a later
 * integration stage.</p>
 */
public final class FlatBufferHyperionClient implements Closeable {
    public static final int MIN_PRIORITY = 100;
    public static final int MAX_PRIORITY = 199;
    public static final int MAX_REQUEST_PAYLOAD_SIZE = 64 * 1024 * 1024;
    public static final int MAX_REPLY_PAYLOAD_SIZE = 1024 * 1024;
    public static final int MAX_REGISTRATION_ATTEMPTS = 2;

    private static final int MAX_UNSOLICITED_STATE_REPLIES = 2;

    private final String host;
    private final int port;
    private final int priority;
    private final String origin;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object exchangeLock = new Object();
    private final Object closeLock = new Object();

    private volatile boolean registered;
    private volatile boolean closed;

    public FlatBufferHyperionClient(
            String host,
            int port,
            int priority,
            String origin,
            int connectTimeoutMs,
            int readTimeoutMs) throws IOException {
        validateConnectionParameters(
                host, port, priority, origin, connectTimeoutMs, readTimeoutMs);

        this.host = host;
        this.port = port;
        this.priority = priority;
        this.origin = origin;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;

        Socket connectedSocket = connect(host, port, connectTimeoutMs, readTimeoutMs);
        DataInputStream connectedInput = null;
        DataOutputStream connectedOutput = null;
        try {
            connectedInput = new DataInputStream(
                    new BufferedInputStream(connectedSocket.getInputStream()));
            connectedOutput = new DataOutputStream(
                    new BufferedOutputStream(connectedSocket.getOutputStream()));
        } catch (IOException | RuntimeException failure) {
            closeSocketAfterConstructionFailure(connectedSocket, failure);
            throw failure;
        }

        socket = connectedSocket;
        input = connectedInput;
        output = connectedOutput;

        try {
            synchronized (exchangeLock) {
                performRegistration();
            }
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(failure);
            throw failure;
        }
    }

    /** Returns true only while the socket is open and the requested priority is registered. */
    public boolean isConnected() {
        return !closed
                && registered
                && socket.isConnected()
                && !socket.isClosed()
                && !socket.isInputShutdown()
                && !socket.isOutputShutdown();
    }

    /** Sends a packed {@code 0xRRGGBB} color value and a duration in milliseconds. */
    public void setColor(int rgbColor, int durationMs) throws IOException {
        exchange(buildColorRequest(rgbColor, durationMs), Operation.COLOR);
    }

    /**
     * Sends an RGB24 or RGB32/RGBA raw image without conversion.
     *
     * <p>The fourth byte of every RGB32 pixel is transmitted unchanged. Hyperion NG 2.2.1 uses
     * the first three bytes as red, green, and blue.</p>
     */
    public void setImage(byte[] data, int width, int height, int durationMs) throws IOException {
        validateImage(data, width, height);
        exchange(buildImageRequest(data, width, height, durationMs), Operation.IMAGE);
    }

    /**
     * Clears only this connection's registered priority.
     *
     * <p>The official server answers an own-priority clear with {@code registered = -1}. The
     * client then remains socket-connected but not ready and performs a bounded registration
     * handshake before its next color, image, or clear operation.</p>
     */
    public void clear() throws IOException {
        exchange(buildClearRequest(), Operation.CLEAR);
    }

    @Override
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            registered = false;
            socket.close();
        }
    }

    private void exchange(byte[] payload, Operation operation) throws IOException {
        synchronized (exchangeLock) {
            ensureOpen();
            if (!registered) {
                try {
                    performRegistration();
                } catch (IOException | RuntimeException failure) {
                    closeAfterFailure(failure);
                    throw failure;
                }
            }

            try {
                writeFrame(payload);
                processApplicationReply(operation);
            } catch (HyperionServerException serverFailure) {
                // A valid error reply is one complete frame. The upstream server continues its
                // parsing loop, so the synchronized connection remains reusable.
                throw serverFailure;
            } catch (HyperionTimeoutException timeoutFailure) {
                closeAfterFailure(timeoutFailure);
                throw timeoutFailure;
            } catch (SocketTimeoutException timeoutFailure) {
                HyperionTimeoutException timeout = new HyperionTimeoutException(
                        "Timed out while waiting for a Hyperion FlatBuffer reply",
                        timeoutFailure);
                closeAfterFailure(timeout);
                throw timeout;
            } catch (IOException | RuntimeException failure) {
                closeAfterFailure(failure);
                throw failure;
            }
        }
    }

    private void performRegistration() throws IOException {
        registered = false;
        byte[] request = buildRegisterRequest();
        String lastResult = "no reply";

        for (int attempt = 1; attempt <= MAX_REGISTRATION_ATTEMPTS; attempt++) {
            writeFrame(request);
            int stateReplies = 0;

            while (stateReplies <= MAX_UNSOLICITED_STATE_REPLIES) {
                ReplyValues reply = readReplyWithTimeout(
                        "Timed out while waiting for FlatBuffer registration acknowledgment");
                if (reply.hasError) {
                    throw serverException(reply.error);
                }
                if (reply.registered == priority) {
                    registered = true;
                    return;
                }
                if (reply.video != -1 && reply.registered == -1) {
                    stateReplies++;
                    if (stateReplies > MAX_UNSOLICITED_STATE_REPLIES) {
                        throw new HyperionProtocolException(
                                "Too many video state replies during FlatBuffer registration");
                    }
                    continue;
                }

                lastResult = Integer.toString(reply.registered);
                break;
            }
        }

        throw new HyperionProtocolException(
                "Hyperion FlatBuffer registration was not acknowledged for priority "
                        + priority
                        + " after "
                        + MAX_REGISTRATION_ATTEMPTS
                        + " attempts; last registered value was "
                        + lastResult);
    }

    private void processApplicationReply(Operation operation) throws IOException {
        int stateReplies = 0;

        while (stateReplies <= MAX_UNSOLICITED_STATE_REPLIES) {
            ReplyValues reply = readReplyWithTimeout(
                    "Timed out while waiting for a Hyperion FlatBuffer command reply");
            if (reply.hasError) {
                throw serverException(reply.error);
            }

            if (reply.video != -1 && reply.registered == -1) {
                stateReplies++;
                if (stateReplies > MAX_UNSOLICITED_STATE_REPLIES) {
                    throw new HyperionProtocolException(
                            "Too many video state replies before a FlatBuffer command reply");
                }
                continue;
            }

            if (operation == Operation.CLEAR) {
                if (reply.registered == -1) {
                    registered = false;
                    return;
                }
                throw new HyperionProtocolException(
                        "Own-priority clear did not release FlatBuffer registration; registered="
                                + reply.registered);
            }

            if (reply.registered == priority) {
                return;
            }
            if (reply.registered == -1) {
                // FlatBufferServer emits this unsolicited state reply when the global input must
                // be registered again. The reply for the already-written command follows it.
                registered = false;
                stateReplies++;
                if (stateReplies > MAX_UNSOLICITED_STATE_REPLIES) {
                    throw new HyperionProtocolException(
                            "Repeated FlatBuffer registration-required replies");
                }
                continue;
            }

            throw new HyperionProtocolException(
                    "Unexpected registered value in FlatBuffer command reply: "
                            + reply.registered);
        }

        throw new HyperionProtocolException(
                "No FlatBuffer command reply followed the server state replies");
    }

    private byte[] buildRegisterRequest() {
        FlatBufferBuilder builder = new FlatBufferBuilder(256);
        int originOffset = builder.createString(origin);
        int registerOffset = Register.createRegister(builder, originOffset, priority);
        int requestOffset = Request.createRequest(builder, Command.Register, registerOffset);
        Request.finishRequestBuffer(builder, requestOffset);
        return finishPayload(builder);
    }

    private static byte[] buildColorRequest(int rgbColor, int durationMs) {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int colorOffset = Color.createColor(builder, rgbColor, durationMs);
        int requestOffset = Request.createRequest(builder, Command.Color, colorOffset);
        Request.finishRequestBuffer(builder, requestOffset);
        return finishPayload(builder);
    }

    private static byte[] buildImageRequest(
            byte[] data, int width, int height, int durationMs) {
        if (data.length >= MAX_REQUEST_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Raw image data length "
                            + data.length
                            + " leaves no room within the "
                            + MAX_REQUEST_PAYLOAD_SIZE
                            + "-byte FlatBuffer request limit");
        }

        FlatBufferBuilder builder = new FlatBufferBuilder(1024);
        int dataOffset = RawImage.createDataVector(builder, data);
        int rawImageOffset = RawImage.createRawImage(builder, dataOffset, width, height);
        int imageOffset = Image.createImage(
                builder, ImageType.RawImage, rawImageOffset, durationMs);
        int requestOffset = Request.createRequest(builder, Command.Image, imageOffset);
        Request.finishRequestBuffer(builder, requestOffset);
        return finishPayload(builder);
    }

    private byte[] buildClearRequest() {
        FlatBufferBuilder builder = new FlatBufferBuilder(128);
        int clearOffset = Clear.createClear(builder, priority);
        int requestOffset = Request.createRequest(builder, Command.Clear, clearOffset);
        Request.finishRequestBuffer(builder, requestOffset);
        return finishPayload(builder);
    }

    private static byte[] finishPayload(FlatBufferBuilder builder) {
        ByteBuffer buffer = builder.dataBuffer();
        int size = buffer.remaining();
        if (size <= 0 || size > MAX_REQUEST_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid Hyperion FlatBuffer request payload size: " + size);
        }
        byte[] payload = new byte[size];
        buffer.get(payload);
        return payload;
    }

    private void writeFrame(byte[] payload) throws IOException {
        if (payload.length <= 0 || payload.length > MAX_REQUEST_PAYLOAD_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid Hyperion FlatBuffer request payload size: " + payload.length);
        }
        output.writeInt(payload.length);
        output.write(payload);
        output.flush();
    }

    private ReplyValues readReply() throws IOException {
        byte[] header = new byte[4];
        try {
            input.readFully(header);
        } catch (EOFException failure) {
            EOFException eof = new EOFException(
                    "Hyperion FlatBuffer reply ended before its 4-byte header was complete");
            eof.initCause(failure);
            throw eof;
        }

        long size = ((header[0] & 0xffL) << 24)
                | ((header[1] & 0xffL) << 16)
                | ((header[2] & 0xffL) << 8)
                | (header[3] & 0xffL);
        if (size == 0 || size > MAX_REPLY_PAYLOAD_SIZE) {
            throw new HyperionProtocolException(
                    "Invalid Hyperion FlatBuffer reply payload length: "
                            + Long.toUnsignedString(size));
        }

        byte[] payload = new byte[(int) size];
        try {
            input.readFully(payload);
        } catch (EOFException failure) {
            EOFException eof = new EOFException(
                    "Hyperion FlatBuffer reply ended before its "
                            + size
                            + "-byte payload was complete");
            eof.initCause(failure);
            throw eof;
        }
        return parseReply(payload);
    }

    private ReplyValues readReplyWithTimeout(String message) throws IOException {
        try {
            return readReply();
        } catch (SocketTimeoutException failure) {
            throw new HyperionTimeoutException(message, failure);
        }
    }

    private static ReplyValues parseReply(byte[] payload) throws HyperionProtocolException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
            TableBounds bounds = validateReplyRoot(buffer);
            validateScalarField(buffer, bounds, 1);
            validateScalarField(buffer, bounds, 2);
            validateStringField(buffer, bounds, 0);

            Reply reply = Reply.getRootAsReply(buffer);
            String error = reply.error();
            return new ReplyValues(error != null, error, reply.video(), reply.registered());
        } catch (HyperionProtocolException failure) {
            throw failure;
        } catch (RuntimeException | AssertionError failure) {
            throw new HyperionProtocolException(
                    "Unable to parse Hyperion FlatBuffer reply", failure);
        }
    }

    /** Performs narrow root/table bounds checks before invoking generated accessors. */
    private static TableBounds validateReplyRoot(ByteBuffer buffer)
            throws HyperionProtocolException {
        int limit = buffer.limit();
        if (limit < 8) {
            throw new HyperionProtocolException("FlatBuffer reply is too short for a root table");
        }

        long rootOffset = Integer.toUnsignedLong(buffer.getInt(0));
        if (rootOffset < 4 || rootOffset > limit - 4L) {
            throw new HyperionProtocolException(
                    "FlatBuffer reply has an invalid root offset: " + rootOffset);
        }
        int table = (int) rootOffset;
        int vtableDistance = buffer.getInt(table);
        if (vtableDistance <= 0 || vtableDistance > table) {
            throw new HyperionProtocolException(
                    "FlatBuffer reply has an invalid vtable distance: " + vtableDistance);
        }

        int vtable = table - vtableDistance;
        if (vtable > limit - 4) {
            throw new HyperionProtocolException("FlatBuffer reply vtable is truncated");
        }
        int vtableSize = Short.toUnsignedInt(buffer.getShort(vtable));
        int objectSize = Short.toUnsignedInt(buffer.getShort(vtable + 2));
        if (vtableSize < 4
                || (vtableSize & 1) != 0
                || (long) vtable + vtableSize > limit
                || objectSize < 4
                || (long) table + objectSize > limit) {
            throw new HyperionProtocolException("FlatBuffer reply has invalid table bounds");
        }
        return new TableBounds(table, vtable, vtableSize, objectSize, limit);
    }

    private static void validateScalarField(ByteBuffer buffer, TableBounds bounds, int fieldIndex)
            throws HyperionProtocolException {
        int field = fieldPosition(buffer, bounds, fieldIndex, 4);
        if (field != -1 && field > bounds.limit - 4) {
            throw new HyperionProtocolException("FlatBuffer reply scalar field is truncated");
        }
    }

    private static void validateStringField(ByteBuffer buffer, TableBounds bounds, int fieldIndex)
            throws HyperionProtocolException {
        int field = fieldPosition(buffer, bounds, fieldIndex, 4);
        if (field == -1) {
            return;
        }

        long relativeOffset = Integer.toUnsignedLong(buffer.getInt(field));
        long stringStart = (long) field + relativeOffset;
        if (relativeOffset == 0 || stringStart > bounds.limit - 4L) {
            throw new HyperionProtocolException("FlatBuffer reply has an invalid error string");
        }
        int stringLength = buffer.getInt((int) stringStart);
        long stringEnd = stringStart + 4L + stringLength;
        if (stringLength < 0
                || stringEnd >= bounds.limit
                || buffer.get((int) stringEnd) != 0) {
            throw new HyperionProtocolException("FlatBuffer reply error string is truncated");
        }
    }

    private static int fieldPosition(
            ByteBuffer buffer, TableBounds bounds, int fieldIndex, int width)
            throws HyperionProtocolException {
        int vtableEntry = bounds.vtable + 4 + fieldIndex * 2;
        if (vtableEntry + 2 > bounds.vtable + bounds.vtableSize) {
            return -1;
        }
        int offset = Short.toUnsignedInt(buffer.getShort(vtableEntry));
        if (offset == 0) {
            return -1;
        }
        if (offset < 4 || offset + width > bounds.objectSize) {
            throw new HyperionProtocolException("FlatBuffer reply field is outside its table");
        }
        long position = (long) bounds.table + offset;
        if (position + width > bounds.limit) {
            throw new HyperionProtocolException("FlatBuffer reply field is truncated");
        }
        return (int) position;
    }

    private void ensureOpen() throws SocketException {
        if (closed
                || socket.isClosed()
                || socket.isInputShutdown()
                || socket.isOutputShutdown()) {
            throw new SocketException("Hyperion FlatBuffer connection is closed");
        }
    }

    private static Socket connect(
            String host, int port, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Socket socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            return socket;
        } catch (SocketTimeoutException failure) {
            HyperionTimeoutException timeout = new HyperionTimeoutException(
                    "Timed out connecting to the Hyperion FlatBuffer server", failure);
            closeSocketAfterConstructionFailure(socket, timeout);
            throw timeout;
        } catch (IOException | RuntimeException failure) {
            closeSocketAfterConstructionFailure(socket, failure);
            throw failure;
        }
    }

    private static void validateConnectionParameters(
            String host,
            int port,
            int priority,
            String origin,
            int connectTimeoutMs,
            int readTimeoutMs) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be null or empty");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (priority < MIN_PRIORITY || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException(
                    "Priority must be between " + MIN_PRIORITY + " and " + MAX_PRIORITY);
        }
        if (origin == null || origin.trim().isEmpty()) {
            throw new IllegalArgumentException("Origin must not be null or empty");
        }
        if (connectTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connect timeout must be positive");
        }
        if (readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Read timeout must be positive");
        }
    }

    private static void validateImage(byte[] data, int width, int height) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Image width and height must be positive; width="
                            + width
                            + ", height="
                            + height);
        }

        final long pixels;
        final long rgbSize;
        final long rgb32Size;
        try {
            pixels = Math.multiplyExact((long) width, (long) height);
            rgbSize = Math.multiplyExact(pixels, 3L);
            rgb32Size = Math.multiplyExact(pixels, 4L);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "Image dimensions overflow supported RGB sizes; width="
                            + width
                            + ", height="
                            + height
                            + ", actual data length="
                            + data.length,
                    failure);
        }

        if (data.length != rgbSize && data.length != rgb32Size) {
            throw new IllegalArgumentException(
                    "Image data length must be exactly "
                            + rgbSize
                            + " bytes for RGB24 or "
                            + rgb32Size
                            + " bytes for RGB32; actual="
                            + data.length);
        }
    }

    private static HyperionServerException serverException(String message) {
        return new HyperionServerException(
                message == null || message.isEmpty()
                        ? "Hyperion rejected the FlatBuffer request"
                        : message);
    }

    private void closeAfterFailure(Throwable failure) {
        try {
            close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void closeSocketAfterConstructionFailure(Socket socket, Throwable failure) {
        try {
            socket.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private enum Operation {
        COLOR,
        IMAGE,
        CLEAR
    }

    private static final class ReplyValues {
        final boolean hasError;
        final String error;
        final int video;
        final int registered;

        ReplyValues(boolean hasError, String error, int video, int registered) {
            this.hasError = hasError;
            this.error = error;
            this.video = video;
            this.registered = registered;
        }
    }

    private static final class TableBounds {
        final int table;
        final int vtable;
        final int vtableSize;
        final int objectSize;
        final int limit;

        TableBounds(int table, int vtable, int vtableSize, int objectSize, int limit) {
            this.table = table;
            this.vtable = vtable;
            this.vtableSize = vtableSize;
            this.objectSize = objectSize;
            this.limit = limit;
        }
    }
}
