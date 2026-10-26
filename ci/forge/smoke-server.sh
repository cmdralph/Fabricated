#!/usr/bin/env bash
# The forge branch's server smoke (docs/PORTING.md §4.1 and §7, F3): installs a Minecraft Forge dedicated server, adds
# the Fabricated jar and the branch's Fabric test mod, boots it and reports what Forge made of them: whether it ran
# Fabricated's locators, took the nested language provider and constructed the in-game mod, whether the translated
# test mod loaded, ran its entrypoints and had its mixin applied, and whether Forge's loading warnings name a Fabric mod
# that wasn't loaded.
#
# Usage: ci/forge/smoke-server.sh <minecraft-version> <forge-version> <work-dir> [extra-mods-dir]
# Exit status: the server must start, and every check that applies to the run must pass; the lines read from debug.log,
# which Forge's debug output words as it likes, are only reported. With extra-mods-dir (the official Fabric API jar), its
# jars join the mods folder.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge/<minecraft-version>/build/testmods/fabricated-testmod.jar
set -euo pipefail

mc="$1"
forge="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
timeout_seconds="${SMOKE_TIMEOUT:-600}"

rm -rf "$work"
mkdir -p "$work"
cd "$work"

echo "::group::Install Forge $mc-$forge"
curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${mc}-${forge}/forge-${mc}-${forge}-installer.jar"
java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -n 60 installer.log; exit 1; }
echo "::endgroup::"

mkdir -p mods
cp "$root"/build/libs/fabricated-forge-*+mc"$mc".jar mods/
cp "$root"/mc/forge/"$mc"/build/testmods/fabricated-testmod.jar mods/
# A Fabric mod whose dependency is missing: Fabricated doesn't load it, and Forge's loading warnings name it.
mkdir unmet
cat > unmet/fabric.mod.json <<'JSON'
{"schemaVersion": 1, "id": "fabricated_forge_unmet", "version": "1.0.0", "depends": {"fabricated_forge_missing": "*"}}
JSON
jar --create --file mods/fabricated-forge-unmet.jar -C unmet fabric.mod.json
# Two Fabric mods that depend on each other, as Fabric allows: the loader's sorter refuses a cycle of load orderings, so
# Fabricated leaves one of theirs out (ORDER-1), and the server must still start.
mkdir -p cycle
for self in a b; do
	other=$([ "$self" = a ] && echo b || echo a)
	printf '{"schemaVersion": 1, "id": "fabricated_cycle_%s", "version": "1.0.0", "depends": {"fabricated_cycle_%s": "*"}}\n' "$self" "$other" > cycle/fabric.mod.json
	jar --create --file "mods/fabricated-cycle-$self.jar" -C cycle fabric.mod.json
done
if [ -n "$extra" ] && [ -d "$extra" ]; then
	cp "$extra"/*.jar mods/ 2>/dev/null || true
fi
ls -1 mods
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
if [ "$status" = "timeout" ] && ! kill -0 "$pid" 2>/dev/null; then
	if crashed; then status="crashed"; else status="exited"; fi
fi
if [ "$status" = "started" ]; then
	sleep 5
	echo "stop" >&3
	for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 2; done
fi
kill "$pid" 2>/dev/null || true
wait "$pid" 2>/dev/null || true
exec 3>&-
echo "::endgroup::"

echo "::group::Server output (tail)"
tail -n 120 server.out || true
echo "::endgroup::"
# Mixin's notice for each config without minVersion is an ERROR line that changes nothing; it filled this group.
echo "::group::First errors in the log"
{ grep -n -E "/(ERROR|FATAL)\]|Exception|Caused by" logs/latest.log 2>/dev/null | grep -v 'does not specify "minVersion"' | head -n 60; } || true
echo "::endgroup::"
# The console has what the game logs before its log files are set up, and the causes of a crash past the tail above.
echo "::group::Mixin failures in the logs"
grep -h -m 20 -E "InjectionError|Invalid[A-Za-z]*Exception|MixinApplyError|MixinInitialisationError|Mixin apply for mod .* failed" \
	server.out logs/latest.log logs/debug.log 2>/dev/null | cut -c1-600 || true
echo "::endgroup::"
for report in crash-reports/*.txt; do
	[ -f "$report" ] || continue
	echo "::group::Crash report $(basename "$report") (first 120 lines)"
	head -n 120 "$report"
	echo "::endgroup::"
done

# What Forge made of the Fabricated jar (docs/PORTING.md §4.1, questions 2, 4 and 5). Forge's discovery logs at debug
# level, which only logs/debug.log keeps.
echo "::group::Forge discovery and Fabricated (debug.log)"
grep -n -i -E "locator|language provider|langprovider|fabricated" logs/debug.log 2>/dev/null | cut -c1-400 | head -n 80 || true
echo "::endgroup::"
echo "::group::Fabricated's report (logs/fabric-bridge-report.txt)"
cat logs/fabric-bridge-report.txt 2>/dev/null || echo "(none)"
echo "::endgroup::"
# Where the installer left Mojang's mappings and the vanilla jar (docs/PORTING.md §4.1, question 3).
echo "::group::libraries/net/minecraft"
find libraries/net/minecraft -maxdepth 4 2>/dev/null | sort | head -n 60 || true
# The mapping file's format, and which names the installer's game jars use (MinecraftServer keeps its name everywhere;
# Level is obfuscated in the vanilla jar).
for f in libraries/net/minecraft/server/"$mc"/*-mappings.*; do
	[ -f "$f" ] || continue
	echo "-- $f (first lines, and methods whose descriptors name classes: which names they use)"
	head -n 8 "$f" | cut -c1-200
	grep -m 6 -E $'^\t[^ ]+ \([^)]*L[a-z]' "$f" | cut -c1-200 || true
	grep -m 3 -E '^net/minecraft/server/MinecraftServer ' "$f" | cut -c1-200 || true
done
for f in libraries/net/minecraft/server/"$mc"/*.jar; do
	[ -f "$f" ] || continue
	entries=$(unzip -Z1 "$f" 2>/dev/null || true)
	echo "-- $f: $(printf '%s\n' "$entries" | grep -c '\.class$') classes; MinecraftServer: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/server/MinecraftServer\.class$'), net/minecraft/world/level/Level: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/world/level/Level\.class$')"
done
echo "::endgroup::"
# A pipeline's last grep reads all its input (no -q): under pipefail, -q's exit at the first match can kill the grep
# feeding it with SIGPIPE, and a line that is there reads as not seen.
report() { if eval "$2"; then echo "PASS: $1"; else echo "NOT SEEN: $1"; fi; }
failed=0
check() { if eval "$2"; then echo "PASS: $1"; else echo "FAIL: $1"; failed=1; fi; }
report "Forge ran Fabricated's mod locator" "grep -qi 'fabricated' logs/debug.log 2>/dev/null && grep -i 'locator' logs/debug.log | grep -i 'fabricated' > /dev/null"
report "Forge took Fabricated's language provider" "grep -i -E 'language|lang' logs/debug.log 2>/dev/null | grep -i 'fabricated' > /dev/null"
check "Fabricated's discovery ran" "grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null"
check "Fabricated handed Forge the translated test mod" "grep -q 'Fabricated handed Forge [1-9]' logs/latest.log 2>/dev/null"
check "Forge constructed Fabricated's in-game mod" "grep -q 'Fabricated ran the Fabric pre-launch entrypoints' logs/latest.log 2>/dev/null"
check "the in-game mod's resources load (its pack.mcmeta)" "! grep -q 'Missing metadata in pack mod:fabricated\$' logs/latest.log 2>/dev/null"
check "the test mod's resources load (Forge reads no pack without metadata)" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null && ! grep -q 'Missing metadata in pack mod:fabricated_forge_testmod' logs/latest.log 2>/dev/null"
check "the test mod's main entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null"
check "the test mod's server entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST server entrypoint OK' logs/latest.log 2>/dev/null"
check "the test mod's mixin applied (Mixin read the overlay's MixinConfigs)" "grep -q 'FABRICATED-FORGE-TEST mixin OK' logs/latest.log 2>/dev/null"
check "Forge took Fabricated's transformation service" "grep -q 'Fabricated transforms [1-9][0-9]* class(es) for Fabric mods before Mixin' logs/latest.log 2>/dev/null"
check "the test mod's injected interface is on MinecraftServer (inject-interface)" "grep -q 'FABRICATED-FORGE-TEST injected interface OK' logs/latest.log 2>/dev/null"
check "the test mod's client-only method was stripped (@Environment)" "grep -q 'FABRICATED-FORGE-TEST environment strip OK' logs/latest.log 2>/dev/null"
report "Forge added MixinExtras for the test mod" "grep -i 'mixinextras' logs/debug.log 2>/dev/null | grep -i -E 'select|load|add' > /dev/null"
check "the Fabric main entrypoints ran in Forge's registry events" "grep -q 'Fabricated ran the Fabric main entrypoints of [1-9][0-9]* mod(s) on Forge (0 failed)' logs/latest.log 2>/dev/null"
check "the test mod registered an item" "grep -q 'FABRICATED-FORGE-TEST registry OK' logs/latest.log 2>/dev/null"
check "content built during vanilla's bootstrap is registered by the main initializer, as BetterNether's entity types are (CORPUS-31)" "grep -q 'FABRICATED-FORGE-TEST content built at bootstrap registered OK' logs/latest.log 2>/dev/null"
check "the test mod's item is in the frozen registry at the first tick" "grep -q 'FABRICATED-FORGE-TEST registry entry kept OK' logs/latest.log 2>/dev/null"
check "tag files' fabric:remove and Forge's remove both take entries out of a tag (the in-game mod's mixin)" "grep -q 'FABRICATED-FORGE-TEST tag removal OK' logs/latest.log 2>/dev/null"
check "native fabric-loot-api-v3: Fabric's loot table events change dirt's loot table and drops on Forge's loot loading" "grep -q 'FABRICATED-FORGE-TEST loot OK' logs/latest.log 2>/dev/null"
if [ -n "$extra" ] && [[ "$mc" == 26.* ]]; then
	check "fabric-object-builder-api-v1 (with Fabric API, 26.x): a cow's default armor set through FabricDefaultAttributeRegistry.MODIFY is what the game reads (Forge's attribute modification event)" "grep -q 'FABRICATED-FORGE-TEST default attributes OK' logs/latest.log 2>/dev/null"
fi
if [ -n "$extra" ] && { [ "$mc" = 26.2 ] || [ "$mc" = 26.3 ]; }; then
	check "fabric-advancement-api-v1 (with Fabric API, 26.2 and 26.3): a vanilla advancement's reward set through AdvancementEvents.MODIFY is what the server loads (on Forge 66 through the in-game mod's hook on Forge's conditional decoding)" "grep -q 'FABRICATED-FORGE-TEST advancement modify OK' logs/latest.log 2>/dev/null"
fi
check "a Fabric mod's fluids have Forge fluid types (the in-game mod's, registered under their ids), and a pig falls through one that isn't water as through air" "grep -q 'FABRICATED-FORGE-TEST fluid types OK' logs/latest.log 2>/dev/null"
check "Forge's loading warnings name the Fabric mod that wasn't loaded" "grep -qsE 'ServerModLoader.*: Fabric mod fabricated_forge_unmet 1\\.0\\.0 was not loaded: ' logs/latest.log logs/debug.log"
check "an access widener leaves a field private that Forge's coremod reads through a getter: the test mod's BucketItem.content (PLAT-4)" "grep -q 'BucketItem.content must stay private on Forge' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -qsE 'Field (net/minecraft/world/item/BucketItem\.)?content is not private' logs/latest.log server.out"
check "two Fabric mods that depend on each other load, one ordering of their cycle left out (ORDER-1)" "grep -q 'fabricated_cycle_b doesn.t load after fabricated_cycle_a' logs/fabric-bridge-report.txt 2>/dev/null"
check "Fabricated's report names Forge as the loader" "grep -q '^  Forge: ' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -q '^  NeoForge: ' logs/fabric-bridge-report.txt 2>/dev/null"

echo "Server status: $status"
[ "$status" = "started" ] && [ "$failed" = 0 ]
