# Locomotive Diagnostic Runtime Contract v2

Status: mandatory target contract for new locomotive diagnostic packages.

This contract exists to prevent catalogue-only or generic troubleshooting scenarios. A scenario is accepted only when its runtime route genuinely narrows the fault using observations available to the locomotive crew and changes the next step based on the answer.

## Runtime shape

Every scenario has a `runtimeGraph` with an explicit `startNodeId` and unique `nodes`. Supported node types are `question`, `check`, `finding`, `source_action`, `emergency_action`, and `terminal`. Transitions use `choices[].nextNodeId` or `nextNodeId`. Every reachable non-terminal node must lead onward and every route must end in a terminal or a deliberate uncertainty/stop terminal.

The graph mirrors the proven Ermak runtime model: a user's answer changes the current node, branch history is retained, and policy-bound action text is hidden if its gate fails.

## Diagnostic usefulness

A scenario MUST NOT be accepted when it is only a generic sequence like “inspect equipment → check power/air → check wiring/connections → report”. A useful scenario contains locomotive- and symptom-specific discriminators. At least one early branch must split plausible causes using an observable fact: apparatus state, pressure/current/voltage/temperature behaviour, relay/contactor state, smoke colour, sound, smell, leakage, vibration, timing of the fault, a controller position, a protection indication, or another concrete observation.

Different answers must lead to materially different checks, findings, or safe actions. A cosmetic branch whose alternatives immediately converge onto the same generic sequence does not count.

## Action location / access level

Every `check`, `source_action`, and `emergency_action` node declares `actionLevel`:

- `CAB` — available from the cab/normal operating position.
- `SAFE_STOP` — only after a safe stop and stated prerequisites.
- `AUTHORIZED_ONLY` — only by personnel with the required admission/authorization.
- `STOP` — stop further diagnostic actions and secure/report according to the applicable rule.

If a route moves from cab observations to physical inspection, that transition must be explicit. A scenario must never silently send the user out of the cab or into equipment spaces while the locomotive is in an incompatible state.

## Node requirements

### question
Requires `id`, `type`, `text`, at least two meaningful choices, and an uncertainty path (`uncertainNextNodeId` or an explicit “not sure” choice). Questions must discriminate causes; “Is everything normal?” is not sufficient unless the exact observed parameter is named.

### check
Requires `text`, `expected`, `ifAbnormal`, `actionLevel`, and a transition. A check is an observation-producing step, not an instruction to repair.

### finding
States what has been localized or excluded and why; normally reached after a discriminating observation.

### source_action / emergency_action
Requires `actionLevel`, `riskClass`, `userFacingPolicy`, `sourceBound=true` for source-dependent actions, a provenance/source reference, and a profile/applicability gate when execution matters. These nodes follow the same fail-closed policy principles as `DiagnosticPolicyEngine`; blocked action text must not be exposed.

### terminal
Must state a localized probable fault and next safe action, an operation restriction/stop/report requirement, or insufficient evidence plus the specific observations needed next. “Check according to the manual” or “contact maintenance” alone is not a diagnostic result unless the route has already localized the fault as far as the crew can safely do.

## Required scenario metadata

Retain locomotive/profile applicability, symptom and natural-language aliases, equipment/system IDs, immediate safe actions, danger signs/stop conditions, report fields, evidence references/provenance, confidence, and assistant search vocabulary.

## Provenance boundary

`FIELD_PRACTICE`, `LOCAL_INSTRUCTION`, `CONFLICT`, and `UNVERIFIED` evidence may improve symptom recognition, hypotheses, and discriminating checks but does not automatically authorize an executable action. Field bypasses, jumpers, wedges, defeated protections, and similar measures remain research evidence until a current source, exact locomotive profile, and safety policy allow them.

## Semantic anti-generic gate

Final QA must reject missing/invalid starts; invalid targets; unreachable nodes; questions without meaningful alternatives or uncertainty; branches that immediately collapse without new information; no symptom-specific discriminator before terminal; identical/near-identical route signatures across unrelated scenarios; terminals that do not localize/restrict/stop/request specific evidence; checks/actions without `actionLevel`; gated actions without source/profile/safety policy; profile leakage; and scenarios that only rename equipment inside a generic template.

## ChME3 migration rule

The existing ChME3 pass-3 `decisionTree` records are content candidates, not final runtime graphs. Their symptoms, evidence, hypotheses, and terminology remain useful, but ChME3 is not diagnostics-complete until all accepted scenarios are promoted to this runtime contract and pass semantic QA.

This rule applies to every future locomotive package. Existing VL80S and Ermak diagnostics will be re-audited against the same anti-generic standard during the later harmonization stage.