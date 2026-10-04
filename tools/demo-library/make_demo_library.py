"""Generate the MusicMate demo library: original synthesized music, covers and tags.

Everything is made here (invented artists, generated audio and art), so screenshots of it can be
published freely. Needs numpy and mutagen, plus macOS afconvert and swift.

usage: python make_demo_library.py OUT_DIR
"""
import os
import subprocess
import sys
import wave

import numpy as np
from mutagen.flac import FLAC, Picture
from mutagen.mp4 import MP4, MP4Cover

HERE = os.path.dirname(os.path.abspath(__file__))

# (artist, album, genre, year, codec, bits, rate, dynamics, palette, tempo, tracks [(title, seconds)])
ALBUMS = [
    ("Aurora Lane", "Night Drive", "Electronic", 2024, "flac", 24, 96000, "loud", ("1A1446", "E94560"), 122,
     [("Neon Avenue", 192), ("Midnight Signal", 178), ("Afterglow", 205), ("Coastline Lights", 161)]),
    ("Kasem Quartet", "Strings at Dawn", "Classical", 2021, "flac", 24, 192000, "wide", ("2C3E50", "D4A373"), 72,
     [("Allegro Moderato", 125), ("Adagio Cantabile", 140)]),
    ("Mae Riverside", "Back Porch", "Folk", 2019, "flac", 16, 44100, "medium", ("6B4226", "F2C57C"), 96,
     [("Porch Light", 185), ("Slow River", 220), ("Home by Dusk", 172), ("Paper Boats", 198)]),
    ("The Lanterns", "City of Glass", "Rock", 2023, "alac", 24, 48000, "loud", ("0B0C10", "45A29E"), 132,
     [("Glass Towers", 210), ("Static Hearts", 182), ("Last Train Home", 224)]),
    ("Siam Breeze", "Monsoon Letters", "Luk Thung", 2022, "flac", 16, 44100, "medium", ("004E64", "F6AE2D"), 104,
     [("Rain on Tin Roofs", 190), ("Letters Home", 215), ("Rice Field Sunset", 175)]),
    ("Nova Jazz Trio", "Blue Hour Sessions", "Jazz", 2020, "flac", 24, 88200, "wide", ("0F2027", "2C5364"), 88,
     [("Blue Hour", 245), ("Brushes and Smoke", 200), ("Late Set", 230)]),
]

SCALE = np.array([0, 2, 4, 5, 7, 9, 11])
PROGRESSION = [0, 5, 3, 4]  # I vi IV V, as scale degrees


def midi_hz(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def tone(freq, n, rate, harmonics=(1.0, 0.5, 0.25, 0.12)):
    t = np.arange(n, dtype=np.float32) / rate
    out = np.zeros(n, dtype=np.float32)
    for k, amp in enumerate(harmonics, 1):
        if freq * k < rate / 2.2:
            out += amp * np.sin(2 * np.pi * freq * k * t, dtype=np.float32)
    return out


def envelope(n, rate, attack=0.01, release=0.3):
    env = np.ones(n, dtype=np.float32)
    a, r = min(n, int(attack * rate)), min(n, int(release * rate))
    if a: env[:a] = np.linspace(0, 1, a, dtype=np.float32)
    if r: env[-r:] *= np.linspace(1, 0, r, dtype=np.float32)
    return env


def synth_track(seconds, rate, tempo, dynamics, seed, percussive):
    rng = np.random.default_rng(seed)
    n = int(seconds * rate)
    mix = np.zeros((2, n), dtype=np.float32)
    root = 48 + int(rng.integers(0, 7))
    beat = int(rate * 60 / tempo)
    bar = beat * 4
    for start in range(0, n, bar):
        chord = PROGRESSION[(start // bar) % 4]
        length = min(bar, n - start)
        for i, voice in enumerate((0, 2, 4)):
            deg = chord + voice
            note = root + 12 + SCALE[deg % 7] + 12 * (deg // 7)
            pad = 0.10 * tone(midi_hz(note), length, rate) * envelope(length, rate, 0.4, 0.6)
            mix[i % 2, start:start + length] += pad
            mix[(i + 1) % 2, start:start + length] += 0.6 * pad
        for b in range(4):
            s = start + b * beat
            if s >= n: break
            m = min(beat, n - s)
            bass = 0.22 * tone(midi_hz(root - 12 + SCALE[chord % 7]), m, rate, (1.0, 0.3)) * envelope(m, rate, 0.005, 0.2)
            mix[:, s:s + m] += bass
            if percussive:
                k = min(int(0.25 * rate), m)
                t = np.arange(k, dtype=np.float32) / rate
                kick = 0.5 * np.sin(2 * np.pi * (50 + 90 * np.exp(-t * 30)) * t) * np.exp(-t * 12)
                mix[:, s:s + k] += kick.astype(np.float32)
                h = min(int(0.05 * rate), m)
                hat = 0.05 * rng.standard_normal(h).astype(np.float32) * np.linspace(1, 0, h, dtype=np.float32)
                off = s + beat // 2
                if off + h <= n: mix[:, off:off + h] += hat
    step = beat // 2
    degree = 4
    for s in range(0, n, step):
        if rng.random() < 0.3: continue
        degree = int(np.clip(degree + rng.integers(-2, 3), 0, 13))
        m = min(step * int(rng.integers(1, 3)), n - s)
        note = root + 24 + SCALE[degree % 7] + 12 * (degree // 7)
        lead = 0.12 * tone(midi_hz(note), m, rate, (1.0, 0.4, 0.2)) * envelope(m, rate, 0.01, 0.15)
        pan = rng.random()
        mix[0, s:s + m] += lead * (1 - pan)
        mix[1, s:s + m] += lead * pan

    t = np.linspace(0, 1, n, dtype=np.float32)
    if dynamics == "wide":
        # Quiet opening, swell to a climax, fade, with sparse loud accents: a wide dynamic range like an acoustic recording
        mix *= (0.35 + 0.65 * np.sin(np.pi * t) ** 2).astype(np.float32)
        for s in range(0, n, bar):
            if rng.random() < 0.35:
                k = min(int(0.6 * rate), n - s)
                hit = 0.25 * tone(midi_hz(root - 12), k, rate, (1.0, 0.5, 0.3)) * np.exp(-np.arange(k, dtype=np.float32) / rate * 6)
                mix[:, s:s + k] += hit
        peak = 0.89
    elif dynamics == "medium":
        mix *= (0.5 + 0.5 * np.sin(np.pi * t)).astype(np.float32)
        mix = np.tanh(1.5 * mix)
        peak = 0.94
    else:
        # Heavy limiting, like a loud modern master
        mix = np.tanh(2.6 * mix)
        peak = 0.98
    fade = min(n, int(2 * rate))
    mix[:, -fade:] *= np.linspace(1, 0, fade, dtype=np.float32)
    return mix * (peak / max(1e-9, float(np.abs(mix).max())))


def write_wav(path, mix, rate, bits):
    scale = 2 ** (bits - 1) - 1
    ints = np.ascontiguousarray(np.round(mix.T * scale).astype(np.int32))
    if bits == 16:
        data = ints.astype("<i2").tobytes()
    else:
        data = (ints.astype("<i4").view(np.uint8).reshape(-1, 4)[:, :3]).tobytes()
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(bits // 8)
        w.setframerate(rate)
        w.writeframes(data)


def main(out_dir):
    for a, (artist, album, genre, year, codec, bits, rate, dynamics, palette, tempo, tracks) in enumerate(ALBUMS):
        folder = os.path.join(out_dir, f"{artist} - {album}")
        os.makedirs(folder, exist_ok=True)
        cover = os.path.join(folder, "cover.jpg")
        subprocess.run(["swift", os.path.join(HERE, "cover.swift"), cover, album, artist, *palette, str(a + 7)], check=True)
        art = open(cover, "rb").read()
        for i, (title, seconds) in enumerate(tracks, 1):
            base = os.path.join(folder, f"{i:02d} {title}")
            wav = base + ".wav"
            write_wav(wav, synth_track(seconds, rate, tempo, dynamics, seed=a * 100 + i,
                                       percussive=genre in ("Electronic", "Rock", "Luk Thung")), rate, bits)
            if codec == "flac":
                path = base + ".flac"
                subprocess.run(["afconvert", "-f", "flac", "-d", "flac", wav, path], check=True)
                tags = FLAC(path)
                tags.update({"TITLE": title, "ARTIST": artist, "ALBUM": album, "ALBUMARTIST": artist,
                             "GENRE": genre, "DATE": str(year), "TRACKNUMBER": str(i), "TRACKTOTAL": str(len(tracks))})
                pic = Picture()
                pic.type, pic.mime, pic.width, pic.height, pic.depth, pic.data = 3, "image/jpeg", 1000, 1000, 24, art
                tags.add_picture(pic)
            else:
                path = base + ".m4a"
                subprocess.run(["afconvert", "-f", "m4af", "-d", "alac", wav, path], check=True)
                tags = MP4(path)
                tags.update({"\xa9nam": title, "\xa9ART": artist, "\xa9alb": album, "aART": artist,
                             "\xa9gen": genre, "\xa9day": str(year), "trkn": [(i, len(tracks))],
                             "covr": [MP4Cover(art, imageformat=MP4Cover.FORMAT_JPEG)]})
            tags.save()
            os.remove(wav)
            print(f"{os.path.relpath(path, out_dir)}  {bits}/{rate}  {dynamics}", flush=True)
        os.remove(cover)


if __name__ == "__main__":
    main(sys.argv[1])
