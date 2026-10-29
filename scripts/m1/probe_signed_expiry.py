"""Wait for real Bilibili/YouTube signed-URL deadlines, without persisting URLs.

Use a report outside the repository for a long-running observation. This does not
play media, touch the phone, or change system time. No account credentials used.
"""
import argparse
import json
import sys
import time
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
from probe_sources import Client, attempt
from validate_streaming import resolve_bilibili, resolve_youtube


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--newpipe-dir", type=Path, required=True)
    parser.add_argument("--java", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source", choices=("all", "bilibili", "youtube"), default="all")
    args = parser.parse_args()
    report = {"started_at": datetime.now(timezone.utc).isoformat(), "status": "initializing", "samples": []}
    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    def youtube():
        diagnostic = {}
        result = resolve_youtube(args.newpipe_dir, diagnostic, args.java)
        report["youtube_resolution"] = diagnostic.get("youtube")
        return result
    streams = []
    if args.source in ("all", "bilibili"):
        streams.append(resolve_bilibili())
    if args.source in ("all", "youtube"):
        streams.extend(youtube())
    if not streams:
        report["status"] = "initialization_failed"
        save()
        print("No resolved audio inputs; failure recorded.", flush=True)
        return
    pending = []
    for stream in streams:
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(stream["url"]).query)
        field = "deadline" if stream["source"] == "bilibili" else "expire"
        expiry = int(params[field][0])
        row = {"source": stream["source"], "id": stream["id"], "mime": stream["mime"],
               "deadline_epoch": expiry, "not_before_epoch": expiry + 30, "status": "waiting"}
        referer = "https://www.bilibili.com/" if stream["source"] == "bilibili" else "https://www.youtube.com/"
        client = Client(referer)
        attempt(row, "before", lambda: client.media(stream["url"]))
        report["samples"].append(row)
        pending.append((expiry + 30, stream, row, client))
    report["status"] = "waiting_for_real_deadlines"
    save()
    print("Waiting for real signed-URL deadlines; URLs remain only in process memory.", flush=True)
    for deadline, stream, row, client in sorted(pending, key=lambda item: item[0]):
        while time.time() < deadline:
            time.sleep(min(5, deadline - time.time()))
        row["checked_at"] = datetime.now(timezone.utc).isoformat()
        attempt(row, "original_after", lambda: client.media(stream["url"]))
        try:
            fresh = resolve_bilibili() if stream["source"] == "bilibili" else next(
                item for item in youtube() if item["mime"] == stream["mime"])
            row["fresh_url_differs"] = fresh["url"] != stream["url"]
            attempt(row, "fresh_media", lambda: client.media(fresh["url"]))
        except Exception as failure:
            row["refresh_error"] = type(failure).__name__
        row["old_resource_rejected"] = row["original_after"].get("http_status") in (401, 403, 410)
        row["status"] = "observed"
        save()
    report["status"] = "completed"
    save()
    print("Signed-URL observations completed.", flush=True)


if __name__ == "__main__": main()
