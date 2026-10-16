"""Build-plan and artifact checks. The isolated Gradle publisher handles uploading."""
import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tomllib
import urllib.error
import urllib.request
import zipfile
from pathlib import Path

REPOSITORY = "destanunal/TrafficEngine"
PLATFORMS = {"1.20.1": ("fabric", "forge"), "1.21.1": ("fabric", "neoforge")}
METADATA = {"fabric": "fabric.mod.json", "forge": "META-INF/mods.toml",
            "neoforge": "META-INF/neoforge.mods.toml"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def properties(text):
    return dict(re.findall(r"(?m)^\s*([\w.]+)\s*=\s*(.*?)\s*$", text))


def github_json(path, token):
    request = urllib.request.Request(
        "https://api.github.com/repos/" + REPOSITORY + "/" + path,
        headers={"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
                 "User-Agent": "TrafficEngine-release-automation/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise ValueError("GitHub source lookup failed (HTTP " + str(error.code) + ").") from None


def plan(version, mode, targets, channel, notes, tokens, resolve):
    require(re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?", version),
            "Use a version such as 1.8.2 or 1.8.3-beta.1.")
    require(mode in ("build", "publish"), "Unknown operation.")
    require(targets in ("curseforge", "modrinth", "both"), "Unknown destination.")
    require(channel in ("release", "beta", "alpha"), "Unknown release channel.")
    require(0 < len(notes.strip()) <= 40000, "Provide a changelog (maximum 40,000 characters).")
    require(re.search(r"(?m)^# .*\b" + re.escape(version) + r"(?![\w.+-])", notes),
            "The changelog heading must contain the selected version; update the old release notes.")
    sites = ("curseforge", "modrinth") if targets == "both" else (targets,)
    if mode == "publish":
        for site in sites:
            require(tokens.get(site, "").strip(),
                    "Add " + site.upper() + "_TOKEN in Settings > Secrets and variables > Actions.")
    builds, uploads = [], []
    for minecraft, loaders in PLATFORMS.items():
        sha, props = resolve(minecraft)
        require(re.fullmatch(r"[0-9a-f]{40}", sha), "Invalid source commit.")
        require(props.get("mod_version") == version, "mc-" + minecraft + " is not version " + version + ".")
        require(props.get("minecraft_version") == minecraft, "Source Minecraft version mismatch.")
        builds.append({"minecraft": minecraft, "sha": sha, "native": loaders[1]})
        uploads.extend({"minecraft": minecraft, "loader": loader, "destination": site, "sha": sha}
                       for loader in loaders for site in sites)
    return {"version": version, "mode": mode, "channel": channel, "targets": targets,
            "builds": builds, "uploads": uploads}


def filename(version, minecraft, loader):
    require(minecraft in PLATFORMS and loader in PLATFORMS[minecraft], "Unsupported Minecraft/loader pair.")
    require(re.fullmatch(r"\d+\.\d+\.\d+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?", version), "Invalid version.")
    return "trafficengine-" + loader + "-" + version + "+" + minecraft + ".jar"


def inspect_archive(archive, version, minecraft, loader):
    """Accept paths or in-memory ZIPs; tests do not need trial JAR files."""
    with zipfile.ZipFile(archive) as jar:
        require(jar.testzip() is None, "Corrupt distribution archive.")
        entries = set(jar.namelist())
        require(METADATA[loader] in entries, "Missing " + loader + " metadata.")
        require(not any(path in entries for key, path in METADATA.items() if key != loader),
                "Distribution contains another loader's metadata.")
        expected = version + "+" + minecraft
        if loader == "fabric":
            metadata = json.loads(jar.read(METADATA[loader]))
            require(metadata.get("id") == "trafficengine" and metadata.get("version") == expected,
                    "Wrong mod ID/version in Fabric metadata.")
            require(metadata.get("depends", {}).get("minecraft") == "~" + minecraft,
                    "Wrong Minecraft version in Fabric metadata.")
        else:
            metadata = tomllib.loads(jar.read(METADATA[loader]).decode("utf-8"))
            require(any(mod.get("modId") == "trafficengine" and mod.get("version") == expected
                        for mod in metadata.get("mods", [])), "Wrong mod ID/version in platform metadata.")
            require(any(dep.get("modId") == "minecraft"
                        and dep.get("versionRange", "").startswith("[" + minecraft + ",")
                        for dep in metadata.get("dependencies", {}).get("trafficengine", [])),
                    "Wrong Minecraft version in platform metadata.")
        require("com/destan/trafficengine/TrafficEngine.class" in entries, "Common mod classes are missing.")
        maximum = 61 if minecraft == "1.20.1" else 65
        for path in entries:
            if path.startswith("com/destan/trafficengine/") and path.endswith(".class"):
                header = jar.read(path)[:8]
                require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe"
                        and int.from_bytes(header[6:8], "big") <= maximum,
                        "Incompatible Java bytecode: " + path)


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def collect(root, output, release, minecraft):
    build = next(item for item in release["builds"] if item["minecraft"] == minecraft)
    actual = properties((root / "gradle.properties").read_text(encoding="utf-8"))
    require(actual.get("mod_version") == release["version"]
            and actual.get("minecraft_version") == minecraft, "Checkout version changed after planning.")
    current = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
    require(current == build["sha"], "Checkout commit changed after planning.")
    files = []
    for loader in PLATFORMS[minecraft]:
        name = filename(release["version"], minecraft, loader)
        source = root / loader / "build/libs" / name
        require(source.is_file(), "Missing final remapped distribution: " + name)
        inspect_archive(source, release["version"], minecraft, loader)
        files.append({"loader": loader, "name": name, "sha256": digest(source), "bytes": source.stat().st_size})
    output.mkdir(parents=True, exist_ok=True)
    for item in files:
        shutil.copy2(root / item["loader"] / "build/libs" / item["name"], output / item["name"])
    manifest = {"version": release["version"], "minecraft": minecraft, "source_sha": build["sha"], "files": files}
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("Validated Minecraft " + minecraft + ": " + ", ".join(f["name"] for f in files))


def verify(directory, release, minecraft, loader):
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    build = next(item for item in release["builds"] if item["minecraft"] == minecraft)
    require(manifest.get("version") == release["version"] and manifest.get("minecraft") == minecraft
            and manifest.get("source_sha") == build["sha"], "Downloaded artifact does not match this release.")
    name = filename(release["version"], minecraft, loader)
    matches = [item for item in manifest["files"] if item["loader"] == loader and item["name"] == name]
    require(len(matches) == 1, "Missing or duplicate distribution entry.")
    path = directory / name
    require(path.is_file() and digest(path) == matches[0]["sha256"], "Distribution checksum mismatch.")
    inspect_archive(path, release["version"], minecraft, loader)
    return path.resolve()


def output(name, value):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
        stream.write(name + "=" + value + "\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("prepare", "collect", "verify"))
    parser.add_argument("--root", type=Path)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--plan", type=Path)
    parser.add_argument("--minecraft", choices=PLATFORMS)
    parser.add_argument("--loader", choices=METADATA)
    args = parser.parse_args()
    if args.operation == "prepare":
        require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Run this workflow in " + REPOSITORY + ".")
        token = os.environ["GH_TOKEN"]

        def resolve(minecraft):
            sha = github_json("commits/mc-" + minecraft, token)["sha"]
            data = github_json("contents/gradle.properties?ref=" + sha, token)
            return sha, properties(base64.b64decode(data["content"]).decode("utf-8"))

        notes = os.environ["RELEASE_CHANGELOG"].strip()
        release = plan(os.environ["RELEASE_VERSION"].strip(), os.environ["RELEASE_MODE"],
                       os.environ["RELEASE_TARGETS"], os.environ["RELEASE_CHANNEL"], notes,
                       {"curseforge": os.environ.get("CURSEFORGE_TOKEN", ""),
                        "modrinth": os.environ.get("MODRINTH_TOKEN", "")}, resolve)
        args.directory.mkdir(parents=True, exist_ok=True)
        (args.directory / "plan.json").write_text(json.dumps(release, indent=2) + "\n", encoding="utf-8")
        (args.directory / "changelog.md").write_text(notes + "\n", encoding="utf-8")
        output("version", release["version"])
        output("build_matrix", json.dumps({"include": release["builds"]}, separators=(",", ":")))
        output("publish_matrix", json.dumps({"include": release["uploads"]}, separators=(",", ":")))
        print("Pinned source commits. All four distributions must pass checks before uploading.")
    else:
        release = json.loads(args.plan.read_text(encoding="utf-8"))
        if args.operation == "collect":
            collect(args.root, args.directory, release, args.minecraft)
        else:
            output("artifact", str(verify(args.directory, release, args.minecraft, args.loader)))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, zipfile.BadZipFile, StopIteration) as error:
        print("Release check failed: " + str(error), file=sys.stderr)
        sys.exit(1)
