# Dev14 canonical data contract — inventory

Source snapshot: `a3c9861f5d00c4c2993eb405541694ecead7e83a` from the previous prepared source.
The 14 compressed JSON packages are the canonical input for dev14. They are not yet
copied into the dev13 application and this stage makes no visible UI changes.

## Interpretation rule

A field may be rendered only when this contract classifies it as **content**.
Rules, graph structures, indexes, IDs, evidence and generation metadata must be
consumed by typed runtime code or validation; they must never become fallback text
cards.

`unknown`, `not confirmed`, range boundaries and candidate fragments are valid
runtime states. They cannot silently resolve to a different profile.

## Packages and intended consumers

| Package | Primary records | Intended runtime role |
| --- | ---: | --- |
| `vl80s_variants` | 9 physical buckets | VL80S profile resolver; ranges, rules and non-merge rules are constraints |
| `vl80s_equipment` | 89 records | equipment catalogue; applicability and feature rules filter records |
| `vl80s_acceptance` | 66 items / 7 routes | shared inspection state, route projection, gates and persistence |
| `vl80s_diagnostics` | 93 scenarios / 1171 edges | diagnostic graph; edges, gates and exceptions control navigation |
| `vl80s_electrical` | 8 schemes / 8 overlays | interactive scheme runtime; nodes/edges/overlays are not prose |
| `vl80s_electrical_variants` | 8 overlays | additional scheme compatibility constraints |
| `vl80s_pneumatic` | 8 views / 13 states / 5 references | pneumatic runtime; paths and gates control presentation |
| `vl80s_pneumatic_variants` | 13 applicability entries | pneumatic compatibility constraints |
| `ermak_system_map` | 15 systems / 16 critical links | system graph and cross-navigation |
| `ermak_equipment` | 111 records | equipment catalogue filtered by applicability |
| `ermak_knowledge` | 138 articles | articles; variant rules filter or annotate content |
| `ermak_diagnostics` | 135 scenarios | interactive graph and safety/profile gates |
| `ermak_schemes` | 22 schemes | selector, rule and hotspot-based scheme runtime |
| `ermak_links` | 4 indexes | one-time direct/reverse relationship indexes only |

## Classification

### Content
Titles, summaries, symptoms, observable signs, normal/abnormal states, purpose,
location, approved actions, source-backed explanatory notes and explicitly
user-facing article blocks.

### Runtime selectors and constraints
Profile ranges, model/section dimensions, `applicability`, `variantRule`,
`variantRules`, `hardRules`, `requiredWhen`, `trigger`, `variantGate`,
non-merge rules and safety gates. These produce typed selection state and filtering.

### Graph/runtime control
Scenario IDs, nodes, edges, paths, outcomes, flow history, terminal states,
hotspots, overlays and click targets. These drive interaction and do not render as
generic cards.

### Relationship/index
Equipment/system/scenario/scheme references, reverse indexes, crosslinks and
critical links. These create links only after IDs have been validated and indexed.

### Metadata/evidence
Schema/catalog versions, generated dates, source registry, coverage/statistics,
policies, quarantine/open-work/final gates and integration contracts. These feed
validation/audit and are not normal user content.

## Required validation before repository implementation

1. Validate all stable IDs, duplicates and dangling references.
2. Validate diagnostic graph reachability and acceptance route membership.
3. Validate profile/variant rule syntax and impossible combinations.
4. Declare every top-level and critical nested field as one of the above classes.
5. Produce deterministic relationship and record-count fingerprints.
6. Verify repository construction and index preparation stay off the UI thread.

No dev14 UI component may be written until these checks pass.


## Baseline audit result

The initial audit passes on the source snapshot with **14 packages**, **604 declared
technical identities** and **7,683 technical references**. It also exposes **89
legacy VL80S equipment aliases** (`vl80-eq-*`). Those aliases are valid dev13
identities, but they are not canonical dev14 equipment IDs. Stage 2 must provide
an explicit alias-to-canonical mapping and tests for it; it must not create a
second card for either identity.

The audit treats indexes, state-applicability lists and variant-rule lists as
references or declared constraint IDs, not as duplicate user entities.
