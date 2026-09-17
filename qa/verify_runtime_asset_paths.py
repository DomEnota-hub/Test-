#!/usr/bin/env python3
import re
import sys
import zipfile
from pathlib import Path

PATTERN = re.compile(r'technical/[A-Za-z0-9_./-]+\.json(?:\.gz)?')


def candidates(path: str):
    if path.endswith('.json.gz'):
        return (path, path[:-3])
    if path.endswith('.json'):
        return (path, path + '.gz')
    return (path,)


def collect(code_root: Path):
    found = set()
    for file in code_root.rglob('*.kt'):
        text = file.read_text(encoding='utf-8', errors='replace')
        found.update(PATTERN.findall(text))
    return sorted(found)


def main() -> int:
    if len(sys.argv) not in (3, 4):
        print('usage: verify_runtime_asset_paths.py <code_root> <assets_root> [apk]', file=sys.stderr)
        return 2

    code_root = Path(sys.argv[1])
    assets_root = Path(sys.argv[2])
    apk = Path(sys.argv[3]) if len(sys.argv) == 4 else None

    refs = collect(code_root)
    if not refs:
        print('No technical JSON runtime paths found', file=sys.stderr)
        return 1

    source_failures = []
    for ref in refs:
        if not any((assets_root / candidate).is_file() for candidate in candidates(ref)):
            source_failures.append(ref)

    if source_failures:
        print('RUNTIME ASSET SOURCE CONTRACT FAILED', file=sys.stderr)
        for ref in source_failures:
            print(f' - {ref}: no matching source asset', file=sys.stderr)
        return 1

    if apk is not None:
        if not apk.is_file():
            print(f'APK not found: {apk}', file=sys.stderr)
            return 2
        with zipfile.ZipFile(apk) as zf:
            names = set(zf.namelist())
        package_failures = []
        for ref in refs:
            packaged = [f'assets/{candidate}' for candidate in candidates(ref)]
            if not any(candidate in names for candidate in packaged):
                package_failures.append((ref, packaged))
        if package_failures:
            print('RUNTIME ASSET APK CONTRACT FAILED', file=sys.stderr)
            for ref, packaged in package_failures:
                print(f" - {ref}: expected one of {', '.join(packaged)}", file=sys.stderr)
            return 1

    print(f'RUNTIME ASSET PATH CONTRACT PASS: {len(refs)} referenced paths')
    for ref in refs:
        print(f' - {ref}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
