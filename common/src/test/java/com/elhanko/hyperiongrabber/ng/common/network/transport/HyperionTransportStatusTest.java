package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class HyperionTransportStatusTest {
    @Test
    public void protocolBuffersNameIsPresented() {
        assertEquals("Connected using Protocol Buffers",
                HyperionTransportStatus.connectedUsing("Protocol Buffers"));
    }

    @Test
    public void experimentalFlatBufferNameIsPresentedUnchanged() {
        assertEquals("Connected using FlatBuffer (experimental)",
                HyperionTransportStatus.connectedUsing("FlatBuffer (experimental)"));
    }

    @Test
    public void missingTransportNamePreservesLegacyStatusBehavior() {
        assertNull(HyperionTransportStatus.connectedUsing(null));
        assertNull(HyperionTransportStatus.connectedUsing(" "));
    }

    @Test
    public void errorUsesServiceReportedTransportContext() {
        assertEquals("FlatBuffer (experimental): Could not connect",
                HyperionTransportStatus.formatError(
                        "FlatBuffer (experimental)", "Could not connect"));
    }

    @Test
    public void missingTransportNamePreservesExistingErrorText() {
        assertEquals("Could not connect",
                HyperionTransportStatus.formatError(null, "Could not connect"));
        assertNull(HyperionTransportStatus.formatError("Protocol Buffers", null));
    }

    @Test
    public void unknownBroadcastTransportRemainsSafeDisplayContext() {
        assertEquals("Connected using future transport",
                HyperionTransportStatus.connectedUsing("future transport"));
    }
}
