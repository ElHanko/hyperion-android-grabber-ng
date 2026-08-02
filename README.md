# Hyperion Grabber NG

Hyperion Grabber NG erfasst den Bildschirminhalt eines Android-Geräts und
überträgt ihn an einen Hyperion-Server. Das Projekt stellt getrennte Apps für
Android-Mobilgeräte und Android TV beziehungsweise Fire TV bereit.

## Status und Herkunft des Forks

Das Projekt ist ein modernisierter Fork des ursprünglichen **Hyperion Android
Grabber** von Dave Anderson. Der Fork wird von Mathias (ElHanko) unter dem Namen
Hyperion Grabber NG gepflegt. Die ursprüngliche Arbeit und die Änderungen des
Forks stehen unter der MIT-Lizenz.

Version 2.1.0 schloss die erste Modernisierungsphase ab und wurde auf Fire OS 8
getestet. Version 2.1.1 synchronisiert das ProtoBuffer-Protokoll mit Hyperion NG
2.2.1 und härtet den TCP-Transport. Ihre Builds und automatisierten Tests sind
erfolgreich; der reale Update- und Hyperion-Test auf dem Zielgerät steht noch
aus. Deshalb wird 2.1.1 weiterhin als `Unreleased` geführt. Details stehen im
[Changelog](CHANGELOG.md).

## Funktionen

- Bildschirmübertragung zu Hyperion über den ProtoBuffer-TCP-Server
- RGB- und RGBA-Bildübertragung sowie optionaler Durchschnittsfarbmodus
- getrennte Oberflächen für Mobile, Android TV und Fire TV
- einstellbare Serveradresse, Priorität, Bildrate und Capture-Skalierung
- optionaler automatischer Verbindungsaufbau nach einem Transportabbruch
- Start beim Geräteboot und Schnellzugriffsmöglichkeiten der vorhandenen Apps
- vollständiges Request/Reply-Framing mit geprüfter Hyperion-Fehlerbehandlung

## Kompatibilität

| Eigenschaft | Stand |
| --- | --- |
| Application-ID | `com.elhanko.hyperiongrabber.ng` |
| Hyperion-Referenz | Hyperion NG 2.2.1 |
| Übertragungsprotokoll | ProtoBuffer über TCP |
| `minSdk` | 21 |
| `compileSdk` | 36 |
| `targetSdk` | vorerst 26 |
| Zielgerät | Fire TV Stick 4K Max, Modell AFTKRT |
| Fire OS | 8.1.8.0 |
| Android API des Zielgeräts | 30 |

Der Phase-1-Build 2.1.0 wurde auf dem genannten Fire-TV-Modell grundlegend
getestet. Für 2.1.1 sind Schema, TCP-Framing, Fehlerbehandlung, Reconnect nach
Abbruch und APK-Erzeugung automatisiert geprüft. Ein erfolgreicher realer
Fire-TV- oder Hyperion-NG-2.2.1-Lauf wird erst nach dem noch ausstehenden
manuellen Test dokumentiert.

Die genaue Protokollbasis und die Abweichungen des früheren Schemas beschreibt
die [Hyperion-NG-2.2.1-Kompatibilitätsdokumentation](docs/hyperion-ng-2.2.1-compatibility.md).

## Installation

Für ein bestehendes, mit demselben Release-Zertifikat installiertes TV-Paket
kann die Release-APK mit `-r` als Update installiert werden. Der Container
`firetv-adb` muss Zugriff auf das verbundene Gerät und auf `/workspace` haben:

```bash
docker exec firetv-adb \
  adb install -r /workspace/dist/tv/tv-release.apk
```

Die TV-Version erhöht sich dabei von `2100/2.1.0` auf `2101/2.1.1`. Vor der
Installation sollte die APK wie im Abschnitt „Docker-Build“ beschrieben selbst
gebaut werden. Eine bestehende Installation darf nicht deinstalliert werden,
wenn Anwendungsdaten und die Update-Kette erhalten bleiben sollen.

## Konfiguration

In den App-Einstellungen werden mindestens folgende Werte gesetzt:

- Hostname oder IP-Adresse des Hyperion-Servers
- ProtoBuffer-Port, standardmäßig `19445`
- Priorität im für ProtoBuffer vorgesehenen Bereich `100–199`, standardmäßig
  `150`
- Reconnect und Reconnect-Verzögerung, standardmäßig aktiviert und fünf
  Sekunden
- horizontale und vertikale LED-Anzahl für die Capture-Skalierung
- Bildrate und optionaler Durchschnittsfarbmodus
- optionaler Start beim Geräteboot

Der ProtoBuffer-Server muss in der verwendeten Hyperion-Instanz erreichbar
sein. Eine automatische neue Server-Discovery ist nicht Bestandteil dieser
Version.

## Verwendung auf Android TV und Fire TV

Nach Installation wird die TV-App über den Leanback-Launcher geöffnet. In der
Einrichtung werden Hyperion-Host, Port und Capture-Einstellungen festgelegt.
Danach kann die Bildschirmübertragung in der App gestartet und gestoppt werden.
Die Android-Systemabfrage zur Bildschirmfreigabe muss bestätigt werden, wenn
sie angezeigt wird.

Auf Fire TV gelten dieselben Einstellungen. Da der reale 2.1.1-Test noch
aussteht, sollten Update-Installation, Verbindungsaufbau, laufendes Capture,
Reconnect und korrektes Löschen der Hyperion-Priorität auf dem tatsächlichen
Gerät geprüft werden.

## Docker-Build

Voraussetzung ist eine funktionierende Docker-Installation. Der Builder enthält
die festgelegte JDK-/Android-Toolchain und verwendet einen persistenten
Gradle-Cache.

Debug-Build:

```bash
./docker/build.sh debug
```

Signierter Release-Build:

```bash
./docker/build.sh release
```

Die erzeugten APKs werden nach Modulen abgelegt:

```text
dist/mobile/
dist/tv/
```

Weitere Informationen zu Image, Cache und Build-Ablauf enthält die
[Docker-Dokumentation](docker/README.md).

## Release-Signierung

Release-Signierung bleibt ausschließlich lokal. Erwartet wird diese ignorierte
Verzeichnisstruktur im Projekt:

```text
signing/
├── hyperion-ng-release.p12
└── signing.properties
```

Beide App-Module verwenden dieselbe PKCS#12-Konfiguration. Das Buildskript
bindet die lokalen Dateien schreibgeschützt in den Builder ein und bricht ab,
wenn die Konfiguration fehlt oder unvollständig ist. Keystore, Passwörter und
andere geheime Werte dürfen nicht in das Repository aufgenommen oder in
Dokumentation und Build-Ausgaben veröffentlicht werden.

## Entwicklung und Tests

Die gemeinsame Android-Bibliothek enthält den ProtoBuffer-Transport und die
generierten Nachrichtenklassen. JVM-Tests starten lokale `ServerSocket`-Server;
Android-Hardware und ein externer Hyperion-Server sind dafür nicht erforderlich.
Sie prüfen unter anderem COLOR, RGB/RGBA-IMAGE, CLEAR, CLEARALL, Big-Endian-
Framing, fragmentierte Pakete, Timeouts, EOF, Serverfehler und Reconnect nach
einem Verbindungsabbruch.

Der vollständige Testtask lautet:

```text
:common:test
```

Er wird im selben Docker-Builder wie die App-Builds ausgeführt. Vor Änderungen
sollten außerdem die Angaben in der
[Kompatibilitätsdokumentation](docs/hyperion-ng-2.2.1-compatibility.md) beachtet
werden.

## Bekannte Einschränkungen

- ProtoBuffer wird derzeit verwendet und von Hyperion NG 2.2.1 noch
  unterstützt. Der Hyperion-Quellcode sieht langfristig eine Ablösung des
  Proto-Servers vor.
- FlatBuffer ist nicht Bestandteil dieser Phase und muss separat bewertet
  werden.
- Eine neue mDNS-/SSDP-Server-Discovery ist nicht Bestandteil dieser Phase.
- `targetSdk 26` ist bewusst noch temporär. Anpassungen an neuere
  MediaProjection- und Foreground-Service-Anforderungen folgen separat.
- Der reale 2.1.1-Update-, Fire-TV- und Hyperion-Server-Test ist noch offen.

## Projektgeschichte

Dave Anderson entwickelte den ursprünglichen Hyperion Android Grabber. Mathias
(ElHanko) führt ihn als Hyperion Grabber NG fort. Die erste
Modernisierungsphase aktualisierte Identität, Pakete, AndroidX, View Binding und
die reproduzierbare Build- und Signierumgebung. Die zweite Phase beginnt mit
der dokumentierten Hyperion-NG-2.2.1-ProtoBuffer-Kompatibilität und einem
gehärteten TCP-Transport. Frühere Veröffentlichungen bleiben im
[Changelog](CHANGELOG.md) erhalten.

## Lizenz

Die ursprüngliche Arbeit von Dave Anderson und der modernisierte Fork von
Mathias (ElHanko) stehen unter der MIT-Lizenz. Die vollständigen Hinweise stehen
in [LICENSE.txt](LICENSE.txt). Informationen zum Umgang mit Daten enthält die
[Datenschutzerklärung](privacy-policy.md).
