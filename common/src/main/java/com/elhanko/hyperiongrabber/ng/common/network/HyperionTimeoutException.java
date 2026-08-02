package com.elhanko.hyperiongrabber.ng.common.network;

import java.net.SocketTimeoutException;

/** Indicates that a connected Hyperion server did not complete its reply in time. */
public final class HyperionTimeoutException extends SocketTimeoutException {
    public HyperionTimeoutException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
