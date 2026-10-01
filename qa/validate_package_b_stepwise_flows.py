#!/usr/bin/env python3
import gzip
import json
import re
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/assets/technical/stepwise_scheme_flows.json"
PATCH = ROOT / "patch/app/src/main/assets/technical/stepwise_scheme_flows.json"
INTERNAL = re.compile(r"(?i)(?:CHME3E|CHME3T|CHME3|VL80|VL|ER|SYS|SAFETY)(?:-[A-Z0-9_]+)+")


def fail(msg):
    raise SystemExit("PACKAGE_B_VALIDATION_FAIL: " + msg)


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def load_gz(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        return json.load(f)


def main():
    if not APP.exists() or not PATCH.exists():
        fail("generated asset missing")
    if APP.read_bytes() != PATCH.read_bytes():
        fail("app/patch stepwise assets differ")
    data = load(APP)
    if data.get("schemaVersion") != 1:
        fail("unexpected schemaVersion")
    sequences = data.get("sequences") or []
    if not sequences:
        fail("no sequences")
    ids = [s.get("id") for s in sequences]
    if None in ids or len(ids) != len(set(ids)):
        fail("sequence IDs missing or duplicated")

    allowed_domains = set(data["contract"]["domains"])
    allowed_repr = set(data["contract"]["representationKinds"])
    allowed_flows = {x["flowKind"] for x in data["contract"]["legend"]}
    required_families = {"ERMAK", "CHME3", "CHME3T", "CHME3E"}
    family_counts = Counter()
    domain_counts = Counter()
    flow_counts = Counter()
    total_steps = 0

    for seq in sequences:
        sid = seq["id"]
        family = seq.get("family")
        family_counts[family] += 1
        domain = seq.get("domain")
        domain_counts[domain] += 1
        if domain not in allowed_domains:
            fail(f"{sid}: invalid domain {domain}")
        if seq.get("representation") not in allowed_repr:
            fail(f"{sid}: invalid representation")
        disclaimer = (seq.get("functionalDisclaimer") or "").lower()
        if "не" not in disclaimer or ("монтаж" not in disclaimer and "функцион" not in disclaimer):
            fail(f"{sid}: functional disclaimer is not explicit")
        app = seq.get("applicability") or {}
        if not app:
            fail(f"{sid}: applicability missing")
        sources = seq.get("sourcePresentations") or []
        if not sources:
            fail(f"{sid}: sources missing")
        for src in sources:
            title = (src.get("title") or "").strip()
            if not title:
                fail(f"{sid}: source has no human title")
            if title == src.get("sourceId") or INTERNAL.fullmatch(title):
                fail(f"{sid}: source title exposes internal key")
        title = seq.get("title") or ""
        if not title or INTERNAL.search(title):
            fail(f"{sid}: invalid user-facing title")
        steps = seq.get("steps") or []
        if not steps:
            fail(f"{sid}: no steps")
        total_steps += len(steps)
        expected = list(range(1, len(steps) + 1))
        actual = [x.get("step") for x in steps]
        if actual != expected:
            fail(f"{sid}: steps are not contiguous")
        for step in steps:
            if not step.get("title") or INTERNAL.search(step["title"]):
                fail(f"{sid}: step title exposes ID")
            if not step.get("explanation") or INTERNAL.search(step["explanation"]):
                fail(f"{sid}: step explanation exposes ID")
            edges = step.get("edges") or []
            if not edges:
                fail(f"{sid}: step {step['step']} has no edges")
            for edge in edges:
                kind = edge.get("flowKind")
                if kind not in allowed_flows:
                    fail(f"{sid}: unknown flowKind {kind}")
                flow_counts[kind] += 1
                if not edge.get("label") or INTERNAL.search(edge["label"]):
                    fail(f"{sid}: user-facing edge label invalid")
                has_eq = edge.get("fromEquipmentId") and edge.get("toEquipmentId")
                has_anchor = edge.get("fromAnchor") and edge.get("toAnchor")
                if not (has_eq or has_anchor):
                    fail(f"{sid}: edge endpoints missing")

        # Hard profile isolation.
        serialized = json.dumps(seq, ensure_ascii=False)
        if family in {"CHME3", "CHME3E"} and "CHME3T-EQ-" in serialized:
            fail(f"{sid}: CHME3T equipment leaked into {family}")
        if family != "CHME3E" and "CHME3E-EQ-" in serialized:
            fail(f"{sid}: CHME3E equipment leaked into {family}")
        if sid.startswith("CHME3T-STEP-EDB"):
            profiles = set((app.get("profileIds") or []))
            if profiles != {"chme3t-rheostatic"}:
                fail(f"{sid}: EDB profile gate is not exclusive")
        if sid == "CHME3E-STEP-PREHEAT-EXTERNAL":
            safety = (seq.get("safetyNote") or "").lower()
            if seq.get("authority") != "INFORMATION_ONLY" or "не является инструкцией" not in safety or "трёхфаз" not in safety:
                fail("CHME3E external preheat safety boundary missing")

    if not required_families.issubset(family_counts):
        fail(f"family coverage incomplete: {family_counts}")
    if domain_counts["ELECTRICAL"] == 0 or domain_counts["PNEUMATIC"] == 0:
        fail(f"domain coverage incomplete: {domain_counts}")
    for needed in ("TRACTION_CURRENT", "COMMAND", "FEEDBACK_SIGNAL", "AIR_SUPPLY", "CONTROL_PRESSURE", "BRAKE_PIPE_PRESSURE_CHANGE"):
        if flow_counts[needed] == 0:
            fail(f"required semantic flow kind missing: {needed}")

    # Every Ermak sequence must correspond exactly to an existing source flow record/path.
    ermak = load_gz(ROOT / "app/src/main/assets/technical/ermak_schemes.json.gz")
    scheme_by_id = {s["id"]: s for s in ermak.get("schemes", [])}
    for seq in (s for s in sequences if s["family"] == "ERMAK"):
        scheme = scheme_by_id.get(seq.get("sourceSchemeRef"))
        if not scheme:
            fail(f"{seq['id']}: source Ermak scheme missing")
        flow_id = (seq.get("origin") or {}).get("flowRecordId")
        flow = next((x for x in ((scheme.get("layers") or {}).get("flowLayer") or []) if x.get("id") == flow_id), None)
        if not flow:
            fail(f"{seq['id']}: Ermak flow origin missing")
        path = flow.get("path") or []
        flattened = []
        for step in seq["steps"]:
            edge = step["edges"][0]
            if not flattened:
                flattened.append(edge["fromAnchor"])
            flattened.append(edge["toAnchor"])
        if flattened != path:
            fail(f"{seq['id']}: generated path diverges from source flowLayer")
        if (flow.get("confidence") or "").lower() != "source_backed_functional":
            fail(f"{seq['id']}: source flow is not source_backed_functional")

    # CHME sequence sourceSchemeRefs must be current canonical scheme IDs.
    common = load(ROOT / "docs/locomotives/chme3/atlas_schemes_functional.json")
    variant = load(ROOT / "docs/locomotives/diesel/chme3-family/chme3e/schemes_variant.json")
    chme_scheme_ids = {x["id"] for x in common.get("records", []) + variant.get("records", [])}
    for seq in (s for s in sequences if s["family"].startswith("CHME3")):
        if seq.get("sourceSchemeRef") not in chme_scheme_ids:
            fail(f"{seq['id']}: CHME sourceSchemeRef missing")

    # The package deliberately has no automatic upgrade to installation tracing.
    if any(s.get("representation") != "SOURCE_BACKED_FUNCTIONAL" for s in sequences):
        fail("a sequence was upgraded beyond functional evidence")

    print("PACKAGE_B_VALIDATION_PASS")
    print("sequences", len(sequences), "steps", total_steps)
    print("families", dict(sorted(family_counts.items())))
    print("domains", dict(sorted(domain_counts.items())))
    print("flowKinds", dict(sorted(flow_counts.items())))


if __name__ == "__main__":
    main()
