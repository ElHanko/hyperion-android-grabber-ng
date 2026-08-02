package com.elhanko.hyperiongrabber.ng.common.network.transport;

/** Stable transport choices and their future preference values. */
public enum HyperionTransportType {
    PROTOBUF("protobuf", "Protocol Buffers"),
    FLATBUFFER("flatbuffer", "FlatBuffer (experimental)");

    public static final HyperionTransportType DEFAULT = PROTOBUF;

    private final String persistedValue;
    private final String displayName;

    HyperionTransportType(String persistedValue, String displayName) {
        this.persistedValue = persistedValue;
        this.displayName = displayName;
    }

    public String persistedValue() {
        return persistedValue;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Parses an exact, case-sensitive persisted value.
     *
     * <p>Missing, empty, differently cased, and unknown values resolve to the stable Protocol
     * Buffers default. No host or port participates in this decision.</p>
     */
    public static HyperionTransportType fromPersistedValue(String value) {
        for (HyperionTransportType type : values()) {
            if (type.persistedValue.equals(value)) {
                return type;
            }
        }
        return DEFAULT;
    }
}
