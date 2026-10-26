#!/usr/bin/env bash
# Starts the target's dedicated server with SMPUTILITIES_SMOKE=1 (see SmokeTest):
# it loads every mixin target, runs a few commands and stops. Fails when the
# server crashes, a mixin does not apply, or a command throws.
#
# Most targets run Loom's dev server. Loom's Forge 1.21.11 dev jar pairs the
# patched ByteBufCodecs with vanilla's inner classes, so Minecraft fails before
# any mod loads; that target installs a real Forge server and runs the built
# jar from mods/ instead.
#
# Fabric targets also run tools/client-smoke/bot.js (needs node and an
# `npm install` there): a vanilla client joins and checks the chunks it gets,
# including what the anti-xray hides (SMPUTILITIES_SMOKE=client).
set -uo pipefail
target="$1"
case "$target" in
    1.21.11-forge) mode=installed ;;
    *) mode=dev ;;
esac
mc=$(sed -n 's/^minecraft_version=//p' "versions/$target/gradle.properties")
smoke=1
case "$target" in
    *-fabric) [ -d tools/client-smoke/node_modules ] && node tools/client-smoke/bot.js --check "$mc" && smoke=client ;;
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
server-port=25565
enforce-secure-profile=false
PROPS
if [ "$mode" = dev ]; then
    SMPUTILITIES_SMOKE=$smoke timeout 900 ./gradlew "-Psu.targets=$target" -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64 ":$target:runServer" > smoke.log 2>&1 &
    server=$!
    client_status=0
    if [ "$smoke" = client ]; then
        # The server prints READY once its own checks passed and it waits for the bot.
        while kill -0 "$server" 2>/dev/null && ! grep -q "SMPUTILITIES SMOKE READY" smoke.log; do
            sleep 2
        done
        if grep -q "SMPUTILITIES SMOKE READY" smoke.log; then
            echo "== client smoke test ($mc)"
            timeout 240 node tools/client-smoke/bot.js "$mc" 25565
            client_status=$?
            if [ "$client_status" != 0 ]; then
                echo "== client smoke test failed (exit $client_status)"
            fi
        fi
    fi
    wait "$server"
    status=$?
    if [ "$client_status" != 0 ]; then
        grep -E "SMPUTILITIES SMOKE|Exception|Error|Caused by|^\s+at com\.arthou" smoke.log | head -60
        exit 1
    fi
else
    forge=$(sed -n 's/^forge_version=//p' "versions/$target/gradle.properties")
    installer="$run/installer.jar"
    curl -fsSL --retry 3 -o "$installer" "https://maven.minecraftforge.net/net/minecraftforge/forge/$mc-$forge/forge-$mc-$forge-installer.jar" || exit 1
    (cd "$run" && java -jar installer.jar --installServer . > install.log 2>&1) || { tail -40 "$run/install.log"; exit 1; }
    mkdir -p "$run/mods"
    cp dist/*.jar "$run/mods/"
    (cd "$run" && SMPUTILITIES_SMOKE=1 timeout 900 bash run.sh nogui) > smoke.log 2>&1
    status=$?
fi
grep -E "SMPUTILITIES SMOKE|Anti-xray self test|Mixin|mixin|Exception|Error|Caused by" smoke.log | grep -vE "^\s+at |DEBUG|unsupported|is disabled|(No|0) (errors|exceptions)" | head -60
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
