#!/usr/bin/env bash
# Forge's own transformers of game classes, as the installed server's libraries hold them, for pre-flight to read the
# game's classes as they leave them (CORPUS-61's Forge half): the coremod resources and classes in Forge's jars, the
# resources printed whole (they are short target lists and scripts), the classes' string constants and the calls and
# field accesses they make (javap), which name the methods, fields and classes they change. Informational: it never
# fails. Only the JDK's javap reads the classes; nothing from the jars runs.
#
# Usage: ci/forge/forge-coremods.sh <server-dir>
set -uo pipefail

dir="$1"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
pattern='coremod|FieldToMethod|finalize_spawn|Redirector|FieldRedirect|MethodRedirect|ReplaceField|transformers?/'

echo "== Forge's own transformers in $dir/libraries"
find "$dir/libraries" -name '*.jar' -path '*minecraftforge*' 2>/dev/null | sort | while read -r jar; do
	entries="$(unzip -Z1 "$jar" 2>/dev/null | grep -i -E "$pattern" | grep -v '/$' | head -60)"
	[ -n "$entries" ] || continue
	echo "-- $(basename "$jar")"
	echo "$entries" | sed 's/^/  /'
	target="$out/$(basename "$jar" .jar)"
	mkdir -p "$target"
	echo "$entries" | head -60 | while read -r entry; do
		unzip -q -o "$jar" "$entry" -d "$target" 2>/dev/null || continue
		case "$entry" in
			*.class)
				echo "--- $entry: constants, calls and fields"
				javap -c -p -constants "$target/$entry" 2>/dev/null \
					| grep -E 'ldc|invoke|getfield|putfield|getstatic|// (String|Method|Field|class) |^  [a-z].*\(' \
					| sed -E 's/^ +//' | awk '!seen[$0]++' | head -40 | cut -c1-260
				;;
			*.json|*.js|*.txt|*.cfg)
				echo "--- $entry"
				head -c 6000 "$target/$entry" | cut -c1-260
				echo
				;;
		esac
	done
done
# The services Forge loads transformers through, by name: which classes are coremods.
find "$dir/libraries" -name '*.jar' -path '*minecraftforge*' 2>/dev/null | sort | while read -r jar; do
	for service in $(unzip -Z1 "$jar" 2>/dev/null | grep -E '^META-INF/services/.*(coremod|ICoreMod|ITransformer|ITransformationService)' ); do
		echo "-- $(basename "$jar") $service:"
		unzip -p "$jar" "$service" 2>/dev/null | sed 's/^/  /' | head -20
	done
done
exit 0
