#!/usr/bin/env bash
set -euo pipefail
apk="$(find "$GITHUB_WORKSPACE/emulator-input" -name '*.apk' -print -quit)"
test -n "$apk"
mkdir -p "$GITHUB_WORKSPACE/qa-evidence"
git -C "$GITHUB_WORKSPACE" rev-parse HEAD > "$GITHUB_WORKSPACE/qa-evidence/source-sha.txt"
APK="$apk" QA_OUT="$GITHUB_WORKSPACE/qa-evidence" \
  python3 "$GITHUB_WORKSPACE/qa/tem2_android_smoke.py"
