# Hyperion Android Grabber NG roadmap

## Project identity and scope

The project name is **Hyperion Android Grabber NG**.

**NG** stands for **Next Generation**. The application remains a focused screen
grabber for Hyperion, not a general client, dashboard, or administration tool.

Amazon Fire TV / Fire OS is the sole forward-looking development target.
Android provides the underlying operating system and API framework; it is not
an independent generic target. Smartphones, tablets, generic Android TV
compatibility, and cross-platform capture frameworks are outside the scope.

The application has three responsibilities:

- capture the Fire TV screen;
- transmit the captured image data reliably to Hyperion;
- provide only the configuration, permissions, and status information required
  for that purpose.

The existing Mobile module remains in the repository as historical code. It is
not an active development or future support target. Historical Mobile artifacts
and validation records remain valid records of completed work.

The documented hardware reference is Fire TV `AFTKRT`, Fire OS 8, Android API 30.
Other Fire TV devices must not be described as supported without actual
validation. This reference does not establish generic Android TV compatibility.

## Modernization approach

Apply KISS and YAGNI: fix confirmed correctness problems first, then measure and
make only improvements with a concrete expected benefit. Working inherited code
may remain; age alone does not justify replacement. Use the smallest coherent
change that meets an actual Fire TV requirement.

The independent architecture audit informs the work packages below. Completing
them does not require a new capture engine or a comprehensive modernization of
every component.

## Status legend

| Status | Meaning |
| --- | --- |
| Completed | Scope completed and validated to the documented extent; release status is recorded separately. |
| Implemented | Code is merged into `main`; validation status is recorded for each work package. |
| In progress | Work is underway, but the work package has not met its completion and validation criteria. |
| Planned | Future work; implementation and validation are not complete. |
| Conditional | Work proceeds only if measurements justify it. |

## Completed phases

| Phase | Scope | Status | Released version |
| --- | --- | --- | --- |
| Phase 1 | Project foundation, package migration and reusable signing | Completed | 2.1.0 |
| Phase 2 | Hyperion NG 2.2.1 Protocol Buffers compatibility | Completed | 2.1.1 |
| Phase 3 | Discovery and experimental FlatBuffer transport | Completed | 2.2.0 |

The Phase 1 through Phase 3 sections below preserve the historical scope,
including Mobile artifacts and Android TV terminology. They do not extend the
future development target beyond Fire TV.

## Current work overview

| Work | Status | Goal |
| --- | --- | --- |
| Work Package A | Completed | Fire TV reliability and lifecycle correctness validated for 2.3.0 |
| Work Package B | Completed | Existing capture pipeline retained; RGB copy removed and validated on Fire TV |
| Optional architecture decision and larger engine changes | Conditional | Address a measured requirement that targeted changes cannot reasonably meet |

These work packages replace the former planned Phases 4 through 7. Work Package A
and the targeted RGB simplification from Work Package B are merged into `main`
and included in the validated TV version `2.3.0`. Both work packages are complete.

## Version strategy

TV version `2.3.0` / versionCode `2300` is the latest validated TV version, with
release validation completed on October 27, 2026. The final APK build, signature
verification, and in-place update on Fire TV `AFTKRT` passed. Settings were
retained, and screen capture and LED color output worked after the update.
Earlier development tests used `2.2.0` / `2200` and are recorded separately in
the [2.3.0 changelog](../CHANGELOG.md#230---2026-10-27). Version `2.2.0` remains a
historical published release; the Mobile module remains at `2.2.0` / `1200`.

Later patch or minor versions depend on the changes actually delivered. There
are no fixed release dates. `3.0.0` is not a goal in itself and does not justify
an engine rewrite.

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

## Work Package A – Fire TV reliability and lifecycle

**Status:** Completed; validated for version `2.3.0`.

### Implementation and validation status

The lifecycle and Fire TV reliability changes are merged into `main`, including
exception-safe image release, idempotent capture cleanup, MediaProjection and
VirtualDisplay termination handling, guarded session startup, accurate session
and reconnect status, notification/error improvements, and private internal
components. TV now uses `minSdk 25` and `targetSdk 30`, with `compileSdk 36`,
application identity, and signing unchanged.

For commit `7462cf9`, the operator confirmed successful Common and TV JVM tasks,
instrumentation-test compilation, and a signed TV release build on October 27,
2026. The instrumentation tests were not executed. Development hardware checks
passed on `AFTKRT` / `karat`, Fire OS 8 / API 30: signed in-place update with
retained settings, Start/Stop/Clear/restart, Home/background capture, return
without another screen-sharing permission request, both transports, and
Protocol Buffers connection loss/reconnect and intentional Stop during a server
interruption. Fire OS `8.1.8.0` is the historical patch version and was not
rechecked during these runs.

The tested development build still used `2.2.0` / `2200`. The final `2.3.0` / `2300`
APK build, signature verification, and in-place update on `AFTKRT` subsequently
passed, with retained settings, working capture, and LED color output.
System-initiated MediaProjection termination is not included in the confirmed
hardware checks. See the [2.3.0 changelog](../CHANGELOG.md#230---2026-10-27) for
the recorded validation scope and limitations.

### Goal and scope

Operate the existing Fire TV application reliably with explicit session state
and resource ownership. Address the confirmed implementation findings and review
platform risks in the Fire OS environments and distribution channels actually
selected for support:

- guarantee exception-safe release of every acquired `Image`;
- handle a system-initiated MediaProjection stop through complete session cleanup;
- give capture resources a clear owner and close them reliably on normal stop,
  startup failure, and system termination of capture;
- define correct start, stop, restart, and process-recreation behavior;
- report actual connection and reconnect state accurately;
- register and unregister broadcasts according to the owning lifecycle;
- review Foreground Service startup order, service types, notifications, and
  permissions against applicable Fire OS requirements;
- review boot entry points and background-start restrictions on Fire OS;
- check whether exported components are actually needed;
- review the relevant historical `release/1.1-beta_1` changes recorded in
  [GitHub issue #1](https://github.com/ElHanko/hyperion-android-grabber-ng/issues/1).

Historical beta changes are evidence to evaluate, not patches to copy blindly.
Keep, replace, or discard them according to current Fire TV requirements and
observed behavior.

Further SDK increases are not automatic requirements. Justify future SDK changes
through the supported Fire TV platform and chosen distribution channels,
distinguishing current requirements from behavior that changes only with a
higher target SDK.

Fix functional UI and accessibility problems within the affected flows, including
status, errors, D-pad interaction, and accessible control labels. Use existing
string resources and preserve translations when updating visible text. No
separate comprehensive UI redesign or localization phase is planned.

### Acceptance criteria

- Every acquired image is released on success, frame discard, and processing
  failure; capture resources and callbacks have a defined lifetime.
- Repeated start, stop, and restart produce correct session state without stale
  resources. Process recreation has a defined outcome and does not assume that
  an old projection permission can be reused.
- System-initiated capture termination releases resources and updates the visible
  state correctly.
- Reconnect uses only the selected transport, reports its actual state, and stops
  after intentional shutdown.
- Stop/Clear releases only the grabber's own Hyperion priority when the server is
  reachable. An unreachable server does not prevent local cleanup.
- Changed lifecycle and transport behavior is covered by proportionate automated
  checks and actual Fire TV validation, with the device, Fire OS version, and
  untested cases recorded explicitly.
- The final `2.3.0` APK signature was verified, and its in-place update passed
  with retained settings, working capture, and LED color output.

## Work Package B – Measure and selectively improve capture

**Status:** Completed; validated for version `2.3.0`.

### Implemented scope and outcome

Commit `d2e33c3` replaces `ByteArrayOutputStream` in RGB and average-color
conversion with directly filled byte arrays, removing the extra output-array
copy. RGB values, dimensions, order, strides, border handling, and average-color
behavior remain unchanged; capture lifecycle and transports were not changed.

On October 27, 2026, the operator confirmed all seven targeted RGB JVM tests and
the signed TV release build passed. In-place installation on the same `AFTKRT`
Fire TV passed, with capture and LED color output continuing to work. This build
also still used `2.2.0` / `2200`. The final `2.3.0` / `2300` APK build, signature,
in-place update, retained settings, capture, and LED color output have since
passed release validation. No performance measurements were performed, so no
measured CPU, memory, frame-rate, or latency improvement is claimed.
Further pipeline changes are not planned because no concrete need was identified.

### Method for future work if a concrete need arises

For further performance work, evaluate the existing pipeline using real Fire TV
measurements and the lifecycle fixes already merged from Work Package A.
Correctness fixes do not depend on completing a performance study.

Use the smallest reproducible measurement set needed to answer a concrete
question. As relevant, record:

- effective capture resolution and actual frame rate;
- frame-processing and network-transmission times;
- CPU and memory effects, including relevant allocations;
- observable latency or image-quality problems.

Record the device, Fire OS version, capture configuration, and selected transport
so that comparisons describe the same conditions. Only then implement targeted
changes with a concrete expected benefit, such as removing unnecessary byte
copies, correcting problematic resolution selection, or fixing a demonstrated
resource bottleneck. Compare the result with the baseline and preserve capture
quality, lifecycle behavior, and transport selection.

An asynchronous frame sender, latest-frame queue, buffer pool, GPU scaling,
RGB32 conversion, zero-copy pipeline, adaptive frame rate, and a new capture
engine are not predetermined requirements. Any such change needs evidence that
it solves a relevant problem at proportionate cost.

### Completion outcome

The existing capture pipeline was reviewed, the unnecessary RGB array copy was
removed, and the result passed targeted JVM tests and Fire TV validation. No
further optimizations are planned without a concrete need. Work Package B and
the documented final `2.3.0` release validation are complete.

The outcome is:

**The existing capture pipeline is sufficient; no further changes are needed.**

## Optional – Architecture decision and larger engine changes

**Status:** Conditional

Consider a larger engine change only when real measurements show that a relevant
Fire TV requirement cannot reasonably be met with the existing architecture and
targeted improvements. Completing Work Packages A and B does not automatically
start a research phase or an engine rewrite.

Before deciding, document the unmet requirement, measured structural limitation,
technical benefits, simpler alternatives, implementation cost, and compatibility
risks. The following outcomes have equal standing:

1. Retain the existing engine.
2. Improve the existing engine selectively.
3. Carry out a technically justified larger conversion.

Make the decision before committing to a larger implementation. If a new design
is justified, derive it from actual Fire TV requirements and platform
capabilities rather than historical implementation choices. Reuse suitable
components, including the established transports, instead of redesigning them
without a demonstrated need. There is no mandatory prototype program or reserved
release number for this work.

## Project principles

- Develop for Fire TV / Fire OS only; retain Mobile as historical code.
- Apply KISS and YAGNI: keep complexity and feature scope minimal.
- Establish correctness before optimization; prefer measured improvements over
  speculative rewrites.
- Preserve working components unless a demonstrated requirement justifies change;
  code age alone is not a reason to replace them.
- Respect Fire OS platform behavior and justify SDK changes through actual support
  and distribution requirements.
- Preserve application ID `com.elhanko.hyperiongrabber.ng`, signing identity,
  settings, and the validated in-place upgrade path.
- Keep Protocol Buffers the stable default and FlatBuffer an explicit experimental
  opt-in unless separately qualified; do not introduce silent transport fallback.
- Maintain explicit Stop/Clear of the grabber's own priority and controlled
  reconnect that ends on intentional stop.
- Preserve manual endpoint configuration and keep discovery optional.
- Validate release-relevant behavior on Fire TV; document the tested environment
  and never claim hardware coverage that was not performed.
