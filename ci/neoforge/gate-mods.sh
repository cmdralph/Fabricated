#!/usr/bin/env bash
# A gate on a client run with other people's Fabric mods: the run passed every check smoke-client.sh makes (its exit
# status, passed here) and each named mod loaded (Fabricated's report lists it under "Loaded Fabric mods").
# Usage: gate-mods.sh <run dir> <smoke-client.sh's exit status> <what the run is> <mod id>...
set -u
dir=$1
status=$2
what=$3
shift 3
report="$dir/run/logs/fabric-bridge-report.txt"
failed=0
if [ "$status" -eq 0 ]; then
	echo "PASS: $what: every smoke check"
else
	echo "::error::FAIL: $what: a smoke check failed (exit status $status; its FAIL lines are above)"
	failed=1
fi
for id in "$@"; do
	if awk -v id="$id" '/^== Loaded Fabric mods ==/ { s = 1; next } /^== / { s = 0 } s && $1 == id { found = 1 } END { exit !found }' "$report" 2>/dev/null; then
		echo "PASS: $what: $id loaded"
	else
		echo "::error::FAIL: $what: $id didn't load (the report's Disabled and Skipped sections say why)"
		failed=1
	fi
done
exit "$failed"
