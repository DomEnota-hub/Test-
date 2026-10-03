# Expanded emergency diagnostics — Stage 4 checkpoint

Status: `COMPLETE_GREEN`

Branch: `integration/extended-emergency-stage4`

Stage 3 base checkpoint: `81a189cb3eb31d83000f6e40338895dd92ec2696`

Stage 4 implementation SHA before this checkpoint: `9ce2c4695083c5fff56d2da7dffe0e0b19897576`

## Result

Stage 4 connects the expanded/emergency research layer to the Android diagnostic runtime without changing the authority of the standard diagnostic layer.

The Android runtime contains 92/92 routing decisions and 77 safe display records: 49 `INFORMATION_ONLY`, 28 `PROHIBITED`, 0 `CONDITIONAL_ACTION`. Expanded evidence remains non-executable, procedure-level hazardous detail remains hidden, exact-profile matching is mandatory, adjacent-variant inheritance is denied, and unknown execution remains fail-closed.

The user-facing expanded card remains visually distinct with the agreed turquoise expanded-scenario treatment while red remains reserved for danger/prohibition. Human-readable source title, provenance and status are shown; raw candidate/source IDs are not exposed.

## Null-profile defect found and fixed

During final integration review, the first Android implementation mapped expanded profiles through the legacy `TechnicalFamily` enum and returned `null` for families not represented there. This made the prepared TEM2/TEM2U expanded data unreachable and created a repeatable failure mode for every future locomotive integration.

Stage 4 replaces that nullable path with the central `LocomotiveProfileContext` / `LocomotiveProfileRegistry` contract:

- profile resolution never returns `null`;
- every profile registered in `docs/locomotives/manifests/locomotive_families.json` must have an exact runtime mapping;
- unknown, blank, not-yet-integrated or cross-family profile requests resolve to explicit `UNKNOWN_FAIL_CLOSED` with profile ID `unknown`;
- `ExtendedEmergencyRuntimeRepository` accepts a non-null `LocomotiveProfileContext` and only serves evidence when the context is an exact registered mapping;
- legacy `TechnicalFamily` compatibility resolves ChME3/ChME3T/ChME3E exactly and resolves VL80S/Ermak explicitly fail-closed rather than returning `null`;
- TEM2 and TEM2U are already represented in the central runtime profile registry as `tem2-base` and `tem2u-improved` without falsely adding them to the legacy `TechnicalFamily` enum before their full app-section integration.

The manifest/runtime guard currently covers exactly five registered profiles:

- `chme3-base` → `chme3-family`
- `chme3t-rheostatic` → `chme3-family`
- `chme3e-electronic` → `chme3-family`
- `tem2-base` → `tem2-family`
- `tem2u-improved` → `tem2-family`

A dedicated repository-wide workflow, `Locomotive profile registry guard`, now runs when the global locomotive manifest or runtime profile registry changes. A future locomotive/profile added to the manifest without a runtime mapping fails CI instead of silently producing a nullable/unreachable runtime state.

## Integration scope

The currently materialized UI attachment remains on the already integrated ChME diagnostic screen. TEM2/TEM2U expanded evidence is profile-ready in the central registry and runtime asset, but TEM2/TEM2U are deliberately not added to the legacy `TechnicalFamily`/screen routing until their separate full Android locomotive integration is performed. This avoids claiming app integration that does not yet exist.

## Green validation before checkpoint

Implementation SHA `9ce2c4695083c5fff56d2da7dffe0e0b19897576` passed:

- `Locomotive profile registry guard`, run `37097665652` — SUCCESS;
- `Expanded emergency Stage 4 integration`, run `37097665614` — SUCCESS.

The Stage 4 integration run passed Stage 2/Stage 3 regression, Stage 4 cross-layer validation, global non-null profile-registry validation, Stage 1 authority-boundary regression, ChME3 standard projection and diagnostic graph regression, TEM2 Stage 1–3 regression, deterministic runtime/UI materialization checks, Kotlin compile, unit tests, lint, `assembleDebug`, Stage 4 change-boundary enforcement and `git diff --check`.

## Deliberate limitation

Android CI still uses the existing source-only validation workaround for the unavailable real `sherpa-onnx.aar`. Therefore this checkpoint does not claim a real offline voice-runtime validation. The expanded diagnostic/profile integration itself is covered independently of that limitation.

## Completion rule

This Stage 4 checkpoint is complete only when the Stage 4 workflow is green on the exact checkpoint commit SHA. No Stage 5 or further locomotive integration is implied by this document.
