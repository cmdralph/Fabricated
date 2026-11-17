"""Tests of ci/retry-downloads.sh: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent

# A build that counts its runs in a file beside it, prints what a failing one printed and fails until run $PASS_AT.
BUILD = """#!/usr/bin/env bash
count="$(dirname "$0")/runs"
runs=$(( $(cat "$count" 2>/dev/null || echo 0) + 1 ))
echo "$runs" > "$count"
if [ "$runs" -lt "${PASS_AT:-99}" ]; then
	echo "$FAILURE"
	exit "${STATUS:-1}"
fi
echo "BUILD SUCCESSFUL"
"""

# ForgeGradle's Mavenizer as Forge's maven reset its connection, and Gradle as Maven Central refused a request.
MAVENIZER = "Caused by: java.net.SocketException: Connection reset"
CENTRAL = ("Could not GET 'https://repo.maven.apache.org/maven2/org/junit/junit-bom/5.13.4/junit-bom-5.13.4.pom'. "
           "Received status code 429 from server: Too Many Requests")
COMPILER = "MixinPreflight.java:12: error: cannot find symbol"


class RetryDownloadsTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.build = self.dir / "build"
        self.build.write_text(BUILD)
        self.build.chmod(0o755)

    def run_build(self, failure, **env):
        result = subprocess.run([str(HERE / "retry-downloads.sh"), str(self.build)], capture_output=True, text=True,
                                env=dict(os.environ, FAILURE=failure, RETRY_WAIT="0", **env))
        return result.returncode, result.stdout, int((self.dir / "runs").read_text())

    def test_a_build_that_passes_runs_once(self):
        status, output, runs = self.run_build(MAVENIZER, PASS_AT="1")
        self.assertEqual((status, runs), (0, 1))
        self.assertIn("BUILD SUCCESSFUL", output)

    def test_a_reset_connection_is_tried_again(self):
        status, output, runs = self.run_build(MAVENIZER, PASS_AT="2")
        self.assertEqual((status, runs), (0, 2))
        self.assertIn("A download failed (Connection reset): try 2 of 3", output)

    def test_a_refused_request_is_tried_again(self):
        status, _, runs = self.run_build(CENTRAL, PASS_AT="3")
        self.assertEqual((status, runs), (0, 3))

    def test_a_download_that_keeps_failing_fails_after_three_tries(self):
        status, _, runs = self.run_build(MAVENIZER, STATUS="7")
        self.assertEqual((status, runs), (7, 3))

    def test_a_compile_error_is_not_tried_again(self):
        status, output, runs = self.run_build(COMPILER, STATUS="1")
        self.assertEqual((status, runs), (1, 1))
        self.assertNotIn("try 2", output)


if __name__ == "__main__":
    unittest.main()
