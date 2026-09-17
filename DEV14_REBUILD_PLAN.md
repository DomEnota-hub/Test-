# Dev14 rebuild from dev13

Base commit: `8b1fffb7f3e28ae489b9b037f46f4b02be1e3253` (`1.2.2-dev13`).
Working branch: `dev14-rebuild-from-dev13`.

## Core rule

A green CI build is not acceptance. Every integration stage must pass three independent gates:

1. **Structural contract audit** — data shape, IDs, references, mappings, reachable handlers, no orphaned or dead canonical fields.
2. **Android black-box acceptance** — real launch/navigation/interaction on an emulator with fresh UI trees and screenshots.
3. **Dev13 regression comparison** — unchanged parts of the app must still behave and render as before.

No stage may be merged or used as the base of the next stage until all three gates are green.

## Stage 0 — Freeze and fingerprint dev13

Goal: create an immutable behavioural/UI baseline before technical integration.

- Pin source SHA and release signing certificate.
- Record package/version, launcher activity, screen registry, drawer routes, hidden exam trigger, local persistence keys and navigation state.
- Capture screenshots + fresh UI XML for all major dev13 screens.
- Record representative interactions, not only static screens.
- Hash important source/assets and store a baseline manifest.
- Build a signed dev13 control APK from the pinned source when needed.

Exit criteria:
- dev13 builds and launches;
- baseline screenshots/UI trees exist;
- all pre-existing user routes are enumerated;
- no hidden feature or persistent state is undocumented.

## Stage 1 — Canonical data inventory and semantic classification

Goal: understand what each canonical field *does* before writing UI code.

For every VL80S/Ermak JSON field classify it as one of:
- user-visible content;
- selector/input;
- compatibility/constraint rule;
- runtime control/graph;
- relationship/index;
- metadata/source/evidence;
- internal-only implementation data.

Create explicit contracts for:
- stable IDs;
- allowed references;
- required/optional fields;
- profile/variant dimensions;
- acceptance states/gates;
- diagnostic graph semantics;
- scheme layers/hotspots/flows;
- cross-links.

Structural workflow checks:
- duplicate IDs;
- dangling references;
- unknown enum/state values;
- impossible compatibility rules;
- unreachable diagnostic nodes;
- route items missing from acceptance source of truth;
- canonical fields with no declared consumer;
- UI strings accidentally sourced from fields classified as control/rules.

Exit criteria:
- 100% of canonical top-level/critical fields classified;
- no critical orphan/dangling references;
- no runtime/UI implementation yet.

## Stage 2 — Data/repository layer only

Goal: load canonical packages without changing visible dev13 UI.

- Add parsers/models/repositories behind interfaces.
- Keep all new technical data off the screen initially.
- Build indexes once; avoid repeated whole-dataset scans on the UI thread.
- Add deterministic unit tests for counts, IDs, relationship checksums and graph reachability.
- Add performance tests for large Ermak diagnostic/index preparation.

Structural workflow checks:
- repository output vs canonical counts/checksums;
- every parsed field either consumed intentionally or explicitly marked internal/unimplemented;
- no silent fallback to another variant;
- no regex/object creation inside obvious per-item hot loops where it can be precomputed;
- no UI-thread blocking repository initialization.

Android black-box:
- entire dev13 UI must remain visually/functionally unchanged.

Exit criteria:
- canonical data loads successfully;
- dev13 regression suite remains green;
- zero new user-visible cards.

## Stage 3 — Profile/variant engine

Goal: implement the rules that *build* the correct technical view instead of displaying rule text as content.

VL80S examples:
- number range/profile resolution;
- section/variant applicability;
- equipment/scheme/diagnostic filtering by resolved profile.

Ermak examples:
- model (2ES5K/3ES5K);
- head/booster section;
- traction/control/brake/safety/bearing dimensions;
- requiredWhen/hardRules/variantRules compatibility.

Rules must drive resolution; they must not become generic user cards unless explicitly marked as explanatory content.

Structural workflow checks:
- all selector dimensions represented in typed runtime state;
- every hard rule has an executable predicate and a test;
- unknown values remain unknown (no silent substitution);
- every resulting profile has a deterministic fingerprint;
- every canonical rule is referenced by the resolver or explicitly documented as non-runtime.

Android black-box:
- select representative profiles;
- verify incompatible combinations are blocked;
- verify unknown profile does not silently resolve;
- verify switching profile updates dependent technical content.

Exit criteria:
- resolver works independently of UI presentation;
- rule text is not dumped as technical cards.

## Stage 4 — VL80S integration, one subsystem at a time

Order:
1. equipment/reference;
2. acceptance;
3. diagnostics;
4. electrical schemes;
5. pneumatic schemes/reference benchmarks;
6. cross-links/search.

Each subsystem gets its own checkpoint before the next begins.

Acceptance-specific checks:
- one canonical item state shared across routes;
- five states;
- comments/resolution requirements;
- safety gates;
- route modes;
- persistence across navigation/restart.

Scheme-specific checks:
- overlays/states/paths control rendering/interactions rather than appearing as raw text blocks;
- representative hotspots and flow paths are clickable and resolve to the correct entities.

Android black-box:
- fresh UI XML for every step (never reuse stale dump after failure);
- screenshots at route roots/details/error states;
- Back/navigation/persistence tests.

Exit criteria:
- all VL80S user paths green;
- unchanged dev13 screens still match baseline.

## Stage 5 — Ermak integration, one subsystem at a time

Order:
1. systems/equipment/knowledge;
2. profile selector;
3. interactive diagnostics;
4. schemes;
5. critical links/cross-navigation/search.

Diagnostics requirements:
- heavy graph/index preparation off UI thread;
- one-time indexes;
- loading/error/retry UI;
- model/profile/safety gates enforced;
- graph history/reset/terminal outcomes validated.

Scheme requirements:
- selector drives compatibility and rendered scheme;
- hard rules/variant rules are executable constraints;
- hotspot/semantic/flow layers are interactive runtime data, not prose cards.

Android black-box:
- three VL80S ↔ Ermak switches;
- representative Ermak scenario to terminal or gated branch;
- representative scheme profile and hotspot;
- switch back to VL80S without stale Ermak state.

Exit criteria:
- Ermak critical routes green;
- no ANR/freeze during family/profile switching.

## Stage 6 — Whole-app regression and structural dead-path audit

Goal: prove that the technical update did not damage the rest of dev13.

Structural audit must detect:
- navigation destinations with no reachable entry point;
- click handlers that do nothing;
- UI strings/blocks constructed but never rendered;
- rendered blocks with empty/null content;
- canonical IDs referenced by UI but absent from repositories;
- dead search categories;
- duplicate cards created from rule/control fields;
- raw JSON/control prose leaking into user-facing cards;
- stale fallback branches that override selected family/profile;
- state that is reset by a LaunchedEffect/remember key unexpectedly;
- expensive synchronous work in first composition/click handlers.

Black-box regression matrix:
- Home;
- calculations/history;
- mass/appendix tools;
- first aid;
- exam/Q&A including hidden 1000 + 2381 route;
- palette/settings;
- atlas/reference;
- acceptance;
- VL80S diagnostics/schemes;
- Ermak diagnostics/schemes;
- search;
- drawer and Back behaviour;
- process restart/persistence.

Use image/UI-tree comparison against Stage 0 for screens that should not have changed.

Exit criteria:
- zero critical structural findings;
- all unchanged dev13 flows still pass;
- all new dev14 flows pass on Android.

## Stage 7 — Release candidate and upgrade-path verification

- Increment version only after functional acceptance.
- Build **release**, never debug, for candidate delivery.
- Sign with certificate SHA-256:
  `ea9fe9341a6ba72b585bd7ff82ce29c8fc5b7b59e76b8b134df203eaab8e2788`.
- Verify package/version/signature using `aapt` + `apksigner`.
- Install candidate **over dev13** on Android without uninstalling.
- Verify existing local data/preferences/history survive upgrade.
- Repeat critical smoke tests after in-place upgrade, not only clean install.
- Produce APK SHA-256 and final manifest of canonical assets/counts.

Exit criteria:
- in-place installation over dev13 succeeds;
- local data survives;
- release signature matches dev13;
- final black-box suite green.

## Workflow design rules

- CI compilation/unit tests are necessary but never sufficient.
- Android probes must use fresh `uiautomator` dumps; a failed dump cannot fall back to an old XML snapshot.
- Every failure uploads screenshot, fresh/last XML, activity state and logcat.
- Structural audits run before emulator tests and fail on unresolved critical contracts.
- Workflows derive representative target IDs/titles from the packaged canonical data instead of hardcoding assumptions where possible.
- New integrations are split by subsystem; never integrate all canonical packages in one giant commit.
- Keep a checkpoint commit after every green subsystem.
- No automatic promotion to the next stage on a merely successful build; promotion requires structural + Android + regression evidence.
