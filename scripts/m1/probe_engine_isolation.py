"""Run the hash-pinned validation engine from a fresh folder and minimal PATH.

This is not a clean-machine installation test or approval to distribute this CI
binary. No registry, file associations, global environment or user config changes.
"""
import argparse
import hashlib
import json
import math
import os
import shutil
import struct
import sys
import tempfile
import wave
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
from validate_streaming import Mpv, ValidationFailure, require, resolve_bilibili, validate_controls


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mpv", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    report = {"checked_at": datetime.now(timezone.utc).isoformat(), "scope": "isolated_directory_on_current_windows_host", "distribution_approved": False, "muted": True}
    report["executable_sha256"] = hashlib.sha256(args.mpv.read_bytes()).hexdigest()
    require(report["executable_sha256"] == manifest["mpv_executable_sha256"], "engine_hash_mismatch")
    report["hash_matches"] = True
    engine = None
    try:
        with tempfile.TemporaryDirectory(prefix="rxplayer-engine-isolation-") as directory:
            root = Path(directory)
            executable = root / "mpv.exe"
            shutil.copyfile(args.mpv, executable)
            for name in ("temp", "appdata", "localappdata"):
                (root / name).mkdir()
            system_root = os.environ["SystemRoot"]
            environment = {"SystemRoot": system_root, "WINDIR": system_root,
                           "PATH": str(Path(system_root) / "System32"),
                           "TEMP": str(root / "temp"), "TMP": str(root / "temp"),
                           "APPDATA": str(root / "appdata"), "LOCALAPPDATA": str(root / "localappdata")}
            report["external_executables_on_path"] = {name: shutil.which(name, path=environment["PATH"]) is not None for name in ("mpv", "ffmpeg", "vlc")}
            require(not any(report["external_executables_on_path"].values()), "unexpected_path_dependency")
            tone = root / "original-tone.wav"
            sample_rate = 22050
            frame = b"".join(struct.pack("<h", int(1000 * math.sin(2 * math.pi * 440 * i / sample_rate))) for i in range(sample_rate))
            with wave.open(str(tone), "wb") as audio:
                audio.setnchannels(1)
                audio.setsampwidth(2)
                audio.setframerate(sample_rate)
                audio.writeframes(frame * 45)
            try:
                engine = Mpv(executable, environment=environment, working_directory=root)
                report["engine_version"] = engine.get("mpv-version")
                report["ffmpeg_version"] = engine.get("ffmpeg-version")
                report["local_playback"] = validate_controls(engine, {"source": "authored_wav", "id": "isolation-tone", "mime": "audio/wav", "url": str(tone), "headers": []})
                report["https_playback"] = validate_controls(engine, resolve_bilibili())
            finally:
                if engine is not None:
                    report["shutdown"] = engine.close()
                    engine = None
            require(report["shutdown"]["exit_code"] == 0 and report["shutdown"]["pipe_removed"], "engine_shutdown_failed")
            report["passed"] = True
    except Exception as failure:
        report["passed"] = False
        report["error"] = str(failure) if isinstance(failure, ValidationFailure) else type(failure).__name__
    finally:
        if engine is not None:
            report["shutdown"] = engine.close()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Engine isolation: " + str(report["passed"]))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
