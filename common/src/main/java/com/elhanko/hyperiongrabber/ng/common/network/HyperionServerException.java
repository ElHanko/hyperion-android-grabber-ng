package com.elhanko.hyperiongrabber.ng.common.network;

import java.io.IOException;

/** Indicates a valid negative reply returned by the Hyperion server. */
public final class HyperionServerException extends IOException {
    public HyperionServerException(String message) {
        super(message);
    }
}
