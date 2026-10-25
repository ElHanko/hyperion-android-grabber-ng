# Repository agent instructions

## Project scope

- Amazon Fire TV / Fire OS is the sole forward-looking development target.
  Android is the underlying API framework, not a generic support target.
- The Mobile module is historical and remains in the repository; do not actively
  develop it or remove it without separate approval.
- Do not broaden device support or introduce generic Android abstractions without
  explicit approval. Fire TV requirements must justify architectural changes.
- Keep the application a focused screen grabber for Hyperion. Follow KISS, YAGNI,
  and evidence-based optimization; prefer small, understandable changes and
  preserve working components. Code age alone does not justify a rewrite.
- Read [docs/roadmap.md](docs/roadmap.md) and relevant local documentation before
  changing the affected area. Work packages describe plans, not implementation
  authorization.

## Language

- Communicate with the user in German. Write repository content, documentation,
  identifiers, and commit messages in English. Preserve the original language of
  existing technical code, translations, and historical records.

## Changes and scope

- Inspect the relevant implementation and current Git state before editing.
  Stay within the explicitly requested scope; avoid speculative features,
  unrelated refactoring, and generic frameworks without a Fire TV requirement.
- Preserve unrelated local modifications. Never silently overwrite, discard, or
  reset user changes.
- Do not change SDK versions, application identity, signing, transport defaults,
  or release versions without an explicitly justified and authorized task.

## Builds and tests

- Any build requires explicit authorization for the concrete
  operation in the current session. Before requesting it, state the purpose,
  smallest necessary scope, exact command, and expected result.
- A general instruction to implement, test, continue, or finish is not build
  authorization. Check whether a test or helper implicitly builds; the same gate
  applies. Non-building source inspection and static checks are allowed.
- A failed build does not authorize another attempt. Diagnose within scope and
  obtain renewed authorization before rebuilding.
- Do not change source, Docker configuration, APT, DNS, HTTP settings, or
  infrastructure to work around an unrelated build or network failure. The known
  Docker/APT networking problem with VPN enabled must not trigger automatic
  retries or workarounds. Stop the affected operation at the first such
  infrastructure error, preserve available logs, and report the cause.
- Distinguish inspected tests from executed tests; never claim an unexecuted test
  passed. Keep checks proportionate to the change.

## Git operations

- Do not commit without explicit user authorization. Pushing requires separate
  explicit authorization; tags, releases, branch merges, and branch deletion
  also require authorization.
- Before an authorized commit, inspect the complete relevant diff and staged
  diff, run `git diff --check` and `git diff --cached --check`, and verify that
  only task-related files are staged.
- Check changes for secrets, credentials, signing material, device-specific data,
  and relevant third-party provenance. Do not add private keys, local addresses,
  private device identifiers, or personal workstation paths to repository content.
- Never use destructive resets or forced pushes as routine cleanup. Preserve
  existing signing credentials and the validated release update path.

## Fire TV validation

- Fire TV hardware validation is the release-relevant hardware validation.
  Hardware deployment requires explicit user authorization.
- The documented reference is Fire TV `AFTKRT`, Fire OS 8, Android API 30. Record
  the exact device model and Fire OS version for each validation; do not infer
  support for other Fire TV devices, Mobile, or generic Android TV.
- Preserve start, stop, restart, reconnect, capture, transport selection, and
  own-priority Clear behavior. Keep Protocol Buffers the stable default and
  regression reference; FlatBuffer remains experimental unless separately
  qualified. Do not introduce silent fallback.
- JVM tests do not establish real MediaProjection behavior. Report static checks,
  automated tests, and physical hardware validation separately, including missing
  coverage. Preserve application identity, settings, and signed in-place updates.

## Reporting

At completion, report in German what changed and why, affected files, checks
actually performed, checks not performed, remaining risks, and the Git working-tree
state. Make uncertainty explicit; do not present assumptions as facts.
