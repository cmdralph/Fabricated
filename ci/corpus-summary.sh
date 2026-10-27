#!/usr/bin/env bash
# What a corpus run made of its Fabric mods, from the game's folder (a server's work folder, or a client's run/): how
# many loaded, each disabled mod with its reason, why pre-flight disabled mods, the Fabric Loader references Fabricated
# doesn't provide, the mods loaded without some of their mixins, a crash's description, and the log's errors as the game
# ran (ci/log-digest.py). Informational: it never fails.
#
# Usage: ci/corpus-summary.sh <game-dir> [label]
set -uo pipefail

dir="$1"
label="${2:-$1}"
root="$(cd "$(dirname "$0")/.." && pwd)"
report="$dir/logs/fabric-bridge-report.txt"
log="$dir/logs/latest.log"
# A section of the report: its lines up to the next blank line.
section() { awk -v head="== $1" 'index($0, head) == 1 { show = 1; next } /^$/ { show = 0 } show' "$report"; }

echo "== $label"
if [ -f "$report" ]; then
	echo "-- loaded Fabric mods, without Fabric API's modules: $(section 'Loaded Fabric mods' | awk '/^  [^ ]/ && $1 !~ /^fabric/' | wc -l)"
	echo "-- disabled mods:"
	section 'Disabled Fabric mods' | awk '/^  [^ ]/ { id = $1 } /^      / && !/^      Fix:/ && id { print "  " id ": " substr($0, 7); id = "" }' | cut -c1-260
	echo "-- why pre-flight disabled them:"
	section 'Mixin pre-flight failures' | head -30 | cut -c1-500
	echo "-- Fabric Loader references Fabricated doesn't provide:"
	section 'Linkage warnings' | grep -E "missing [a-z]+ net\.fabricmc\.(loader|api)\." | head -30 | cut -c1-300
	echo "-- loaded without some of their mixins (LEAVE-1):"
	section 'Mixins left out' | grep -v '^  fabric' | head -40 | cut -c1-260
else
	echo "(no Fabricated report: the game stopped before discovery finished)"
fi
for crash in "$dir"/crash-reports/*.txt; do
	[ -f "$crash" ] || continue
	echo "-- $(basename "$crash")"
	sed -n '/^Description:/,/^A detailed walkthrough/p' "$crash" | grep -v '^A detailed' | head -40 | cut -c1-300
done
if [ -f "$log" ]; then
	python3 -I "$root/ci/log-digest.py" "$log" || true
	if grep -q -E 'Done \([0-9.]+s\)!|Successfully finished\.' "$log"; then
		echo "-- the game started"
	else
		echo "-- the game didn't start, or the client reached no world; the log's last lines:"
		tail -n 6 "$log" | cut -c1-300
	fi
fi
exit 0
