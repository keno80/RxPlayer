"""Bounded anonymous Bilibili pagination, identity and multi-part observations.

Never save signed URLs, cookies, complete responses or exception messages.
"""
import argparse
import json
import re
import sys
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
from probe_sources import Client, attempt, ProbeFailure


def identify(value):
    """Accept canonical BV / AV IDs and Bilibili video URLs, with a part index."""
    value = value.strip()
    part = 1
    if "://" in value:
        parsed = urllib.parse.urlsplit(value)
        if parsed.scheme != "https" or parsed.hostname not in ("www.bilibili.com", "m.bilibili.com", "bilibili.com") or parsed.username or parsed.password or parsed.port not in (None, 443):
            raise ValueError("unsupported_video_url")
        match = re.fullmatch(r"/video/([^/]+)/?", parsed.path)
        if not match:
            raise ValueError("unsupported_video_path")
        value = match[1]
        parts = urllib.parse.parse_qs(parsed.query, keep_blank_values=True).get("p", ["1"])
        if len(parts) != 1 or not re.fullmatch(r"[1-9]\d{0,5}", parts[0]):
            raise ValueError("invalid_part")
        part = int(parts[0])
    if re.fullmatch(r"BV[A-Za-z0-9]{10}", value):
        return {"bvid": value, "part": part}
    if re.fullmatch(r"av[1-9]\d{0,18}", value, re.IGNORECASE):
        return {"aid": int(value[2:]), "part": part}
    raise ValueError("invalid_video_id")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "auth": "anonymous", "query": "Blender 入门教程 合集"}
    client = Client("https://www.bilibili.com/")
    attempt(report, "session", lambda: {"http_status": client.get(client.referer, 1024)[1]})
    pages = []
    for page in (1, 2):
        def search():
            payload, status = client.api("https://api.bilibili.com/x/web-interface/search/type", search_type="video", keyword=report["query"], page=page, page_size=5)
            data = payload.get("data") or {}
            ids = [row["bvid"] for row in data.get("result") or [] if re.fullmatch(r"BV[A-Za-z0-9]{10}", row.get("bvid", ""))]
            return {"http_status": status, "code": payload.get("code"), "requested_page": page, "returned_page": data.get("page"), "ids": ids}
        pages.append(attempt({}, "result", search))
    report["search_pages"] = pages
    report["pagination_distinct"] = bool(pages[0].get("ids") and pages[1].get("ids") and set(pages[1]["ids"]) - set(pages[0]["ids"]))
    candidates = list(dict.fromkeys(video for page in pages for video in page.get("ids", [])))[:5]
    report["multipart_candidates"] = []
    for bvid in candidates:
        try:
            payload, _ = client.api("https://api.bilibili.com/x/web-interface/view", bvid=bvid)
            data = payload.get("data") or {}
            parts = data.get("pages") or []
            report["multipart_candidates"].append({"bvid": bvid, "code": payload.get("code"), "parts": len(parts)})
            if len(parts) < 2:
                continue
            canonical = {"bvid": data["bvid"], "aid": data["aid"], "parts": len(parts)}
            av, _ = client.api("https://api.bilibili.com/x/web-interface/view", aid=data["aid"])
            canonical["av_bv_same_video"] = (av.get("data") or {}).get("bvid") == data["bvid"]
            report["multipart"] = canonical
            canonical["audio_parts"] = []
            for part in parts[:2]:
                row = {"part": part["page"], "cid": part["cid"], "duration_seconds": part.get("duration")}
                def resolve():
                    payload, status = client.api("https://api.bilibili.com/x/player/playurl", bvid=bvid, cid=part["cid"], fnval=16, fnver=0, qn=64)
                    audio = ((payload.get("data") or {}).get("dash") or {}).get("audio") or []
                    row.update({"http_status": status, "code": payload.get("code"), "audio_present": bool(audio)})
                    if not audio:
                        raise ProbeFailure("no_audio")
                    stream = audio[0]
                    url = stream.get("baseUrl") or stream["base_url"]
                    row["mime"] = stream.get("mimeType")
                    row["codec"] = stream.get("codecs")
                    row["deadline_present"] = "deadline" in urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
                    return client.media(url)
                attempt(row, "media", resolve)
                canonical["audio_parts"].append(row)
            canonical["different_cids"] = parts[0]["cid"] != parts[1]["cid"]
            break
        except Exception as failure:
            report.setdefault("candidate_errors", []).append({"bvid": bvid, "error": type(failure).__name__})
    attempt(report, "unavailable", lambda: {"code": client.api("https://api.bilibili.com/x/web-interface/view", aid=1)[0].get("code")})
    multi = report.get("multipart") or {}
    report["passed"] = bool(report["pagination_distinct"] and all(p.get("code") == 0 for p in pages) and multi.get("av_bv_same_video") and multi.get("different_cids") and len(multi.get("audio_parts", [])) == 2 and all(p.get("media", {}).get("http_status") == 206 for p in multi.get("audio_parts", [])))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Bilibili boundaries: " + str(report["passed"]))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
