#!/usr/bin/env bash
# Runs a part of a version's game runs (ci/runs.tsv, in its order): gathers each run's other mods, starts the harness
# (ci/smoke-server.sh, ci/smoke-client.sh), which checks the run (ci/check-log.py), and prints what an informational or
# failed run made of its mods. The runs' results join one JSON summary, checks-<platform>-<minecraft>-<part>.json.
#
# Usage: ci/smoke.sh <platform> <minecraft> <part> [<run>...]   the part's runs, or the named ones
#        ci/smoke.sh <platform> <minecraft> warm                fills the caches: both sides' installs, the pinned downloads
# Settings: EXTENDED=true adds the extended runs; SMOKE_WORK, the runs' folder ($RUNNER_TEMP/smoke); SMOKE_CACHE (see
# ci/smoke-lib.sh). Run from the repository's root, on the game's Java.
# Exit status: 1 when a run that gates failed.
# shellcheck disable=SC2154 # the version's pins are set by ci/plan.py target, below
set -uo pipefail

platform="$1"
mc="$2"
part="$3"
shift 3
cd "$(dirname "$0")/.." || exit 1
# The version's pins (loader, java, fabric_api, plugin_hosts, renderers, fabric_renderers), as shell assignments: taken
# apart from eval, which would hide plan.py's failure.
assignments=$(python3 -I ci/plan.py target "$platform" "$mc") || exit 1
eval "$assignments"
work="${SMOKE_WORK:-${RUNNER_TEMP:-/tmp}/smoke}"
side=
# shellcheck source=ci/smoke-lib.sh
source ci/smoke-lib.sh
mkdir -p "$work"
export SMOKE_SUMMARY="$work/checks-$platform-$mc-$part.json"
export MODRINTH_CACHE="${SMOKE_CACHE:+$SMOKE_CACHE/downloads/modrinth}"
rm -f "$SMOKE_SUMMARY"
fabric_api_url="https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$fabric_api/fabric-api-$fabric_api.jar"

fetch() {
	python3 -I ci/fetch-corpus.py "$mc" "$@"
}

if [ "$part" = warm ]; then
	SMOKE_INSTALL_ONLY=1 ci/smoke-server.sh "$platform" "$mc" "$loader" "$work/warm-server" || exit 1
	SMOKE_INSTALL_ONLY=1 ci/smoke-client.sh "$platform" "$mc" "$loader" "$work/warm-client" || exit 1
	download "$fabric_api_url" "$work/fabric-api.jar" || exit 1
	# The pinned builds (<project>@<version id>) the version's runs fetch: the plugin hosts' for the platform's loader
	# (Forge's for both Forge platforms), the renderers' for NeoForge.
	for pins in "${platform%-legacy} $plugin_hosts" "neoforge $renderers" "fabric $fabric_renderers"; do
		# shellcheck disable=SC2086 # the loader, then one pin per word
		set -- $pins
		case "$*" in *@*) fetch "$work/warm-mods" "$@" ;; esac
	done
	exit 0
fi

download "$fabric_api_url" "$work/fabric-api.jar" || exit 1
extended=()
[ "${EXTENDED:-}" = true ] && extended=(--extended)
failed=0
while IFS=$'\x1f' read -r run run_side gates needs mods env skip_reason; do
	dir="$work/$run"
	extra="$work/$run.mods"
	rm -rf "$extra"
	mkdir -p "$extra"
	settings=("SMOKE_RUN=$run")
	read -ra words <<< "$env"
	settings+=("${words[@]}")
	run_loader="$loader"
	echo "::group::$run: gathering its mods ($mods)"
	for token in $mods; do
		# shellcheck disable=SC2086 # a drop: token's glob is meant to expand
		case "$token" in
			-testmods) settings+=(SMOKE_TESTMODS=none) ;;
			testmod:*) cp "$testmods_dir/fabricated-testmod-${token#testmod:}.jar" "$extra/" 2>/dev/null || echo "No ${token#testmod:} test mod for $mc" ;;
			fabric-api) cp "$work/fabric-api.jar" "$extra/" ;;
			modrinth:*)
				spec="${token#modrinth:}"
				IFS=, read -ra projects <<< "${spec#*:}"
				fetch "$extra" "${spec%%:*}" "${projects[@]}" | tee -a "$extra/fetched.txt"
				;;
			corpus) fetch "$extra" | tee -a "$extra/fetched.txt" ;;
			corpus:advanced)
				# shellcheck disable=SC2046 # one project per word
				fetch "$extra" fabric $(sed -e '/^#/d' -e '/^$/d' ci/mod-corpus-advanced.txt) | tee -a "$extra/fetched.txt"
				;;
			drop:*) rm -fv "$extra"/${token#drop:} ;;
			standins) standins "$(fabricated_jar)" "$extra" ;;
			standins:*) standins "$(fabricated_jar)" "$extra" "${token#standins:}" ;;
			connector)
				# Sinytra Connector, as a stand-in with its mod id.
				mkdir -p "$extra/src/$(dirname "$mods_toml")"
				printf 'modLoader = "lowcodefml"\nloaderVersion = "[1,)"\nlicense = "GPL-3.0-only"\n[[mods]]\nmodId = "connector"\nversion = "1.0.0"\n' \
					> "$extra/src/$mods_toml"
				jar --create --file "$extra/connector-standin.jar" -C "$extra/src" "$mods_toml"
				;;
			fabricated-copy) cp "$(fabricated_jar)" "$extra/fabricated-0.0.1+mc$mc.jar" ;;
			unreadable-pack)
				# A Fabric mod whose pack.mcmeta isn't JSON.
				mkdir -p "$extra/src"
				printf '{"schemaVersion": 1, "id": "fabricated-unreadable-pack", "version": "1.0.0"}\n' > "$extra/src/fabric.mod.json"
				printf '{"pack": {"description": "cut off' > "$extra/src/pack.mcmeta"
				jar --create --file "$extra/fabricated-unreadable-pack.jar" -C "$extra/src" .
				;;
			energy)
				# Team Reborn's energy library, or without a build of its own the one a Fabric tech mod ships in its jar.
				fetch "$extra/energy" fabric team-reborn-energy | tee -a "$extra/fetched.txt"
				if ! ls "$extra"/energy/*.jar > /dev/null 2>&1; then
					fetch "$extra/energy-hosts" fabric techreborn modern-industrialization | tee -a "$extra/fetched.txt"
					python3 -I ci/nested-jar.py team_reborn_energy "$extra/energy" "$extra"/energy-hosts/*.jar | tee -a "$extra/fetched.txt"
				fi
				for jar in "$extra"/energy/*.jar; do [ -f "$jar" ] && cp "$jar" "$extra/team-reborn-energy.jar" && break; done
				;;
			seed:*)
				mkdir -p "$work/$run.seed"
				cp -r "$work/${token#seed:}" "$work/$run.seed/" 2>/dev/null || true
				settings+=("SMOKE_SEED=$work/$run.seed")
				;;
			reuse:*) dir="$work/${token#reuse:}" && settings+=(SMOKE_REUSE=1) ;;
			*) echo "ci/runs.tsv: $run: unknown mods token $token" >&2; exit 1 ;;
		esac
	done
	echo "::endgroup::"
	nothing=
	if [ -n "$needs" ] && [[ "$needs" == */* ]]; then
		compgen -G "$work/$needs" > /dev/null || nothing="$needs isn't there"
	elif [ -n "$needs" ] && ! grep -qE "^(${needs// /|}): [^n]" "$extra/fetched.txt" 2>/dev/null; then
		nothing="Modrinth has no build of $needs for $mc"
	fi
	if [[ " ${settings[*]} " == *" SMOKE_LOADER=newest "* ]]; then
		run_loader=$(newest_loader)
		echo "Newest $platform build for $mc: ${run_loader:-none} (pinned: $loader)"
		[ -z "$run_loader" ] || [ "$run_loader" = "$loader" ] && nothing="the pinned build is the newest"
	fi
	if [ -n "$nothing" ]; then
		python3 -I ci/check-log.py "$platform" "$mc" "$run" --skip "$nothing: $skip_reason" --summary "$SMOKE_SUMMARY"
		continue
	fi
	echo "== $run"
	status=0
	env "${settings[@]}" "ci/smoke-$run_side.sh" "$platform" "$mc" "$run_loader" "$dir" "$extra" || status=$?
	# A harness that stopped before its checks (an install that failed, say) left no entry: that is the run's failure.
	if [ "$(python3 -I ci/check-log.py --entries "$SMOKE_SUMMARY" "$run")" = 0 ]; then
		python3 -I ci/check-log.py "$platform" "$mc" "$run" --error "the harness stopped before its checks (exit status $status)" --summary "$SMOKE_SUMMARY"
		[ "$gates" = none ] || failed=1
	elif [ "$status" -ne 0 ]; then
		failed=1
	fi
	# What a run that failed, or an informational one of other people's mods, made of them.
	if [ "$status" -ne 0 ] || [ "$gates" = none ]; then
		game="$dir"
		console="$dir/server.out"
		[ "$run_side" = client ] && game="$dir/run" && console="$dir/client.out"
		ci/corpus-summary.sh "$game" "$run"
		ci/corpus-causes.sh "$game" "$console" "$run"
	fi
done < <(python3 -I ci/plan.py runs "$platform" "$mc" "$part" "${extended[@]}" "$@")

python3 -I ci/check-log.py --report "$SMOKE_SUMMARY"
exit "$failed"
