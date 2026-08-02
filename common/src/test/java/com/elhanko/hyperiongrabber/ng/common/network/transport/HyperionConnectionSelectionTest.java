package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoveredHyperionServer;
import com.elhanko.hyperiongrabber.ng.common.discovery.DiscoverySelection;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class HyperionConnectionSelectionTest {
    @Test
    public void missingTransportUsesProtobuf() {
        assertType(null, HyperionTransportType.PROTOBUF);
    }

    @Test
    public void emptyTransportUsesProtobuf() {
        assertType("", HyperionTransportType.PROTOBUF);
    }

    @Test
    public void unknownTransportUsesProtobuf() {
        assertType("unknown", HyperionTransportType.PROTOBUF);
    }

    @Test
    public void exactProtobufValueSelectsProtobuf() {
        assertType("protobuf", HyperionTransportType.PROTOBUF);
    }

    @Test
    public void exactFlatBufferValueSelectsFlatBuffer() {
        assertType("flatbuffer", HyperionTransportType.FLATBUFFER);
    }

    @Test
    public void differentlyCasedFlatBufferValueUsesProtobuf() {
        assertType("FlatBuffer", HyperionTransportType.PROTOBUF);
    }

    @Test
    public void protobufUsesExistingProtobufPort() {
        HyperionConnectionSelection selection = resolve("protobuf", "19445", "19400");
        assertEquals(19_445, selection.transportConfig().port());
    }

    @Test
    public void flatBufferUsesSeparateFlatBufferPort() {
        HyperionConnectionSelection selection = resolve("flatbuffer", "19445", "19500");
        assertEquals(19_500, selection.transportConfig().port());
    }

    @Test
    public void missingOrEmptyFlatBufferPortUsesManualDefault() {
        assertEquals(19_400, resolve("flatbuffer", "broken", null).transportConfig().port());
        assertEquals(19_400, resolve("flatbuffer", "broken", " ").transportConfig().port());
    }

    @Test
    public void invalidUnselectedFlatBufferPortDoesNotBlockProtobuf() {
        HyperionConnectionSelection selection = resolve("protobuf", "19445", "broken");
        assertSame(HyperionTransportType.PROTOBUF, selection.transportType());
        assertEquals(19_445, selection.transportConfig().port());
    }

    @Test
    public void invalidUnselectedProtobufPortDoesNotBlockFlatBuffer() {
        HyperionConnectionSelection selection = resolve("flatbuffer", "broken", "19400");
        assertSame(HyperionTransportType.FLATBUFFER, selection.transportType());
        assertEquals(19_400, selection.transportConfig().port());
    }

    @Test
    public void invalidSelectedPortIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> resolve("protobuf", "broken", "19400"));
        assertThrows(IllegalArgumentException.class,
                () -> resolve("flatbuffer", "19445", "70000"));
    }

    @Test
    public void sharedHostIsRetainedAndUnspecifiedHostRejected() {
        assertEquals("hyperion.local",
                resolve("protobuf", "19445", "19400").transportConfig().host());
        assertThrows(IllegalArgumentException.class,
                () -> HyperionConnectionSelection.resolve(
                        "protobuf", "0.0.0.0", "19445", null, "150", 1000, 2000));
    }

    @Test
    public void priorityIsRetainedAndValidated() {
        HyperionConnectionSelection selection = HyperionConnectionSelection.resolve(
                "protobuf", "host", "19445", null, "199", 1000, 2000);
        assertEquals(199, selection.transportConfig().priority());
        assertThrows(IllegalArgumentException.class,
                () -> HyperionConnectionSelection.resolve(
                        "protobuf", "host", "19445", null, "bad", 1000, 2000));
    }

    @Test
    public void flatBufferUsesFixedNonPersonalOrigin() {
        HyperionConnectionSelection selection = resolve("flatbuffer", "19445", "19400");
        assertEquals("Hyperion Android Grabber NG", selection.transportConfig().origin());
    }

    @Test
    public void protobufDoesNotRequireOrReceiveOrigin() {
        HyperionConnectionSelection selection = resolve("protobuf", "19445", "bad");
        assertNull(selection.transportConfig().origin());
    }

    @Test
    public void resolutionDoesNotMutateStoredPortStrings() {
        String protobufPort = "19445";
        String flatBufferPort = "19400";
        resolve("flatbuffer", protobufPort, flatBufferPort);
        assertEquals("19445", protobufPort);
        assertEquals("19400", flatBufferPort);
    }

    @Test
    public void discoveryModelRemainsProtocolBuffersSpecific() {
        boolean protoPortFound = false;
        for (Field field : DiscoveredHyperionServer.class.getDeclaredFields()) {
            protoPortFound |= field.getName().equals("protoServerPort");
            assertFalse(field.getName().toLowerCase().contains("flatbuffer"));
            assertFalse(field.getName().toLowerCase().contains("transporttype"));
        }
        assertEquals(true, protoPortFound);
        for (Method method : DiscoverySelection.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == HyperionTransportType.class);
                assertFalse(parameter == HyperionTransportConfig.class);
            }
        }
    }

    private static void assertType(String value, HyperionTransportType expected) {
        assertSame(expected, resolve(value, "19445", "19400").transportType());
    }

    private static HyperionConnectionSelection resolve(
            String type, String protobufPort, String flatBufferPort) {
        return HyperionConnectionSelection.resolve(
                type,
                "hyperion.local",
                protobufPort,
                flatBufferPort,
                "150",
                1_000,
                2_000);
    }
}
