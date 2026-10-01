"""Validate the generated Android projection of the ChME3 foundation."""
import gzip
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(name, apk=None):
    relative = f"technical/chme3_{name}.json.gz"
    if apk:
        # Android packages .gz assets after decompressing them and drops the suffix.
        with zipfile.ZipFile(apk) as archive:
            return json.loads(archive.read(f"assets/{relative.removesuffix('.gz')}"))
    return json.loads(gzip.decompress((ROOT / "app/src/main/assets" / relative).read_bytes()))


def check(apk=None):
    expected = {"chme3": (67, 55), "chme3t": (70, 56), "chme3e": (100, 67)}
    for variant, (equipment_count, scenario_count) in expected.items():
        entries = read(f"{variant}_catalog", apk)["entries"]
        scenarios = read(f"{variant}_diagnostics", apk)["scenarios"]
        by_id = {item["id"]: item for item in entries}
        equipment = {item["id"] for item in entries if item["section"] == "EQUIPMENT"}
        assert len(equipment) == equipment_count, (variant, "equipment", len(equipment))
        assert len(scenarios) == scenario_count, (variant, "diagnostics", len(scenarios))
        assert len(by_id) == len(entries), (variant, "duplicate entry")
        assert len({s["id"] for s in scenarios}) == len(scenarios), (variant, "duplicate scenario")
        assert {s["id"] for s in scenarios} == {s["id"] for s in scenarios if s["graph"]["nodes"]}, variant
        assert sum(item["status"] == "MANDATORY_CHECK" for item in entries) == (21 if variant == "chme3t" else 20)
        assert sum(item["status"] == "PROFILE_EXTENDED_CHECK" for item in entries) == (4 if variant == "chme3e" else 0)
        schemes = [item for item in entries if item["status"] == "INTERACTIVE_SCHEME"]
        assert len(schemes) == {"chme3": 11, "chme3t": 12, "chme3e": 15}[variant]
        for entry in entries:
            assert all(step in by_id for step in entry["sequence"]), (variant, entry["id"], "route")
            assert all(h["equipmentId"] in equipment for h in entry["hotspots"]), (variant, entry["id"], "hotspot")
        for scenario in scenarios:
            graph = scenario["graph"]
            nodes = {node["id"]: node for node in graph["nodes"]}
            assert graph["startNodeId"] in nodes, scenario["id"]
            for node in nodes.values():
                for target in [node.get("nextNodeId")] + [choice["nextNodeId"] for choice in node.get("choices", [])]:
                    assert target is None or target in nodes, (scenario["id"], target)
            assert set(scenario["equipmentRefs"]) <= equipment, scenario["id"]
        if variant == "chme3e":
            assert not any("CHME3T-" in item["id"] for item in entries + scenarios)
            assert len([s for s in scenarios if s["id"].startswith("CHME3E-DIAG-")]) == 12
        print(f"{variant}: {len(equipment)} equipment, {len(schemes)} interactive views, {len(scenarios)} diagnostics, routes valid")


if __name__ == "__main__":
    check(sys.argv[1] if len(sys.argv) > 1 else None)
