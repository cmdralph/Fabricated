#!/usr/bin/env bash
# What stopped a game run (an informational one, or one that failed: ci/smoke.sh), past what ci/corpus-summary.sh
# prints of it (a crash's description only). First the stop itself: why CI stopped the game, where it says; each crash
# report's exception chain, with the frames of its innermost cause, and Mixin's failures in it whole; a JVM fatal error
# log; and without a crash report, for a game that didn't start, the console's last exception chain before it ended
# (CI-12). Then what may lie behind it: the Fabric mods whose entrypoints failed, the Fabric API modules skipped and
# their mixins left out, as Fabricated's report lists them; Fabricated's errors and warnings in the game's logs; the
# exception chains and Mixin errors in the game's logs, Mixin's failures whole; and from the console, the registries'
# loading errors and its exception chains, which the logs may not have had written, and its last lines, for a game that
# ends with no exception or crash report (CORPUS-53, CORPUS-54, CORPUS-55). An exception a DEBUG or TRACE line logged
# is left out: those are probes that fail as expected (Fabricated's of MixinExtras's version, CI-12). Informational: it
# never fails.
#
# Usage: ci/corpus-causes.sh <game-dir> <console-output> [label]
# <console-output> is the game's own console (server.out, client.out), in the smoke script's work directory.
set -uo pipefail

dir="$1"
console="$2"
label="${3:-$1}"

# What both readers of exception chains below know of a line: an exception's first line, the outermost one's (an
# uncaught one's after the JVM's "Exception in thread") or a cause's or suppressed exception's, which Java indents
# within a suppressed one; and a log line, whose level an exception printed after it is logged at.
lines='
	function outermost(line) { return line ~ /^(Exception in thread "[^"]*" )?[A-Za-z_$][A-Za-z0-9_$.]*\.[A-Za-z0-9_$]*(Exception|Error|Throwable)(: |$)/ }
	function exceptionLine(line) { return outermost(line) || line ~ /^[ \t]*(Caused by|Suppressed): / }
	function logLine(line) { return line ~ /^\[[^]]*\] \[[^]]*\/[A-Z]+\]/ }
	function quiet(line) { return line ~ /^\[[^]]*\] \[[^]]*\/(DEBUG|TRACE)\]/ }
'

# An exception chain: its exception lines, numbered, then the first frames of its innermost cause, the last exception
# line; past the exceptions DEBUG and TRACE lines logged, and only between the lines [3] and [4] where given. A log
# line, or the test mod's saying the game ends, ends the last one's frames.
chain() {
	awk -v limit="$2" -v from="${3:-0}" -v to="${4:-0}" "$lines"'
		FNR < from { next }
		to > 0 && FNR > to { exit }
		logLine($0) || /^FABRICATED-TEST game ended / { skip = quiet($0); open = 0 }
		skip { next }
		exceptionLine($0) { print FNR ": " substr($0, 1, 400); last = FNR; n = 0; open = 1; next }
		open && /^[ \t]+at / && n < limit { frames[n++] = substr($0, 1, 300) }
		END { if (n) { print "  innermost cause, from line " last ":"; for (i = 0; i < n; i++) print "  " frames[i] } }
	' "$1"
}

# The console's last exception chain before the game ended, where the test mod's shutdown hook says it ends or the
# launcher that it exited: its first and last lines' numbers, from its outermost exception up to the next log line or a
# thread dump CI asked for.
last_chain() {
	awk "$lines"'
		/^(FABRICATED-TEST game ended from thread |Minecraft exited with code )/ { exit }
		logLine($0) { skip = quiet($0); open = 0; next }
		/^Full thread dump / { open = 0 }
		!skip && outermost($0) { open = 1; start = FNR }
		open { end = FNR }
		END { if (start) print start, end }
	' "$1"
}

# Mixin's failures whole, folded, where the lines above cut them: the hook, its target and what Mixin found there, the
# part a stop names last (owo's MixinExtras root, CORPUS-77).
whole() {
	grep -o -E '(Critical injection failure|[A-Za-z0-9_.$]*(InvalidInjectionException|InjectionError|SugarApplicationException|InvalidMixinException|MixinApplyError|InvalidAccessorException))[^ ]*.*' "$1" \
		| awk '!seen[$0]++' | head -n 6 | cut -c1-3000 | fold -w 300
	# A local discriminator's message goes on over the next lines: the locals MixinExtras and Mixin saw at the point
	# (ResourcefulLib's implicit PoseStack, CORPUS-79).
	grep -n -A 30 -m 1 -E 'Invalid implicit variable discriminator|Unable to find matching local|InvalidImplicitDiscriminatorException' "$1" \
		| cut -c1-300 | head -n 32
}

echo "== $label: what stopped it"
own="$(mktemp)"
trap 'rm -f "$own"' EXIT
# Why the smoke script stopped the game, where it says (a client: mc-runtime-test's stop, or the line it stopped on).
reason="$(dirname "$console")/stop-reason.txt"
if [ -f "$reason" ]; then
	echo "-- stopped by CI: $(head -c 400 "$reason")"
fi
found=
for crash in "$dir"/crash-reports/*.txt; do
	[ -f "$crash" ] || continue
	found=1
	echo "-- $(basename "$crash")"
	# The report's own exception: the lines from its description up to the system details, where the chain ends.
	sed -n '/^Description:/,/^-- System Details --/p' "$crash" > "$own"
	chain "$own" 25 | head -60
	echo "-- $(basename "$crash"): Mixin's failures, whole"
	whole "$own"
done
[ -n "$found" ] || echo "-- no crash report"
for fatal in "$dir"/hs_err_pid*.log "$(dirname "$dir")"/hs_err_pid*.log; do
	[ -f "$fatal" ] || continue
	echo "-- JVM fatal error log $(basename "$fatal")"
	head -n 30 "$fatal" | cut -c1-300
done
# Without a crash report, what ended the game is the console's last exception chain: one that ends a launch is printed
# there alone, uncaught, as the JVM exits (Forge 47's Mixin failing to apply FrozenLib's hook, CI-12). A game that
# started (a server's "Done", mc-runtime-test's lines as a client joins its world, as ci/corpus-summary.sh reads them)
# was stopped by the run, and an exception as it stops is no stop.
start=
if [ -z "$found" ] && [ -f "$console" ]; then
	if grep -q -E 'Done \([0-9.]+s\)!|Successfully finished\.|Running game tests\.\.\.' "$console"; then
		echo "-- the game started, and the run stopped it"
	elif read -r start end < <(last_chain "$console"); then
		echo "-- the console's last exception chain before the game ended"
		chain "$console" 20 "$start" "$end" | head -n 40
		echo "-- the console's last exception chain: Mixin's failures, whole"
		sed -n "${start},${end}p" "$console" > "$own"
		whole "$own"
	fi
fi
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
for log in "$dir/logs/latest.log" "$dir/logs/debug.log"; do
	[ -f "$log" ] || continue
	echo "-- $(basename "$log"): Mixin's errors"
	grep -n -E 'Mixin apply|MixinTransformerError|MixinApplyError|MixinPreProcessorException|InvalidMixinException|InvalidInjectionException|Critical injection failure|Mixin prepare for|Mixin config .* could not' "$log" | awk '!seen[substr($0, index($0, ":") + 1)]++' | head -30 | cut -c1-500
	echo "-- $(basename "$log"): Mixin's failures, whole"
	whole "$log"
	if [ "$(basename "$log")" = latest.log ]; then
		echo "-- $(basename "$log"): Fabricated's errors and warnings"
		grep -n -E '/(ERROR|WARN)\] \[Fabricated/\]' "$log" | awk '!seen[substr($0, index($0, ": ") + 2)]++' | head -40 | cut -c1-500
	fi
	echo "-- $(basename "$log"): exception chains (the last one's innermost frames)"
	chain "$log" 20 | tail -n 80
done
if [ -f "$console" ]; then
	# What the game printed as it stopped and its logs may lack, as a game the smoke script stops can leave them
	# unwritten: the registries' loading errors, each registry and element with its exception (the Forge 47 corpus
	# server's data packs), and the console's exception chains, up to the last one where the stop above is it.
	echo "-- the console: registries' loading errors"
	grep -n -E '^(Registry loading errors|>{1,2} Errors in (registry|element) )|Registry loading errors:' "$console" | head -n 40 | cut -c1-400
	echo "-- the console: exception chains${start:+ before its last} (the last one's innermost frames)"
	chain "$console" 20 1 "$((${start:-0} - 1))" | tail -n 60
	# A game CI stopped at its timeout printed every thread last (smoke-client.sh's SIGQUIT): the lines before that,
	# then what the game's own threads were doing, which the job log keeps only this way.
	echo "-- the console's last lines"
	awk '/^Full thread dump/ { exit } { print }' "$console" | tail -n 40 | cut -c1-400
	dump="$(dirname "$console")/thread-dump.txt"
	if [ -f "$dump" ]; then
		echo "-- the game's threads shortly before CI stopped it"
		awk 'BEGIN { RS = ""; ORS = "\n\n" } /^"(Render thread|Server thread|Worker-Main|IO-Worker|modloading-worker)/' "$dump" | head -n 200 | cut -c1-300
	fi
fi
exit 0
