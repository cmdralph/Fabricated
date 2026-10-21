#!/usr/bin/env bash
# CI smoke test: installs a real NeoForge dedicated server, adds Fabricated and Fabric test mods,
# boots it, and checks that Fabric mods load and that broken Fabric mods are isolated.
#
# Usage: ci/smoke-server.sh <minecraft-version> <neoforge-version> <work-dir> [extra-mods-dir]
# Exit status: every check must pass; with extra-mods-dir, only the server start must succeed.
# Expects: build/libs/fabricated-*+mc<minecraft-version>.jar and mc/<minecraft-version>/build/testmods/*.jar
set -euo pipefail

mc="$1"
neo="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/.." && pwd)"
timeout_seconds="${SMOKE_TIMEOUT:-900}"

rm -rf "$work"
mkdir -p "$work"
cd "$work"

echo "::group::Install NeoForge $neo"
curl -fsSL --retry 4 -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/${neo}/neoforge-${neo}-installer.jar"
java -jar installer.jar --installServer . > installer.log 2>&1 || { cat installer.log; exit 1; }
echo "::endgroup::"

mkdir -p mods config
cp "$root"/build/libs/fabricated-*+mc"$mc".jar mods/
cp "$root"/mc/"$mc"/build/testmods/*.jar mods/
if [ -n "$extra" ] && [ -d "$extra" ]; then
	cp "$extra"/*.jar mods/ 2>/dev/null || true
fi
ls -1 mods

# Keep loading after an entrypoint failure so the test can verify isolation.
cat > config/fabricated.json <<'JSON'
{ "schema": 1, "onEntrypointFailure": "disable", "logLevel": "DEBUG" }
JSON
echo "eula=true" > eula.txt
cat > server.properties <<'PROPS'
online-mode=false
level-type=minecraft\:flat
view-distance=3
simulation-distance=3
spawn-protection=0
max-tick-time=-1
sync-chunk-writes=false
PROPS

echo "::group::Run server"
mkfifo console
# Keep the FIFO open for writing so the server does not see EOF on stdin.
exec 3<>console
chmod +x run.sh
./run.sh nogui < console > server.out 2>&1 &
pid=$!
start=$(date +%s)
status="timeout"
crashed() { grep -qE 'Preparing crash report|Game crashed!|Crash report saved to|FatalStartupException' server.out 2>/dev/null; }
while kill -0 "$pid" 2>/dev/null; do
	if crashed; then status="crashed"; sleep 10; break; fi
	if grep -q 'Done (' server.out 2>/dev/null; then status="started"; break; fi
	if [ $(( $(date +%s) - start )) -gt "$timeout_seconds" ]; then break; fi
	sleep 5
done
# The server may have stopped between two checks: its output says how.
if [ "$status" = "timeout" ] && ! kill -0 "$pid" 2>/dev/null; then
	if crashed; then status="crashed"; else status="exited"; fi
fi
if [ "$status" = "started" ]; then
	# Let a few ticks run so tick-based checks (mixins) can fire, then reload data packs once so reload
	# listeners run a second time.
	sleep 10
	echo "reload" >&3
	sleep 10
	echo "stop" >&3
	for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 2; done
fi
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
exec 3>&-
echo "::endgroup::"

echo "::group::Server output (tail)"
tail -n 200 server.out || true
echo "::endgroup::"
echo "::group::First errors in the log"
grep -n -m 80 -E "/(ERROR|FATAL)\]|Exception|Caused by" logs/latest.log 2>/dev/null || true
echo "::endgroup::"
if [ -n "$extra" ]; then
	echo "::group::Where the Fabric API classes the game could not find come from"
	python3 -I "$root/ci/find-classes.py" logs/latest.log mods || true
	echo "::endgroup::"
fi
for report in crash-reports/*.txt; do
	[ -f "$report" ] || continue
	echo "::group::Crash report $(basename "$report") (first 120 lines)"
	head -n 120 "$report"
	echo "::endgroup::"
done
echo "::group::fabric-bridge-report.txt"
cat logs/fabric-bridge-report.txt || echo "(report missing)"
echo "::endgroup::"

# With extra mods (the official Fabric API jar), only "the server starts" is required: the other checks are
# evidence for the report, since Fabric API's pass-through modules may legitimately change what loads.
fail=0
required_fail=0
check() {
	if grep -q -- "$1" "$2"; then echo "PASS: $3"; else echo "FAIL: $3 (missing '$1' in $2)"; fail=1; fi
}
[ "$status" = "started" ] && echo "PASS: server started" || { echo "FAIL: server did not start ($status)"; fail=1; required_fail=1; }
check "FABRICATED-TEST main entrypoint OK" server.out "main entrypoint ran"
check "FABRICATED-TEST loader api OK" server.out "Fabric Loader API works"
check "FABRICATED-TEST custom entrypoint OK" server.out "custom entrypoint resolved"
check "FABRICATED-TEST classpath scan OK" server.out "classpath scan finds a Fabric mod's package directory (SCAN-1)"
check "FABRICATED-TEST class resources OK" server.out "a mod's class finds another jar's file, as on Fabric (RES-1)"
check "FABRICATED-TEST registry OK" server.out "direct registry registration works"
check "FABRICATED-TEST late registry OK" server.out "a registry vanilla's code adds while mods start joins the root registry (REG-3)"
check "FABRICATED-TEST early registry writes OK" server.out "vanilla's code writes to built-in registries before NeoForge's registration events, as Wover does (REG-3)"
check "FABRICATED-TEST immutable pack sources OK" server.out "NeoForge adds its pack finders to sources a mod left immutable, as FrozenLib does (PACK-1)"
check "FABRICATED-TEST fabric api base event OK" server.out "fabric-api-base events work"
check "FABRICATED-TEST access widener OK" server.out "access widener applied"
check "FABRICATED-TEST server entrypoint OK" server.out "server entrypoint ran"
check "FABRICATED-TEST mixin OK" server.out "mixin applied"
check "FABRICATED-TEST early language adapter OK" server.out "Fabric's start-up order: language adapters created before the mods' mixin plugins load"
check "FABRICATED-TEST lifecycle server started OK" server.out "native lifecycle: SERVER_STARTED"
check "FABRICATED-TEST lifecycle server tick OK" server.out "native lifecycle: END_SERVER_TICK"
check "FABRICATED-TEST lifecycle server stopping OK" server.out "native lifecycle: SERVER_STOPPING"
check "FABRICATED-TEST lifecycle tags loaded OK" server.out "native lifecycle: TAGS_LOADED after the items' default components are bound"
check "FABRICATED-TEST block entity events OK" server.out "native lifecycle: a block entity loads when placed and unloads when broken"
check "FABRICATED-TEST networking registration OK" server.out "native networking: receiver registered"
check "FABRICATED-TEST configuration disconnect OK" server.out "native networking: DISCONNECT for a connection closed during configuration"
check "Registered [0-9]* Fabric payload type" server.out "native networking: payloads handed to NeoForge"
check "FABRICATED-TEST resource reload listener OK (2 data file" server.out "native resource loader: listener, shared state and built-in pack data"
check "resource loader state: order=true, store=2, builtinPackEnabled=true" server.out "native resource loader: ordering, data store and built-in pack"
if [ "$(grep -c 'FABRICATED-TEST resource reload listener OK' server.out)" -ge 2 ]; then
	echo "PASS: native resource loader: listener runs again on /reload"
else
	echo "FAIL: native resource loader: listener runs again on /reload"; fail=1
fi
check "FABRICATED-TEST registry sync: custom registry OK" server.out "native registry sync: custom registry, entry callback, attributes, NeoForge sync flag"
check "registry sync state: alias=true, dynamic=1, setupCallback=1" server.out "native registry sync: alias, synced dynamic registry, setup callback"
check "entity events seen:.*allow_add,after_add,before_remove" server.out "native entity events: mob effects"
check "entity events seen:.*allow_damage,after_damage" server.out "native entity events: damage"
check "entity events seen:.*allow_death,after_death$" server.out "native entity events: death (no AFTER_DAMAGE for the killing hit)"
check "FABRICATED-TEST object builder OK" server.out "native object builder: attributes, attribute modification, spawn placement, entity data serializer, block entity types"
check "FABRICATED-TEST block api OK" server.out "native block api: properties id, trapdoor tag, appearance (26.2+: enchanting power, fluid flow event)"
check "FABRICATED-TEST item api OK" server.out "native item api: equipment slot, crafting remainder, allow enchanting, custom damage, default components, creator mod"
check "FABRICATED-TEST interaction events OK" server.out "native interaction events: use/attack block, item and entity callbacks, block and item events, block break events, pick item, fake player"
check "FABRICATED-TEST loot OK" server.out "native loot: replace/modify with source, all loaded, modified drops"
check "FABRICATED-TEST content registries OK" server.out "native content registries: flammable, waxable, vibration frequency (up to 26.2: compostable, strippable, fuel)"
# fabric-biome-api-v1 is native only on 26.3 (upstream's module elsewhere, which this run doesn't load).
if [ "$mc" = "26.3" ]; then
	check "FABRICATED-TEST biome modifications OK" server.out "native biome api: an added mob spawn and a climate change reach the biome NeoForge reads (BIOME-1)"
fi
check "FABRICATED-TEST transfer OK" server.out "native transfer: item and fluid storages over vanilla blocks and in items, transaction abort and commit"
check "FABRICATED-TEST fluid types OK" server.out "Fabric fluids: a registered NeoForge fluid type per family, named and lit from Fabric's attributes, water-like by tag; placed in the world; entities move through an untagged one as through air"
check "FABRICATED-TEST commands OK" server.out "/fabricated why, disable and enable from the console"
check "FABRICATED-TEST api OK" server.out "public API: Fabricated detected, mod and module status, after-entrypoints callback"
check "FABRICATED-TEST environment stripping OK" server.out "@EnvironmentInterface: a client-only interface is stripped on the dedicated server"
check "fabricated-testmod-missing" logs/fabric-bridge-report.txt "missing dependency reported"
check "fabricated-library-that-does-not-exist" logs/fabric-bridge-report.txt "missing dependency named"
check "LEFT_OUT .*ImpossibleMixin.*fabricatedMethodThatDoesNotExist" logs/fabric-bridge-report.txt "impossible mixin caught by pre-flight and left out"
check "intentional failure for Fabricated CI" logs/fabric-bridge-report.txt "throwing entrypoint isolated"
# The test mods log "FABRICATED-TEST ... FAILED" when one of their own checks fails.
if grep -q "FABRICATED-TEST .*FAILED" server.out; then
	echo "FAIL: a test mod check reported a failure:"; grep "FABRICATED-TEST .*FAILED" server.out | head -n 5; fail=1
else
	echo "PASS: no test mod check reported a failure"
fi
if [ -n "$extra" ]; then
	[ "$fail" -ne 0 ] && echo "NOTE: failures other than the server start are informational in this run."
	exit "$required_fail"
fi
exit "$fail"
