package com.elhanko.hyperiongrabber.ng.common.network.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class HyperionTransportTypeConfigTest {
    @Test
    public void parsesExactProtobufValue() {
        assertSame(
                HyperionTransportType.PROTOBUF,
                HyperionTransportType.fromPersistedValue("protobuf"));
    }

    @Test
    public void parsesExactFlatBufferValue() {
        assertSame(
                HyperionTransportType.FLATBUFFER,
                HyperionTransportType.fromPersistedValue("flatbuffer"));
    }

    @Test
    public void missingEmptyAndUnknownValuesUseProtobufDefault() {
        assertSame(HyperionTransportType.PROTOBUF, HyperionTransportType.DEFAULT);
        assertSame(HyperionTransportType.PROTOBUF,
                HyperionTransportType.fromPersistedValue(null));
        assertSame(HyperionTransportType.PROTOBUF,
                HyperionTransportType.fromPersistedValue(""));
        assertSame(HyperionTransportType.PROTOBUF,
                HyperionTransportType.fromPersistedValue("unknown"));
    }

    @Test
    public void persistedValuesAreStableAndCaseSensitive() {
        assertEquals("protobuf", HyperionTransportType.PROTOBUF.persistedValue());
        assertEquals("flatbuffer", HyperionTransportType.FLATBUFFER.persistedValue());
        assertSame(HyperionTransportType.PROTOBUF,
                HyperionTransportType.fromPersistedValue("FlatBuffer"));
    }

    @Test
    public void transportTypeHasNoPortBasedSelectionApi() {
        for (java.lang.reflect.Method method : HyperionTransportType.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse("Transport type must not be selected by a numeric port",
                        parameter == int.class || parameter == Integer.class);
            }
        }
    }

    @Test
    public void acceptsAndRetainsValidConfiguration() {
        HyperionTransportConfig config = config("origin");

        assertEquals("127.0.0.1", config.host());
        assertEquals(19_445, config.port());
        assertEquals(150, config.priority());
        assertEquals("origin", config.origin());
        assertEquals(1_000, config.connectTimeoutMs());
        assertEquals(2_000, config.readTimeoutMs());
    }

    @Test
    public void configurationFieldsArePrivateAndFinal() {
        assertTrue(Modifier.isFinal(HyperionTransportConfig.class.getModifiers()));
        for (Field field : HyperionTransportConfig.class.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertTrue(Modifier.isFinal(field.getModifiers()));
        }
    }

    @Test
    public void rejectsNullOrEmptyHost() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig(null, 1, 150, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("  ", 1, 150, null, 1, 1));
    }

    @Test
    public void rejectsPortsOutsideValidRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 0, 150, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 65_536, 150, null, 1, 1));
    }

    @Test
    public void rejectsPrioritiesOutsideHyperionInputRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 1, 99, null, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 1, 200, null, 1, 1));
    }

    @Test
    public void rejectsNonPositiveConnectTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 1, 150, null, 0, 1));
    }

    @Test
    public void rejectsNonPositiveReadTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new HyperionTransportConfig("host", 1, 150, null, 1, 0));
    }

    @Test
    public void configurationAllowsMissingOriginUntilTransportSelection() {
        HyperionTransportConfig config = config(null);
        assertNull(config.origin());
        assertThrows(IllegalArgumentException.class, config::requireFlatBufferOrigin);
    }

    private static HyperionTransportConfig config(String origin) {
        return new HyperionTransportConfig(
                "127.0.0.1", 19_445, 150, origin, 1_000, 2_000);
    }
}
