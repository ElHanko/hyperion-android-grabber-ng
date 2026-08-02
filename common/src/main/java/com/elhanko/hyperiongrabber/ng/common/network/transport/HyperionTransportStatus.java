package com.elhanko.hyperiongrabber.ng.common.network.transport;

/** Formats optional transport context supplied by the running capture service. */
public final class HyperionTransportStatus {
    private HyperionTransportStatus() {
    }

    /** Returns a displayable active-transport status, or {@code null} for older broadcasts. */
    public static String connectedUsing(String transportName) {
        if (isBlank(transportName)) {
            return null;
        }
        return "Connected using " + transportName;
    }

    /** Adds service-provided transport context without changing an existing error message. */
    public static String formatError(String transportName, String error) {
        if (error == null) {
            return null;
        }
        if (isBlank(transportName)) {
            return error;
        }
        return transportName + ": " + error;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
