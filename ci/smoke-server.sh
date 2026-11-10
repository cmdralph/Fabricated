#!/usr/bin/env bash
# CI's server harness, every platform's: installs the platform's dedicated server (its adapter, ci/<platform>/platform.sh),
# adds Fabricated, the test mods and the extra mods, boots it, and checks the run against ci/checks.tsv
# (ci/check-log.py).
#
# Usage: ci/smoke-server.sh <platform> <minecraft> <loader version> <work-dir> [extra-mods-dir]
# Settings: SMOKE_RUN, the run of ci/runs.tsv it is (server); SMOKE_TESTMODS=none leaves the test mods out;
# SMOKE_TIMEOUT; SMOKE_WARM_LAUNCH=1 launches once more, up to the end of discovery, with everything cached;
# SMOKE_BENCH=1 records the heap the started server keeps (heap-histogram.txt, for ci/bench.py);
# SMOKE_REUSE=1 launches again in the work directory as the last launch left it; SMOKE_INSTALL_ONLY=1 only fills the
# install cache; SMOKE_ON_ENTRYPOINT_FAILURE, SMOKE_LOG_LEVEL and FABRICATED_MODS, Fabricated's config (the adapter
# has defaults); SMOKE_SUMMARY, the JSON summary the run's results join.
# Exit status: check-log.py's, 1 when a check that gates the run failed.
# Expects: the build's jar in build/libs and test mods (the adapter says where), and the game's Java on the PATH.
set -euo pipefail

platform="$1"
mc="$2"
loader="$3"
work="$4"
extra="${5:-}"
side=server
# shellcheck source=ci/smoke-lib.sh
source "$(dirname "$0")/smoke-lib.sh"
timeout_seconds="${SMOKE_TIMEOUT:-$server_timeout}"

if [ -z "${SMOKE_REUSE:-}" ]; then
	rm -rf "$work"
	mkdir -p "$work"
fi
cd "$work"
if [ -z "${SMOKE_REUSE:-}" ]; then
	echo "::group::Install $platform $loader"
	cached_install . install_server
	echo "::endgroup::"
	[ -n "${SMOKE_INSTALL_ONLY:-}" ] && exit 0
	add_mods mods
	write_config config
	echo "eula=true" > eula.txt
	cat > server.properties <<-'PROPS'
	online-mode=false
	level-type=minecraft\:flat
	view-distance=3
	simulation-distance=3
	spawn-protection=0
	max-tick-time=-1
	sync-chunk-writes=false
	PROPS
	# A free port, so a server another job or person runs on this machine can't stop it.
	echo "server-port=$(python3 -I -c 'import socket; s = socket.socket(); s.bind(("", 0)); print(s.getsockname()[1])')" >> server.properties
	chmod +x run.sh
fi
# shellcheck disable=SC1091 # what add_mods found, kept for a launch in the same folder
source flags.txt

echo "::group::Run server"
rm -f console
mkfifo console
# Kept open for writing, so the server doesn't see the end of its input.
exec 3<>console
./run.sh nogui < console > server.out 2>&1 &
pid=$!
start=$SECONDS
status=timeout
crashed() { grep -qE 'Preparing crash report|Game crashed!|Crash report saved to|FatalStartupException' server.out 2>/dev/null; }
while kill -0 "$pid" 2>/dev/null; do
	if crashed; then status=crashed; break; fi
	if grep -q 'Done (' server.out 2>/dev/null; then status=started; break; fi
	if [ $((SECONDS - start)) -gt "$timeout_seconds" ]; then break; fi
	sleep 5
done
# The server may have stopped between two looks: its output says how.
if [ "$status" = timeout ] && ! kill -0 "$pid" 2>/dev/null; then
	if crashed; then status=crashed; else status=exited; fi
fi
wait_for_exit=15
if [ "$status" = started ]; then
	# A few ticks for the tick-based checks, then a data pack reload, so reload listeners run a second time.
	sleep 10
	# The heap the started server keeps, after the full GC jcmd's class histogram makes (ci/bench.py): each class's
	# share, the total last.
	if [ -n "${SMOKE_BENCH:-}" ]; then
		jcmd "$(pgrep -P "$pid" -x java || echo "$pid")" GC.class_histogram > heap-histogram.txt 2>&1 || true
	fi
	echo "reload" >&3
	sleep "${server_settle:-5}"
	echo "stop" >&3
	wait_for_exit=60
fi
# A crashed server writes its report and exits; what it records as it stops (mixin-failures.json) is written by then.
for _ in $(seq 1 "$wait_for_exit"); do kill -0 "$pid" 2>/dev/null || break; sleep 2; done
kill_tree "$pid"
wait "$pid" 2>/dev/null || true
exec 3>&-
echo "$status" > status.txt
echo "Server: $status after $((SECONDS - start)) s"
echo "::endgroup::"

echo "::group::Server output (tail)"
tail -n 200 server.out || true
echo "::endgroup::"
diagnose logs/latest.log server.out
if [ -n "$has_extra" ]; then
	echo "::group::Where the Fabric API classes the game could not find come from"
	python3 -I "$root/ci/find-classes.py" logs/latest.log mods || true
	echo "::endgroup::"
fi
for report in crash-reports/*.txt; do
	[ -f "$report" ] || continue
	echo "::group::Crash report $(basename "$report") (first 120 lines)"
	head -n 120 "$report"
	echo "::endgroup::"
done
if [ -f logs/debug.log ]; then
	# Forge's discovery logs at debug level, which only debug.log keeps.
	echo "::group::The loader's discovery and Fabricated (debug.log)"
	grep -n -i -E "locator|language provider|langprovider|fabricated" logs/debug.log | cut -c1-400 | head -n 80 || true
	echo "::endgroup::"
fi
echo "::group::fabric-bridge-report.txt"
cat logs/fabric-bridge-report.txt 2>/dev/null || echo "(report missing)"
echo "::endgroup::"

# A second launch, up to the end of discovery: every translation comes from the cache, and how long discovery takes with
# everything cached (the launch timings in the report). Its logs are kept beside the first's (warm-*), which stay where
# the checks read them.
if [ "$status" = started ] && [ -n "${SMOKE_WARM_LAUNCH:-}" ]; then
	mkdir -p cold
	cp logs/*.log logs/fabric-bridge-report.txt cold/
	exec 3<>console
	./run.sh nogui < console > server-warm.out 2>&1 &
	pid=$!
	for _ in $(seq 1 60); do grep -q "Fabricated discovery finished" server-warm.out 2>/dev/null && break; kill -0 "$pid" 2>/dev/null || break; sleep 2; done
	sleep 2
	kill_tree "$pid"
	wait "$pid" 2>/dev/null || true
	exec 3>&-
	mv logs/fabric-bridge-report.txt fabric-bridge-report-warm.txt
	cp cold/* logs/
	echo "Discovery, cold: $(grep -o 'discovery finished in .*' server.out | head -1 | cut -c1-400)"
	echo "Discovery, warm: $(grep -o 'discovery finished in .*' server-warm.out | head -1 | cut -c1-400)"
	for report in logs/fabric-bridge-report.txt fabric-bridge-report-warm.txt; do
		launch=$([ "$report" = logs/fabric-bridge-report.txt ] && echo cold || echo warm)
		awk -v launch="$launch" '/^== Launch timings ==/ { show = 1; next } /^$/ { show = 0 } show { print "Launch timings, " launch ":" $0 }' "$report" | cut -c1-400
	done
fi

if declare -F after_run > /dev/null; then after_run; fi
evaluate
