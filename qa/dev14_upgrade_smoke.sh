#!/usr/bin/env bash
set -euo pipefail

out="${QA_OUT:-qa-upgrade-evidence}"
mkdir -p "$out"

adb install "$LEGACY_APK"
adb install -r "$DEV14_APK"
adb shell dumpsys package ru.railbrake.calculator > "$out/package.txt"
grep -Fq 'versionCode=142' "$out/package.txt"

adb logcat -c
adb shell am force-stop ru.railbrake.calculator
adb shell monkey -p ru.railbrake.calculator 1 > "$out/launch.txt"
sleep 3
adb exec-out uiautomator dump /dev/tty > "$out/home.xml"
grep -Fq 'Железнодорожный помощник' "$out/home.xml"
adb exec-out screencap -p > "$out/home-after-upgrade.png"
adb logcat -d > "$out/logcat.txt"
! grep -Fq 'FATAL EXCEPTION' "$out/logcat.txt"
