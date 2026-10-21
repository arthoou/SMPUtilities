#!/usr/bin/env python3
"""Prints the compiler errors of a Gradle log, one line each, with paths mapped
back to the shared sources (build/preprocessed/java/... -> mod/src/.../java/...).

Format: path:line: message | source line | symbol (when javac names one)."""
import os
import re
import sys

log = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
roots = ["core/src/main/java", "mod/src/main/java", "mod/src/fabric/java", "mod/src/forge/java", "mod/src/neoforge/java"]
ERROR = re.compile(r"^\s*(/\S+\.java):(\d+): error: (.*)$")


def source_path(path):
    match = re.search(r"build/preprocessed/java/(.+\.java)", path)
    if not match:
        return path
    relative = match.group(1)
    for root in roots:
        if os.path.exists(os.path.join(root, relative)):
            return os.path.join(root, relative)
    return relative


seen = set()
lines = []
for i, line in enumerate(log):
    match = ERROR.match(line)
    if not match:
        continue
    path, number, message = source_path(match.group(1)), match.group(2), match.group(3).strip()
    key = (path, number, message)
    if key in seen:
        continue
    seen.add(key)
    code = ""
    symbol = ""
    for extra in log[i + 1:i + 6]:
        text = extra.strip()
        if ERROR.match(extra):
            break
        if text.startswith("symbol:"):
            symbol = text[len("symbol:"):].strip()
        elif text.startswith("location:"):
            symbol += " in " + text[len("location:"):].strip()
        elif not code and text and set(text) != {"^"}:
            code = text
    parts = [f"{path.replace('mod/src/main/java/com/arthou/smputilities/', '')}:{number}: {message[:140]}", code[:150]]
    if symbol:
        parts.append(symbol[:120])
    lines.append(" | ".join(parts))

if lines:
    print("\n".join(sorted(lines)))
else:
    for i, line in enumerate(log):
        if "What went wrong" in line:
            print("\n".join(log[i:i + 15]))
            break
print(f"== {len(lines)} compiler error(s)")
