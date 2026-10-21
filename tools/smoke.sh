#!/usr/bin/env bash
# Starts the target's dedicated server with SMPUTILITIES_SMOKE=1 (see SmokeTest):
# it loads every mixin target, runs a few commands and stops. Fails when the
# server crashes, a mixin does not apply, or a command throws.
set -uo pipefail
target="$1"
run="versions/$target/run"
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
SMPUTILITIES_SMOKE=1 timeout 900 ./gradlew "-Psu.targets=$target" -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64 ":$target:runServer" > smoke.log 2>&1
status=$?
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
echo "== smoke test passed"
