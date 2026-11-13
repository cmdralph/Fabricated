"""Tests of scripts/sync-platform-branch.sh, in a repository of its own: python3 -B -m unittest discover -s ci -p 'test_*.py'."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "sync-platform-branch.sh"
ENV = dict(os.environ, GIT_CONFIG_GLOBAL=os.devnull, GIT_CONFIG_NOSYSTEM="1", GIT_AUTHOR_NAME="CI", GIT_AUTHOR_EMAIL="ci@invalid",
           GIT_COMMITTER_NAME="CI", GIT_COMMITTER_EMAIL="ci@invalid")


def git(repo, *args):
    return subprocess.run(["git", *args], cwd=repo, env=ENV, capture_output=True, text=True, check=True).stdout


def write(repo, files):
    for name, text in files.items():
        path = repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)


class SyncTest(unittest.TestCase):
    def setUp(self):
        """main with NeoForge and a shared file; forge with its own platform; main's shared file changed since."""
        self.repo = Path(tempfile.mkdtemp())
        git(self.repo, "init", "-q", "-b", "main")
        write(self.repo, {"shared.txt": "1\n", "mc/neoforge/1.21.1/a.txt": "n\n", "ci/neoforge/platform.sh": "n\n"})
        git(self.repo, "add", "-A")
        git(self.repo, "commit", "-q", "-m", "main")
        git(self.repo, "checkout", "-q", "-b", "forge")
        git(self.repo, "rm", "-q", "-r", "mc/neoforge", "ci/neoforge")
        write(self.repo, {"mc/forge/26.3/b.txt": "f\n", "ci/forge/platform.sh": "f\n"})
        git(self.repo, "add", "-A")
        git(self.repo, "commit", "-q", "-m", "forge")
        git(self.repo, "checkout", "-q", "main")
        write(self.repo, {"shared.txt": "2\n"})
        git(self.repo, "commit", "-q", "-am", "shared")
        git(self.repo, "checkout", "-q", "-b", "claude/dev-forge", "forge")

    def test_a_sync_from_a_subdirectory_merges_as_from_the_root(self):
        result = subprocess.run(["bash", str(SCRIPT), "forge", "main"], cwd=self.repo / "mc", env=ENV, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(git(self.repo, "ls-tree", "-r", "--name-only", "HEAD").split(),
                         ["ci/forge/platform.sh", "mc/forge/26.3/b.txt", "shared.txt"])
        self.assertEqual(git(self.repo, "show", "HEAD:shared.txt"), "2\n")
        self.assertEqual(len(git(self.repo, "rev-list", "--parents", "-n", "1", "HEAD").split()), 3)


if __name__ == "__main__":
    unittest.main()
