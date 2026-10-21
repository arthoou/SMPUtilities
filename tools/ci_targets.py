#!/usr/bin/env python3
"""Prints targets=<json list> for the CI matrix.

Argument: comma-separated targets, "all", or empty to use .github/ci-targets.txt
(one target per line, '#' comments, or the single word "all").
"""
import json
import os
import sys

available = sorted(d for d in os.listdir("versions") if os.path.isfile(os.path.join("versions", d, "gradle.properties")))
requested = sys.argv[1].strip() if len(sys.argv) > 1 else ""
if not requested:
    with open(".github/ci-targets.txt") as handle:
        lines = [line.split("#", 1)[0].strip() for line in handle]
    requested = ",".join(line for line in lines if line)
names = [name.strip() for name in requested.split(",") if name.strip()]
selected = available if names in ([], ["all"]) else names
unknown = [name for name in selected if name not in available]
if unknown:
    sys.exit("unknown targets: " + ", ".join(unknown))
print("targets=" + json.dumps(selected))
