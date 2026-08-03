# Hyperion Android Grabber NG

Hyperion Android Grabber NG captures an Android screen and sends image data to a
Hyperion server. Separate applications are provided for mobile Android devices
and Android TV / Fire TV.

## Project status and fork history

This project is a modernized fork of Dave Anderson's original **Hyperion Android
Grabber**. Mathias (ElHanko) maintains the fork. Both the original work and the
fork are available under the MIT License.

Version 2.1.0 is the foundation release. Version 2.1.1 is the Hyperion NG 2.2.1
Protocol Buffers release. Version 2.2.0, released on August 3, 2026, completes
Phase 3: Phase 3A delivered Android NSD discovery for Protocol Buffers, and
Phase 3B delivered the experimental FlatBuffer opt-in. Protocol Buffers remains
the stable default. See the [changelog](CHANGELOG.md) and
[roadmap](docs/roadmap.md).

## Features

- Screen capture through the stable Protocol Buffers transport
- Optional experimental FlatBuffer transport with explicit user opt-in
- Protocol Buffers as the stable default, with no automatic transport fallback
- Separate manual Protocol Buffers and FlatBuffer ports
- Protocol Buffers manual default port `19445`; FlatBuffer default port `19400`
- Android NSD discovery for Protocol Buffers endpoints only
- RGB24 and supported four-byte RGB image transfer
- Optional average-color mode
- Reconnect using the selected transport only
- Mobile and Android TV / Fire TV interfaces

FlatBuffer is experimental and is not presented as a stable or generally
recommended replacement for Protocol Buffers.

## Compatibility

| Property | Value |
| --- | --- |
| Application ID | `com.elhanko.hyperiongrabber.ng` |
| Hyperion reference | Hyperion NG 2.2.1 |
| Transport | Stable Protocol Buffers ProtoServer transport with an optional experimental FlatBuffer transport |
| `minSdk` | 21 |
| `compileSdk` | 36 |
| `targetSdk` | temporarily 26 |

Stage 7 provided comprehensive FlatBuffer hardware validation on an Amazon Fire
TV while the tested app still carried version `2.1.1` / TV versionCode `2101`.
The final signed `2.2.0` / `2200` TV build was subsequently validated as an
in-place update and through short Protocol Buffers, FlatBuffer, Stop/Clear, and
application-restart regressions on the following environment:

| Property | Validated value |
| --- | --- |
| Device | Amazon Fire TV |
| ADB model | `AFTKRT` |
| Device codename | `karat` |
| Platform | Fire OS 8 / Android API 30 |

The Mobile `2.2.0` / `1200` APK was built, signed, and automatically tested, but
has not been validated on real mobile hardware.

The exact Protocol Buffers reference is described in the
[Hyperion NG 2.2.1 compatibility document](docs/hyperion-ng-2.2.1-compatibility.md).

## Installation

Build the release APKs first as described in [Docker build](#docker-build). Then
install or update them with Android Debug Bridge:

```bash
adb install -r dist/tv/tv-release.apk
adb install -r dist/mobile/mobile-release.apk
```

The signed TV update path `2101 / 2.1.1 -> 2200 / 2.2.0` was successfully
validated with `adb install -r`. The compatible signature retained the existing
application, its data and settings, and the original `firstInstallTime` while
updating `lastUpdateTime`. Uninstalling would remove application data and break
this update path, so it is not part of the update procedure.

## Configuration

Manual endpoint configuration remains available at all times.

| Setting | Default | Notes |
| --- | ---: | --- |
| Protocol Buffers port | `19445` | Stable default and the endpoint used by discovery |
| FlatBuffer port | `19400` | Separate experimental endpoint |

FlatBuffer must be explicitly enabled and confirmed. The change applies on the
next grabber start; it never live-switches a running capture session. Disabling
FlatBuffer selects Protocol Buffers for the next start. There is no automatic
fallback after a FlatBuffer error.

Android NSD discovery changes only the Protocol Buffers host and port after an
explicit selection. It does not enable FlatBuffer, alter the FlatBuffer port, or
provide FlatBuffer discovery.

## Android TV and Fire TV usage

Use the D-pad to enter manual settings or start Protocol Buffers discovery. The
FlatBuffer control is D-pad operable and has a separate port field. Enabling it
opens a confirmation dialog with **Cancel** and **Enable FlatBuffer**; Cancel and
Back leave Protocol Buffers selected. No touch input is required.

The status view identifies the transport actually active for the running capture
session. Changes made in settings take effect only at the next grabber start.

## Docker build

A working Docker installation supplies the pinned JDK and Android toolchain.

```bash
./docker/build.sh debug
./docker/build.sh release
```

APKs are collected under:

```text
dist/mobile/
dist/tv/
```

See [docker/README.md](docker/README.md) for builder and cache details.

## Release signing

Release signing remains local and ignored by Git. Both modules use a local
PKCS#12 configuration. Do not commit or publish credentials, certificate
contents, or other sensitive signing material.

## Development and tests

Regular JVM tests run offline and include generated FlatBuffer schema contracts,
reproducible code generation, isolated FlatBuffer fake-server tests, transport
adapter and factory tests, lifecycle and reconnect tests, and settings and
confirmation-dialog contracts. They also cover the existing Protocol Buffers
transport and Android NSD discovery.

The opt-in Protocol Buffers and FlatBuffer real-server tests are separate from
the regular matrix. The FlatBuffer runner requires an explicitly selected server:

```bash
HYPERION_FLATBUFFER_INTEGRATION_TEST=1 \
HYPERION_FLATBUFFER_HOST='<hyperion-host>' \
HYPERION_FLATBUFFER_PORT=19400 \
./tools/run-flatbuffer-integration-test.sh
```

Both real-server tests are opt-in and do not run during normal offline builds.
Stage 7 additionally validated the primary Fire TV use case on real hardware.
The final `2.2.0 / 2200` TV artifact passed its signed in-place update and short
Protocol Buffers, FlatBuffer, Stop/Clear, and application-restart regressions.

## Known limitations

- FlatBuffer remains experimental; Protocol Buffers remains the stable default.
- There is no automatic transport fallback.
- FlatBuffer discovery is not implemented; discovery is Protocol Buffers only.
- One capture session uses exactly one selected transport.
- Hardware validation covers one primary Fire TV model and use case, not all Android TV or Fire TV devices.
- Mobile hardware validation has not been performed.
- `targetSdk 26` is intentionally temporary; broader lifecycle modernization is Phase 4 work.

## Project history

Phase 1 established the independent package identity, modern Android build
foundation, and reusable signing. Phase 2 delivered Hyperion NG 2.2.1 Protocol
Buffers compatibility. Version 2.2.0 completed Phase 3: Phase 3A added Protocol
Buffers-only Android NSD discovery, while Phase 3B added the experimental
FlatBuffer transport without removing manual configuration or the stable
default. Earlier releases are kept in the [changelog](CHANGELOG.md).

## License

The original work and the ElHanko-maintained fork are licensed under the MIT
License. See [LICENSE.txt](LICENSE.txt) and the [privacy policy](privacy-policy.md).
