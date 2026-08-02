# Hyperion Android Grabber NG roadmap

## Project identity and scope

The project name is **Hyperion Android Grabber NG**.

**NG** stands for **Next Generation**. It describes a new technical generation
of the existing focused application, not a broader feature set or a change in
product purpose.

The application continues to have three closely related responsibilities:

- capture the Android device screen;
- transmit the captured image data reliably to Hyperion;
- provide only the configuration, permissions, and status information required
  for that purpose.

Hyperion Android Grabber NG is not intended to become a general Hyperion client,
dashboard, administration tool, or management interface. Modernization changes
how the application is built and maintained, not what the application is.

## Modernization approach

The project is a comprehensive technical renovation of an older application
whose original idea remains useful. It is comparable to renovating an old house:
the sound foundation, working core, and original purpose are preserved, while
obsolete, broken, or no longer maintainable surrounding components are removed.
Their replacements must form a modern and internally consistent technical
structure.

Working components are not replaced without a clear technical reason. At the
same time, obsolete code is not retained merely to keep an outdated architecture
running under limited conditions, and new compatibility workarounds should not
be layered indefinitely over old ones.

This is neither a complete product rewrite nor a collection of isolated repairs.
The result remains the same focused application, rebuilt to current technical
standards: preserve the core, remove the obsolete, and modernize the surrounding
structure as one coherent system.

## Status legend

| Status | Meaning |
| --- | --- |
| Completed | Implemented, validated to the documented extent, and released. |
| In progress | Work is underway, but the phase has not met all completion and validation criteria. |
| Planned | Defined future work that is not available as a completed feature. |

## Phase overview

| Phase   | Scope                                                      | Status      | Release       |
| ------- | ---------------------------------------------------------- | ----------- | ------------- |
| Phase 1 | Project foundation, package migration and reusable signing | Completed   | 2.1.0         |
| Phase 2 | Hyperion NG 2.2.1 Protocol Buffers compatibility           | Completed   | 2.1.1         |
| Phase 3 | Modern discovery and experimental FlatBuffer transport     | In progress | 2.2.0 planned |
| Phase 4 | Android platform and lifecycle modernization               | Planned     | Not assigned  |
| Phase 5 | UI modernization and localization                          | Planned     | Not assigned  |

## Phase 1 – Foundation and signed package migration

**Status:** Completed  
**Release:** 2.1.0

Phase 1 established the independent technical foundation and a stable update
path for the modernized project:

- migrated to application ID `com.elhanko.hyperiongrabber.ng`;
- separated the application from the original
  `com.abrenoch.hyperiongrabber` installation;
- introduced reusable local release signing;
- produced signed Mobile and TV release APKs;
- established a stable signing certificate for future updates;
- completed the initial Fire OS 8 validation;
- assigned TV versionCode `2100`;
- assigned Mobile versionCode `1100`;
- recorded the validated state with tag `phase-1-final-fireos8`.

The phase created a maintainable project base without breaking the identity or
update chain of the new package.

## Phase 2 – Hyperion NG 2.2.1 compatibility

**Status:** Completed  
**Release:** 2.1.1

Phase 2 aligned and validated the existing production transport:

- aligned the Protocol Buffers schema exactly with Hyperion NG 2.2.1;
- hardened framed TCP request and reply transport;
- made timeout, EOF, framing, and protocol-error handling deterministic;
- replaced finalization with explicit and idempotent close behavior;
- added request, priority, image-dimension, and image-data validation;
- added offline JVM transport tests;
- added an optional real-server integration test;
- connected successfully to a real Hyperion NG 2.2.1 server;
- validated Fire TV screen capture and continuous LED output;
- validated reconnect after an actual Hyperion server interruption;
- confirmed automatic continuation after the server became available again;
- confirmed that intentionally stopping the grabber causes no unwanted
  reconnect;
- completed a signed TV update from versionCode `2100` to `2101`;
- assigned Mobile versionCode `1101`;
- published the validated release with tag `v2.1.1`.

Phase 2 is completed, tested, and published. Protocol Buffers remains the stable
production transport established by this phase.

## Phase 3 – Discovery and experimental FlatBuffer transport

**Status:** In progress  
**Planned release:** 2.2.0

Phase 3 contains two separate technical workstreams. Discovery improves how the
existing Protocol Buffers endpoint is configured. Experimental FlatBuffer work
will evaluate an alternative transport without changing the application's
purpose or the stable default.

### Phase 3A – Modern Hyperion discovery

**Status:** Completed

Phase 3A replaces an obsolete network-search implementation while preserving the
same connection setup purpose. Its scope is to:

- replace the IPv4 `/24` subnet scanner with Android NSD and mDNS;
- discover the Hyperion Protocol Buffers service through
  `_hyperiond-protobuf._tcp.`;
- use the actual SRV port published by Hyperion;
- support multiple discovered Hyperion servers;
- deduplicate services with the Hyperion TXT `id` when available;
- retain IPv4 and IPv6 information;
- require explicit user selection before changing the configured endpoint;
- preserve complete manual host and port configuration;
- keep discovery optional rather than mandatory;
- stop discovery when the user leaves the visible setup flow;
- support D-pad interaction on Android TV and Fire OS.

The production connection continues to use Protocol Buffers. Discovery does not
enable FlatBuffer, does not select a transport, and does not expand the product
beyond screen capture and Hyperion connectivity. Manual host and port entry
remains a complete fallback when mDNS is unavailable or unsuitable.

The discovery flow was validated on Fire OS 8 / Android API 30 against a real
Hyperion NG 2.2.1 ProtoServer. The validation confirmed service discovery,
resolved-port selection, preference transfer, Protocol Buffers connection,
screen capture, LED output, retry, cancellation, lifecycle cleanup, and D-pad
operation. FlatBuffer was neither enabled nor tested as part of Phase 3A.

The implementation architecture, original scanner assessment, automated
validation, and real Fire TV validation are documented in the
[discovery audit](discovery-audit.md).

### Phase 3B – Experimental FlatBuffer transport

**Status:** In progress

| FlatBuffer stage | Status |
| --- | --- |
| Stage 1 – Reproducible schema generation | Completed |
| Stage 2 – Isolated socket client | Completed |
| Stage 3 – Common transport boundary | Completed |
| Stage 4 – Production lifecycle integration | Completed |
| Stage 5 – Experimental settings UI | Completed |
| Stage 6 – Optional real-server integration | Planned |
| Stage 7 – Real Fire TV validation | Planned |
| Stage 8 – Phase 3 release completion | Planned |

Stages 1 through 5 are complete. The two official Hyperion NG 2.2.1 schemas, pinned
FlatBuffers 25.9.23 toolchain and runtime, reproducible Java generation, and
schema-level offline tests are integrated into the build. An isolated socket
client now implements the official TCP framing, registration, Color, RGB24 and
RGB32 RawImage, own-priority Clear, bounded reply parsing, and deterministic
error handling. Its 58 loopback fake-server JVM tests pass.

A minimal common interface, immutable configuration, stable `protobuf` and
`flatbuffer` type values, thin adapters around both existing clients, and a
stateless single-selection factory are also implemented. Forty-three offline
adapter and factory tests verify exact delegation, the Protocol Buffers default,
preserved failures, independent transports, and no fallback.

The production capture path now uses that common boundary and factory. Internal
preferences retain `protobuf` as the safe default, keep separate Protocol Buffers
and FlatBuffer ports, and permit only one snapshotted transport per session. The
single reconnect loop retries only that same selection, closes a failed transport
before replacement, drops frames while disconnected, and is stopped explicitly
by intentional shutdown. Thirty-eight additional offline tests cover preference,
lifecycle, reconnect, delegation, status, and no-fallback guarantees.

FlatBuffer is now an explicit experimental opt-in in both Mobile and TV settings.
The visible control stores only the existing string selection and retains separate
ports; it does not live-swap a running capture session. No FlatBuffer discovery,
real-server test, or hardware validation has been implemented. Protocol Buffers
remains the stable production default. Stage 5 is complete; Stages 6 through 8
remain planned.

Protocol Buffers remains the stable default transport, including for existing
installations. FlatBuffer is an experimental opt-in transport:

- an explicit GUI checkbox enables the experiment;
- the checkbox is disabled by default for new installations and updates;
- no installation will be migrated automatically to FlatBuffer;
- there will be no silent fallback to Protocol Buffers after a FlatBuffer
  failure;
- transport errors will identify the transport selected by the user;
- disabling the option will restore the existing Protocol Buffers path;
- exactly one transport will be active at a time;
- intentionally stopping the grabber will stop reconnect attempts for either
  transport.

The implemented internal boundary is intentionally small:

```text
Hyperion transport abstraction
- stable Protocol Buffers implementation
- experimental FlatBuffer implementation
```

The completed Stage 1 through Stage 5 validation covers generated code from the
official Hyperion NG FlatBuffer schema, the isolated framed socket client, both
transport adapters, the factory-driven production lifecycle, and the settings and
status presentation contracts. Later planned validation includes:

- an optional real-server integration test;
- real Fire TV validation;
- verification that Protocol Buffers remains the unchanged default.

FlatBuffer is a possible modern technical transport path, not an expansion of
the application's purpose. Completing Stages 1 through 5 makes FlatBuffer a
visible experimental opt-in, not a stable or hardware-validated transport.

## Phase 4 – Android platform and lifecycle modernization

**Status:** Planned  
**Release:** Not assigned

Phase 4 will modernize the existing capture and service lifecycle independently
of transport development. The Android and distribution-platform requirements
current at implementation time will determine the exact changes. The scope is
planned to include:

- controlled updates of compile and target SDK levels;
- review of current Android and distribution-platform requirements;
- modernization of MediaProjection handling;
- modernization of Foreground Service declarations and startup order;
- required service types and permissions;
- notification channels and service notifications;
- background-start restrictions;
- boot entry points;
- Quick Settings entry points;
- deliberate-stop behavior;
- validation on Fire OS and regular Android where possible.

No future API level is committed in advance. This phase will not introduce
additional background features. Obsolete lifecycle workarounds should be removed
and replaced with one consistent lifecycle rather than surrounded with further
workarounds.

## Phase 5 – UI modernization and localization

**Status:** Planned  
**Release:** Not assigned

### UI modernization

The UI work will present the existing functionality more clearly, using the
Android and Android TV guidance current at implementation time. Its planned scope
includes:

- modernized setup, settings, status, and error surfaces;
- suitable layouts and interaction models for Mobile and TV;
- reliable D-pad and focus navigation;
- a consistent visual hierarchy;
- clear discovery, connection, capture, and transport status;
- accessible contrast and scalable text;
- content descriptions and other accessibility improvements.

No unrelated dashboard or management functionality is planned. Stable transport
and capture layers should not be functionally changed merely to support visual
modernization.

### Localization

All user-visible text will move to Android string resources. English will remain
the default and fallback language, with German as the first additional
translation. By default, the application will follow the device or system
language, and unsupported languages will fall back to English.

An optional application-language setting is planned with these choices:

- System default
- English
- Deutsch

A manual selection will persist as an application-language override. Localization
will cover:

- UI labels and settings;
- dialogs and errors;
- notifications and foreground-service text;
- discovery messages;
- connection and capture status;
- transport warnings;
- onboarding;
- accessibility descriptions.

Protocol names, discovered server names, and developer-only technical log
messages will not be translated. The exact implementation of the manual language
selector may depend on the Android compatibility levels reached in Phase 4.
Localization improves accessibility and consistency; it does not expand the
product scope.

## Project principles

- Keep the application small and focused.
- Preserve the original purpose of capturing the Android screen for Hyperion.
- Interpret **NG** as a new technical generation, not a broader feature set.
- Preserve the working core and original idea.
- Remove obsolete, broken, and unmaintainable components.
- Do not retain obsolete code merely because it still works under limited
  conditions.
- Do not accumulate compatibility workarounds around outdated architecture.
- Replace outdated surrounding structures with modern, consistent equivalents.
- Preserve functioning components unless there is a clear technical reason to
  replace them.
- Maintain consistency across architecture, network transport, lifecycle, UI,
  tests, and documentation.
- Preserve a known-working upgrade path.
- Keep stable functionality as the default.
- Introduce risky protocol changes only as explicit opt-in features.
- Keep manual configuration available unless an equivalent reliable replacement
  exists.
- Do not add features unrelated to screen capture and Hyperion connectivity.
- Validate significant network and lifecycle changes with automated tests and
  real Fire TV testing.
- Document completed work separately from planned work.
- Never claim hardware validation before it has actually been performed.
- Favor maintainable and understandable solutions over unnecessary abstraction.
- Avoid both feature bloat and endless compatibility patching.

Hyperion Android Grabber NG should remain a small and focused application. Its
modernization preserves the working core and original purpose, removes obsolete
surrounding components, and rebuilds the technical structure to current
standards. The result should be the same application in a modern, consistent,
and maintainable form—not a larger or fundamentally different product.
