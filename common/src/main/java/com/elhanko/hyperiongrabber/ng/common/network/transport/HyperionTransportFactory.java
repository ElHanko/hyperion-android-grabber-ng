package com.elhanko.hyperiongrabber.ng.common.network.transport;

import java.io.IOException;

/** Stateless factory that creates exactly the explicitly selected transport. */
public final class HyperionTransportFactory {
    public HyperionTransport create(
            HyperionTransportType type, HyperionTransportConfig config) throws IOException {
        if (config == null) {
            throw new NullPointerException("config");
        }

        HyperionTransportType selected = type == null ? HyperionTransportType.DEFAULT : type;
        switch (selected) {
            case PROTOBUF:
                return new ProtobufHyperionTransport(config);
            case FLATBUFFER:
                return new FlatBufferHyperionTransport(config);
            default:
                throw new AssertionError("Unhandled Hyperion transport type: " + selected);
        }
    }
}
