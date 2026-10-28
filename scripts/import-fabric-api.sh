#!/usr/bin/env bash
# Copies Fabric API module sources (Apache-2.0) at an exact upstream tag into a Fabricated version
# directory. Used for the modules Fabricated ships natively (see fabric-api-plan.json). Re-run it when
# moving a Minecraft version to a newer Fabric API release; review the diff before committing.
#
# Usage: scripts/import-fabric-api.sh [--yarn <yarn-version>] <fabric-api-tag> <destination-dir> <module>[:api] [module[:api]...]
# A ":api" suffix imports only the module's public API (net/fabricmc/fabric/api/**); Fabricated then
# provides the implementation itself on NeoForge events instead of upstream's mixins.
# Files in <destination-dir>-overrides/java are copied over the imported ones afterwards.
# Example: scripts/import-fabric-api.sh 0.164.0+26.3 mc/neoforge/26.3/src/fabricapi fabric-api-base fabric-lifecycle-events-v1:api
#
# Upstream writes the game in Mojang's names from 1.21.11 on, as the in-game mod compiles. For older versions it writes
# Yarn's: --yarn names the Yarn build the tag's sources use, and the sources are then migrated to Mojang's names with
# Fabric Loom's migrateMappings in a scratch project (Loom 1.18 needs a Java 25 JVM to run Gradle; JAVA_HOME must point
# at one). Mojang's mappings only rename the sources; the result is upstream's code otherwise.
# Example: scripts/import-fabric-api.sh --yarn 1.21.1+build.3 0.116.17+1.21.1 mc/neoforge/1.21.1/src/fabricapi fabric-api-base
set -euo pipefail

yarn=""
if [ "${1:-}" = "--yarn" ]; then
	yarn="$2"
	shift 2
fi

if [ "$#" -lt 3 ]; then
	echo "usage: $0 [--yarn <yarn-version>] <fabric-api-tag> <destination-dir> <module> [module...]" >&2
	exit 2
fi

tag="$1"
dest="$2"
shift 2
root="$(cd "$(dirname "$0")/.." && pwd)"

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

# The sources as upstream writes them, then (with --yarn) migrated into $dest. keep.txt lists the files that go to $dest:
# a ":api" module's public API only. With --yarn, its other sources are migrated too, so the names its API uses from
# them (an accessor mixin's methods, which the migration renames after their fields) change with them.
imported="$work/imported"
keep="$work/keep.txt"
mkdir -p "$imported"
: > "$keep"
for spec in "$@"; do
	module="${spec%%:api}"
	found=0
	for part in main client; do
		src="$work/fabric/$module/src/$part/java"
		[ -d "$src" ] || continue
		if [ "$spec" != "$module" ]; then
			[ -d "$src/net/fabricmc/fabric/api" ] || continue
			(cd "$src" && find net/fabricmc/fabric/api -type f) >> "$keep"
			if [ -n "$yarn" ]; then
				cp -R "$src/." "$imported/"
			else
				mkdir -p "$imported/net/fabricmc/fabric/api"
				cp -R "$src/net/fabricmc/fabric/api/." "$imported/net/fabricmc/fabric/api/"
			fi
		else
			(cd "$src" && find . -type f | sed 's|^\./||') >> "$keep"
			cp -R "$src/." "$imported/"
		fi
		found=1
	done
	if [ "$found" -eq 0 ]; then
		echo "module $module not found at $tag" >&2
		exit 1
	fi
done

if [ -n "$yarn" ]; then
	minecraft="${tag#*+}"
	project="$work/migrate"
	mkdir -p "$project/gradle"
	cp -R "$root/gradle/wrapper" "$project/gradle/"
	cp "$root/gradlew" "$project/"
	cat > "$project/settings.gradle" <<-EOF
		pluginManagement {
		    repositories {
		        maven { url = 'https://maven.fabricmc.net/' }
		        gradlePluginPortal()
		    }
		}
		rootProject.name = 'migrate'
	EOF
	# The whole Fabric API release on the class path, so the migration resolves what the imported sources use.
	cat > "$project/build.gradle" <<-EOF
		plugins { id 'fabric-loom' version '1.18.4' }
		repositories { maven { url = 'https://maven.fabricmc.net/' } }
		dependencies {
		    minecraft 'com.mojang:minecraft:${minecraft}'
		    mappings 'net.fabricmc:yarn:${yarn}:v2'
		    modImplementation 'net.fabricmc:fabric-loader:0.16.14'
		    modImplementation 'net.fabricmc.fabric-api:fabric-api:${tag}'
		}
	EOF
	(cd "$project" && ./gradlew --no-daemon --quiet migrateMappings --mappings "net.minecraft:mappings:${minecraft}" \
			--input "$imported" --output "$work/migrated")
	imported="$work/migrated"
fi

mkdir -p "$dest/java"
tar -C "$imported" -cf - -T "$keep" | tar -C "$dest/java" -xf -

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
	[ -n "$yarn" ] && echo "yarn=$yarn (migrated to Mojang's names with Fabric Loom's migrateMappings)"
	echo "modules=$*"
} > "$dest/UPSTREAM.properties"
echo "Imported $* from FabricMC/fabric $tag ($commit) into $dest"
