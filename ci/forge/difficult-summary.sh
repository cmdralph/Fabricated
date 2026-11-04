#!/usr/bin/env bash
# What a client run with one of the difficult mods' Fabric builds showed (task 5): the smoke's failed checks, which of
# the named mods loaded, what Fabricated's report left out or warned of, the mods' own errors and warnings, and what
# stopped the game (ci/forge/corpus-causes.sh). Informational: it never fails.
#
# Usage: ci/forge/difficult-summary.sh <work-dir> <smoke-client.sh's exit status> <what the run is> <log pattern> <mod id>...
# The smoke's output is in <work-dir>.out; <log pattern> picks the mods' lines in the game log (grep -E).
set -uo pipefail

dir="$1"
status="$2"
what="$3"
pattern="$4"
shift 4
report="$dir/run/logs/fabric-bridge-report.txt"
log="$dir/run/logs/latest.log"

echo "== $what: smoke-client.sh exit status $status"
grep -E '^(FAIL|NOT SEEN):' "$dir.out" 2>/dev/null | head -n 40 | cut -c1-300
for id in "$@"; do
	if awk -v id="$id" '/^== Loaded Fabric mods ==/ { s = 1; next } /^== / { s = 0 } s && $1 == id { found = 1 } END { exit !found }' "$report" 2>/dev/null; then
		echo "loaded: $id"
	else
		echo "NOT loaded: $id"
	fi
done
echo "-- the report: what it left out or warned of"
awk '/^== / { show = /^== (Summary|Disabled|Skipped|Unsupported mods loaded on request|Mixin pre-flight failures|Linkage warnings|Fabric API mixins skipped|Translation errors|Dependency warnings)/ } show' \
	"$report" 2>/dev/null | head -n 120 | cut -c1-400
echo "-- the mods' errors and warnings in the game log"
grep -n -E "/(WARN|ERROR|FATAL)\]" "$log" 2>/dev/null | grep -E "$pattern" | awk '!seen[substr($0, index($0, "]"))]++' | head -n 60 | cut -c1-400
ci/forge/corpus-causes.sh "$dir/run" "$dir/client.out" "$what"
exit 0
