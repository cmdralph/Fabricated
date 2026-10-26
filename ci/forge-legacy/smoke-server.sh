#!/usr/bin/env bash
# The forge-legacy branch's server smoke (docs/PORTING.md §7, L3), the forge branch's ported to Forge 47: installs a
# Minecraft Forge dedicated server, adds the Fabricated jar and the branch's Fabric test mod, boots it and reports what
# Forge made of them: whether it ran Fabricated's locators, took the nested language provider and constructed the in-game
# mod, whether the translated test mod loaded, ran its entrypoints and had its mixin applied in SRG's names, and whether
# Forge's loading warnings name a Fabric mod that wasn't loaded.
#
# Usage: ci/forge-legacy/smoke-server.sh <minecraft-version> <forge-version, Minecraft's included> <work-dir> [extra-mods-dir]
# Exit status: the server must start; the checks are reported (until L3's discovery is settled). With extra-mods-dir
# (the official Fabric API jar), its jars join the mods folder.
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge-legacy/<minecraft-version>/build/testmods/fabricated-testmod.jar
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

echo "::group::Install Forge $forge"
curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/${forge}/forge-${forge}-installer.jar"
java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -n 60 installer.log; exit 1; }
echo "::endgroup::"

mkdir -p mods
cp "$root"/build/libs/fabricated-forge-*+mc"$mc".jar mods/
cp "$root"/mc/forge-legacy/"$mc"/build/testmods/fabricated-testmod.jar mods/
# A Fabric mod whose dependency is missing: Fabricated doesn't load it, and Forge's loading warnings name it.
mkdir unmet
cat > unmet/fabric.mod.json <<'JSON'
{"schemaVersion": 1, "id": "fabricated_forge_unmet", "version": "1.0.0", "depends": {"fabricated_forge_missing": "*"}}
JSON
jar --create --file mods/fabricated-forge-unmet.jar -C unmet fabric.mod.json
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
# Where the installer left Mojang's and MCPConfig's mappings and the game jars (ForgeLibraries' layout, UNVERIFIED until
# this run: docs/PORTING.md §3.1).
echo "::group::libraries/net/minecraft and libraries/de/oceanlabs"
find libraries/net/minecraft libraries/de/oceanlabs -maxdepth 5 2>/dev/null | sort | head -n 80 || true
for f in libraries/de/oceanlabs/mcp/mcp_config/*/*.txt libraries/net/minecraft/server/*/*-mappings.txt; do
	[ -f "$f" ] || continue
	echo "-- $f (first lines)"
	head -n 6 "$f" | cut -c1-200
done
# Which names the game jars use: MinecraftServer keeps its name everywhere, Level is obfuscated in the vanilla jar, and
# tickServer is m_5705_ in SRG's names.
for f in libraries/net/minecraft/server/*/*.jar; do
	[ -f "$f" ] || continue
	entries=$(unzip -Z1 "$f" 2>/dev/null || true)
	srg=0
	# A here-string, not a pipe: under pipefail grep -q's early exit fails the pipe's writer.
	if grep -q '^net/minecraft/server/MinecraftServer\.class$' <<<"$entries"; then
		srg=$(unzip -p "$f" net/minecraft/server/MinecraftServer.class 2>/dev/null | grep -a -c 'm_5705_' || true)
	fi
	echo "-- $f: $(printf '%s\n' "$entries" | grep -c '\.class$') classes; MinecraftServer: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/server/MinecraftServer\.class$'), net/minecraft/world/level/Level: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/world/level/Level\.class$'), m_5705_ in MinecraftServer: $srg"
done
echo "::endgroup::"
# The Mojang names the native fabric-biome-api-v1 relies on (L4) that no source at hand showed for 1.20.1, as the
# installer's copy of Mojang's mappings has them: a class, then its members of those names.
echo "::group::Mojang's names the native biome module relies on (server mappings)"
for f in libraries/net/minecraft/server/*/*-mappings.txt; do
	[ -f "$f" ] || continue
	awk '
		BEGIN {
			want["net.minecraft.world.level.levelgen.NoiseChunk"] = "<init>\\(|cachedClimateSampler\\("
			want["net.minecraft.world.level.levelgen.RandomState"] = "<init>\\(| sampler( |\\()"
			want["net.minecraft.world.level.levelgen.synth.ImprovedNoise"] = "<init>\\(| noise\\(double,double,double\\)"
			want["net.minecraft.world.level.biome.BiomeSource"] = "possibleBiomes"
			want["net.minecraft.world.level.biome.TheEndBiomeSource"] = " CODEC | create\\(|getNoiseBiome\\(|<init>\\("
			want["net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset"] = "usedBiomes"
			want["net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList$Preset$1"] = " apply\\("
			want["net.minecraft.world.level.biome.Climate$ParameterList"] = " values\\("
			want["net.minecraft.world.level.biome.Climate$TargetPoint"] = "\\(\\) -> "
			want["net.minecraft.world.level.levelgen.placement.PlacedFeature"] = "getFeatures\\("
			want["net.minecraft.core.HolderGetter"] = " get\\(|getOrThrow\\("
			want["net.minecraft.core.HolderLookup$Provider"] = "lookupOrThrow"
			want["net.minecraft.resources.RegistryOps"] = "retrieveGetter"
			want["net.minecraft.world.level.biome.Biome"] = "getBaseTemperature|getMobSettings| climateSettings | specialEffects "
			want["net.minecraft.world.level.biome.BiomeSpecialEffects$Builder"] = "grassColorModifier\\("
			want["net.minecraft.world.level.biome.MobSpawnSettings$Builder"] = "creatureGenerationProbability\\(|addMobCharge\\("
			want["net.minecraft.world.level.levelgen.structure.Structure"] = " biomes\\("
			want["net.minecraft.world.level.dimension.LevelStem"] = " NETHER | END | generator\\("
		}
		/^[^ \t#]/ { cls = $1; if (cls in want) print; next }
		cls in want && $0 ~ want[cls] { print }
	' "$f" | cut -c1-300
done
echo "::endgroup::"
# The same for the native fabric-loot-api-v2 and fabric-loot-tables-v1 (L4): the members their mixins and builders
# name, some of which no source at hand showed for 1.20.1 (Forge's patches name them in SRG's names).
echo "::group::Mojang's names the native loot modules rely on (server mappings)"
for f in libraries/net/minecraft/server/*/*-mappings.txt; do
	[ -f "$f" ] || continue
	awk '
		BEGIN {
			want["net.minecraft.world.level.storage.loot.LootTable"] = " pools | functions | randomSequence |getParamSet\\("
			want["net.minecraft.world.level.storage.loot.LootTable$Builder"] = " pools | functions |withPool\\(|setParamSet\\(|setRandomSequence\\("
			want["net.minecraft.world.level.storage.loot.LootPool"] = " entries | conditions | functions | rolls | bonusRolls "
			want["net.minecraft.world.level.storage.loot.LootPool$Builder"] = " entries | conditions | functions |setRolls\\(| add\\(| when\\(| apply\\("
			want["net.minecraft.world.level.storage.loot.LootDataManager"] = " apply\\(| reload\\("
			want["net.minecraft.world.level.storage.loot.Deserializers"] = "createLootTableSerializer\\("
			want["net.minecraft.util.GsonHelper"] = " fromJson\\(com.google.gson.Gson,java.lang.String,java.lang.Class\\)"
			want["net.minecraft.core.registries.BuiltInRegistries"] = " LOOT_POOL_ENTRY_TYPE "
		}
		/^[^ \t#]/ { cls = $1; if (cls in want) print; next }
		cls in want && $0 ~ want[cls] { print }
	' "$f" | cut -c1-300
done
echo "::endgroup::"
# The same for the native fabric-dimensions-v1 (L4): the members its port, its mixins and the test mod's stand-ins name
# in Mojang's names (Forge's patches name them in SRG's names).
echo "::group::Mojang's names the native dimensions module relies on (server mappings)"
for f in libraries/net/minecraft/server/*/*-mappings.txt; do
	[ -f "$f" ] || continue
	awk '
		BEGIN {
			want["net.minecraft.world.entity.Entity"] = " findDimensionEntryPoint\\(| changeDimension\\(| moveTo\\(double,double,double,float,float\\)| setDeltaMovement\\(net| setYHeadRot\\(| getXRot\\(| level\\(\\)"
			want["net.minecraft.server.level.ServerPlayer"] = " findDimensionEntryPoint\\(| changeDimension\\(| connection -> "
			want["net.minecraft.server.network.ServerGamePacketListenerImpl"] = " teleport\\(double,double,double,float,float\\)"
			want["net.minecraft.server.MinecraftServer"] = " getRunningThread\\("
			want["net.minecraft.world.level.portal.PortalInfo"] = " pos -> | speed -> | yRot -> | xRot -> "
		}
		/^[^ \t#]/ { cls = $1; if (cls in want) print; next }
		cls in want && $0 ~ want[cls] { print }
	' "$f" | cut -c1-300
done
echo "::endgroup::"
# The same for the test mod's Fabric fluids (fluid types, L4): the members its stand-ins name in Intermediary's names,
# in Mojang's names with their obfuscated names, to check against Intermediary's.
echo "::group::Mojang's names the test mod's fluid stand-ins rely on (server mappings)"
for f in libraries/net/minecraft/server/*/*-mappings.txt; do
	[ -f "$f" ] || continue
	awk '
		BEGIN {
			want["net.minecraft.world.level.material.Fluid"] = " getBucket\\(| canBeReplacedWith\\(| getTickDelay\\(| getExplosionResistance\\(| createLegacyBlock\\(| isSource\\(| getAmount\\(| isSame\\(| createFluidStateDefinition\\("
			want["net.minecraft.world.level.material.FlowingFluid"] = " LEVEL | getFlowing\\(\\)| getSource\\(\\)| canConvertToSource\\(| beforeDestroyingBlock\\(| getSlopeFindDistance\\(| getDropOff\\(| getLegacyLevel\\("
			want["net.minecraft.world.level.block.state.StateHolder"] = " getValue\\(| setValue\\("
			want["net.minecraft.world.level.block.state.StateDefinition$Builder"] = " add\\("
			want["net.minecraft.world.level.block.LiquidBlock"] = " LEVEL |<init>"
			want["net.minecraft.world.level.block.state.BlockBehaviour$Properties"] = " copy\\("
			want["net.minecraft.world.entity.Entity"] = " tick\\(\\)| getY\\(\\)"
			want["net.minecraft.world.item.Items"] = " AIR "
			want["net.minecraft.core.registries.BuiltInRegistries"] = " FLUID "
		}
		/^[^ \t#]/ { cls = $1; if (cls in want) print; next }
		cls in want && $0 ~ want[cls] { print }
	' "$f" | cut -c1-300
done
echo "::endgroup::"
# A pipeline's last grep reads all its input (no -q): under pipefail, -q's exit at the first match can kill the grep
# feeding it with SIGPIPE, and a line that is there reads as not seen.
report() { if eval "$2"; then echo "PASS: $1"; else echo "NOT SEEN: $1"; fi; }
report "Forge ran Fabricated's mod locator" "grep -qi 'fabricated' logs/debug.log 2>/dev/null && grep -i 'locator' logs/debug.log | grep -i 'fabricated' > /dev/null"
report "Forge took Fabricated's language provider" "grep -i -E 'language|lang' logs/debug.log 2>/dev/null | grep -i 'fabricated' > /dev/null"
report "Fabricated's discovery ran" "grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null"
report "Fabricated handed Forge the translated test mod" "grep -q 'Fabricated handed Forge [1-9]' logs/latest.log 2>/dev/null"
report "Forge constructed Fabricated's in-game mod" "grep -q 'Fabricated ran the Fabric pre-launch entrypoints' logs/latest.log 2>/dev/null"
# Forge 47 logs each mod pack it made at debug level; vanilla warns of metadata it can't read (1.20.1 requires
# pack_format: ForgeDescriptor).
pack_loaded() {
	grep -q "Generating PackInfo named mod:$1 for" logs/debug.log 2>/dev/null && return 0
	grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null && ! grep -q "Missing metadata in pack mod:$1\$" logs/latest.log 2>/dev/null \
		&& ! grep -q 'Failed to read pack metadata' logs/latest.log 2>/dev/null
}
report "the in-game mod's resources load (its pack.mcmeta)" "pack_loaded fabricated"
report "the test mod's resources load (Fabricated's pack.mcmeta)" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null && pack_loaded fabricated_forge_testmod"
report "the test mod's main entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null"
report "the test mod's server entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST server entrypoint OK' logs/latest.log 2>/dev/null"
report "the test mod's mixin applied (its Intermediary method name remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST mixin OK' logs/latest.log 2>/dev/null"
report "Forge took Fabricated's transformation service" "grep -q 'Fabricated transforms [1-9][0-9]* class(es) for Fabric mods before Mixin' logs/latest.log 2>/dev/null"
report "the test mod's injected interface is on MinecraftServer (inject-interface)" "grep -q 'FABRICATED-FORGE-TEST injected interface OK' logs/latest.log 2>/dev/null"
report "the test mod's client-only method was stripped (@Environment)" "grep -q 'FABRICATED-FORGE-TEST environment strip OK' logs/latest.log 2>/dev/null"
report "the Fabric main entrypoints ran in Forge's registry events" "grep -q 'Fabricated ran the Fabric main entrypoints of [1-9][0-9]* mod(s) on Forge (0 failed)' logs/latest.log 2>/dev/null"
report "the test mod registered an item (Intermediary names remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST registry OK' logs/latest.log 2>/dev/null"
report "the test mod read a private field its access widener opens (Intermediary names remapped to SRG's)" "grep -q 'FABRICATED-FORGE-TEST access widener OK' logs/latest.log 2>/dev/null"
report "the minecraft mod container holds vanilla's resources (its en_us.json)" "grep -q 'FABRICATED-FORGE-TEST game resources OK' logs/latest.log 2>/dev/null"
report "the test mod's item is in the frozen registry at the first tick" "grep -q 'FABRICATED-FORGE-TEST registry entry kept OK' logs/latest.log 2>/dev/null"
report "MixinExtras ran the test mod's @WrapMethod (nested in the in-game mod)" "grep -q 'FABRICATED-FORGE-TEST MixinExtras OK' logs/latest.log 2>/dev/null"
report "the in-game mod's mixin reads tag files' fabric:remove (the in-game mod reobfuscated to SRG's names)" "grep -q 'FABRICATED-FORGE-TEST fabric:remove OK' logs/latest.log 2>/dev/null"
report "native fabric-biome-api-v1: a spawn and a temperature added to plains reach the biome Forge reads (Forge's biome modifiers)" "grep -q 'FABRICATED-FORGE-TEST biome modifications OK' logs/latest.log 2>/dev/null"
report "native fabric-loot-api-v2 and fabric-loot-tables-v1: a pool added in MODIFY and a replaced table are in the tables the game keeps, each event seeing its table's source (Forge's pools list)" "grep -q 'FABRICATED-FORGE-TEST loot OK' logs/latest.log 2>/dev/null"
report "native fabric-dimensions-v1: an armor stand teleported to the End lands at its target with no obsidian platform built (Forge's teleporter)" "grep -q 'FABRICATED-FORGE-TEST dimensions OK' logs/latest.log 2>/dev/null"
report "a Fabric mod's fluids have Forge fluid types (the in-game mod's, registered under their ids), and a pig falls through one that isn't water as through air" "grep -q 'FABRICATED-FORGE-TEST fluid types OK' logs/latest.log 2>/dev/null"
report "Forge's biome coremod applied (the biome's climate field stayed private)" "! grep -qs 'field_to_method.js function biome' logs/latest.log server.out"
report "Forge's loading warnings name the Fabric mod that wasn't loaded" "grep -qsE 'ServerModLoader.*: Fabric mod fabricated_forge_unmet 1\\.0\\.0 was not loaded: ' logs/latest.log logs/debug.log"
report "Fabricated's report names Forge as the loader" "grep -q '^  Forge: ' logs/fabric-bridge-report.txt 2>/dev/null && ! grep -q '^  NeoForge: ' logs/fabric-bridge-report.txt 2>/dev/null"

echo "Server status: $status"
[ "$status" = "started" ]
