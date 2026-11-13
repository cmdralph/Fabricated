# Sourced by ci/corpus-summary.sh and ci/corpus-causes.sh: what both read of a game run.
# shellcheck shell=bash disable=SC2154 # report is the sourcing script's

# section <title>: a section of Fabricated's report ($report), the lines after its "== <title>" header up to the next
# blank line.
section() { awk -v head="== $1" 'index($0, head) == 1 { show = 1; next } /^$/ { show = 0 } show' "$report"; }

# started <log>: whether the game started: a server's "Done", or mc-runtime-test's line as a client joins its world,
# "Successfully finished." (which ci/smoke-client.sh waits for) where it runs no game tests, "Running game tests..."
# first where it does (Forge 47's clients).
started() { grep -q -E 'Done \([0-9.]+s\)!|Successfully finished\.|Running game tests\.\.\.' "$1"; }
