# Forge 47 and 52's part of CI (the forge-legacy branch's ci/forge-legacy/platform.sh): its build (ci.yml), and what
# sets it apart from Forge 61 to 66 for the harness; the rest is ci/forge-shared/platform.sh's. Sourced with root,
# platform, mc (the Minecraft version) and loader (the Forge build: with Minecraft's version on 1.20.1, as ModDevGradle's
# legacyforge plugin takes it; without it on 1.21.1, as ForgeGradle's) set.
# shellcheck shell=bash disable=SC2034,SC2154 # the harness reads these, and sets what it is sourced with
case "$loader" in
	"$mc"-*) forge_id="$loader" ;;
	*) forge_id="$mc-$loader" ;;
esac
testmod_key=key.fabricated-forge-testmod.check

# ModDevGradle's legacyforge plugin (1.20.1), or ForgeGradle (1.21.1); testmods also remaps 1.20.1's test mods to
# Intermediary's names. checkForgeJar checks the jar's layout for Forge.
gradle_tasks() {
	local project=":mc:forge-legacy-$mc"
	echo "-Pfabricated.platforms=forge-legacy --configure-on-demand $project:fabricatedJar $project:testmods $project:checkForgeJar"
}

# shellcheck source=ci/forge-shared/platform.sh
source "$root/ci/forge-shared/platform.sh"
