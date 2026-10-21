#!/usr/bin/env bash
# Starts the target's dedicated server with SMPUTILITIES_SMOKE=1 (see SmokeTest):
# it loads every mixin target, runs a few commands and stops. Fails when the
# server crashes, a mixin does not apply, or a command throws.
#
# Most targets run Loom's dev server. Loom's Forge 1.21.11 dev jar pairs the
# patched ByteBufCodecs with vanilla's inner classes, so Minecraft fails before
# any mod loads; that target installs a real Forge server and runs the built
# jar from mods/ instead.
set -uo pipefail
target="$1"
case "$target" in
    1.21.11-forge) mode=installed ;;
    *) mode=dev ;;
esac
if [ "$mode" = dev ]; then
    run="versions/$target/run"
else
    run="build/smoke-server/$target"
fi
mkdir -p "$run"
echo "eula=true" > "$run/eula.txt"
cat > "$run/server.properties" <<'PROPS'
online-mode=false
level-type=flat
generate-structures=false
spawn-protection=0
max-tick-time=-1
sync-chunk-writes=false
view-distance=4
simulation-distance=4
PROPS
if [ "$mode" = dev ]; then
    SMPUTILITIES_SMOKE=1 timeout 900 ./gradlew "-Psu.targets=$target" -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64 ":$target:runServer" > smoke.log 2>&1
    status=$?
else
    mc=$(sed -n 's/^minecraft_version=//p' "versions/$target/gradle.properties")
    forge=$(sed -n 's/^forge_version=//p' "versions/$target/gradle.properties")
    installer="$run/installer.jar"
    curl -fsSL --retry 3 -o "$installer" "https://maven.minecraftforge.net/net/minecraftforge/forge/$mc-$forge/forge-$mc-$forge-installer.jar" || exit 1
    (cd "$run" && java -jar installer.jar --installServer . > install.log 2>&1) || { tail -40 "$run/install.log"; exit 1; }
    mkdir -p "$run/mods"
    cp dist/*.jar "$run/mods/"
    (cd "$run" && SMPUTILITIES_SMOKE=1 timeout 900 bash run.sh nogui) > smoke.log 2>&1
    status=$?
fi
grep -E "SMPUTILITIES SMOKE|Mixin|mixin|Exception|Error|Caused by" smoke.log | grep -vE "^\s+at |DEBUG|unsupported|is disabled|(No|0) (errors|exceptions)" | head -60
if ! grep -q "SMPUTILITIES SMOKE OK" smoke.log; then
    echo "== smoke test failed (exit $status)"
    tail -80 smoke.log
    exit 1
fi
if grep -qE "Command exception|Unexpected error while running command|InvalidInjectionException|MixinApplyError|MixinTransformerError" smoke.log; then
    echo "== smoke test logged errors"
    exit 1
fi
echo "== smoke test passed ($mode server)"
