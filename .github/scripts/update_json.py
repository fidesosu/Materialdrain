#!/usr/bin/env python3
"""
Writes update.json, which the app reads to find out about a new version (see app/.../update/UpdateChecker.kt), and which
the build workflow publishes next to the APK of every release, stable and dev alike.

Usage: update_json.py <apk> <output-metadata.json> <out>

- versionName/versionCode: the APK's, from the build's output-metadata.json. The app offers the release when its
  versionCode is higher than its own (the commit's minutes since 2025, so it only goes up, see app/build.gradle.kts).
- sha256/size: of the APK, which the app checks before handing it to the installer.
- changes: the commits that change what the app does (its code and resources, the hosts', the bundled configs), newest
  first, each as the first paragraph of its message, with the commit's time. The app shows those newer than its own
  build: everything it would gain. Tests, the workflows and docs aren't changes of the app. A commit with a
  "Changes: none" line in its message is left out too.
"""
import hashlib
import json
import os
import subprocess
import sys

MAX_CHANGES = 150
CHANGE_PATHS = [
    ":(glob)app/src/main/**",
    ":(glob)provider-*/src/main/**",
    ":(glob)docs/provider-configs/**",
    ":(exclude,glob)**/*.md",
]


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True, encoding="utf-8").stdout


def changes() -> list[dict]:
    # Each commit as "<time>\x1f<message>\x1e": separators which no commit message has
    log = git("log", "--no-merges", f"-{MAX_CHANGES}", "--format=%ct%x1f%B%x1e", "HEAD", "--", *CHANGE_PATHS)
    result = []
    for entry in log.split("\x1e"):
        entry = entry.strip()
        if not entry:
            continue
        time, message = entry.split("\x1f", 1)
        if any(line.strip().lower() == "changes: none" for line in message.splitlines()):
            continue
        # The first paragraph, as one line: the rest (and the Co-Authored-By lines) is for developers
        text = " ".join(message.strip().split("\n\n", 1)[0].split())
        if text:
            result.append({"time": int(time), "text": text})
    return result


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
        "changes": changes(),
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(update, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
