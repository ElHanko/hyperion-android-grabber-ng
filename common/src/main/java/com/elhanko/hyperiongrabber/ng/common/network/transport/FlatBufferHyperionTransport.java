package com.elhanko.hyperiongrabber.ng.common.network.transport;

import com.elhanko.hyperiongrabber.ng.common.network.flatbuffer.FlatBufferHyperionClient;

import java.io.IOException;

/** Thin transport adapter around the isolated experimental FlatBuffer client. */
public final class FlatBufferHyperionTransport implements HyperionTransport {
    public static final String NAME = HyperionTransportType.FLATBUFFER.displayName();

    private final FlatBufferHyperionClient client;

    public FlatBufferHyperionTransport(HyperionTransportConfig config) throws IOException {
        config.requireFlatBufferOrigin();
        client = new FlatBufferHyperionClient(
                config.host(),
                config.port(),
                config.priority(),
                config.origin(),
                config.connectTimeoutMs(),
                config.readTimeoutMs());
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void setColor(int rgbColor, int durationMs) throws IOException {
        client.setColor(rgbColor, durationMs);
    }

    @Override
    public void setImage(byte[] data, int width, int height, int durationMs)
            throws IOException {
        client.setImage(data, width, height, durationMs);
    }

    @Override
    public void clear() throws IOException {
        client.clear();
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
