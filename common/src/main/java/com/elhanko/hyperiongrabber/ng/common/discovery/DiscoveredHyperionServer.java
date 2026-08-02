package com.elhanko.hyperiongrabber.ng.common.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable Android-independent description of a discovered Hyperion ProtoServer. */
public final class DiscoveredHyperionServer {
    public enum AddressFamily {
        IPV4,
        IPV6,
        UNKNOWN
    }

    public enum LastSeenState {
        AVAILABLE,
        LOST
    }

    private final String stableKey;
    private final String hyperionId;
    private final String displayName;
    private final String hyperionVersion;
    private final List<InetAddress> addresses;
    private final InetAddress hostAddress;
    private final int protoServerPort;
    private final String serviceName;
    private final LastSeenState lastSeenState;

    DiscoveredHyperionServer(
            @NonNull String stableKey,
            @Nullable String hyperionId,
            @NonNull String displayName,
            @Nullable String hyperionVersion,
            @NonNull List<InetAddress> addresses,
            int protoServerPort,
            @NonNull String serviceName,
            @NonNull LastSeenState lastSeenState) {
        this.stableKey = stableKey;
        this.hyperionId = hyperionId;
        this.displayName = displayName;
        this.hyperionVersion = hyperionVersion;
        this.addresses = Collections.unmodifiableList(new ArrayList<>(addresses));
        this.hostAddress = choosePreferredAddress(this.addresses);
        this.protoServerPort = protoServerPort;
        this.serviceName = serviceName;
        this.lastSeenState = lastSeenState;
    }

    @NonNull
    public String getStableKey() {
        return stableKey;
    }

    @Nullable
    public String getHyperionId() {
        return hyperionId;
    }

    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    public String getHyperionVersion() {
        return hyperionVersion;
    }

    @NonNull
    public List<InetAddress> getAddresses() {
        return addresses;
    }

    @NonNull
    public InetAddress getHostAddress() {
        return hostAddress;
    }

    @NonNull
    public String getHostAddressText() {
        return hostAddress.getHostAddress();
    }

    @NonNull
    public AddressFamily getAddressFamily() {
        if (hostAddress instanceof Inet4Address) {
            return AddressFamily.IPV4;
        }
        if (hostAddress instanceof Inet6Address) {
            return AddressFamily.IPV6;
        }
        return AddressFamily.UNKNOWN;
    }

    public int getProtoServerPort() {
        return protoServerPort;
    }

    @NonNull
    public String getServiceName() {
        return serviceName;
    }

    @NonNull
    public LastSeenState getLastSeenState() {
        return lastSeenState;
    }

    private static InetAddress choosePreferredAddress(List<InetAddress> addresses) {
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("At least one host address is required");
        }
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address && isUsable(address)) {
                return address;
            }
        }
        for (InetAddress address : addresses) {
            if (address instanceof Inet4Address) {
                return address;
            }
        }
        for (InetAddress address : addresses) {
            if (isUsable(address)) {
                return address;
            }
        }
        return addresses.get(0);
    }

    private static boolean isUsable(InetAddress address) {
        return !address.isAnyLocalAddress() && !address.isLoopbackAddress();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiscoveredHyperionServer)) {
            return false;
        }
        DiscoveredHyperionServer that = (DiscoveredHyperionServer) other;
        return protoServerPort == that.protoServerPort
                && stableKey.equals(that.stableKey)
                && Objects.equals(hyperionId, that.hyperionId)
                && displayName.equals(that.displayName)
                && Objects.equals(hyperionVersion, that.hyperionVersion)
                && addresses.equals(that.addresses)
                && serviceName.equals(that.serviceName)
                && lastSeenState == that.lastSeenState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stableKey,
                hyperionId,
                displayName,
                hyperionVersion,
                addresses,
                protoServerPort,
                serviceName,
                lastSeenState);
    }
}
