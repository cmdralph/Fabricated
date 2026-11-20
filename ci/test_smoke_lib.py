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


class ConfigFileTest(unittest.TestCase):
    """config_file, which smoke.sh's config: token names a file by: ci/config's files only, named from the repository's root."""

    def setUp(self):
        # A repository of its own: smoke-lib.sh finds its root from where it is, and the links below stay out of the real one.
        self.dir = Path(tempfile.mkdtemp())
        self.repo = self.dir / "repo"
        config = self.repo / "ci" / "config"
        (self.repo / "ci" / "neoforge").mkdir(parents=True)
        (self.repo / "ci" / "smoke-lib.sh").write_text((HERE / "smoke-lib.sh").read_text())
        (self.repo / "ci" / "neoforge" / "platform.sh").write_text("")
        (self.repo / "ci" / "runs.tsv").write_text("run\n")
        (self.repo / ".git").mkdir()
        (self.repo / ".git" / "config").write_text("[core]\n")
        (config / "sub").mkdir(parents=True)
        (config / "a.json").write_text("{}\n")
        (config / "sub" / "b.json").write_text("{}\n")
        (self.dir / "outside.json").write_text("{}\n")
        (config / "out.json").symlink_to(self.dir / "outside.json")
        (config / "runs.json").symlink_to(self.repo / "ci" / "runs.tsv")
        (config / "in.json").symlink_to(config / "a.json")
        self.cwd = self.dir / "elsewhere"
        self.cwd.mkdir()

    def config_file(self, path):
        script = f"platform=neoforge mc=1.21.1 loader=21.1.265 work=. side=\nsource {self.repo / 'ci' / 'smoke-lib.sh'}\nconfig_file \"$1\""
        result = subprocess.run(["bash", "-uc", script, "bash", path], capture_output=True, text=True, cwd=self.cwd)
        return result.returncode, result.stdout.strip(), result.stderr

    def test_a_file_of_ci_config_named_from_the_root(self):
        real = str((self.repo / "ci" / "config" / "a.json").resolve())
        self.assertEqual(self.config_file("ci/config/a.json")[:2], (0, real), "wherever the harness runs")
        self.assertEqual(self.config_file("ci/config/../config/a.json")[:2], (0, real))
        self.assertEqual(self.config_file("ci/config/in.json")[:2], (0, real), "a link within ci/config")
        self.assertEqual(self.config_file("ci/config/sub/b.json")[:2], (0, str((self.repo / "ci" / "config" / "sub" / "b.json").resolve())))

    def test_nothing_else(self):
        for path, why in [(str(self.dir / "outside.json"), "not a path relative"), ("/etc/hostname", "not a path relative"), ("", "not a path relative"),
                          (".git/config", "not in ci/config"), ("ci/runs.tsv", "not in ci/config"), ("ci/config/../runs.tsv", "not in ci/config"),
                          ("ci/config/runs.json", "not in ci/config"), ("ci/config/out.json", "not in ci/config"),
                          ("../outside.json", "not in ci/config"), ("ci/config", "not in ci/config"), ("ci/config/sub", "not a file"),
                          ("ci/config/none.json", "no such file")]:
            status, out, err = self.config_file(path)
            self.assertNotEqual(status, 0, path)
            self.assertEqual(out, "", path)
            self.assertIn(why, err, path)

if __name__ == "__main__":
    unittest.main()
