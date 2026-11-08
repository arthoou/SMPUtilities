#!/usr/bin/env bash
# End-to-end test with a client running the mod: starts the target's dev server
# (SMPUTILITIES_SMOKE=modclient, see SmokeTest) and a dev client named
# SmokeClient on a virtual display (see ClientSmoke). The server runs /tts as
# that player and voices a TTS line for it; the client must open the TTS menu,
# keep it open and receive the line. Needs xvfb-run.
#
# With "installed" as the second argument the server is a real one (the
# loader's installer or launcher, Fabric API from its Maven) running the built
# jar from mods/, as players run it, instead of the dev server.
set -uo pipefail
target="$1"
mode="${2:-dev}"
props="versions/$target/gradle.properties"
prop() { sed -n "s/^$1=//p" "$props"; }
mc=$(prop minecraft_version)
platform=$(prop loom.platform)
if [ "$mode" = installed ]; then
    server_dir="build/e2e-server/$target"
else
    server_dir="versions/$target/run"
fi
client_dir="versions/$target/run-client"
mkdir -p "$server_dir" "$client_dir"
echo "eula=true" > "$server_dir/eula.txt"
cat > "$server_dir/server.properties" <<'PROPS'
online-mode=false
level-type=flat
generate-structures=false
spawn-protection=0
max-tick-time=-1
view-distance=4
simulation-distance=4
server-port=25565
enforce-secure-profile=false
PROPS
# No first-launch screens, no sound device, small window.
cat > "$client_dir/options.txt" <<'OPTS'
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
narrator:0
renderDistance:2
simulationDistance:5
soundCategory_master:0.0
OPTS
gradle=(./gradlew "-Psu.targets=$target" -Porg.gradle.java.installations.fromEnv=JAVA_HOME_17_X64,JAVA_HOME_21_X64)
# Compile once, so the server and the client do not build at the same time.
"${gradle[@]}" ":$target:build" > e2e-build.log 2>&1 || { tail -40 e2e-build.log; exit 1; }
if [ "$mode" = installed ]; then
    java_home_var="JAVA_HOME_$(prop java_version)_X64"
    java="${!java_home_var}/bin/java"
    mkdir -p "$server_dir/mods"
    cp dist/*.jar "$server_dir/mods/"
    fetch() { curl -fsSL --retry 3 -o "$1" "$2" || { echo "== download failed: $2"; exit 1; }; }
    case "$platform" in
        fabric)
            installer=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["version"])')
            fetch "$server_dir/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$mc/$(prop fabric_loader_version)/$installer/server/jar"
            api=$(prop fabric_api_version)
            fetch "$server_dir/mods/fabric-api.jar" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$api/fabric-api-$api.jar"
            start=("$java" -Xmx2G -jar server.jar nogui)
            ;;
        forge)
            fetch "$server_dir/installer.jar" "https://maven.minecraftforge.net/net/minecraftforge/forge/$mc-$(prop forge_version)/forge-$mc-$(prop forge_version)-installer.jar"
            ;;
        neoforge)
            fetch "$server_dir/installer.jar" "https://maven.neoforged.net/releases/net/neoforged/neoforge/$(prop neoforge_version)/neoforge-$(prop neoforge_version)-installer.jar"
            ;;
    esac
    if [ "$platform" != fabric ]; then
        (cd "$server_dir" && "$java" -jar installer.jar --installServer . > install.log 2>&1) || { tail -40 "$server_dir/install.log"; exit 1; }
        if [ -f "$server_dir/run.sh" ]; then
            start=(env "PATH=$(dirname "$java"):$PATH" bash run.sh nogui)
        else
            start=("$java" -Xmx2G -jar "$(cd "$server_dir" && ls forge-*.jar | grep -v installer | head -1)" nogui)
        fi
    fi
    (cd "$server_dir" && SMPUTILITIES_SMOKE=modclient timeout 900 "${start[@]}") > e2e-server.log 2>&1 &
else
    SMPUTILITIES_SMOKE=modclient timeout 900 "${gradle[@]}" ":$target:runServer" > e2e-server.log 2>&1 &
fi
server=$!
while kill -0 "$server" 2>/dev/null && ! grep -q "SMPUTILITIES SMOKE READY" e2e-server.log; do
    sleep 2
done
if ! grep -q "SMPUTILITIES SMOKE READY" e2e-server.log; then
    echo "== server did not start"; tail -60 e2e-server.log; exit 1
fi
case "$mc" in
    1.18*|1.19*) join=(--server localhost --port 25565) ;;
    *) join=(--quickPlayMultiplayer localhost:25565) ;;
esac
SMPUTILITIES_SMOKE=modclient timeout 600 xvfb-run -a -s "-screen 0 1280x720x24" \
    "${gradle[@]}" ":$target:runClient" --args="--username SmokeClient ${join[*]}" > e2e-client.log 2>&1
client=$?
wait "$server"
echo "== server"
grep -E "SMPUTILITIES SMOKE" e2e-server.log | head -20
echo "== client (exit $client)"
grep -E "SMPUTILITIES CLIENT SMOKE|Exception|ERROR" e2e-client.log | grep -vE "^\s+at |OpenAL|sound|Sound|narrator|Narrator" | head -40
if ! grep -q "SMPUTILITIES CLIENT SMOKE OK" e2e-client.log; then
    echo "== client end-to-end test failed"
    tail -60 e2e-client.log
    exit 1
fi
echo "== client end-to-end test passed ($target, $mode server)"
