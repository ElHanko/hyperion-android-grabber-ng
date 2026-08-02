package com.elhanko.hyperiongrabber.ng.common.network;

import java.io.IOException;

/** Indicates invalid framing or protocol content from a Hyperion server. */
public final class HyperionProtocolException extends IOException {
    public HyperionProtocolException(String message) {
        super(message);
    }

    public HyperionProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
