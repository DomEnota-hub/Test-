# Ermak comprehensive audit — legacy batch A (2026-09-26)

## Scope

Re-audited and deepened 24 scenarios that already had separate trees but still exposed legacy `source_action` nodes:

`ER-DIAG-001`, `ER-DIAG-006…014`, `ER-DIAG-016…029`.

## Source policy

1. Current/publicly verified normative material controls permissions and limitations.
2. Manufacturer RE is used for apparatus, circuits and profile-dependent technical structure.
3. Specialist sites and the superseded 2010 recommendations are cross-check material only.
4. Manual HV operation, repeated resets, trial fuse replacement, sequential re-energizing to reproduce a short circuit, protection bypass and similar legacy procedures are not exposed as generic user actions.
5. When a later public revision or exact locomotive profile is not proven, the route fails closed.

## Sources cross-checked

- OAO RZD order №996/r of 12.04.2022, Appendix 9 for 2ES5K/3ES5K. Latest publicly confirmed edition found during the audit: №592/r of 05.03.2024; RZD procurement data in 2025 also names that edition. Absence of a later non-public/non-indexed revision is **not** assumed.
- Manufacturer operation manual IDMB.661142.009RE1/RE4/RE6 (public copies), used only after execution/profile confirmation.
- RCIT technical materials and specialist mirrors as independent symptom/circuit cross-checks; legacy №671r (31.03.2010) is marked historical.

## Technical anchors retained, but profile-gated

- `F1/F2`, `SA1/SA2`
- `SF22`, `QF1-UA2`
- `F37`, `QF11/QF12`
- `KM41/KM42`
- `QT1`, wires `E6/N36/N37`
- `A73/A74`, `SF45`
- reserve-compressor equipment `Q6/QS27/QS28` (observation/profile only)
- `SF25/SF26/SF27`, `KK11/KK12/KK14`, `KM11/KM12/KM14`, `S11/S12/S17`

## Safety changes

21 of the 24 target graphs remove `source_action` completely. `ER-DIAG-012`, `ER-DIAG-013` and `ER-DIAG-014` retain exactly one historical `source_action` node each solely to preserve runtime coverage of the application's `SAFETY_GATE_REQUIRED` policy. These nodes explicitly state that the 2010 manual-QT1 procedure is **not a currently permitted user action**, are marked `manual_power_apparatus`, remain source-bound, and lead only to a safe reassessment terminal.

All 24 routes now separate symptom scope, confirmed execution profile, command/power/protection/actuator/indication evidence, and safe terminal outcomes. Historical recovery operations remain traceable but are not used as ordinary diagnostic tests.

The canonical action policy for all 24 routes is `TRIAGE_ONLY_NO_REPAIR` until a current source and exact profile authorize a further action.

## QA contract

`qa/verify_ermak_legacy_batchA_001_006_029.py` verifies:

- 136 scenarios preserved;
- app/patch mirrors identical;
- 21 routes contain no `source_action`;
- `ER-DIAG-012…014` each contain exactly one explicitly historical, blocked `SAFETY_GATE_REQUIRED` node;
- no old generic prompt in those routes;
- at least four distinct questions, three checks and three prohibitions per route;
- full graph reachability;
- required apparatus tokens remain present;
- old №671r reference is historical;
- current №996/r cross-check reference exists;
- dangerous legacy phrases are absent from the canonical user graph/actions.
