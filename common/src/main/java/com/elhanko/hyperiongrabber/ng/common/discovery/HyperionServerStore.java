package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deduplicates resolved ProtoServer records without depending on Android NSD classes. */
public final class HyperionServerStore {
    private final Map<String, Entry> entriesByDiscoveryKey = new LinkedHashMap<>();

    public synchronized boolean put(@NonNull HyperionDiscoveryBackend.ResolvedService resolved) {
        InetAddress host = resolved.getHost();
        int port = resolved.getPort();
        if (host == null || port < 1 || port > 65535) {
            return false;
        }

        String discoveryKey = resolved.getReference().getDiscoveryKey();
        String hyperionId = decodeUtf8(resolved.getHyperionId());
        String version = decodeUtf8(resolved.getHyperionVersion());
        entriesByDiscoveryKey.put(
                discoveryKey,
                new Entry(
                        discoveryKey,
                        resolved.getReference().getServiceName(),
                        host,
                        port,
                        hyperionId,
                        version));
        return true;
    }

    public synchronized boolean remove(@NonNull String discoveryKey) {
        return entriesByDiscoveryKey.remove(discoveryKey) != null;
    }

    public synchronized void clear() {
        entriesByDiscoveryKey.clear();
    }

    @NonNull
    public synchronized List<DiscoveredHyperionServer> snapshot() {
        Map<String, List<Entry>> grouped = new LinkedHashMap<>();
        for (Entry entry : entriesByDiscoveryKey.values()) {
            grouped.computeIfAbsent(entry.stableKey(), ignored -> new ArrayList<>()).add(entry);
        }

        List<DiscoveredHyperionServer> servers = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> group : grouped.entrySet()) {
            List<Entry> entries = group.getValue();
            entries.sort(ENTRY_ORDER);
            Entry primary = entries.get(0);
            List<InetAddress> addresses = new ArrayList<>();
            for (Entry entry : entries) {
                if (!addresses.contains(entry.host)) {
                    addresses.add(entry.host);
                }
            }
            addresses.sort(ADDRESS_ORDER);

            String version = null;
            for (Entry entry : entries) {
                if (entry.version != null) {
                    version = entry.version;
                    break;
                }
            }

            servers.add(new DiscoveredHyperionServer(
                    group.getKey(),
                    primary.hyperionId,
                    primary.serviceName,
                    version,
                    addresses,
                    primary.port,
                    primary.serviceName,
                    DiscoveredHyperionServer.LastSeenState.AVAILABLE));
        }

        servers.sort(Comparator
                .comparing(DiscoveredHyperionServer::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DiscoveredHyperionServer::getHostAddressText)
                .thenComparingInt(DiscoveredHyperionServer::getProtoServerPort));
        return Collections.unmodifiableList(servers);
    }

    @Nullable
    static String decodeUtf8(@Nullable byte[] value) {
        if (value == null || value.length == 0) {
            return null;
        }
        try {
            String decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString()
                    .trim();
            return decoded.isEmpty() ? null : decoded;
        } catch (CharacterCodingException ignored) {
            return null;
        }
    }

    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing((Entry entry) -> !(entry.host instanceof Inet4Address))
            .thenComparing(entry -> normalizeHost(entry.host))
            .thenComparingInt(entry -> entry.port)
            .thenComparing(entry -> entry.serviceName, String.CASE_INSENSITIVE_ORDER);

    private static final Comparator<InetAddress> ADDRESS_ORDER = Comparator
            .comparing((InetAddress address) -> !(address instanceof Inet4Address))
            .thenComparing(HyperionServerStore::normalizeHost);

    private static String normalizeHost(InetAddress host) {
        return host.getHostAddress().toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        private final String discoveryKey;
        private final String serviceName;
        private final InetAddress host;
        private final int port;
        private final String hyperionId;
        private final String version;

        private Entry(
                String discoveryKey,
                String serviceName,
                InetAddress host,
                int port,
                String hyperionId,
                String version) {
            this.discoveryKey = discoveryKey;
            this.serviceName = serviceName;
            this.host = host;
            this.port = port;
            this.hyperionId = hyperionId;
            this.version = version;
        }

        private String stableKey() {
            if (hyperionId != null) {
                return "id:" + hyperionId;
            }
            return "endpoint:" + normalizeHost(host) + "|" + port;
        }
    }
}
