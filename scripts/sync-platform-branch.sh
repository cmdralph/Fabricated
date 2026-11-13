#!/usr/bin/env bash
# Merges main into a Forge platform branch, keeping the branch's own platform and leaving main's out. main holds the
# shared code (the code both Forge platforms share included, CI's one workflow and harness among it) and NeoForge;
# forge adds Forge 61 to 66, forge-legacy Forge 47 and 52. Shared changes go to main and reach the platform branches
# through this merge; a platform branch is never merged into main.
#
# Run it in a checkout of a working branch made from the platform branch, with main fetched. It makes a merge commit
# (no rebase, no force): the branch's own files are kept as they are, the files only main has are left out, the files
# the branch no longer needs are deleted, and every shared file is main's. Then push the working branch and open a pull
# request into the platform branch, whose full matrix checks the merge.
#
# Usage: scripts/sync-platform-branch.sh <forge|forge-legacy> [<main ref, default origin/main>]
set -euo pipefail

platform="${1:-}"
base="${2:-origin/main}"
case "$platform" in
	forge)
		own=(mc/forge ci/forge README.md docs/FORGE.md)
		gone=(loader/forge)
		;;
	forge-legacy)
		own=(mc/forge-legacy mc/platform-forge-legacy.gradle ci/forge-legacy README.md docs/FORGE-LEGACY.md)
		gone=(loader/forge-legacy ci/forge-legacy/ffapi-anatomy.py)
		;;
	*)
		echo "Usage: $0 <forge|forge-legacy> [<main ref>]" >&2
		exit 2
		;;
esac
# What the branch had of its own until CI became one workflow (.github/workflows/ci.yml) and one harness (ci/smoke*.sh,
# with the platform's adapter ci/<platform>/platform.sh): deleted, as main holds what replaced them
# (ci/forge-shared/standin-linkage.py, ci/corpus-causes.sh among them). Gone once, these delete nothing again.
gone+=(".github/workflows/build-$platform.yml" "ci/$platform/smoke-server.sh" "ci/$platform/smoke-client.sh"
	"ci/$platform/corpus-causes.sh" "ci/$platform/difficult-summary.sh" "ci/$platform/standin-linkage.py")
# What only main has: NeoForge's platform and the release workflow, which builds every platform from main.
mains=(loader/neoforge mc/neoforge mc/platform-neoforge.gradle ci/neoforge .github/workflows/release.yml)

# The paths below are the repository's: what is removed and checked out is named from its root, wherever this is run.
cd "$(git rev-parse --show-toplevel)"
if [ -n "$(git status --porcelain)" ]; then
	echo "The working tree has changes; commit or stash them first." >&2
	exit 1
fi
# ci.yml runs the branch's platform through its adapter: without it, every job of the sync's pull request would fail.
if ! git cat-file -e "HEAD:ci/$platform/platform.sh" 2>/dev/null; then
	echo "ci/$platform/platform.sh, the platform's part of CI, is missing: add it first." >&2
	exit 1
fi

# Shared paths are main's: a platform branch never changes them, so the merge takes them as main has them rather than
# merging them, which git's rename detection gets wrong across the platforms' folders (it pairs a platform's file with
# one main moved). A shared file the branch changed since its last sync is named, as that change belongs on main.
base_commit=$(git rev-parse "$base")
platform_paths=()
for path in "${own[@]}" "${mains[@]}" "${gone[@]}"; do platform_paths+=(":(exclude)$path"); done
edited=$(git diff --name-only "$(git merge-base HEAD "$base_commit")" HEAD -- . "${platform_paths[@]}")
if [ -n "$edited" ]; then
	echo "warning: the branch changed these shared files since its last sync; they are taken as main has them:" >&2
	echo "$edited" >&2
fi
git merge --no-ff --no-commit -s ours "$base_commit" > /dev/null
git read-tree --reset -u "$base_commit"
# The branch's own files stay as they are.
for path in "${own[@]}"; do
	if git cat-file -e "HEAD:$path" 2>/dev/null; then
		git rm -r -q --cached --ignore-unmatch -- "$path"
		rm -rf -- "$path"
		git checkout HEAD -- "$path"
	fi
done
for path in "${mains[@]}" "${gone[@]}"; do
	git rm -r -q --cached --ignore-unmatch -- "$path"
	rm -rf -- "$path"
done
git commit -q --no-edit -m "chore: sync $platform with main" -m "Merges $base_commit."
git log --oneline -1
