#!/usr/bin/env bash
# CI client smoke test: installs a real NeoForge client with the official installer, adds Fabricated and the Fabric
# test mod, and starts it headlessly (Xvfb) with HeadlessMC. mc-runtime-test's client mod creates a singleplayer
# world, waits until the player's chunk is loaded and quits; the test then checks the client-side test markers
# (client entrypoint, client lifecycle, client resource listener, a configuration task and a networking round trip with
# the integrated server).
#
# Usage: ci/smoke-client.sh <minecraft-version> <neoforge-version> <work-dir> [extra-mods-dir]
# Expects: build/libs/fabricated-*+mc<minecraft-version>.jar and mc/<minecraft-version>/build/testmods/*.jar,
# JAVA_HOME pointing at the Java version the game needs, xvfb-run and gh (with GH_TOKEN) on the PATH.
set -euo pipefail

mc="$1"
neo="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
timeout_seconds="${SMOKE_TIMEOUT:-900}"
hmc_version="2.10.0"
mcrt_version="4.5.2"

rm -rf "$work"
mkdir -p "$work/HeadlessMC" "$work/run/mods" "$work/run/config" "$work/minecraft"
cd "$work"

echo "::group::Install NeoForge $neo (client)"
# The installer only checks that a launcher profile file exists.
echo '{"profiles":{}}' > minecraft/launcher_profiles.json
curl -fsSL --retry 4 -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/${neo}/neoforge-${neo}-installer.jar"
java -jar installer.jar --installClient minecraft > installer.log 2>&1 || { cat installer.log; exit 1; }
ls minecraft/versions
echo "::endgroup::"

echo "::group::Get HeadlessMC and mc-runtime-test"
curl -fsSL --retry 4 -o hmc.jar "https://github.com/3arthqu4ke/headlessmc/releases/download/${hmc_version}/headlessmc-launcher-${hmc_version}.jar"
gh release download "$mcrt_version" -R headlesshq/mc-runtime-test -D run/mods -p "mc-runtime-test-${mc}-*-neoforge-release.jar"
cat > HeadlessMC/config.properties <<PROPS
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$work/run
hmc.mcdir=$work/minecraft
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.assets.dummy=true
PROPS
echo "::endgroup::"

cp "$root"/build/libs/fabricated-*+mc"$mc".jar run/mods/
# Only the working test mod: the deliberately broken ones would put NeoForge's warning screen in front of the game.
cp "$root"/mc/"$mc"/build/testmods/fabricated-testmod.jar run/mods/
if [ -n "$extra" ] && [ -d "$extra" ]; then
	cp "$extra"/*.jar run/mods/ 2>/dev/null || true
fi
ls -1 run/mods
if [ -n "$extra" ]; then
	# With extra mods, a mod's failing entrypoint is reported and the game goes on, as the server smoke test does.
	echo '{ "schema": 1, "onEntrypointFailure": "disable", "logLevel": "DEBUG" }' > run/config/fabricated.json
else
	echo '{ "schema": 1, "logLevel": "DEBUG" }' > run/config/fabricated.json
fi
cat > run/options.txt <<'OPTS'
onboardAccessibility:false
pauseOnLostFocus:false
OPTS

echo "::group::Run client"
status=0
# A 24-bit screen with GLX: xvfb-run's default 8-bit screen has no OpenGL visual. 26.3 opens its window with SDL3,
# whose GLX visual request Xvfb can't match; EGL (Mesa's software renderer) works there, so SDL is told to use it.
export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
timeout "$timeout_seconds" xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset" java -Dhmc.check.xvfb=true -jar hmc.jar \
	--command launch "neoforge-${neo}" -regex --jvm "-Djava.awt.headless=true -DMcRuntimeGameTest=false" \
	> client.out 2>&1 &
client_pid=$!
dumped=0
# The game doesn't exit when no renderer can be created; stop it then instead of waiting for the timeout.
while kill -0 "$client_pid" 2>/dev/null; do
	# Shortly before the timeout, record what every game thread is doing, so a hang can be diagnosed from the log.
	if [ "$dumped" -eq 0 ] && [ "$SECONDS" -ge $((timeout_seconds - 45)) ]; then
		dumped=1
		for pid in $(pgrep -f -- "-DMcRuntimeGameTest=false" || true); do
			"$JAVA_HOME/bin/jstack" "$pid" >> thread-dump.txt 2>&1 || true
		done
	fi
	# After a crash or a fatal loading error the game shows its error window until closed; stop it once the crash report
	# is written.
	if grep -qE "Preparing crash report|Game crashed!|Crash report saved to|Failed to wait for future|Mixin prepare for mod .* failed" client.out run/logs/latest.log 2>/dev/null; then
		echo "The game crashed; stopping the client."
		sleep 15
		pkill -P "$client_pid" 2>/dev/null || true
		kill "$client_pid" 2>/dev/null || true
		break
	fi
	if grep -q "Failed to create backend OpenGL" client.out && grep -q "Failed to create backend Vulkan" client.out; then
		echo "No render backend could be created; stopping the client."
		sleep 5
		pkill -P "$client_pid" 2>/dev/null || true
		kill "$client_pid" 2>/dev/null || true
		break
	fi
	sleep 2
done
wait "$client_pid" || status=$?
echo "::endgroup::"

log=run/logs/latest.log
[ -f "$log" ] || log=client.out
echo "::group::Client output (tail)"
tail -n 300 client.out || true
echo "::endgroup::"
if [ "$status" -ne 0 ]; then
	echo "::group::Game log without the loading-screen spam (last 250 lines)"
	grep -v "Waiting for player to load" "$log" | tail -n 250 || true
	echo "::endgroup::"
fi
if [ "$status" -ne 0 ]; then
	echo "::group::First errors in the game log"
	grep -n -m 80 -E "/(ERROR|FATAL)\]|Exception|Caused by" "$log" || true
	echo "::endgroup::"
	if [ -n "$extra" ]; then
		echo "::group::Where the Fabric API classes the game could not find come from"
		python3 -I "$root/ci/find-classes.py" "$log" run/mods || true
		echo "::endgroup::"
	fi
fi
for report in run/crash-reports/*.txt; do
	[ -f "$report" ] || continue
	echo "::group::Crash report $(basename "$report") (first 120 lines)"
	head -n 120 "$report"
	echo "::endgroup::"
done
if [ -f thread-dump.txt ]; then
	echo "::group::Thread dump before the timeout (game threads)"
	# Paragraphs (one per thread) for the threads that matter for a stuck world join.
	awk 'BEGIN { RS = ""; ORS = "\n\n" } /^"(Render thread|Server thread|Netty|Local|IO-Worker|Worker-Main)/' thread-dump.txt || true
	echo "::endgroup::"
fi
echo "::group::fabric-bridge-report.txt"
cat run/logs/fabric-bridge-report.txt || echo "(report missing)"
echo "::endgroup::"

fail=0
check() {
	if grep -q -- "$1" "$log" client.out 2>/dev/null; then echo "PASS: $2"; else echo "FAIL: $2 (missing '$1')"; fail=1; fi
}
[ "$status" -eq 0 ] && echo "PASS: client exited normally" || { echo "FAIL: client exit status $status"; fail=1; }
check "Successfully finished." "client joined a singleplayer world (mc-runtime-test)"
check "FABRICATED-TEST main entrypoint OK" "main entrypoint ran on the client"
check "FABRICATED-TEST client entrypoint OK" "client entrypoint ran"
check "FABRICATED-TEST client started OK" "native client lifecycle: CLIENT_STARTED"
check "FABRICATED-TEST client resource reload listener OK" "native client resource reload listener"
check "FABRICATED-TEST configuration task OK" "Fabric configuration task with the integrated server"
check "FABRICATED-TEST client join event OK" "native client networking: join event"
check "FABRICATED-TEST client networking round trip OK" "native networking: client to integrated server and back"
check "FABRICATED-TEST extended menu opened with its data OK" "extended menu (menu api): opening data reaches the client"
check "FABRICATED-TEST registry sync: custom registry OK" "registry sync on the client"
check "FABRICATED-TEST object builder OK" "native object builder in the integrated server"
check "FABRICATED-TEST block api OK" "native block api in the integrated server"
check "FABRICATED-TEST item api OK" "native item api in the integrated server"
check "FABRICATED-TEST interaction events OK" "native interaction events in the integrated server"
check "FABRICATED-TEST loot OK" "native loot in the integrated server"
check "FABRICATED-TEST content registries OK" "native content registries in the integrated server"
check "FABRICATED-TEST transfer OK" "native transfer in the integrated server"
check "FABRICATED-TEST api OK" "public API on the client"
check "FABRICATED-TEST modmenu config screen OK" "a Fabric mod's Mod Menu config screen is behind NeoForge's mod list button"
check "FABRICATED-TEST environment stripping OK" "@EnvironmentInterface kept on the client"
# The test mods log "FABRICATED-TEST ... FAILED" when one of their own checks fails.
if grep -q "FABRICATED-TEST .*FAILED" "$log" client.out 2>/dev/null; then
	echo "FAIL: a test mod check reported a failure:"; grep -h "FABRICATED-TEST .*FAILED" "$log" client.out 2>/dev/null | head -n 5; fail=1
else
	echo "PASS: no test mod check reported a failure"
fi
exit "$fail"
