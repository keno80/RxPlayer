"""Generate three original, quiet PCM loops using only Python's standard library."""

import math
import struct
import wave
from pathlib import Path

OUTPUT = Path(__file__).resolve().parents[1] / "composeApp/src/commonMain/composeResources/files"
SAMPLE_RATE = 22050
DURATION = 16
MELODIES = {
    "night": [60, 64, 67, 71, 67, 64, 62, 59],
    "daylight": [65, 69, 72, 76, 74, 72, 69, 67],
    "orbit": [57, 64, 69, 72, 71, 67, 64, 60],
}


def generate(name: str, notes: list[int]) -> None:
    frames = bytearray()
    for index in range(SAMPLE_RATE * DURATION):
        time = index / SAMPLE_RATE
        beat = int(time / .5)
        elapsed = time % .5
        frequency = 440 * 2 ** ((notes[beat % len(notes)] - 69) / 12)
        envelope = min(elapsed / .015, 1) * math.exp(-elapsed * 5)
        melody = math.sin(2 * math.pi * frequency * elapsed)
        melody += .22 * math.sin(4 * math.pi * frequency * elapsed)
        bass = math.sin(2 * math.pi * 130.8128 * time) * .12
        fade = min(time / .1, 1, (DURATION - time) / .3)
        sample = (melody * envelope * .22 + bass) * fade
        frames.extend(struct.pack("<h", round(sample * 32767)))
    with wave.open(str(OUTPUT / f"{name}.wav"), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(SAMPLE_RATE)
        audio.writeframes(frames)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for name, notes in MELODIES.items():
        generate(name, notes)
        print(f"Generated {name}.wav: {DURATION}s, mono PCM16, {SAMPLE_RATE}Hz")
