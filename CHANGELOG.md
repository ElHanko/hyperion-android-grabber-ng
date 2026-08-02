# Changelog

## [2.1.1] — versionCode 2101 — Unreleased

Erstes Kompatibilitätsupdate der zweiten Modernisierungsphase:

### Changed

- Das ProtoBuffer-Schema wurde exakt mit Hyperion NG 2.2.1 synchronisiert; die
  veralteten `GRABBING`-Elemente wurden entfernt und `VIDEO` sowie `video`
  erhielten die offiziellen Werte und Feldnummern.
- Der TCP-Transport verwendet jetzt vollständiges Big-Endian-Framing,
  wiederverwendete gepufferte Streams und serialisierte Request/Reply-Zugriffe.
- Connect- und Read-Timeouts sind konfigurierbar, TCP-NoDelay ist aktiviert und
  der Verbindungszustand wird vollständig geprüft.
- Prioritäten, Bildabmessungen, RGB-/RGBA-Datenlängen sowie maximale Request-
  und Reply-Größen werden vor der Verarbeitung validiert.

### Fixed

- Fragmentierte TCP-Header und Reply-Bodys werden vollständig eingelesen;
  `InputStream.available()` wird nicht mehr zur Paketabgrenzung verwendet.
- Hyperion-Fehlerantworten, fehlende Erfolgsfelder, ungültige Paketlängen,
  Parsefehler, EOF und Read-Timeouts werden mit nachvollziehbaren Fehlertypen
  behandelt.
- Nach einem nicht mehr zuverlässigen Transportzustand wird die Verbindung
  deterministisch geschlossen, sodass die vorhandene Reconnect-Logik eine neue
  Verbindung aufbauen kann.
- `finalize()` wurde durch idempotentes, explizites Schließen ersetzt.

### Verified

- 23 JVM-Transporttests mit lokalem `ServerSocket` prüfen Requests, Framing,
  RGB/RGBA, fragmentierte Antworten, Fehlerfälle, mehrere Requests auf einer
  Verbindung und einen neuen Verbindungsaufbau nach einem Abbruch.
- Debug- und signierte Release-APKs für Mobile und TV wurden reproduzierbar im
  Docker-Builder erstellt.
- Schema- und Transportkompatibilität wurden gegen die offiziellen
  Hyperion-NG-2.2.1-Quellen und automatisierte Fake-Server-Tests bestätigt.
- Versionen: Mobile `1101/2.1.1`, TV `2101/2.1.1`.

Der reale Update-, Fire-TV- und Hyperion-Server-Test für 2.1.1 steht noch aus.
Bis zu dessen Abschluss bleibt diese Version `Unreleased`.

## [2.1.0] — versionCode 2100 — 2026-08-02

Abschluss der ersten Modernisierungsphase:

### Changed

- Neue Projektidentität **Hyperion Grabber NG**.
- Application-ID auf `com.elhanko.hyperiongrabber.ng` umgestellt.
- Vollständige Paketmigration auf die ElHanko-Namespaces.
- Reproduzierbares Docker-Buildsystem für Debug- und Release-Builds eingeführt.
- Build-Toolchain auf Gradle 9.5.0, Android Gradle Plugin 9.3.0 und JDK 17
  aktualisiert.
- Kompilierung auf `compileSdk 36` aktualisiert.
- Support Libraries vollständig zu AndroidX migriert.
- Butter Knife durch Android View Binding beziehungsweise direkte Android-APIs
  ersetzt.
- Lokale, wiederverwendbare PKCS#12-Release-Signierung eingerichtet.
- Versionen: Mobile `1100/2.1.0`, TV `2100/2.1.0`.

### Verified

- Reproduzierbare Debug- und signierte Release-Builds für Mobile und TV.
- Erfolgreicher Phase-1-Basistest auf Fire OS 8.

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
