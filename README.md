# Hyperion Grabber NG

Hyperion Grabber NG sends the content of an Android screen to a Hyperion
instance for processing.

This repository is a modernized fork maintained by Mathias (ElHanko). Dave
Anderson created the original Hyperion Android Grabber project. Both the
original work and this fork are distributed under the MIT License; see
[`LICENSE.txt`](LICENSE.txt) for the retained copyright notices.

The Android application ID is `com.elhanko.hyperiongrabber.ng`. The module
namespaces are:

- `com.elhanko.hyperiongrabber.ng.common`
- `com.elhanko.hyperiongrabber.ng.mobile`
- `com.elhanko.hyperiongrabber.ng.tv`

Information about Hyperion is available at
[hyperion-project.org](https://hyperion-project.org/).

## Development build

The reproducible Android build runs in Docker:

```bash
./docker/build.sh debug
```

The resulting APKs are copied to `dist/mobile/` and `dist/tv/`. See
[`docker/README.md`](docker/README.md) for cache and release-build details.

## Modernization status

The project compiles against Android API 36 with JDK 17, but deliberately keeps
`targetSdk 26` during this first modernization phase. Raising the
target SDK to 36, hardening the manifests, and adapting MediaProjection and
foreground-service behavior are separate follow-up work.

AndroidX versions were selected to preserve `minSdk 21`; in particular, Core
1.16.0 predates the newer AndroidX default of API 23. LocalBroadcastManager and
the existing AsyncTask-based TV setup remain temporarily unchanged. Konfetti
was raised from 1.1.2 to 1.3.2 because the old release depended on the removed
JCenter repository while the newer release keeps the existing API.
