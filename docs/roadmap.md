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

| Phase   | Scope                                                       | Status    | Planned release |
| ------- | ----------------------------------------------------------- | --------- | --------------- |
| Phase 1 | Project foundation, package migration and reusable signing | Completed | 2.1.0           |
| Phase 2 | Hyperion NG 2.2.1 Protocol Buffers compatibility            | Completed | 2.1.1           |
| Phase 3 | Modern discovery and experimental FlatBuffer transport      | Completed | 2.2.0           |
| Phase 4 | Android lifecycle and current capture-pipeline optimization | Planned   | 2.3.0           |
| Phase 5 | UI modernization and localization                           | Planned   | 2.4.0           |
| Phase 6 | Capture-engine analysis, audit and evaluation                | Planned   | No release      |
| Phase 7 | Implementation of the Phase 6 architecture decision         | Planned   | 2.5.0 or 3.0.0  |

## Version philosophy

The 2.x series represents the renovation and modernization of the existing
application and capture architecture. It preserves the original focused
purpose, preserves working foundations where technically justified, replaces
obsolete surrounding components, improves the existing capture pipeline, avoids
feature bloat, and remains recognizably the renovated original application.

The version boundary after the research phase is deliberate:

- `2.5.0` is possible when Phase 6 concludes that a major capture-engine
  conversion can still be implemented within the renovated existing architecture.
- `3.0.0` is possible when Phase 6 concludes that the technically appropriate
  solution requires a fundamentally new capture engine.

In house terms, 2.x is the complete renovation and modernization of the old
house. Version 2.5.0 is a major conversion while the existing house remains the
structural basis. Version 3.0.0 is the transition to a modern new house whose
design is based on current requirements rather than historical construction.

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

**Status:** Completed

**Release:** 2.2.0

Phase 3 delivered two separate technical workstreams. Discovery improves how the
existing Protocol Buffers endpoint is configured. Experimental FlatBuffer adds
an alternative opt-in transport without changing the application's purpose or
the stable default.

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

**Status:** Completed

| FlatBuffer stage | Status |
| --- | --- |
| Stage 1 – Reproducible schema generation | Completed |
| Stage 2 – Isolated socket client | Completed |
| Stage 3 – Common transport boundary | Completed |
| Stage 4 – Production lifecycle integration | Completed |
| Stage 5 – Experimental settings UI | Completed |
| Stage 6 – Optional real-server integration | Completed |
| Stage 7 – Real Fire TV validation | Completed |
| Stage 8 – Phase 3 release completion | Completed |

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
ports; it does not live-swap a running capture session. No FlatBuffer discovery
has been completed. The independently opt-in Stage 6 JVM test successfully
validated a real Hyperion NG 2.2.1 FlatBuffer server on August 3, 2026, using
port `19400` and priority `190`. It confirmed the factory-driven FlatBuffer-only
path, registration, Color, RGB24, RGB32, own-priority Clear, re-registration,
cleanup, and orderly close without a Protocol Buffers fallback.

Stage 7 then validated the signed TV release APK on an Amazon Fire TV device
reported by ADB as `AFTKRT` / `karat`. The in-place update path, retained app
data, Protocol Buffers reference use case, D-pad settings flow, separate
FlatBuffer port, confirmation dialog, FlatBuffer capture, Stop/Clear, restart,
and functional reconnect were all observed successfully. The hardware validation
is limited to this primary Android TV / Fire TV use case and does not claim
Mobile hardware or all Fire TV models. Protocol Buffers remains the stable
production default. Stages 1 through 8 are complete.

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
status presentation contracts. Stage 6 implementation additionally provides a
factory-driven, opt-in FlatBuffer server test for Register, Color, RGB24, RGB32,
own-priority Clear, and re-registration. It is skipped unless explicitly enabled
and has no Protocol Buffers fallback. Its real Hyperion NG 2.2.1 validation
completed successfully on August 3, 2026. Stage 7 completed the real Fire TV
functional validation of the signed TV release while retaining Protocol Buffers
as the stable default.

FlatBuffer is a possible modern technical transport path, not an expansion of
the application's purpose. Completing Stages 1 through 8 makes FlatBuffer a
real-server- and Fire-TV-validated experimental opt-in, not a generally stable
or universally hardware-validated transport.

Stage 8 completed version `2.2.0`, TV versionCode `2200`, and Mobile versionCode
`1200`. The final offline matrix passed with 221 Common tests and two expected
opt-in skips, one Mobile test, and four TV tests. Debug and signed Release builds,
APK metadata, v1/v2 signatures, and the established release certificate were
verified.

The exact final TV artifact passed the signed in-place update from
`2101 / 2.1.1` to `2200 / 2.2.0`. Installation and application data remained
available, including the original `firstInstallTime` and the existing endpoint,
priority, reconnect, and capture settings. Short Protocol Buffers and FlatBuffer
capture regressions, Stop/Clear for both paths, and an application force-stop and
restart also passed. The stored FlatBuffer selection and separate port remained
available after restart. Mobile was built, signed, and automatically tested but
was not hardware-validated.

Phase 3 and Phase 3B are complete. Version `2.2.0` was published and tagged as
`v2.2.0`.

## Phase 4 – Android lifecycle and current capture-pipeline optimization

**Status:** Planned  
**Planned release:** 2.3.0

Phase 4 remains based on the current application and capture engine. It has two
coordinated workstreams. The target release is planned as `2.3.0`, but it remains
unreleased and its exact implementation has not yet started.

### Phase 4A – Android platform and lifecycle modernization

The Android, Fire OS, and distribution-platform requirements current at
implementation time will determine the exact changes. The planned scope
includes:

- controlled compile SDK and target SDK updates;
- review of Android, Fire OS, and distribution-platform requirements current at
  implementation time;
- MediaProjection lifecycle modernization;
- Foreground Service permissions, service types, declarations, and startup order;
- notification channels and foreground-service notifications;
- background-start restrictions;
- boot entry points;
- Quick Settings entry points;
- exported component rules;
- deliberate-stop behavior;
- deterministic resource cleanup;
- validation on Fire OS and regular Android where possible;
- explicit review of the historical upstream `release/1.1-beta_1` branch as
  recorded in GitHub issue `#1`.

Historical beta changes are evidence to review. They are not patches to copy
blindly into the current application.

No future API level is committed in advance. This phase will not introduce
additional background features. Obsolete lifecycle workarounds should be removed
and replaced with one consistent lifecycle rather than surrounded with further
workarounds.

### Phase 4B – Existing capture-pipeline optimization

The second workstream optimizes the current capture engine before any decision
about a fundamental replacement. Its planned scope includes:

- establishing measurable baseline behavior before optimization;
- decoupling screen capture from network transmission;
- using a latest-frame strategy with no unbounded queue of stale frames;
- retaining at most one pending current frame;
- reducing per-frame allocations and garbage-collection pressure;
- evaluating safe reusable frame buffers;
- improving capture-resolution and scaling selection;
- benchmarking RGB24 and RGB32 paths rather than assuming either is superior;
- measuring CPU usage, memory behavior, frame-processing time, dropped frames,
  network volume, and end-to-end response;
- preserving transport-neutral operation for Protocol Buffers and FlatBuffer;
- preserving intentional Stop, own-priority Clear, reconnect, orientation
  handling, and update behavior;
- considering black-bar handling, average-color processing, and adaptive frame
  rates only after the core pipeline improvements are measured.

Phase 4 optimizes the existing engine. It is not the phase for a fundamental
capture-engine rewrite.

## Phase 5 – UI modernization and localization

**Status:** Planned  
**Planned release:** 2.4.0

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

## Phase 6 – Capture-engine analysis, audit and evaluation

**Status:** Planned<br>
**Release:** No release

Phase 6 is a research and decision phase, not a production implementation
phase. Its purpose is to determine whether the future capture engine should
remain a major conversion of the current architecture or become a fundamentally
new implementation.

### Fixed rules

Phase 6 follows these rules:

- no application version change;
- no release;
- no production feature delivery;
- no changes to production capture code on `main`;
- no predetermined decision between `2.5.0` and `3.0.0`;
- no automatic merging of prototypes;
- no assumption that the historical grabber architecture remains the correct
  foundation;
- no redesign of the established Hyperion transports unless the evaluation
  proves a necessary interface change.

### Evaluation perspective

The investigation must begin from:

1. what Hyperion actually needs from an Android screen grabber;
2. what current Android and Fire OS APIs can efficiently provide;
3. the simplest maintainable architecture connecting those requirements.

The old capture implementation may be used as behavioral and historical
reference, but it must not define the new design.

### Required analysis

The evaluation must include:

- Hyperion input requirements;
- useful image resolution and frame-rate ranges;
- supported and useful pixel formats;
- latency and timeout behavior;
- full-frame versus edge-oriented or other preprocessing strategies;
- color-space, HDR, scaling, and black-bar considerations;
- Android MediaProjection, VirtualDisplay, Surface, ImageReader,
  HardwareBuffer, CPU, and GPU capabilities current at evaluation time;
- copy-count and memory-layout analysis;
- an audit of the optimized Phase 4 engine;
- identification of structural limits versus fixable implementation weaknesses;
- benchmark methodology;
- Fire TV as the primary hardware target;
- regular Android validation where hardware is available;
- honest documentation of unperformed hardware coverage.

### Prototype branches

Phase 6 may create isolated evaluation branches for focused prototypes, such as:

- `evaluation/capture-surface-pipeline`;
- `evaluation/rgb32-pipeline`;
- `evaluation/gpu-scaling`;
- `evaluation/edge-sampling`;
- `evaluation/latest-frame-alternative`.

Prototypes are measurement and learning tools. They may be incomplete, are not
release candidates, must not be merged automatically, and may be discarded
entirely. They do not change the application version and do not make Phase 6 a
release phase.

Each evaluated approach receives one documented outcome:

- `Pursue`;
- `Reference only`;
- `Reject`;
- `Inconclusive`.

### Required outputs

Phase 6 should produce:

- a Hyperion requirements analysis;
- an Android and Fire OS capture-capabilities audit;
- an audit of the Phase 4 capture engine;
- reproducible benchmarks;
- prototype findings where appropriate;
- an architecture comparison;
- an Architecture Decision Record;
- a concrete implementation recommendation for Phase 7.

Phase 6 has no release target.

## Phase 7 – Implementation of the architecture decision

**Status:** Planned<br>
**Planned release:** 2.5.0 or 3.0.0

Phase 7 is the first phase allowed to implement the fundamental architecture
decision made in Phase 6. It must not be described as already committed to
either outcome.

### Outcome A – Major conversion within the existing architecture

When Phase 6 determines that the existing modernized architecture remains a
suitable foundation, Phase 7 will:

- implement the selected capture-engine conversion;
- retain the renovated 2.x structural foundation;
- preserve compatible application behavior and settings where sensible;
- target version `2.5.0`.

This is a major conversion of the renovated old house rather than a new
building.

### Outcome B – Fundamentally new capture engine

When Phase 6 determines that the existing architecture prevents the most
appropriate solution, Phase 7 will:

- design and implement a new capture engine;
- derive the architecture from Hyperion requirements and current Android
  capabilities;
- use the old grabber only as behavioral reference;
- reuse transports, discovery, UI, settings, signing, or other components only
  where they remain technically suitable;
- define migration and compatibility behavior explicitly;
- target version `3.0.0`.

Version `3.0.0` marks the new-house boundary: the application purpose remains
focused and recognizable, but the capture foundation is newly designed.

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
- Optimize the current capture engine before deciding to replace it.
- Let measured requirements and platform capabilities guide a future engine.
- Do not treat historical implementation choices as permanent requirements.
- Isolate experimental prototypes from production branches.
- Do not assign a release version to research-only work.
- Use major version `3.0.0` only for a genuinely fundamental new capture
  foundation.

Hyperion Android Grabber NG should remain a small and focused application. Its
modernization preserves the working core and original purpose, removes obsolete
surrounding components, and rebuilds the technical structure to current
standards. The result should be the same application in a modern, consistent,
and maintainable form—not a larger or fundamentally different product.
