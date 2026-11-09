# Forge 47 and 52's part of CI (the forge-legacy branch's ci/forge-legacy/platform.sh): its build (ci.yml), and what
# sets it apart from Forge 61 to 66 for the harness; the rest is ci/forge-shared/platform.sh's. Sourced with root,
# platform, mc (the Minecraft version) and loader (the Forge build, Minecraft's version included, as ModDevGradle's
# legacyforge plugin takes it) set.
# shellcheck shell=bash disable=SC2034,SC2154 # the harness reads these, and sets what it is sourced with
forge_id="$loader"
testmod_key=key.fabricated-forge-testmod.check

# ModDevGradle's legacyforge plugin; testmods also reobfuscates the renderer API test mod to SRG's names. checkForgeJar
# checks the jar's layout for Forge.
gradle_tasks() {
	local project=":mc:forge-legacy-$mc"
	echo "-Pfabricated.platforms=forge-legacy --configure-on-demand $project:fabricatedJar $project:testmodJar" \
		"$project:testmodThrowsJar $project:testmods $project:checkForgeJar"
}

# shellcheck source=ci/forge-shared/platform.sh
source "$root/ci/forge-shared/platform.sh"
