# Sourced by the smoke scripts. A CI job starts the game several times on one version, and each start used to run the
# official installer again. The first run's install (before any mod is added) is now kept in $SMOKE_CACHE and the runs
# after it copy it. In GitHub Actions SMOKE_CACHE defaults to the job's temporary folder, so nothing outlives the job;
# elsewhere, or with SMOKE_CACHE set empty, every run installs.
SMOKE_CACHE="${SMOKE_CACHE-${RUNNER_TEMP:+$RUNNER_TEMP/smoke-cache}}"

# cached_install <key> <dir> <command...>: <command> installs into <dir> (relative to the current folder), once per key.
cached_install() {
	local key="$1" dir="$2"
	shift 2
	if [ -n "$SMOKE_CACHE" ] && [ -f "$SMOKE_CACHE/$key.complete" ]; then
		mkdir -p "$dir"
		cp -a "$SMOKE_CACHE/$key/." "$dir/"
		echo "Copied the install of $key from the first run"
		return
	fi
	"$@"
	if [ -n "$SMOKE_CACHE" ]; then
		rm -rf "${SMOKE_CACHE:?}/$key"
		mkdir -p "$SMOKE_CACHE/$key"
		cp -a "$dir/." "$SMOKE_CACHE/$key/"
		touch "$SMOKE_CACHE/$key.complete"
	fi
}
