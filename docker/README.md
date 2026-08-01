# Docker-Build

Der Builder verwendet ein fest definiertes Android-SDK und ein persistentes Docker-Volume für den Gradle-Cache. Der Quellcode wird nicht in das Image kopiert, sondern beim Build schreibbar nach `/workspace` eingebunden.

## Voraussetzungen

- Docker
- ausführbarer Gradle Wrapper im Repository (`gradlew`)
- auf JDK 17 modernisierte Gradle-/Android-Gradle-Plugin-Konfiguration

## Debug-APKs bauen

```bash
./docker/build.sh
```

oder ausdrücklich:

```bash
./docker/build.sh debug
```

## Release-APKs bauen

```bash
./docker/build.sh release
```

Eine Release-Signierung muss im Android-Projekt konfiguriert sein. Schlüsseldateien und Passwörter gehören nicht in das Docker-Image oder Repository.

## Ausgaben

Die erzeugten APKs werden gesammelt unter folgenden Verzeichnissen abgelegt:

```text
dist/mobile/
dist/tv/
```

## Gradle-Cache

Standardmäßig wird das benannte Volume `hyperion-android-grabber-ng-gradle-cache` verwendet. Dadurch bleiben Gradle Wrapper, Abhängigkeiten und Build-Cache zwischen Builds erhalten.

Cache löschen:

```bash
docker volume rm hyperion-android-grabber-ng-gradle-cache
```

Andere Namen können über Umgebungsvariablen gesetzt werden:

```bash
IMAGE_NAME=my-android-builder CACHE_VOLUME=my-gradle-cache ./docker/build.sh debug
```
