"""Standalone Windows/mpv and NewPipe runtime validation; never builds the app.

Requires a verified mpv binary and NewPipe v0.26.5 runtime JARs outside the repo.
Temporary media URLs remain in memory and are sent through local IPC, never argv.
"""

import argparse
import ctypes
import hashlib
import json
import os
import msvcrt
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
from probe_sources import Client, UA


class ValidationFailure(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ValidationFailure(message)


class Mpv:
    def __init__(self, executable, proxy_mode="system"):
        self.pipe_name = r"\\.\pipe\rxplayer-m1-" + uuid.uuid4().hex
        self.events = []
        self.sequence = 0
        self.pipe = None
        self.buffer = b""
        environment = os.environ.copy()
        if proxy_mode == "system":
            # FFmpeg/mpv does not automatically read the Windows Internet proxy.
            # Bridge existing user configuration only to this child, never argv/logs.
            proxies = urllib.request.getproxies()
            for scheme in ("http", "https"):
                if scheme in proxies:
                    environment[scheme + "_proxy"] = proxies[scheme]
            environment["no_proxy"] = ",".join(filter(None, [proxies.get("no"), "127.0.0.1", "localhost"]))
        self.process = subprocess.Popen(
            [str(executable), "--no-config", "--load-scripts=no", "--terminal=no",
             "--no-video", "--idle=yes", "--ytdl=no", "--ao=wasapi", "--mute=yes",
             "--cache=yes", "--demuxer-max-bytes=8MiB", "--network-timeout=20",
             "--input-ipc-server=" + self.pipe_name],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NO_WINDOW,
            env=environment,
        )
        try:
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                require(self.process.poll() is None, "engine_exited_before_ipc")
                try:
                    self.pipe = open(self.pipe_name, "r+b", buffering=0)
                    break
                except FileNotFoundError:
                    time.sleep(0.05)
            require(self.pipe is not None, "ipc_start_timeout")
        except Exception:
            self.process.kill()
            self.process.wait(timeout=5)
            raise

    def _message(self, deadline):
        # A blocking read and write on the same synchronous Windows handle deadlock.
        # Peek first, then read only available bytes; all IPC stays on this thread.
        peek = ctypes.WinDLL("kernel32", use_last_error=True).PeekNamedPipe
        peek.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_uint32,
                         ctypes.c_void_p, ctypes.POINTER(ctypes.c_uint32), ctypes.c_void_p]
        peek.restype = ctypes.c_int
        while time.monotonic() < deadline:
            if b"\n" in self.buffer:
                line, self.buffer = self.buffer.split(b"\n", 1)
                return json.loads(line)
            available = ctypes.c_uint32()
            handle = msvcrt.get_osfhandle(self.pipe.fileno())
            require(bool(peek(handle, None, 0, None, ctypes.byref(available), None)), "ipc_closed")
            if available.value:
                self.buffer += self.pipe.read(available.value)
            else:
                time.sleep(0.01)
        raise ValidationFailure("ipc_reply_timeout")

    def command(self, *command):
        self.sequence += 1
        request = {"command": list(command), "request_id": self.sequence}
        self.pipe.write(json.dumps(request).encode("utf-8") + b"\n")
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            response = self._message(deadline)
            if response.get("request_id") == self.sequence:
                require(response.get("error") == "success", "mpv_command_" + str(response.get("error")))
                return response.get("data")
            if "event" in response:
                self.events.append(response["event"])
        raise ValidationFailure("ipc_command_timeout")

    def get(self, name):
        try:
            return self.command("get_property", name)
        except ValidationFailure as failure:
            if str(failure) == "mpv_command_property unavailable":
                return None
            raise

    def wait(self, predicate, label, seconds=25):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            require(self.process.poll() is None, "engine_exited")
            result = predicate()
            if result:
                return result
            time.sleep(0.15)
        raise ValidationFailure(label + "_timeout")

    def load(self, stream):
        self.command("set_property", "http-header-fields", stream["headers"])
        self.command("set_property", "pause", False)
        self.command("loadfile", stream["url"], "replace")

    def close(self):
        forced = False
        try:
            if self.process.poll() is None:
                self.command("quit")
                self.process.wait(timeout=5)
        except (OSError, ValidationFailure, subprocess.TimeoutExpired):
            if self.process.poll() is None:
                forced = True
                self.process.kill()
                self.process.wait(timeout=5)
        finally:
            if self.pipe is not None:
                self.pipe.close()
        try:
            with open(self.pipe_name, "r+b", buffering=0):
                pipe_removed = False
        except OSError:
            pipe_removed = True
        return {"exit_code": self.process.returncode, "forced": forced, "pipe_removed": pipe_removed}


def resolve_bilibili():
    client = Client("https://www.bilibili.com/")
    client.get(client.referer, 1024)
    metadata, _ = client.api("https://api.bilibili.com/x/web-interface/view", bvid="BV1xx411c7mD")
    cid = metadata["data"]["pages"][0]["cid"]
    play, _ = client.api("https://api.bilibili.com/x/player/playurl",
                         bvid="BV1xx411c7mD", cid=cid, fnval=16, fnver=0, qn=64)
    audio = play["data"]["dash"]["audio"][0]
    return {"source": "bilibili", "id": "BV1xx411c7mD", "mime": audio["mimeType"],
            "url": audio.get("baseUrl") or audio["base_url"],
            "headers": ["User-Agent: " + UA, "Referer: " + client.referer]}


def resolve_netease():
    client = Client("https://music.163.com/")
    play, _ = client.api("https://music.163.com/api/song/enhance/player/url", ids="[139774]", br=128000)
    audio = play["data"][0]
    require(bool(audio.get("url")), "netease_no_url")
    url = urllib.parse.urlsplit(audio["url"])._replace(scheme="https").geturl()
    return {"source": "netease", "id": "139774", "mime": "audio/mpeg", "url": url,
            "headers": ["User-Agent: " + UA, "Referer: " + client.referer]}


def resolve_youtube(jars, report, java="java"):
    command = [str(java), "-Djava.net.useSystemProxies=true", "--class-path", str(jars / "*"),
               str(Path(__file__).with_name("NewPipeProbe.java")), "aqz-KE-bpKQ"]
    result = subprocess.run(command, capture_output=True, encoding="utf-8", timeout=180,
                            creationflags=subprocess.CREATE_NO_WINDOW)
    require(result.returncode == 0, "newpipe_launcher_failed")
    data = json.loads(result.stdout)
    streams = []
    sanitized = []
    for audio in data.pop("audio", []):
        content = audio.pop("content", None)
        if audio.get("is_url") and content:
            uri = urllib.parse.urlsplit(content)
            require(uri.scheme == "https", "youtube_non_https")
            audio["host"] = uri.hostname
            streams.append({"source": "youtube", "id": data["video_id"], "mime": audio["mime"],
                            "url": content, "headers": ["User-Agent: " + UA, "Referer: https://www.youtube.com/"]})
        sanitized.append(audio)
    data["audio"] = sanitized
    report["youtube"] = data
    # Validate one AAC and one Opus input if provided, not just the first format.
    selected = []
    for prefix in ("audio/mp4", "audio/webm"):
        candidate = next((s for s in streams if s["mime"] and s["mime"].startswith(prefix)), None)
        if candidate:
            selected.append(candidate)
    return selected


def validate_controls(engine, stream):
    result = {key: stream[key] for key in ("source", "id", "mime")}
    result["https"] = urllib.parse.urlsplit(stream["url"]).scheme == "https"
    engine.load(stream)
    engine.wait(lambda: isinstance(engine.get("time-pos"), (int, float)), "load")
    start = engine.get("time-pos")
    engine.wait(lambda: (engine.get("time-pos") or 0) > start + 0.6, "progress")
    result.update({"loaded": True, "duration_seconds": engine.get("duration"),
                   "audio_codec": engine.get("audio-codec"), "audio_params": engine.get("audio-params"),
                   "audio_output": engine.get("current-ao"), "seekable": engine.get("seekable")})
    require(result["audio_output"] == "wasapi" and result["audio_params"], "audio_output_not_initialized")
    engine.command("set_property", "pause", True)
    time.sleep(0.2)
    before = engine.get("time-pos")
    time.sleep(1)
    after = engine.get("time-pos")
    require(engine.get("pause") is True and abs(after - before) <= 0.1, "pause_drift")
    result["pause_drift_seconds"] = round(abs(after - before), 4)
    engine.command("seek", 30, "absolute+exact")
    engine.wait(lambda: abs((engine.get("time-pos") or 0) - 30) < 0.75, "seek", 15)
    result["seek_position_seconds"] = engine.get("time-pos")
    engine.command("set_property", "pause", False)
    engine.wait(lambda: (engine.get("time-pos") or 0) > 30.6, "resume")
    result["resume_position_seconds"] = engine.get("time-pos")
    engine.command("stop")
    engine.wait(lambda: engine.get("idle-active") is True, "stop", 5)
    result["stopped"] = True
    result["passed"] = True
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mpv", type=Path, required=True)
    parser.add_argument("--newpipe-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--proxy-mode", choices=("inherit", "system"), default="system")
    parser.add_argument("--java", default="java")
    args = parser.parse_args()
    require(os.name == "nt", "windows_required")
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "muted": True,
              "mpv_sha256": hashlib.sha256(args.mpv.read_bytes()).hexdigest(),
              "platform": "Windows", "auth": "anonymous", "playback": [],
              "proxy_mode": args.proxy_mode}
    def save():
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    streams = []
    for name, resolver in (("bilibili", resolve_bilibili), ("netease", resolve_netease)):
        try:
            streams.append(resolver())
        except Exception as failure:
            report[name + "_resolve_error"] = type(failure).__name__
    try:
        streams.extend(resolve_youtube(args.newpipe_dir, report, args.java))
    except Exception as failure:
        report["youtube"] = {"success": False, "error": type(failure).__name__}
    save()
    print("Resolved inputs: " + str(len(streams)), flush=True)
    engine = None
    try:
        engine = Mpv(args.mpv, args.proxy_mode)
        report["mpv_version"] = engine.get("mpv-version")
        for stream in streams:
            try:
                result = validate_controls(engine, stream)
            except Exception as failure:
                result = {"source": stream["source"], "mime": stream["mime"], "passed": False,
                          "error_type": type(failure).__name__,
                          "step": str(failure) if isinstance(failure, ValidationFailure) else "runtime_error"}
            report["playback"].append(result)
            save()
            print(stream["source"] + " " + stream["mime"] + ": " + str(result.get("passed")), flush=True)
        if len(streams) >= 2:
            for stream in (streams[0], streams[1], streams[0]):
                engine.load(stream)
            engine.wait(lambda: (engine.get("time-pos") or 0) > 0.6, "rapid_replace")
            report["rapid_replace"] = {"last_source": streams[0]["source"],
                                        "identity_matches": engine.get("path") == streams[0]["url"],
                                        "playlist_count": engine.get("playlist-count")}
    except Exception as failure:
        report["engine_error"] = type(failure).__name__
    finally:
        if engine is not None:
            report["shutdown"] = engine.close()
            report["events"] = sorted(set(engine.events))
        save()
    # A second process must initialize the same actual output after normal shutdown.
    if streams and report.get("shutdown", {}).get("exit_code") == 0:
        restart = None
        try:
            restart = Mpv(args.mpv, args.proxy_mode)
            restart.load(streams[0])
            restart.wait(lambda: (restart.get("time-pos") or 0) > 0.6, "restart")
            report["restart_output"] = restart.get("current-ao")
        except Exception as failure:
            report["restart_error"] = type(failure).__name__
        finally:
            if restart is not None:
                report["restart_shutdown"] = restart.close()
    save()
    print("Report saved: " + str(args.output))


if __name__ == "__main__":
    main()
