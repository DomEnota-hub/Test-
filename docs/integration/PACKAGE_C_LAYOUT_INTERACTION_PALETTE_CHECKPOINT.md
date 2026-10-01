# Package C — layout, interaction and semantic palette checkpoint

Status: **GENERATED / VALIDATION REQUIRED**

## Scope

Package C prepares renderer contracts only. It does not replace the Android scheme renderer and does not claim physical wire, terminal, pipe, or equipment placement accuracy.

## Generated coverage

- Package B sequences: **55**
- Layout nodes: **208**
- Existing interactive hotspot placements reused: **208**
- Deterministic functional fallback placements: **0**
- Layout modes: `{'SOURCE_HOTSPOT_COORDINATES': 55}`
- Semantic flow kinds covered: **19**

## View contract

- open/reset state is `RESET_TO_FIT`;
- zoom is a multiplier relative to fit (`1.0..5.0`), so narrow screens can always display the complete scheme;
- two-axis pan is available after zoom;
- pinch zoom is centred on the gesture centroid;
- a visible reset-to-fit control is mandatory;
- portrait and landscape are both supported;
- reduced-motion mode disables moving-flow animation;
- step meaning never depends on colour alone.

## Semantic palette

Separate light/dark tokens exist for every Package B flow kind. Purpose is neutral, normal condition is green, deviation is orange, danger/prohibition is red. Deviation orange is deliberately distinct from the amber application accent.

The requested theme migration `стальная зелёная → яркая зелёная` is recorded as a renderer/theme integration target (`#16A34A` light / `#4ADE80` dark), but this Package C does not change the current Kotlin theme runtime.

## Spatial truth boundary

`SOURCE_HOTSPOT_COORDINATES` means an existing interactive drawing coordinate was reused. It does **not** mean the location is a verified physical installation coordinate. `DETERMINISTIC_FUNCTIONAL_LAYOUT` is renderer-only placement. Neither may be presented as a wiring/piping route.
