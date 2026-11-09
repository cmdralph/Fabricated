#!/usr/bin/env bash
# CI's client harness, every platform's: installs the platform's client with its official installer (the adapter,
# ci/<platform>/platform.sh), adds Fabricated, the test mod and the extra mods, and starts it headlessly (Xvfb) with
# HeadlessMC. mc-runtime-test creates a singleplayer world, waits until the player's chunk is loaded and quits; the run
# is then checked against ci/checks.tsv (ci/check-log.py).
#
# Usage: ci/smoke-client.sh <platform> <minecraft> <loader version> <work-dir> [extra-mods-dir]
# Settings: SMOKE_RUN, the run of ci/runs.tsv it is (client); SMOKE_TESTMODS=none; SMOKE_TIMEOUT; SMOKE_EXIT_TIMEOUT;
# SMOKE_SEED, a directory copied into the game directory first, as an earlier launch left it; SMOKE_RESOURCE_PACKS,
# resource packs selected from the start (a JSON list's items: '"continuity:default"'); SMOKE_SHADERS=1, a minimal
# shader pack for Iris, enabled; SMOKE_JVM_ARGS, more of the game's JVM options (system properties, separated by spaces);
# SMOKE_INSTALL_ONLY, SMOKE_ON_ENTRYPOINT_FAILURE, SMOKE_LOG_LEVEL, FABRICATED_MODS and SMOKE_SUMMARY as for the server.
# Exit status: check-log.py's.
# Expects: the build's jar and test mods, JAVA_HOME pointing at the game's Java, and xvfb-run on the PATH.
set -euo pipefail

platform="$1"
mc="$2"
loader="$3"
work="$4"
extra="${5:-}"
side=client
# shellcheck source=ci/smoke-lib.sh
source "$(dirname "$0")/smoke-lib.sh"
timeout_seconds="${SMOKE_TIMEOUT:-900}"
# A game that reached its world (mc-runtime-test's "Successfully finished.") is stopped by mc-runtime-test; one that
# hasn't exited this many seconds later is stuck shutting down, and is stopped then, not at the run's time limit.
exit_timeout="${SMOKE_EXIT_TIMEOUT:-120}"
hmc_version=2.10.0
mcrt_version=4.5.2

rm -rf "$work"
mkdir -p "$work/HeadlessMC" "$work/run/mods" "$work/run/config" "$work/minecraft"
cd "$work"

echo "::group::Install $platform $loader (client)"
cached_install minecraft install_client minecraft
echo "::endgroup::"
echo "::group::Get HeadlessMC and mc-runtime-test"
download "https://github.com/3arthqu4ke/headlessmc/releases/download/$hmc_version/headlessmc-launcher-$hmc_version.jar" hmc.jar
mcrt="mc-runtime-test-$mc-$mcrt_version-$mcrt_loader-release.jar"
download "https://github.com/headlesshq/mc-runtime-test/releases/download/$mcrt_version/$mcrt" "run/mods/$mcrt"
echo "::endgroup::"
[ -n "${SMOKE_INSTALL_ONLY:-}" ] && exit 0
ls minecraft/versions
cat > HeadlessMC/config.properties <<PROPS
hmc.java.versions=$JAVA_HOME/bin/java
hmc.gamedir=$work/run
hmc.mcdir=$work/minecraft
hmc.offline=true
hmc.rethrow.launch.exceptions=true
hmc.exit.on.failed.command=true
hmc.assets.dummy=true
PROPS

add_mods run/mods
[ -n "$has_extra" ] && quiet_load_warnings run
if [ -n "${SMOKE_SEED:-}" ] && [ -d "$SMOKE_SEED" ]; then
	cp -r "$SMOKE_SEED"/. run/
fi
write_config run/config
cat > run/options.txt <<'OPTS'
onboardAccessibility:false
pauseOnLostFocus:false
OPTS
# Keys saved for test mods' key bindings, which they must have once the game started. The file then names the game's
# data version (its client jar's): without one the game takes it for a file from before 1.13, whose data fixer reads
# each key as an LWJGL 2 key code, fails on a key's name and drops the whole file.
keys=$(client_keys)
if [ -n "$keys" ]; then
	if data_version=$(python3 -I -c 'import json, sys, zipfile; print(json.loads(zipfile.ZipFile(sys.argv[1]).read("version.json"))["world_version"])' \
			"minecraft/versions/$mc/$mc.jar"); then
		printf '%s\n' "version:$data_version" "$keys" >> run/options.txt
	else
		echo "::warning::The client jar's data version wasn't read; no key is saved for the test mods' key bindings"
	fi
fi
if [ -n "${SMOKE_RESOURCE_PACKS:-}" ]; then
	printf 'resourcePacks:[%s]\n' "$SMOKE_RESOURCE_PACKS" >> run/options.txt
fi
cat run/options.txt
# Its terrain program reads block ids, so Iris uses its extended vertex format for the world, which Sodium's chunk
# meshes (Fabric models included) are written in.
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
# Polydium asks once, in the first world, whether to switch to Vulkan, and mc-runtime-test waits for no screen to be
# open: its own marker, in the user's config folder (the run's own), says it has asked.
export XDG_CONFIG_HOME="$work/xdg"
mkdir -p "$XDG_CONFIG_HOME/polydium" && touch "$XDG_CONFIG_HOME/polydium/vulkan-prompt"

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
		--command launch "$client_launch_id" -regex --jvm "-Djava.awt.headless=true -DMcRuntimeGameTest=false${SMOKE_JVM_ARGS:+ $SMOKE_JVM_ARGS}" \
		> client.out 2>&1 &
	client_pid=$!
	dumped=0
	finished_at=""
	# What every game thread is doing, so a hang can be diagnosed from the log. SIGQUIT makes a JVM print every thread's
	# stack to its output (client.out) and carry on; jstack can't attach to the game here. Every Java process the
	# launcher started, the game's included; a shell would quit on it.
	dump_threads() {
		dumped=1
		for pid in $(descendants "$client_pid"); do
			if [ "$(cat "/proc/$pid/comm" 2>/dev/null)" = java ]; then kill -QUIT "$pid" 2>/dev/null || true; fi
		done
		sleep 3
		awk '/^Full thread dump/ { dump = 1 } dump' client.out > thread-dump.txt || true
	}
	stop="Preparing crash report|Game crashed!|Crash report saved to|Failed to wait for future|Mixin prepare for mod .* failed"
	while kill -0 "$client_pid" 2>/dev/null; do
		if [ "$dumped" -eq 0 ] && [ $((SECONDS - started)) -ge $((timeout_seconds - 45)) ]; then
			dump_threads
		fi
		if [ -z "$finished_at" ] && grep -q "Successfully finished\." client.out run/logs/latest.log 2>/dev/null; then
			finished_at=$SECONDS
		fi
		# A game stuck shutting down after it reached its world: its threads, then the whole tree is stopped (the game's
		# JVM would outlive the launcher into the next run), and the run fails as a timeout would.
		if [ -n "$finished_at" ] && [ $((SECONDS - finished_at)) -ge "$exit_timeout" ]; then
			echo "The game reached its world but didn't exit within $exit_timeout seconds; stopping the client."
			echo "The game didn't exit within $exit_timeout seconds of reaching its world (mc-runtime-test's stop)" > stop-reason.txt
			[ "$dumped" -eq 1 ] || dump_threads
			kill_tree "$client_pid"
			break
		fi
		# After a crash or a fatal loading error the game shows its error window until closed: stopped once the crash
		# report is written.
		if grep -qE "$stop" client.out run/logs/latest.log 2>/dev/null; then
			echo "The game crashed; stopping the client."
			# The line that stopped it, for the summary: a pattern can match an error the game survives.
			grep -m 1 -hE "$stop" client.out run/logs/latest.log 2>/dev/null | head -n 1 > stop-reason.txt || true
			sleep 15
			kill_tree "$client_pid"
			break
		fi
		# The game doesn't exit when no renderer can be created.
		if grep -q "Failed to create backend OpenGL" client.out && grep -q "Failed to create backend Vulkan" client.out; then
			echo "No render backend could be created; stopping the client."
			echo "No render backend could be created" > stop-reason.txt
			sleep 5
			kill_tree "$client_pid"
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
echo "$status" > status.txt
echo "Client: status $status after $((SECONDS - started)) s"
echo "::endgroup::"

# CI-3: log4j rotates the game log at 00:00 UTC, moving the lines before it to logs/<date>-<n>.log.gz. The run directory
# is new each run, so every rotated file is this run's: they go in front of latest.log, oldest first, for the checks.
rotated=$(ls -1tr run/logs/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-*.log.gz 2>/dev/null || true)
if [ -n "$rotated" ] && [ -f run/logs/latest.log ]; then
	{ for f in $rotated; do gzip -dc "$f"; done; cat run/logs/latest.log; } > run/logs/whole.log && mv run/logs/whole.log run/logs/latest.log
	echo "The game log was rotated during the run: checking it whole, with $(echo "$rotated" | wc -l) rotated file(s) in front"
fi
log=run/logs/latest.log
[ -f "$log" ] || log=client.out
echo "::group::Client output (tail)"
tail -n 300 client.out || true
echo "::endgroup::"
if [ "$status" -ne 0 ]; then
	echo "::group::Game log without the loading-screen spam (last 250 lines)"
	grep -v "Waiting for player to load" "$log" | tail -n 250 || true
	echo "::endgroup::"
	diagnose "$log" client.out
	echo "::group::Errors and warnings in the game log, by logger"
	python3 -I "$root/ci/log-digest.py" "$log" || true
	echo "::endgroup::"
	if [ -n "$has_extra" ]; then
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
	# A mixin that failed as Mixin applied it, though pre-flight passed it: its handler, as the jar has it and as
	# Fabricated left it, and the target's code (CORPUS-18).
	echo "::group::The mixin the crash names"
	python3 -I "$root/ci/mixin-diag.py" "$report" "$work" || true
	echo "::endgroup::"
done
if [ -f thread-dump.txt ]; then
	echo "::group::Thread dump of a game stuck before the timeout or shutting down (game threads)"
	awk 'BEGIN { RS = ""; ORS = "\n\n" } /^"(Render thread|Server thread|Netty|Local|IO-Worker|Worker-Main)/' thread-dump.txt || true
	echo "::endgroup::"
fi
echo "::group::How the client left its world"
grep -h -E "Successfully finished|Stopping!|[Dd]isconnect|Saving and pausing|left the game" "$log" 2>/dev/null | tail -n 8 | cut -c1-300 || true
echo "::endgroup::"
echo "::group::fabric-bridge-report.txt"
cat run/logs/fabric-bridge-report.txt 2>/dev/null || echo "(report missing)"
echo "::endgroup::"

evaluate
