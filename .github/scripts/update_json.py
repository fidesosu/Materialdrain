#!/usr/bin/env python3
"""
Writes update.json, which the app reads to find out about a new version (see app/.../update/AppUpdates.kt), and which
the build workflow publishes next to the APK of every release, stable and dev alike.

Usage: update_json.py <apk> <output-metadata.json> <out>

- versionName/versionCode: the APK's, from the build's output-metadata.json. The app offers the release when its
  versionCode is higher than its own (the commit's minutes since 2025, so it only goes up, see app/build.gradle.kts).
- sha256/size: of the APK, which the app checks before handing it to the installer.
- changes: the commits that change what the app does, newest first, each as the first paragraph of its message with the
  commit's time, as the APK has them itself (assets/changes.json, see ChangesTask in app/build.gradle.kts, which says
  which commits count). The app shows those newer than its own build: everything it would gain.
"""
import hashlib
import json
import os
import subprocess
import sys
import zipfile

def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8").stdout


def changes(apk: str) -> list[dict]:
    """The APK's own list of changes (assets/changes.json, made by the build: see ChangesTask in app/build.gradle.kts)."""
    with zipfile.ZipFile(apk) as archive:
        try:
            return json.loads(archive.read("assets/changes.json").decode("utf-8"))["changes"]
        except KeyError:
            return []


def main() -> None:
    apk, metadata, out = sys.argv[1:4]
    element = json.load(open(metadata, encoding="utf-8"))["elements"][0]
    with open(apk, "rb") as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    update = {
        "versionName": element["versionName"],
        "versionCode": element["versionCode"],
        "commitTime": int(git("log", "-1", "--format=%ct").strip()),
        "apk": os.path.basename(apk),
        "sha256": digest,
        "size": os.path.getsize(apk),
        "changes": changes(apk),
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(update, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
