#!/usr/bin/env bash
# Merges main into a Forge platform branch, keeping the branch's own platform and leaving main's out. main holds the
# shared engine and NeoForge; forge holds the shared engine and Forge 61 to 66, forge-legacy the shared engine and
# Forge 47 (both with a copy of the Forge platforms' shared folders). Shared changes go to main and reach the platform
# branches through this merge; a platform branch is never merged into main.
#
# Run it in a checkout of a working branch made from the platform branch, with main fetched. It makes a merge commit
# (no rebase, no force): the branch's own files are kept as they are, the files only main has are left out, and a
# conflict in a shared file stops it for a person to resolve. Then push the working branch and open a pull request
# into the platform branch, whose full matrix checks the merge.
#
# Usage: scripts/sync-platform-branch.sh <forge|forge-legacy> [<main ref, default origin/main>]
set -euo pipefail

platform="${1:-}"
base="${2:-origin/main}"
case "$platform" in
	forge)
		own=(loader/forge loader/forge-shared mc/forge mc/forge-shared mc/platform-forge.gradle mc/forge-mixinextras.gradle
			ci/forge .github/workflows/build-forge.yml README.md docs/FORGE.md)
		;;
	forge-legacy)
		own=(loader/forge-legacy loader/forge-shared mc/forge-legacy mc/forge-shared mc/platform-forge-legacy.gradle
			mc/forge-mixinextras.gradle ci/forge-legacy .github/workflows/build-forge-legacy.yml README.md docs/FORGE-LEGACY.md)
		;;
	*)
		echo "Usage: $0 <forge|forge-legacy> [<main ref>]" >&2
		exit 2
		;;
esac
# What only main has: NeoForge's platform and the workflows that build and release from main.
mains=(loader/neoforge mc/neoforge mc/platform-neoforge.gradle ci/neoforge .github/workflows/build.yml .github/workflows/release.yml)

if [ -n "$(git status --porcelain)" ]; then
	echo "The working tree has changes; commit or stash them first." >&2
	exit 1
fi

git merge --no-ff --no-commit "$base" || true
# The branch's own files stay as they are, whatever main did with them (main left them out at the split).
for path in "${own[@]}"; do
	if git cat-file -e "HEAD:$path" 2>/dev/null; then
		git rm -r -q --cached --ignore-unmatch -- "$path"
		git checkout HEAD -- "$path"
	fi
done
for path in "${mains[@]}"; do
	git rm -r -q --cached --ignore-unmatch -- "$path"
	rm -rf -- "$path"
done
if [ -n "$(git diff --name-only --diff-filter=U)" ]; then
	echo "Conflicts in shared files, to resolve by hand, then commit:" >&2
	git diff --name-only --diff-filter=U >&2
	exit 1
fi
git commit -q --no-edit -m "chore: sync $platform with main" -m "Merges $(git rev-parse "$base")."
git log --oneline -1
