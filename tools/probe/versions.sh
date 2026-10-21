#!/usr/bin/env bash
# Prints the newest published loader and tool versions for every supported Minecraft version.
# Used by the probe workflow; the output only goes to the job log.
set -u
MCS="1.18.2 1.19.2 1.19.4 1.20.1 1.20.4 1.20.6 1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11"
meta() { curl -fsSL --retry 3 "$1" | grep -oE "<version>[^<]+</version>" | sed 's/<[^>]*>//g'; }

echo "### FORGE (newest per MC)"
meta https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml > forge.txt
for mc in $MCS; do echo "forge $mc: $(grep -E "^$mc-[0-9.]+$" forge.txt | sort -V | tail -1)"; done
echo "### FORGE PROMOS"
curl -fsSL https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json | python3 -c '
import json,sys
promos=json.load(sys.stdin)["promos"]
for mc in sys.argv[1].split():
    print(mc, promos.get(mc+"-recommended"), promos.get(mc+"-latest"))' "$MCS"

echo "### NEOFORGE (newest per line)"
meta https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml > neo.txt
for p in 20.4 20.6 21.1 21.3 21.4 21.5 21.8 21.10 21.11; do echo "neoforge $p: $(grep -E "^$p\.[0-9]+(-beta)?$" neo.txt | sort -V | tail -1)"; done

echo "### FABRIC LOADER"
curl -fsSL https://meta.fabricmc.net/v2/versions/loader | python3 -c 'import json,sys; print([v["version"] for v in json.load(sys.stdin) if v["stable"]][:3])'
echo "### FABRIC API (newest per MC)"
meta https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml > fapi.txt
for mc in $MCS; do echo "fabric-api $mc: $(grep -E "\+$mc$" fapi.txt | sort -V | tail -1)"; done

echo "### ARCHITECTURY LOOM (newest 12, sorted)"
meta https://maven.architectury.dev/dev/architectury/loom/dev.architectury.loom.gradle.plugin/maven-metadata.xml | sort -V | tail -12 | tr '\n' ' '; echo
meta https://maven.architectury.dev/dev/architectury/architectury-loom/maven-metadata.xml | sort -V | tail -12 | tr '\n' ' '; echo
curl -fsSL https://maven.architectury.dev/dev/architectury/architectury-loom/1.17-SNAPSHOT/maven-metadata.xml | grep -oE "<(timestamp|buildNumber|value)>[^<]+" | head -6
echo "### MODDEVGRADLE (newest 5)"
meta https://maven.neoforged.net/releases/net/neoforged/moddev/net.neoforged.moddev.gradle.plugin/maven-metadata.xml | sort -V | tail -5 | tr '\n' ' '; echo
echo "### FABRIC LOOM (newest 5)"
meta https://maven.fabricmc.net/fabric-loom/fabric-loom.gradle.plugin/maven-metadata.xml | sort -V | tail -5 | tr '\n' ' '; echo

echo "### VOICECHAT API"
meta https://maven.maxhenkel.de/repository/public/de/maxhenkel/voicechat/voicechat-api/maven-metadata.xml | sort -V | tail -5 | tr '\n' ' '; echo
echo "### FABRIC PERMISSIONS API"
meta https://repo1.maven.org/maven2/me/lucko/fabric-permissions-api/maven-metadata.xml | tail -8
echo "### MIXINEXTRAS"
meta https://repo1.maven.org/maven2/io/github/llamalad7/mixinextras-common/maven-metadata.xml | tail -4
echo "### DONE"
