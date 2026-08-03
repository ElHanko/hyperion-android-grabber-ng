# Changelog

## [2.2.0] - 2026-08-03

TV versionCode: `2200`<br>
Mobile versionCode: `1200`<br>
Release version: `2.2.0`

### Added

- Android NSD discovery for the Protocol Buffers service
  `_hyperiond-protobuf._tcp.`, including explicit lifecycle and server selection.
- Reproducible official Hyperion NG 2.2.1 FlatBuffer schemas with a pinned
  FlatBuffers compiler and runtime.
- An isolated FlatBuffer client with Register, Color, RGB24, RGB32, and
  own-priority Clear support.
- A common transport abstraction with explicit `protobuf` and `flatbuffer`
  selection, a separate FlatBuffer port, and active-transport status context.
- Experimental FlatBuffer controls for Mobile and TV, including a D-pad
  confirmation dialog before FlatBuffer is enabled on TV.
- An opt-in real-server FlatBuffer integration test and isolated Docker runner.

### Changed

- The production capture lifecycle now uses the common transport boundary;
  reconnect recreates only the selected transport.
- Protocol Buffers remains the default for absent, empty, or unknown values.
- Discovery continues to configure Protocol Buffers only, using the resolved
  DNS-SD SRV port rather than assuming manual port `19445`.
- Transport selection applies only at the next capture start. Mobile and TV
  preserve separate Protocol Buffers and FlatBuffer ports.
- Visible status and errors identify the active transport.

### Fixed

- Replaced the obsolete `/24` discovery scanner and fixed-port probing with
  Android NSD discovery.
- Hardened transport framing and error handling for the supported release paths.
- Fixed TV FlatBuffer activation so that it requires explicit confirmation.
  Cancel and Back no longer leave a visually enabled but unconfirmed FlatBuffer
  state. This was found and corrected during pre-release validation, not in a
  published 2.2.0 release.

### Verified

#### Automated and offline

- Common, Mobile, and TV JVM test tasks, including generated schema checks,
  fake-server tests, adapter/factory/lifecycle/reconnect/settings tests, and
  confirmation-dialog contracts.
- Debug builds and signed release builds for both APKs.
- APK metadata, v1/v2 signature schemes, and the expected release certificate
  fingerprint.

#### Real server

- Phase 2 Protocol Buffers validation against Hyperion NG 2.2.1.
- Stage 6 FlatBuffer integration against Hyperion NG 2.2.1: registration,
  Color, RGB24, RGB32, own-priority Clear, re-registration, cleanup, and no
  Protocol Buffers fallback in that isolated integration path.

#### Fire TV Stage 7

- Signed in-place APK update with retained data for the Stage 7 `2.1.1 / 2101`
  build, not the final 2.2.0 artifact.
- Protocol Buffers reference capture, FlatBuffer capture, D-pad settings and
  activation dialog, Start/Stop/Clear, functional reconnect, and no app restart
  requirement.
- Mobile was not hardware-tested.

#### Final 2.2.0 Fire TV release validation

- Verified the exact signed TV APK with SHA-256
  `c682160145412c5d1b7996ae4089e8a0df835b04dde5ba59fa7a232a8f3c82fd`.
- Successfully updated `2101 / 2.1.1` to `2200 / 2.2.0` with
  `adb install -r`; the application ID and signature remained compatible,
  `firstInstallTime` was unchanged, and application data and settings were
  retained.
- Completed short Protocol Buffers and explicitly enabled FlatBuffer capture
  regressions with responsive LED output. The active FlatBuffer status and its
  separate port `19400` were confirmed.
- Verified Stop/Clear for both transport paths, releasing the grabber's own
  priority.
- Successfully force-stopped and restarted the application. The FlatBuffer
  selection, FlatBuffer port, and other settings remained stored, and the
  grabber could be started and stopped again.
- The signed Mobile APK has SHA-256
  `1519c7defd919a4fee630a3e38f387e927b7ac3b2dcdfa5354e7edeae38d5baf`;
  it was built and automatically tested but not hardware-validated.

### Known limitations

- FlatBuffer remains experimental; Protocol Buffers remains the stable default.
- There is no automatic transport fallback or FlatBuffer discovery.
- Hardware validation covers one primary Fire TV model/use case. Mobile
  hardware validation has not been performed.
- `targetSdk 26` remains intentionally temporary; broader lifecycle
  modernization is planned for Phase 4.

## [2.1.1] - 2026-08-02

TV versionCode: `2101`<br>
Mobile versionCode: `1101`<br>
Release version: `2.1.1`

### Changed

- Aligned the Protocol Buffers schema exactly with Hyperion NG 2.2.1, removing
  the obsolete `GRABBING` elements and assigning the official values and field
  numbers to `VIDEO` and `video`.
- Reworked the TCP transport to use complete big-endian framing, one reusable
  pair of buffered streams per connection, and serialized request/reply
  exchanges.
- Added configurable connect and read timeouts, enabled TCP no-delay, and made
  connection-state checks account for closed and shut-down sockets.
- Added validation for priorities, image dimensions, RGB/RGBA data lengths,
  and maximum request and reply sizes.
- Added a disabled-by-default real-server integration test controlled only by
  environment variables. It uses short-lived COLOR and RGB IMAGE requests and
  attempts to clear only its own test priority.

### Fixed

- Fragmented TCP headers and reply bodies are now read fully without using
  `InputStream.available()` for message framing.
- Hyperion error replies, missing success fields, invalid frame lengths, parse
  failures, EOF, and read timeouts now produce explicit error types.
- Unreliable transport states close deterministically so the existing reconnect
  logic can create a fresh connection.
- Replaced finalization with explicit, idempotent closing.

### Verified

- 23 JVM transport tests using local `ServerSocket` instances
- Protocol Buffers schema alignment against the official Hyperion NG 2.2.1 tag
- Fake-server request framing, fragmented replies, error handling, and reconnect
- Mobile and TV debug builds
- Mobile and TV signed release builds
- APK application ID, SDK, release-version, and versionCode metadata
- APK signatures and the expected release certificate fingerprint
- Signed update installation from TV versionCode `2100` to `2101` using
  `adb install -r`
- Installed TV versionCode `2101` with the retained application ID
  `com.elhanko.hyperiongrabber.ng`
- Application startup and successful operation on an Amazon Fire TV Stick 4K
  Max, model AFTKRT, running Fire OS 8.1.8.0 and Android API 30
- Connection to a real Hyperion NG 2.2.1 server through the ProtoServer
- Continuous screen capture and LED output on the real Fire TV
- Reconnect after a real Hyperion server interruption and automatic continuation
  after the server became available again
- No unwanted reconnect after intentionally stopping the grabber
- Opt-in integration test against a real Hyperion NG 2.2.1 ProtoServer:
  `tests=1`, `skipped=0`, `failures=0`, `errors=0`; short-lived COLOR and small
  RGB IMAGE requests were processed successfully

## [2.1.0] - 2026-08-02

TV versionCode: `2100`<br>
Mobile versionCode: `1100`

### Changed

- Renamed the project to **Hyperion Grabber NG**.
- Changed the application ID to `com.elhanko.hyperiongrabber.ng`.
- Completed the Java package migration to the ElHanko namespaces.
- Added a reproducible Docker build system for debug and release builds.
- Updated the build toolchain to Gradle 9.5.0, Android Gradle Plugin 9.3.0,
  and JDK 17.
- Updated compilation to `compileSdk 36`.
- Migrated the support libraries fully to AndroidX.
- Replaced Butter Knife with Android View Binding and direct Android APIs.
- Added reusable local PKCS#12 release signing.
- Set the release versions to Mobile `2.1.0` and TV `2.1.0`.

### Verified

- Reproducible debug and signed release builds for Mobile and TV
- Successful phase-1 baseline test on Fire OS 8

## Legacy releases

## [v1.0]
### Changes
- Arabic translation

### Fixed
- Possible NPE when stopping the grabber

## [v0.5-beta]
### Changes
- Added the ability to send only the average color of the screen
- French translation
- Norwegian translation
- Czech translation
- German translation
- Dutch translation
- Partial Russian translation
- Partial Spanish translation
- Removed openGL grabber option
- Added toggle grabber activity shortcut
- LEDs will now be cleared when rebooting or shutting down

### Fixed
- Lights now clear (if running) when shutting down
- Assertion bug in TV settings
- Possible null intent when starting grabber
- OOM bug

## [v0.4-alpha]
### Changes
- Start grabber on device boot
- Added some eye candy for when grabber is started
- General UI tweaks (tv & mobile)
- Reconnect behavior implemented for mobile build
- New connection wizard
- New settings/connection page (tv build)
- Quick settings tile to toggle grabber (mobile build)
- Screen orientation change updates grabber
- Configurable grabber image quality
- Pressing the notification will now return to the app's main activity

### Fixed
- Grabber would fail to resume when waking device
- OpenGL grabber sometimes halting immediately after starting screen grab
- Default grabber failing to send data the first time it is turned on
- Grabber not stopping when the host is unreachable
- Aspect ratio of grabbed image being slightly off
- OOM bug

## [v0.3-alpha]
### Changes
- Leanback launcher support (tv build)
- Revised layout (tv build)
- Reconnect if connection is lost to hyperion server (tv build)

## [v0.2-alpha]
### Changes
- App Icon
- Fancy toggle button
- Bug fixes
- New Grabber (old grabber can be enabled in the settings)

### Known Bugs
- OpenGL grabber will sometimes hang when started, making the lights unresponsive. Quitting the app and starting again generally fixes the problem.
- New grabber fails to send any data the first time it is initialized. Turning off and back on one more time seems to fix the problem.
