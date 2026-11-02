#!/usr/bin/env bash
# CI check of the safety net for a Fabric mod's mixin that fails as Mixin applies it, which pre-flight passed (APPLY-1).
# A dedicated server with Fabricated and the impossible-mixin test mod, whose pre-flight findings are ignored
# ("ignorePreflight"), so Mixin applies both its mixins into MinecraftServer:
#   1. ImpossibleMixin fails as Mixin's error handlers see it: the launch stops, and the failure is recorded;
#   2. that launch leaves ImpossibleMixin out and says so; UnmatchedCallMixin then fails with an injector's critical
#      failure, which bypasses the handlers and is recorded as the game stops;
#   3. that launch leaves both out, says so, and the server starts.
#
# Usage: ci/neoforge/smoke-apply-failure.sh <minecraft-version> <neoforge-version> <work-dir>
# Expects: build/libs/fabricated-*+mc<minecraft-version>.jar and mc/neoforge/<minecraft-version>/build/testmods/*.jar
set -euo pipefail

mc="$1"
neo="$2"
work="$3"
root="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=ci/install-cache.sh
source "$root/ci/install-cache.sh"
timeout_seconds="${SMOKE_TIMEOUT:-300}"

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
cp "$root"/build/libs/fabricated-[0-9]*+mc"$mc".jar mods/
cp "$root"/mc/neoforge/"$mc"/build/testmods/fabricated-testmod-badmixin.jar mods/
cat > config/fabricated.json <<'JSON'
{ "schema": 1, "logLevel": "DEBUG", "mods": { "fabricated-testmod-badmixin": { "ignorePreflight": true } } }
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
chmod +x run.sh

failures=.fabricated/mixin-failures.json
report=logs/fabric-bridge-report.txt
config_name=fabricated-testmod-badmixin.mixins.json
section="Mixins left out (they failed as Mixin applied them in the last launch)"
fail=0

# launch <n>: runs the server until it starts (then stops it), stops by itself or times out; sets $status and $out.
launch() {
	out="server-$1.out"
	rm -f console
	mkfifo console
	# Keep the FIFO open for writing so the server does not see EOF on stdin.
	exec 3<>console
	./run.sh nogui < console > "$out" 2>&1 &
	local pid=$! start
	start=$(date +%s)
	status="timeout"
	while kill -0 "$pid" 2>/dev/null; do
		if grep -q 'Done (' "$out" 2>/dev/null; then status="started"; break; fi
		if [ $(( $(date +%s) - start )) -gt "$timeout_seconds" ]; then break; fi
		sleep 3
	done
	if [ "$status" = "timeout" ] && ! kill -0 "$pid" 2>/dev/null; then status="stopped"; fi
	if [ "$status" = "started" ]; then
		echo "stop" >&3
		for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 2; done
	fi
	kill "$pid" 2>/dev/null || true
	wait "$pid" 2>/dev/null || true
	exec 3>&-
	echo "::group::Launch $1: $status; the output's mixin and Fabricated lines"
	grep -n -E "Mixin|mixin|Fabricated|Exception in thread" "$out" | tail -n 80 | cut -c1-400 || true
	echo "::endgroup::"
	echo "::group::Launch $1: $failures"
	cat "$failures" 2>/dev/null || echo "(none)"
	echo "::endgroup::"
	echo "::group::Launch $1: the report's section of the mixins left out as they failed"
	grep -F -A 6 -- "$section" "$report" 2>/dev/null | head -n 20 || true
	echo "::endgroup::"
}

expect() {
	if eval "$1"; then echo "PASS: $2"; else echo "FAIL: $2"; fail=1; fi
}

launch 1
expect '[ "$status" = stopped ]' "launch 1 stops, as Mixin fails on ImpossibleMixin"
expect 'grep -q "\"$config_name:ImpossibleMixin\"" "$failures"' "launch 1 recorded ImpossibleMixin, which Mixin's error handlers saw"
cp "$report" report-1.txt 2>/dev/null || true

launch 2
expect 'grep -qF "fabricated-testmod-badmixin: $config_name:ImpossibleMixin failed as Mixin applied it in the last launch" "$report"' \
	"launch 2 leaves ImpossibleMixin out and its report says why"
expect '[ "$status" = stopped ]' "launch 2 stops, as Mixin fails on UnmatchedCallMixin"
expect 'grep -q "\"$config_name:UnmatchedCallMixin\"" "$failures"' \
	"launch 2 recorded UnmatchedCallMixin, an injector's critical failure past Mixin's error handlers"

launch 3
expect '[ "$status" = started ]' "launch 3 starts, without both mixins"
expect 'grep -qF "fabricated-testmod-badmixin: $config_name:UnmatchedCallMixin failed as Mixin applied it in the last launch" "$report"' \
	"launch 3 leaves UnmatchedCallMixin out and its report says why"
expect 'grep -qF "2 mixin(s) left out that failed as Mixin applied them in the last launch" "$report"' "launch 3's row for the mod counts both"
exit "$fail"
