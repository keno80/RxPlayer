"""Controlled loopback header/Range check, separate from real HTTPS source tests."""

import argparse
import json
import re
import subprocess
import sys
import tempfile
import threading
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.dont_write_bytecode = True
from validate_streaming import Mpv, require, validate_controls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mpv", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "requests": []}
    with tempfile.TemporaryDirectory(prefix="rxplayer-m1-tone-") as folder:
        tone = Path(folder) / "tone.mp3"
        process = subprocess.run(["ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                                  "sine=frequency=440:duration=45", "-c:a", "libmp3lame",
                                  "-q:a", "8", str(tone)], capture_output=True, timeout=30)
        require(process.returncode == 0, "tone_generation_failed")
        media = tone.read_bytes()

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_GET(self):
                valid = (self.headers.get("User-Agent") == "RxPlayer-M1-Header-Probe"
                         and self.headers.get("Referer") == "https://rxplayer.invalid/probe")
                row = {"headers_match": valid, "range_present": bool(self.headers.get("Range"))}
                report["requests"].append(row)
                if not valid:
                    row["status"] = 403
                    self.send_error(403)
                    return
                start, end = 0, len(media) - 1
                requested = self.headers.get("Range")
                if requested:
                    match = re.fullmatch(r"bytes=(\d+)-(\d*)", requested)
                    if not match or int(match[1]) >= len(media):
                        row["status"] = 416
                        self.send_error(416)
                        return
                    start = int(match[1])
                    end = min(int(match[2]), end) if match[2] else end
                row["status"] = 206 if requested else 200
                self.send_response(row["status"])
                self.send_header("Content-Type", "audio/mpeg")
                self.send_header("Content-Length", str(end - start + 1))
                self.send_header("Accept-Ranges", "bytes")
                if requested:
                    self.send_header("Content-Range", f"bytes {start}-{end}/{len(media)}")
                self.end_headers()
                try:
                    self.wfile.write(media[start:end + 1])
                except (ConnectionResetError, BrokenPipeError):
                    pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        engine = None
        try:
            url = f"http://127.0.0.1:{server.server_port}/tone.mp3"
            try:
                urllib.request.urlopen(url, timeout=5).close()
                report["missing_headers_status"] = 200
            except urllib.error.HTTPError as failure:
                report["missing_headers_status"] = failure.code
                failure.close()
            require(report["missing_headers_status"] == 403, "negative_control_failed")
            engine = Mpv(args.mpv)
            stream = {"source": "loopback_header_guard", "id": "generated-tone", "mime": "audio/mpeg",
                      "url": url, "headers": ["User-Agent: RxPlayer-M1-Header-Probe",
                                               "Referer: https://rxplayer.invalid/probe"]}
            report["playback"] = validate_controls(engine, stream)
            require(any(r["headers_match"] and r["status"] == 206 for r in report["requests"]),
                    "range_not_observed")
            report["passed"] = True
        except Exception as failure:
            report["passed"] = False
            report["error"] = str(failure) if type(failure).__name__ == "ValidationFailure" else type(failure).__name__
        finally:
            if engine is not None:
                report["shutdown"] = engine.close()
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Report saved: " + str(args.output))


if __name__ == "__main__":
    main()
