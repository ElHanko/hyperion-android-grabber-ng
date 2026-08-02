package com.elhanko.hyperiongrabber.ng.common.network.transport;

import com.elhanko.hyperiongrabber.ng.common.network.Hyperion;

import java.io.IOException;

/** Thin transport adapter around the existing Protocol Buffers client. */
public final class ProtobufHyperionTransport implements HyperionTransport {
    public static final String NAME = "Protocol Buffers";

    private final int priority;
    private final Hyperion client;

    public ProtobufHyperionTransport(HyperionTransportConfig config) throws IOException {
        priority = config.priority();
        client = new Hyperion(
                config.host(),
                config.port(),
                config.connectTimeoutMs(),
                config.readTimeoutMs());
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void setColor(int rgbColor, int durationMs) throws IOException {
        client.setColor(rgbColor, priority, durationMs);
    }

    @Override
    public void setImage(byte[] data, int width, int height, int durationMs)
            throws IOException {
        client.setImage(data, width, height, priority, durationMs);
    }

    @Override
    public void clear() throws IOException {
        client.clear(priority);
    }

    @Override
    public String transportName() {
        return NAME;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
