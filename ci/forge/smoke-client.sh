#!/usr/bin/env bash
# The forge branch's client smoke: installs a real Minecraft Forge client with the official installer, adds Fabricated
# and the branch's Fabric test mod, and starts it headlessly (Xvfb) with HeadlessMC. mc-runtime-test's Forge build
# creates a singleplayer world, waits until the player's chunk is loaded and quits; the test then checks that the Fabric
# mod's main and client entrypoints ran, in Fabric's order, with Fabric Loader's game instance set.
#
# Usage: ci/forge/smoke-client.sh <minecraft-version> <forge-version> <work-dir> [extra-mods-dir]
# Exit status: the client must reach a world and exit normally, and every check must pass. With extra-mods-dir (the
# official Fabric API jar), its jars join the mods folder.
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
ls -1 run/mods
cat > run/options.txt <<'OPTS'
onboardAccessibility:false
pauseOnLostFocus:false
OPTS

echo "::group::Run client"
for attempt in 1 2; do
	status=0
	started=$SECONDS
	rm -rf run/logs run/crash-reports client.out stop-reason.txt
	export SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1
	timeout "$timeout_seconds" xvfb-run -a -s "-screen 0 1280x720x24 +extension GLX +render -noreset" java -Dhmc.check.xvfb=true -jar hmc.jar \
		--command launch "${mc}-forge" -regex --jvm "-Djava.awt.headless=true -DMcRuntimeGameTest=false" \
		> client.out 2>&1 &
	client_pid=$!
	while kill -0 "$client_pid" 2>/dev/null; do
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
check "FABRICATED-FORGE-TEST registry OK" "the test mod registered an item on the client"
check "FABRICATED-FORGE-TEST content built at bootstrap registered OK" "content built during vanilla's bootstrap is registered by the main initializer (CORPUS-31)"
check "FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK" "the built-in registries' freeze runs after the main initializers (CORPUS-32)"
if [ -n "$extra" ]; then
	check "FABRICATED-FORGE-TEST networking round trip OK" "fabric-networking-api-v1 (with Fabric API): a payload the client sends as it joins comes back from the integrated server with the server's answer"
	check "FABRICATED-FORGE-TEST Fabric command registered OK" "fabric-command-api-v2 (with Fabric API): a command registered through CommandRegistrationCallback is in the integrated server's dispatcher (CTOR-2)"
	check "FABRICATED-FORGE-TEST block break events OK" "fabric-events-interaction-v0 (with Fabric API): a fake player's breaks on the integrated server fire PlayerBlockBreakEvents, and a BEFORE refusal keeps the block (BREAK-1)"
	check "FABRICATED-FORGE-TEST interaction events OK" "fabric-events-interaction-v0 (with Fabric API): on the integrated server, a fake player's uses and attacks ask the use and attack callbacks, and their answers hold"
	check "FABRICATED-FORGE-TEST sleep events OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, a hay block EntitySleepEvents.ALLOW_BED admits stays hay while an armor stand sleeps on it (SLEEP-1)"
	check "FABRICATED-FORGE-TEST damage events OK" "fabric-entity-events-v1 (with Fabric API): on the integrated server, an entity hurt asks ALLOW_DAMAGE, whose refusal holds, and fires AFTER_DAMAGE (DAMAGE-1)"
	check "FABRICATED-FORGE-TEST storages OK" "fabric-transfer-api-v1 (with Fabric API): on the integrated server, a Fabric mod's storages, on blocks and in tank items, are Forge's capabilities and a Forge mod's handlers Fabric's storages"
fi
if [ -n "$extra" ] && [ "$mc" = 1.21.11 ]; then
	check "FABRICATED-FORGE-TEST colour providers OK" "fabric-rendering-v1 (with Fabric API, 1.21.11): its colour provider registry finds grass's provider in Forge's block colours, and none for stone (COLOR-1)"
	check "FABRICATED-FORGE-TEST block dust tint event OK" "fabric-particles-v1 (with Fabric API, 1.21.11): a stone particle made as the client joins asks ParticleRenderEvents.ALLOW_BLOCK_DUST_TINT, where Forge decides the tint (PART-2)"
fi
exit "$fail"
