#!/usr/bin/env bash
# The forge branch's client smoke: installs a real Minecraft Forge client with the official installer, adds Fabricated
# and the branch's Fabric test mod, and starts it headlessly (Xvfb) with HeadlessMC. mc-runtime-test's Forge build
# creates a singleplayer world, waits until the player's chunk is loaded and quits; the test then checks that the Fabric
# mod's main and client entrypoints ran, in Fabric's order, with Fabric Loader's game instance set.
#
# Usage: ci/forge/smoke-client.sh <minecraft-version> <forge-version> <work-dir> [extra-mods-dir]
# Exit status: the client must reach a world and exit normally, and every check must pass. With extra-mods-dir (the
# official Fabric API jar, and on 26.x the test mod for Fabric API's client modules, whose checks then apply), its jars
# join the mods folder.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge/<minecraft-version>/build/testmods/fabricated-testmod.jar,
# JAVA_HOME pointing at the Java version the game needs, xvfb-run and gh (with GH_TOKEN) on the PATH.
set -euo pipefail

mc="$1"
forge="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=ci/install-cache.sh
source "$root/ci/install-cache.sh"
timeout_seconds="${SMOKE_TIMEOUT:-900}"
# A game that reached its world (mc-runtime-test's "Successfully finished.") is stopped by mc-runtime-test; one that
# hasn't exited this many seconds later is stuck shutting down, and is stopped then, not at the run's time limit.
exit_timeout="${SMOKE_EXIT_TIMEOUT:-120}"
hmc_version="2.10.0"
mcrt_version="4.5.2"

rm -rf "$work"
mkdir -p "$work/HeadlessMC" "$work/run/mods" "$work/run/config" "$work/minecraft"
cd "$work"

echo "::group::Install Forge $mc-$forge (client)"
install_client() {
	# The installer only checks that a launcher profile file exists.
	echo '{"profiles":{}}' > minecraft/launcher_profiles.json
	curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${mc}-${forge}/forge-${mc}-${forge}-installer.jar"
	java -jar installer.jar --installClient minecraft > installer.log 2>&1 || { tail -n 60 installer.log; exit 1; }
	# The installer leaves the vanilla version's jar but not its version file, which HeadlessMC reads as the Forge
	# version's parent: it comes from Mojang's version manifest.
	if [ ! -f "minecraft/versions/$mc/$mc.json" ]; then
		url=$(curl -fsSL --retry 4 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
			| python3 -c 'import json, sys; print(next(v["url"] for v in json.load(sys.stdin)["versions"] if v["id"] == sys.argv[1]))' "$mc")
		mkdir -p "minecraft/versions/$mc"
		curl -fsSL --retry 4 -o "minecraft/versions/$mc/$mc.json" "$url"
	fi
}
cached_install "forge-client-$forge" minecraft install_client
ls minecraft/versions minecraft/versions/"$mc"
echo "::endgroup::"

echo "::group::Get HeadlessMC and mc-runtime-test"
curl -fsSL --retry 4 -o hmc.jar "https://github.com/3arthqu4ke/headlessmc/releases/download/${hmc_version}/headlessmc-launcher-${hmc_version}.jar"
gh release download "$mcrt_version" -R headlesshq/mc-runtime-test -D run/mods -p "mc-runtime-test-${mc}-*-lexforge-release.jar"
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

cp "$root"/build/libs/fabricated-forge-*+mc"$mc".jar run/mods/
# Only the working test mod: a Fabric mod that doesn't load would put Forge's warning screen in front of the game.
cp "$root"/mc/forge/"$mc"/build/testmods/fabricated-testmod.jar run/mods/
if [ -n "$extra" ] && [ -d "$extra" ]; then
	cp "$extra"/*.jar run/mods/ 2>/dev/null || true
	# Forge then doesn't put its loading warnings on a screen, which mc-runtime-test takes for a failure; they are
	# still logged.
	printf '[client]\nshowLoadWarnings = false\n' > run/config/forge-client.toml
fi
# SMOKE_SEED: a directory copied into the game directory first, as an earlier launch left it (Essential's download).
if [ -n "${SMOKE_SEED:-}" ] && [ -d "$SMOKE_SEED" ]; then
	cp -r "$SMOKE_SEED"/. run/
fi
# Fabricated's config for the run: SMOKE_ON_ENTRYPOINT_FAILURE, as in the server smoke, its onEntrypointFailure for a
# corpus run, and FABRICATED_MODS its per-mod overrides (the "mods" object), as on main.
if [ -n "${SMOKE_ON_ENTRYPOINT_FAILURE:-}" ] || [ -n "${FABRICATED_MODS:-}" ]; then
	printf '{ "schema": 1, "onEntrypointFailure": "%s", "mods": %s }\n' "${SMOKE_ON_ENTRYPOINT_FAILURE:-error}" "${FABRICATED_MODS:-{\}}" \
		> run/config/fabricated.json
	cat run/config/fabricated.json
fi
ls -1 run/mods
cat > run/options.txt <<'OPTS'
onboardAccessibility:false
pauseOnLostFocus:false
OPTS
# A key saved for the test mod's key mapping, which it must have once the game has started (KEYMAP-3). The file then
# names the game's data version (its client jar's): without one, the game takes it for a file from before 1.13, whose
# data fixer reads each key as an LWJGL 2 key code, fails on a key's name, and drops the whole file (as on main).
# Should the jar not say, the key isn't saved, and the key mapping check fails on it.
if data_version=$(python3 -I -c 'import json, sys, zipfile; print(json.loads(zipfile.ZipFile(sys.argv[1]).read("version.json"))["world_version"])' \
		"minecraft/versions/$mc/$mc.jar"); then
	printf '%s\n' "version:$data_version" "key_key.fabricated_forge_testmod.check:key.keyboard.k" >> run/options.txt
else
	echo "::warning::The client jar's data version wasn't read; no key is saved for the test mod's key mapping"
fi
cat run/options.txt

echo "::group::Run client"
for attempt in 1 2; do
	status=0
	started=$SECONDS
	rm -rf run/logs run/crash-reports client.out stop-reason.txt thread-dump.txt
	export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
	timeout "$timeout_seconds" xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset" java -Dhmc.check.xvfb=true -jar hmc.jar \
		--command launch "${mc}-forge" -regex --jvm "-Djava.awt.headless=true -DMcRuntimeGameTest=false${SMOKE_JVM_ARGS:+ $SMOKE_JVM_ARGS}" \
		> client.out 2>&1 &
	client_pid=$!
	descendants() {
		for child in $(pgrep -P "$1" || true); do
			echo "$child"
			descendants "$child"
		done
	}
	dumped=0
	finished_at=""
	# What every game thread is doing, so a hang can be diagnosed from the log. SIGQUIT makes a JVM print every thread's
	# stack to its output (client.out) and carry on. Every Java process the launcher started, the game's included; a
	# shell would quit on it.
	dump_threads() {
		dumped=1
		for pid in $(descendants "$client_pid"); do
			if [ "$(cat "/proc/$pid/comm" 2>/dev/null)" = java ]; then kill -QUIT "$pid" 2>/dev/null || true; fi
		done
		sleep 3
		awk '/^Full thread dump/ { dump = 1 } dump' client.out > thread-dump.txt || true
	}
	while kill -0 "$client_pid" 2>/dev/null; do
		# Shortly before the timeout, the threads of a game that never got there (as on main).
		if [ "$dumped" -eq 0 ] && [ $((SECONDS - started)) -ge $((timeout_seconds - 45)) ]; then
			dump_threads
		fi
		# A game stuck shutting down after it reached its world: its threads, then it is stopped, and the run fails as a
		# timeout would.
		if [ -z "$finished_at" ] && grep -q "Successfully finished\." client.out run/logs/latest.log 2>/dev/null; then
			finished_at=$SECONDS
		fi
		if [ -n "$finished_at" ] && [ $((SECONDS - finished_at)) -ge "$exit_timeout" ]; then
			echo "The game reached its world but didn't exit within $exit_timeout seconds; stopping the client."
			echo "The game didn't exit within $exit_timeout seconds of reaching its world (mc-runtime-test's stop)" > stop-reason.txt
			[ "$dumped" -eq 1 ] || dump_threads
			# The whole tree, the game's JVM included, which would otherwise outlive the launcher into the next run.
			tree=$(descendants "$client_pid")
			kill $tree "$client_pid" 2>/dev/null || true
			sleep 5
			kill -9 $tree "$client_pid" 2>/dev/null || true
			break
		fi
		stop="Preparing crash report|Game crashed!|Crash report saved to|Failed to wait for future|Mixin prepare for mod .* failed"
		if grep -qE "$stop" client.out run/logs/latest.log 2>/dev/null; then
			echo "The game crashed; stopping the client."
			grep -m 1 -hE "$stop" client.out run/logs/latest.log 2>/dev/null | head -n 1 > stop-reason.txt || true
			sleep 15
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
echo "Client finished after $((SECONDS - started)) s with status $status"
echo "::endgroup::"

# CI-3: log4j rotates the game log at 00:00 UTC, moving the lines before it to logs/<date>-<n>.log.gz. The run directory
# is new each run, so every rotated file is this run's: they go in front of latest.log, oldest first, for every check
# that reads it (here and in the workflow).
rotated=$(ls -1tr run/logs/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]-*.log.gz 2>/dev/null || true)
if [ -n "$rotated" ] && [ -f run/logs/latest.log ]; then
	{ for f in $rotated; do gzip -dc "$f"; done; cat run/logs/latest.log; } > run/logs/whole.log && mv run/logs/whole.log run/logs/latest.log
	echo "The game log was rotated during the run: checking it whole, with $(echo "$rotated" | wc -l) rotated file(s) in front"
fi
log=run/logs/latest.log
[ -f "$log" ] || log=client.out
echo "::group::Client output (tail)"
tail -n 200 client.out || true
echo "::endgroup::"
# The job log keeps the client output's end only: the game log's errors and warnings, by logger, from the start.
echo "::group::Errors and warnings in the game log"
python3 -I "$root/ci/log-digest.py" "$log" || true
echo "::endgroup::"
if [ "$status" -ne 0 ]; then
	echo "::group::First errors in the game log"
	grep -n -m 80 -E "/(ERROR|FATAL)\]|Exception|Caused by" "$log" || true
	echo "::endgroup::"
fi
for report in run/crash-reports/*.txt; do
	[ -f "$report" ] || continue
	echo "::group::Crash report $(basename "$report") (first 120 lines)"
	head -n 120 "$report"
	echo "::endgroup::"
done
if [ -f thread-dump.txt ]; then
	echo "::group::Thread dump of a game stuck before the timeout or shutting down (game threads)"
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
# mc-runtime-test logs "Successfully finished." where it runs no game tests, or "Running game tests..." first (as its
# 1.20.1 build does), and exits with -1 if one fails: either line with a normal exit means the world was reached.
check "Successfully finished\.\|Running game tests\.\.\." "the client joined a singleplayer world (mc-runtime-test)"
check "Fabricated ran the Fabric main entrypoints of [1-9][0-9]* mod(s) on Forge (0 failed)" "the Fabric main and client entrypoints ran in Forge's registry events"
check "FABRICATED-FORGE-TEST main entrypoint OK" "the test mod's main entrypoint ran on the client"
check "FABRICATED-FORGE-TEST client entrypoint OK" "its client entrypoint ran after the main one, as on Fabric"
check "FABRICATED-FORGE-TEST client game instance OK" "Fabric Loader's game instance is the Minecraft instance when the client entrypoints run"
check "FABRICATED-FORGE-TEST api after entrypoints OK" "Fabricated's API runs what waits for the Fabric initializers once they ran (Fabricated.get().afterEntrypoints)"
check "FABRICATED-FORGE-TEST modmenu config screen OK" "a Fabric mod's Mod Menu config screen is behind Forge's mod list button, as on NeoForge"
check "FABRICATED-FORGE-TEST registry OK" "the test mod registered an item on the client"
check "FABRICATED-FORGE-TEST mod pack data is stable OK" "a Fabric mod's registry entries are stable, so creating the world asked nothing (PACK-5)"
if [ "$mc" = 1.21.11 ]; then keys="key binding" keys_module=fabric-key-binding-api-v1; else keys="key mapping" keys_module=fabric-key-mapping-api-v1; fi
check "FABRICATED-FORGE-TEST $keys OK" "native $keys_module: a $keys a Fabric mod registers from its client entrypoint is in the game's options, sorted after the game's, refused twice and once Forge's event added it, as upstream, and has the key the options file saved for it (KEYMAP-3)"
check "FABRICATED-FORGE-TEST content built at bootstrap registered OK" "content built during vanilla's bootstrap is registered by the main initializer (CORPUS-31)"
check "FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK" "the built-in registries' freeze runs after the main initializers (CORPUS-32)"
check "FABRICATED-FORGE-TEST hud method end hook OK" "a Fabric mod's hook at the end of vanilla's HUD method ran after Forge's layers, outside the loading screen, pointed at the method's returns (HUD-4)"
check "FABRICATED-FORGE-TEST hud method call hook OK" "a Fabric mod's hook at the HUD method's crosshair call, which Forge's crosshair layer makes, ran, pointed at the called method's start (HUD-4)"
if [ -n "$extra" ]; then
	check "FABRICATED-FORGE-TEST networking round trip OK" "fabric-networking-api-v1 (with Fabric API): a payload the client sends as it joins comes back from the integrated server with the server's answer"
	check "FABRICATED-FORGE-TEST Fabric command registered OK" "fabric-command-api-v2 (with Fabric API): a command registered through CommandRegistrationCallback is in the integrated server's dispatcher (CTOR-2)"
	check "FABRICATED-FORGE-TEST block break events OK" "fabric-events-interaction-v0 (with Fabric API): a fake player's breaks on the integrated server fire PlayerBlockBreakEvents, and a BEFORE refusal keeps the block (BREAK-1)"
	check "FABRICATED-FORGE-TEST interaction events OK" "fabric-events-interaction-v0 (with Fabric API): on the integrated server, a fake player's uses and attacks ask the use and attack callbacks, and their answers hold"
	check "FABRICATED-FORGE-TEST sleep events OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, a hay block EntitySleepEvents.ALLOW_BED admits stays hay while an armor stand sleeps on it (SLEEP-1)"
	check "FABRICATED-FORGE-TEST damage events OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, an entity hurt asks ALLOW_DAMAGE, whose refusal holds, and fires AFTER_DAMAGE (DAMAGE-1)"
	check "FABRICATED-FORGE-TEST effect command context OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, an effect the effect command gives reaches ALLOW_ADD from that command, and one given by code from none (EFFECT-1)"
	check "FABRICATED-FORGE-TEST storages OK" "fabric-transfer-api-v1 (with Fabric API): on the integrated server, a Fabric mod's storages, on blocks, in tank items and in bags, are Forge's capabilities and a Forge mod's handlers Fabric's storages"
	check "FABRICATED-FORGE-TEST builtin resource pack enabled by default OK" "fabric-resource-loader-v1 (with Fabric API): a built-in pack a Fabric mod's client entrypoint registers as enabled by default is switched on, as upstream does once the options load, which on Forge is before the entrypoints (CORPUS-104)"
fi
# The test mod for Fabric API's client modules (26.x: mc/forge/common26/src/testmodfrapi), where the run has it.
if [ -n "$extra" ] && [ -f "$extra/fabricated-testmod-frapi.jar" ] && [ "$mc" != 1.21.11 ]; then
	check "FABRICATED-TEST key mapping OK" "fabric-key-mapping-api-v1 (native, with Fabric API's jar too): the client API test mod's key mapping is in the game's options (KEYMAP-3)"
	check "FABRICATED-TEST hud element OK" "fabric-rendering-v1 (with Fabric API): a Fabric HUD element added last was drawn"
	check "FABRICATED-TEST hud element after chat OK" "fabric-rendering-v1 (with Fabric API): a Fabric HUD element attached after chat was drawn, from Forge's HUD layers (HUD-2)"
	check "FABRICATED-TEST hud element before the hotbar OK" "fabric-rendering-v1 (with Fabric API): a Fabric HUD element attached before the hotbar was drawn, from Forge's hotbar layers (HUD-2)"
	check "FABRICATED-TEST screen init event OK" "fabric-screen-api-v1 (with Fabric API): a screen's initialization fired Fabric's event"
	check "FABRICATED-TEST screen draw event OK" "fabric-screen-api-v1 (with Fabric API): a screen's drawing fired Fabric's event, from Forge's screen render events (SCREEN-1)"
	check "FABRICATED-TEST screen key press event OK" "fabric-screen-api-v1 (with Fabric API): a key press sent to the shown screen asked allowKeyPress, from Forge's hook, and its refusal held (SCREEN-1)"
	check "FABRICATED-TEST screen key release event OK" "fabric-screen-api-v1 (with Fabric API): a key release sent to the shown screen fired afterKeyRelease, from Forge's hook (SCREEN-1)"
	check "FABRICATED-TEST screen character event OK" "fabric-screen-api-v1 (with Fabric API): a character typed into the shown screen asked allowCharType, from Forge's hook (SCREEN-1)"
	check "FABRICATED-TEST screen mouse click event OK" "fabric-screen-api-v1 (with Fabric API): a mouse button pressed over the shown screen asked allowMouseClick, from Forge's event factory (SCREEN-1)"
	check "FABRICATED-TEST screen mouse release event OK" "fabric-screen-api-v1 (with Fabric API): a mouse button released over the shown screen asked allowMouseRelease, from Forge's event factory (SCREEN-1)"
	check "FABRICATED-TEST client commands OK" "fabric-command-api-v2 (with Fabric API): Fabric's client commands were registered on joining the world"
	check "FABRICATED-TEST client stopping OK" "fabric-lifecycle-events-v1 (with Fabric API): CLIENT_STOPPING fired as the game stopped"
	if [ "$fail" = 1 ]; then
		echo "::group::The client API test mod's lines in the game log"
		grep -n -m 60 -E "fabricated-testmod-frapi|FABRICATED-TEST|ScreenInputEvents|ScreenDrawEvents" "$log" || true
		echo "::endgroup::"
	fi
fi
# Fabric's renderer API (task 4, R5): the renderer test mod's blocks and item, drawn through upstream's hooks, which
# take Forge's code where they can, and on Forge 61 through Fabricated's for moving blocks, the breaking overlay and
# blocks drawn as entities, which upstream's can't reach there. Each block reaches Fabric's renderer through Fabricated's
# guard (seam=Fabricated: FRAPI rule 8), and a Fabric model that throws there (alone, as a multipart model's part, when
# drawn as an entity, or inside a model wrapping it) fails alone: it is drawn from its parts, its failure is logged once
# and listed in the report, a wrapper whose wrapped model threw isn't blamed, and the transform it left pushed never
# reaches another block. A Fabric block model's level-aware particle sprite reaches the screen effect inside it, its
# block markers and its breaking particles, where Forge asks the model with its model data (PART-4). Gates.
# Under another renderer of Fabric's API (Sodium's Fabric build, run with the test mod in the difficult mods' job),
# Fabric API's Indigo stands down and that renderer draws Fabric's block models itself: in its own chunk sections, which
# it meshes, and wherever it is asked to draw a block. Fabricated's guard (FRAPI rule 8) is its seam into Indigo, and
# the test mod's broken models throw only through it, so its checks are Indigo's; under the other renderer the test
# mod's Fabric block model must reach that renderer's chunk sections, and the rest is printed.
other_renderer=
if [ -n "$extra" ] && ls "$extra"/sodium*.jar >/dev/null 2>&1; then other_renderer=Sodium; fi
if [ -n "$extra" ] && [ -f "$extra/fabricated-testmod-frapi.jar" ] && [ -n "$other_renderer" ]; then
	frapi_fail=$fail
	fail=0
	check "Different rendering plugin detected; not applying Indigo" "fabric-renderer-api-v1 (with Fabric API): Fabric API's renderer stood down for $other_renderer's"
	check "FABRICATED-TEST frapi terrain OK .*\(LevelSlice\|WorldSlice\)" "fabric-renderer-api-v1 (with Fabric API): $other_renderer meshed a Fabric block model into its chunk sections, through its renderer"
	echo "::group::Fabric's renderer API under $other_renderer (informational)"
	grep -n -m 80 -E "FABRICATED-TEST (frapi|model)|threw while drawn in|Fabric block models|fabric-renderer|indigo|Indigo|Indium" "$log" || true
	echo "::endgroup::"
	if [ "$frapi_fail" = 1 ]; then fail=1; fi
elif [ -n "$extra" ] && [ -f "$extra/fabricated-testmod-frapi.jar" ]; then
	frapi_fail=$fail
	fail=0
	check "FABRICATED-TEST frapi terrain OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model was meshed into a chunk section, through Fabricated's guard"
	check "FABRICATED-TEST frapi nested terrain OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric model inside a vanilla multipart model was meshed, through Fabricated's guard"
	check "FABRICATED-TEST frapi moving block OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a falling Fabric block model was drawn as a moving block, through Fabricated's guard"
	check "FABRICATED-TEST frapi breaking OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model got the breaking overlay, through Fabricated's guard"
	check "FABRICATED-TEST frapi entity block OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model carried by a minecart was drawn as the minecart's block, through Fabricated's guard"
	check "FABRICATED-TEST frapi item screen OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in the hotbar"
	check "FABRICATED-TEST frapi item hand OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in the player's hand"
	check "FABRICATED-TEST frapi item dropped OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn as a dropped item"
	check "FABRICATED-TEST frapi item frame OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in an item frame"
	# Forge 65 draws no block's screen effect: its patch submits it only where a listener cancelled Forge's event for it
	# (RenderBlockScreenEffectEvent), where Forge 64's draws it unless one did, so no block shows one there.
	if [ "$mc" != 26.2 ]; then
		check "FABRICATED-TEST frapi screen effect sprite OK" "fabric-renderer-api-v1 (with Fabric API): the screen effect inside a Fabric block model showed its level-aware particle sprite (PART-4)"
		# Where it didn't: what the test met, which the renderer test mod logs as the game stops.
		grep -m 3 -E "FABRICATED-TEST frapi screen effect (not asked|: )" "$log" | cut -c1-1200 || true
	fi
	check "FABRICATED-TEST frapi block marker sprite OK" "fabric-renderer-api-v1 (with Fabric API): a block marker of a Fabric block model took its level-aware particle sprite (PART-4)"
	check "FABRICATED-TEST frapi block particle sprite OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model's breaking particles kept its level-aware particle sprite, which Forge sets again (PART-4)"
	if [ "$mc" = 1.21.11 ]; then
		check "FABRICATED-TEST frapi outline extraction event OK" "fabric-rendering-v1 (with Fabric API): WorldRenderEvents.AFTER_BLOCK_OUTLINE_EXTRACTION fired, from upstream's hook in a method Forge 61 gave a parameter (SIG-3)"
		check "FABRICATED-TEST frapi end extraction event OK" "fabric-rendering-v1 (with Fabric API): WorldRenderEvents.END_EXTRACTION fired, from upstream's hooks sharing that hook's extraction context (SIG-3)"
	fi
	if [ "$mc" != 1.21.11 ]; then
		check "FABRICATED-TEST model deserializer OK" "fabric-model-loading-api-v1 (with Fabric API): a model file of a Fabric type was read by its Fabric deserializer"
	fi
	# 26.1.2 draws no outline around moving blocks. On 26.3 the check is a gate since it held on both of v0.6.0-beta's
	# release head extended runs (task 7, CI-1).
	if [ "$mc" = 26.2 ] || [ "$mc" = 26.3 ]; then
		check "FABRICATED-TEST frapi moving block outline OK" "fabric-renderer-api-v1 (with Fabric API): a glowing falling Fabric block model's outline was drawn by Fabric's renderer"
	fi
	check "FABRICATED-TEST frapi broken model fallback OK" "FRAPI rule 8: a Fabric model that threw in a chunk section was drawn from its parts"
	check "FABRICATED-TEST frapi broken part fallback OK" "FRAPI rule 8: a multipart model whose Fabric part threw was drawn, the part from its parts"
	check "FABRICATED-TEST frapi broken entity model fallback OK" "FRAPI rule 8: a Fabric model that threw when drawn as a minecart's block was drawn from its parts"
	check "FABRICATED-TEST frapi wrapped model fallback OK" "FRAPI rule 8: a block whose Fabric model wraps one that threw was drawn from its parts that time"
	check "FABRICATED-TEST frapi wrapper kept OK" "FRAPI rule 8: a Fabric model wrapping one that threw kept Fabric's renderer, the throw being the wrapped model's (Continuity)"
	for model in BrokenModel BrokenPartModel BrokenEntityModel; do
		count=$(grep -c "Fabric block models of class [^ ]*\$$model (.*) threw while drawn in" "$log" || true)
		if [ "$count" = 1 ]; then
			echo "PASS: FRAPI rule 8: the failure of $model was logged once"
		else
			echo "FAIL: FRAPI rule 8: the failure of $model was logged $count times, not once"
			fail=1
		fi
	done
	count=$(grep -c "A model wrapped by a Fabric block model of class [^ ]*\$WrapperCheckModel (.*) threw while drawn in" "$log" || true)
	if [ "$count" = 1 ]; then
		echo "PASS: FRAPI rule 8: the wrapped model's failure was logged once, naming its wrapper"
	else
		echo "FAIL: FRAPI rule 8: the wrapped model's failure was logged $count times, not once"
		fail=1
	fi
	if grep -q "Fabric block models of class [^ ]*\$WrapperCheckModel (" "$log"; then
		echo "FAIL: FRAPI rule 8: the wrapper's class was marked broken for a throw of the model it wraps"
		fail=1
	fi
	if grep -q "threw while drawn in" run/logs/fabric-bridge-report.txt 2>/dev/null; then
		echo "PASS: FRAPI rule 8: the report lists the failures"
	else
		echo "FAIL: FRAPI rule 8: the report doesn't list the broken models' failures"
		fail=1
	fi
	if grep -q "FABRICATED-TEST frapi leaked transform FAILED" "$log"; then
		echo "FAIL: FRAPI rule 8: a transform a broken model left pushed reached another block"
		fail=1
	fi
	if [ "$fail" = 1 ]; then
		echo "::group::The renderer test mod's lines in the game log"
		grep -n -m 80 -E "FABRICATED-TEST (frapi|model)|FabricBlockRendering|fabricated-forge-frapi|threw while drawn in|fabric-renderer|indigo|Indigo|/(ERROR|FATAL)\]" "$log" || true
		echo "::endgroup::"
	fi
	if [ "$frapi_fail" = 1 ]; then fail=1; fi
fi
if [ -n "$extra" ] && [ "$mc" = 1.21.11 ]; then
	check "FABRICATED-FORGE-TEST colour providers OK" "fabric-rendering-v1 (with Fabric API, 1.21.11): its colour provider registry finds grass's provider in Forge's block colours, and none for stone (COLOR-1)"
	check "FABRICATED-FORGE-TEST block dust tint event OK" "fabric-particles-v1 (with Fabric API, 1.21.11): a stone particle made as the client joins asks ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT, where Forge decides the tint (PART-2)"
	check "FABRICATED-FORGE-TEST hud element after chat OK" "fabric-rendering-v1 (with Fabric API, 1.21.11): a Fabric HUD element attached after chat was drawn, from Forge's HUD layers (HUD-2)"
	check "FABRICATED-FORGE-TEST hud element before the hotbar OK" "fabric-rendering-v1 (with Fabric API, 1.21.11): a Fabric HUD element attached before the hotbar was drawn, from Forge's hotbar layers (HUD-2)"
	check "FABRICATED-FORGE-TEST hud render callback OK" "fabric-rendering-v1 (with Fabric API, 1.21.11): HudRenderCallback fired from upstream's hook at the HUD method's end, after Forge's HUD layers, outside the loading screen (HUD-2, HUD-4)"
fi
exit "$fail"
