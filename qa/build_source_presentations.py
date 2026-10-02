"""Project human-readable source titles into Android only for runtime-integrated sources."""
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TECHNICAL_ASSETS = ROOT / "app/src/main/assets/technical"
PRESENTATIONS = TECHNICAL_ASSETS / "source_presentations.json"


def load_json(path: Path):
    if path.name.endswith(".json.gz"):
        with gzip.open(path, "rt", encoding="utf-8") as fh:
            return json.load(fh)
    return json.loads(path.read_text(encoding="utf-8"))


def collect_source_ids(value, out):
    if isinstance(value, dict):
        for nested in value.values():
            collect_source_ids(nested, out)
    elif isinstance(value, list):
        for nested in value:
            collect_source_ids(nested, out)
    elif isinstance(value, str) and "-SRC-" in value:
        out.add(value)


# Preserve source IDs that are already part of the Android presentation contract.
# This keeps the projection stable for legacy/runtime sources that may be resolved
# indirectly by Kotlin rather than by a JSON asset.
integrated_ids = set()
if PRESENTATIONS.exists():
    integrated_ids.update((load_json(PRESENTATIONS).get("sources") or {}).keys())

# New source IDs enter the Android projection automatically once a runtime asset
# actually references them. Merely adding a knowledge-foundation source registry
# must not silently integrate an unfinished locomotive family into the app.
runtime_source_ids = set()
for path in sorted(TECHNICAL_ASSETS.iterdir()):
    if path == PRESENTATIONS or not (path.name.endswith(".json") or path.name.endswith(".json.gz")):
        continue
    try:
        collect_source_ids(load_json(path), runtime_source_ids)
    except Exception:
        continue

allowed_source_ids = integrated_ids | runtime_source_ids
records = {}
for path in sorted((ROOT / "docs/locomotives").rglob("*source_registry*.json")):
    for source in json.loads(path.read_text(encoding="utf-8")).get("sources", []):
        source_id = source.get("id")
        title = source.get("title") or source.get("document")
        if not source_id or not title or source_id not in allowed_source_ids:
            continue
        detail = source.get("document") or ""
        if detail == title:
            detail = ""
        records[source_id] = {"title": title, "detail": detail}

# The stepwise package carries readable primary references for Ermak schemes and
# is itself an Android runtime asset, so its source presentations are authoritative
# for projection even if no matching docs registry exists.
flows = json.loads((TECHNICAL_ASSETS / "stepwise_scheme_flows.json").read_text(encoding="utf-8"))
for sequence in flows["sequences"]:
    for source in sequence.get("sourcePresentations", []):
        source_id, title = source.get("sourceId"), source.get("title")
        if source_id and title and title != "Источник схемы":
            records.setdefault(source_id, {"title": title, "detail": source.get("documentDetails") or ""})

payload = json.dumps({"schemaVersion": 1, "sources": records}, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
for base in ("app", "patch/app"):
    path = ROOT / base / "src/main/assets/technical/source_presentations.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(payload, encoding="utf-8")
print(
    f"Projected {len(records)} readable source titles "
    f"from {len(allowed_source_ids)} integrated/runtime source IDs"
)
