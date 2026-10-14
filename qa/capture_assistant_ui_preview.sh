#!/usr/bin/env bash
set -euo pipefail
PACKAGE="ru.railbrake.calculator.uipreview"
COMPONENT="$PACKAGE/ru.railbrake.calculator.MainActivity"
APK="$GITHUB_WORKSPACE/buildsrc/RailBrakeCalculator/app/build/outputs/apk/debug/app-debug.apk"
OUT="$GITHUB_WORKSPACE/qa-ui-evidence"
mkdir -p "$OUT"

adb install -r "$APK"
adb shell settings put system font_scale 1.0
adb shell wm size > "$OUT/device.txt"
adb shell wm density >> "$OUT/device.txt"

capture_theme() {
  local theme="$1"
  adb shell cmd uimode night "$theme"
  adb shell am force-stop "$PACKAGE" || true
  adb shell pm clear "$PACKAGE" > /dev/null
  adb shell am start -W -n "$COMPONENT"
  sleep 9
  adb shell uiautomator dump /sdcard/ui-dump.xml >/dev/null
  adb exec-out cat /sdcard/ui-dump.xml > "$OUT/$theme-home.xml"
  grep -q 'Расчёты' "$OUT/$theme-home.xml"
  grep -q 'Помощник' "$OUT/$theme-home.xml"
  adb exec-out screencap -p > "$OUT/$theme-home.png"
  adb shell input swipe 510 1730 510 540 430
  sleep 2
  adb exec-out screencap -p > "$OUT/$theme-home-scrolled.png"
}
capture_theme yes
capture_theme no
adb logcat -d -t 600 > "$OUT/logcat-tail.txt" || true
echo "Captured real Android home screenshots in both themes."
