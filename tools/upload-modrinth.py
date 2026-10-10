"""Uploads every jar in dist/ to a Modrinth project as its own version.

Each jar becomes one Modrinth version tagged with its exact Minecraft version, the Fabric
loader, and a required dependency on Fabric API. Versions already on Modrinth (same version
number) are skipped, so it is safe to run again.

    python3 tools/upload-modrinth.py <project-slug-or-id>             # dry run: shows what it would do
    python3 tools/upload-modrinth.py <project-slug-or-id> --publish   # actually uploads

The token is read from the MODRINTH_TOKEN environment variable or ~/.modrinth-token. Create one at
https://modrinth.com/settings/pats with the "Create versions" and "Read projects" scopes.
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"
USER_AGENT = "adhrit/cape-creator-uploader (github.com/adhrit)"
FABRIC_API = "P7dR8mSH"  # Fabric API's Modrinth project id
DIST = Path(__file__).resolve().parent.parent / "dist"
JAR_NAME = re.compile(r"^cape-creator-(?P<mod>[\d.]+)\+mc(?P<mc>[\d.]+)\.jar$")


def token():
    t = os.environ.get("MODRINTH_TOKEN")
    if not t:
        f = Path.home() / ".modrinth-token"
        if f.exists():
            t = f.read_text().strip()
    if not t:
        sys.exit("No token: set MODRINTH_TOKEN or put it in ~/.modrinth-token")
    return t


def request(method, path, auth=None, body=None, content_type=None):
    req = urllib.request.Request(API + path, data=body, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if auth:
        req.add_header("Authorization", auth)
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path} failed: HTTP {e.code} {e.read().decode(errors='replace')}")


def multipart(data, file_path):
    boundary = uuid.uuid4().hex
    parts = [
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
        f"Content-Type: application/json\r\n\r\n{json.dumps(data)}\r\n".encode(),
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'
        f"Content-Type: application/java-archive\r\n\r\n".encode(),
        file_path.read_bytes(),
        f"\r\n--{boundary}--\r\n".encode(),
    ]
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def mc_key(mc):
    return [int(x) for x in mc.split(".")]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("project", help="Modrinth project slug or id")
    ap.add_argument("--publish", action="store_true", help="actually upload (default is a dry run)")
    ap.add_argument("--changelog", default="Initial release.")
    ap.add_argument("--type", default="release", choices=["release", "beta", "alpha"])
    args = ap.parse_args()

    auth = token()
    project = request("GET", f"/project/{args.project}", auth)
    if project.get("versions"):
        # listing needs the "Read versions" token scope; only needed when versions already exist
        existing = {v["version_number"] for v in request("GET", f"/project/{project['id']}/version", auth)}
    else:
        existing = set()
    print(f"Project: {project['title']} ({project['slug']}, id {project['id']}), {len(existing)} versions already uploaded")

    jars = []
    for f in DIST.glob("*.jar"):
        m = JAR_NAME.match(f.name)
        if m:
            jars.append((mc_key(m["mc"]), m["mod"], m["mc"], f))
    if not jars:
        sys.exit(f"No jars in {DIST} - run ./gradlew dist first")
    jars.sort()

    for _, mod, mc, jar in jars:
        version_number = f"{mod}+mc{mc}"
        if version_number in existing:
            print(f"  skip   {version_number} (already on Modrinth)")
            continue
        data = {
            "name": f"{project['title']} {mod} for Minecraft {mc}",
            "version_number": version_number,
            "changelog": args.changelog,
            "dependencies": [{"project_id": FABRIC_API, "dependency_type": "required"}],
            "game_versions": [mc],
            "version_type": args.type,
            "loaders": ["fabric"],
            "featured": mc == jars[-1][2],
            "status": "listed",
            "project_id": project["id"],
            "file_parts": ["file"],
            "primary_file": "file",
        }
        if not args.publish:
            print(f"  would upload {jar.name}: MC {mc}, fabric, requires Fabric API")
            continue
        body, ctype = multipart(data, jar)
        v = request("POST", "/version", auth, body, ctype)
        print(f"  uploaded {version_number} -> https://modrinth.com/mod/{project['slug']}/version/{v['id']}")

    if not args.publish:
        print("Dry run only. Add --publish to upload.")


if __name__ == "__main__":
    main()
