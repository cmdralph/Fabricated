#!/usr/bin/env bash
# The forge-legacy branch's client smoke (Forge 47): installs a real Minecraft Forge client with the official installer,
# adds Fabricated and the branch's Fabric test mod, and starts it headlessly (Xvfb) with HeadlessMC. mc-runtime-test's
# Forge build creates a singleplayer world, waits until the player's chunk is loaded and quits; the test then checks that
# the Fabric mod's main and client entrypoints ran, in Fabric's order, with Fabric Loader's game instance set.
#
# Usage: ci/forge-legacy/smoke-client.sh <minecraft-version> <forge-version> <work-dir> [extra-mods-dir]
# Exit status: the client must reach a world and exit normally, and every check must pass. With extra-mods-dir (the
# official Fabric API jar, and the renderer test mod, whose checks then apply), its jars join the mods folder.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and
# mc/forge-legacy/<minecraft-version>/build/testmods/fabricated-testmod.jar,
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
# SMOKE_JVM_ARGS: more of the game's JVM options (system properties, separated by spaces, none holding one), for a
# corpus run's evidence (Mixin's export of a class as it leaves it, CORPUS-76).
jvm_args="-Djava.awt.headless=true -DMcRuntimeGameTest=false${SMOKE_JVM_ARGS:+ $SMOKE_JVM_ARGS}"
hmc_version="2.10.0"
mcrt_version="4.5.2"

rm -rf "$work"
mkdir -p "$work/HeadlessMC" "$work/run/mods" "$work/run/config" "$work/minecraft"
cd "$work"

echo "::group::Install Forge $forge (client)"
install_client() {
	# The installer only checks that a launcher profile file exists.
	echo '{"profiles":{}}' > minecraft/launcher_profiles.json
	curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${forge}/forge-${forge}-installer.jar"
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
cached_install "forge-legacy-client-$forge" minecraft install_client
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
cp "$root"/mc/forge-legacy/"$mc"/build/testmods/fabricated-testmod.jar run/mods/
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
# A key saved for the test mod's key binding, which it must have once the game has started (KEYMAP-3). The file then
# names the game's data version (its client jar's): without one, the game takes it for a file from before 1.13, whose
# data fixer reads each key as an LWJGL 2 key code, fails on a key's name, and drops the whole file (as on forge).
# Should the jar not say, the key isn't saved, and the key binding check fails on it.
if data_version=$(python3 -I -c 'import json, sys, zipfile; print(json.loads(zipfile.ZipFile(sys.argv[1]).read("version.json"))["world_version"])' \
		"minecraft/versions/$mc/$mc.jar"); then
	printf '%s\n' "version:$data_version" "key_key.fabricated-forge-testmod.check:key.keyboard.k" >> run/options.txt
else
	echo "::warning::The client jar's data version wasn't read; no key is saved for the test mod's key binding"
fi
cat run/options.txt

echo "::group::Run client"
for attempt in 1 2; do
	status=0
	started=$SECONDS
	rm -rf run/logs run/crash-reports client.out stop-reason.txt thread-dump.txt
	export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
	timeout "$timeout_seconds" xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset" java -Dhmc.check.xvfb=true -jar hmc.jar \
		--command launch "${mc}-forge" -regex --jvm "$jvm_args" \
		> client.out 2>&1 &
	client_pid=$!
	descendants() {
		for child in $(pgrep -P "$1" || true); do
			echo "$child"
			descendants "$child"
		done
	}
	finished_at=""
	# What every game thread is doing, so a hang can be diagnosed from the log. SIGQUIT makes a JVM print every thread's
	# stack to its output (client.out) and carry on. Every Java process the launcher started, the game's included; a
	# shell would quit on it.
	dump_threads() {
		for pid in $(descendants "$client_pid"); do
			if [ "$(cat "/proc/$pid/comm" 2>/dev/null)" = java ]; then kill -QUIT "$pid" 2>/dev/null || true; fi
		done
		sleep 3
		awk '/^Full thread dump/ { dump = 1 } dump' client.out > thread-dump.txt || true
	}
	while kill -0 "$client_pid" 2>/dev/null; do
		# A game stuck shutting down after it reached its world: its threads, then it is stopped, and the run fails as a
		# timeout would.
		if [ -z "$finished_at" ] && grep -q "Successfully finished\." client.out run/logs/latest.log 2>/dev/null; then
			finished_at=$SECONDS
		fi
		if [ -n "$finished_at" ] && [ $((SECONDS - finished_at)) -ge "$exit_timeout" ]; then
			echo "The game reached its world but didn't exit within $exit_timeout seconds; stopping the client."
			echo "The game didn't exit within $exit_timeout seconds of reaching its world (mc-runtime-test's stop)" > stop-reason.txt
			dump_threads
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
	echo "::group::Thread dump of a game stuck shutting down (game threads)"
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
check "FABRICATED-FORGE-TEST content built at bootstrap registered OK" "content built during vanilla's bootstrap is registered by the main initializer (CORPUS-31)"
check "FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK" "the built-in registries' freeze runs after the main initializers (CORPUS-32)"
if grep -qs "was empty after loading" "$log" client.out; then
	echo "FAIL: vanilla's check of the built-in registries logged an empty registry (REG-6)"; fail=1
else
	echo "PASS: vanilla's check of the built-in registries, which Fabric API moves to the client's start, passes over Forge's empty registries (REG-6)"
fi
# The test mod's two block models name loaders Forge lacks. One is in a Fabric mod's namespace: Forge leaves it to the
# Fabric mods' own hooks into vanilla's model reader, as on Fabric, and the model loads. The other is in Forge's
# namespace: Forge fails that model as without Fabricated (CORPUS-92).
check "Model loader fabricated-forge-testmod:own_loader is neither Forge's nor a Forge mod's" "a block model naming a loader Forge lacks, in a Fabric mod's namespace, is left to the Fabric mods' model hooks, as on Fabric (CORPUS-92)"
if grep -qs "Failed to load model fabricated-forge-testmod:models/block/fabric_loader.json" "$log" client.out; then
	echo "FAIL: Forge failed the test mod's block model whose loader is in a Fabric mod's namespace (CORPUS-92)"; fail=1
else
	echo "PASS: the test mod's block model whose loader is in a Fabric mod's namespace loaded (CORPUS-92)"
fi
check "Model loader 'forge:fabricated_testmod_absent' not found" "a block model naming a loader Forge lacks, in Forge's namespace, fails as without Fabricated (CORPUS-92)"
check "FABRICATED-FORGE-TEST constructor hook OK" "the test mod's @Inject at a call in Commands' constructor ran, which Forge 47's Mixin takes only at a return (CTOR-4)"
check "FABRICATED-FORGE-TEST constructor construction hook OK" "the test mod's @Inject just after Commands' constructor stores its dispatcher ran and saw it, shifted by 2 as Dripstone Fluid Lib's hook into Minecraft's constructor is (CORPUS-103)"
check "FABRICATED-FORGE-TEST particles OK" "native fabric-particles-v1: the particle engine finds the test mod's factory, kept by the type's id as Forge keeps them, and its sprite provider holds its sprite in the particle atlas (PART-1), and the providers by the type's raw id, as Porting Lib's accessor reads them through the in-game mod's bridge, hold its factory (CORPUS-37)"
check "FABRICATED-FORGE-TEST key binding OK" "native fabric-key-binding-api-v1: a key binding a Fabric mod registers from its client entrypoint is in the game's options, sorted after the game's, refused twice and once Forge's event added it, as upstream, and has the key the options file saved for it (KEYMAP-3)"
if [ -n "$extra" ]; then
	check "FABRICATED-FORGE-TEST networking round trip OK" "fabric-networking-api-v1 (with Fabric API): a ping the client sends on a channel as it joins comes back from the integrated server with the server's answer"
	check "FABRICATED-FORGE-TEST Fabric command registered OK" "fabric-command-api-v2 (with Fabric API): a command registered through CommandRegistrationCallback is in the integrated server's dispatcher (CTOR-4)"
	check "FABRICATED-FORGE-TEST block break events OK" "fabric-events-interaction-v0 (with Fabric API): a fake player's breaks on the integrated server fire PlayerBlockBreakEvents, and a BEFORE refusal keeps the block (BREAK-1)"
	check "FABRICATED-FORGE-TEST interaction events OK" "fabric-events-interaction-v0 (with Fabric API): on the integrated server, a fake player's uses and attacks ask the use and attack callbacks, and their answers hold"
	check "FABRICATED-FORGE-TEST sleep events OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, a hay block EntitySleepEvents.ALLOW_BED admits stays hay while an armor stand sleeps on it (SLEEP-1), and a player may sleep there in the daytime ALLOW_SLEEP_TIME allows (SLEEP-2)"
	check "FABRICATED-FORGE-TEST storages OK" "fabric-transfer-api-v1 (with Fabric API): on the integrated server, a Fabric mod's storages, on blocks and in tank items, are Forge's capabilities and a Forge mod's handlers Fabric's storages"
	check "FABRICATED-FORGE-TEST fuel registry OK" "fabric-content-registries-v0 (with Fabric API): on the integrated server, an item FuelRegistry made a fuel is a fuel to Forge's furnace (FUEL-1)"
	check "FABRICATED-FORGE-TEST brewing recipe OK" "fabric-content-registries-v0 (with Fabric API): on the integrated server, a FabricBrewingRecipeRegistry recipe brews (BREW-1)"
	check "FABRICATED-FORGE-TEST colour providers OK" "fabric-rendering-v1 (with Fabric API): its colour provider registry finds grass's provider in Forge's block colours, and none for stone (COLOR-1)"
	check "FABRICATED-FORGE-TEST model bake events OK" "fabric-model-loading-api-v1 (with Fabric API): a model loading plugin's before-bake and after-bake modifiers see the models baked, whose code Forge moved into an overload taking the sprite getter (MODEL-3)"
	check "FABRICATED-FORGE-TEST screen mouse and key events OK" "fabric-screen-api-v1 (with Fabric API): a click and a key press on a screen, and their releases, ask ScreenMouseEvents and ScreenKeyboardEvents, whose hooks Forge's mouse and key lambdas take through bridges (LAMBDA-7)"
	check "FABRICATED-FORGE-TEST hud render callback OK" "fabric-rendering-v1 (with Fabric API): HudRenderCallback fired from Forge's HUD drawing, which takes the place of vanilla's method upstream fires it from (HUD-2)"
	check "FABRICATED-FORGE-TEST builtin resource pack enabled by default OK" "fabric-resource-loader-v0 (with Fabric API): a built-in pack a Fabric mod's client entrypoint registers as enabled by default is switched on, as upstream does once the options load, which on Forge is before the entrypoints (CORPUS-104)"
fi
# Fabric's renderer API (task 4, R5): the renderer test mod's blocks and item. Upstream's hooks miss Forge 47's chunk
# section compiler and block renderers, so Fabricated's send each block through Fabric's renderer (Indigo), with FRAPI
# rule 8's guard (seam=Fabricated): in chunk sections, as a falling block, as a block a piston moves and with the
# breaking overlay. Items are drawn by Indigo's own item hook. A Fabric model that throws there (alone, as a multipart
# model's part, or inside a model wrapping it) fails alone: Forge's renderer draws it from its quads, its failure is
# logged once and listed in the report, a wrapper whose wrapped model threw isn't blamed, and the transform it left
# pushed never reaches another block. Gates.
if [ -n "$extra" ] && [ -f "$extra/fabricated-testmod-frapi.jar" ]; then
	frapi_fail=$fail
	fail=0
	check "FABRICATED-TEST frapi model plugin OK" "fabric-model-loading-api-v1 (with Fabric API): the renderer test mod's model plugin put a Fabric model in place of a baked block model"
	check "FABRICATED-TEST frapi terrain OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model was meshed into a chunk section, through Fabricated's hook"
	check "FABRICATED-TEST frapi nested terrain OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric model inside a vanilla multipart model was meshed, through Fabricated's hook"
	check "FABRICATED-TEST frapi moving block OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a falling Fabric block model was drawn as a moving block, through Fabricated's hook"
	check "FABRICATED-TEST frapi piston OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model a piston moved was drawn, through Fabricated's hook"
	check "FABRICATED-TEST frapi breaking OK .*seam=Fabricated" "fabric-renderer-api-v1 (with Fabric API): a Fabric block model got the breaking overlay, through Fabricated's hook"
	check "FABRICATED-TEST frapi item screen OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in the hotbar"
	check "FABRICATED-TEST frapi item hand OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in the player's hand"
	check "FABRICATED-TEST frapi item dropped OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn as a dropped item"
	check "FABRICATED-TEST frapi item frame OK" "fabric-renderer-api-v1 (with Fabric API): a Fabric item model's quads were drawn in an item frame"
	check "FABRICATED-TEST frapi broken model fallback OK" "FRAPI rule 8: a Fabric model that threw in a chunk section was drawn by Forge's renderer"
	check "FABRICATED-TEST frapi broken part fallback OK" "FRAPI rule 8: a multipart model whose Fabric part threw was drawn by Forge's renderer"
	check "FABRICATED-TEST frapi wrapped model fallback OK" "FRAPI rule 8: a block whose Fabric model wraps one that threw was drawn by Forge's renderer that time"
	check "FABRICATED-TEST frapi wrapper kept OK" "FRAPI rule 8: a Fabric model wrapping one that threw kept Fabric's renderer, the throw being the wrapped model's (Continuity)"
	for model in BrokenModel BrokenPartModel; do
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
		grep -n -m 80 -E "FABRICATED-TEST (frapi|model)|FabricBlockRendering|fabricated-forge-frapi|threw while drawn in|Fabric block models|fabric-renderer|indigo|Indigo|/(ERROR|FATAL)\]" "$log" || true
		echo "::endgroup::"
	fi
	if [ "$frapi_fail" = 1 ]; then fail=1; fi
fi
exit "$fail"
