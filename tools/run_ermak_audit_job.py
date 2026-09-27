#!/usr/bin/env python3
from __future__ import annotations

import argparse
import gzip
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "qa" / "ermak_audit_job.json"
ASSET_APP = "app/src/main/assets/technical/ermak_diagnostics.json.gz"
ASSET_PATCH = "patch/app/src/main/assets/technical/ermak_diagnostics.json.gz"
DEFAULT_ALLOWED_CHANGED = [ASSET_APP, ASSET_PATCH]


def die(message: str) -> None:
    raise SystemExit(message)


def load_manifest() -> dict:
    if not MANIFEST.exists():
        die(f"Missing manifest: {MANIFEST}")
    data = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        die("Audit manifest must be a JSON object")

    mode = data.get("mode")
    if mode not in {"verify_only", "apply_and_commit"}:
        die(f"Unsupported mode: {mode!r}")

    batch = data.get("batch")
    if not isinstance(batch, str) or not batch.strip():
        die("Manifest batch must be a non-empty string")

    for key in ("transformers", "qa"):
        value = data.get(key, [])
        if not isinstance(value, list) or not all(isinstance(x, str) and x for x in value):
            die(f"Manifest {key} must be a list of non-empty strings")

    allowed = data.get("allowed_changed", DEFAULT_ALLOWED_CHANGED)
    if allowed != DEFAULT_ALLOWED_CHANGED:
        die(f"allowed_changed must stay exactly {DEFAULT_ALLOWED_CHANGED!r}")

    for path in data.get("transformers", []):
        if not path.startswith("tools/") or ".." in Path(path).parts or not path.endswith(".py"):
            die(f"Unsafe transformer path: {path}")
    for path in data.get("qa", []):
        if not path.startswith("qa/") or ".." in Path(path).parts or not path.endswith(".py"):
            die(f"Unsafe QA path: {path}")

    if mode == "apply_and_commit":
        if not data.get("transformers"):
            die("apply_and_commit requires at least one transformer")
        msg = data.get("commit_message")
        if not isinstance(msg, str) or not msg.strip() or "\n" in msg:
            die("commit_message must be one non-empty line")
    return data


def run_python(path: str) -> None:
    full = ROOT / path
    if not full.is_file():
        die(f"Missing script: {path}")
    print(f"::group::python3 {path}", flush=True)
    try:
        subprocess.run([sys.executable, str(full)], cwd=ROOT, check=True)
    finally:
        print("::endgroup::", flush=True)


def changed_paths() -> list[str]:
    out = subprocess.check_output(
        ["git", "status", "--porcelain=v1", "--untracked-files=all"],
        cwd=ROOT,
        text=True,
    )
    paths: list[str] = []
    for line in out.splitlines():
        if not line:
            continue
        raw = line[3:]
        if " -> " in raw:
            raw = raw.split(" -> ", 1)[1]
        paths.append(raw)
    return sorted(set(paths))


def assert_asset_mirror() -> None:
    a = ROOT / ASSET_APP
    b = ROOT / ASSET_PATCH
    if not a.is_file() or not b.is_file():
        die("Ermak diagnostics mirror is missing")
    if a.read_bytes() != b.read_bytes():
        die("app/patch Ermak diagnostics assets differ")


def strict_generic_count() -> int:
    with gzip.open(ROOT / ASSET_APP, "rt", encoding="utf-8") as fh:
        data = json.load(fh)
    prompt = "Отказ локальный (одна секция/узел) или общий?"
    return sum(
        any(node.get("prompt") == prompt for node in scenario.get("graph", {}).get("nodes", []))
        for scenario in data.get("scenarios", [])
    )


def cmd_apply(data: dict) -> None:
    if data["mode"] == "verify_only":
        if data.get("transformers"):
            die("verify_only must not declare transformers")
        if changed_paths():
            die(f"Working tree must be clean before verify-only run: {changed_paths()}")
        print(f"ERMAK_AUDIT_JOB={data['batch']} mode=verify_only")
        return

    before = changed_paths()
    if before:
        die(f"Working tree must be clean before transform: {before}")

    for script in data.get("transformers", []):
        run_python(script)

    actual = changed_paths()
    expected = sorted(data.get("allowed_changed", DEFAULT_ALLOWED_CHANGED))
    if actual != expected:
        die(f"Unexpected changed paths. expected={expected} actual={actual}")
    assert_asset_mirror()
    subprocess.run(["git", "diff", "--check"], cwd=ROOT, check=True)
    print(f"ERMAK_AUDIT_JOB={data['batch']} transformed_paths={','.join(actual)}")


def cmd_verify(data: dict) -> None:
    assert_asset_mirror()
    for script in data.get("qa", []):
        run_python(script)

    subprocess.run(["git", "diff", "--check"], cwd=ROOT, check=True)
    count = strict_generic_count()
    max_generic = data.get("max_generic")
    if max_generic is not None:
        if not isinstance(max_generic, int) or max_generic < 0:
            die("max_generic must be a non-negative integer")
        if count > max_generic:
            die(f"Strict generic count {count} exceeds manifest max {max_generic}")
    print(f"ERMAK_STRICT_GENERIC_ROUTES={count}")
    print(f"ERMAK_AUDIT_VERIFY_PASS={data['batch']}")


def cmd_stage(data: dict) -> None:
    if data["mode"] != "apply_and_commit":
        die("stage is only valid for apply_and_commit")
    expected = sorted(data.get("allowed_changed", DEFAULT_ALLOWED_CHANGED))
    actual = changed_paths()
    if actual != expected:
        die(f"Refusing to stage unexpected tree. expected={expected} actual={actual}")
    subprocess.run(["git", "add", "--", *expected], cwd=ROOT, check=True)
    staged = subprocess.check_output(
        ["git", "diff", "--cached", "--name-only"], cwd=ROOT, text=True
    ).splitlines()
    if sorted(staged) != expected:
        die(f"Unexpected staged files: {staged}")
    msgfile = ROOT / ".git" / "ERMAK_AUDIT_COMMIT_MESSAGE"
    msgfile.write_text(data["commit_message"].strip() + "\n", encoding="utf-8")
    print(f"ERMAK_AUDIT_STAGE_PASS={data['batch']}")


def cmd_info(data: dict) -> None:
    print(json.dumps({
        "batch": data["batch"],
        "mode": data["mode"],
        "run_android": bool(data.get("run_android", True)),
        "max_generic": data.get("max_generic"),
    }, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["info", "apply", "verify", "stage"])
    args = parser.parse_args()
    data = load_manifest()
    {"info": cmd_info, "apply": cmd_apply, "verify": cmd_verify, "stage": cmd_stage}[args.command](data)


if __name__ == "__main__":
    main()
