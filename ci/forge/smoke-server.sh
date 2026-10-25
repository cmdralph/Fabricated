#!/usr/bin/env bash
# The forge branch's server smoke (docs/PORTING.md §4.1 and §7, F3): installs a Minecraft Forge dedicated server, adds
# the Fabricated jar and the branch's Fabric test mod, boots it and reports what Forge made of them: whether it ran
# Fabricated's locators, took the nested language provider and constructed the in-game mod, and whether the translated
# test mod loaded, ran its entrypoints and had its mixin applied.
#
# Usage: ci/forge/smoke-server.sh <minecraft-version> <forge-version> <work-dir>
# Exit status: the server must start; the checks are reported (until F3's discovery is settled).
# Expects: build/libs/fabricated-forge-*+mc<minecraft-version>.jar and mc/forge-<minecraft-version>/build/testmods/fabricated-testmod.jar
set -euo pipefail

mc="$1"
forge="$2"
work="$3"
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
cp "$root"/mc/forge-"$mc"/build/testmods/fabricated-testmod.jar mods/
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
echo "::group::First errors in the log"
grep -n -m 60 -E "/(ERROR|FATAL)\]|Exception|Caused by" logs/latest.log 2>/dev/null || true
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
	echo "-- $f (first lines)"
	head -n 8 "$f" | cut -c1-200
done
for f in libraries/net/minecraft/server/"$mc"/*.jar; do
	[ -f "$f" ] || continue
	entries=$(unzip -Z1 "$f" 2>/dev/null || true)
	echo "-- $f: $(printf '%s\n' "$entries" | grep -c '\.class$') classes; MinecraftServer: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/server/MinecraftServer\.class$'), net/minecraft/world/level/Level: $(printf '%s\n' "$entries" | grep -c '^net/minecraft/world/level/Level\.class$')"
done
echo "::endgroup::"
report() { if eval "$2"; then echo "PASS: $1"; else echo "NOT SEEN: $1"; fi; }
report "Forge ran Fabricated's mod locator" "grep -qi 'fabricated' logs/debug.log 2>/dev/null && grep -i 'locator' logs/debug.log | grep -qi 'fabricated'"
report "Forge took Fabricated's language provider" "grep -i -E 'language|lang' logs/debug.log 2>/dev/null | grep -qi 'fabricated'"
report "Fabricated's discovery ran" "grep -q 'Fabricated discovery finished' logs/latest.log 2>/dev/null"
report "Fabricated handed Forge the translated test mod" "grep -q 'Fabricated handed Forge [1-9]' logs/latest.log 2>/dev/null"
report "Forge constructed Fabricated's in-game mod" "grep -q 'Fabricated ran the Fabric entrypoints' logs/latest.log 2>/dev/null"
report "the in-game mod's resources load (its pack.mcmeta)" "! grep -q 'Missing metadata in pack mod:fabricated' logs/latest.log 2>/dev/null"
report "the test mod's main entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST main entrypoint OK' logs/latest.log 2>/dev/null"
report "the test mod's server entrypoint ran" "grep -q 'FABRICATED-FORGE-TEST server entrypoint OK' logs/latest.log 2>/dev/null"
report "the test mod's mixin applied (Mixin read the overlay's MixinConfigs)" "grep -q 'FABRICATED-FORGE-TEST mixin OK' logs/latest.log 2>/dev/null"
report "Forge added MixinExtras for the test mod" "grep -i 'mixinextras' logs/debug.log 2>/dev/null | grep -qi -E 'select|load|add'"

echo "Server status: $status"
[ "$status" = "started" ]
