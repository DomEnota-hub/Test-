#!/usr/bin/env python3
import gzip
import json
import sys
import zipfile
from pathlib import Path

EXPECTED = [
    "ermak_diagnostics",
    "ermak_equipment",
    "ermak_knowledge",
    "ermak_links",
    "ermak_schemes",
    "ermak_system_map",
    "vl80s_acceptance",
    "vl80s_diagnostics",
    "vl80s_electrical",
    "vl80s_electrical_variants",
    "vl80s_equipment",
    "vl80s_pneumatic",
    "vl80s_pneumatic_variants",
    "vl80s_variants",
]


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: verify_technical_assets_in_apk.py <apk>", file=sys.stderr)
        return 2

    apk = Path(sys.argv[1])
    if not apk.is_file():
        print(f"APK not found: {apk}", file=sys.stderr)
        return 2

    failures = []
    resolved = []
    with zipfile.ZipFile(apk) as zf:
        names = set(zf.namelist())
        for stem in EXPECTED:
            plain = f"assets/technical/{stem}.json"
            zipped = f"assets/technical/{stem}.json.gz"
            if plain in names:
                path = plain
                raw = zf.read(path)
            elif zipped in names:
                path = zipped
                try:
                    raw = gzip.decompress(zf.read(path))
                except Exception as exc:
                    failures.append(f"{zipped}: gzip decode failed: {exc}")
                    continue
            else:
                failures.append(f"{stem}: missing .json/.json.gz asset")
                continue

            try:
                json.loads(raw.decode("utf-8"))
            except Exception as exc:
                failures.append(f"{path}: JSON parse failed: {exc}")
                continue
            resolved.append(path)

    if failures:
        print("TECHNICAL ASSET CONTRACT FAILED", file=sys.stderr)
        for item in failures:
            print(f" - {item}", file=sys.stderr)
        return 1

    print(f"TECHNICAL ASSET CONTRACT PASS: {len(resolved)}/{len(EXPECTED)}")
    for item in resolved:
        print(f" - {item}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
