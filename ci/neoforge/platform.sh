# NeoForge's part of CI: how its build is made (ci.yml), and how the harness (ci/smoke-lib.sh) installs and launches its
# server and client. Sourced with root, mc (the Minecraft version) and loader (the NeoForge version) set.
# shellcheck shell=bash disable=SC2034,SC2154 # the harness reads these, and sets what it is sourced with
jar_globs=("fabricated-neoforge-[0-9]*+mc$mc.jar")
testmods_dir="$root/mc/neoforge/$mc/build/testmods"
mods_toml=META-INF/neoforge.mods.toml
server_timeout=900
# The server reloads its data packs once started, so reload listeners run a second time (checks.tsv's reload-again).
server_reload=1
server_settle=10
client_launch_id="neoforge-$loader"
mcrt_loader=neoforge

gradle_tasks() {
	echo ":mc:neoforge-$mc:fabricatedJar :mc:neoforge-$mc:testmods"
}

installer() {
	curl -fsSL --retry 4 -o installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$loader/neoforge-$loader-installer.jar"
}

install_server() {
	installer
	java -jar installer.jar --installServer . > installer.log 2>&1 || { cat installer.log; return 1; }
}

# install_client <minecraft dir>
install_client() {
	# The installer only checks that a launcher profile file exists.
	echo '{"profiles":{}}' > "$1/launcher_profiles.json"
	installer
	java -jar installer.jar --installClient "$1" > installer.log 2>&1 || { cat installer.log; return 1; }
}

# Every test mod on a server, the deliberately broken ones included; on a client only the working one, as the broken
# ones would put NeoForge's warning screen in front of the game.
server_testmods() {
	cp "$testmods_dir"/*.jar "$1/"
}
client_testmods() {
	cp "$testmods_dir/fabricated-testmod.jar" "$1/"
}

# Fabricated's config: debug logging, and on a server, or a client with other mods, a mod whose entrypoint fails is
# disabled and the game goes on, so its isolation is checked.
config_defaults() {
	: "${SMOKE_LOG_LEVEL=DEBUG}"
	if [ "$side" = server ] || [ -n "$has_extra" ]; then : "${SMOKE_ON_ENTRYPOINT_FAILURE=disable}"; fi
}

# With other mods, NeoForge doesn't put its loading warnings on a screen, which mc-runtime-test takes for a failure;
# they are still logged ("Mods loaded with N warning(s)").
quiet_load_warnings() {
	echo "showLoadWarnings = false" > "$1/config/neoforge-client.toml"
}

# The keys options.txt saves for test mods' key bindings: on 1.21.1 the renderer API test mod's (KEYMAP-2).
client_keys() {
	if [ "$mc" = 1.21.1 ]; then echo "key_key.fabricated_testmod_frapi.check:key.keyboard.k"; fi
}

standins() {
	python3 -I "$root/ci/neoforge/standins.py" "$@"
}

# The newest NeoForge build of the pinned build's line.
newest_loader() {
	curl -fsSL --retry 4 https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml | python3 -c '
import re, sys
builds = [v for v in re.findall(r"<version>([^<]+)</version>", sys.stdin.read()) if v.startswith(sys.argv[1])]
print(builds[-1] if builds else "")' "${loader%.*}."
}
