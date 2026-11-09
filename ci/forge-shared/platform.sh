# What both Forge platforms' parts of CI share (ci/forge/platform.sh and ci/forge-legacy/platform.sh, on their
# branches, source it): how the harness (ci/smoke-lib.sh) installs and launches a Forge server and client. They set
# forge_id (the installer's version, Minecraft's included), testmod_key (the test mod's key binding in options.txt)
# and gradle_tasks first; root, platform, mc and loader are set.
# shellcheck shell=bash disable=SC2034,SC2154 # the harness reads these, and sets what it is sourced with
jar_globs=("fabricated-forge-[0-9]*+mc$mc.jar")
testmods_dir="$root/mc/$platform/$mc/build/testmods"
mods_toml=META-INF/mods.toml
server_timeout=600
server_reload=0
client_launch_id="$mc-forge"
mcrt_loader=lexforge

installer() {
	curl -fsSL --retry 4 -o installer.jar "https://maven.minecraftforge.net/net/minecraftforge/forge/$forge_id/forge-$forge_id-installer.jar"
}

install_server() {
	installer
	java -jar installer.jar --installServer . > installer.log 2>&1 || { tail -n 60 installer.log; return 1; }
}

# install_client <minecraft dir>
install_client() {
	echo '{"profiles":{}}' > "$1/launcher_profiles.json"
	installer
	java -jar installer.jar --installClient "$1" > installer.log 2>&1 || { tail -n 60 installer.log; return 1; }
	# The installer leaves the vanilla version's jar but not its version file, which HeadlessMC reads as the Forge
	# version's parent: it comes from Mojang's version manifest.
	if [ ! -f "$1/versions/$mc/$mc.json" ]; then
		local url
		url=$(curl -fsSL --retry 4 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
			| python3 -c 'import json, sys; print(next(v["url"] for v in json.load(sys.stdin)["versions"] if v["id"] == sys.argv[1]))' "$mc")
		mkdir -p "$1/versions/$mc"
		curl -fsSL --retry 4 -o "$1/versions/$mc/$mc.json" "$url"
	fi
}

# The test mod and the one that fails on purpose after leaving an intrusive holder nothing registers (its failure must
# be isolated, and the registries' freeze must drop the holder: CORPUS-42, HOLD-2), and a Fabric mod whose dependency is
# missing, which Fabricated doesn't load and Forge's loading warnings name. On a client the test mod alone: a Fabric mod
# that doesn't load would put Forge's warning screen in front of the game.
server_testmods() {
	cp "$testmods_dir/fabricated-testmod.jar" "$testmods_dir/fabricated-testmod-throws.jar" "$1/"
	mkdir -p unmet
	echo '{"schemaVersion": 1, "id": "fabricated_forge_unmet", "version": "1.0.0", "depends": {"fabricated_forge_missing": "*"}}' > unmet/fabric.mod.json
	jar --create --file "$1/fabricated-forge-unmet.jar" -C unmet fabric.mod.json
}
client_testmods() {
	cp "$testmods_dir/fabricated-testmod.jar" "$1/"
}

# On a server a mod whose entrypoint fails is disabled and the game goes on, as the test mod that fails on purpose does;
# a client keeps Fabricated's defaults, so one stops the game.
config_defaults() {
	if [ "$side" = server ]; then : "${SMOKE_ON_ENTRYPOINT_FAILURE=disable}"; fi
}

quiet_load_warnings() {
	printf '[client]\nshowLoadWarnings = false\n' > "$1/config/forge-client.toml"
}

client_keys() {
	echo "key_$testmod_key:key.keyboard.k"
}

standins() {
	python3 -I "$root/ci/$platform/standins.py" "$@"
}

# After a server run with the stand-ins: whether Fabricated's linkage check counted their classes (checks.tsv's
# forge-standin-linkage reads it).
after_run() {
	if [ "$side" = server ] && [ -n "$extra" ] && [ -f "$extra/standin-forgified-fabric-api.jar" ]; then
		local result=0
		python3 -I "$root/ci/forge-shared/standin-linkage.py" "$extra" logs/fabric-bridge-report.txt > standin-linkage.txt || result=$?
		echo "exit $result" >> standin-linkage.txt
	fi
}

# The newest Forge build for the version, named as the pinned one is.
newest_loader() {
	local newest
	newest=$(curl -fsSL --retry 4 https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml \
		| grep -o "<version>$mc-[^<]*" | sed 's/<version>//' | tail -n 1)
	if [ "$forge_id" = "$loader" ]; then echo "$newest"; else echo "${newest#"$mc"-}"; fi
}
