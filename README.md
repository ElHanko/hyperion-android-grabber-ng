# Hyperion Grabber NG

Hyperion Grabber NG captures the screen of an Android device and sends it to a
Hyperion server. The project provides separate applications for Android mobile
devices and Android TV or Fire TV.

## Project status and fork history

This project is a modernized fork of the original **Hyperion Android Grabber**
created by Dave Anderson. Mathias (ElHanko) maintains the fork as Hyperion
Grabber NG. Both the original work and the changes in this fork are available
under the MIT License.

Version 2.1.0 completed the first modernization phase and received a baseline
test on Fire OS 8. Version 2.1.1 aligns the Protocol Buffers protocol with
Hyperion NG 2.2.1 and hardens the TCP transport. Its builds, automated tests,
signed update installation, continuous screen capture, real-server operation,
and reconnect behavior were successfully validated on the target Fire TV on
August 2, 2026. See the [changelog](CHANGELOG.md) for details.

## Features

- Screen capture sent through the Hyperion ProtoServer using Protocol Buffers
- RGB and RGBA image transfer, plus an optional average-color mode
- Separate user interfaces for mobile, Android TV, and Fire TV
- Configurable server address, priority, frame rate, and capture scaling
- Optional automatic reconnect after a transport failure
- Start-on-boot and quick-access features provided by the existing apps
- Complete request/reply framing with explicit Hyperion error handling

## Compatibility

| Property | Value |
| --- | --- |
| Project | Hyperion Grabber NG |
| Application ID | `com.elhanko.hyperiongrabber.ng` |
| Hyperion reference | Hyperion NG 2.2.1 |
| Protocol | Protocol Buffers through the Hyperion ProtoServer |
| `minSdk` | 21 |
| `compileSdk` | 36 |
| `targetSdk` | temporarily 26 |
| Target device | Fire TV Stick 4K Max, model AFTKRT |
| Fire OS | 8.1.8.0 |
| Target-device Android API | 30 |

TV version 2.1.1 was validated on the listed Amazon Fire TV Stick 4K Max. The
test successfully updated TV versionCode `2100` to `2101` with
`adb install -r`, retained application ID `com.elhanko.hyperiongrabber.ng`, and
confirmed application startup. Hyperion NG 2.2.1 communication through the
current Protocol Buffers transport, continuous screen capture and LED output,
reconnect after a real server interruption, and automatic continuation after
the server became available again all worked successfully. Intentionally
stopping the grabber did not trigger an unwanted reconnect.

The mobile APK was built and covered by the automated validation, but it was
not hardware-tested as part of this release validation.

The [Hyperion NG 2.2.1 compatibility document](docs/hyperion-ng-2.2.1-compatibility.md)
describes the exact protocol reference and the corrected schema differences.

## Installation

Build the release APK first as described under [Docker builds](#docker-builds).
To install or update the TV application with Android Debug Bridge, run:

```bash
adb install -r dist/tv/tv-release.apk
```

The mobile APK can be installed in the same way:

```bash
adb install -r dist/mobile/mobile-release.apk
```

The `-r` option preserves the installed application and its data during an
update. Updating from TV `2100/2.1.0` to `2101/2.1.1` also requires the new APK
to be signed with the same certificate as the installed package. Do not
uninstall the existing app if its data and update chain must be preserved. This
exact signed update path was successfully validated on the target Fire TV.

## Configuration

Configure at least these values in the application settings:

- Hyperion server hostname or IP address
- ProtoServer port, default `19445`
- priority in the ProtoServer range `100–199`, default `150`
- reconnect and reconnect delay, enabled by default with a five-second delay
- horizontal and vertical LED counts used for capture scaling
- frame rate and optional average-color mode
- optional start on device boot

The Hyperion ProtoServer must be enabled and reachable. New automatic server
discovery is not part of this release.

## Android TV and Fire TV usage

After installation, open the TV application from the Leanback launcher. Enter
the Hyperion host, port, and capture settings during setup, then use the app to
start or stop screen capture. Accept the Android system screen-capture prompt
when it appears.

Fire TV uses the same settings. TV version 2.1.1 was successfully exercised on
the documented AFTKRT device with continuous capture and LED output against a
real Hyperion NG 2.2.1 server. Reconnect resumed operation after a real server
interruption, while intentionally stopping the grabber did not cause an
unwanted reconnect.

## Docker builds

A working Docker installation is required. The builder provides the pinned JDK
and Android toolchain and uses a persistent Gradle cache.

Build debug APKs:

```bash
./docker/build.sh debug
```

Build signed release APKs:

```bash
./docker/build.sh release
```

Generated APKs are collected by module under:

```text
dist/mobile/
dist/tv/
```

See the [Docker build documentation](docker/README.md) for details about the
builder image, cache, and release-build workflow.

## Release signing

Release signing remains local. The project expects this ignored directory
structure:

```text
signing/
├── hyperion-ng-release.p12
└── signing.properties
```

Both app modules use the same local PKCS#12 configuration. The build script
mounts these files read-only and stops if the signing configuration is missing
or incomplete. Never commit or publish the keystore, passwords, private keys,
or other secret values in documentation or build output.

## Development and tests

The shared Android library contains the Protocol Buffers transport and generated
message classes. JVM tests start local `ServerSocket` instances, so regular test
runs work offline and require neither Android hardware nor an external Hyperion
server. They cover COLOR, RGB/RGBA IMAGE, CLEAR, CLEARALL, big-endian framing,
fragmented packets, timeouts, EOF, server errors, and reconnect after an
emulated connection failure.

The complete test task is:

```text
:common:test
```

It runs in the same Docker builder used for the application builds. Before
changing the transport, also consult the
[compatibility document](docs/hyperion-ng-2.2.1-compatibility.md).

An additional real-server test is strictly opt-in. It requires
`HYPERION_INTEGRATION_TESTS=1`, `HYPERION_TEST_HOST`, `HYPERION_TEST_PORT`, and
`HYPERION_TEST_PRIORITY=199`. Without the opt-in flag, it performs no network
access. Missing configuration, an unreachable server, or a failed optional
integration check causes that test to be skipped rather than failing the
regular build. The test sends only short-lived COLOR and RGB IMAGE requests and
attempts to clear its own priority in a `finally` block; it never sends
`CLEARALL`. This opt-in test was successfully run against a real Hyperion NG
2.2.1 ProtoServer with `tests=1`, `skipped=0`, `failures=0`, and `errors=0`;
both the COLOR request and the small RGB IMAGE request received successful
replies.

## Known limitations

- Protocol Buffers through the ProtoServer is currently used and remains
  supported by Hyperion NG 2.2.1. Hyperion's source records a longer-term plan
  to retire the ProtoServer after third-party clients have migrated.
- FlatBuffer transport is not part of this phase and requires a separate
  evaluation.
- New mDNS/SSDP server discovery is not part of this phase.
- `targetSdk 26` is intentionally temporary. Changes for newer MediaProjection
  and foreground-service requirements remain separate follow-up work.
- The mobile APK was built and automatically tested, but was not tested on
  mobile hardware during this validation.

## Project history

Dave Anderson developed the original Hyperion Android Grabber. Mathias
(ElHanko) continues it as Hyperion Grabber NG. The first modernization phase
updated the project identity, Java packages, AndroidX stack, View Binding, and
reproducible build and signing environment. The second phase begins with the
documented Hyperion NG 2.2.1 Protocol Buffers compatibility update and hardened
TCP transport. Earlier releases remain available in the
[changelog](CHANGELOG.md).

## License

The original work by Dave Anderson and the modernized fork by Mathias (ElHanko)
are licensed under the MIT License. See [LICENSE.txt](LICENSE.txt) for the full
license and retained copyright notices. See the
[privacy policy](privacy-policy.md) for information about data handling.
