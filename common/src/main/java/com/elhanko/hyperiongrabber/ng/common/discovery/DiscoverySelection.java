package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;

/** Applies only an explicit, valid Protocol Buffers server selection. */
public final class DiscoverySelection {
    private DiscoverySelection() {
    }

    public static void save(
            @NonNull DiscoveredHyperionServer server,
            @NonNull HostPortStore store) {
        int port = server.getProtoServerPort();
        String host = server.getHostAddressText();
        if (host.isEmpty() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("The selected ProtoServer endpoint is invalid");
        }
        store.putHostAndPort(host, port);
    }
}
