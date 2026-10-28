#!/usr/bin/env bash
# CI smoke test: installs a real NeoForge dedicated server, adds Fabricated and Fabric test mods,
# boots it, and checks that Fabric mods load and that broken Fabric mods are isolated.
#
# Usage: ci/neoforge/smoke-server.sh <minecraft-version> <neoforge-version> <work-dir> [extra-mods-dir]
# Exit status: every check must pass; with extra-mods-dir, only the server start must succeed, unless it holds the mods
# ci/neoforge/standins.py writes, whose checks gate too. SMOKE_TESTMODS=none runs without the test mods (the start alone
# is checked).
# Expects: build/libs/fabricated-*+mc<minecraft-version>.jar and mc/neoforge/<minecraft-version>/build/testmods/*.jar
set -euo pipefail

mc="$1"
neo="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=ci/install-cache.sh
source "$root/ci/install-cache.sh"
timeout_seconds="${SMOKE_TIMEOUT:-900}"

rm -rf "$work"
mkdir -p "$work"
cd "$work"

echo "::group::Install NeoForge $neo"
install_server() {
	curl -fsSL --retry 4 -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/${neo}/neoforge-${neo}-installer.jar"
	java -jar installer.jar --installServer . > installer.log 2>&1 || { cat installer.log; exit 1; }
}
cached_install "neoforge-server-$neo" . install_server
echo "::endgroup::"

mkdir -p mods config
# Not fabricated-forge-*: a local build of every platform has those in build/libs too.
cp "$root"/build/libs/fabricated-[0-9]*+mc"$mc".jar mods/
# SMOKE_TESTMODS=none leaves the test mods out: a run of other people's mods alone, of which only the start is checked.
testmods="${SMOKE_TESTMODS:-all}"
if [ "$testmods" != none ]; then
	cp "$root"/mc/neoforge/"$mc"/build/testmods/*.jar mods/
	# Two Fabric mods that depend on each other, as Fabric allows: the loader's sorter refuses a cycle of load orderings,
	# so Fabricated leaves one of theirs out (ORDER-1), and the server must still start.
	mkdir -p cycle
	for self in a b; do
		other=$([ "$self" = a ] && echo b || echo a)
		printf '{"schemaVersion": 1, "id": "fabricated_cycle_%s", "version": "1.0.0", "depends": {"fabricated_cycle_%s": "*"}}\n' "$self" "$other" > cycle/fabric.mod.json
		jar --create --file "mods/fabricated-cycle-$self.jar" -C cycle fabric.mod.json
	done
fi
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

# SMOKE_EXPECT_REFUSAL: the launch must stop, with this text in the server's output (Fabricated refusing Sinytra
# Connector or a second Fabricated jar beside it).
if [ -n "${SMOKE_EXPECT_REFUSAL:-}" ]; then
	# The refusal must be what stops the launch: a module error after it is what the player would see (DUP-2).
	if grep -q "ResolutionException" server.out; then
		echo "FAIL: the launch stopped with a module error, not the refusal: $(grep -h -m 1 "ResolutionException" server.out | cut -c1-300)"
		exit 1
	fi
	if [ "$status" != started ] && grep -qF -- "$SMOKE_EXPECT_REFUSAL" server.out; then
		echo "PASS: the launch stopped with: $(grep -hF -m 1 -- "$SMOKE_EXPECT_REFUSAL" server.out | cut -c1-400)"
		exit 0
	fi
	echo "FAIL: the launch should have stopped with '$SMOKE_EXPECT_REFUSAL' (status $status)"
	exit 1
fi

# With extra mods (the official Fabric API jar), only "the server starts" is required: the other checks are
# evidence for the report, since Fabric API's pass-through modules may legitimately change what loads.
fail=0
required_fail=0
# A test mod check another mod's module answers for logs "<check> SKIPPED: <why>" in place of "<check> OK".
check() {
	if grep -q -- "$1" "$2"; then echo "PASS: $3"
	elif [ "${1% OK}" != "$1" ] && grep -q -- "${1% OK} SKIPPED" "$2"; then echo "SKIP: $3 ($(grep -m 1 -o -- "${1% OK} SKIPPED.*" "$2"))"
	else echo "FAIL: $3 (missing '$1' in $2)"; fail=1; fi
}
[ "$status" = "started" ] && echo "PASS: server started" || { echo "FAIL: server did not start ($status)"; fail=1; required_fail=1; }
if [ "$testmods" = none ]; then
	echo "NOTE: no test mods in this run; only the server start is checked."
	exit "$required_fail"
fi
check "FABRICATED-TEST main entrypoint OK" server.out "main entrypoint ran"
check "fabricated_cycle_b doesn.t load after fabricated_cycle_a" logs/fabric-bridge-report.txt "two Fabric mods that depend on each other load, one ordering of their cycle left out (ORDER-1)"
check "FABRICATED-TEST loader api OK" server.out "Fabric Loader API works"
check "FABRICATED-TEST custom entrypoint OK" server.out "custom entrypoint resolved"
check "FABRICATED-TEST added entrypoint OK" server.out "an entrypoint a mixin plugin added to Fabric Loader's internal storage resolves, as FrozenLib's do (LOADER-4)"
check "FABRICATED-TEST classpath scan OK" server.out "classpath scan finds a Fabric mod's package directory (SCAN-1)"
check "FABRICATED-TEST class resources OK" server.out "a mod's class finds another jar's file, as on Fabric (RES-1)"
check "FABRICATED-TEST registry OK" server.out "direct registry registration works"
check "FABRICATED-TEST content built at bootstrap registered OK" server.out "content built during vanilla's bootstrap is registered by the main initializer, as BetterNether's entity types are (CORPUS-31)"
check "FABRICATED-TEST built-in registries frozen after the main initializers OK" server.out "a Fabric mod's mixin into the built-in registries' freeze runs after the main initializers, where Wover makes the item definitions BetterEnd's subscribes to (CORPUS-32)"
check "FABRICATED-TEST late registry OK" server.out "a registry vanilla's code adds while mods start joins the root registry (REG-3)"
check "FABRICATED-TEST early registry writes OK" server.out "vanilla's code writes to built-in registries before NeoForge's registration events, as Wover does (REG-3)"
check "FABRICATED-TEST immutable pack sources OK" server.out "NeoForge adds its pack finders to sources a mod left immutable, as FrozenLib does (PACK-1)"
check "FABRICATED-TEST data without NeoForge conditions OK" server.out "a Fabric jar's data loads without the conditions only its NeoForge build reads (DATA-1)"
check "FABRICATED-TEST built-in pack data without NeoForge conditions OK" server.out "so does its built-in pack's (PACK-2), whose pack.mcmeta has its NeoForge build's sections too (PACK-4)"
check "FABRICATED-TEST built-in pack data is stable OK" server.out "a built-in pack's registry entries aren't experimental: a world with them opens without a confirmation (PACK-3)"
check "FABRICATED-TEST fabric api base event OK" server.out "fabric-api-base events work"
check "FABRICATED-TEST access widener OK" server.out "access widener applied"
check "FABRICATED-TEST server entrypoint OK" server.out "server entrypoint ran"
check "FABRICATED-TEST creative tab order OK" server.out "a creative tab registered by a main initializer is ordered after the vanilla tabs"
check "FABRICATED-TEST resource bundle with a control OK" server.out "translations loaded with a ResourceBundle.Control from a named module (LuckPerms, LOADER-3)"
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
check "FABRICATED-TEST late payload type registration OK" server.out "native networking: a payload type registered as the server starts (Ledger, NET-3)"
check "FABRICATED-TEST resource reload listener OK (2 data file" server.out "native resource loader: listener, shared state and built-in pack data"
check "resource loader state: order=true, store=2, builtinPackEnabled=true" server.out "native resource loader: ordering, data store and built-in pack"
if [ "$(grep -c 'FABRICATED-TEST resource reload listener OK' server.out)" -ge 2 ]; then
	echo "PASS: native resource loader: listener runs again on /reload"
else
	echo "FAIL: native resource loader: listener runs again on /reload"; fail=1
fi
check "FABRICATED-TEST registry sync: custom registry OK" server.out "native registry sync: custom registry, entry callback, attributes, NeoForge sync flag"
check "FABRICATED-TEST registry sync: tracked registry OK" server.out "a registry NeoForge tracks, made in a Fabric initializer after NeoForge's NewRegistryEvent, as Forgified Fabric API's builder makes one (PROVIDER-6)"
check "registry sync state: alias=true, dynamic=1, setupCallback=1" server.out "native registry sync: alias, synced dynamic registry, setup callback"
if [ "$mc" = "1.21.1" ]; then
	# Fabric API 0.116 (1.21.1) has no mob effect events; its mob conversion event is tested instead.
	check "FABRICATED-TEST entity events: mob conversion OK" server.out "native entity events: mob conversion"
else
	check "entity events seen:.*allow_add,after_add,before_remove" server.out "native entity events: mob effects"
fi
check "entity events seen:.*allow_damage,after_damage" server.out "native entity events: damage"
check "entity events seen:.*allow_death,after_death$" server.out "native entity events: death (no AFTER_DAMAGE for the killing hit)"
check "FABRICATED-TEST object builder OK" server.out "native object builder: attributes, attribute modification, spawn placement, entity data serializer, block entity types"
check "FABRICATED-TEST block api OK" server.out "native block api: properties id, trapdoor tag, appearance (26.2+: enchanting power, fluid flow event)"
check "FABRICATED-TEST item api OK" server.out "native item api: equipment slot, crafting remainder, allow enchanting, custom damage, default components, creator mod"
check "FABRICATED-TEST interaction events OK" server.out "native interaction events: use/attack block, item and entity callbacks, block and item events, block break events, pick item, fake player"
check "FABRICATED-TEST loot OK" server.out "native loot: replace/modify with source, all loaded, modified drops"
check "FABRICATED-TEST tag removal OK" server.out "tag files' fabric:remove and NeoForge's remove both take entries out of a tag"
check "FABRICATED-TEST content registries OK" server.out "native content registries: flammable, waxable, vibration frequency (up to 26.2: compostable, strippable, fuel)"
check "FABRICATED-TEST biome modifications OK" server.out "native biome api: an added mob spawn and a climate change reach the biome NeoForge reads (before 26.3: an environment attribute too, and the biome is sent to clients) (BIOME-1)"
check "FABRICATED-TEST transfer OK" server.out "native transfer: item and fluid storages over vanilla blocks and in items, transaction abort and commit"
check "FABRICATED-TEST fluid types OK" server.out "Fabric fluids: a registered NeoForge fluid type per family, named and lit from Fabric's attributes, water-like by tag; placed in the world; entities move through an untagged one as through air"
check "FABRICATED-TEST hidden default OK" server.out "a mixin's private self() doesn't hide the default self() LivingEntity inherits from NeoForge from its subclasses (HIDE-1)"
check "FABRICATED-TEST commands OK" server.out "/fabricated why, disable and enable from the console"
check "FABRICATED-TEST api OK" server.out "public API: Fabricated detected, mod and module status, after-entrypoints callback"
check "FABRICATED-TEST environment stripping OK" server.out "@EnvironmentInterface: a client-only interface is stripped on the dedicated server"
check "fabricated-testmod-missing" logs/fabric-bridge-report.txt "missing dependency reported"
check "fabricated-library-that-does-not-exist" logs/fabric-bridge-report.txt "missing dependency named"
check "LEFT_OUT .*ImpossibleMixin.*fabricatedMethodThatDoesNotExist" logs/fabric-bridge-report.txt "impossible mixin caught by pre-flight and left out"
check "intentional failure for Fabricated CI" logs/fabric-bridge-report.txt "throwing entrypoint isolated"
# The mods ci/neoforge/standins.py writes, which share the game with Fabricated: every check gates their run.
standins=false
if [ -n "$extra" ] && [ -f "$extra/standin-forgified-fabric-api.jar" ]; then
	standins=true
	check '+standin  \[Stand-in Fabric API Base\]' logs/fabric-bridge-report.txt "a Fabric API module another mod provides is that mod's, and Fabricated's copy stands down (PROVIDER-1)"
	check '+standin  \[Stand-in fabric-' logs/fabric-bridge-report.txt "so is one a mod nests, with Fabricated's mixins for it"
	check "standin-loader.jar has its own copy of Fabric Loader's API, which is left out" logs/fabric-bridge-report.txt "another jar's copy of Fabric Loader's API is left out, with its service file (PROVIDER-2)"
	check "Using standin_lib 1.1.0" logs/fabric-bridge-report.txt "of two versions of a library Fabric mods nest, the newer loads"
	if grep -q "== Linkage warnings" logs/fabric-bridge-report.txt; then
		echo "FAIL: the linkage check counts the classes of the modules other mods provide"; { sed -n '/== Linkage warnings/,/^$/p' logs/fabric-bridge-report.txt | head -5; }; fail=1
	else
		echo "PASS: the linkage check counts the classes of the modules other mods provide"
	fi
	# A second launch, up to the end of discovery: every translation comes from the cache.
	if [ "$status" = "started" ]; then
		cp logs/fabric-bridge-report.txt fabric-bridge-report-cold.txt
		exec 3<>console
		./run.sh nogui < console > server-warm.out 2>&1 &
		pid=$!
		for _ in $(seq 1 60); do grep -q "Fabricated discovery finished" server-warm.out 2>/dev/null && break; kill -0 "$pid" 2>/dev/null || break; sleep 2; done
		sleep 2
		# run.sh starts Java as its child.
		pkill -P "$pid" 2>/dev/null || true
		kill "$pid" 2>/dev/null || true
		wait "$pid" 2>/dev/null || true
		exec 3>&-
		echo "Discovery: cold $(grep -o 'discovery finished in [0-9]* ms' server.out | head -1), warm $(grep -o 'discovery finished in [0-9]* ms' server-warm.out | head -1)"
		if grep -q "Fabricated discovery finished" server-warm.out && ! grep -q "translated in" logs/fabric-bridge-report.txt; then
			echo "PASS: a second launch takes every translation from the cache"
		else
			echo "FAIL: a second launch takes every translation from the cache"; { grep "translated in" logs/fabric-bridge-report.txt || true; }; fail=1
		fi
	fi
fi
# The test mods log "FABRICATED-TEST ... FAILED" when one of their own checks fails.
if grep -q "FABRICATED-TEST .*FAILED" server.out; then
	echo "FAIL: a test mod check reported a failure:"; { grep -m 5 "FABRICATED-TEST .*FAILED" server.out || true; }; fail=1
else
	echo "PASS: no test mod check reported a failure"
fi
if [ -n "$extra" ] && ! $standins; then
	[ "$fail" -ne 0 ] && echo "NOTE: failures other than the server start are informational in this run."
	exit "$required_fail"
fi
exit "$fail"
