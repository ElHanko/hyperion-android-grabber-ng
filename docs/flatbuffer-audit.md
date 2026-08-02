# Experimental FlatBuffer transport audit

## 1. Scope and product constraints

This document records the technical audit and implementation plan for Phase 3B.
It is not an implementation specification that makes FlatBuffer available in a
current build. The repository state reviewed for this audit is based on Hyperion
NG 2.2.1 and the existing Android Protocol Buffers transport.

The product constraints are fixed:

- Protocol Buffers remains the stable production default.
- FlatBuffer may be added only as an explicit experimental opt-in.
- New installations, updates, and installations without a stored transport value
  resolve to Protocol Buffers.
- There is no automatic migration, transport detection, silent fallback, or
  concurrent use of both transports.
- A FlatBuffer failure remains a FlatBuffer failure. Returning to Protocol
  Buffers requires an explicit user action.
- The application remains a screen grabber. It does not become a general
  Hyperion administration client.
- Phase 3A Protocol Buffers discovery remains independent. This audit does not
  add FlatBuffer discovery.

No dependency, schema, generated source, runtime client, abstraction, preference,
UI, discovery, test, manifest, or version change is part of this audit.

The architecture analysis in this document remains the original audit record.
The later, deliberately limited Stage 1 and Stage 2 implementations are recorded
under their implementation-status headings in section 6. Neither stage makes
FlatBuffer selectable in the application or authorizes a later integration
stage.

### Documentation consistency at the audit point

The existing [roadmap](roadmap.md), [discovery audit](discovery-audit.md),
[README](../README.md), and [changelog](../CHANGELOG.md) were checked without
modification. They consistently describe Phase 3 as in progress, Phase 3A as
completed, Phase 3B as planned, version 2.2.0 as unreleased, and FlatBuffer as not
implemented. Adding a link from those files is not necessary before any runtime
work exists, so this audit remains the only documentation change.

## 2. Current Protocol Buffers architecture

### Client and wire behavior

[`Hyperion.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/Hyperion.java)
is a final, `Closeable` Protocol Buffers TCP client. Both constructors connect
immediately. The two-argument constructor uses a 1,000 ms connect timeout and a
2,000 ms read timeout; the four-argument constructor accepts both values. The
client enables TCP no-delay and wraps the socket streams in buffered
`DataInputStream` and `DataOutputStream` instances. Its generated messages come
from [`HyperionProto.proto`](../common/src/main/proto/HyperionProto.proto).

The class currently combines several responsibilities:

- socket connection, framing, timeouts, and close state;
- Protocol Buffers request construction and validation;
- Protocol Buffers reply parsing and semantic validation;
- public color, RGB/RGBA image, clear, and clear-all operations.

Each Protocol Buffers request is framed as an unsigned-size-compatible four-byte
big-endian length followed by the serialized payload. The entire write and the
corresponding single reply read are guarded by `exchangeLock`, so callers cannot
interleave request/reply exchanges. `close()` is guarded separately and is
idempotent. A valid negative server reply throws `HyperionServerException` but
keeps the socket open. Timeout, EOF, malformed content, and framing failures
close it. The Android client imposes a 64 MiB request limit and a 1 MiB reply
limit; these are local safety limits, not values copied from the server.

The accepted priority range is `100..199`. Image validation requires positive
dimensions and exactly three or four bytes per pixel. The current encoder emits
packed RGB bytes: R, G, and B in that order. Average-color mode emits the same
format as a 1 x 1 image. The client also exposes `clearAll()`, but the capture
service uses only `clear(priority)`.

### Callers and lifecycle

The production path is:

```text
HyperionScreenService
  -> HyperionThread
    -> Hyperion
      -> Hyperion NG ProtoServer

HyperionScreenEncoder
  -> HyperionThread.HyperionThreadListener.sendFrame(RGB, width, height)
```

[`HyperionScreenService.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/HyperionScreenService.java)
reads the shared host, Protocol Buffers port, priority, reconnect flag, reconnect
delay, and capture settings. It constructs one `HyperionThread`, then creates the
screen encoder. [`HyperionScreenEncoder.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/HyperionScreenEncoder.java)
removes the alpha byte from Android `RGBA_8888` images and passes transport-neutral
RGB bytes, width, and height to the thread listener.

[`HyperionThread.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/HyperionThread.java)
is the main coupling point. It stores `Hyperion` directly, constructs a
Protocol Buffers `HyperionRequest` directly in `sendFrame()`, and calls the
Protocol Buffers-specific `sendRequest()`. It also constructs `Hyperion` for the
initial connection and reconnect. Its listener's `sendFrame`, `clear`,
`disconnect`, and status callbacks are otherwise close to transport-neutral.

The current reconnect behavior is owned partly by `HyperionThread`: after a
previous successful connection, a send failure can delay and construct another
`Hyperion`. An initial connection failure is reported and is not retried because
`HAS_CONNECTED` is still false. The service and thread each retain their own
reconnect boolean. Intentional stop sets the service field to false, stops the
encoder, eventually asks the listener to clear and disconnect, and interrupts
the thread. A future two-transport lifecycle must replace this implicit copied
state with an explicit stop request before closing the active transport; it must
not otherwise broaden the reconnect refactor.

The other production caller is the TV connection test in
[`BasicSettingsStepFragment.kt`](../tv/src/main/java/com/elhanko/hyperiongrabber/ng/tv/fragments/settings/BasicSettingsStepFragment.kt).
It constructs `Hyperion` directly, sends a bounded color, and disconnects. Mobile
settings do not open a test connection.

### Preferences, settings, discovery, and errors

[`Preferences.kt`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/util/Preferences.kt)
wraps default `SharedPreferences`. Integers, including ports and priority, are
stored as strings. Host and port can be adopted atomically through
`putHostAndPort()`. The existing keys are shared by TV and Mobile:

- `pref_key_host`;
- `pref_key_port`, manual default `19445`;
- `pref_key_priority`, resource default `150`;
- reconnect enabled by default with a five-second delay.

The service currently uses a literal priority fallback of `50` if no string is
available, while the declared resource default is `150` and the transport accepts
only `100..199`. The later transport configuration boundary must validate the
effective priority without silently changing saved values in this audit.

Mobile renders the shared preference XML through
[`SettingsActivity.java`](../mobile/src/main/java/com/elhanko/hyperiongrabber/ng/mobile/SettingsActivity.java).
TV builds equivalent guided actions in `BasicSettingsStepFragment`. Both expose
the same host and Protocol Buffers port today.

Phase 3A is explicitly Protocol Buffers-only:

- [`AndroidNsdDiscoveryBackend.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/discovery/AndroidNsdDiscoveryBackend.java)
  browses `_hyperiond-protobuf._tcp.`;
- [`DiscoveredHyperionServer.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/discovery/DiscoveredHyperionServer.java)
  models a `protoServerPort`;
- [`DiscoverySelection.java`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/discovery/DiscoverySelection.java)
  saves only an explicitly selected Protocol Buffers host and SRV port.

The current error classes distinguish invalid Protocol Buffers framing/content,
a valid server rejection, and read timeout:
`HyperionProtocolException`, `HyperionServerException`, and
`HyperionTimeoutException`. Their names and comments are not fully
transport-neutral, so a later shared layer should categorize failures without
forcing a rewrite of the tested Protocol Buffers exceptions.

### Existing validation and required transport surface

[`HyperionTest.java`](../common/src/test/java/com/elhanko/hyperiongrabber/ng/common/network/HyperionTest.java)
contains 23 offline JVM tests backed by loopback `ServerSocket` instances. They
cover request types, RGB/RGBA validation, exact big-endian framing, fragmented
replies, connection reuse, serialized concurrent calls, server errors, timeout,
EOF, invalid lengths, malformed replies, reconnect via a new client, and
idempotent close. [`HyperionIntegrationTest.java`](../common/src/test/java/com/elhanko/hyperiongrabber/ng/common/network/HyperionIntegrationTest.java)
is an opt-in real ProtoServer check using environment variables and guaranteed
best-effort cleanup of only its own priority.

The application needs only this common runtime surface:

```text
connect
isConnected (meaning connected and ready for application requests)
sendColor(color, duration)
sendImage(RGB or supported four-byte RGB, width, height, duration)
clear(own priority)
close
transportName
```

Host, selected port, priority, connect/read timeouts, and an origin/name belong to
immutable construction configuration, not to every method call. `clearAll` and a
generic raw-message API are deliberately absent. This is the smallest useful
surface for the service and the TV connection test.

## 3. Official Hyperion NG 2.2.1 FlatBuffer protocol

This section is derived only from the official `hyperion-project/hyperion.ng`
tag `2.2.1`, commit `1a003600361118483ecfc6445a549d3703f9e063`.
No separate normative FlatBuffer wire-protocol document was found in that tag;
the schemas and the client/server implementations below are therefore the
authoritative sources for this audit.

### Schema and types

The two official schemas use the namespace `hyperionnet`:

- [`hyperion_request.fbs`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/hyperion_request.fbs)
- [`hyperion_reply.fbs`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/hyperion_reply.fbs)

The request schema defines:

| Type | Fields and exact schema defaults |
| --- | --- |
| `Register` | required `origin:string`; `priority:int` (implicit default `0`) |
| `RawImage` | `data:[ubyte]`; `width:int = -1`; `height:int = -1` |
| `NV12Image` | `data_y:[ubyte]`; `data_uv:[ubyte]`; `width:int`; `height:int`; `stride_y:int = 0`; `stride_uv:int = 0` |
| `ImageType` union | `RawImage`, `NV12Image` |
| `Image` | required `data:ImageType`; `duration:int = -1` |
| `Clear` | `priority:int` (implicit default `0`) |
| `Color` | `data:int = -1`; `duration:int = -1` |
| `Command` union | `Color`, `Image`, `Clear`, `Register` |
| `Request` | required `command:Command`; root type |
| `Reply` | `error:string`; `video:int = -1`; `registered:int = -1`; root type |

The schema has no file identifier, protocol version, request identifier,
authentication field, server/instance selector, or separate client-name field.
The selected TCP host and port identify the server. `origin` is the client
description used during registration; the server records it with the peer
address.

### Registration and readiness

The official
[`FlatBufferConnection.cpp`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferConnection.cpp)
connects to TCP and immediately sends `Request(Command_Register,
Register(origin, priority))`. It does not send color or image data until a reply
has `error == null` and `registered` equals the requested priority. The server
accepts registration priorities only in `100..199` and returns an error string
outside that range.

The server can also send an unsolicited reply with `registered = -1` from its
`registationRequired` path. This is intended to request registration again when
Hyperion invalidates a global input registration. The official C++ reply parser
only evaluates `registered` while its local `_isRegistered` flag is false, so its
handling of that unsolicited signal after readiness is not conclusive. A Java
implementation must explicitly test and define this case rather than copy that
ambiguity.

The server handlers do not contain a separate authentication or version
handshake, and the schema cannot express one. The server code also does not
explicitly reject a color/image handler merely because `_priority` is not yet in
range; nevertheless, following the official client lifecycle means registration
and its matching reply are mandatory before the Android client reports itself
connected.

### Commands

- **Color:** `data` is packed as `0xRRGGBB`; the server extracts red, green, and
  blue with Qt's RGB helpers. `duration` is milliseconds. The schema default is
  `-1`; the official C++ convenience method defaults color to `1` ms.
- **Raw image:** the server requires positive width and height and non-empty data.
  It derives bytes per pixel as `dataSize / (width * height)` and accepts 3 or 4.
  Three-byte input is `RGB24`. Four-byte input is `RGB32`: the resampler reads R,
  G, and B from the first three bytes and ignores the fourth byte. The future
  Android client should enforce an exact `width * height * 3` or
  `width * height * 4` length, because the upstream quotient check alone does not
  reject every remainder case.
- **NV12 image:** separate Y and UV byte vectors, dimensions, and strides are in
  the schema and handled by the server. The Android encoder already produces RGB,
  so adding NV12 conversion would be unnecessary Phase 3B scope.
- **Clear:** the server emits a clear for the supplied priority. `-1` is how the
  official client implements clear-all. Phase 3B must expose and send only the
  app's own configured priority. Clearing the connection's own priority changes
  the server-side connection priority to `-1`; if the socket is retained for a
  later resume, the client must register again before its next color or image.
- **Duration:** color and image duration values are passed to Hyperion in
  milliseconds. `-1` is the schema default and is used for continuing image
  input. Short integration requests must use a bounded positive duration.

The official standalone grabbers use the common FlatBuffer client, an origin such
as `<capture type> Standalone`, the configured priority, and default port `19400`.
They start capture only after `isReadyToSend`, stop capture on disconnect, and
stop on a reported server error. The
[`hyperion-drm` grabber](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/src/hyperion-drm/hyperion-drm.cpp)
is a representative implementation; the X11, XCB, V4L2, Amlogic, Dispmanx,
framebuffer, macOS, and Qt grabbers follow the same connection pattern.

## 4. Framing, replies, and message lifecycle

### Exact wire framing

Both request and reply directions use this framing:

```text
offset  size  meaning
0       4     payload length as an unsigned 32-bit big-endian integer
4       N     exactly N bytes of a normal, non-size-prefixed FlatBuffer root
```

For a payload length `N`, the header bytes are `(N >> 24) & 0xff`,
`(N >> 16) & 0xff`, `(N >> 8) & 0xff`, and `N & 0xff`. This is explicit in both
the official client `sendMessage()` and server `sendMessage()`. It is transport
framing around the FlatBuffer; it is not FlatBuffers' size-prefixed root format.
TCP fragmentation and coalescing are expected. Both official receivers accumulate
bytes, parse only after four header bytes are available, wait for the complete
body, and loop over additional complete frames.

### Request/reply sequence

The safe Android lifecycle is:

```text
TCP connect
  -> Register(origin, priority)
  <- Reply(error absent, registered == priority)
ready
  -> one Color, Image, or Clear request
  <- one Reply
close, or re-register before later data if own priority was cleared
```

The server sends a reply for every recognized command and for malformed or
unsupported requests. The official C++ client has a `skipReply` mode, but that is
not suitable here: it would discard registration state and explicit errors. The
Android implementation should accept at most one outstanding application
request, preserve a buffered frame parser, and serialize all writes and reply
state transitions. There are no request IDs with which to match concurrent
responses.

A `Reply` has no explicit success boolean. A non-null `error` is a server error.
With no error:

- `registered == requested priority` acknowledges registration and is also
  included in ordinary success replies while that priority remains registered;
- `registered == -1` can request re-registration or reflect a cleared priority;
- `video != -1` represents a video-mode reply in the official client, although
  the inspected FlatBuffer server command handlers do not produce such a value;
- normal command success otherwise consists of a valid no-error reply.

An unsolicited `registered = -1` must be treated as a state event, not silently
matched to an application request. A dedicated single connection worker, or an
equivalent strictly serialized reader/state machine, is the lowest-risk design.
It can maintain one FIFO application exchange while consuming registration
events. This behavior requires fake-server tests before runtime integration.

### Limits, connection end, and reconnect assumptions

The wire header can encode an unsigned 32-bit value. However, the 2.2.1 server
does not impose an explicit maximum before waiting for the declared body, and the
schema states no practical maximum. Therefore the supported maximum is
**unknown**, not 4 GiB. Stage 2 must establish conservative client-local request
and reply limits based on Android memory use and measured capture dimensions,
with overflow-safe arithmetic, before allocating. The limits must be documented
and tested; they must not be guessed from the existing Protocol Buffers limits.

Invalid request FlatBuffers receive an error reply and the server continues its
parse loop. A server inactivity timer closes a connection after the configured
period; the 2.2.1 configuration default is five seconds. On TCP disconnect the
server clears the registered priority when it is in `100..199`.

The official C++ client owns an internal five-second reconnect timer. The Android
client must not copy that ownership: the existing application lifecycle must own
reconnect so that intentional stop, transport selection, and status are consistent
for both transports. EOF, truncated frames, timeouts, invalid framing, or invalid
replies make the Java connection unusable and require close plus a service-owned
reconnect. A valid server error does not desynchronize the stream; registration
errors still prevent readiness and should fail the connection attempt.

## 5. Differences from the current Protocol Buffers transport

| Concern | Current Protocol Buffers path | Hyperion 2.2.1 FlatBuffer path |
| --- | --- | --- |
| TCP frame | 4-byte big-endian length + payload | Same outer framing |
| Serialization | Protobuf extensions and command enum | FlatBuffer tables and unions |
| Initialization | No separate registration | `Register(origin, priority)` and matching reply before ready |
| Priority | Repeated in color/image/clear requests | Registered once; color/image use connection priority; clear carries a priority |
| Success reply | Explicit type and required `success` boolean | Absence of `error`; `registered`/`video` carry state |
| Request identity | None | None |
| Protocol version | None in current app schema | None in FlatBuffer schema |
| Origin | Not present in current requests | Required during registration |
| Image input | Exact RGB or RGBA validated locally | Raw RGB24 or RGB32, plus upstream NV12 support |
| Clear all | Separate `CLEARALL` command | `Clear(priority = -1)` |
| Server error | Valid negative reply; socket reusable | Valid `error` string; server parse loop remains active |
| Client limits | 64 MiB request, 1 MiB reply | No explicit upstream maximum; local limits must be selected and measured |
| Reconnect | Android thread/service behavior | Official C++ client retries internally, but Android must retain service ownership |

The shared layer must normalize only the operations the app needs. It must not
pretend that the reply models or registration behavior are identical.

## 6. Schema and code-generation assessment

### Audited upstream toolchain

Hyperion 2.2.1 checks in the two `.fbs` schemas and generates C++ headers during
its build. Its
[`libsrc/flatbufserver/CMakeLists.txt`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/CMakeLists.txt)
calls `compile_flatbuffer_schema`; the implementation in
[`dependencies/CMakeLists.txt`](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/dependencies/CMakeLists.txt)
invokes `flatc -c --no-includes --gen-mutable --gen-object-api` into the build
directory. Hyperion 2.2.1 pins the FlatBuffers submodule at commit
[`187240970746d00bbd26b0f5873ed54d2477f9f3`](https://github.com/google/flatbuffers/commit/187240970746d00bbd26b0f5873ed54d2477f9f3),
whose version is 25.9.23. The Hyperion changelog also records the update to
FlatBuffers 25.9.23 and a 2.2.1 fix for finding locally installed `flatc` and
`protoc`.

At that exact FlatBuffers commit, the official Java POM identifies the runtime as
`com.google.flatbuffers:flatbuffers-java:25.9.23`, Apache License 2.0, compiled
for Java 8. That bytecode level is compatible in principle with this project's
JDK 17 compilation and Android toolchain, but Stage 1 must still prove D8/Android
API 21 compatibility in both app variants.

### Audited reproducible approach

Stage 1 should use all of the following as one reproducibility unit:

1. Vendor unmodified copies of the two schema files from Hyperion tag 2.2.1,
   with a small provenance record containing the tag, Hyperion commit, source
   URLs, and checksums. The audited SHA-256 values are:
   - request: `63213a82d8c813556573ab13ead1549d5686627ce5aa5568617fddb37ffde721`;
   - reply: `37c2328e6429682c57b2e5bcdd75ba403911095eac7d52a976c483862bf02996`.
2. Pin `flatc` to 25.9.23 in the Docker builder and verify the downloaded tool or
   source archive with a committed checksum. Do not use whichever `flatc` happens
   to be installed on a developer workstation.
3. Use a small custom Gradle generation task in `common`, because `common` is the
   shared source of both APKs. Declare the schema files, exact compiler, compiler
   version marker, and flags as inputs; generate Java under `common/build/`, and
   register that directory as generated source. Confirm the exact Java flags and
   resulting `hyperionnet` package in a proof-only Stage 1 change.
4. Add only the official Java runtime matching 25.9.23; do not add the separate
   gRPC or Kotlin artifacts. Do not introduce reflection schemas or use the
   FlexBuffers API. Measure the complete published Java runtime before considering
   any narrower, separately reviewed vendoring approach.
5. Generate twice from clean checkouts in the Docker image and byte-compare the
   output. Fail the reproducibility check on non-deterministic output.

An independent Gradle plugin is not recommended. A dedicated task is easier to
pin and audit and avoids adding another release lifecycle. Checking in generated
Java would make local builds independent of `flatc`, but duplicates generated
code, increases stale-code risk, and makes tool-version drift harder to detect.
Build-time generation from checked-in schemas is preferred. Generated sources
must never be edited manually.

The current Docker builder is Ubuntu-based JDK 17 with Android platform/build
tools 36, Gradle 9.5.0, AGP 9.3.0, `minSdk 21`, `compileSdk 36`, and temporary
`targetSdk 26`. Stage 1 must run `:common:test`, assemble both Mobile and TV debug
APKs, and exercise the release build before runtime integration.

APK cost was unknown at the audit point. Measure both APKs before and after the
runtime and generated sources with `apkanalyzer`, record compressed/uncompressed
deltas, and inspect the dependency graph. Do not predict a size saving from the
wire format.

Hyperion's schemas are part of an MIT-licensed repository whose 2.2.1 license
attributes the Hyperion Project (2014-2026). The Google FlatBuffers compiler and
Java runtime are Apache-2.0 licensed. Vendored schemas must retain provenance and
the applicable Hyperion MIT notice; distribution documentation must retain the
runtime's Apache-2.0 notice. The schema files have no individual header, so the
repository-level provenance must not be lost.

### Stage 1 implementation status

**Status:** Completed on August 2, 2026

Stage 1 implements schema and build integration only. It does not contain a
FlatBuffer socket client, outer TCP framing, registration lifecycle, service or
transport integration, preferences, settings UI, discovery, reconnect behavior,
or real-server/hardware validation. Protocol Buffers remains the only usable
production transport.

The implemented reproducibility boundary is:

- byte-for-byte copies of the two Hyperion NG 2.2.1 schemas under
  `common/src/main/flatbuffers/hyperion-ng-2.2.1/`, with tag, commit, immutable
  URLs, SHA-256 values, license notices, and update instructions in the adjacent
  provenance file;
- the official Linux x86-64 `flatc` 25.9.23 release artifact, verified before
  extraction with SHA-256
  `de0c6ad114a5a686ecf64322528c602c7d4512446a93f290f54f00ee5abea487`;
- the official FlatBuffers commit
  `187240970746d00bbd26b0f5873ed54d2477f9f3`. Its Java POM declares
  `com.google.flatbuffers:flatbuffers-java:25.9.23`, but this release is absent
  from Maven Central. Docker therefore verifies the tagged source archive,
  compiles the Java 8 runtime, creates a timestamp-normalized JAR, verifies its
  deterministic SHA-256
  `89e9f694825de4848e0c676dd6478b6c005e8cef2514bc77c54ecd24446d9c17`,
  and supplies that exact coordinate through an image-local Maven repository;
- a typed Gradle task named `generateFlatBuffersJava`, with declared schema and
  compiler inputs, version and option inputs, and a declared output directory at
  `common/build/generated/source/flatbuffers/main`;
- AGP's generated-source Variant API, so compilation depends on generation while
  generated Java remains ignored and uncommitted;
- `--java` as the complete generation option set. Mutable and object APIs are not
  generated.

Generation produces exactly these ten top-level classes in package
`hyperionnet`: `Clear`, `Color`, `Command`, `Image`, `ImageType`, `NV12Image`,
`RawImage`, `Register`, `Reply`, and `Request`. Two clean generations produced
identical per-file hashes and the manifest tree hash
`cfaa9736bf58371f6258a26614553010c74decc0fe9693cc7dfb95dd2470f4d3`.
A second unchanged Gradle invocation reported the task `UP-TO-DATE`.

The Java generator emits version guards and root accessors, but it does not emit
a structural verifier API. The generated size-prefixed root helpers are also not
the Hyperion TCP frame described in section 4 and are unused in Stage 1. The
socket implementation in Stage 2 must establish explicit bounds and parsing
validation before it accepts network input; Stage 1 round-trip tests are not a
claim of hostile-input verification.

Seven offline JVM tests exercise version guards and schema-consistent round trips
for registration, color, three-byte-per-pixel raw image data, four-byte-per-pixel
raw image data, clear, success replies, and error replies. They use no sockets
and test no framing. The complete regular test matrix passed: Common contained
51 tests with the one existing opt-in ProtoServer test skipped, Mobile contained
one passing test, and TV had no JVM test sources.

Both Debug and signed Release builds completed for Mobile and TV. Release APK
measurements against the pre-Stage-1 build from the same branch were:

| APK | Before | After | APK delta | Percentage | Uncompressed delta |
| --- | ---: | ---: | ---: | ---: | ---: |
| Mobile | 4,279,696 bytes | 4,316,759 bytes | +37,063 bytes | +0.8660% | +99,117 bytes |
| TV | 5,155,366 bytes | 5,189,829 bytes | +34,463 bytes | +0.6685% | +95,360 bytes |

Both APKs retain application ID `com.elhanko.hyperiongrabber.ng`, version name
`2.1.1`, `minSdk 21`, temporary `targetSdk 26`, Mobile versionCode `1101`, TV
versionCode `2101`, and the same signing-certificate SHA-256 digest as the
baseline. DEX analysis confirms all ten generated top-level classes in each APK.
It also confirms 68 classes from the complete official Java runtime; its bundled
reflection and FlexBuffers support is present as library code but is not called,
and no reflection schema or FlexBuffers API was added to project code. Debug and
Release dependency insight each resolve exactly one FlatBuffers runtime version:
`25.9.23`.

### Stage 2 implementation status

**Status:** Completed on August 2, 2026

Stage 2 adds the isolated
[`FlatBufferHyperionClient`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/flatbuffer/FlatBufferHyperionClient.java)
in package
`com.elhanko.hyperiongrabber.ng.common.network.flatbuffer`. It is a final,
`Closeable`, Android-independent client with no background reader and no
reconnect policy. It owns exactly one TCP socket, one buffered input stream and
one buffered output stream, enables `TCP_NODELAY`, and accepts immutable host,
port, priority, origin, connect-timeout, and read-timeout values. Host and origin
must be non-empty, port must be in `1..65535`, priority in `100..199`, and both
timeouts must be positive.

Construction connects and immediately performs the official registration
handshake. The client sends `Register(origin, priority)` and becomes ready only
after a no-error reply whose `registered` value equals the requested priority.
`isConnected()` therefore means both socket-open and registered. A server error,
EOF, timeout, malformed reply, or failure to obtain the matching acknowledgement
closes the construction attempt. A mismatched acknowledgement causes one fresh
Register request; at most two consecutive registration attempts are made.

Both directions use exactly a four-byte unsigned big-endian payload length
followed by a normal, non-size-prefixed FlatBuffer root. Reads consume exactly
the complete header and body, so fragmented frames and multiple buffered frames
remain separate. The client never uses `InputStream.available()` or generated
size-prefixed accessors. Its local Android protection limits are 64 MiB for an
outgoing request payload and 1 MiB for a reply payload, excluding the four-byte
header. These are client-local allocation limits, not claimed Hyperion protocol
limits; zero, unsigned-oversized, and otherwise out-of-range lengths are rejected
before body allocation.

The deliberately small public request API is `setColor`, `setImage`, `clear`,
`isConnected`, and `close`:

- color data is transferred unchanged as the schema's packed `0xRRGGBB` integer,
  and duration is transferred unchanged in milliseconds;
- raw images are sent through `Image` / `RawImage` without conversion. RGB24
  requires exactly `width * height * 3` bytes and RGB32/RGBA exactly
  `width * height * 4`; dimensions, multiplication, byte count, and final request
  size are validated before network output. The fourth RGB32 byte is preserved,
  while Hyperion NG 2.2.1 consumes the first three bytes as R, G, and B;
- clear always sends only the configured own priority. There is no clear-all or
  generic raw-request API.

Reply handling evaluates the schema's `error`, `registered`, and `video` fields.
Because FlatBuffers Java 25.9.23 has no complete verifier API, the implementation
performs narrow root, table, scalar, and string bounds checks before calling the
normal generated root accessor. Runtime parsing failures and assertions caused
by invalid server data are converted to `HyperionProtocolException`; no complete
custom FlatBuffer verifier was introduced. A present server error field is
surfaced as the existing `HyperionServerException` (with a generic rejection
message if its string is empty). Read timeouts use the existing
`HyperionTimeoutException`; framing, parsing, and invalid-state failures use the
existing `HyperionProtocolException`.

The implemented registration state follows the observed 2.2.1 server behavior:

- a successful own-priority Clear reply has `registered = -1`; Clear still
  succeeds, but the socket becomes not ready and the next Color, Image, or Clear
  call performs a fresh Register handshake before sending its request;
- an unsolicited `registered = -1` before a normal command reply is consumed as
  a state event rather than mistaken for that reply. The already-sent command is
  considered complete only after its following valid reply, and the next public
  operation re-registers lazily;
- `video != -1` with `registered = -1` is consumed as a bounded state reply, with
  no additional video-mode behavior invented for the Android client;
- state replies and consecutive Register attempts are bounded, so repeated
  `registered = -1` values cannot create an infinite loop.

The upstream server continues its parse loop after sending a valid error reply,
so a server error for a normal operation leaves the synchronized connection
open and reusable. A registration error is different: it prevents readiness and
closes the client. Timeouts, EOF, socket errors, invalid frame lengths, malformed
FlatBuffers, and invalid reply state are fatal and close the connection.
Locally rejected parameters and image data do not touch the socket. `close()` is
idempotent and can close the socket while another thread is blocked in a read;
one exchange lock serializes every complete write/reply sequence.

The Stage 2 fake-server suite contains 58 deterministic offline JVM tests across
three client test classes. Loopback `ServerSocket` instances use operating-system
assigned ports and propagate server-thread failures. The suite covers connection
and registration, exact framing and fragmentation, Color, RGB24/RGB32 RawImage,
Clear and re-registration, all local bounds, malformed input, timeout/EOF/socket
failure, valid server-error reuse, concurrent serialization, reply separation,
and idempotent/concurrent close. The complete regular matrix passed with 109
Common tests (one existing opt-in Protocol Buffers integration test skipped), one
Mobile test, and no TV JVM test sources. Mobile and TV Debug and signed Release
APKs also built successfully with unchanged IDs, versions, and signing identity.

This client has no production caller. No transport boundary, adapter,
`HyperionThread` or service integration, preference, FlatBuffer port setting,
UI, discovery, production reconnect, real FlatBuffer server test, or hardware
validation is part of Stage 2. Protocol Buffers remains the sole production
transport and stable default.

## 7. Proposed minimal transport abstraction

### Recommendation: compose the existing client through an adapter

The lowest-risk choice is option 1: leave the existing `Hyperion` class as the
tested Protocol Buffers implementation and compose it inside a thin adapter.
Reasons:

- no rename or package move obscures the Phase 2 history;
- the 23 socket tests and the opt-in integration test continue to exercise the
  exact existing class;
- Protocol Buffers request construction can remain inside `Hyperion`;
- only `HyperionThread` and the TV test need to stop constructing `Hyperion`
  directly;
- a later rename remains possible after both transports have stabilized, but is
  not required to ship the experiment.

Renaming or moving `Hyperion` to a Protocol Buffers-specific class would make the
code visually clearer, but it creates a wide, low-value diff and test-history
noise before the new boundary has proved itself. It is not recommended for the
initial implementation.

### Contract and factory boundary

Do not commit final class names in the audit. The conceptual contract is:

```text
connect()
isConnected()
sendColor(rgb, durationMs)
sendImage(bytes, width, height, durationMs)
clear()
close()
transportName()
```

The selected host, selected transport-specific port, priority, origin, connect
timeout, and read timeout form an immutable configuration object supplied to a
factory. The factory creates exactly one selected implementation. Priority does
not appear on each call because it is configuration for both transports, even
though the Protocol Buffers adapter delegates it into every existing request.

`connect()` completes only when the transport is ready: socket-connected for the
adapter and socket-connected plus registered for FlatBuffer. Alternatively, the
factory may return an already-connected object to preserve `Hyperion` constructor
semantics; callers must still observe the same ready-or-throw boundary.

The interface extends `Closeable` or equivalent. `close()` is idempotent and does
not implicitly clear another priority. The lifecycle explicitly attempts
`clear()` for its own priority before close when appropriate. Public operations
are thread-safe and serialize protocol exchanges. The FlatBuffer implementation
owns framing and registration state, not reconnect policy.

The encoder listener can remain `sendFrame(byte[], width, height)`. Its current
RGB output is already suitable for both transports. The direct Protocol Buffers
request construction in `HyperionThread` must be replaced by
`activeTransport.sendImage(...)`; that is the essential decoupling. A small
factory seam also lets the TV color test use the selected implementation and
makes service lifecycle tests possible without sockets.

## 8. Preference and GUI model

### Persisted model

Use a stored transport value, not a FlatBuffer-only boolean in the domain model:

```text
protobuf
flatbuffer
```

The UI checkbox maps to this value:

```text
unchecked -> protobuf
checked   -> flatbuffer
```

The migration rules are:

- missing transport key resolves to `protobuf` without rewriting unrelated
  preferences;
- a new installation defaults to `protobuf`;
- an update with existing host/port settings remains on `protobuf`;
- no code infers FlatBuffer from a port, server response, discovery result, or
  reachability;
- an unrecognized stored value should be treated as invalid configuration and
  surfaced before capture, or conservatively resolved to Protocol Buffers only
  if a forward-compatibility policy is explicitly approved. This choice remains
  open; it is not runtime fallback.

Keep the existing common host and Protocol Buffers port keys. Add a separate
FlatBuffer port key with a manual default of `19400`. Reading the default must not
overwrite a previously stored value. Checking or unchecking the transport must
not modify host, Protocol Buffers port, or FlatBuffer port.

Phase 3A discovery continues to browse only `_hyperiond-protobuf._tcp.` and to
write only the common host and existing Protocol Buffers port after explicit
selection. It never changes the transport value or FlatBuffer port. Extending
discovery later would require a separate, explicitly scoped change for
`_hyperiond-flatbuf._tcp.`; it is not a prerequisite for manual FlatBuffer use.

### Planned presentation

Add one small section to both settings experiences only after runtime integration:

```text
Experimental transport

[ ] Use experimental FlatBuffer transport

Experimental. Protocol Buffers remains the stable default.
Disable this option if FlatBuffer does not work reliably.
```

When checked, show an editable FlatBuffer port and a persistent experimental
warning. Status and errors include `FlatBuffer`. When unchecked, retain the
current Protocol Buffers port, connection test, capture, and reconnect behavior;
no FlatBuffer object is constructed. The settings test action must use only the
currently selected transport and own priority.

Changing transport while capture is active must request a controlled capture
service restart. It must not hot-swap a socket under the encoder, start both
transports, ask an automatic question, or silently reset the checkbox. TV actions
must remain D-pad reachable and Mobile must retain its normal AndroidX preference
flow.

## 9. Error, fallback, reconnect, and stop behavior

The required state flow is:

```text
FlatBuffer selected
  -> attempt FlatBuffer connection and registration
  -> success: use FlatBuffer only
  -> failure: report an explicit FlatBuffer error
```

It is never `FlatBuffer failed -> use Protocol Buffers`.

Use a transport-neutral failure category at the service boundary while retaining
the existing detailed Protocol Buffers exception types. Every displayed/logged
failure is prefixed or structured with the selected transport name.

| Category | FlatBuffer handling | Connection reusable? |
| --- | --- | --- |
| Connect timeout | Abort and close the candidate socket | No |
| Read timeout | Fail the pending exchange and close | No |
| EOF before header/body completion | Report EOF/truncated frame and close | No |
| Invalid frame size or overflow | Reject before allocation and close | No |
| Invalid FlatBuffer payload/reply | Reject verified parsing failure and close | No |
| Invalid reply state | Reject impossible/mismatched registration state and close | No |
| Valid server error reply | Surface exact server message with transport name | Usually yes; registration error is not ready and the attempt closes |
| Unsupported protocol version | No 2.2.1 version field exists; this cannot currently be detected | Not applicable until a future protocol defines negotiation |
| Wrong image dimensions/data length | Reject locally before writing | Yes |
| Socket error | Close and let lifecycle policy decide whether to reconnect | No |

The official server can send an error reply for an invalid request and continue,
but a correctly implemented client should never emit such a request. Invalid
incoming framing or reply content loses trust in stream alignment and always
closes the Java connection.

The service snapshots the selected transport and its port when a capture session
starts. Reconnect always asks the factory for that same selection; it never
rereads a changed checkbox mid-attempt and never touches the other transport.
Exactly one active transport reference exists.

Intentional stop must set a thread-safe `stopRequested` state before clear,
close, sleep interruption, or callbacks can schedule another connection. It then
best-effort clears only the app's own priority, closes idempotently, cancels
pending reconnect delay/work, and suppresses subsequent reconnect callbacks.
Unexpected failure while reconnect is enabled schedules the existing configured
delay and recreates only the selected transport. Changing the saved transport
while capture is active follows the controlled-stop path and requires a new
session.

## 10. Testing strategy

### Transport-neutral tests

Use a fake factory and fake transports to prove:

- Protocol Buffers is the default for a new or missing preference;
- existing preferences remain compatible and are not rewritten;
- exactly one transport is created;
- changing transport closes the previous transport through a controlled restart;
- reconnect recreates the selected transport only;
- intentional stop prevents reconnect;
- no error causes an automatic fallback;
- status and failures identify the selected transport;
- the Protocol Buffers adapter preserves the existing client behavior and tests.

### FlatBuffer fake-server JVM tests

**Stage 2 status:** Completed with 58 passing tests.

The implemented loopback `ServerSocket` tests are offline and deterministic and
cover:

- TCP connection and the first `Register(origin, priority)` frame;
- readiness only after `registered == priority`;
- registration rejection and an unsolicited `registered = -1` event;
- color packing and duration;
- exact RGB24 image bytes, dimensions, and duration;
- exact four-byte RGB32 input, with the fourth byte ignored by server semantics;
- exact four-byte big-endian frame encoding;
- fragmented headers and bodies;
- multiple replies in one TCP read and replies split across reads;
- ordered, serialized request/reply exchanges with no request IDs;
- no-error success, error reply, and unexpected reply fields;
- zero, oversized, overflowing, and truncated frame lengths;
- invalid FlatBuffer body and wrong union/table type;
- EOF and read timeout;
- socket failure followed by construction of a new selected client;
- own-priority clear, no public clear-all, and re-registration before reuse after
  own-priority clear;
- priority `100..199` validation;
- positive dimensions and exact 3/4-byte payload-length validation with overflow
  cases;
- selected local request/reply maximum boundaries;
- thread-safe serialization and idempotent close.

The official tag's [`test` directory](https://github.com/hyperion-project/hyperion.ng/tree/2.2.1/test)
contains no dedicated FlatBuffer protocol/socket tests found by this audit.
Therefore the Android fake server must derive its frames from the checked-in
official schema and include captured interoperability fixtures only after those
fixtures are reproducibly documented.

### Optional real integration test

Add an opt-in test only after the fake-server suite is complete. Suggested
environment variables are:

```text
HYPERION_FLATBUFFER_INTEGRATION_TESTS=1
HYPERION_TEST_HOST=<explicit host>
HYPERION_TEST_FLATBUFFER_PORT=<explicit port>
HYPERION_TEST_PRIORITY=199
```

Without the exact opt-in value, the test performs no network access. It connects,
registers a non-secret origin, sends one short color and one very small RGB image
with a duration no longer than 1,000 ms, checks their replies, and in `finally`
attempts to clear only priority `199` and close. It never sends `Clear(-1)`, never
changes server configuration or instances, and contains no fixed private server
address. Unreachable optional infrastructure is reported separately and does not
fail the regular offline build.

### Real hardware validation

After automated and real-server integration tests, validate on Fire TV:

- a signed update retains Protocol Buffers as the default and works unchanged;
- the experimental checkbox is initially off for both new and upgraded installs;
- explicit activation, warning, selected status, and FlatBuffer port editing;
- real FlatBuffer registration, capture, and continuous LED output;
- server interruption, reconnect with FlatBuffer only, and automatic resume;
- intentional stop with no reconnect;
- explicit deactivation and a new Protocol Buffers session;
- no silent fallback under any FlatBuffer failure;
- persistence across app/device restart and usable D-pad navigation.

Do not claim Mobile hardware compatibility until it is tested on Mobile hardware.

## 11. Staged implementation plan

### Stage 1 - Reproducible schema generation only

**Status:** Completed

- Added the two unmodified 2.2.1 schemas with provenance and checksums.
- Pinned FlatBuffers/`flatc` 25.9.23 in Docker with verified source/artifact checksum.
- Added a custom `common` generation task and matching Java runtime.
- Proved clean reproducibility, JDK 17/AGP 9.3.0/Gradle 9.5.0 compatibility,
  `minSdk 21` D8 compatibility, both variants' builds, licenses, and APK deltas.
- Added no socket or service integration.

### Stage 2 - Isolated FlatBuffer socket client

**Status:** Completed

- Implemented framing, guarded parsing, registration state, bounded allocation,
  serialized exchanges, timeouts, errors, own-priority clear, and idempotent close.
- Added the complete 58-test offline fake-server suite.
- Kept the client unreachable from production UI and service code.

### Stage 3 - Minimal transport boundary

**Status:** Planned

- Add the small contract, immutable connection configuration, factory, and thin
  adapter around the unchanged `Hyperion` class.
- Route unit-test-only callers through the boundary first.
- Prove Protocol Buffers remains the default and its 23 existing tests are
  unchanged and passing.

### Stage 4 - Service lifecycle and preference guarantees

**Status:** Planned

- Route `HyperionThread` image/clear/close through exactly one selected transport.
- Add the stored transport value and separate FlatBuffer port with missing-value
  Protocol Buffers semantics.
- Make stop-before-reconnect explicit and snapshot selection per capture session.
- Add transport-neutral factory, reconnect, switching, stop, and no-fallback tests.

### Stage 5 - Experimental settings UI

**Status:** Planned

- Add the unchecked checkbox, warning, conditional FlatBuffer port, and selected
  transport status/errors to TV and Mobile settings.
- Update the TV connection test to use the explicit selection.
- Require a controlled capture restart after a transport change and validate
  D-pad behavior.

### Stage 6 - Optional real FlatBuffer integration

**Status:** Planned

- Add the environment-gated Hyperion NG 2.2.1 FlatBuffer test.
- Record registration, color, RGB image, replies, own-priority cleanup, and close
  separately from the offline suite.

### Stage 7 - Real Fire TV validation

**Status:** Planned

- Perform the update/default, opt-in, capture, LED, interruption/reconnect,
  intentional-stop, persistence, explicit return-to-Protobuf, no-fallback, and
  D-pad checks listed above.
- Record only observed TV results; do not infer Mobile validation.

### Stage 8 - Phase 3 release completion

**Status:** Planned

- Update user documentation, changelog, roadmap, version metadata, and release
  artifacts only after all prior gates pass.
- The already planned final Phase 3 target is release `2.2.0`, TV versionCode
  `2200`, and Mobile versionCode `1200`.
- Do not raise those values before completed discovery, implemented experimental
  FlatBuffer, confirmed Protocol Buffers default, automated tests, real Fire TV
  validation, and final documentation.

Each stage is independently reviewable and must leave Protocol Buffers usable.

## 12. Compatibility and migration guarantees

- The existing application ID and signing/update chain remain unchanged.
- Existing host, Protocol Buffers port, priority, reconnect, and capture settings
  retain their keys and meanings.
- An absent transport preference always means Protocol Buffers.
- Protocol Buffers remains selected on upgrades and new installations.
- FlatBuffer has a separate port; enabling it never overwrites either port.
- Discovery Phase 3A remains a Protocol Buffers endpoint workflow and never
  changes transport selection.
- Exactly one transport exists per capture session.
- No failure triggers automatic selection or fallback.
- Returning to Protocol Buffers is explicit and starts a controlled new session.
- The existing `Hyperion` implementation and its direct socket tests remain the
  reference for unchanged Protocol Buffers behavior.
- `targetSdk 26` remains temporary but is outside Phase 3B transport scope.
- FlatBuffer remains labeled experimental even after implementation until a
  separate decision changes that status.

## 13. Unresolved questions and required proofs

1. What conservative FlatBuffer request and reply limits fit the largest actual
   capture dimensions on supported Android devices without memory pressure? The
   upstream server defines no explicit maximum.
2. Should an unknown future transport preference fail visibly or resolve to the
   stable Protocol Buffers default? Missing values are already fixed to Protocol
   Buffers; corrupted/unknown values need a deliberate compatibility policy.
3. What stable, non-sensitive origin string should the app register for Mobile
   and TV, and should it include the release version? The protocol requires an
   origin but provides no structured client/version fields.
4. How should the Java reader state machine distinguish and order an unsolicited
   `registered = -1` event against an in-flight normal reply? This must be proven
   with a fake server and then a real 2.2.1 server.
5. Does a real 2.2.1 server require immediate re-registration after an own-priority
   clear when the socket remains open, or is lazy registration before the next
   frame sufficient?
6. Which client-local action should follow a valid non-registration server error:
   reuse the synchronized socket, or close conservatively? Upstream remains open,
   but real-server tests should verify subsequent request ordering.
7. What are the exact generated Java flags and package layout for unmodified
   namespace `hyperionnet` under `flatc` 25.9.23? Stage 1 must record the command
   rather than infer it from Hyperion's C++ flags.
8. What are the measured Mobile and TV APK size deltas and D8 method counts for
   `flatbuffers-java:25.9.23` plus generated classes?
9. Does the FlatBuffers Java 25.9.23 runtime pass all app builds and tests at
   `minSdk 21`, despite the upstream sample Android app using a newer minimum?
10. Should later FlatBuffer discovery reuse the common host or introduce a
    transport-specific discovered host? Phase 3B currently plans one shared host
    and separate ports; discovery extension is out of scope.
11. Should a saved transport change while capture runs be applied only at the
    next manual start, or should settings explicitly request an immediate
    controlled service restart? No live socket swap is allowed.

## 14. Upstream source references

All Hyperion links below are pinned to official tag `2.2.1`:

- [request schema](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/hyperion_request.fbs)
  and [reply schema](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/hyperion_reply.fbs);
- [official FlatBuffer client header](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/include/flatbufserver/FlatBufferConnection.h)
  and [implementation](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferConnection.cpp);
- [per-connection server header](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferClient.h)
  and [implementation](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferClient.cpp);
- [FlatBuffer server header](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/include/flatbufserver/FlatBufferServer.h)
  and [implementation](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/FlatBufferServer.cpp);
- [server configuration schema](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/hyperion/schema/schema-flatbufServer.json)
  for port, inactivity timeout, and pixel-decimation defaults;
- [FlatBuffer library build](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/flatbufserver/CMakeLists.txt)
  and [schema compiler function/dependency selection](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/dependencies/CMakeLists.txt);
- [mDNS service mapping](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/include/mdns/MdnsServiceRegister.h)
  for `_hyperiond-flatbuf._tcp.local.`;
- [representative standalone DRM grabber](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/src/hyperion-drm/hyperion-drm.cpp)
  and [standalone source tree](https://github.com/hyperion-project/hyperion.ng/tree/2.2.1/src);
- [pixel format declaration](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/include/utils/PixelFormat.h)
  and [RGB24/RGB32 byte handling](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/libsrc/utils/ImageResampler.cpp);
- [Hyperion 2.2.1 changelog](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/CHANGELOG.md),
  [test tree](https://github.com/hyperion-project/hyperion.ng/tree/2.2.1/test),
  [submodule pin](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/.gitmodules),
  and [MIT license](https://github.com/hyperion-project/hyperion.ng/blob/2.2.1/LICENSE).

The exact Google FlatBuffers sources referenced by that Hyperion tag are the
[25.9.23 commit](https://github.com/google/flatbuffers/commit/187240970746d00bbd26b0f5873ed54d2477f9f3),
its [Java runtime POM](https://github.com/google/flatbuffers/blob/187240970746d00bbd26b0f5873ed54d2477f9f3/java/pom.xml),
and its [Apache-2.0 license](https://github.com/google/flatbuffers/blob/187240970746d00bbd26b0f5873ed54d2477f9f3/LICENSE).
