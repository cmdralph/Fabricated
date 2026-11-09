# Forge 61 to 66's part of CI (the forge branch's ci/forge/platform.sh): its build (ci.yml), and what sets it apart
# from Forge 47 for the harness; the rest is ci/forge-shared/platform.sh's. Sourced with root, platform, mc (the
# Minecraft version) and loader (the Forge build, without Minecraft's version) set.
# shellcheck shell=bash disable=SC2034,SC2154 # the harness reads these, and sets what it is sourced with
forge_id="$mc-$loader"
testmod_key=key.fabricated_forge_testmod.check

# ForgeGradle 7 configures only the requested project and what it depends on (--configure-on-demand); checkForgeJar
# checks the jar's layout for Forge.
gradle_tasks() {
	local project=":mc:forge-$mc"
	echo "-Pfabricated.platforms=forge --configure-on-demand $project:fabricatedJar $project:testmodJar $project:testmodThrowsJar" \
		"$project:testmodFrapiJar $project:checkForgeJar"
}

# shellcheck source=ci/forge-shared/platform.sh
source "$root/ci/forge-shared/platform.sh"
