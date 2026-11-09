#!/usr/bin/env python3
"""Uploads a release's jars to Fabricated's CurseForge project (the release workflow): NeoForge's and Forge's to the
one project, each tagged with its Minecraft version, its loader, the Java version that Minecraft version runs on, and
the client and server environments.

A jar's name gives its loader, version and Minecraft version (fabricated-<loader>-<version>+mc<minecraft>.jar), and
the version gives the release type: a pre-release suffix naming alpha is an alpha, one naming beta, rc or pre a beta,
none a release. The changelog is the section of the changelog file whose "## " heading names the version. The project
is CURSEFORGE_PROJECT_ID and the token CURSEFORGE_TOKEN, both from the environment, and neither is ever printed.
Every jar's game versions are resolved before the first upload, so a game version CurseForge lacks uploads nothing.

--dry-run uploads nothing and prints what would be uploaded: with a token, the game versions' CurseForge ids, looked
up read-only; without, their names. A changelog section or a game version that is missing is a warning there and an
error otherwise.

Usage: ci/curseforge.py [--dry-run] <changelog.md> <jar>...
"""
import argparse
import http.client
import json
import os
import re
import secrets
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://minecraft.curseforge.com"
USER_AGENT = "cmdralph/Fabricated release (github.com/cmdralph/Fabricated)"
JAR = re.compile(r"fabricated-(neoforge|forge)-(\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?)\+mc(\d+(?:\.\d+)+)\.jar")
LOADERS = {"neoforge": "NeoForge", "forge": "Forge"}
RETRIES = 3
TIMEOUT = 120
CHANGELOG_LINES = 5


class Failure(Exception):
    """A failure the release's log explains by its message alone."""


def parse_jar(name):
    """(loader, version, minecraft) from a release jar's file name."""
    match = JAR.fullmatch(name)
    if not match:
        raise Failure(f"{name} is not named fabricated-<neoforge|forge>-<version>+mc<minecraft>.jar")
    return match.groups()


def release_type(version):
    suffix = version.partition("-")[2].lower()
    if not suffix:
        return "release"
    if "alpha" in suffix:
        return "alpha"
    if any(word in suffix for word in ("beta", "rc", "pre")):
        return "beta"
    raise Failure(f"The pre-release suffix of {version} names none of alpha, beta, rc and pre")


def java_version(minecraft):
    """The Java version a Minecraft version runs on (Mojang moved to Java 21 with 1.20.5)."""
    release = tuple(int(part) for part in minecraft.split("."))
    if release[0] == 26:
        return 25
    if release[0] == 1 and release >= (1, 20, 5):
        return 21
    if release[0] == 1 and release >= (1, 18):
        return 17
    raise Failure(f"No Java version is known for Minecraft {minecraft}")


def changelog(text, version):
    """The section of a changelog whose "## " heading names the version as a whole word (v-prefixed or not), without
    its heading, or None. 0.6.0 doesn't name 0.6.0-beta, nor does 0.6.0-beta name 0.6.0-beta.2."""
    named = re.compile(rf"(?<![\w.+-])v?{re.escape(version)}(?![\w+-]|\.\w)")
    lines = text.splitlines()
    for start, line in enumerate(lines):
        if line.startswith("## ") and named.search(line):
            end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
            return "\n".join(lines[start + 1:end]).strip() or None
    return None


def wanted(loader, minecraft):
    """The game versions a jar is tagged with: (label, prefix of its type's slug, name, required). A Minecraft
    version is looked for in every Minecraft type (minecraft-1-21 holds 1.21.x), as the types 26.x's versions are in
    aren't assumed; resolve() fails on a name two of them hold."""
    return [("Minecraft", "minecraft-", minecraft, True),
            ("loader", "modloader", LOADERS[loader], True),
            ("Java", "java", f"Java {java_version(minecraft)}", True),
            ("environment", "environment", "Client", False),
            ("environment", "environment", "Server", False)]


def resolve(prefix, name, types, versions):
    """(id, None) for the one game version of that name in the types whose slugs start with the prefix, else (None,
    why). Names compare without case; CurseForge's snapshots ("1.21-Snapshot") never equal a release's name."""
    try:
        slugs = {t["id"]: t["slug"] for t in types if t["slug"].startswith(prefix)}
        found = [v for v in versions if v["gameVersionTypeID"] in slugs and v["name"].casefold() == name.casefold()]
    except (KeyError, TypeError, AttributeError):
        raise Failure("CurseForge's game versions aren't lists of {id, name, slug, gameVersionTypeID}") from None
    if len(found) == 1:
        return found[0]["id"], None
    if not found:
        searched = ", ".join(sorted(slugs.values())) or f"none, no type's slug starting with {prefix}"
        return None, f"CurseForge has no game version {name} (types searched: {searched})"
    return None, f"{name} is ambiguous on CurseForge: " + ", ".join(
        f"id {v['id']} in {slugs[v['gameVersionTypeID']]}" for v in found)


class CurseForge:
    """CurseForge's upload API. The token goes in its request header alone, and is taken out of every message."""

    def __init__(self, token):
        # http.client names a header value it refuses in its exception, so a token with a line break would be printed.
        if not re.fullmatch(r"[\x21-\x7e]+", token):
            raise Failure("CURSEFORGE_TOKEN holds characters an HTTP header can't carry")
        self.token = token

    def redact(self, text):
        return text.replace(self.token, "***")

    def request(self, what, path, body=None, content_type=None, retries=RETRIES):
        """The JSON answer. A server error, a dropped connection or a timeout is tried again, after 2, 4 and 8
        seconds; a client error (4xx) is not, as it would fail again."""
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
        if content_type:
            headers["Content-Type"] = content_type
        for attempt in range(retries + 1):
            try:
                request = urllib.request.Request(API + path, data=body, headers=headers)
                # Not sent on along a redirect, which could lead to another host.
                request.add_unredirected_header("X-Api-Token", self.token)
                with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
                    return json.loads(response.read())
            except urllib.error.HTTPError as e:
                if e.code < 500 or attempt == retries:
                    raise Failure(self.redact(f"{what}: HTTP {e.code} from CurseForge: {error_body(e)}")) from None
                problem = f"HTTP {e.code}"
            except (urllib.error.URLError, http.client.HTTPException, ConnectionError, TimeoutError) as e:
                reason = getattr(e, "reason", e)
                problem = str(reason) or type(reason).__name__
                if not isinstance(reason, (ConnectionError, TimeoutError)) or attempt == retries:
                    raise Failure(self.redact(f"{what}: {problem}")) from None
            except ValueError:
                raise Failure(f"{what}: CurseForge's answer isn't JSON") from None
            print(self.redact(f"{what}: {problem}; trying again in {2 << attempt} s"))
            time.sleep(2 << attempt)

    def get(self, path):
        return self.request(f"GET {path}", path)

    def upload(self, project, jar, metadata):
        """The new file's id. The metadata is a JSON form field next to the file, as CurseForge's upload API asks.
        Tried once: CurseForge may have stored a file whose answer was lost, and a second try would upload it twice."""
        boundary = secrets.token_hex(16)
        head = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n\r\n{json.dumps(metadata)}\r\n'
                f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
                "Content-Type: application/java-archive\r\n\r\n")
        body = head.encode() + jar.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
        answer = self.request(f"Upload of {jar.name}", f"/api/projects/{project}/upload-file", body,
                              f"multipart/form-data; boundary={boundary}", retries=0)
        if not isinstance(answer, dict) or "id" not in answer:
            raise Failure(self.redact(f"Upload of {jar.name}: CurseForge's answer names no file id: {answer}"))
        return answer["id"]


def error_body(error):
    try:
        body = error.read(2000).decode("utf-8", "replace").strip()
    except OSError:
        body = ""
    return body or error.reason


def run(changelog_path, jars, dry_run, env):
    parsed = [(jar, *parse_jar(jar.name)) for jar in jars]
    versions = sorted({version for _, _, version, _ in parsed})
    if len(versions) != 1:
        raise Failure(f"The jars carry more than one version: {', '.join(versions)}")
    version = versions[0]
    kind = release_type(version)
    token = env.get("CURSEFORGE_TOKEN", "").strip()
    project = env.get("CURSEFORGE_PROJECT_ID", "").strip()
    if project and not re.fullmatch(r"[0-9]+", project):
        raise Failure("CURSEFORGE_PROJECT_ID is not a number")
    if not dry_run and not (token and project):
        raise Failure("Uploading needs CURSEFORGE_TOKEN and CURSEFORGE_PROJECT_ID")
    client = CurseForge(token) if token else None

    problems = []
    notes = changelog(changelog_path.read_text(encoding="utf-8"), version)
    print(f"{'Dry run: would upload' if dry_run else 'Uploading'} {len(parsed)} jars of Fabricated {version}, "
          f"release type {kind}, to the CurseForge project (CURSEFORGE_PROJECT_ID {'set' if project else 'unset'})")
    if notes is None:
        problems.append(f"{changelog_path} has no '## ' section whose heading names {version}")
        notes = ""
    else:
        lines = notes.splitlines()
        print(f"Changelog: {len(lines)} lines from {changelog_path}, starting")
        for line in lines[:CHANGELOG_LINES]:
            print(f"  | {line}")
    if client:
        types, game_versions = client.get("/api/game/version-types"), client.get("/api/game/versions")
    else:
        print("Game versions by name: CURSEFORGE_TOKEN is unset, so their CurseForge ids aren't looked up")

    uploads = []
    for jar, loader, _, minecraft in parsed:
        display = f"Fabricated {version} for {LOADERS[loader]} {minecraft}"
        print(f"{jar.name}\n  {'display name':12}  {display}\n  {'release type':12}  {kind}")
        ids = []
        for label, prefix, name, required in wanted(loader, minecraft):
            if not client:
                print(f"  {label:12}  {name}")
                continue
            game_id, why = resolve(prefix, name, types, game_versions)
            if game_id is not None:
                ids.append(game_id)
                print(f"  {label:12}  {name} (id {game_id})")
            elif required:
                problems.append(f"{jar.name}: {why}")
                print(f"  {label:12}  {name} (unresolved)")
            else:
                print(f"  {label:12}  {name} (left out: {why})")
        uploads.append((jar, {"changelog": notes, "changelogType": "markdown", "displayName": display,
                              "gameVersions": ids, "releaseType": kind}))

    if problems and not dry_run:
        raise Failure("Nothing was uploaded: " + "; ".join(problems))
    for problem in problems:
        print(f"warning: {problem}")
    if dry_run:
        print("Dry run: nothing was uploaded")
        return
    for index, (jar, metadata) in enumerate(uploads):
        try:
            file_id = client.upload(project, jar, metadata)
        except Failure as e:
            done = ", ".join(j.name for j, _ in uploads[:index]) or "none"
            raise Failure(f"{e}. Uploaded before it: {done}; not uploaded: "
                          + ", ".join(j.name for j, _ in uploads[index:])) from None
        print(f"{jar.name}: uploaded, CurseForge file {file_id}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Uploads a release's jars to Fabricated's CurseForge project.")
    parser.add_argument("--dry-run", action="store_true", help="upload nothing, print what would be uploaded")
    parser.add_argument("changelog", type=Path, help="the changelog, whose section for the version is the files'")
    parser.add_argument("jars", type=Path, nargs="+", help="the release's jars")
    args = parser.parse_args(argv)
    try:
        run(args.changelog, args.jars, args.dry_run, os.environ)
    except (Failure, OSError) as e:
        # After what was printed before it: stdout to a pipe is buffered, stderr not.
        sys.stdout.flush()
        print(f"error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
