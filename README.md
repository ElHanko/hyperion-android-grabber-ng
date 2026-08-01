# hyperion-android-grabber
Screen grabber for hyperion

This application will send the content of an android screen to a hyperion instance to handle the image.

Info about hyperion can be found on their website: 
https://hyperion-project.org/

## Development build

The reproducible Android build runs in Docker:

```bash
./docker/build.sh debug
```

The resulting APKs are copied to `dist/mobile/` and `dist/tv/`. See
[`docker/README.md`](docker/README.md) for cache and release-build details.

## Modernization status

The project compiles against Android API 36 with JDK 17, but deliberately keeps
`targetSdk 26` during this first build-only modernization phase. Raising the
target SDK to 36, hardening the manifests, and adapting MediaProjection and
foreground-service behavior are separate follow-up work.

AndroidX versions were selected to preserve `minSdk 21`; in particular, Core
1.16.0 predates the newer AndroidX default of API 23. LocalBroadcastManager and
the existing AsyncTask-based TV setup remain temporarily unchanged. Konfetti
was raised from 1.1.2 to 1.3.2 because the old release depended on the removed
JCenter repository while the newer release keeps the existing API.

---

**To join the alpha testers you will need to join this google+ group and opt-in with the beta testing URL below:**

https://plus.google.com/communities/101526321905444484496

**Beta testing is open and can be joined here:**

https://play.google.com/apps/testing/com.abrenoch.hyperiongrabber

**Playstore link (currently only visible to testers):**

https://play.google.com/store/apps/details?id=com.abrenoch.hyperiongrabber
