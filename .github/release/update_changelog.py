"""Update existing file notes only; never upload, delete or replace a distribution."""
import argparse
import json
import os
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from changelog_format import tight_changelog

PROJECT = 1654475
BASE = f"https://minecraft.curseforge.com/api/projects/{PROJECT}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=("inspect", "update"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--changelog", type=Path)
    args = parser.parse_args()
    token = os.environ["CURSEFORGE_TOKEN"]
    headers = {"X-Api-Token": token, "User-Agent": "TrafficEngine-changelog-maintenance/1.0"}
    with urllib.request.urlopen(urllib.request.Request(BASE + "/files", headers=headers), timeout=40) as response:
        inventory = json.load(response)
    files = inventory.get("data", inventory.get("files", [])) if isinstance(inventory, dict) else inventory
    selected = []
    for item in files:
        name = item.get("displayName", item.get("fileName", item.get("name", "")))
        if f"-{args.version}+" in name or f"-{args.version}-" in name:
            selected.append((item, name))
    print("Matching files:", json.dumps([{"id": f.get("id"), "name": n} for f, n in selected], ensure_ascii=False))
    if args.mode == "inspect":
        return
    expected = {f"trafficengine-{loader}-{args.version}+{mc}.jar" for mc, loader in
                (("1.20.1", "fabric"), ("1.20.1", "forge"), ("1.21.1", "fabric"), ("1.21.1", "neoforge"))}
    if len(selected) != 4 or {n for _, n in selected} != expected:
        raise ValueError("Expected exactly the four matching distribution names; no file was changed.")
    notes = tight_changelog(args.changelog.read_text(encoding="utf-8"))
    if not notes.startswith("# ") or args.version not in notes.splitlines()[0]:
        raise ValueError("The changelog heading must match the selected release.")
    for item, name in selected:
        file_id = int(item["id"])
        metadata = {"fileID": file_id, "changelog": notes, "changelogType": "markdown"}
        boundary = "TrafficEngineMetadata" + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
                'Content-Type: application/json; charset=utf-8\r\n\r\n'
                + json.dumps(metadata, ensure_ascii=False) + f"\r\n--{boundary}--\r\n").encode("utf-8")
        request = urllib.request.Request(BASE + "/update-file", data=body,
            headers={**headers, "Content-Type": f"multipart/form-data; boundary={boundary}"})
        with urllib.request.urlopen(request, timeout=40) as response:
            result = json.load(response)
        if result.get("id") != file_id:
            raise ValueError("Unexpected file update response.")
        print("Updated changelog:", file_id, name)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        print("CurseForge API HTTP", error.code)
        raise SystemExit(1)
