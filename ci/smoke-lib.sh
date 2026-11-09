# Sourced by the harness (ci/smoke-server.sh, ci/smoke-client.sh) once it set platform, mc, loader, work and side: the
# platform's adapter (ci/<platform>/platform.sh, which installs and launches that loader) and what both sides share.
# shellcheck shell=bash disable=SC2154 # platform, mc, loader, work and side are the sourcing script's
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=ci/neoforge/platform.sh
source "$root/ci/$platform/platform.sh"

# A job starts the game several times on one version. Each side's install (before any mod is added) is kept in
# $SMOKE_CACHE/<side>, and every run after the first copies it; CI restores and saves that folder between jobs (ci.yml).
# In GitHub Actions SMOKE_CACHE defaults to the job's temporary folder; elsewhere, or set empty, every run installs.
SMOKE_CACHE="${SMOKE_CACHE-${RUNNER_TEMP:+$RUNNER_TEMP/smoke-cache}}"

# cached_install <dir> <command...>: <command> installs into <dir> (relative to the current folder), once per loader
# version, and once more if it fails: a loader's installer downloads its libraries itself, past curl's retries, and a
# second run reuses what the first downloaded. With SMOKE_INSTALL_ONLY it only fills the cache.
cached_install() {
	local dir="$1" cache="$SMOKE_CACHE/$side/$platform-$loader"
	shift
	if [ -n "$SMOKE_CACHE" ] && [ -f "$cache.complete" ]; then
		[ -n "${SMOKE_INSTALL_ONLY:-}" ] && return
		mkdir -p "$dir"
		cp -a "$cache/." "$dir/"
		echo "Copied the $side install of $platform $loader from the cache"
		return
	fi
	"$@" || { echo "The $side install of $platform $loader failed; trying it once more"; "$@"; } || return 1
	if [ -n "$SMOKE_CACHE" ]; then
		rm -rf "$cache"
		mkdir -p "$cache"
		cp -a "$dir/." "$cache/"
		touch "$cache.complete"
	fi
}

# download <url> <file>: a pinned download, kept in the cache's downloads folder by its URL.
download() {
	local cached
	cached="$SMOKE_CACHE/downloads/$(printf '%s' "$1" | sha256sum | cut -c1-32)"
	if [ -n "$SMOKE_CACHE" ] && [ -s "$cached" ]; then
		cp "$cached" "$2"
		return
	fi
	curl -fsSL --retry 8 --retry-max-time 300 -o "$2" "$1"
	if [ -n "$SMOKE_CACHE" ]; then
		mkdir -p "$SMOKE_CACHE/downloads"
		cp "$2" "$cached"
	fi
}

# The Fabricated jar the build left for this version (the newest, should a local build have left several).
fabricated_jar() {
	local name found
	for name in "${jar_globs[@]}"; do
		# shellcheck disable=SC2086,SC2012 # the glob is meant to expand; the names are the build's
		found=$(ls -t "$root"/build/libs/$name 2>/dev/null | head -n 1)
		if [ -n "$found" ]; then echo "$found"; return; fi
	done
	echo "No Fabricated jar for Minecraft $mc in build/libs (${jar_globs[*]})" >&2
	return 1
}

# add_mods <mods dir>: Fabricated, the side's test mods (SMOKE_TESTMODS=none leaves them out) and the extra mods; the
# folder's listing goes to mods.txt, and what the run had to flags.txt, which a run in the same folder reads again.
add_mods() {
	mkdir -p "$1"
	cp "$(fabricated_jar)" "$1/"
	testmods="${SMOKE_TESTMODS:-all}"
	if [ "$testmods" != none ]; then
		"${side}_testmods" "$1"
		if [ "$side" = server ]; then
			# Two Fabric mods that depend on each other, as Fabric allows: the loader's sorter refuses a cycle of load
			# orderings, so Fabricated leaves one of theirs out (ORDER-1), and the server must still start.
			mkdir -p cycle
			for self in a b; do
				other=$([ "$self" = a ] && echo b || echo a)
				printf '{"schemaVersion": 1, "id": "fabricated_cycle_%s", "version": "1.0.0", "depends": {"fabricated_cycle_%s": "*"}}\n' "$self" "$other" > cycle/fabric.mod.json
				jar --create --file "$1/fabricated-cycle-$self.jar" -C cycle fabric.mod.json
			done
		fi
	fi
	has_extra=
	if [ -n "$extra" ] && ls "$extra"/*.jar > /dev/null 2>&1; then
		cp "$extra"/*.jar "$1/"
		has_extra=1
	fi
	# shellcheck disable=SC2012 # a listing for the log and the checks
	ls -1 "$1" | tee mods.txt
	printf 'testmods=%s\nhas_extra=%s\n' "$testmods" "$has_extra" > flags.txt
}

# write_config <config dir>: Fabricated's config from SMOKE_ON_ENTRYPOINT_FAILURE, SMOKE_LOG_LEVEL and FABRICATED_MODS
# (its "mods" object), each left out when empty, the adapter's defaults filled in first (config_defaults).
write_config() {
	config_defaults
	local fields=()
	[ -n "${SMOKE_ON_ENTRYPOINT_FAILURE:-}" ] && fields+=("\"onEntrypointFailure\": \"$SMOKE_ON_ENTRYPOINT_FAILURE\"")
	[ -n "${SMOKE_LOG_LEVEL:-}" ] && fields+=("\"logLevel\": \"$SMOKE_LOG_LEVEL\"")
	[ -n "${FABRICATED_MODS:-}" ] && fields+=("\"mods\": $FABRICATED_MODS")
	[ ${#fields[@]} -eq 0 ] && return
	mkdir -p "$1"
	(IFS=,; echo "{ \"schema\": 1, ${fields[*]} }") | tee "$1/fabricated.json"
}

# descendants <pid>, kill_tree <pid>: a process's children, theirs and so on; the process with them all, given five
# seconds to stop (run.sh starts Java as its child, HeadlessMC the game).
descendants() {
	local child
	for child in $(pgrep -P "$1" || true); do
		echo "$child"
		descendants "$child"
	done
}
kill_tree() {
	local pids pid alive
	pids="$(descendants "$1") $1"
	# shellcheck disable=SC2086 # one pid per word
	kill $pids 2>/dev/null || true
	for _ in 1 2 3 4 5; do
		sleep 1
		alive=
		for pid in $pids; do kill -0 "$pid" 2>/dev/null && alive=1; done
		[ -z "$alive" ] && return 0
	done
	# shellcheck disable=SC2086
	kill -9 $pids 2>/dev/null || true
}

# diagnose <game log> <console>: the first errors and Mixin's failures, for the log.
diagnose() {
	echo "::group::First errors in the game log"
	# Mixin's notice for each config without minVersion is an ERROR line that changes nothing.
	grep -n -E "/(ERROR|FATAL)\]|Exception|Caused by" "$1" 2>/dev/null | grep -v 'does not specify "minVersion"' | head -n 80 || true
	echo "::endgroup::"
	echo "::group::Mixin failures in the logs"
	grep -h -m 20 -E "InjectionError|Invalid[A-Za-z]*Exception|MixinApplyError|MixinInitialisationError|Mixin apply for mod .* failed" \
		"$2" "$1" 2>/dev/null | cut -c1-600 || true
	echo "::endgroup::"
}

# evaluate: the run's checks (ci/check-log.py), whose exit status is the harness's.
evaluate() {
	local args=("$platform" "$mc" "${SMOKE_RUN:-$side}" "$work" --loader "$loader")
	# shellcheck disable=SC1091
	source "$work/flags.txt"
	[ -n "$has_extra" ] && args+=(--extra)
	[ "$testmods" = none ] && args+=(--no-testmods)
	[ -n "${SMOKE_SUMMARY:-}" ] && args+=(--summary "$SMOKE_SUMMARY")
	echo "Checks of ${SMOKE_RUN:-$side}:"
	python3 -I "$root/ci/check-log.py" "${args[@]}"
}
