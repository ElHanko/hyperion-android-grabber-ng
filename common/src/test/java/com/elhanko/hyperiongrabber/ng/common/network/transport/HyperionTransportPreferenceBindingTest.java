package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HyperionTransportPreferenceBindingTest {
    @Test
    public void missingEmptyUnknownAndDifferentlyCasedValuesShowDisabled() {
        assertFalse(HyperionTransportPreferenceBinding.isFlatBufferEnabled(null));
        assertFalse(HyperionTransportPreferenceBinding.isFlatBufferEnabled(""));
        assertFalse(HyperionTransportPreferenceBinding.isFlatBufferEnabled("unknown"));
        assertFalse(HyperionTransportPreferenceBinding.isFlatBufferEnabled("FlatBuffer"));
    }

    @Test
    public void protobufShowsDisabledAndFlatBufferShowsEnabled() {
        assertFalse(HyperionTransportPreferenceBinding.isFlatBufferEnabled("protobuf"));
        assertTrue(HyperionTransportPreferenceBinding.isFlatBufferEnabled("flatbuffer"));
    }

    @Test
    public void deliberateControlValuesUseOnlyStablePersistedIdentifiers() {
        assertEquals("flatbuffer", HyperionTransportPreferenceBinding.persistedValue(true));
        assertEquals("protobuf", HyperionTransportPreferenceBinding.persistedValue(false));
    }

    @Test
    public void readingTheBindingDoesNotAlterInputValues() {
        String transport = "unknown";
        String port = "19500";
        HyperionTransportPreferenceBinding.isFlatBufferEnabled(transport);
        HyperionTransportPreferenceBinding.flatBufferPortOrDefault(port);
        assertEquals("unknown", transport);
        assertEquals("19500", port);
    }

    @Test
    public void missingFlatBufferPortUsesManualDefaultWithoutPersistence() {
        assertEquals("19400", HyperionTransportPreferenceBinding.flatBufferPortOrDefault(null));
        assertEquals("19400", HyperionTransportPreferenceBinding.flatBufferPortOrDefault(" "));
        assertEquals("19400", Integer.toString(
                HyperionTransportPreferenceBinding.DEFAULT_FLATBUFFER_PORT));
    }

    @Test
    public void storedFlatBufferPortIsRetained() {
        assertEquals("19500", HyperionTransportPreferenceBinding.flatBufferPortOrDefault("19500"));
    }

    @Test
    public void acceptsOnlyDecimalPortsInTcpRange() {
        assertTrue(HyperionTransportPreferenceBinding.isValidPort("1"));
        assertTrue(HyperionTransportPreferenceBinding.isValidPort("19400"));
        assertTrue(HyperionTransportPreferenceBinding.isValidPort("65535"));
        assertFalse(HyperionTransportPreferenceBinding.isValidPort(null));
        assertFalse(HyperionTransportPreferenceBinding.isValidPort(""));
        assertFalse(HyperionTransportPreferenceBinding.isValidPort("not-a-port"));
        assertFalse(HyperionTransportPreferenceBinding.isValidPort("0"));
        assertFalse(HyperionTransportPreferenceBinding.isValidPort("65536"));
    }

    @Test
    public void invalidPortsAreRejectedWithoutCorrection() {
        assertThrows(IllegalArgumentException.class,
                () -> HyperionTransportPreferenceBinding.requireValidPort("0"));
        assertThrows(IllegalArgumentException.class,
                () -> HyperionTransportPreferenceBinding.requireValidPort("invalid"));
        assertEquals(19400, HyperionTransportPreferenceBinding.requireValidPort("19400"));
    }
}
