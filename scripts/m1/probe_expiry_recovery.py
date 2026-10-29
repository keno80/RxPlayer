"""Real HTTP expiry on a controlled fixture, explicitly not source-CDN expiry."""
import argparse
import io
import json
import math
import re
import struct
import sys
import threading
import time
import urllib.parse
import uuid
import wave
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.dont_write_bytecode = True
from probe_sources import Client, attempt
from validate_streaming import Mpv, require, validate_controls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mpv", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "scope": "controlled_http_expiry",
              "real_source_expiry": False}
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as output:
        output.setparams((1, 2, 8000, 0, "NONE", "not compressed"))
        output.writeframes(b"".join(struct.pack("<h", int(400 * math.sin(i * 2 * math.pi * 440 / 8000))) for i in range(45 * 8000)))
    media = buffer.getvalue()
    tokens = {}
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args): pass
        def do_GET(self):
            token = urllib.parse.parse_qs(urllib.parse.urlsplit(self.path).query).get("token", [""])[0]
            if token not in tokens or time.monotonic() >= tokens[token]:
                self.send_error(410)
                return
            requested = self.headers.get("Range")
            start, end = 0, len(media) - 1
            if requested:
                match = re.fullmatch(r"bytes=(\d+)-(\d*)", requested)
                if not match or int(match[1]) >= len(media):
                    self.send_error(416)
                    return
                start = int(match[1])
                end = min(int(match[2]), end) if match[2] else end
            self.send_response(206 if requested else 200)
            payload = media[start:end + 1]
            self.send_header("Content-Type", "audio/wav")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Accept-Ranges", "bytes")
            if requested:
                self.send_header("Content-Range", f"bytes {start}-{end}/{len(media)}")
            self.end_headers()
            try: self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError): pass
    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    def resolve(seconds):
        token = uuid.uuid4().hex
        tokens[token] = time.monotonic() + seconds
        return f"http://127.0.0.1:{server.server_port}/fixture.wav?token={token}"
    engine = None
    try:
        client = Client("http://127.0.0.1/")
        original = resolve(3)
        attempt(report, "before", lambda: client.media(original))
        time.sleep(3.2)
        attempt(report, "expired", lambda: client.media(original))
        refreshed = resolve(60)
        report["refreshed_url_differs"] = refreshed != original
        attempt(report, "refreshed", lambda: client.media(refreshed))
        engine = Mpv(args.mpv)
        report["refreshed_playback"] = validate_controls(engine, {
            "source": "controlled_expiry", "id": "same-fixture", "mime": "audio/wav",
            "url": refreshed, "headers": []})
        require(report["before"]["http_status"] == 206 and report["expired"]["http_status"] == 410
                and report["refreshed"]["http_status"] == 206, "expiry_cycle_failed")
        report["passed"] = True
    except Exception as failure:
        report["passed"] = False
        report["error_type"] = type(failure).__name__
    finally:
        if engine is not None: report["shutdown"] = engine.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Report saved: " + str(args.output))


if __name__ == "__main__": main()
