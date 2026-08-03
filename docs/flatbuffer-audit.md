# Experimental FlatBuffer transport audit

## 1. Scope and product constraints

This document preserves the original technical audit and records the completed
staged implementation of Phase 3B. The pre-implementation architecture reviewed
by the original audit was based on Hyperion NG 2.2.1 and the existing Android
Protocol Buffers transport; the implementation-status sections record the later
repository state through the version 2.2.0 release.

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
UI, discovery, test, manifest, or version change was made by the original audit
itself.

The architecture analysis in this document remains the original audit record.
The later, deliberately limited Stage 1 through Stage 8 implementations are
recorded under their implementation-status headings in section 6. Stage 6 adds
an explicitly enabled real-server check, Stage 7 records the separate Fire TV
hardware validation, and Stage 8 records release completion. Their results
remain distinct from the offline suite.

### Documentation consistency at the original audit point

At the time of the original audit, the existing [roadmap](roadmap.md),
[discovery audit](discovery-audit.md), [README](../README.md), and
[changelog](../CHANGELOG.md) were checked without modification. They then
described Phase 3 as in progress, Phase 3A as completed, Phase 3B as planned,
version 2.2.0 as unreleased, and FlatBuffer as not implemented. This is a
pre-implementation record only; the later implementation-status headings in
this document describe the current state.

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

### Stage 3 implementation status

**Status:** Completed on August 2, 2026

Stage 3 adds the Android-independent package
`com.elhanko.hyperiongrabber.ng.common.network.transport`. Its
[`HyperionTransport`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/HyperionTransport.java)
interface extends `Closeable` and exposes only `isConnected`, `setColor`,
`setImage`, own-priority `clear`, `transportName`, and `close`. Host, port,
priority, origin, and timeouts do not appear on individual operations, and no
wire-format type or generic raw-request method crosses the interface.

The immutable
[`HyperionTransportConfig`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/HyperionTransportConfig.java)
contains host, selected port, priority, origin, connect timeout, and read timeout.
It validates a non-empty host, port `1..65535`, priority `100..199`, and positive
timeouts. Origin remains optional for Protocol Buffers because that protocol does
not transmit it; selecting FlatBuffer requires a non-empty origin before any
socket is opened. The configuration contains no transport selection, preference,
Android object, default address, or device identity.

[`HyperionTransportType`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/HyperionTransportType.java)
is an enum with exact persistent identifiers `protobuf` and `flatbuffer`.
Parsing is deliberately case-sensitive. `null`, empty, differently cased, and
unknown values resolve to the stable `PROTOBUF` default. No port, discovery
result, or reachability check influences this conversion. Stage 3 defines these
values but does not save or read an Android preference.

The two final adapters are deliberately thin:

- [`ProtobufHyperionTransport`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/ProtobufHyperionTransport.java)
  constructs the unchanged `Hyperion` client, stores the configured priority,
  and supplies it to every Color, Image, and Clear call. It forwards color,
  duration, dimensions, and RGB24/RGB32 bytes unchanged. Its stable name is
  `Protocol Buffers`; the adapter does not expose the underlying `clearAll` or
  raw Protobuf API.
- [`FlatBufferHyperionTransport`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/FlatBufferHyperionTransport.java)
  constructs and directly delegates to the Stage 2 client. It neither repeats
  framing and registration nor changes image validation. Its stable name is
  `FlatBuffer (experimental)`. After own-priority Clear, `isConnected()` remains
  false until the next operation completes the Stage 2 lazy re-registration.

The stateless
[`HyperionTransportFactory`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/HyperionTransportFactory.java)
creates exactly one adapter for the explicitly supplied type and configuration.
A `null` type uses the same Protocol Buffers default. The factory owns no active
transport, opens no probe connection, performs no discovery, starts no thread,
and has no fallback branch. Construction failures from the selected transport
are returned to the caller; a FlatBuffer registration error or timeout never
attempts Protocol Buffers.

The common operations do not conceal the protocols' internal semantics:

- Protocol Buffers has no registration handshake, includes priority in each
  request, and requires the explicit reply `success` field. The existing client
  and all its additional public methods remain unchanged.
- FlatBuffer registers origin and priority during construction. Normal Color and
  Image requests do not repeat the priority, success is the absence of `error`,
  and `registered = -1` can remove readiness. Own-priority Clear therefore
  requires lazy re-registration before the next operation.

The adapters do not wrap existing transport failures. `HyperionServerException`,
`HyperionTimeoutException`, `HyperionProtocolException`, ordinary `IOException`,
and local `IllegalArgumentException` behaviors remain observable. This also means
that no error can trigger an implicit transport change.

Forty-three new offline JVM tests cover the type and exact identifiers, safe
default, immutable configuration, adapter delegation for Color/RGB24/RGB32/Clear,
transport names, readiness and Clear re-registration, preserved exception types,
idempotent close, exact factory selection, single-connection failures,
no-fallback behavior, statelessness, and independent factory results. All use
dynamic loopback `ServerSocket` endpoints and contact no external server. The
complete forced test run passed with 152 Common tests (the one existing opt-in
Protocol Buffers integration test skipped), one Mobile test, and no TV JVM test
sources. Mobile and TV Debug and signed Release APKs built successfully with
unchanged application ID, versions, and signing identity.

Only tests call the Stage 3 boundary. `HyperionThread`, screen service, encoders,
Activities, Fragments, Receivers, preferences, discovery, and reconnect logic
still use no Stage 3 type or factory. Protocol Buffers remains the sole production
transport. Product integration, stored selection, a separate FlatBuffer port,
settings UI, reconnect selection, real-server testing, and hardware validation
remain later stages.

### Stage 4 implementation status

**Status:** Completed on August 2, 2026

Stage 4 integrates the Stage 3 boundary into the production capture lifecycle.
The production path is now `HyperionScreenService -> HyperionThread ->
HyperionTransportFactory -> one selected HyperionTransport`. `HyperionThread`
does not construct `Hyperion`, build Protocol Buffers requests, or know either
wire protocol. It delegates frames through `setImage` with duration `-1`, delegates
own-priority clearing through `clear`, and delegates connection shutdown through
idempotent `close`. The encoder remains transport-neutral and unchanged.

Two internal, non-visible preference keys were added:

- `pref_key_transport` stores exactly `protobuf` or `flatbuffer`;
- `pref_key_flatbuffer_port` stores the separate FlatBuffer port and has the
  non-persisting default `19400`.

The existing `pref_key_port` remains the Protocol Buffers port, and both
transports continue to share `pref_key_host`. A missing, empty, unknown, or
differently cased transport value resolves to Protocol Buffers. The pure-Java
`HyperionConnectionSelection` resolver parses and validates only the port for the
selected transport, so a damaged unselected port cannot block startup. It never
writes, repairs, migrates, or infers preferences. FlatBuffer configurations use
the fixed, non-personal origin `Hyperion Android Grabber NG`; Protocol Buffers
does not require an origin.

`HyperionScreenService.prepared()` resolves and snapshots the transport type,
shared host, selected port, priority, origin, and timeouts before starting the
connection thread. Invalid selected configuration becomes a controlled startup
error rather than an uncaught preference parsing failure. Service logs and status
broadcasts identify the selection as `Protocol Buffers` or `FlatBuffer
(experimental)` through the additional stable `SERVICE_TRANSPORT` extra while
retaining the existing status and error extras.

`HyperionThread` owns one factory-driven connection loop. The factory is called
once per attempt and only for the snapshotted type and configuration. Initial
failure before the first successful connection remains a startup failure without
retry. After a successful connection, an unexpected operation failure closes the
broken transport before a replacement is created, waits using the existing
configured reconnect delay, and retries only the same type and configuration.
Frames arriving while no transport is active are discarded rather than queued.
There is no probing, second diagnostic connection, or fallback to the other
transport.

Intentional stop sets the synchronized stop state and disables reconnect before
the existing best-effort clear/close sequence. It wakes a pending reconnect delay,
prevents later factory results from being published, suppresses late connected or
error callbacks, and closes a candidate that finishes after stop. Repeated
disconnect remains safe. A FlatBuffer own-priority Clear is not special-cased in
the thread: the next image operation can use the Stage 2 client's existing lazy
re-registration behavior on the same transport.

Thirty-eight new offline JVM tests cover preference resolution, exact stored
values, selected-port isolation, the fixed origin, factory selection, frame and
Clear delegation, failure-category preservation, close ordering, single-loop
reconnect, discarded frames, intentional stop and late factory completion,
no-fallback behavior, and the service broadcast contract. The complete forced
matrix passed with 190 Common tests (one existing opt-in Protocol Buffers
integration test skipped), one Mobile test, and no TV JVM test sources. Mobile
and TV Debug and signed Release APKs also built successfully with unchanged
application ID, versions, and signing identity.

Stage 4 adds no visible setting or transport switch, changes no discovery code,
and performs no real FlatBuffer server or hardware validation. The internally
wired FlatBuffer selection is therefore not yet a generally available or
validated user feature. Those gates remain in Stages 5 through 8; Protocol
Buffers remains the stable default.

### Stage 5 implementation status

**Status:** Completed on August 2, 2026

Stage 5 makes the existing internal selection visible without adding a second
persisted boolean. The shared Android-independent
[`HyperionTransportPreferenceBinding`](../common/src/main/java/com/elhanko/hyperiongrabber/ng/common/network/transport/HyperionTransportPreferenceBinding.java)
maps the non-persistent UI control to exactly `flatbuffer` when enabled and
`protobuf` when disabled. Missing, empty, unknown, and differently cased values
appear as the disabled Protocol Buffers default; merely opening settings does not
rewrite them.

Mobile uses a non-persistent AndroidX `CheckBoxPreference` titled **Use
experimental FlatBuffer transport**. It retains the existing Protocol Buffers
port field, shows the separate FlatBuffer port only while FlatBuffer is enabled,
and preserves the latter value when it is hidden. The absent FlatBuffer port is
presented as `19400` without storing that default. Input must be a decimal port
in `1..65535`; empty, non-numeric, and out-of-range input is rejected with a
specific message and no automatic correction.

TV and Fire TV use the same binding in the existing Leanback guided settings.
The D-pad-focusable experimental checkbox immediately enables or disables the
separate FlatBuffer port action. The disabled action remains visible for context
but is not focusable, so disabling it cannot leave focus on an inaccessible
element. The warning is a multiline text summary rather than a touch-only
dialog. It states that FlatBuffer is experimental, Protocol Buffers remains the
recommended default, failures do not fall back automatically, and changes take
effect on the next grabber start. The existing manual Protocol Buffers setup and
ProtoServer discovery stay available and unchanged.

The running transport remains independent of the newly stored choice. Mobile and
TV status views read `SERVICE_TRANSPORT` from the service broadcast and show, for
example, `Connected using Protocol Buffers` or `Connected using FlatBuffer
(experimental)`. They retain the previous status behavior for broadcasts without
that extra. Error toasts add the service-reported transport name as context while
preserving the original error text. Neither UI derives an active transport from a
preference, so changing the setting does not live-swap a running connection.

Twenty new offline JVM tests cover the binding defaults and exact identifiers,
port validation, status and error formatting, the Mobile and TV resource/source
contract, visibility/focus behavior, discovery isolation, and use of the service
transport extra. The complete forced matrix passed with 210 Common tests (one
existing opt-in Protocol Buffers integration test skipped), one Mobile test, and
no TV JVM test sources. The loopback fake-server close sequence was also made
deterministic: it waits for an already connected client action before closing its
listener, avoiding a spurious `ServerSocket.accept()` failure during idempotent
close tests.

No emulator was available for a manual visual inspection in this environment.
The Mobile and TV interaction claims above are therefore covered by compiled
UI/resource contracts and code review, not by emulator or Fire TV hardware
validation. No real Hyperion server, FlatBuffer socket, screen capture, or LED
output was contacted or started for Stage 5. FlatBuffer remains experimental;
real-server validation and hardware validation remain later-stage work.

## 7. Minimal transport abstraction

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

The implemented Stage 3 contract is:

```text
isConnected()
setColor(rgb, durationMs)
setImage(bytes, width, height, durationMs)
clear()
close()
transportName()
```

The selected host, selected transport-specific port, priority, origin, connect
timeout, and read timeout form an immutable configuration object supplied to a
factory. The factory creates exactly one selected implementation. Priority does
not appear on each call because it is configuration for both transports, even
though the Protocol Buffers adapter delegates it into every existing request.

The factory returns an already-connected object, preserving both existing client
constructors. Successful creation means socket-connected for Protocol Buffers
and socket-connected plus registered for FlatBuffer. Callers therefore observe
one ready-or-throw construction boundary without a duplicate `connect()` state.

The interface extends `Closeable` or equivalent. `close()` is idempotent and does
not implicitly clear another priority. The lifecycle explicitly attempts
`clear()` for its own priority before close when appropriate. Public operations
are thread-safe and serialize protocol exchanges. The FlatBuffer implementation
owns framing and registration state, not reconnect policy.

The encoder listener can remain `sendFrame(byte[], width, height)`. Its current
RGB output is already suitable for both transports. Stage 4 replaced the direct
Protocol Buffers request construction in `HyperionThread` with
`activeTransport.setImage(...)`; that is the essential decoupling. Its small
factory seam makes lifecycle tests possible without sockets. The existing TV
diagnostic color test remains explicitly Protocol Buffers-specific; expanding it
to perform a FlatBuffer server check is outside the completed Stage 5 UI scope.

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
- an unrecognized, empty, or differently cased stored value resolves
  conservatively to Protocol Buffers before connection creation. This is input
  normalization, not runtime fallback.

Stage 4 keeps the existing common host and Protocol Buffers port keys and adds
the internal keys `pref_key_transport` and `pref_key_flatbuffer_port`. The latter
has a manual default of `19400`; reading the default does not store it. Only the
selected port is parsed and validated. A future UI check or uncheck must not
modify host, Protocol Buffers port, or FlatBuffer port.

Phase 3A discovery continues to browse only `_hyperiond-protobuf._tcp.` and to
write only the common host and existing Protocol Buffers port after explicit
selection. It never changes the transport value or FlatBuffer port. Extending
discovery later would require a separate, explicitly scoped change for
`_hyperiond-flatbuf._tcp.`; it is not a prerequisite for manual FlatBuffer use.

### Implemented presentation

Stage 5 adds one visible experimental control to both settings experiences:

```text
[ ] Use experimental FlatBuffer transport
    FlatBuffer is experimental. Protocol Buffers remains the recommended default.
```

The control itself is deliberately non-persistent. Its change handler uses the
existing preference wrapper to write only the stable `protobuf` or `flatbuffer`
value. Mobile hides the separate FlatBuffer port while disabled; TV keeps it
visible but disabled and outside D-pad focus. Both preserve the configured port
and show the manual default `19400` without inferring a transport from either
port. The warning explains the no-fallback policy and that the change applies to
the next grabber start.

The status surface uses the transport reported by the service, not the current
settings value. This prevents a saved next-session choice from being presented as
a live transport switch. No connection is started, stopped, or restarted by the
Mobile control. The existing TV settings flow retains its established lifecycle;
the transport control itself never hot-swaps a transport.

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

### Transport-neutral service tests

**Stage 4 status:** Completed with 38 passing tests.

Use a fake factory and fake transports to prove:

- Protocol Buffers is the default for a new or missing preference;
- existing preferences remain compatible and are not rewritten;
- exactly one transport is created;
- reconnect recreates the selected transport only;
- intentional stop prevents reconnect;
- no error causes an automatic fallback;
- status and failures identify the selected transport;
- the Protocol Buffers adapter preserves the existing client behavior and tests.

The suite also proves selected-port-only validation, unchanged factory
configuration across reconnect, close-before-replacement ordering, discarded
frames during reconnect, wakeable reconnect delay, late-candidate cleanup, and
the additive service broadcast contract. Stage 5 separately covers the visible
settings binding and presentation contract without starting a connection.

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

Stage 6 provides a separate JVM test,
[`FlatBufferHyperionIntegrationTest`](../common/src/test/java/com/elhanko/hyperiongrabber/ng/common/network/flatbuffer/FlatBufferHyperionIntegrationTest.java),
that exercises the production-facing path rather than a second protocol client:

```text
HyperionTransportFactory
  -> FlatBufferHyperionTransport
  -> FlatBufferHyperionClient
  -> selected real Hyperion NG 2.2.1 FlatBuffer server
```

It is enabled only when `HYPERION_FLATBUFFER_INTEGRATION_TEST=1` exactly. A
missing, empty, `0`, `true`, `yes`, or any other value is skipped with a JUnit
assumption before host parsing, DNS resolution, or socket construction. Once
enabled, missing or malformed configuration and every connection or protocol
failure fail clearly; they are not silently converted into a skipped test.

The supported environment variables are:

```text
HYPERION_FLATBUFFER_INTEGRATION_TEST=1  required exact opt-in
HYPERION_FLATBUFFER_HOST=<explicit host> required once enabled
HYPERION_FLATBUFFER_PORT=19400          optional, valid range 1..65535
HYPERION_FLATBUFFER_PRIORITY=190        optional, valid range 100..199
HYPERION_FLATBUFFER_CONNECT_TIMEOUT_MS=3000  optional, positive
HYPERION_FLATBUFFER_READ_TIMEOUT_MS=3000     optional, positive
```

The registered origin is the fixed non-personal string `Hyperion Android Grabber
NG integration test`. The test creates exactly one `FLATBUFFER` transport and
has no Protocol Buffers fallback, probe, or second connection type. It verifies
registration, one muted `0x102030` Color request, a static 2-by-2 RGB24 image,
a static 2-by-2 RGB32 image, own-priority Clear, and lazy re-registration on a
subsequent Color request. RGB32 testing only verifies that four bytes per pixel
are accepted; the Hyperion NG 2.2.1 server uses the first three bytes as RGB and
ignores the fourth byte.

The `finally` path best-effort clears only its configured priority and closes the
transport. A cleanup failure is attached to the primary failure instead of
masking it. The test never uses `clearAll`, changes configuration or instances,
or contains a fixed private address.

Use the reproducible Docker runner only against an explicitly selected test
server:

```bash
HYPERION_FLATBUFFER_INTEGRATION_TEST=1 \
HYPERION_FLATBUFFER_HOST='<hyperion-host>' \
HYPERION_FLATBUFFER_PORT=19400 \
HYPERION_FLATBUFFER_PRIORITY=190 \
./tools/run-flatbuffer-integration-test.sh
```

The runner passes only the FlatBuffer integration variables into the existing
Docker builder and runs only this test class. It copies the source into a
temporary isolated workspace, so generated build outputs are cleaned up without
changing ownership in the working tree. Protocol Buffers is not contacted.

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

**Status:** Completed

- Added the small contract, immutable connection configuration, stable transport
  type, stateless factory, and thin adapters around both unchanged clients.
- Kept all callers test-only; the production path does not use the boundary.
- Added 43 adapter, type, configuration, factory, and no-fallback tests and kept
  all existing Protocol Buffers, discovery, schema, and Stage 2 tests passing.

### Stage 4 - Service lifecycle and preference guarantees

**Status:** Completed

- Routed `HyperionThread` image, own-priority Clear, and close through exactly one
  selected transport created by the shared factory.
- Added the internal stored transport value and separate FlatBuffer port with
  safe Protocol Buffers default semantics and selected-port-only validation.
- Snapshotted selection per capture session and implemented one wakeable reconnect
  loop that closes before replacement and never changes transport type.
- Made intentional stop explicit and added 38 preference, lifecycle, reconnect,
  delegation, status-contract, and no-fallback tests.

### Stage 5 - Experimental settings UI

**Status:** Completed

- Added a non-persistent experimental checkbox to Mobile and Leanback TV settings
  that maps only to the stable string transport value.
- Added a separate FlatBuffer port with default presentation `19400`, validation,
  Mobile visibility control, and TV enabled/focus control.
- Added experimental/no-fallback/next-start warning text and service-reported
  active transport context to Mobile and TV status and error presentation.
- Added 20 pure JVM binding, status, and resource/source contract tests. No
  connection is started or switched by a settings change.

### Stage 6 - Optional real FlatBuffer integration

**Status:** Completed on August 3, 2026

- Added the independently environment-gated Hyperion NG 2.2.1 FlatBuffer test
  and offline configuration contract tests.
- The test uses the factory and adapter path, validates Register, Color, RGB24,
  RGB32, own-priority Clear, re-registration, ready state, cleanup, and the
  absence of a Protocol Buffers fallback.
- Added an isolated Docker runner that forwards no protobuf-integration opt-in
  and leaves no generated build output in the working tree.
- The offline configuration contracts and the regular JVM matrix passed with
  220 Common tests, including the existing ProtoServer and new FlatBuffer
  real-server checks both skipped without opt-in; the Mobile test passed and TV
  has no JVM test sources.
- The isolated Docker runner completed with `BUILD SUCCESSFUL` against a real
  Hyperion NG 2.2.1 FlatBuffer server on August 3, 2026. It used FlatBuffer port
  `19400`, test priority `190`, and exactly one `HyperionTransportType.FLATBUFFER`
  path through `HyperionTransportFactory`, `FlatBufferHyperionTransport`, and
  `FlatBufferHyperionClient`.
- The real-server run confirmed TCP connection, Register and ready state, the
  muted Color request, RGB24 RawImage, RGB32/RGBA RawImage, own-priority Clear,
  automatic re-registration, and a successful normal operation after
  re-registration.
- Final cleanup successfully cleared only priority `190` and closed the
  connection in an orderly manner. The run used no `clearAll` and made no
  Protocol Buffers connection, fallback, or probe.
- No private target address is recorded.

### Stage 7 - Fire TV hardware validation

**Status:** Completed on August 3, 2026 for the primary Android TV / Fire TV use case

#### Validation environment and signed update path

The validation used a signed TV release APK on an Amazon Fire TV device. ADB
reported model `AFTKRT` and device codename `karat`. The installed application
remained `com.elhanko.hyperiongrabber.ng`, version `2.1.1`, TV versionCode
`2101`. The selected server was Hyperion NG `2.2.1` with FlatBuffer port
`19400`; no target address or local network detail is recorded.

The signed release APK was installed as an in-place update with `adb install -r`
over an existing installation. Installation succeeded, the application ID and
release signing remained compatible, `firstInstallTime` was retained, and
`lastUpdateTime` changed. Existing application data and settings remained
available. This validates the Stage 7 in-place update path for the tested TV
release, not a general migration claim.

#### Protocol Buffers reference and FlatBuffer settings

After the update, the existing Protocol Buffers use case was tested first on the
same Fire TV: the grabber started, MediaProjection captured the screen, images
reached Hyperion, LEDs responded to image content, and stopping succeeded. This
is a real reference-use-case check that the FlatBuffer work did not damage the
tested Protocol Buffers flow; it is not a complete Protocol Buffers regression
suite.

FlatBuffer settings were then checked with the D-pad only. The experimental
control is reachable, the FlatBuffer port remains separately configurable and
retained at `19400`, and the separate Protocol Buffers port remains unchanged.
The FlatBuffer port action is not focusable while FlatBuffer is disabled and
becomes focusable after confirmed activation. Back navigation works, and a
settings change does not live-switch a running transport: the selected transport
is used only at the next grabber start.

The first hardware pass found that enabling FlatBuffer showed only the permanent
experimental summary rather than an explicit confirmation. Commit
`6a6e965 Require confirmation before enabling FlatBuffer on TV` corrected this
with `android.app.AlertDialog` and no additional UI library. The corrected dialog
was revalidated on the same Fire TV. `Cancel` is initially focused, both actions
are D-pad reachable, and Cancel leaves the persisted transport as `protobuf`,
the control visually disabled, and the FlatBuffer port action disabled. The Back
button and any dialog cancellation have the same result. `Enable FlatBuffer` is
also D-pad reachable; only that deliberate confirmation stores `flatbuffer`,
enables the port action, and retains the saved FlatBuffer port. It does not start
or restart a grabber and does not live-switch an existing connection.

No additional preference key, persistent consent boolean, migration value, or
one-time approval is used. The confirmation is requested on every deliberate
Protocol Buffers-to-FlatBuffer switch.

#### FlatBuffer capture, stop, and restart

After explicit activation, the Fire TV grabber connected to the configured
FlatBuffer endpoint and showed the active FlatBuffer transport. MediaProjection
capture worked, a virtual `HyperionScreenEncoder` was created, image data reached
Hyperion, and LEDs responded continuously to changing image content. In this
tested use case, the visible result was equivalent to the preceding Protocol
Buffers reference behavior; this does not claim that the transports are
technically identical or that every FlatBuffer capability was tested.

The application remained stable. Stopping removed the virtual display encoder
and released the grabber's own Hyperion priority through the normal Stop/Clear
path. A subsequent start succeeded. No claim is made for other Fire TV models.

#### Functional reconnect validation and system-level evidence

With FlatBuffer selected and automatic reconnect enabled, the running server or
FlatBuffer service was stopped for longer than one configured reconnect interval.
The connection and LED updates stopped as expected while the application stayed
stable and the grabber was not manually stopped or restarted. After the service
became available again, capture transmission and LED updates resumed
automatically without an application restart. The grabber could then be stopped
and started normally. The FlatBuffer reconnect scenario therefore passed as a
functional Fire TV validation.

A system-level Fire OS log provided supplementary lifecycle evidence only:

- `10:10:21`: capture started under application process PID `25843`.
- `10:16:05`: the same process remained active and the foreground notification
  was active.
- `10:20:21`: manual stop retained that process, removed the foreground
  notification, and removed `HyperionScreenEncoder`.
- `10:20:25`: manual restart created a new `HyperionScreenEncoder`, switched it
  to `ON`, and created the foreground notification again.

A system-level Fire OS log confirmed that the application process remained alive
during the validation run and that subsequent stop and restart operations cleanly
removed and recreated the virtual capture display and foreground service
notification. The signed release build did not expose application-specific
transport or reconnect messages, so the reconnect boundary and continued
FlatBuffer selection were validated functionally and through the existing
transport architecture and tests rather than through an application-level
reconnect trace.

Accordingly, the system log does not directly establish the socket-disconnect
time, individual reconnect attempts, a successful transport handshake, the
transport name, the selected port or priority, the resumed network transfer, or
the absence of a Protocol Buffers fallback.

#### No-fallback boundary and Mobile scope

Functionally, transmission resumed after the FlatBuffer service returned without
a settings change, while the application stayed on its previously selected
FlatBuffer configuration. No visible port probing or transport switch was
observed. The system-level log alone cannot prove this. The no-fallback guarantee
is primarily established by the selected-transport preference, transport factory,
lifecycle tests, and Stage 6 real-server integration; Stage 7 adds real Fire TV
functional validation to that evidence.

The Mobile APK continued to build, and existing Mobile and Common tests remained
part of the test matrix. Stage 7 contains no Mobile hardware validation. It is
completed for the primary Android TV / Fire TV use case; Mobile hardware
validation is not a release blocker for version `2.2.0` and is not claimed here.

### Stage 8 - Phase 3 release completion

**Status:** Completed on August 3, 2026

The final release is version `2.2.0`, TV versionCode `2200`, and Mobile
versionCode `1200`. It preserves application ID
`com.elhanko.hyperiongrabber.ng`, the existing SDK levels and signing identity,
the Protocol Buffers default, and FlatBuffer's experimental opt-in status.

The forced offline validation matrix passed without enabling either real-server
integration-test environment flag: 221 Common unit tests with two expected
opt-in skips, one Mobile unit test, and four TV unit tests completed with zero
failures and zero errors. Both Debug builds and signed Release builds completed.
APK metadata, v1 and v2 signatures, and the common established release
certificate were verified. The certificate SHA-256 fingerprint is:

```text
18aa41fd37c7531ec67f7f84f14beefddff39e7cc28540d41ed0bfc602173700
```

The final signed APK checksums are:

```text
Mobile: 1519c7defd919a4fee630a3e38f387e927b7ac3b2dcdfa5354e7edeae38d5baf
TV:     c682160145412c5d1b7996ae4089e8a0df835b04dde5ba59fa7a232a8f3c82fd
```

The exact final TV artifact was installed successfully with `adb install -r`
over `2.1.1 / 2101`. The resulting installation reported `2.2.0 / 2200` with
the same application ID and a compatible signature. `firstInstallTime` remained
unchanged, `lastUpdateTime` advanced, and existing application data and settings
were retained, including host, both transport ports, priority, reconnect, and
capture settings.

A short Protocol Buffers regression confirmed MediaProjection, image transfer,
responsive LED output, and Stop/Clear of the selected priority. A separate
explicitly confirmed FlatBuffer regression verified the active FlatBuffer status,
port `19400`, MediaProjection, image transfer, responsive LED output, and
Stop/Clear of its own priority without a visible error. After an application
force-stop and restart, the app started normally, retained the FlatBuffer
selection and port, and could start and stop the grabber again.

The final artifact did not repeat the server-interruption reconnect scenario;
Stage 7 remains the functional reconnect evidence for the same implementation.
The system-log evidence and its limits documented in Stage 7 remain unchanged.
Mobile `2.2.0 / 1200` was built, signed, and automatically tested but was not
hardware-validated. Stage 8, Phase 3B, and Phase 3 are completed.

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
- Returning to Protocol Buffers is explicit and applies to the next grabber
  session; no running transport is hot-swapped.
- The existing `Hyperion` implementation and its direct socket tests remain the
  reference for unchanged Protocol Buffers behavior.
- `targetSdk 26` remains temporary but is outside Phase 3B transport scope.
- FlatBuffer remains labeled experimental even after implementation until a
  separate decision changes that status.

## 13. Remaining questions and required proofs

1. What conservative FlatBuffer request and reply limits fit the largest actual
   capture dimensions on supported Android devices without memory pressure? The
   upstream server defines no explicit maximum.
2. How should the Java reader state machine distinguish and order an unsolicited
   `registered = -1` event against an in-flight normal reply? The Stage 6
   real-server run confirms own-priority Clear followed by lazy registration and
   a subsequent normal operation, while this narrower unsolicited-reply ordering
   remains a future robustness question.
3. Does a real 2.2.1 server require immediate re-registration after an own-priority
   clear when the socket remains open, or is lazy registration before the next
   frame sufficient? The Stage 6 real-server run confirmed that lazy
   re-registration before the next normal request is sufficient.
4. Which client-local action should follow a valid non-registration server error:
   reuse the synchronized socket, or close conservatively? Upstream remains open,
   but this remains a future failure-mode ordering question.
5. Should later FlatBuffer discovery reuse the common host or introduce a
   transport-specific discovered host? Phase 3B uses one shared host and separate
   ports; a discovery extension remains out of scope.

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
