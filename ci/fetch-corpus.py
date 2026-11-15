#!/usr/bin/env python3
"""Downloads mods for one Minecraft version from Modrinth into a directory (CI only): ci/mod-corpus.txt's Fabric
mods, or the given projects' builds for the given loader.

Each project's newest build for the version is fetched (passing over a later version's build that lists this one
too), or the build named as <project>@<version-id>, then its required dependencies, recursively (the version a
dependency names when it is a build for the version, else the newest). Files come only from Modrinth's CDN and must
match the SHA-512 Modrinth lists. Fabric API is left out (CI adds the official jar). Prints one line per mod, with
the version id for pinning; a mod without a build for the version is reported and skipped.

Usage: ci/fetch-corpus.py <minecraft-version> <out-dir> [<loader> <project>[@<version-id>]...]
"""
import hashlib
import json
import os
import re
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
CDN = "https://cdn.modrinth.com/"
FABRIC_API = "P7dR8mSH"
HEADERS = {"User-Agent": "cmdralph/Fabricated CI (github.com/cmdralph/Fabricated)"}
MAX_BYTES = 256 << 20
IMMUTABLE = re.compile(r"^(https://api\.modrinth\.com/v2/version/[A-Za-z0-9]+|https://cdn\.modrinth\.com/.+)$")
ATTEMPTS = 7


def get(url):
    """The response's body, from $MODRINTH_CACHE for what never changes at its URL (a version by its id, a file on the
    CDN; a file's SHA-512 is checked as it is used). A timeout, a dropped connection or a server error (429, 5xx) is
    tried again, waiting 2 s and doubling up to about two minutes in all: a host's gateway errors can last that long."""
    cache = os.environ.get("MODRINTH_CACHE")
    if cache and IMMUTABLE.match(url):
        path = os.path.join(cache, hashlib.sha256(url.encode()).hexdigest())
        if os.path.isfile(path):
            with open(path, "rb") as f:
                return f.read()
        data = fetch(url)
        os.makedirs(cache, exist_ok=True)
        with open(path + ".part", "wb") as f:
            f.write(data)
        os.replace(path + ".part", path)
        return data
    return fetch(url)


def forget(url):
    """Drops the cached copy of a file that failed its checks, so the next run downloads it again."""
    cache = os.environ.get("MODRINTH_CACHE")
    if cache and IMMUTABLE.match(url):
        path = os.path.join(cache, hashlib.sha256(url.encode()).hexdigest())
        if os.path.isfile(path):
            os.remove(path)


def fetch(url):
    for attempt in range(ATTEMPTS):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as response:
                return response.read(MAX_BYTES + 1)
        except urllib.error.HTTPError as e:
            if attempt + 1 == ATTEMPTS or e.code != 429 and e.code < 500:
                raise
        except (urllib.error.URLError, TimeoutError, socket.timeout, ConnectionError):
            if attempt + 1 == ATTEMPTS:
                raise
        time.sleep(2 << attempt)


RELEASE = re.compile(r"(?<![\d.])\d+(?:\.\d+)+(?![\d.])")


def release(name):
    """A release's version as numbers (26.1.2 as (26, 1, 2)), or None for a snapshot's name."""
    return tuple(int(part) for part in name.split(".")) if re.fullmatch(r"\d+(?:\.\d+)+", name) else None


def made_for_later(version, mc):
    """Whether the build names, in its version number or file name, only Minecraft versions it lists that are later
    than mc: a later version's build its project also lists for mc. Sodium's and Iris's 26.3 builds list 26.1.2 and
    26.2, and Sodium's refuses those versions' LWJGL 3.4.1 at launch (PR run 41665998918)."""
    file = next((f for f in version["files"] if f.get("primary")), version["files"][0] if version["files"] else {})
    text = version.get("version_number", "") + " " + file.get("filename", "").removesuffix(".jar")
    listed = set(version.get("game_versions", []))
    named = {release(token) for token in RELEASE.findall(text) if token in listed} - {None}
    return bool(named) and min(named) > release(mc)


def newest(project, mc, loader):
    """The project's newest build for mc, passing over the builds made for a later version that list mc too."""
    query = urllib.parse.urlencode({"loaders": json.dumps([loader]), "game_versions": json.dumps([mc])})
    versions = json.loads(get(f"{API}/project/{urllib.parse.quote(project)}/version?{query}"))
    if not versions:
        return None
    if release(mc) is None:
        return versions[0]
    version = next((v for v in versions if not made_for_later(v, mc)), versions[0])
    if version is not versions[0]:
        print(f"note: {project}'s {versions[0]['version_number']} is a later Minecraft version's build that lists {mc}; "
              f"fetching {version['version_number']}", file=sys.stderr)
    return version


def pinned(project, mc, loader):
    version_id = project.split("@", 1)[1]
    version = json.loads(get(f"{API}/version/{urllib.parse.quote(version_id)}"))
    if mc not in version.get("game_versions", []) or loader not in version.get("loaders", []):
        raise ValueError(f"version {version_id} is not a {loader} build for {mc}")
    return version


def download(version, out):
    file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
    if not file["url"].startswith(CDN):
        raise ValueError(f"unexpected download host: {file['url']}")
    name = os.path.basename(file["filename"])
    if not name.endswith(".jar"):
        raise ValueError(f"not a jar: {name}")
    data = get(file["url"])
    if len(data) > MAX_BYTES:
        forget(file["url"])
        raise ValueError(f"{name} is too large")
    if hashlib.sha512(data).hexdigest() != file["hashes"]["sha512"]:
        forget(file["url"])
        raise ValueError(f"{name} does not match Modrinth's SHA-512")
    with open(os.path.join(out, name), "wb") as f:
        f.write(data)
    return name


def main():
    mc, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    if len(sys.argv) > 4:
        loader, queue = sys.argv[3], sys.argv[4:]
    else:
        loader = "fabric"
        root = os.path.dirname(os.path.abspath(__file__))
        with open(os.path.join(root, "mod-corpus.txt")) as f:
            queue = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    seen = {FABRIC_API}
    while queue:
        project = queue.pop(0)
        try:
            version = pinned(project, mc, loader) if "@" in project else newest(project.split("~")[0], mc, loader)
            if "~" in project:  # a dependency's named version, when it is a build for this version and loader
                try:
                    version = pinned(project.replace("~", "@"), mc, loader)
                except Exception:
                    pass
            if version is None:
                print(f"{project.split('@')[0].split('~')[0]}: no {loader} build for {mc}")
                continue
            if version["project_id"] in seen:
                continue
            seen.add(version["project_id"])
            name = download(version, out)
            print(f"{project.split('@')[0].split('~')[0]}: {version['version_number']} [{version['id']}] ({name})")
            queue += [d["project_id"] + (f"~{d['version_id']}" if d.get("version_id") else "") for d in version.get("dependencies", [])
                      if d.get("dependency_type") == "required" and d.get("project_id") and d["project_id"] not in seen]
        except Exception as e:  # one mod's problem doesn't stop the others
            print(f"{project.split('@')[0].split('~')[0]}: not fetched: {e}")


if __name__ == "__main__":
    main()
