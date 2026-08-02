# Privacy Policy

Effective date: August 2, 2026

## Overview

Hyperion Android Grabber NG captures the screen of an Android or Fire OS device,
processes the captured frames, and sends the resulting image data directly to a
Hyperion server selected or configured by the device user.

The app does not route captured screen content through a server operated by the
application maintainer. This policy describes the behavior of the current
open-source app. It does not describe independent processing performed by the
user's Hyperion server, operating system, device manufacturer, app store, network
operator, or other installed software.

## Data processed by the app

### Screen content

Screen capture begins only after the user grants Android's MediaProjection
screen-capture permission. While the grabber is running, captured frames are held
and processed only as needed to prepare them for network transmission to the
configured Hyperion server.

The app does not intentionally save screenshots or screen recordings as files.
The application maintainer does not receive, store, or inspect the captured
screen content through the app.

The Hyperion server is selected or configured by the device user and is outside
the maintainer's control. Its operator is responsible for its configuration,
access protection, network environment, and any processing or storage it may
perform.

### Local settings

The app stores configuration values locally in Android application preferences.
These may include:

- the Hyperion host name or address;
- the Protocol Buffers port;
- the input priority;
- the capture frame rate;
- horizontal and vertical capture dimensions or LED counts;
- reconnect behavior and delay;
- the average-color option;
- boot or startup preferences; and
- other configuration values required to operate the app.

The app does not transmit these preferences to the maintainer. They remain on the
device until the user changes them, clears the app's data through Android
settings, or uninstalls the app.

### Local network discovery

The app can optionally use Android NSD/mDNS to find compatible Hyperion Protocol
Buffers services on the local network. During discovery it may process:

- the discovered service name;
- a host name or IP address;
- the advertised port;
- the Hyperion identifier from the service's TXT record, if available; and
- the Hyperion version from the service's TXT record, if available.

Discovery results are used to display available servers. The app does not select
a server automatically. Only after the user explicitly selects a result does the
app save that server's resolved host and port in local application preferences.
Discovery stays within the local network, and the app does not upload discovery
results to the maintainer. Manual host and port configuration remains available,
so discovery is optional.

### Technical logs

The app may write technical status and error messages to the Android system log.
Such messages can include technical connection information. The app does not
itself upload system logs to the maintainer. Access to and retention of system
logs are controlled by the operating system and device environment.

## Network transmission and security

Captured screen data is transmitted directly from the device to the
user-selected Hyperion server. The current Hyperion network transport is not
encrypted by the app, and the app does not provide end-to-end encryption for
this connection.

Users should operate the app and Hyperion server on a trusted local network. If
traffic must cross an untrusted network, users should provide an appropriate
secure network layer, such as a trusted VPN or encrypted tunnel.

## Data collection by the maintainer

The current app does not include:

- user accounts;
- advertising;
- analytics or behavioral tracking;
- built-in crash reporting;
- telemetry sent to the maintainer;
- cookies;
- cloud synchronization; or
- a backend service operated by the developer or maintainer.

The maintainer does not receive screen content, local preferences, discovery
results, usage statistics, or system logs through the app.

## Third-party libraries and platform services

The app uses open-source software libraries required for its user interface,
screen capture, local discovery, and Hyperion communication. These libraries are
not integrated into the app for advertising, analytics, behavioral tracking, or
telemetry.

The app does not depend on Google Play Services. Android or Fire OS, the device
manufacturer, an app store, the network operator, or user-installed software may
independently process technical data under their own privacy policies. Such
platform or third-party processing is outside the application maintainer's
control and is not data collection performed by this app.

## Data retention and deletion

Screen data is held only as needed for processing and transmission and is not
intentionally retained by the app as screenshot or recording files. Local
configuration remains in Android application preferences until it is changed,
the app's data is cleared, or the app is uninstalled.

Depending on device and operating-system settings, Android or Fire OS may
independently back up or restore local application data as a platform function.

The app does not control retention performed independently by a user-configured
Hyperion server, the operating system, or other software.

## User control

The user controls whether Android screen-capture permission is granted. Stopping
the grabber ends the active screen capture and transmission. Server settings can
be changed manually, and local app data can be removed through Android
application settings or by uninstalling the app.

The app does not create or maintain user accounts.

## Children's privacy

The app is not specifically directed at children. The maintainer does not
knowingly collect personal data through the app. Captured screen content is sent
only to the Hyperion server configured or selected by the device user.

## Changes to this policy

This policy may be updated when the app's behavior changes or when clarification
is needed. Changes are published in this repository with an updated effective
date when appropriate.

## Contact

Questions about this policy can be raised through the
[Hyperion Android Grabber NG repository](https://github.com/ElHanko/hyperion-android-grabber-ng)
or the maintainer's available contact options on the
[ElHanko GitHub profile](https://github.com/ElHanko).

Do not include sensitive, confidential, or private information in a public GitHub
issue.
