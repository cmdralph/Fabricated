#!/usr/bin/env bash
# The forge branch's server smoke (docs/PORTING.md §4.1 and §7, F3): installs a Minecraft Forge dedicated server, adds
# the Fabricated jar and the branch's Fabric test mod, boots it and reports what Forge made of them: whether it ran
# Fabricated's locators, took the nested language provider and constructed the in-game mod, whether the translated
# test mod loaded, ran its entrypoints and had its mixin applied, and whether Forge's loading warnings name a Fabric mod
# that wasn't loaded.
#
# Usage: ci/forge/smoke-server.sh <minecraft-version> <forge-version> <work-dir> [extra-mods-dir]
# Exit status: the server must start, and every check that applies to the run must pass; the lines read from debug.log,
# which Forge's debug output words as it likes, are only reported. With extra-mods-dir, its jars join the mods folder:
# the official Fabric API jar (fabric-api.jar), whose modules' checks then apply, or the stand-ins ci/forge/standins.py
# writes for mods that share the game (standin-forgified-fabric-api.jar), whose checks then apply instead.
# SMOKE_TESTMODS=none leaves the test mods out; SMOKE_EXPECT_REFUSAL (below) expects the launch to stop instead.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge/<minecraft-version>/build/testmods/fabricated-testmod.jar
set -euo pipefail

mc="$1"
forge="$2"
work="$3"
extra="${4:-}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=ci/install-cache.sh
source "$root/ci/install-cache.sh"
timeout_seconds="${SMOKE_TIMEOUT:-600}"

rm -rf "$work"
mkdir -p "$work"
cd "$work"

echo "::group::Install Forge $mc-$forge"
install_server() {
	curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${mc}-${forge}/forge-${mc}-${forge}-installer.jar"
	java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -n 60 installer.log; exit 1; }
}
cached_install "forge-server-$forge" . install_server
echo "::endgroup::"

mkdir -p mods
# SMOKE_ON_ENTRYPOINT_FAILURE: Fabricated's onEntrypointFailure for the run, "disable" unless set: the run keeps loading
# past a mod whose initializer throws, as the test mod that fails on purpose does (below), and as NeoForge's smoke does.
mkdir -p config
printf '{ "schema": 1, "onEntrypointFailure": "%s" }\n' "${SMOKE_ON_ENTRYPOINT_FAILURE:-disable}" > config/fabricated.json
cp "$root"/build/libs/fabricated-forge-*+mc"$mc".jar mods/
if [ "${SMOKE_TESTMODS:-all}" != none ]; then
	cp "$root"/mc/forge/"$mc"/build/testmods/fabricated-testmod.jar mods/
	# mc/shared's test mod that fails on purpose, after leaving an intrusive holder nothing registers: its failure must be
	# isolated, and the registries' freeze after the entrypoints must drop the holder (CORPUS-42, HOLD-2).
	cp "$root"/mc/forge/"$mc"/build/testmods/fabricated-testmod-throws.jar mods/
	# A Fabric mod whose dependency is missing: Fabricated doesn't load it, and Forge's loading warnings name it.
	mkdir unmet
	cat > unmet/fabric.mod.json <<-'JSON'
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
fi
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

# SMOKE_EXPECT_REFUSAL: the launch must stop, Fabricated refusing Sinytra Connector, a second Fabricated jar or a jar with
# its own copy of Fabric Loader beside it with an error that holds this text, as on NeoForge; with SMOKE_REFUSAL_AT_ONCE,
# the refusal's exception must be what ends the launch, where it otherwise shows in Forge's loading error.
if [ -n "${SMOKE_EXPECT_REFUSAL:-}" ]; then
	# The refusal must be what stops the launch: a module error before it is what the player would see (DUP-2).
	if grep -qE "ResolutionException|LayerInstantiationException|Duplicate key" server.out; then
		echo "FAIL: the launch stopped with a module error, not the refusal: $(grep -hE -m 1 "ResolutionException|LayerInstantiationException|Duplicate key" server.out | cut -c1-300)"
		exit 1
	fi
	refusal=$(grep -h -m 1 -oP '\]: Fabricated: \K.*' server.out 2>/dev/null || true)
	if [ "$status" = started ] || [ -z "$refusal" ] || [[ "$refusal" != *"$SMOKE_EXPECT_REFUSAL"* ]]; then
		echo "FAIL: the launch should have stopped with '$SMOKE_EXPECT_REFUSAL' (status $status; Fabricated logged: ${refusal:-nothing})"
		exit 1
	fi
	if [ -n "${SMOKE_REFUSAL_AT_ONCE:-}" ]; then
		# A copy of Fabric Loader beside Fabricated's: the locator's exception stops the launch before Forge builds a
		# layer, where its error screen would fail on the two copies (PROVIDER-2), so it is what ends the launch.
		if ! grep -qF -- "LaunchRefused: Fabricated: $refusal" server.out; then
			echo "FAIL: the launch didn't stop at once with the refusal: $refusal"
			exit 1
		fi
	# Forge reads the error's text as a message pattern: its crash report must show it as written.
	elif ! grep -qsF -- "$refusal" crash-reports/*.txt; then
		echo "FAIL: Forge's loading error doesn't show the refusal as written: $refusal"
		exit 1
	fi
	echo "PASS: the launch stopped with: $refusal"
	exit 0
fi

# What Forge made of the Fabricated jar (docs/PORTING.md §4.1, questions 2, 4 and 5). Forge's discovery logs at debug
# level, which only logs/debug.log keeps.
echo "::group::Forge discovery and Fabricated (debug.log)"
grep -n -i -E "locator|language provider|langprovider|fabricated" logs/debug.log 2>/dev/null | cut -c1-400 | head -n 80 || true
echo "::endgroup::"
echo "::group::Fabricated's report (logs/fabric-bridge-report.txt)"
cat logs/fabric-bridge-report.txt 2>/dev/null || echo "(none)"
echo "::endgroup::"
# A pipeline's last grep reads all its input (no -q): under pipefail, -q's exit at the first match can kill the grep
# feeding it with SIGPIPE, and a line that is there reads as not seen.
report() { if eval "$2"; then echo "PASS: $1"; else echo "NOT SEEN: $1"; fi; }
failed=0
# Which mods the extra folder holds: Fabric API's jar, or the stand-ins for mods that provide Fabric API modules.
fapi=false
standins=false
if [ -n "$extra" ]; then
	[ -f "$extra/fabric-api.jar" ] && fapi=true
	[ -f "$extra/standin-forgified-fabric-api.jar" ] && standins=true
fi
check() { if eval "$2"; then echo "PASS: $1"; else echo "FAIL: $1"; failed=1; fi; }
report "Forge ran Fabricated's mod locator" "grep -qi 'fabricated' logs/debug.log 2>/dev/null && grep -i 'locator' logs/debug.log | grep -i 'fabricated' > /dev/null"
report "Forge took Fabricated's language provider" "grep -i -E 'language|lang' logs/debug.log 2>/dev/null | grep -i 'fabricated' > /dev/null"
check "Fabricated's discovery ran" "grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null"
check "Fabricated handed Forge the translated test mod" "grep -q 'Fabricated handed Forge [1-9]' logs/latest.log 2>/dev/null"
check "Forge constructed Fabricated's in-game mod" "grep -q 'Fabricated ran the Fabric pre-launch entrypoints' logs/latest.log 2>/dev/null"
check "the in-game mod's resources load (its pack.mcmeta)" "! grep -q 'Missing metadata in pack mod:fabricated\$' logs/latest.log 2>/dev/null"
check "the test mod's resources load (Forge reads no pack without metadata)" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null && ! grep -q 'Missing metadata in pack mod:fabricated_forge_testmod' logs/latest.log 2>/dev/null"
check "the test mod's main entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's registry entries are stable, as on Fabric: its pack has Fabric API's known-pack identity, so a world holding them opens without asking to confirm experimental settings (PACK-5)" "grep -q 'FABRICATED-FORGE-TEST mod pack data is stable OK' logs/latest.log 2>/dev/null"
check "the test mod's server entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST server entrypoint OK' logs/latest.log 2>/dev/null"
check "the test mod's mixin applied (Mixin read the overlay's MixinConfigs)" "grep -q 'FABRICATED-FORGE-TEST mixin OK' logs/latest.log 2>/dev/null"
check "Forge took Fabricated's transformation service" "grep -q 'Fabricated transforms [1-9][0-9]* class(es) for Fabric mods before Mixin' logs/latest.log 2>/dev/null"
check "the test mod's injected interface is on MinecraftServer (inject-interface)" "grep -q 'FABRICATED-FORGE-TEST injected interface OK' logs/latest.log 2>/dev/null"
check "a mixin's @Shadow of a field another mixin adds is that field, as on Fabric, where Forge's Mixin finds only the class's own (CORPUS-74)" "grep -q 'FABRICATED-FORGE-TEST shared mixin field OK' logs/latest.log 2>/dev/null"
check "a mod nested with a NeoForge jar-in-jar list too loads once, translated: Forge's jar-in-jar locator leaves it alone (JIJ-1)" "grep -q 'FABRICATED-FORGE-TEST nested jar loaded once OK' logs/latest.log 2>/dev/null"
check "the test mod's client-only method was stripped (@Environment)" "grep -q 'FABRICATED-FORGE-TEST environment strip OK' logs/latest.log 2>/dev/null"
report "Forge added MixinExtras for the test mod" "grep -i 'mixinextras' logs/debug.log 2>/dev/null | grep -i -E 'select|load|add' > /dev/null"
check "the Fabric main entrypoints ran in Forge's registry events, only the test mod that fails on purpose failing" "grep -q 'Fabricated ran the Fabric main entrypoints of [1-9][0-9]* mod(s) on Forge (1 failed)' logs/latest.log 2>/dev/null"
check "the failed initializer is in the report, as on NeoForge, and in Forge's loading warnings" "grep -q 'Fabric mod fabricated-testmod-throws: main entrypoint' logs/fabric-bridge-report.txt 2>/dev/null"
check "Fabricated's API runs what waits for the Fabric initializers once they ran, the failed mod known (Fabricated.get().afterEntrypoints)" "grep -q 'FABRICATED-FORGE-TEST api after entrypoints OK' logs/latest.log 2>/dev/null"
check "the failing test mod's entrypoint is isolated, its failure logged" "grep -q 'intentional failure for Fabricated CI' logs/latest.log 2>/dev/null"
check "the holder the failing test mod left is dropped at the registries' freeze, with an error (CORPUS-42, HOLD-2)" "grep -q 'built but never registered, left by a Fabric mod whose entrypoint failed' logs/fabric-bridge-report.txt 2>/dev/null"
check "the test mod registered an item" "grep -q 'FABRICATED-FORGE-TEST registry OK' logs/latest.log 2>/dev/null"
check "content built during vanilla's bootstrap is registered by the main initializer, as BetterNether's entity types are (CORPUS-31)" "grep -q 'FABRICATED-FORGE-TEST content built at bootstrap registered OK' logs/latest.log 2>/dev/null"
check "the built-in registries' freeze runs after the main initializers, where Wover makes the item definitions BetterEnd's subscribes to (CORPUS-32)" "grep -q 'FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK' logs/latest.log 2>/dev/null"
check "the test mod's item is in the frozen registry at the first tick" "grep -q 'FABRICATED-FORGE-TEST registry entry kept OK' logs/latest.log 2>/dev/null"
check "tag files' fabric:remove and Forge's remove both take entries out of a tag (the in-game mod's mixin)" "grep -q 'FABRICATED-FORGE-TEST tag removal OK' logs/latest.log 2>/dev/null"
check "vanilla reads a player's stats with Gson's internal parser, a package Gson's module doesn't export (GSON-2)" "grep -q 'FABRICATED-FORGE-TEST vanilla stats read OK' logs/latest.log 2>/dev/null"
check "an empty optional ingredient, a shaped recipe's empty slot as Fabric API's recipe sync writes it, goes over the network as vanilla's empty item list and comes back empty, where Forge's codec threw (the in-game mod's mixin, CORPUS-83)" "grep -q 'FABRICATED-FORGE-TEST optional ingredients on the network OK' logs/latest.log 2>/dev/null"
if $standins; then
	# The provider policy (PROVIDER-1), as on NeoForge: another mod's copy of a module is the one loaded, and Fabricated's
	# part of it (its packages, mixin configs, mixins and Forge hooks) stands down.
	check "a Fabric API module another Forge mod provides is that mod's, and Fabricated's copy stands down (PROVIDER-1)" "grep -q '+standin  \[Stand-in Fabric API Base\]' logs/fabric-bridge-report.txt 2>/dev/null"
	check "so is one a Forge mod nests with Forge's jar-in-jar, with its mixin configs" "grep -q '+standin  \[Stand-in fabric-loot-api-v3\]' logs/fabric-bridge-report.txt 2>/dev/null"
	check "Fabricated's in-game mod leaves out their packages and the mixin configs of the module, its manifest no longer naming them" "grep -q 'leaves out [0-9]* package(s) of the modules other mods provide, the mixin configs \[.*fabricated-forge-loot.mixins.json' logs/fabric-bridge-report.txt 2>/dev/null"
	check "Fabricated's hooks into Forge's loot table loading stand down with its copy of the loot module, firing none of its events" "grep -q 'FABRICATED-FORGE-TEST loot left to its provider OK' logs/latest.log 2>/dev/null"
	check "of two versions of a library Fabric mods nest, the newer loads" "grep -q 'Using standin_lib 1.1.0' logs/fabric-bridge-report.txt 2>/dev/null"
	# Without Fabric API the test mod's checks that need its other modules leave linkage warnings; none may name a class
	# of the stand-ins'.
	check "the linkage check counts the classes of the modules other mods provide" "python3 -I '$root/ci/forge/standin-linkage.py' '$extra' logs/fabric-bridge-report.txt"
else
	check "native fabric-loot-api-v3: Fabric's loot table events change dirt's loot table and drops on Forge's loot loading" "grep -q 'FABRICATED-FORGE-TEST loot OK' logs/latest.log 2>/dev/null"
fi
if $fapi && [[ "$mc" == 26.* ]]; then
	check "fabric-object-builder-api-v1 (with Fabric API, 26.x): a cow's default armor set through FabricDefaultAttributeRegistry.MODIFY is what the game reads (Forge's attribute modification event)" "grep -q 'FABRICATED-FORGE-TEST default attributes OK' logs/latest.log 2>/dev/null"
fi
if $fapi && { [ "$mc" = 26.2 ] || [ "$mc" = 26.3 ]; }; then
	check "fabric-advancement-api-v1 (with Fabric API, 26.2 and 26.3): a vanilla advancement's reward set through AdvancementEvents.MODIFY is what the server loads (on Forge 66 through the in-game mod's hook on Forge's conditional decoding)" "grep -q 'FABRICATED-FORGE-TEST advancement modify OK' logs/latest.log 2>/dev/null"
fi
if $fapi; then
	check "fabric-resource-conditions-api-v1 (with Fabric API): of two advancements with fabric:load_conditions, a JSON data loader's files, the server loads the one whose conditions hold and not the other (on Forge 61 and 66 through upstream's hook bridged to Forge's lambda, LAMBDA-8)" "grep -q 'FABRICATED-FORGE-TEST data loader load conditions OK' logs/latest.log 2>/dev/null"
	check "fabric-resource-conditions-api-v1 (with Fabric API): of two damage types with fabric:load_conditions, a data pack registry's entries, the server loads the one whose conditions hold and not the other (on Forge 61 through Forge's conditional decoder)" "grep -q 'FABRICATED-FORGE-TEST registry load conditions OK' logs/latest.log 2>/dev/null"
	check "fabric-biome-api-v1 (with Fabric API): a feature BiomeModifications.addFeature adds to the plains is among their features, after upstream's rebuild of Forge's retyped featuresPerStep, written with Forge's type as the server started (CORPUS-33)" "grep -q 'FABRICATED-FORGE-TEST biome modification OK' logs/latest.log 2>/dev/null"
	check "fabric-command-api-v2 (with Fabric API): a command registered through CommandRegistrationCallback is in the server's dispatcher, its hook into Commands' constructor marked unsafe (CTOR-2)" "grep -q 'FABRICATED-FORGE-TEST Fabric command registered OK' logs/latest.log 2>/dev/null"
	check "fabric-registry-sync-v0 (with Fabric API): an alias added through FabricRegistry.addAlias names its entry in the registry's lookups by name and by key and in a data pack's tag, as Waystones' renamed blocks need (CORPUS-75)" "grep -q 'FABRICATED-FORGE-TEST registry aliases OK' logs/latest.log 2>/dev/null"
	check "fabric-events-interaction-v0 (with Fabric API): a fake player's breaks through destroyBlock fire PlayerBlockBreakEvents BEFORE, CANCELED and AFTER, and a BEFORE refusal keeps the block (BREAK-1)" "grep -q 'FABRICATED-FORGE-TEST block break events OK' logs/latest.log 2>/dev/null"
	check "fabric-events-interaction-v0 (with Fabric API): a fake player's uses of an item, a block and an entity, and its attacks on a block and an entity, through the game's own code ask UseItemCallback, UseBlockCallback, UseEntityCallback, AttackBlockCallback and AttackEntityCallback, and their answers hold" "grep -q 'FABRICATED-FORGE-TEST interaction events OK' logs/latest.log 2>/dev/null"
	check "fabric-entity-events-v1 (with Fabric API): a hay block EntitySleepEvents.ALLOW_BED admits stays hay while an armor stand sleeps on it, and the occupation and orientation events run (SLEEP-1)" "grep -q 'FABRICATED-FORGE-TEST sleep events OK' logs/latest.log 2>/dev/null"
	check "fabric-entity-events-v1 (with Fabric API): an entity hurt through the game's own hurtServer asks ServerLivingEntityEvents.ALLOW_DAMAGE, whose refusal holds, and fires AFTER_DAMAGE with the damage dealt and taken (DAMAGE-1)" "grep -q 'FABRICATED-FORGE-TEST damage events OK' logs/latest.log 2>/dev/null"
	check "fabric-transfer-api-v1 (with Fabric API): a Fabric mod's item and fluid storages are Forge's ITEM_HANDLER and FLUID_HANDLER capabilities of its block entity, and a Forge mod's ItemStackHandler and FluidTank Fabric's storages, moved only once a transaction commits; a Fabric mod's tank items are Forge's FLUID_HANDLER_ITEM and a Forge mod's Fabric's FluidStorage.ITEM" "grep -q 'FABRICATED-FORGE-TEST storages OK' logs/latest.log 2>/dev/null"
fi
check "an interface a mixin adds to LivingEntity, whose default self() has the name and type of the one Forge's IForgeLivingEntity gives it, gets a bridge, so both answer the pig (CORPUS-63, through Fabricated's Mixin extension)" "grep -q 'FABRICATED-FORGE-TEST default bridge OK' logs/latest.log 2>/dev/null"
check "one a mixin adds to Mob, whose default isMultipartEntity() Mob has through Entity's IForgeEntity, gets a bridge calling the superclass's (DEFAULT-1)" "grep -q 'FABRICATED-FORGE-TEST inherited default bridge OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's fluids have Forge fluid types (the in-game mod's, registered under their ids), and a pig falls through one that isn't water as through air; their buckets, made before the fluids were registered, hold them (CORPUS-73)" "grep -q 'FABRICATED-FORGE-TEST fluid types OK' logs/latest.log 2>/dev/null"
check "Forge's loading warnings name the Fabric mod that wasn't loaded" "grep -qsE 'ServerModLoader.*: Fabric mod fabricated_forge_unmet 1\\.0\\.0 was not loaded: ' logs/latest.log logs/debug.log"
check "an access widener leaves a field private that Forge's coremod reads through a getter: the test mod's BucketItem.content (PLAT-4)" "grep -q 'BucketItem.content must stay private on Forge' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -qsE 'Field (net/minecraft/world/item/BucketItem\.)?content is not private' logs/latest.log server.out"
check "two Fabric mods that depend on each other load, one ordering of their cycle left out (ORDER-1)" "grep -q 'fabricated_cycle_b doesn.t load after fabricated_cycle_a' logs/fabric-bridge-report.txt 2>/dev/null"
check "Fabricated's report names Forge as the loader" "grep -q '^  Forge: ' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -q '^  NeoForge: ' logs/fabric-bridge-report.txt 2>/dev/null"

echo "Server status: $status"
[ "$status" = "started" ] && [ "$failed" = 0 ]
