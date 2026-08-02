package com.elhanko.hyperiongrabber.ng.common.network.transport;

import java.io.Closeable;
import java.io.IOException;

/** Small transport-neutral surface for sending screen-grabber data to Hyperion. */
public interface HyperionTransport extends Closeable {
    /** Returns true only when the transport is ready for normal requests. */
    boolean isConnected();

    /** Sends a packed {@code 0xRRGGBB} color and its duration in milliseconds. */
    void setColor(int rgbColor, int durationMs) throws IOException;

    /** Sends an unmodified RGB24 or RGB32/RGBA image. */
    void setImage(byte[] data, int width, int height, int durationMs) throws IOException;

    /** Clears only the priority configured when this transport was created. */
    void clear() throws IOException;

    /** Returns a stable English name suitable for status and error context. */
    String transportName();

    @Override
    void close() throws IOException;
}
