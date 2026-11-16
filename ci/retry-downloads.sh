#!/usr/bin/env bash
# Runs a build, and tries it again where it failed on a download.
#
# A Gradle build fetches what it needs itself, past the harness's own retries (ci/smoke-lib.sh's retrying_curl):
# Gradle its dependencies, and ForgeGradle's Mavenizer Minecraft's and Forge's files as it configures a project, each
# asked for once. A build whose output shows such a download failing (a connection reset, refused or timed out, a
# server's 429 or 5xx) is run up to twice more, each run keeping what the last one fetched. A build that failed without
# one, a compile error or a failed test, fails at once with its own status: running it again would only hide it.
#
# Usage: ci/retry-downloads.sh <command> [<argument>...]
# RETRY_WAIT: the seconds to wait before a new try (by default 30, then 60).
set -uo pipefail

FAILED_DOWNLOAD='Connection reset|Connection refused|Read timed out|[Rr]equest timed out|HttpTimeoutException|Could not (GET|HEAD) |status code:? (429|5[0-9][0-9])|UnknownHostException|SSLHandshakeException|Remote host terminated the handshake'
log=$(mktemp)
trap 'rm -f "$log" "$log.status"' EXIT
for attempt in 1 2 3; do
	# The command's own status, whatever the pipe's.
	{ "$@" 2>&1; echo "$?" > "$log.status"; } | tee "$log"
	status=$(cat "$log.status")
	[ "$status" != 0 ] || exit 0
	[ "$attempt" != 3 ] && grep -Eq "$FAILED_DOWNLOAD" "$log" || exit "$status"
	echo "A download failed ($(grep -Eom 1 "$FAILED_DOWNLOAD" "$log")): try $((attempt + 1)) of 3"
	sleep "${RETRY_WAIT:-$((attempt * 30))}"
done
