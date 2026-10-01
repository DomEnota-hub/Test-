"""Project human-readable source titles into the Android atlas without exposing routing IDs."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
records = {}
for path in sorted((ROOT / "docs/locomotives").rglob("*source_registry*.json")):
    for source in json.loads(path.read_text(encoding="utf-8")).get("sources", []):
        source_id = source.get("id")
        title = source.get("title") or source.get("document")
        if not source_id or not title:
            continue
        detail = source.get("document") or ""
        if detail == title:
            detail = ""
        records[source_id] = {"title": title, "detail": detail}

# The stepwise package also carries the readable primary references for Ermak schemes.
flows = json.loads((ROOT / "app/src/main/assets/technical/stepwise_scheme_flows.json").read_text(encoding="utf-8"))
for sequence in flows["sequences"]:
    for source in sequence.get("sourcePresentations", []):
        source_id, title = source.get("sourceId"), source.get("title")
        if source_id and title and source_id not in records and title != "Источник схемы":
            records[source_id] = {"title": title, "detail": source.get("documentDetails") or ""}

payload = json.dumps({"schemaVersion": 1, "sources": records}, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
for base in ("app", "patch/app"):
    path = ROOT / base / "src/main/assets/technical/source_presentations.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(payload, encoding="utf-8")
print(f"Projected {len(records)} readable source titles")
