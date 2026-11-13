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


def source_lib(script, cache, path_dir):
    """Runs a script after sourcing smoke-lib.sh as smoke.sh does (no errexit); its status and output."""
    env = dict(os.environ, SMOKE_CACHE=str(cache), PATH=f"{path_dir}{os.pathsep}{os.environ['PATH']}")
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


if __name__ == "__main__":
    unittest.main()
