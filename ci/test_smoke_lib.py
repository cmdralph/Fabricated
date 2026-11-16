"""Tests of ci/smoke-lib.sh's helpers, sourced as the harness sources them: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent

# A curl that leaves part of the file behind and fails, as one does when the connection drops after its retries.
PARTIAL_CURL = """#!/usr/bin/env bash
while [ "$#" -gt 0 ]; do
	if [ "$1" = -o ]; then printf 'part of a jar' > "$2"; shift; fi
	shift
done
exit 18
"""

# A curl whose connection is reset twice, part of the answer already written, before a whole answer comes: it counts
# its calls in a file beside itself.
RESET_CURL = """#!/usr/bin/env bash
count="$(dirname "$0")/calls"
calls=$(( $(cat "$count" 2>/dev/null || echo 0) + 1 ))
echo "$calls" > "$count"
out=/dev/stdout
while [ "$#" -gt 0 ]; do
	if [ "$1" = -o ]; then out="$2"; shift; fi
	shift
done
if [ "$calls" -le 2 ]; then printf 'part' > "$out"; exit 56; fi
printf 'the whole answer' > "$out"
"""


def source_lib(script, cache, path_dir):
    """Runs a script after sourcing smoke-lib.sh as smoke.sh does (no errexit); its status and output."""
    env = dict(os.environ, SMOKE_CACHE=str(cache), SMOKE_RETRY_WAIT="0", PATH=f"{path_dir}{os.pathsep}{os.environ['PATH']}")
    prelude = (f"platform=neoforge mc=1.21.1 loader=21.1.265 work={cache} side=\n"
               f"source {HERE / 'smoke-lib.sh'}\n")
    result = subprocess.run(["bash", "-uc", prelude + script], capture_output=True, text=True, env=env, cwd=cache)
    return result.returncode, result.stdout + result.stderr


class DownloadTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.bin = self.dir / "bin"
        self.bin.mkdir()
        curl = self.bin / "curl"
        curl.write_text(PARTIAL_CURL)
        curl.chmod(0o755)
        self.cache = self.dir / "cache"
        self.cache.mkdir()

    def test_a_failed_download_fails_and_is_not_cached(self):
        _, output = source_lib('download https://example.invalid/a.jar a.jar; echo "status $?"', self.cache, self.bin)
        self.assertIn("status 18", output)
        cached = self.cache / "downloads"
        self.assertEqual([p.name for p in cached.iterdir()] if cached.is_dir() else [], [])


class RetryTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.bin = self.dir / "bin"
        self.bin.mkdir()
        self.cache = self.dir / "cache"
        self.cache.mkdir()

    def curl(self, script):
        curl = self.bin / "curl"
        curl.write_text(script)
        curl.chmod(0o755)

    def test_a_reset_connection_is_tried_again_into_the_file(self):
        self.curl(RESET_CURL)
        _, output = source_lib('retrying_curl -o a.jar https://example.invalid/a.jar; echo "status $?"',
                               self.cache, self.bin)
        self.assertIn("status 0", output)
        self.assertEqual((self.cache / "a.jar").read_text(), "the whole answer")
        self.assertEqual((self.bin / "calls").read_text().strip(), "3")

    def test_what_reads_the_answer_gets_only_a_whole_one(self):
        self.curl(RESET_CURL)
        _, output = source_lib('answer=$(retrying_curl https://example.invalid/list); echo "status $? [$answer]"',
                               self.cache, self.bin)
        self.assertIn("status 0 [the whole answer]", output)

    def test_a_connection_that_stays_down_fails_after_five_tries(self):
        self.curl(PARTIAL_CURL)
        _, output = source_lib('retrying_curl -o a.jar https://example.invalid/a.jar; echo "status $?"',
                               self.cache, self.bin)
        self.assertIn("status 18", output)

    def test_an_http_error_is_not_tried_again(self):
        self.curl(RESET_CURL.replace("exit 56", "exit 22"))
        _, output = source_lib('retrying_curl -o a.jar https://example.invalid/a.jar; echo "status $?"',
                               self.cache, self.bin)
        self.assertIn("status 22", output)
        self.assertEqual((self.bin / "calls").read_text().strip(), "1")


if __name__ == "__main__":
    unittest.main()
