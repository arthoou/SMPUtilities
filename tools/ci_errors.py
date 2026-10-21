#!/usr/bin/env python3
"""Prints the compiler errors of a Gradle log compactly, with paths mapped back
to the shared sources (build/preprocessed/java/... -> mod/src/.../java/...)."""
import os
import re
import sys

log = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
roots = ["core/src/main/java", "mod/src/main/java", "mod/src/fabric/java", "mod/src/forge/java", "mod/src/neoforge/java"]

def source_path(path):
    match = re.search(r"build/preprocessed/java/(.+\.java)", path)
    if not match:
        return path
    relative = match.group(1)
    for root in roots:
        if os.path.exists(os.path.join(root, relative)):
            return os.path.join(root, relative)
    return relative

errors = 0
for i, line in enumerate(log):
    match = re.match(r"^(/\S+\.java):(\d+): (error|warning): (.*)$", line)
    if match and match.group(3) == "error":
        errors += 1
        print(f"{source_path(match.group(1))}:{match.group(2)}: {match.group(4)}")
        for extra in log[i + 1:i + 3]:
            if extra.strip() and not re.match(r"^/\S+\.java:\d+:", extra):
                print("    " + extra.strip()[:200])
for i, line in enumerate(log):
    if "What went wrong" in line:
        print("\n".join(log[i:i + 12]))
print(f"== {errors} compiler error(s)")
