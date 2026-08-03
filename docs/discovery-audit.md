# Hyperion server discovery audit

## Scope and evidence

This document contains the original discovery audit and the architecture that was
implemented from it during Phase 3. The sections beginning with **Original
implementation audit** preserve the pre-implementation findings and proposal;
the **Implemented Phase 3 architecture** section records the current repository
state. Phase 3A is completed, and the discovery work is released as part of
version 2.2.0. Protocol Buffers remains the stable production transport. Manual
host and port configuration remains supported and discovery remains optional.

The Hyperion findings below were verified against the official `hyperion-project/hyperion.ng` tag `2.2.1`, in particular:

- [`include/mdns/MdnsServiceRegister.h`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/include/mdns/MdnsServiceRegister.h)
- [`libsrc/mdns/MdnsProvider.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/mdns/MdnsProvider.cpp)
- [`libsrc/protoserver/ProtoServer.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/protoserver/ProtoServer.cpp)
- [`libsrc/flatbufserver/FlatBufferServer.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferServer.cpp)
- [`libsrc/jsonserver/JsonServer.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/jsonserver/JsonServer.cpp)
- [`libsrc/webserver/WebServer.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/webserver/WebServer.cpp)
- the corresponding configuration schemas under [`libsrc/hyperion/schema`](https://github.com/hyperion-project/hyperion.ng/tree/2.2.1/libsrc/hyperion/schema)

No private address or local network configuration is used in this document.

## Implemented Phase 3 architecture

The shared `common` module now provides Android NSD discovery for the usable
Hyperion ProtoServer endpoint. `AndroidNsdDiscoveryBackend` calls
`NsdManager.discoverServices()` with `NsdManager.PROTOCOL_DNS_SD` and exactly
`_hyperiond-protobuf._tcp.`. It does not browse the FlatBuffer, JSON, HTTP, or
HTTPS service types. A resolved SRV port is retained as published; `19445`
remains only the default for manual configuration.

The implementation is divided into the following responsibilities:

| Component | Implemented responsibility |
| --- | --- |
| `AndroidNsdDiscoveryBackend` | Owns the Android discovery listener, legacy API-30 resolution calls, TXT extraction, and short-lived multicast lock. |
| `HyperionDiscoveryController` | Owns one generation, rejects parallel starts, serializes resolves, removes lost services, and ignores stale callbacks. |
| `HyperionServerStore` | Validates resolved records, strictly decodes UTF-8 TXT values, groups results, retains addresses, and produces deterministic immutable snapshots. |
| `DiscoveredHyperionServer` | Exposes Android-independent identity, display metadata, address family, all observed addresses, selected ProtoServer endpoint, and availability state. |
| `HyperionDiscovery` | Provides the shared application-facing `start()`, `stop()`, `isRunning()`, result, and close operations with UI callbacks on the main thread. |
| `DiscoverySelection` and `HostPortStore` | Validate an explicit selection and write the discovered host and SRV port together without changing preferences during search. |

Each found service is placed in a de-duplicated queue. The controller resolves
exactly one service at a time, advances after either success or failure, clears
pending work on stop, and removes queued or resolved entries after
`onServiceLost()`. A monotonically increasing generation prevents callbacks
from an earlier run from changing a later run.

TXT attributes are read only from the resolved `NsdServiceInfo`. The `id` and
`version` values use a strict UTF-8 decoder; missing or malformed values are
treated as absent. Results group first by a non-empty Hyperion `id` and otherwise
by normalized host and resolved port. Multiple addresses observed for one ID are
retained, with a usable IPv4 address preferred for the existing preference
format and IPv6 preserved as an `InetAddress` until selection.

The common manifest declares only the additional normal
`CHANGE_WIFI_MULTICAST_STATE` permission needed for discovery. The backend
acquires a non-reference-counted `WifiManager.MulticastLock` immediately before
starting NSD and releases it on cancellation, start failure, stop failure,
successful stop, or owner destruction. The lock is not used by normal grabber
operation.

Mobile settings now expose a **Find Hyperion servers** action. TV onboarding and
TV settings use the same discovery component and result adapter. Both flows
require the user to press **Start search**, allow cancellation and retry, show
multiple resolved endpoints, retain manual setup, and save nothing until a row
is explicitly selected. Selection stores the host and discovered port together
and does not start the grabber.

The former `/24` subnet probe has been removed together with `NetworkScanner`,
`HyperionScannerTask`, `ScanResultActivity`, and their unused resources and
dependencies.

## Automated validation

Offline JVM tests cover result validation and grouping, IPv4 and IPv6 behavior,
strict TXT decoding, service loss, serialized resolution, generation isolation,
repeated start/stop, synchronous start failure, and atomic explicit selection.
The tests use platform-independent fakes and do not require an Android device,
LAN, or external Hyperion server.

## Real Fire TV validation

The implemented discovery flow was successfully validated on August 2, 2026,
using an Amazon Fire TV Stick 4K Max, model AFTKRT, running Fire OS 8.1.8.0 and
Android API 30. The observed flow confirmed:

- successful application startup and opening of TV setup and discovery;
- discovery of a real Hyperion NG 2.2.1 server through Android NSD/mDNS and
  `_hyperiond-protobuf._tcp.`;
- resolution of the advertised Protocol Buffers SRV port `19445`;
- explicit server selection and transfer of the discovered host and port into
  the existing configuration;
- connection through the existing Protocol Buffers transport, followed by
  screen capture and LED output;
- retry and cancellation behavior;
- leaving the discovery view without a stale active search remaining;
- continued availability of manual host and port configuration;
- usable Fire TV D-pad navigation;
- no observed crash or fatal exception during the tested flow.

This validation covers the TV application only. It does not claim Mobile
hardware validation. Protocol Buffers remains the production default, and
FlatBuffer was neither enabled nor tested as part of this validation.

## Remaining limitations

- Discovery depends on mDNS being advertised and allowed by the local network.
  Client isolation, multicast filtering, VLAN boundaries, or device-specific NSD
  behavior can still prevent results.
- API 30 resolution exposes one host address per resolved `NsdServiceInfo`; it
  does not provide the complete address list available through newer APIs.
- Only the usable Protocol Buffers service is browsed. FlatBuffer discovery is
  not implemented; the separate experimental FlatBuffer transport does not
  participate in discovery.
- Manual host and port configuration remains the complete fallback and is not
  replaced by discovery.
- The discovery flow has not been validated on Mobile hardware.

## Original implementation audit

### Classes and callers

The current automatic search is confined to the TV onboarding flow:

| Component | Current responsibility |
| --- | --- |
| `common/.../network/NetworkScanner.java` | Builds an IPv4 address list and tries a TCP connection to every address on port `19445`. |
| `common/.../util/HyperionScannerTask.java` | Runs the scanner in a deprecated `AsyncTask`, reports progress, and returns the first address that accepts a connection. |
| `tv/.../activities/NetworkScanActivity.java` | Starts the task, displays progress, and forwards the first result to the result activity. This is the only scanner caller. |
| `tv/.../activities/ScanResultActivity.java` | Displays one address, performs color-preview requests, and saves the selected host and fixed port. |
| `tv/.../activities/MainActivity.java` | Opens TV onboarding when either the saved host or saved port is absent. |
| `tv/.../activities/ManualSetupActivity.java` | Hosts the guided manual settings flow. |
| `tv/.../fragments/settings/BasicSettingsStepFragment.kt` | Edits and tests the TV host, port, priority, and other settings. |
| `tv/.../fragments/settings/SettingsStepBaseFragment.kt` | Supplies the guided-field parsing used by the TV manual form. |
| `mobile/.../MainActivity.java` | Opens mobile settings from the options menu; it does not initiate onboarding or discovery. |
| `mobile/.../SettingsActivity.java` | Exposes manual host and port fields through the shared preference resource. It has no discovery entry point. |
| `common/src/main/res/xml/pref_general.xml` | Defines the shared mobile host and port preference fields. |
| `common/.../util/Preferences.kt` | Stores host and port as strings in default `SharedPreferences`; integer access parses the stored string. |
| `common/.../HyperionScreenService.java` | Reads the selected host and port for the production Protocol Buffers connection. |
| `mobile/.../HyperionGrabberTileService.java` | Checks whether host and port exist before starting from the quick tile. |

The shared preference keys are `pref_key_host` and `pref_key_port`. The default port in `common/src/main/res/values/pref_values.xml` is `19445`. Both the TV and mobile manual forms use these same settings, so a discovered Protocol Buffers endpoint can be adopted without a preference migration.

### How the scan works

1. `NetworkScanner` enumerates all non-loopback `NetworkInterface` addresses.
2. Only IPv4 addresses are used. An IPv6 enumeration branch exists but is never called.
3. Every local IPv4 address is treated as if it belonged to a `/24` network. The first three octets are retained and `.1` through `.254` are generated.
4. Addresses numerically close to the local last octet are tried first. Address lists from multiple interfaces are interleaved.
5. A blocking `Socket.connect` is attempted sequentially with a 50 ms timeout and the fixed port `19445`.
6. The first endpoint accepting TCP is returned. No Hyperion request is sent and no Hyperion reply is checked.
7. TV onboarding immediately leaves the search screen, displays that single endpoint, and offers confirmation. Confirmation writes the address and `19445` to the existing preferences.

### Weaknesses

- A listening TCP port is treated as proof of a Hyperion ProtoServer. Any other service on `19445` is a false positive.
- The configured ProtoServer port is never discovered. Servers using a non-default port cannot be found.
- The `/24` assumption ignores actual interface prefixes and fails on other subnet sizes. It can also scan irrelevant VPN or virtual interfaces.
- IPv6 is not searched. The unused helper removes an IPv6 zone suffix, which would make a link-local address unusable.
- Search is sequential, potentially slow, and generates avoidable connection traffic across every assumed subnet.
- Failed sockets are not closed in a `finally` block and are left for garbage collection.
- Broad exceptions are swallowed, so the UI cannot distinguish no network, invalid interface data, cancellation, timeout, or another failure.
- Only the first endpoint is retained. Multiple servers, multiple interfaces, duplicate announcements, updates, and removals cannot be represented.
- `HyperionScannerTask` does not check `isCancelled()`, implement `onCancelled()`, or expose ownership of the task to the activity.
- Leaving the screen does not stop the scan. The weak listener avoids one strong activity reference, but the background work continues and its resource use outlives the UI.
- `isScanning` becomes true only after the first progress callback. Rapid repeated activation can enqueue duplicate `AsyncTask` scans before the UI state changes.
- Rotation or recreation has no explicit session ownership. Results belong to the old task rather than a retained, generation-aware scan session.
- Progress never represents discovery state accurately; it is only the fraction of guessed IPv4 addresses attempted.
- TV and mobile manual forms verify that the port is numeric, but do not enforce the valid TCP range `1..65535`. The screen service rejects only the sentinel `-1`, and malformed stored integer text can escape `Preferences.getInt()` as a parsing error.
- The TV result preview uses a hard-coded priority of `50`. Current transport validation accepts priorities `100` through `199`, and the preview catches `IOException` but not the resulting argument error. This should be corrected with the later UI integration, not folded into discovery itself.
- Mobile has manual configuration only, while TV discovery is available only when initial configuration is missing. There is no reusable discovery component for either settings screen.

### Reuse and replacement

Reuse:

- the existing `pref_key_host` and `pref_key_port` settings and `Preferences` wrapper;
- the manual TV and mobile configuration paths;
- the production `Hyperion` connection and its tested Protocol Buffers transport, after a user selects an endpoint;
- the TV onboarding decision in `MainActivity`, adapted to present discovery as an option rather than a prerequisite.

Replace:

- subnet enumeration, address guessing, and TCP-port probing in `NetworkScanner`;
- `HyperionScannerTask` and its `AsyncTask` lifecycle;
- the single-result handoff between `NetworkScanActivity` and `ScanResultActivity`;
- fixed-port assumptions in the discovery result path.

The old scanner should be removed only after the new implementation and device validation are complete. It is not removed by this audit.

## Hyperion NG 2.2.1 discovery services

Hyperion maps internal service keys to the following fully qualified DNS-SD types:

| Server | Internal key | Advertised type in Hyperion source | Published instance name | Default port | Meaning for this app |
| --- | --- | --- | --- | ---: | --- |
| ProtoServer | `protobuffer` | `_hyperiond-protobuf._tcp.local.` | local host name | `19445` | Required and selectable production transport. |
| FlatBuffer server | `flatbuffer` | `_hyperiond-flatbuf._tcp.local.` | local host name | `19400` | May be displayed as metadata, but is not selectable or implemented in this phase. |
| JSON server | `jsonapi` | `_hyperiond-json._tcp.local.` | local host name | `19444` | Management/API service; not the screen-grabber transport. |
| HTTP web server | `http` | `_http._tcp.local.` | `Hyperion@` followed by the local host name | `8090` | Generic web service type; useful only after Hyperion identity is verified. |
| HTTPS web server | `https` | `_https._tcp.local.` | `Hyperion@` followed by the local host name | `8092` | Generic secure web service type; not the grabber transport. |

For Android `NsdManager`, the browse string should use the service type without the `.local.` domain, for example `_hyperiond-protobuf._tcp.`. Android's documentation uses both a trailing-dot and no-trailing-dot form in examples; the implementation should centralize and device-test one canonical form, beginning with the documented `_hyperiond-protobuf._tcp.` form.

### Runtime ports and publication

The numbers in the table are schema defaults, not discovery constants. Each server emits its actual bound port to `MdnsProvider` only after it has started listening successfully. Web server port selection can also move away from its configured default when the port is unavailable. A client must therefore use the resolved SRV port and must never replace it with a hard-coded default.

Publication also depends on the Hyperion build having mDNS and the respective server enabled and available. The absence of an announcement does not prove that no manually reachable Hyperion server exists.

The inspected individual server stop paths do not explicitly withdraw their record from `MdnsProvider`; the provider clears all records when it is stopped, and a later successful bind updates a type. A discovered record is therefore a location hint, not a health guarantee. The Android client must handle `onServiceLost`, resolution failure, and a later TCP connection failure without silently choosing another endpoint.

### Names and TXT records

`MdnsProvider` publishes exactly these TXT attributes for every service type:

| Key | Value source | Use |
| --- | --- | --- |
| `id` | `AuthManager::getID()`, the daemon UUID stored in Hyperion metadata | Primary cross-service and cross-address server identity. It can be empty if the manager is unavailable, so a fallback is still required. |
| `version` | `HYPERION_VERSION` | Display and diagnostics. It must not be treated as authorization or as proof that a TCP peer is trustworthy. |

Protocol, host address, port, and service instance name are DNS-SD/SRV/address-record data, not additional TXT fields. Service names can be renamed because of mDNS name conflicts, so a display name is not a stable identifier.

### Multiple Hyperion instances and servers

Hyperion's network servers and `MdnsProvider` are owned once by `HyperionDaemon`, outside the per-instance `HyperionIManager` objects. Multiple logical LED instances inside one daemon therefore do not create separate mDNS server records. Discovery identifies a daemon/network endpoint, not an individual logical Hyperion instance.

Multiple Hyperion daemons on the LAN advertise separate records. Their `id` TXT values are the strongest grouping key. All resolved services with the same non-empty `id` should be represented by one `HyperionServer`, even if service names or resolved addresses differ. Different non-empty IDs must never be merged merely because their display names match.

### IPv4 and IPv6

The Hyperion TCP servers bind using `QHostAddress::Any`, and the mDNS provider delegates hostname/address advertisement to QMdnsEngine. The service records themselves are not IPv4- or IPv6-specific; resolution supplies address records available for the host.

On the Android API range relevant here, a successfully resolved `NsdServiceInfo` reliably provides:

- service instance name and type;
- one `InetAddress` from `getHost()`;
- the resolved runtime port from `getPort()`;
- the `id` and `version` TXT attributes from `getAttributes()` on API 21 and later.

API 30 does not expose the complete address list: `getHostAddresses()` was added in API 34. A dual-stack server may therefore be reported to this app as either one IPv4 or one IPv6 address. The model must be family-neutral, preserve any IPv6 scope information, and not manufacture an IPv4 fallback. IPv4, global IPv6, and scoped link-local IPv6 require explicit tests.

## Android NSD assessment

### Recommendation

Use the platform `android.net.nsd.NsdManager` with `NsdManager.PROTOCOL_DNS_SD`. It has existed since API 16, while this project has `minSdk 21`. `NsdServiceInfo.getAttributes()` is available from API 21, so the minimum supported version can consume Hyperion's grouping ID and version. No external discovery dependency is justified.

The API is asynchronous and based on DNS-SD over mDNS. The legacy `discoverServices(String, int, DiscoveryListener)` and `resolveService(NsdServiceInfo, ResolveListener)` methods are the compatible path for API 21 through the target Fire OS 8 / API 30 device. Newer callback APIs can be considered only in a future target-SDK project; they are not required for Phase 3.

Relevant platform documentation:

- [Use network service discovery](https://developer.android.com/develop/connectivity/wifi/use-nsd)
- [`NsdManager` API reference](https://developer.android.com/reference/android/net/nsd/NsdManager)
- [`NsdServiceInfo` API reference](https://developer.android.com/reference/android/net/nsd/NsdServiceInfo)
- [`WifiManager.MulticastLock` API reference](https://developer.android.com/reference/android/net/wifi/WifiManager.MulticastLock)
- [Amazon Fire OS 8 development notes](https://developer.amazon.com/docs/fire-tv/fire-os-8.html)

### Permissions and multicast

The merged manifest currently declares `INTERNET` and `ACCESS_NETWORK_STATE`, which already cover the production TCP connection and network-state inspection.

Current Android guidance states that devices before T SDK extension 7, including Android 12 and below, require an app to hold a `WifiManager.MulticastLock` while performing mDNS on Wi-Fi, even in the foreground. Fire OS 8 / API 30 falls into that compatibility path. A later implementation should therefore add the normal, non-runtime `CHANGE_WIFI_MULTICAST_STATE` permission, acquire a non-reference-counted multicast lock only for an active foreground discovery session, and release it exactly once on every stop or failure path. This audit does not change the manifest.

No location permission is required for the API 21-30 NSD operation. Future local-network permission changes on newer Android targets must be reassessed when `targetSdk` is modernized; `targetSdk 26` is explicitly outside this phase.

The Wi-Fi multicast lock is irrelevant to Ethernet itself. Ethernet discovery still depends on the device NSD implementation, interface routing, and the network forwarding mDNS. The architecture must not assume Wi-Fi, inspect or embed a subnet, or fall back to broad TCP scanning.

### Android TV and Fire OS behavior

`NsdManager` is a framework API and does not depend on Google Play services, so it is the appropriate platform choice for Android TV and Fire TV. Fire OS 8 is based on Android API 30, but Amazon notes that not every Android feature is necessarily present on every Fire OS device. No Amazon guarantee specific to Hyperion mDNS or `NsdManager` was found. The implementation must therefore be verified on the target Fire TV hardware rather than documented as compatible in advance.

Expected environmental limitations are:

- access-point client isolation, VLAN boundaries, multicast filtering, or routers that do not bridge mDNS;
- Wi-Fi power saving and device standby delaying or suppressing multicast traffic;
- Ethernet and Wi-Fi being active at the same time, while API 30 resolution exposes only one address and no `Network` on `NsdServiceInfo`;
- services appearing, changing, or disappearing while resolution is queued;
- OEM NSD limits producing `FAILURE_ALREADY_ACTIVE`, `FAILURE_MAX_LIMIT`, or internal errors.

Discovery should run only while its visible screen is resumed and the user has explicitly started it. It should not acquire wake locks or try to continue through standby.

### Listener and resolution lifecycle

- Create a fresh `DiscoveryListener` per service type and per search generation.
- Track each listener as requested before `discoverServices`, then active after `onDiscoveryStarted`. If cancellation arrives while start is pending, mark a pending stop and stop the same listener as soon as start is confirmed; no result from that generation may reach the UI.
- Use the exact same listener instance for stopping.
- Stop all active discoveries when the user presses Cancel, starts a new search, navigates to manual setup, selects a result, or the owning view reaches `onStop`.
- Treat `onStartDiscoveryFailed`, `onStopDiscoveryFailed`, and exceptions from a stale/repeated stop as terminal for that listener, without crashing the UI.
- Serialize legacy `resolveService` work through a small queue. This avoids overlapping OEM resolver requests and makes cancellation and stale-result handling deterministic.
- API 30 cannot cancel an in-flight resolution. Cancellation must clear queued work, invalidate a monotonically increasing generation token, stop discovery listeners, release the multicast lock, and ignore any later callback from the old generation.
- Marshal all state changes to one application-controlled executor or main-thread `Handler`; publish immutable snapshots to the UI.
- Never retain an `Activity` in the NSD adapter. The owning screen subscribes while visible and detaches before the controller is closed.

## Original proposed architecture

The implementation should live primarily in the shared `common` module and isolate Android NSD callbacks from pure result logic.

```text
TV or mobile discovery screen
          |
          v
HyperionDiscoveryController  -- session state, timeout, cancel, generation
          |
          v
AndroidNsdDiscoveryAdapter    -- NsdManager listeners, resolve queue, multicast lock
          |
          v
DiscoveryResultMerger         -- pure Java grouping, deduplication, ordering
          |
          v
immutable DiscoverySnapshot   -- rendered by the current visible screen only
```

Suggested responsibilities:

- `AndroidNsdDiscoveryAdapter`: a thin wrapper around `NsdManager`; it converts callbacks to resolved service records and owns no UI.
- `HyperionDiscoveryController`: starts and stops a bounded manual session, owns all listener registrations, handles partial failures, and emits state snapshots.
- `DiscoveryResultMerger`: a platform-free deterministic reducer suitable for JVM unit tests.
- TV/mobile presentation adapters: render server rows and explicitly apply the selected Protocol Buffers endpoint to `Preferences`.

The first production slice should browse `_hyperiond-protobuf._tcp.` because it is the only endpoint the app can use. The model should already understand all five Hyperion service kinds. A follow-up slice may browse JSON, FlatBuffer, HTTP, and HTTPS to enrich the “available services” display. Generic `_http._tcp.` and `_https._tcp.` results must be resolved and accepted only when Hyperion TXT identity is present; otherwise unrelated web servers would pollute the list. Discovering a FlatBuffer advertisement does not implement or select FlatBuffer transport.

### Data model

```text
HyperionServiceKind
  PROTOBUF | JSON | FLATBUFFER | HTTP | HTTPS

ResolvedHyperionService
  discoveryKey       service type + service instance name
  instanceName       mDNS display name
  kind               mapped HyperionServiceKind
  address            InetAddress; family-neutral and scope-preserving
  port               resolved SRV port
  hyperionId         optional TXT "id"
  hyperionVersion    optional TXT "version"
  generation         owning search generation

HyperionServer
  serverKey          "id:" + non-empty Hyperion ID, otherwise conservative fallback
  displayName        stable choice from service names for this session
  hyperionId         optional
  hyperionVersion    optional
  addresses          ordered unique resolved addresses
  services           EnumMap<HyperionServiceKind, ServiceEndpoint>
  selectable         true only with a valid Protocol Buffers endpoint

ServiceEndpoint
  kind
  instanceName
  address
  port

DiscoverySnapshot
  generation
  state              IDLE | STARTING | SEARCHING | STOPPING | FINISHED | ERROR
  servers             immutable ordered list
  perTypeErrors       non-fatal diagnostics
```

Deduplication rules:

1. Reject records with an unknown type, null host, or a port outside `1..65535`.
2. Within one type, identify an announcement by normalized service type plus service instance name. Repeated callbacks update rather than append it.
3. Group all records with the same non-empty TXT `id`, regardless of address or service type.
4. If `id` is absent, use a conservative fallback containing normalized instance label and canonical resolved address. Do not merge different addresses or merge on display name alone; false separation is safer than selecting the wrong server.
5. Preserve all service kinds in the server record, but select and save only the Protocol Buffers endpoint.
6. Sort servers deterministically by case-insensitive display name, then address and Protocol Buffers port. Discovery order must not determine automatic selection.

When an API 30 resolution provides an IPv6 address, retain the `InetAddress` until presentation and convert it with `getHostAddress()` only at the preference boundary. Scoped link-local output must retain its scope. The selection code should validate the resulting host text and discovered port before writing both existing preferences together.

## UI flow

### Shared behavior

1. Show “Search for Hyperion servers” with **Start search** and **Manual setup** available immediately.
2. Starting clears only the transient result list, creates a new generation, acquires resources, and changes the primary action to **Cancel search** before calling NSD. This prevents duplicate starts.
3. Add or update rows as resolved results arrive. Never navigate away or select the first result automatically.
4. Each row shows server name, resolved host or IP address, discovered services and ports, and Hyperion version when present.
5. A row without Protocol Buffers may be shown for diagnostics but is disabled with “Protocol Buffers unavailable”.
6. Selecting an enabled row opens a confirmation step or explicit **Use this server** action. Only that action stores the selected Protocol Buffers host and discovered port.
7. Cancel stops the active session and leaves already resolved rows visible until the next search or screen exit.
8. A bounded search that finishes with no selectable result shows a clear message explaining that manual setup remains available and that multicast/network isolation may prevent discovery.
9. Leaving for manual setup or navigating away stops discovery before the transition.

The initial search window should be bounded so that “no results” is reachable; ten seconds is a reasonable starting value to validate on hardware, but the final duration remains a UX decision. Users may cancel earlier or explicitly retry with a fresh generation.

### TV

Replace the one-result celebration handoff with a D-pad-friendly result list in the onboarding screen. Keep **Manual setup** focusable throughout the scan. The same discovery entry should later be available from TV settings, not only when no host has ever been configured. Focus must remain stable when rows are updated or removed.

The color preview in `ScanResultActivity` is not necessary to prove discovery. If retained in the later selection flow, it must use a valid configured priority, issue a bounded request, clear it, close the connection, and report failure without losing the selection.

### Mobile

Add a “Find Hyperion servers” action adjacent to the existing manual host and port preferences. It opens the shared discovery presentation, returns an explicit selection, updates the same preference keys, and then refreshes the preference summaries. Manual editing remains possible before and after discovery.

## Lifecycle and error handling

The controller owns one search generation at a time. `start()` first closes any previous generation, increments the token, clears transient failures/results, marks the UI searching synchronously, then acquires the multicast lock and starts listeners. Every callback checks its captured token before mutating state.

`stop(reason)` is idempotent:

1. mark the generation stopping and reject new resolve work;
2. stop each started listener once and mark each still-pending listener to stop immediately if its start callback arrives;
3. clear the resolve queue and invalidate late callbacks;
4. cancel the UI timeout callback;
5. release the multicast lock exactly once;
6. detach the presentation observer when the owner leaves the screen;
7. publish the terminal state only to a still-attached owner.

An error for one optional service type is recorded without discarding results from another type. Failure of the Protocol Buffers browse should produce an actionable message and keep manual setup enabled. Malformed TXT data is ignored field by field; it must not invalidate an otherwise usable resolved Protocol Buffers endpoint. Discovery never opens the production streaming connection and never writes preferences by itself.

Service-lost events remove or update the matching transient announcement during an active scan. A selected preference is not silently cleared later; discovery is an onboarding/settings operation, not continuous server monitoring.

## Testing strategy

### Offline JVM tests

Place platform-free model and reducer tests in `common/src/test` using the existing JUnit setup. Cover at least:

- one resolved Protocol Buffers server and explicit selection;
- duplicate found/resolved callbacks updating one service;
- ProtoServer, JSON, web, and FlatBuffer records with one `id` merging into one server;
- two or more distinct Hyperion IDs remaining separate even with the same display name;
- missing `id` fallback behavior and deliberate refusal to over-merge;
- missing host, null host, unknown type, port `0`, negative ports, and ports above `65535`;
- IPv4, global IPv6, and scoped link-local IPv6 formatting;
- deterministic ordering independent of callback order;
- selection disabled without a Protocol Buffers endpoint;
- selection storing the discovered Protocol Buffers port rather than `19445`.

### Controller and adapter contract tests

Wrap the Android service behind an interface so fake listeners can exercise lifecycle behavior without a network:

- cancel before `onDiscoveryStarted`;
- cancel during discovery and during an in-flight resolution;
- repeated cancel and repeated start;
- stale callbacks from an old generation after retry;
- discovery-start, resolve, and stop failures;
- duplicate service-found and service-lost callbacks;
- several service types with partial failure;
- leaving the view while searching;
- timeout with no result and timeout with partial results;
- multicast lock acquisition and exactly-once release on success, failure, cancellation, and owner destruction.

Android instrumentation tests should verify preference adoption and presentation integration:

- selecting a result writes host and discovered port to the existing keys;
- backing out writes nothing;
- TV and mobile summaries reflect the saved values;
- manual host/port configuration works with discovery unavailable, cancelled, or empty;
- returning to the discovery view starts no background search automatically.

No regular automated test should require a real LAN or Hyperion server. A fake NSD adapter is the reproducible boundary. The later Fire TV test is manual and supplements, rather than replaces, offline tests.

### Manual Fire OS validation

On the target Fire OS 8 / API 30 device, verify separately:

- discovery on Wi-Fi and, if available, Ethernet;
- exact Protocol Buffers port returned for default and custom-port servers;
- multiple Hyperion daemons and duplicate service types;
- Start, Cancel, Retry, Back, Manual setup, and selection with the remote control;
- no callbacks, listeners, or multicast lock after leaving the screen;
- server removal/restart during a scan;
- wake/standby behavior without attempting background discovery;
- IPv4 and IPv6 where the test network supports them;
- continued manual configuration when mDNS is filtered or Hyperion mDNS is disabled.

Only observed results from that test should later be described as Fire TV compatibility.

## Implementation stages

1. **Pure domain layer:** add service-kind mapping, immutable records, merger, ordering, selection rules, and JVM tests in `common`.
2. **Platform adapter:** add the `NsdManager` adapter, legacy resolve queue, generation handling, bounded session, `MulticastLock`, and the normal multicast manifest permission. Add adapter/controller contract tests without a real network.
3. **Protocol Buffers discovery:** browse `_hyperiond-protobuf._tcp.`, show multiple results, select explicitly, and save the resolved host and port. Retain manual setup and keep the old scanner available until validation.
4. **TV integration:** replace the single-result path with the focus-safe result list and fix or remove the current invalid-priority preview behavior.
5. **Mobile integration:** add the optional discovery action to settings while preserving direct preference editing.
6. **Metadata enrichment:** if useful after the core path is stable, browse other Hyperion service types and merge them by TXT `id`. Never enable FlatBuffer selection.
7. **Validation and cleanup:** run all offline tests, perform the documented Fire OS checks, then remove the subnet scanner and `AsyncTask` only after the new path is confirmed.

Each stage should be independently reviewable. No stage may make discovery mandatory or change the selected server without a user action.

## Known Fire OS considerations

- Fire OS 8 reports Android API 30, but its NSD behavior must be tested on the actual target device.
- API 30 requires the legacy discovery/resolve listener path and exposes only one resolved address.
- A foreground Wi-Fi discovery session needs a manually managed multicast lock according to current Android guidance.
- Fire TV standby and network power saving can interrupt discovery; the app should surface a retry, not keep searching in the background.
- D-pad focus and screen lifecycle are correctness requirements: pressing Home, Back, or Manual setup must release discovery resources.
- Router multicast policy, client isolation, and VLAN topology can make a healthy server undiscoverable. Manual host and port entry is the supported fallback.

## Unresolved questions

1. Should the first released UI browse only the usable Protocol Buffers type, or also enrich rows with JSON and web services immediately? Protocol Buffers-only is the lower-risk first slice.
2. Is ten seconds the appropriate default search window on the target Fire TV across Wi-Fi and Ethernet?
3. Should non-Protocol-Buffers-only Hyperion servers appear as disabled diagnostic rows or be omitted from the user-facing list?
4. On API 30 dual-stack networks, which address family does the target Fire OS resolver return, and does a scoped IPv6 literal survive the existing preference-to-`Socket` path?
5. How does the target device behave with concurrent browse listeners for all five service types? If OEM limits are restrictive, service-type enrichment should run sequentially or remain deferred.
6. Should a service-lost row disappear immediately or remain visibly marked unavailable until the bounded search completes?
7. Where should the TV settings entry live after onboarding, and should the mobile discovery result use a dedicated screen or a dialog-style list?
8. Should the current color preview be removed, or retained after it is changed to a valid bounded request with guaranteed cleanup?
