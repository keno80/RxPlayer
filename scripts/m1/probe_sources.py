"""Bounded anonymous source probes. No credentials, full media files, or URL logs.

Run from repository root: python scripts/m1/probe_sources.py --output <report.json>
These private web endpoints are observations, not supported public API contracts.
"""

import argparse
import http.cookiejar
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36"
MAX_JSON = 4 * 1024 * 1024
MAX_MEDIA = 65536


class ProbeFailure(Exception):
    pass


class Client:
    def __init__(self, referer):
        self.referer = referer
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())
        )

    def get(self, url, limit=MAX_JSON, headers=None):
        if urllib.parse.urlsplit(url).scheme not in ("http", "https"):
            raise ProbeFailure("unsupported URL scheme")
        request = urllib.request.Request(
            url, headers={"User-Agent": UA, "Referer": self.referer, **(headers or {})}
        )
        with self.opener.open(request, timeout=20) as response:
            body = response.read(limit)
            return body, response.status, response.headers, response.url

    def api(self, base, **params):
        url = base + "?" + urllib.parse.urlencode(params)
        body, status, _, _ = self.get(url)
        return json.loads(body), status

    def media(self, url):
        body, status, headers, final_url = self.get(
            url, MAX_MEDIA, {"Range": f"bytes=0-{MAX_MEDIA - 1}"}
        )
        return {
            "http_status": status,
            "bytes_read": len(body),
            "content_type": headers.get("Content-Type"),
            "range_supported": status == 206 and bool(headers.get("Content-Range")),
            "https": urllib.parse.urlsplit(final_url).scheme == "https",
            "host": urllib.parse.urlsplit(final_url).hostname,
            "signature": "mp4/ftyp" if b"ftyp" in body[:32] else
                         "webm" if body.startswith(bytes.fromhex("1a45dfa3")) else
                         "mp3/id3" if body.startswith(b"ID3") else "unclassified",
        }


def attempt(report, key, function):
    try:
        report[key] = function()
    except urllib.error.HTTPError as error:
        report[key] = {"error": "http", "http_status": error.code}
    except Exception as error:
        # Exception messages may include signed URLs or request parameters.
        report[key] = {"error": type(error).__name__}
    return report[key]


def bilibili(report, video_id):
    client = Client("https://www.bilibili.com/")
    attempt(report, "anonymous_session", lambda: {"http_status": client.get(client.referer, 1024)[1]})

    def search():
        data, status = client.api("https://api.bilibili.com/x/web-interface/search/type",
                                  search_type="video", keyword="Blender", page=1)
        rows = (data.get("data") or {}).get("result") or []
        return {"http_status": status, "code": data.get("code"), "query": "Blender",
                "count": len(rows), "sample_ids": [row.get("bvid") for row in rows[:3]]}

    attempt(report, "search", search)
    view, status = client.api("https://api.bilibili.com/x/web-interface/view", bvid=video_id)
    details = view.get("data") or {}
    pages = details.get("pages") or []
    report["metadata"] = {"http_status": status, "code": view.get("code"), "bvid": video_id,
                          "duration_seconds": details.get("duration"), "parts": len(pages),
                          "cid": pages[0].get("cid") if pages else None}
    if not pages:
        return
    play, status = client.api("https://api.bilibili.com/x/player/playurl",
                              bvid=video_id, cid=pages[0]["cid"], fnval=16, fnver=0, qn=64)
    data = play.get("data") or {}
    audio = (data.get("dash") or {}).get("audio") or []
    report["resolve"] = {"http_status": status, "code": play.get("code"),
                         "audio": [{"id": row.get("id"), "mime": row.get("mimeType"),
                                    "codecs": row.get("codecs")} for row in audio]}
    if audio:
        url = audio[0].get("baseUrl") or audio[0].get("base_url")
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(url).query)
        report["resolve"]["deadline_epoch"] = (query.get("deadline") or [None])[0]
        attempt(report, "media_range", lambda: client.media(url))


def netease(report, song_id):
    client = Client("https://music.163.com/")
    data, status = client.api("https://music.163.com/api/search/get/web",
                              s="纯音乐", type=1, offset=0, limit=3)
    songs = (data.get("result") or {}).get("songs") or []
    report["search"] = {"http_status": status, "code": data.get("code"), "query": "纯音乐",
                        "count": len(songs), "sample_ids": [song.get("id") for song in songs]}
    selected = song_id or next((s["id"] for s in songs if s.get("fee") == 0),
                               songs[0]["id"] if songs else None)
    if selected is None:
        return
    play, status = client.api("https://music.163.com/api/song/enhance/player/url",
                              ids=json.dumps([selected]), br=128000)
    rows = play.get("data") or []
    report["resolve"] = {"http_status": status, "code": play.get("code"), "song_id": selected,
                         "audio": [{"code": row.get("code"), "type": row.get("type"),
                                    "bitrate": row.get("br"), "size": row.get("size"),
                                    "expi_seconds": row.get("expi"), "url_present": bool(row.get("url")),
                                    "fee": row.get("fee"), "trial_present": bool(row.get("freeTrialInfo"))}
                                   for row in rows]}
    if rows and rows[0].get("url"):
        url = rows[0]["url"]
        attempt(report, "media_range", lambda: client.media(url))
        if urllib.parse.urlsplit(url).scheme == "http":
            secure_url = urllib.parse.urlsplit(url)._replace(scheme="https").geturl()
            attempt(report, "media_range_https", lambda: client.media(secure_url))


def embedded_json(html, variable):
    match = re.search(r"(?:var\s+)?" + re.escape(variable) + r"\s*=\s*", html)
    if not match:
        raise ProbeFailure("embedded JSON absent")
    return json.JSONDecoder().raw_decode(html[match.end():])[0]


def video_ids(value):
    if isinstance(value, dict):
        renderer = value.get("videoRenderer")
        if renderer and renderer.get("videoId"):
            yield renderer["videoId"]
        for child in value.values():
            yield from video_ids(child)
    elif isinstance(value, list):
        for child in value:
            yield from video_ids(child)


def youtube(report, video_id):
    client = Client("https://www.youtube.com/")

    def search():
        body, status, _, _ = client.get("https://www.youtube.com/results?search_query=Blender+Big+Buck+Bunny")
        data = embedded_json(body.decode("utf-8"), "ytInitialData")
        ids = list(dict.fromkeys(video_ids(data)))
        return {"http_status": status, "query": "Blender Big Buck Bunny", "count": len(ids),
                "sample_ids": ids[:3]}

    attempt(report, "search", search)
    body, status, _, _ = client.get("https://www.youtube.com/watch?" + urllib.parse.urlencode({"v": video_id}))
    data = embedded_json(body.decode("utf-8"), "ytInitialPlayerResponse")
    playable = data.get("playabilityStatus") or {}
    formats = (data.get("streamingData") or {}).get("adaptiveFormats") or []
    audio = [row for row in formats if row.get("mimeType", "").startswith("audio/")]
    report["resolve"] = {"http_status": status, "video_id": video_id, "status": playable.get("status"),
                         "audio": [{"itag": row.get("itag"), "mime": row.get("mimeType"),
                                    "direct_url": bool(row.get("url")),
                                    "cipher": bool(row.get("signatureCipher") or row.get("cipher"))}
                                   for row in audio]}
    direct = next((row["url"] for row in audio if row.get("url")), None)
    if direct:
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(direct).query)
        report["resolve"]["expire_epoch"] = (query.get("expire") or [None])[0]
        attempt(report, "media_range", lambda: client.media(direct))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", choices=["all", "bilibili", "netease", "youtube"], default="all")
    parser.add_argument("--bilibili-id", default="BV1xx411c7mD")
    parser.add_argument("--netease-id", type=int)
    parser.add_argument("--youtube-id", default="aqz-KE-bpKQ")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "auth": "anonymous",
              "media_byte_limit": MAX_MEDIA, "sources": {}}
    for name, probe, content_id in [("bilibili", bilibili, args.bilibili_id),
                                     ("netease", netease, args.netease_id),
                                     ("youtube", youtube, args.youtube_id)]:
        if args.source not in ("all", name):
            continue
        result = report["sources"][name] = {}
        attempt(result, "probe", lambda: probe(result, content_id) or {"completed": True})
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Report saved: {args.output}")


if __name__ == "__main__":
    main()
