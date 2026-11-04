#!/usr/bin/env bash
# The forge-legacy branch's server smoke (docs/PORTING.md §7, L3), the forge branch's ported to Forge 47: installs a
# Minecraft Forge dedicated server, adds the Fabricated jar and the branch's Fabric test mod, boots it and reports what
# Forge made of them: whether it ran Fabricated's locators, took the nested language provider and constructed the in-game
# mod, whether the translated test mod loaded, ran its entrypoints and had its mixin applied in SRG's names, and whether
# Forge's loading warnings name a Fabric mod that wasn't loaded.
#
# Usage: ci/forge-legacy/smoke-server.sh <minecraft-version> <forge-version, Minecraft's included> <work-dir> [extra-mods-dir]
# Exit status: the server must start, and every check must pass; the lines read from debug.log, which Forge's debug
# output words as it likes, are only reported. With extra-mods-dir, its jars join the mods folder: the official Fabric
# API jar (fabric-api.jar), whose modules' checks then apply, or the stand-ins ci/forge-legacy/standins.py writes for mods
# that share the game (standin-forgified-fabric-api.jar), whose checks then apply too.
# SMOKE_TESTMODS=none leaves the test mods out; SMOKE_EXPECT_REFUSAL (below) expects the launch to stop instead.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge-legacy/<minecraft-version>/build/testmods/fabricated-testmod.jar
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

echo "::group::Install Forge $forge"
install_server() {
	curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${forge}/forge-${forge}-installer.jar"
	java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -n 60 installer.log; exit 1; }
}
cached_install "forge-legacy-server-$forge" . install_server
echo "::endgroup::"

mkdir -p mods
# SMOKE_ON_ENTRYPOINT_FAILURE: Fabricated's onEntrypointFailure for the run, "disable" unless set: the run keeps loading
# past a mod whose initializer throws, as the test mod that fails on purpose does (below), and as NeoForge's smoke does.
mkdir -p config
printf '{ "schema": 1, "onEntrypointFailure": "%s" }\n' "${SMOKE_ON_ENTRYPOINT_FAILURE:-disable}" > config/fabricated.json
cp "$root"/build/libs/fabricated-forge-*+mc"$mc".jar mods/
if [ "${SMOKE_TESTMODS:-all}" != none ]; then
	cp "$root"/mc/forge-legacy/"$mc"/build/testmods/fabricated-testmod.jar mods/
	# mc/shared's test mod that fails on purpose, after leaving an intrusive holder nothing registers: its failure must be
	# isolated, and the registries' freeze after the entrypoints must drop the holder (CORPUS-42, HOLD-2).
	cp "$root"/mc/forge-legacy/"$mc"/build/testmods/fabricated-testmod-throws.jar mods/
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
# Forge 47 logs each mod pack it made at debug level; vanilla warns of metadata it can't read (1.20.1 requires
# pack_format: ForgeDescriptor).
pack_loaded() {
	grep -q "Generating PackInfo named mod:$1 for" logs/debug.log 2>/dev/null && return 0
	grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null && ! grep -q "Missing metadata in pack mod:$1\$" logs/latest.log 2>/dev/null \
		&& ! grep -q 'Failed to read pack metadata' logs/latest.log 2>/dev/null
}
check "the in-game mod's resources load (its pack.mcmeta)" "pack_loaded fabricated"
check "the test mod's resources load (Fabricated's pack.mcmeta)" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null && pack_loaded fabricated_forge_testmod"
check "the test mod's main entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null"
check "the test mod's server entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST server entrypoint OK' logs/latest.log 2>/dev/null"
check "the test mod's mixin applied (its Intermediary method name remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST mixin OK' logs/latest.log 2>/dev/null"
check "Forge took Fabricated's transformation service" "grep -q 'Fabricated transforms [1-9][0-9]* class(es) for Fabric mods before Mixin' logs/latest.log 2>/dev/null"
check "the test mod's injected interface is on MinecraftServer (inject-interface)" "grep -q 'FABRICATED-FORGE-TEST injected interface OK' logs/latest.log 2>/dev/null"
check "a mixin's @Shadow of a field another mixin adds is that field, as on Fabric, where Forge's Mixin finds only the class's own (CORPUS-74)" "grep -q 'FABRICATED-FORGE-TEST shared mixin field OK' logs/latest.log 2>/dev/null"
check "two mixins' @Unique fields of one name stay a field each, as on Fabric, where Forge's Mixin would merge them (CORPUS-95)" "grep -q 'FABRICATED-FORGE-TEST unique mixin fields OK' logs/latest.log 2>/dev/null"
check "a mod nested with a NeoForge jar-in-jar list too loads once, translated: Forge's jar-in-jar locator leaves it alone (JIJ-1)" "grep -q 'FABRICATED-FORGE-TEST nested jar loaded once OK' logs/latest.log 2>/dev/null"
check "the test mod's client-only method was stripped (@Environment)" "grep -q 'FABRICATED-FORGE-TEST environment strip OK' logs/latest.log 2>/dev/null"
check "the Fabric main entrypoints ran in Forge's registry events, only the test mod that fails on purpose failing" "grep -q 'Fabricated ran the Fabric main entrypoints of [1-9][0-9]* mod(s) on Forge (1 failed)' logs/latest.log 2>/dev/null"
check "the failed initializer is in the report, as on NeoForge, and in Forge's loading warnings" "grep -q 'Fabric mod fabricated-testmod-throws: main entrypoint' logs/fabric-bridge-report.txt 2>/dev/null"
check "Fabricated's API runs what waits for the Fabric initializers once they ran, the failed mod known (Fabricated.get().afterEntrypoints)" "grep -q 'FABRICATED-FORGE-TEST api after entrypoints OK' logs/latest.log 2>/dev/null"
check "the failing test mod's entrypoint is isolated, its failure logged" "grep -q 'intentional failure for Fabricated CI' logs/latest.log 2>/dev/null"
check "the holder the failing test mod left is dropped at the registries' freeze, with an error (CORPUS-42, HOLD-2)" "grep -q 'built but never registered, left by a Fabric mod whose entrypoint failed' logs/fabric-bridge-report.txt 2>/dev/null"
check "the test mod registered an item (Intermediary names remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST registry OK' logs/latest.log 2>/dev/null"
check "content built during vanilla's bootstrap is registered by the main initializer, as BetterNether's entity types are (CORPUS-31)" "grep -q 'FABRICATED-FORGE-TEST content built at bootstrap registered OK' logs/latest.log 2>/dev/null"
check "the built-in registries' freeze runs after the main initializers, where Wover makes the item definitions BetterEnd's subscribes to (CORPUS-32)" "grep -q 'FABRICATED-FORGE-TEST built-in registries frozen after the main initializers OK' logs/latest.log 2>/dev/null"
check "the test mod read a private field its access widener opens (Intermediary names remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST access widener OK' logs/latest.log 2>/dev/null"
check "the minecraft mod container holds vanilla's resources (its en_us.json)" "grep -q 'FABRICATED-FORGE-TEST game resources OK' logs/latest.log 2>/dev/null"
check "the test mod's item is in the frozen registry at the first tick" "grep -q 'FABRICATED-FORGE-TEST registry entry kept OK' logs/latest.log 2>/dev/null"
check "MixinExtras ran the test mod's @WrapMethod (nested in the in-game mod)" "grep -q 'FABRICATED-FORGE-TEST MixinExtras OK' logs/latest.log 2>/dev/null"
check "Mixin ran the test mod's @ModifyArgs, its generated class in a module (CORPUS-68)" "grep -q 'FABRICATED-FORGE-TEST @ModifyArgs OK' logs/latest.log 2>/dev/null"
check "the in-game mod's mixin reads tag files' fabric:remove (the in-game mod reobfuscated to SRG's names)" "grep -q 'FABRICATED-FORGE-TEST fabric:remove OK' logs/latest.log 2>/dev/null"
check "native fabric-biome-api-v1: a spawn and a temperature added to plains reach the biome Forge reads (Forge's biome modifiers)" "grep -q 'FABRICATED-FORGE-TEST biome modifications OK' logs/latest.log 2>/dev/null"
check "vanilla reads a player's stats with Gson's internal parser, a package Gson's module doesn't export (GSON-2)" "grep -q 'FABRICATED-FORGE-TEST vanilla stats read OK' logs/latest.log 2>/dev/null"
check "Forge Config API Port's registry makes a config Forge loads: read right after registering, then loaded once with the port's loading event, its file's values kept (CORPUS-81)" "grep -q 'FABRICATED-FORGE-TEST config port OK' logs/latest.log 2>/dev/null"
check "native fabric-loot-api-v2 and fabric-loot-tables-v1: a pool added in MODIFY and a replaced table are in the tables the game keeps, each event seeing its table's source (Forge's pools list), and a table a mod names as MODIFY's builder builds it keeps that name, as Porting Lib Loot names them (CORPUS-90)" "grep -q 'FABRICATED-FORGE-TEST loot OK' logs/latest.log 2>/dev/null"
check "native fabric-dimensions-v1: an armor stand teleported to the End lands at its target with no obsidian platform built (Forge's teleporter)" "grep -q 'FABRICATED-FORGE-TEST dimensions OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's fluids have Forge fluid types (the in-game mod's, registered under their ids), and a pig falls through one that isn't water as through air; their liquid blocks and buckets, made before the fluids were registered, hold them (CORPUS-73)" "grep -q 'FABRICATED-FORGE-TEST fluid types OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's block item registered before its block is that block's item, and its music disc made before its sound was registered plays that sound (CORPUS-73)" "grep -q 'FABRICATED-FORGE-TEST registration order OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's accessors of a brewing mix's from and to, and its own read of from, read vanilla's potions through the in-game mod's bridges, where Forge 47 keeps holders (CORPUS-37, CORPUS-107)" "grep -q 'FABRICATED-FORGE-TEST brewing mix accessors OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's accessor of a mob bucket's entity type reads the type through the in-game mod's bridge, where Forge 47 keeps a supplier of it (CORPUS-94)" "grep -q 'FABRICATED-FORGE-TEST mob bucket accessor OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's language file is read on the server by vanilla's language reader, which Fabric mods hook (owo-lib's rich text), and its strings are in the server's language; Forge 47 read it with its own copy of the reader (CORPUS-100)" "grep -q 'FABRICATED-FORGE-TEST language files OK' logs/latest.log 2>/dev/null"
check "a Fabric mod's @ModifyVariable takes a local by its type after a frame that chops one, in a method whose frames append three locals at once where it has four argument slots, which Forge 47's Mixin 0.8.5 counts right as it reads the frames expanded (Forge 61 to 66's 0.8.7 miscounts them until Fabricated repairs them, CORPUS-79)" "grep -q 'FABRICATED-FORGE-TEST recorded frames OK' logs/latest.log 2>/dev/null"
check "an interface a mixin adds to LivingEntity, whose default self() has the name and type of the one Forge's IForgeLivingEntity gives it, gets a bridge, so both answer the pig (CORPUS-63, through Fabricated's Mixin extension)" "grep -q 'FABRICATED-FORGE-TEST default bridge OK' logs/latest.log 2>/dev/null"
check "one a mixin adds to Mob, whose default isMultipartEntity() Mob has through Entity's IForgeEntity, gets a bridge calling the superclass's (DEFAULT-1)" "grep -q 'FABRICATED-FORGE-TEST inherited default bridge OK' logs/latest.log 2>/dev/null"
check "Forge's biome coremod applied (the biome's climate field stayed private)" "! grep -qs 'field_to_method.js function biome' logs/latest.log server.out"
check "Forge's loading warnings name the Fabric mod that wasn't loaded" "grep -qsE 'ServerModLoader.*: Fabric mod fabricated_forge_unmet 1\\.0\\.0 was not loaded: ' logs/latest.log logs/debug.log"
check "an access widener leaves a field private that Forge's coremod reads through a getter: the test mod's BucketItem fluid (PLAT-4)" "grep -q 'BucketItem.f_40687_ must stay private on Forge' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -qs 'field_to_method.js function bucketitem' logs/latest.log server.out"
check "two Fabric mods that depend on each other load, one ordering of their cycle left out (ORDER-1)" "grep -q 'fabricated_cycle_b doesn.t load after fabricated_cycle_a' logs/fabric-bridge-report.txt 2>/dev/null"
check "Fabricated's report names Forge as the loader" "grep -q '^  Forge: ' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -q '^  NeoForge: ' logs/fabric-bridge-report.txt 2>/dev/null"
check "the test mod's @Inject at a call in Commands' constructor ran, which Forge 47's Mixin takes only at a return (CTOR-4)" "grep -q 'FABRICATED-FORGE-TEST constructor hook OK' logs/latest.log 2>/dev/null"
check "the test mod's @Inject just after Commands' constructor stores its dispatcher ran and saw it, shifted by 2 as Dripstone Fluid Lib's hook into Minecraft's constructor is (CORPUS-103)" "grep -q 'FABRICATED-FORGE-TEST constructor construction hook OK' logs/latest.log 2>/dev/null"
check "a dying pig's drops are still in Forge's capture after Forge's drops event, for the test mod's hook at the return of dropAllDeathLoot, which takes and adds them as Porting Lib's does, and they reach the level (CORPUS-105)" "grep -q 'FABRICATED-FORGE-TEST drop capture OK' logs/latest.log 2>/dev/null"
if $standins; then
	# The provider policy (PROVIDER-1), as on NeoForge: another mod's copy of a module is the one loaded, and Fabricated's
	# part of it (its packages, mixin configs, mixins and Forge hooks) stands down. The nested stand-in is Fabricated's own
	# copy of fabric-dimensions-v1, so the teleport check above passes through it.
	check "a Fabric API module another Forge mod provides is that mod's, and Fabricated's copy stands down (PROVIDER-1)" "grep -q '+standin  \[Stand-in Fabric API Base\]' logs/fabric-bridge-report.txt 2>/dev/null"
	check "so is one a Forge mod nests with Forge's jar-in-jar, with its mixin config and refmap" "grep -q '+standin  \[Stand-in fabric-dimensions-v1\]' logs/fabric-bridge-report.txt 2>/dev/null"
	check "Fabricated's in-game mod leaves out their packages and the module's mixin config, its manifest no longer naming it" "grep -q 'leaves out [0-9]* package(s) of the modules other mods provide, the mixin configs \[.*fabricated-dimension.mixins.json' logs/fabric-bridge-report.txt 2>/dev/null"
	check "of two versions of a library Fabric mods nest, the newer loads" "grep -q 'Using standin_lib 1.1.0' logs/fabric-bridge-report.txt 2>/dev/null"
	# The Forgified Fabric API stand-in nests a copy of Fabric Loader's API under Forgified Fabric Loader's identifier: the
	# server started, so Forge's jar-in-jar left it out for the in-game mod's empty jar (PROVIDER-2).
	check "a copy of Fabric Loader a Forge mod nests as Forgified Fabric API does is left out for the in-game mod's empty jar, and the game starts (PROVIDER-2)" "grep -q 'standin-forgified-fabric-api.jar (its standin-forgified-loader.jar) nests a copy of Fabric Loader, which Forge.s jar-in-jar leaves out' logs/fabric-bridge-report.txt 2>/dev/null"
	# Forge 47's replaced mods, not NeoForge's (PLAT-6): Mod Menu's jar stays out for Forge's mod list, and Forge Config
	# API Port's for its API on Forge's own config system (CORPUS-81).
	check "Mod Menu's Fabric jar is left out for Forge's mod list, as Forge's descriptor says (REPL-1)" "grep -q \"in Forge's mod list\" logs/fabric-bridge-report.txt 2>/dev/null"
	check "Forge Config API Port's Fabric jar is left out for its API on Forge's own config system, not NeoForge's (CORPUS-81, PLAT-6)" "grep -q \"on Forge's own config system\" logs/fabric-bridge-report.txt 2>/dev/null && ! grep -q \"on NeoForge's own config system\" logs/fabric-bridge-report.txt 2>/dev/null"
	# Without Fabric API the test mod's checks that need its other modules leave linkage warnings; none may name a class
	# of the stand-ins'.
	check "the linkage check counts the classes of the modules other mods provide" "python3 -I '$root/ci/forge-legacy/standin-linkage.py' '$extra' logs/fabric-bridge-report.txt"
fi
if $fapi; then
	check "fabric-command-api-v2 (with Fabric API): a command registered through CommandRegistrationCallback is in the server's dispatcher, its hook into Commands' constructor applied (CTOR-4)" "grep -q 'FABRICATED-FORGE-TEST Fabric command registered OK' logs/latest.log 2>/dev/null"
	check "fabric-registry-sync-v0 (with Fabric API): an alias added through FabricRegistry.addAlias names its entry in the registry's lookups by name and by key and in a data pack's tag, as Waystones' renamed blocks need (CORPUS-75)" "grep -q 'FABRICATED-FORGE-TEST registry aliases OK' logs/latest.log 2>/dev/null"
	check "fabric-registry-sync-v0 (with Fabric API): a RegistryEntryAddedCallback listener registered before its entries hears of an item registered through vanilla's API and of one registered through Forge's, each once with its raw id, as Architectury's registry listeners need (CORPUS-108)" "grep -q 'FABRICATED-FORGE-TEST registry entry added events OK' logs/latest.log 2>/dev/null"
	check "fabric-events-interaction-v0 (with Fabric API): a fake player's breaks through destroyBlock fire PlayerBlockBreakEvents BEFORE, CANCELED and AFTER, and a BEFORE refusal keeps the block (BREAK-1)" "grep -q 'FABRICATED-FORGE-TEST block break events OK' logs/latest.log 2>/dev/null"
	check "fabric-events-interaction-v0 (with Fabric API): a fake player's uses of an item, a block and an entity, and its attacks on a block and an entity, through the game's own code ask UseItemCallback, UseBlockCallback, UseEntityCallback, AttackBlockCallback and AttackEntityCallback, and their answers hold" "grep -q 'FABRICATED-FORGE-TEST interaction events OK' logs/latest.log 2>/dev/null"
	check "fabric-entity-events-v1 (with Fabric API): a hay block EntitySleepEvents.ALLOW_BED admits stays hay while an armor stand sleeps on it, the occupation and orientation events run (SLEEP-1, LAMBDA-6), and a player may sleep there in the daytime ALLOW_SLEEP_TIME allows (SLEEP-2)" "grep -q 'FABRICATED-FORGE-TEST sleep events OK' logs/latest.log 2>/dev/null"
	check "fabric-transfer-api-v1 (with Fabric API): a Fabric mod's item and fluid storages are Forge's ITEM_HANDLER and FLUID_HANDLER capabilities of its block entity, and a Forge mod's ItemStackHandler and FluidTank Fabric's storages, moved only once a transaction commits; a Fabric mod's tank items are Forge's FLUID_HANDLER_ITEM and a Forge mod's Fabric's FluidStorage.ITEM" "grep -q 'FABRICATED-FORGE-TEST storages OK' logs/latest.log 2>/dev/null"
	check "fabric-content-registries-v0 (with Fabric API): an item FuelRegistry made a fuel is in vanilla's fuel table and a fuel to Forge's furnace, which asks Forge's burn times (FUEL-1)" "grep -q 'FABRICATED-FORGE-TEST fuel registry OK' logs/latest.log 2>/dev/null"
	check "fabric-content-registries-v0 (with Fabric API): a pair OxidizableBlocksRegistry registers is in vanilla's oxidation map, made mutable by upstream's interface mixin, whose @Inject Fabricated applies where Forge 47's Mixin applies none (CORPUS-91, INTERFACE-1)" "grep -q 'FABRICATED-FORGE-TEST oxidizable blocks OK' logs/latest.log 2>/dev/null"
	check "fabric-content-registries-v0 (with Fabric API): an awkward potion brewed with an item a FabricBrewingRecipeRegistry recipe names gives the recipe's potion, the recipe built with Forge's constructor of a brewing mix (BREW-1)" "grep -q 'FABRICATED-FORGE-TEST brewing recipe OK' logs/latest.log 2>/dev/null"
	check "fabric-resource-conditions-api-v1 (with Fabric API): a loot table whose fabric:load_conditions fail doesn't load, upstream's hook found in the loot data manager's parsing lambda (LAMBDA-6)" "grep -q 'FABRICATED-FORGE-TEST loot conditions OK' logs/latest.log 2>/dev/null"
	check "fabric-resource-conditions-api-v1 (with Fabric API): Forge's recipe conditions ask a condition registered with Fabric API, a type Forge doesn't know, as Supplementaries' recipes name theirs: the true one's recipe loads, the false one's doesn't, neither fails to parse (CORPUS-97)" "grep -q 'FABRICATED-FORGE-TEST recipe condition asked: true' logs/latest.log 2>/dev/null && grep -q 'FABRICATED-FORGE-TEST recipe condition asked: false' logs/latest.log 2>/dev/null && grep -q 'FABRICATED-FORGE-TEST recipe conditions OK' logs/latest.log 2>/dev/null && ! grep -q 'Parsing error loading recipe fabricated-forge-testmod:condition_' logs/latest.log 2>/dev/null"
	check "fabric-recipe-api-v1 (with Fabric API): a fabric:any ingredient read as a recipe reads one is Fabric API's custom ingredient, an array of it and stone Forge's compound ingredient, each matching its items, and both come back from the network as they went, Forge's compound holding the custom one read by Forge (CORPUS-99, NET-11)" "grep -q 'FABRICATED-FORGE-TEST Fabric API.s custom ingredients OK' logs/latest.log 2>/dev/null"
fi
check "vanilla's check of the built-in registries, which Fabric API moves to the server's start, passes over Forge's empty registries (REG-6)" "! grep -qs 'was empty after loading' logs/latest.log server.out"

echo "Server status: $status"
[ "$status" = "started" ] && [ "$failed" = 0 ]
