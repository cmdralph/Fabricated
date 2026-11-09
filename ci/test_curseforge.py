"""Tests for ci/curseforge.py: python3 -m unittest ci/test_curseforge.py"""
import contextlib
import http.client
import io
import json
import sys
import tempfile
import unittest
import urllib.error
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import curseforge  # noqa: E402
from curseforge import Failure  # noqa: E402

TOKEN = "0f9e8d7c-aaaa-bbbb-cccc-0123456789ab"

TYPES = [
    {"id": 1, "name": "Bukkit", "slug": "bukkit"},
    {"id": 75124, "name": "Minecraft 1.20", "slug": "minecraft-1-20"},
    {"id": 77784, "name": "Minecraft 1.21", "slug": "minecraft-1-21"},
    {"id": 80001, "name": "Minecraft 26.1", "slug": "minecraft-26-1"},
    {"id": 80003, "name": "Minecraft 26.3", "slug": "minecraft-26-3"},
    {"id": 80009, "name": "Minecraft 26", "slug": "minecraft-26"},
    {"id": 68441, "name": "Modloader", "slug": "modloader"},
    {"id": 2, "name": "Java", "slug": "java"},
    {"id": 75208, "name": "Environment", "slug": "environment"},
]
VERSIONS = [
    {"id": 9990, "gameVersionTypeID": 75124, "name": "1.20.1", "slug": "1-20-1"},
    {"id": 11779, "gameVersionTypeID": 77784, "name": "1.21.1", "slug": "1-21-1"},
    {"id": 11780, "gameVersionTypeID": 77784, "name": "1.21-Snapshot", "slug": "1-21-snapshot"},
    {"id": 50, "gameVersionTypeID": 1, "name": "1.21.1", "slug": "1-21-1"},
    {"id": 13001, "gameVersionTypeID": 80001, "name": "26.1.2", "slug": "26-1-2"},
    {"id": 13003, "gameVersionTypeID": 80003, "name": "26.3", "slug": "26-3"},
    {"id": 13102, "gameVersionTypeID": 80001, "name": "26.2", "slug": "26-2"},
    {"id": 13202, "gameVersionTypeID": 80009, "name": "26.2", "slug": "26-2"},
    {"id": 7498, "gameVersionTypeID": 68441, "name": "Forge", "slug": "forge"},
    {"id": 10150, "gameVersionTypeID": 68441, "name": "NeoForge", "slug": "neoforge"},
    {"id": 8326, "gameVersionTypeID": 2, "name": "Java 17", "slug": "java-17"},
    {"id": 11135, "gameVersionTypeID": 2, "name": "Java 21", "slug": "java-21"},
    {"id": 13500, "gameVersionTypeID": 2, "name": "Java 25", "slug": "java-25"},
    {"id": 9638, "gameVersionTypeID": 75208, "name": "Client", "slug": "client"},
    {"id": 9639, "gameVersionTypeID": 75208, "name": "Server", "slug": "server"},
]
CHANGELOG = """# Fabricated: changelog

## 0.6.0 (2026-12-01)

The release.

## 0.6.0-beta.2 (2026-11-20)

- Forge 52 for Minecraft 1.21.1.
- CurseForge deployment.

### Fixes

- One.

## v0.6.0-beta (2026-11-09)

- The beta.

## 0.5.1-alpha (2026-10-01)
"""


def answer(body):
    return io.BytesIO(json.dumps(body).encode())


def http_error(code, body=b""):
    return urllib.error.HTTPError("https://minecraft.curseforge.com/x", code, "error", {}, io.BytesIO(body))


def game_data(request, timeout):
    return answer(TYPES if request.full_url.endswith("/version-types") else VERSIONS)


class Pure(unittest.TestCase):
    def test_release_type(self):
        self.assertEqual(curseforge.release_type("0.6.0"), "release")
        self.assertEqual(curseforge.release_type("0.4.0-alpha"), "alpha")
        self.assertEqual(curseforge.release_type("0.6.0-beta.2"), "beta")
        self.assertEqual(curseforge.release_type("1.0.0-rc.1"), "beta")
        self.assertEqual(curseforge.release_type("1.0.0-pre3"), "beta")
        self.assertEqual(curseforge.release_type("1.0.0-Beta"), "beta")
        with self.assertRaises(Failure):
            curseforge.release_type("1.0.0-snapshot")

    def test_jar_names(self):
        self.assertEqual(curseforge.parse_jar("fabricated-neoforge-0.6.0-beta.2+mc26.3.jar"),
                         ("neoforge", "0.6.0-beta.2", "26.3"))
        self.assertEqual(curseforge.parse_jar("fabricated-forge-0.6.0-beta.2+mc1.20.1.jar"),
                         ("forge", "0.6.0-beta.2", "1.20.1"))
        self.assertEqual(curseforge.parse_jar("fabricated-forge-1.0.0+mc1.21.11.jar"), ("forge", "1.0.0", "1.21.11"))
        for name in ("fabricated-0.6.0+mc26.3.jar", "fabricated-forge-0.6.0+mc26.3.jar.sha1",
                     "fabricated-fabric-0.6.0+mc26.3.jar", "fabricated-neoforge-0.6+mc26.3.jar",
                     "fabricated-neoforge-0.6.0+26.3.jar", 'fabricated-neoforge-0.6.0-"x+mc26.3.jar'):
            with self.subTest(name), self.assertRaises(Failure):
                curseforge.parse_jar(name)

    def test_java(self):
        for minecraft, java in (("1.20.1", 17), ("1.20.6", 21), ("1.21.1", 21), ("1.21.11", 21), ("26.1.2", 25),
                                ("26.3", 25)):
            with self.subTest(minecraft):
                self.assertEqual(curseforge.java_version(minecraft), java)
        for minecraft in ("1.16.5", "27.1"):
            with self.subTest(minecraft), self.assertRaises(Failure):
                curseforge.java_version(minecraft)

    def test_changelog(self):
        self.assertEqual(curseforge.changelog(CHANGELOG, "0.6.0-beta.2"),
                         "- Forge 52 for Minecraft 1.21.1.\n- CurseForge deployment.\n\n### Fixes\n\n- One.")
        self.assertEqual(curseforge.changelog(CHANGELOG, "0.6.0"), "The release.")
        self.assertEqual(curseforge.changelog(CHANGELOG, "0.6.0-beta"), "- The beta.")
        self.assertIsNone(curseforge.changelog(CHANGELOG, "0.5.1-alpha"), "an empty section is none")
        self.assertIsNone(curseforge.changelog(CHANGELOG, "0.5.1"))
        self.assertIsNone(curseforge.changelog(CHANGELOG, "6.0"))
        self.assertIsNone(curseforge.changelog("## 10.6.0\n\nx\n", "0.6.0"))
        self.assertIsNone(curseforge.changelog("Text naming 0.6.0 outside a heading.\n", "0.6.0"))

    def test_resolve(self):
        def ids(loader, minecraft):
            return [curseforge.resolve(prefix, name, TYPES, VERSIONS)
                    for _, prefix, name, _ in curseforge.wanted(loader, minecraft)]

        self.assertEqual(ids("neoforge", "1.21.1"),
                         [(11779, None), (10150, None), (11135, None), (9638, None), (9639, None)])
        self.assertEqual([i for i, _ in ids("forge", "1.20.1")], [9990, 7498, 8326, 9638, 9639])
        self.assertEqual([i for i, _ in ids("neoforge", "26.3")], [13003, 10150, 13500, 9638, 9639])
        self.assertEqual(ids("forge", "26.1.2")[0], (13001, None))
        game_id, why = ids("forge", "26.2")[0]
        self.assertIsNone(game_id)
        self.assertIn("ambiguous", why)
        self.assertIn("13102", why)
        game_id, why = curseforge.resolve("minecraft-", "1.21.12", TYPES, VERSIONS)
        self.assertIsNone(game_id)
        self.assertIn("minecraft-1-21", why)
        self.assertIn("no type", curseforge.resolve("modloader", "Forge", TYPES[:6], VERSIONS)[1])
        self.assertIsNone(curseforge.resolve("environment", "Client", TYPES[:-1], VERSIONS)[0])
        with self.assertRaises(Failure):
            curseforge.resolve("java", "Java 21", [{"id": 2}], VERSIONS)


class Uploads(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.dir = Path(directory.name)
        self.changelog = self.dir / "CHANGELOG.md"
        self.changelog.write_text(CHANGELOG, encoding="utf-8")
        self.jars = []
        for name in ("fabricated-neoforge-0.6.0-beta.2+mc26.3.jar", "fabricated-forge-0.6.0-beta.2+mc1.20.1.jar"):
            (self.dir / name).write_bytes(b"PK\x03\x04 " + name.encode())
            self.jars.append(self.dir / name)
        sleep = mock.patch.object(curseforge.time, "sleep")
        self.sleeps = sleep.start()
        self.addCleanup(sleep.stop)

    def main(self, args, env, urlopen=None):
        """(exit code, stdout and stderr) of the script with that environment and urlopen."""
        out = io.StringIO()
        fail = mock.Mock(side_effect=AssertionError("no request expected"))
        with mock.patch.dict(curseforge.os.environ, env, clear=True), \
                mock.patch.object(curseforge.urllib.request, "urlopen", urlopen or fail), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
            code = curseforge.main([*args, str(self.changelog), *map(str, self.jars)])
        return code, out.getvalue()

    def test_dry_run_without_token(self):
        code, out = self.main(["--dry-run"], {})
        self.assertEqual(code, 0, out)
        self.assertIn("Fabricated 0.6.0-beta.2 for NeoForge 26.3", out)
        self.assertIn("Fabricated 0.6.0-beta.2 for Forge 1.20.1", out)
        self.assertIn("release type  beta", out)
        self.assertIn("Java 25", out)
        self.assertIn("Java 17", out)
        self.assertIn("| - Forge 52 for Minecraft 1.21.1.", out)
        self.assertIn("aren't looked up", out)
        self.assertIn("nothing was uploaded", out)

    def test_dry_run_with_token_resolves_read_only(self):
        urlopen = mock.Mock(side_effect=game_data)
        code, out = self.main(["--dry-run"], {"CURSEFORGE_TOKEN": TOKEN, "CURSEFORGE_PROJECT_ID": "123456"}, urlopen)
        self.assertEqual(code, 0, out)
        self.assertIn("26.3 (id 13003)", out)
        self.assertIn("NeoForge (id 10150)", out)
        self.assertEqual([c.args[0].get_method() for c in urlopen.call_args_list], ["GET", "GET"])
        self.assertEqual(urlopen.call_args.args[0].unredirected_hdrs, {"X-api-token": TOKEN})
        self.assertNotIn(TOKEN, out)
        self.assertNotIn("123456", out)

    def test_dry_run_warns_and_publish_fails_on_what_is_missing(self):
        self.changelog.write_text("## 0.5.1-alpha\n\n- Old.\n", encoding="utf-8")
        types = [t for t in TYPES if t["slug"] != "minecraft-26-3"]
        urlopen = mock.Mock(side_effect=lambda r, timeout: answer(
            types if r.full_url.endswith("/version-types") else VERSIONS))
        env = {"CURSEFORGE_TOKEN": TOKEN, "CURSEFORGE_PROJECT_ID": "123456"}
        code, out = self.main(["--dry-run"], env, urlopen)
        self.assertEqual(code, 0, out)
        self.assertIn("warning: ", out)
        self.assertIn("no '## ' section", out)
        self.assertIn("no game version 26.3", out)
        code, out = self.main([], env, urlopen)
        self.assertEqual(code, 1, out)
        self.assertIn("Nothing was uploaded", out)
        self.assertNotIn("POST", [c.args[0].get_method() for c in urlopen.call_args_list])

    def test_publish(self):
        posts = []

        def urlopen(request, timeout):
            if request.get_method() == "GET":
                return game_data(request, timeout)
            posts.append(request)
            return answer({"id": 7000 + len(posts)})

        code, out = self.main([], {"CURSEFORGE_TOKEN": TOKEN, "CURSEFORGE_PROJECT_ID": "123456"}, urlopen)
        self.assertEqual(code, 0, out)
        self.assertEqual(len(posts), 2)
        self.assertEqual(posts[0].full_url, "https://minecraft.curseforge.com/api/projects/123456/upload-file")
        self.assertIn("multipart/form-data; boundary=", posts[0].get_header("Content-type"))
        body = posts[0].data.decode("latin-1")
        metadata = json.loads(body.split('name="metadata"\r\n\r\n', 1)[1].split("\r\n", 1)[0])
        self.assertEqual(metadata, {
            "changelog": "- Forge 52 for Minecraft 1.21.1.\n- CurseForge deployment.\n\n### Fixes\n\n- One.",
            "changelogType": "markdown", "displayName": "Fabricated 0.6.0-beta.2 for NeoForge 26.3",
            "gameVersions": [13003, 10150, 13500, 9638, 9639], "releaseType": "beta"})
        self.assertIn('filename="fabricated-neoforge-0.6.0-beta.2+mc26.3.jar"', body)
        self.assertIn("PK\x03\x04 fabricated-neoforge", body)
        self.assertIn("CurseForge file 7001", out)
        self.assertIn("CurseForge file 7002", out)
        self.assertNotIn(TOKEN, out)

    def test_bad_input(self):
        env = {"CURSEFORGE_TOKEN": TOKEN, "CURSEFORGE_PROJECT_ID": "123456"}
        self.assertEqual(self.main([], {"CURSEFORGE_PROJECT_ID": "123456"})[0], 1)
        code, out = self.main(["--dry-run"], {**env, "CURSEFORGE_PROJECT_ID": "12a4"})
        self.assertEqual(code, 1)
        self.assertIn("not a number", out)
        self.assertNotIn("12a4", out)
        (self.dir / "fabricated-forge-0.6.0+mc1.21.11.jar").write_bytes(b"PK")
        self.jars.append(self.dir / "fabricated-forge-0.6.0+mc1.21.11.jar")
        code, out = self.main(["--dry-run"], {})
        self.assertEqual(code, 1)
        self.assertIn("more than one version", out)

    def test_retries(self):
        client = curseforge.CurseForge(TOKEN)
        urlopen = mock.Mock(side_effect=[http_error(502), ConnectionResetError(), answer([1])])
        with mock.patch.object(curseforge.urllib.request, "urlopen", urlopen), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(client.get("/api/game/versions"), [1])
        self.assertEqual(urlopen.call_count, 3)
        urlopen = mock.Mock(side_effect=[http_error(503)] * 4)
        with mock.patch.object(curseforge.urllib.request, "urlopen", urlopen), \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(Failure) as failure:
            client.get("/api/game/versions")
        self.assertEqual(urlopen.call_count, 4)
        self.assertIn("HTTP 503", str(failure.exception))
        for error in (http_error(400, b'{"errorCode":1009,"errorMessage":"Invalid game version ID"}'),
                      http_error(429), http.client.IncompleteRead(b"{"),
                      urllib.error.URLError("certificate verify failed")):
            urlopen = mock.Mock(side_effect=[error])
            with self.subTest(error), mock.patch.object(curseforge.urllib.request, "urlopen", urlopen), \
                    self.assertRaises(Failure) as failure:
                client.get("/api/game/versions")
            self.assertEqual(urlopen.call_count, 1)
        self.assertIn("certificate verify failed", str(failure.exception))

    def test_error_names_status_and_body(self):
        client = curseforge.CurseForge(TOKEN)
        error = http_error(400, b'{"errorCode":1009,"errorMessage":"Invalid game version ID"}')
        with mock.patch.object(curseforge.urllib.request, "urlopen", mock.Mock(side_effect=[error])), \
                self.assertRaises(Failure) as failure:
            client.upload("123456", self.jars[0], {})
        self.assertIn("HTTP 400", str(failure.exception))
        self.assertIn("Invalid game version ID", str(failure.exception))
        self.assertNotIn("123456", str(failure.exception))

    def test_token_never_printed(self):
        env = {"CURSEFORGE_TOKEN": TOKEN, "CURSEFORGE_PROJECT_ID": "123456"}
        echoing = [
            http_error(401, f'{{"errorMessage":"Invalid token {TOKEN}"}}'.encode()),
            http_error(500, f"token={TOKEN}".encode()),
            urllib.error.URLError(ConnectionResetError(f"reset {TOKEN}")),
            urllib.error.URLError(f"no route for {TOKEN}"),
        ]
        for error in echoing:
            with self.subTest(error):
                code, out = self.main(["--dry-run"], env, mock.Mock(side_effect=error))
                self.assertEqual(code, 1)
                self.assertNotIn(TOKEN, out)
                self.assertIn("***", out)
        with self.assertRaises(Failure) as failure:
            curseforge.CurseForge(TOKEN + "\r\nX-Injected: 1")
        self.assertNotIn(TOKEN, str(failure.exception))

        def upload_fails(request, timeout):
            if request.get_method() == "GET":
                return game_data(request, timeout)
            raise http_error(403, TOKEN.encode())

        code, out = self.main([], env, upload_fails)
        self.assertEqual(code, 1)
        self.assertIn("not uploaded: fabricated-neoforge", out)
        self.assertNotIn(TOKEN, out)
        code, out = self.main(["--dry-run"], {**env, "CURSEFORGE_TOKEN": f"{TOKEN}\n{TOKEN}"})
        self.assertEqual(code, 1)
        self.assertNotIn(TOKEN, out)


if __name__ == "__main__":
    unittest.main()
