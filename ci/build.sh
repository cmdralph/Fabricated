#!/usr/bin/env bash
# Builds one version's Fabricated jar and test mods the way its platform's adapter says (ci/<platform>/platform.sh),
# and names them in $GITHUB_OUTPUT (jar, testmods), for CI's artifacts.
#
# Usage: ci/build.sh <platform> <minecraft> [<Gradle option>...]
set -euo pipefail

platform="$1"
mc="$2"
shift 2
cd "$(dirname "$0")/.."
# The version's pins, as shell assignments: taken apart from eval, which would hide plan.py's failure.
assignments=$(python3 -I ci/plan.py target "$platform" "$mc")
eval "$assignments"
side=build
work=
# shellcheck source=ci/smoke-lib.sh
source ci/smoke-lib.sh
# shellcheck disable=SC2046 # one task or option per word
./gradlew $(gradle_tasks) --stacktrace "$@"
jar=$(fabricated_jar)
echo "Built ${jar#"$root"/}"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
	printf 'jar=%s\ntestmods=%s\n' "${jar#"$root"/}" "${testmods_dir#"$root"/}" >> "$GITHUB_OUTPUT"
fi
