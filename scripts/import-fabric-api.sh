#!/usr/bin/env bash
# Copies Fabric API module sources (Apache-2.0) at an exact upstream tag into a Fabricated version
# directory. Used for the modules Fabricated ships natively (see fabric-api-plan.json). Re-run it when
# moving a Minecraft version to a newer Fabric API release; review the diff before committing.
#
# Usage: scripts/import-fabric-api.sh <fabric-api-tag> <destination-dir> <module>[:api] [module[:api]...]
# A ":api" suffix imports only the module's public API (net/fabricmc/fabric/api/**); Fabricated then
# provides the implementation itself on NeoForge events instead of upstream's mixins.
# Files in <destination-dir>-overrides/java are copied over the imported ones afterwards.
# Example: scripts/import-fabric-api.sh 0.162.0+26.3 mc/26.3/src/fabricapi fabric-api-base fabric-lifecycle-events-v1:api
set -euo pipefail

if [ "$#" -lt 3 ]; then
	echo "usage: $0 <fabric-api-tag> <destination-dir> <module> [module...]" >&2
	exit 2
fi

tag="$1"
dest="$2"
shift 2

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

git clone --quiet --depth 1 --filter=blob:none --no-checkout --branch "$tag" https://github.com/FabricMC/fabric "$work/fabric"
patterns=()
for spec in "$@"; do
	module="${spec%%:api}"
	patterns+=("/$module/src/main/java/" "/$module/src/client/java/")
done
git -C "$work/fabric" sparse-checkout set --no-cone "${patterns[@]}"
git -C "$work/fabric" checkout --quiet

mkdir -p "$dest/java"
for spec in "$@"; do
	module="${spec%%:api}"
	found=0
	for part in main client; do
		src="$work/fabric/$module/src/$part/java"
		[ -d "$src" ] || continue
		if [ "$spec" != "$module" ]; then
			if [ -d "$src/net/fabricmc/fabric/api" ]; then
				mkdir -p "$dest/java/net/fabricmc/fabric/api"
				cp -R "$src/net/fabricmc/fabric/api/." "$dest/java/net/fabricmc/fabric/api/"
				found=1
			fi
		else
			cp -R "$src/." "$dest/java/"
			found=1
		fi
	done
	if [ "$found" -eq 0 ]; then
		echo "module $module not found at $tag" >&2
		exit 1
	fi
done

# Files Fabricated had to adapt (e.g. an API default method relying on a module it doesn't provide) live in
# <destination>-overrides/java and replace the imported copies.
if [ -d "$dest-overrides/java" ]; then
	cp -R "$dest-overrides/java/." "$dest/java/"
fi

commit="$(git -C "$work/fabric" rev-parse HEAD)"
{
	echo "# Imported by scripts/import-fabric-api.sh - do not edit by hand."
	echo "tag=$tag"
	echo "commit=$commit"
	echo "modules=$*"
} > "$dest/UPSTREAM.properties"
echo "Imported $* from FabricMC/fabric $tag ($commit) into $dest"
