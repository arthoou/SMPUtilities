#!/usr/bin/env python3
"""Merges build/api.txt of several targets and prints, per class, the members
that are not present in every version, with the versions that have them."""
import re
import sys

def version_key(v):
    return tuple(int(x) for x in v.split("."))

def short(sig):
    sig = re.sub(r"\b(net\.minecraft|com\.mojang|java\.util|java\.lang|java\.util\.function)\.([a-z]+\.)*", "", sig)
    return sig.replace("public ", "").replace("final ", "").replace("abstract ", "")

data = {}
versions = []
for path in sys.argv[1:]:
    match = re.search(r"versions/([0-9.]+)-[a-z]+/build/api\.txt", path)
    version = match.group(1)
    versions.append(version)
    current = None
    for line in open(path):
        line = line.rstrip("\n")
        if line.startswith("### "):
            current = line[4:]
            data.setdefault(current.replace(" (missing)", ""), {})
            if current.endswith("(missing)"):
                data[current.replace(" (missing)", "")].setdefault("<class missing>", set()).add(version)
            current = current.replace(" (missing)", "")
        elif line and current:
            data[current].setdefault(short(line), set()).add(version)
versions.sort(key=version_key)

def ranges(present):
    out, start, prev = [], None, None
    for v in versions:
        if v in present:
            start = start or v
            prev = v
        elif start:
            out.append(start if start == prev else f"{start}..{prev}")
            start = None
    if start:
        out.append(start if start == prev else f"{start}..{prev}")
    return ",".join(out)

print("versions: " + " ".join(versions))
for cls in data:
    members = data[cls]
    changing = {m: v for m, v in members.items() if len(v) != len(versions)}
    common = len(members) - len(changing)
    print(f"## {cls}  (same everywhere: {common})")
    for member in sorted(changing, key=lambda m: (min(version_key(v) for v in changing[m]), m)):
        print(f"  [{ranges(changing[member])}] {member}")
