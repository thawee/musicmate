#!/usr/bin/env python3
"""Rebuild bundled speech with Python's standard library; accepts downloaded OSR WAVs."""
import argparse
import hashlib
import math
from pathlib import Path
import struct
import wave

SOURCES = {
    "female": ("OSR_us_000_0010_8k.wav", "a4bf9becd046d7aedb6d05b6e12347a6294a44f74d263089c636fb0a2b1e6561", 0.45),
    "male": ("OSR_us_000_0030_8k.wav", "d3e1cfba98a44ddfc6e99508e663794c8dc41bdb2baced224f779c453143b622", 1.95),
}


def build(source_dir, output_dir):
    output_dir.mkdir(parents=True, exist_ok=True)
    for voice, (name, expected_hash, start) in SOURCES.items():
        source = source_dir / name
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected_hash:
            raise ValueError(f"Unexpected source SHA-256: {source}")
        with wave.open(str(source), "rb") as recording:
            if (recording.getnchannels(), recording.getsampwidth(), recording.getframerate()) != (1, 2, 8000):
                raise ValueError(f"Expected mono PCM16 8 kHz: {source}")
            recording.setpos(round(start * 8000))
            raw = recording.readframes(24000)
        samples = struct.unpack("<24000h", raw)
        # Six-phase, 32-tap Hann-windowed sinc interpolation. Upsampling does not add
        # bandwidth beyond the original recording's 4 kHz Nyquist limit.
        phases = []
        for phase in range(6):
            weights = []
            for offset in range(-15, 17):
                distance = phase / 6 - offset
                sinc = 1 if distance == 0 else math.sin(math.pi * distance) / (math.pi * distance)
                weights.append(sinc * (0.5 + 0.5 * math.cos(math.pi * distance / 16)) if abs(distance) < 16 else 0)
            total = sum(weights)
            phases.append([weight / total for weight in weights])
        pcm = []
        for frame in range(144000):
            center, phase = divmod(frame, 6)
            value = sum(samples[max(0, min(23999, center + offset))] * weight
                        for offset, weight in zip(range(-15, 17), phases[phase]))
            pcm.append(value * min(1, frame / 960, (143999 - frame) / 960))
        peak = max(abs(value) for value in pcm)
        encoded = struct.pack("<144000h", *(round(value * (0.85 * 32767 / peak)) for value in pcm))
        destination = output_dir / f"{voice}_voice.pcm"
        destination.write_bytes(encoded)
        print(f"{destination.name}: {len(encoded)} bytes, sha256 {hashlib.sha256(encoded).hexdigest()}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_dir", type=Path, help="Directory containing both unmodified OSR WAVs")
    parser.add_argument("--output-dir", type=Path,
                        default=Path(__file__).resolve().parents[1] / "app/src/main/assets/listening")
    options = parser.parse_args()
    build(options.source_dir, options.output_dir)
