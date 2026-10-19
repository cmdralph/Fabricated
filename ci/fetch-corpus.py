#!/usr/bin/env python3
"""Downloads ci/mod-corpus.txt's mods for one Minecraft version from Modrinth into a directory (CI only).

Each listed project's newest Fabric build for the version is fetched, then its required dependencies, recursively.
Files come only from Modrinth's CDN and must match the SHA-512 Modrinth lists. Fabric API is left out (CI adds the
official jar). Prints one line per mod; a mod without a build for the version is reported and skipped.

Usage: ci/fetch-corpus.py <minecraft-version> <out-dir>
"""
import hashlib
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
CDN = "https://cdn.modrinth.com/"
FABRIC_API = "P7dR8mSH"
HEADERS = {"User-Agent": "cmdralph/Fabricated CI (github.com/cmdralph/Fabricated)"}
MAX_BYTES = 256 << 20


def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=HEADERS), timeout=60) as response:
        return response.read(MAX_BYTES + 1)


def newest(project, mc):
    query = urllib.parse.urlencode({"loaders": json.dumps(["fabric"]), "game_versions": json.dumps([mc])})
    versions = json.loads(get(f"{API}/project/{urllib.parse.quote(project)}/version?{query}"))
    return versions[0] if versions else None


def download(version, out):
    file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
    if not file["url"].startswith(CDN):
        raise ValueError(f"unexpected download host: {file['url']}")
    name = os.path.basename(file["filename"])
    if not name.endswith(".jar"):
        raise ValueError(f"not a jar: {name}")
    data = get(file["url"])
    if len(data) > MAX_BYTES:
        raise ValueError(f"{name} is too large")
    if hashlib.sha512(data).hexdigest() != file["hashes"]["sha512"]:
        raise ValueError(f"{name} does not match Modrinth's SHA-512")
    with open(os.path.join(out, name), "wb") as f:
        f.write(data)
    return name


def main():
    mc, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    root = os.path.dirname(os.path.abspath(__file__))
    with open(os.path.join(root, "mod-corpus.txt")) as f:
        queue = [line.strip() for line in f if line.strip() and not line.startswith("#")]
    seen = {FABRIC_API}
    while queue:
        project = queue.pop(0)
        try:
            version = newest(project, mc)
            if version is None:
                print(f"{project}: no Fabric build for {mc}")
                continue
            if version["project_id"] in seen:
                continue
            seen.add(version["project_id"])
            name = download(version, out)
            print(f"{project}: {version['version_number']} ({name})")
            queue += [d["project_id"] for d in version.get("dependencies", [])
                      if d.get("dependency_type") == "required" and d.get("project_id") and d["project_id"] not in seen]
        except Exception as e:  # one mod's problem doesn't stop the others
            print(f"{project}: not fetched: {e}")


if __name__ == "__main__":
    main()
