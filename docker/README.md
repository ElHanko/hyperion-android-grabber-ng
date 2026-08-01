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

Für Release-Builds müssen die ausschließlich lokalen, durch `.gitignore`
geschützten Dateien vorhanden sein:

```text
signing/hyperion-ng-release.p12
signing/signing.properties
```

Das Buildskript bindet dieses Verzeichnis read-only als `/signing` in den
Builder ein. Beide App-Module laden dieselbe PKCS#12-Konfiguration aus
`/signing/signing.properties`. Fehlt eine der Dateien oder ist die
Konfiguration unvollständig, bricht der Release-Build ab, bevor APKs in
`dist/` übernommen werden. Schlüsseldateien und Passwörter werden weder in
das Docker-Image noch in das Repository kopiert.

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
