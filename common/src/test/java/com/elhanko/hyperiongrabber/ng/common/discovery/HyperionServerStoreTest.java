package com.elhanko.hyperiongrabber.ng.common.discovery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HyperionServerStoreTest {
    @Test
    public void resultWithHyperionIdUsesIdAndVersion() throws Exception {
        HyperionServerStore store = new HyperionServerStore();

        assertTrue(store.put(resolved("one", "Living room", "192.0.2.10", 19445,
                bytes("server-id"), bytes("2.2.1"))));

        DiscoveredHyperionServer server = store.snapshot().get(0);
        assertEquals("id:server-id", server.getStableKey());
        assertEquals("server-id", server.getHyperionId());
        assertEquals("2.2.1", server.getHyperionVersion());
        assertEquals("Living room", server.getDisplayName());
    }

    @Test
    public void resultWithoutIdFallsBackToNormalizedEndpoint() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("one", "Hyperion", "192.0.2.11", 19445, null, null));

        DiscoveredHyperionServer server = store.snapshot().get(0);
        assertEquals("endpoint:192.0.2.11|19445", server.getStableKey());
        assertNull(server.getHyperionId());
        assertNull(server.getHyperionVersion());
    }

    @Test
    public void sameIdMergesServicesAndPrefersIpv4() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("v6", "Hyperion", "2001:db8::10", 19445,
                bytes("same"), null));
        store.put(resolved("v4", "Hyperion duplicate", "192.0.2.12", 19445,
                bytes("same"), null));

        List<DiscoveredHyperionServer> servers = store.snapshot();
        assertEquals(1, servers.size());
        assertEquals(2, servers.get(0).getAddresses().size());
        assertEquals(DiscoveredHyperionServer.AddressFamily.IPV4,
                servers.get(0).getAddressFamily());
        assertEquals("192.0.2.12", servers.get(0).getHostAddressText());
    }

    @Test
    public void sameHostAndPortMergeWithoutId() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("one", "Name one", "192.0.2.13", 19445, null, null));
        store.put(resolved("two", "Name two", "192.0.2.13", 19445, null, null));

        assertEquals(1, store.snapshot().size());
    }

    @Test
    public void sameServiceNameOnDifferentHostsRemainsSeparate() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("one", "Hyperion", "192.0.2.14", 19445, null, null));
        store.put(resolved("two", "Hyperion", "192.0.2.15", 19445, null, null));

        assertEquals(2, store.snapshot().size());
    }

    @Test
    public void updatedPortForSameIdReplacesPreviousEndpoint() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("one", "Hyperion", "192.0.2.16", 19445,
                bytes("same"), null));
        store.put(resolved("one", "Hyperion", "192.0.2.16", 25000,
                bytes("same"), null));

        assertEquals(1, store.snapshot().size());
        assertEquals(25000, store.snapshot().get(0).getProtoServerPort());
    }

    @Test
    public void ipv4AndIpv6FamiliesArePreserved() throws Exception {
        HyperionServerStore ipv4Store = new HyperionServerStore();
        ipv4Store.put(resolved("v4", "IPv4", "192.0.2.17", 19445, null, null));
        assertEquals(DiscoveredHyperionServer.AddressFamily.IPV4,
                ipv4Store.snapshot().get(0).getAddressFamily());

        HyperionServerStore ipv6Store = new HyperionServerStore();
        ipv6Store.put(resolved("v6", "IPv6", "2001:db8::17", 19445, null, null));
        assertEquals(DiscoveredHyperionServer.AddressFamily.IPV6,
                ipv6Store.snapshot().get(0).getAddressFamily());
        assertTrue(ipv6Store.snapshot().get(0).getHostAddressText().contains(":"));
    }

    @Test
    public void missingHostAndInvalidPortsAreRejected() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        assertFalse(store.put(new HyperionDiscoveryBackend.ResolvedService(
                reference("missing", "Missing"), null, 19445, null, null)));
        assertFalse(store.put(resolved("zero", "Zero", "192.0.2.18", 0, null, null)));
        assertFalse(store.put(resolved("large", "Large", "192.0.2.18", 65536, null, null)));
        assertTrue(store.snapshot().isEmpty());
    }

    @Test
    public void missingAndInvalidTxtRecordsDoNotCrash() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28};
        store.put(resolved("one", "Hyperion", "192.0.2.19", 19445,
                invalidUtf8, invalidUtf8));

        DiscoveredHyperionServer server = store.snapshot().get(0);
        assertNull(server.getHyperionId());
        assertNull(server.getHyperionVersion());
        assertEquals("endpoint:192.0.2.19|19445", server.getStableKey());
    }

    @Test
    public void removingLostServiceUpdatesOrRemovesGroup() throws Exception {
        HyperionServerStore store = new HyperionServerStore();
        store.put(resolved("one", "Hyperion", "192.0.2.20", 19445,
                bytes("same"), null));
        store.put(resolved("two", "Hyperion", "2001:db8::20", 19445,
                bytes("same"), null));

        assertTrue(store.remove("one"));
        assertEquals(1, store.snapshot().size());
        assertEquals(DiscoveredHyperionServer.AddressFamily.IPV6,
                store.snapshot().get(0).getAddressFamily());
        assertTrue(store.remove("two"));
        assertTrue(store.snapshot().isEmpty());
    }

    private static HyperionDiscoveryBackend.ResolvedService resolved(
            String key,
            String name,
            String host,
            int port,
            byte[] id,
            byte[] version) throws Exception {
        return new HyperionDiscoveryBackend.ResolvedService(
                reference(key, name), InetAddress.getByName(host), port, id, version);
    }

    private static HyperionDiscoveryBackend.ServiceReference reference(String key, String name) {
        return new HyperionDiscoveryBackend.ServiceReference(key, name);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
