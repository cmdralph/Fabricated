#!/usr/bin/env bash
# What stopped a corpus run on Forge, past what ci/corpus-summary.sh prints of it (a crash's description only): the
# Fabric mods whose entrypoints failed, the Fabric API modules skipped and their mixins left out, as Fabricated's report
# lists them; Fabricated's errors and warnings in the game's logs; each crash report's exception chain, with the frames
# of its innermost cause; the exception chains and Mixin errors in the game's logs; a JVM fatal error log if there is
# one; and the console's last lines, for a game that ends with no exception or crash report (CORPUS-53, CORPUS-55).
# Informational: it never fails.
#
# Usage: ci/forge/corpus-causes.sh <game-dir> <console-output> [label]
# <console-output> is the game's own console (server.out, client.out), in the smoke script's work directory.
set -uo pipefail

dir="$1"
console="$2"
label="${3:-$1}"

# An exception chain: its exception lines ("Caused by:" and the outermost one), numbered, then the first frames of its
# innermost cause, the last exception line.
chain() {
	awk -v limit="$2" '
		function exceptionLine(line) { return line ~ /^Caused by: / || line ~ /^[A-Za-z_$][A-Za-z0-9_$.]*\.[A-Za-z0-9_$]*(Exception|Error|Throwable)(: |$)/ }
		exceptionLine($0) { print FNR ": " substr($0, 1, 400); last = FNR; n = 0; next }
		last && /^[ \t]+at / && n < limit { frames[n++] = substr($0, 1, 300) }
		END { if (n) { print "  innermost cause, from line " last ":"; for (i = 0; i < n; i++) print "  " frames[i] } }
	' "$1"
}

echo "== $label: what stopped it"
# The Fabric mods whose entrypoints failed, from Fabricated's report: with SMOKE_ON_ENTRYPOINT_FAILURE=disable the game
# goes on without them, and what stops it later (their data packs naming entries never registered) is only their effect.
report="$dir/logs/fabric-bridge-report.txt"
if [ -f "$report" ]; then
	echo "-- Fabricated's report: entrypoint failures"
	section() { awk -v head="== $1" 'index($0, head) == 1 { show = 1; next } /^$/ { show = 0 } show' "$report"; }
	section 'Entrypoint failures' | head -n 60 | cut -c1-400
	# Which Fabric API modules this game left out or took from another mod, and their mixins pre-flight left out: what a
	# mod's mixin that shadows or calls into one of them meets (MoreCulling's into Fabric's renderer state, CORPUS-74).
	echo "-- Fabricated's report: Fabric API modules skipped"
	section 'Skipped' | head -n 40 | cut -c1-300
	echo "-- Fabricated's report: Fabric API mixins left out"
	section 'Mixins left out' | grep '^  fabric' | head -n 30 | cut -c1-300
fi
own="$(mktemp)"
trap 'rm -f "$own"' EXIT
found=
for crash in "$dir"/crash-reports/*.txt; do
	[ -f "$crash" ] || continue
	found=1
	echo "-- $(basename "$crash")"
	# The report's own exception: the lines from its description up to the system details, where the chain ends.
	sed -n '/^Description:/,/^-- System Details --/p' "$crash" > "$own"
	chain "$own" 25 | head -60
done
[ -n "$found" ] || echo "-- no crash report"
for log in "$dir/logs/latest.log" "$dir/logs/debug.log"; do
	[ -f "$log" ] || continue
	echo "-- $(basename "$log"): Mixin's errors"
	grep -n -E 'Mixin apply|MixinTransformerError|MixinApplyError|MixinPreProcessorException|InvalidMixinException|InvalidInjectionException|Critical injection failure|Mixin prepare for|Mixin config .* could not' "$log" | awk '!seen[substr($0, index($0, ":") + 1)]++' | head -30 | cut -c1-500
	if [ "$(basename "$log")" = latest.log ]; then
		echo "-- $(basename "$log"): Fabricated's errors and warnings"
		grep -n -E '/(ERROR|WARN)\] \[Fabricated/\]' "$log" | awk '!seen[substr($0, index($0, ": ") + 2)]++' | head -40 | cut -c1-500
	fi
	echo "-- $(basename "$log"): exception chains (the last one's innermost frames)"
	chain "$log" 20 | tail -n 80
done
for fatal in "$dir"/hs_err_pid*.log "$(dirname "$dir")"/hs_err_pid*.log; do
	[ -f "$fatal" ] || continue
	echo "-- JVM fatal error log $(basename "$fatal")"
	head -n 30 "$fatal" | cut -c1-300
done
# Why the smoke script stopped the game, where it says (a client: mc-runtime-test's stop, or the line it stopped on).
reason="$(dirname "$console")/stop-reason.txt"
if [ -f "$reason" ]; then
	echo "-- stopped by CI: $(head -c 400 "$reason")"
fi
if [ -f "$console" ]; then
	echo "-- the console's last lines"
	tail -n 40 "$console" | cut -c1-400
fi
exit 0
