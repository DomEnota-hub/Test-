#!/usr/bin/env python3
"""Fresh-tree Android smoke for the dev14 critical user paths."""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "ru.railbrake.calculator"

def adb(*args):
    return subprocess.check_output(["adb", *args])

def tree():
    data = adb("exec-out", "uiautomator", "dump", "/dev/tty")
    return ET.fromstring(data)

def text(node):
    return node.get("text") or node.get("content-desc") or ""

def labels(root):
    return [text(node) for node in root.iter("node") if text(node)]

def wait_for(value, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        root = tree()
        if value in labels(root):
            return root
        time.sleep(.5)
    raise AssertionError(f"Missing UI text: {value}")

def tap(value):
    root = tree()
    parents = {child: parent for parent in root.iter() for child in parent}
    node = next((node for node in root.iter("node") if text(node) == value), None)
    if node is None:
        raise AssertionError(f"Missing tap target: {value}")
    while node.get("clickable") != "true" and node in parents:
        node = parents[node]
    if node.get("clickable") != "true":
        raise AssertionError(f"No clickable ancestor: {value}")
    nums = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
    adb("shell", "input", "tap", str((nums[0] + nums[2]) // 2), str((nums[1] + nums[3]) // 2))

def open_screen(title):
    tap("☰")
    wait_for(title)
    tap(title)
    wait_for(title)

apk = os.environ["APK"]
subprocess.run(["adb", "install", "-r", apk], check=True)
adb("logcat", "-c")
adb("shell", "am", "force-stop", PACKAGE)
adb("shell", "monkey", "-p", PACKAGE, "1")
wait_for("Железнодорожный помощник")

open_screen("Приёмка")
wait_for("Полная приёмка")
tap("Полная приёмка")
wait_for("Пошаговая приёмка")
wait_for("Перед переходом выберите результат проверки этого пункта")
tap("Проверено")
wait_for("Состояние: Проверено")

open_screen("Диагностика")
wait_for("Диагностика ВЛ80С")
tap("Ермак")
wait_for("Техническая база Ермак")
tap("ВЛ80С")
wait_for("Диагностика ВЛ80С")

log = adb("logcat", "-d").decode("utf-8", "replace")
if "FATAL EXCEPTION" in log:
    raise AssertionError("Android crash in logcat")
print("PASS dev14 acceptance state and family switching")
