"""Anonymous permission observations and a real NetEase advertised-TTL recheck.

No credentials or media URLs are persisted. A TTL field is not assumed to be a
hard CDN expiry. Run with --wait-ttl to re-read the original URL after that TTL.
"""
import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
from probe_sources import Client, attempt


def stamp():
    return datetime.now(timezone.utc).isoformat()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wait-ttl", action="store_true")
    args = parser.parse_args()
    report = {"checked_at": stamp(), "auth": "anonymous", "status": "running"}
    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    net = Client("https://music.163.com/")
    def song(song_id):
        data, status = net.api("https://music.163.com/api/song/enhance/player/url", ids=json.dumps([song_id]), br=128000)
        row = (data.get("data") or [{}])[0]
        observation = {"id": song_id, "http_status": status, "code": data.get("code"),
                       "resource_code": row.get("code"), "fee": row.get("fee"),
                       "url_present": bool(row.get("url")), "trial": row.get("freeTrialInfo"),
                       "expi_seconds": row.get("expi"), "size": row.get("size")}
        url = row.get("url")
        if url:
            url = urllib.parse.urlsplit(url)._replace(scheme="https").geturl()
        return observation, url

    free, original_url = song(139774)
    issued = time.time()
    report["netease_baseline"] = free
    expiry = report["netease_ttl"] = {"started_at": stamp(), "advertised_seconds": free.get("expi_seconds"),
                                       "status": "pending"}
    if original_url:
        attempt(expiry, "before", lambda: net.media(original_url))
    search, _ = net.api("https://music.163.com/api/search/get/web", s="周杰伦", type=1, limit=5, offset=0)
    candidates = (search.get("result") or {}).get("songs") or []
    report["netease_permissions"] = []
    for candidate in candidates[:3]:
        observation, url = song(candidate["id"])
        observation["search_fee"] = candidate.get("fee")
        report["netease_permissions"].append(observation)
    observation, _ = song(0)
    report["netease_unavailable"] = observation
    bili = Client("https://www.bilibili.com/")
    def bili_access():
        anonymous, _ = bili.api("https://api.bilibili.com/x/web-interface/nav")
        missing, _ = bili.api("https://api.bilibili.com/x/web-interface/view", aid=1)
        return {"anonymous_nav_code": anonymous.get("code"), "sample_aid": 1, "unavailable_code": missing.get("code")}
    attempt(report, "bilibili_permissions", bili_access)
    save()
    ttl = free.get("expi_seconds")
    if original_url and args.wait_ttl and isinstance(ttl, int) and 0 < ttl <= 3600:
        expiry["recheck_not_before_epoch"] = int(issued + ttl + 30)
        expiry["status"] = "waiting_for_advertised_ttl"
        save()
        print("Waiting for advertised TTL; report contains no media URL.", flush=True)
        while time.time() < issued + ttl + 30:
            time.sleep(min(5, issued + ttl + 30 - time.time()))
        expiry["rechecked_at"] = stamp()
        attempt(expiry, "original_after", lambda: net.media(original_url))
        refreshed, refreshed_url = song(139774)
        expiry["refreshed_metadata"] = refreshed
        expiry["same_url_returned"] = refreshed_url == original_url
        if refreshed_url:
            attempt(expiry, "refreshed_media", lambda: net.media(refreshed_url))
        expiry["observed_hard_expiry"] = expiry["original_after"].get("http_status") in (401, 403, 410)
        expiry["status"] = "observed"
    else:
        expiry["status"] = "not_waited"
    report["status"] = "completed"
    save()
    print("Report saved: " + str(args.output), flush=True)


if __name__ == "__main__":
    main()
