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
    parser.add_argument("--file-ids", required=True)
    args = parser.parse_args()
    raw_ids=args.file_ids.split(",")
    if len(raw_ids)!=4 or any(not value.strip().isdigit() for value in raw_ids):
        raise ValueError("Supply the four exact file IDs in Fabric 1.20.1, Forge 1.20.1, Fabric 1.21.1, NeoForge 1.21.1 order.")
    file_ids=[int(value.strip()) for value in raw_ids]
    if len(set(file_ids))!=4 or any(value<=0 for value in file_ids):
        raise ValueError("File IDs must be positive and unique.")
    selected=[({"id":file_id},f"trafficengine-{loader}-{args.version}+{mc}.jar")
        for file_id,(mc,loader) in zip(file_ids,(("1.20.1","fabric"),("1.20.1","forge"),("1.21.1","fabric"),("1.21.1","neoforge")))]
    print("Target file metadata:",json.dumps([{"id":f["id"],"name":n} for f,n in selected]))
    if args.mode=="inspect":
        return
    token=os.environ["CURSEFORGE_TOKEN"]
    headers={"X-Api-Token":token,"User-Agent":"TrafficEngine-changelog-maintenance/1.0"}
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
