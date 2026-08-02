# Changelog

## [2.2.0] - Unreleased

### Added

- Added optional Android NSD discovery for the Hyperion Protocol Buffers service
  type `_hyperiond-protobuf._tcp.` in both TV and mobile setup flows.
- Added explicit Start, Cancel, Retry, result selection, and manual-setup paths.
- Added a shared immutable discovery model, strict UTF-8 handling for Hyperion
  `id` and `version` TXT records, ID/endpoint deduplication, IPv4 preference with
  IPv6 retention, and atomic host/port adoption.
- Added offline JVM tests for discovery validation, grouping, address handling,
  serialized resolution, service loss, generation isolation, repeated lifecycle
  operations, and explicit preference selection.

### Changed

- Discovery now uses the actual resolved DNS-SD SRV port instead of assuming the
  manual default port `19445`.
- TV onboarding and settings now show the shared multiple-result discovery flow;
  mobile settings provide the same optional discovery entry point.
- Added a short-lived, non-reference-counted multicast lock for visible discovery
  sessions, including release handling for success, failure, cancellation, and
  owner destruction.

### Removed

- Removed the legacy `/24` subnet scanner, sequential fixed-port TCP probing,
  deprecated scanner task, and single-result activity.

### Verification status

- Platform-independent discovery tests pass without a LAN or Android device.
- Manual Fire TV discovery validation is still pending and is not claimed by
  this unreleased entry.

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
