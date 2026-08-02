package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;

/** Atomic persistence boundary for an explicitly selected server endpoint. */
public interface HostPortStore {
    void putHostAndPort(@NonNull String host, int port);
}
