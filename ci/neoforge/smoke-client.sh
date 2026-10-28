#!/usr/bin/env bash
# CI client smoke test: installs a real NeoForge client with the official installer, adds Fabricated and the Fabric
# test mod, and starts it headlessly (Xvfb) with HeadlessMC. mc-runtime-test's client mod creates a singleplayer
# world, waits until the player's chunk is loaded and quits; the test then checks the client-side test markers
# (client entrypoint, client lifecycle, client resource listener, a configuration task and a networking round trip with
# the integrated server).
#
# Usage: ci/neoforge/smoke-client.sh <minecraft-version> <neoforge-version> <work-dir> [extra-mods-dir]
# Expects: build/libs/fabricated-*+mc<minecraft-version>.jar and mc/neoforge/<minecraft-version>/build/testmods/*.jar,
# JAVA_HOME pointing at the Java version the game needs, xvfb-run and gh (with GH_TOKEN) on the PATH.
set -euo pipefail

mc="$1"
neo="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=ci/install-cache.sh
source "$root/ci/install-cache.sh"
timeout_seconds="${SMOKE_TIMEOUT:-900}"
hmc_version="2.10.0"
mcrt_version="4.5.2"

rm -rf "$work"
mkdir -p "$work/HeadlessMC" "$work/run/mods" "$work/run/config" "$work/minecraft"
cd "$work"

echo "::group::Install NeoForge $neo (client)"
install_client() {
	# The installer only checks that a launcher profile file exists.
	echo '{"profiles":{}}' > minecraft/launcher_profiles.json
	curl -fsSL --retry 4 -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/${neo}/neoforge-${neo}-installer.jar"
	java -jar installer.jar --installClient minecraft > installer.log 2>&1 || { cat installer.log; exit 1; }
}
cached_install "neoforge-client-$neo" minecraft install_client
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

cp "$root"/build/libs/fabricated-[0-9]*+mc"$mc".jar run/mods/
# Only the working test mod: the deliberately broken ones would put NeoForge's warning screen in front of the game.
cp "$root"/mc/neoforge/"$mc"/build/testmods/fabricated-testmod.jar run/mods/
if [ -n "$extra" ] && [ -d "$extra" ]; then
	cp "$extra"/*.jar run/mods/ 2>/dev/null || true
fi
ls -1 run/mods
# FABRICATED_MODS: optional per-mod overrides, the "mods" object of config/fabricated.json.
mods_json="${FABRICATED_MODS:-{\}}"
if [ -n "$extra" ]; then
	# With extra mods, a mod's failing entrypoint is reported and the game goes on, as the server smoke test does.
	echo "{ \"schema\": 1, \"onEntrypointFailure\": \"disable\", \"logLevel\": \"DEBUG\", \"mods\": $mods_json }" > run/config/fabricated.json
	# And NeoForge doesn't put its loading warnings (a Fabric mod that didn't load, say) on a screen, which
	# mc-runtime-test takes for a failure and stops the game at; they are still logged ("Mods loaded with N warning(s)").
	echo "showLoadWarnings = false" > run/config/neoforge-client.toml
else
	echo "{ \"schema\": 1, \"logLevel\": \"DEBUG\", \"mods\": $mods_json }" > run/config/fabricated.json
fi
cat run/config/fabricated.json
cat > run/options.txt <<'OPTS'
onboardAccessibility:false
pauseOnLostFocus:false
OPTS
# SMOKE_SHADERS: a minimal shader pack for Iris, enabled from the start. Its terrain program reads block ids, so Iris
# uses its extended vertex format for the world, which Sodium's chunk meshes (Fabric models included) are written in.
if [ -n "${SMOKE_SHADERS:-}" ]; then
	pack=run/shaderpacks/fabricated-test/shaders
	mkdir -p "$pack"
	cat > "$pack/gbuffers_terrain.vsh" <<'GLSL'
#version 120
attribute vec4 mc_Entity;
varying vec2 texcoord;
varying vec4 color;
void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	color = gl_Color * (mc_Entity.x > 0.0 ? 1.0 : 0.95);
}
GLSL
	cat > "$pack/gbuffers_terrain.fsh" <<'GLSL'
#version 120
uniform sampler2D texture;
varying vec2 texcoord;
varying vec4 color;
void main() {
	gl_FragData[0] = texture2D(texture, texcoord) * color;
}
GLSL
	printf 'shaderPack=fabricated-test\nenableShaders=true\n' > run/config/iris.properties
fi

echo "::group::Run client"
# The launcher downloads the game's libraries and assets first. When it fails before the game starts (no game log at
# all), nothing of Fabricated's has run yet, so that one step is tried again once; a game that started is never retried.
for attempt in 1 2; do
	status=0
	started=$SECONDS
	rm -rf run/logs run/crash-reports client.out stop-reason.txt thread-dump.txt
	# A 24-bit screen with GLX: xvfb-run's default 8-bit screen has no OpenGL visual. 26.3 opens its window with SDL3,
	# whose GLX visual request Xvfb can't match; EGL (Mesa's software renderer) works there, so SDL is told to use it.
	export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
	timeout "$timeout_seconds" xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset" java -Dhmc.check.xvfb=true -jar hmc.jar \
		--command launch "neoforge-${neo}" -regex --jvm "-Djava.awt.headless=true -DMcRuntimeGameTest=false" \
		> client.out 2>&1 &
	client_pid=$!
	descendants() {
		for child in $(pgrep -P "$1" || true); do
			echo "$child"
			descendants "$child"
		done
	}
	dumped=0
	# The game doesn't exit when no renderer can be created; stop it then instead of waiting for the timeout.
	while kill -0 "$client_pid" 2>/dev/null; do
		# Shortly before the timeout, record what every game thread is doing, so a hang can be diagnosed from the log.
		if [ "$dumped" -eq 0 ] && [ $((SECONDS - started)) -ge $((timeout_seconds - 45)) ]; then
			dumped=1
			# SIGQUIT makes a JVM print every thread's stack to its output (client.out) and carry on; jstack can't attach to
			# the game here ("non existent JVM pid"). Every Java process the launcher started, the game's included (its
			# command line doesn't carry the launcher's JVM options); a shell would quit on it.
			for pid in $(descendants "$client_pid"); do
				if [ "$(cat "/proc/$pid/comm" 2>/dev/null)" = java ]; then kill -QUIT "$pid" 2>/dev/null || true; fi
			done
			sleep 3
			awk '/^Full thread dump/ { dump = 1 } dump' client.out > thread-dump.txt || true
		fi
		# After a crash or a fatal loading error the game shows its error window until closed; stop it once the crash report
		# is written.
		stop="Preparing crash report|Game crashed!|Crash report saved to|Failed to wait for future|Mixin prepare for mod .* failed"
		if grep -qE "$stop" client.out run/logs/latest.log 2>/dev/null; then
			echo "The game crashed; stopping the client."
			# The line that stopped it, for the summary: a pattern can match an error the game survives.
			grep -m 1 -hE "$stop" client.out run/logs/latest.log 2>/dev/null | head -n 1 > stop-reason.txt || true
			sleep 15
			pkill -P "$client_pid" 2>/dev/null || true
			kill "$client_pid" 2>/dev/null || true
			break
		fi
		if grep -q "Failed to create backend OpenGL" client.out && grep -q "Failed to create backend Vulkan" client.out; then
			echo "No render backend could be created; stopping the client."
			echo "No render backend could be created" > stop-reason.txt
			sleep 5
			pkill -P "$client_pid" 2>/dev/null || true
			kill "$client_pid" 2>/dev/null || true
			break
		fi
		sleep 2
	done
	wait "$client_pid" || status=$?
	if [ "$status" -ne 0 ] && [ ! -f run/logs/latest.log ] && [ "$attempt" -eq 1 ]; then
		echo "The launcher stopped (status $status) before the game started; its output:"
		tail -n 40 client.out || true
		echo "Launching once more."
		continue
	fi
	break
done
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
check "player list was replaced after NeoForge made its view" "getPlayers() follows a player list a mod replaced (the test mod replaces it, as the Polyfrost stack does)"
check "FABRICATED-TEST main entrypoint OK" "main entrypoint ran on the client"
check "FABRICATED-TEST main entrypoint game instance OK" "main entrypoints run once the game instance exists, as on Fabric (CORPUS-16)"
check "FABRICATED-TEST content built at bootstrap registered OK" "content built during vanilla's bootstrap is registered by the main initializer, as BetterNether's entity types are (CORPUS-31)"
check "FABRICATED-TEST built-in registries frozen after the main initializers OK" "a Fabric mod's mixin into the built-in registries' freeze runs after the main and client initializers, where Wover makes the item definitions BetterEnd's subscribes to (CORPUS-32)"
check "FABRICATED-TEST creative tab order OK" "a creative tab registered by a main initializer is ordered after the vanilla tabs"
check "FABRICATED-TEST resource bundle with a control OK" "translations loaded with a ResourceBundle.Control from a named module (LuckPerms, LOADER-3)"
check "FABRICATED-TEST client entrypoint OK" "client entrypoint ran"
check "FABRICATED-TEST client entrypoint registration OK" "a client initializer registers content (registries open, as Fabric API leaves them)"
check "FABRICATED-TEST client started OK" "native client lifecycle: CLIENT_STARTED"
check "FABRICATED-TEST client block entity events OK" "native client lifecycle: block entities unload when the client leaves its world"
check "FABRICATED-TEST block entity events OK" "native lifecycle in the integrated server: block entity load and unload"
check "FABRICATED-TEST client resource reload listener OK" "native client resource reload listener"
check "FABRICATED-TEST late vanilla reload listener OK" "a reload listener registered the vanilla way after NeoForge sorted them (OneConfig)"
check "FABRICATED-TEST early reload listener OK" "a reload listener registered the vanilla way from Minecraft's constructor, before NeoForge's event"
check "FABRICATED-TEST configuration task OK" "Fabric configuration task with the integrated server"
check "FABRICATED-TEST client join event OK" "native client networking: join event"
check "FABRICATED-TEST client networking round trip OK" "native networking: client to integrated server and back"
check "FABRICATED-TEST late payload type round trip OK" "native networking: a payload type the server registered as it started (NET-3)"
check "FABRICATED-TEST minecraft namespace payload round trip OK" "native networking: a payload type in the minecraft namespace, which NeoForge refuses to register (BetterEnd, NET-4)"
case "$mc" in 26.*)
	check "FABRICATED-TEST packet context while encoding OK" "Fabric's packet context, filled as Fabric API fills it, while NeoForge's splitter measures a packet (Polymer, NET-7)"
	check "FABRICATED-TEST packet context while decoding a large payload OK" "Fabric's packet context while NeoForge's splitter reassembles a payload (NET-7)"
	;;
esac
check "FABRICATED-TEST extended menu opened with its data OK" "extended menu (menu api): opening data reaches the client"
check "FABRICATED-TEST registry sync: custom registry OK" "registry sync on the client"
check "FABRICATED-TEST object builder OK" "native object builder in the integrated server"
check "FABRICATED-TEST block api OK" "native block api in the integrated server"
check "FABRICATED-TEST item api OK" "native item api in the integrated server"
check "FABRICATED-TEST interaction events OK" "native interaction events in the integrated server"
check "FABRICATED-TEST loot OK" "native loot in the integrated server"
check "FABRICATED-TEST content registries OK" "native content registries in the integrated server"
check "FABRICATED-TEST transfer OK" "native transfer in the integrated server"
# Fabricated ships fabric-particles-v1 on 1.21.1 only (later versions pass it through from the Fabric API jar).
case "$mc" in 1.21.1)
	check "FABRICATED-TEST particles OK" "native fabric-particles-v1: the particle engine finds the test mod's factory, kept by the type's id as NeoForge keeps them, and its sprite provider holds its sprite in the particle atlas (PART-1)"
	;;
esac
check "FABRICATED-TEST api OK" "public API on the client"
check "FABRICATED-TEST modmenu config screen OK" "a Fabric mod's Mod Menu config screen is behind NeoForge's mod list button"
check "FABRICATED-TEST environment stripping OK" "@EnvironmentInterface kept on the client"
# Where the libraries LIB-1's bridges reflect on live (informational).
grep -h -m 1 -o "FABRICATED-TEST library modules: .*" "$log" 2>/dev/null || true
# The test mods log "FABRICATED-TEST ... FAILED" when one of their own checks fails.
if grep -q "FABRICATED-TEST .*FAILED" "$log" client.out 2>/dev/null; then
	echo "FAIL: a test mod check reported a failure:"; { grep -h -m 5 "FABRICATED-TEST .*FAILED" "$log" client.out 2>/dev/null || true; }; fail=1
else
	echo "PASS: no test mod check reported a failure"
fi
exit "$fail"
