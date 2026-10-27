#!/usr/bin/env python3
"""Synthesizes the two music loops into assets/music/ (menu.ogg, match.ogg).

Needs numpy, and ffmpeg on the PATH to encode OGG Vorbis (without ffmpeg it leaves WAV files,
which the game can't use until they are converted). Run: python3 tools/gen_music.py

Both loops are seamless: the tail of the last notes is wrapped back onto the start.
"""
import os
import shutil
import subprocess
import wave

import numpy as np

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "music")
rng = np.random.default_rng(11)


def hz(midi):
    return 440.0 * 2 ** ((midi - 69) / 12)


def pluck(midi, seconds, brightness=0.5):
    """Karplus-Strong plucked string: a soft ukulele-like note."""
    n = int(seconds * RATE)
    period = max(2, int(RATE / hz(midi)))
    buf = rng.uniform(-1, 1, period)
    # Soften the initial burst so the attack isn't harsh.
    for _ in range(2):
        buf = 0.5 * (buf + np.roll(buf, 1))
    out = np.empty(n)
    decay = 0.996 - (1 - brightness) * 0.004
    for i in range(n):
        j = i % period
        out[i] = buf[j]
        buf[j] = decay * 0.5 * (buf[j] + buf[(j + 1) % period])
    return out


def marimba(midi, seconds):
    t = np.arange(int(seconds * RATE)) / RATE
    f = hz(midi)
    tone = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t * 30)
    return tone * np.exp(-t * 5.5) * np.minimum(1, t * 400)


def bass(midi, seconds):
    t = np.arange(int(seconds * RATE)) / RATE
    phase = (hz(midi) * t) % 1.0
    tri = 4 * np.abs(phase - 0.5) - 1
    return tri * np.exp(-t * 3.0) * np.minimum(1, t * 300)


def kick(seconds=0.22):
    t = np.arange(int(seconds * RATE)) / RATE
    f = 50 + 70 * np.exp(-t * 30)
    return np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-t * 14)


def clap(seconds=0.16):
    t = np.arange(int(seconds * RATE)) / RATE
    noise = rng.uniform(-1, 1, t.size)
    noise = noise - np.concatenate(([0], noise[:-1])) * 0.6  # a little brighter
    return noise * np.exp(-t * 28)


def shaker(seconds=0.06):
    t = np.arange(int(seconds * RATE)) / RATE
    noise = rng.uniform(-1, 1, t.size)
    noise = noise - np.concatenate(([0], noise[:-1]))
    return noise * np.exp(-t * 70)


def chord_notes(root, quality):
    third = 3 if quality == "m" else 4
    return [root, root + third, root + 7]


class Track:
    def __init__(self, bpm, bars):
        self.beat = 60.0 / bpm
        self.bar = 4 * self.beat
        self.length = int(bars * self.bar * RATE)
        self.mix = np.zeros(self.length + 3 * RATE)

    def add(self, sound, at_beats, gain):
        start = int(at_beats * self.beat * RATE)
        self.mix[start:start + sound.size] += sound * gain

    def render(self):
        # Wrap the tail onto the start so the loop is seamless.
        out = self.mix[:self.length].copy()
        tail = self.mix[self.length:]
        out[:tail.size] += tail
        out = np.tanh(out * 1.3)
        return out / np.max(np.abs(out)) * 0.85


def match_track():
    """Upbeat: 112 BPM in C major, plucked chords, marimba tune, bass and light drums."""
    bars = 16
    t = Track(112, bars)
    prog = [(60, ""), (55, ""), (57, "m"), (53, ""), (60, ""), (55, ""), (53, ""), (55, "")]
    melody_a = [
        [76, None, 79, None, 76, 74, 72, None], [74, None, 67, None, 71, None, 74, None],
        [72, None, 76, None, 81, None, 79, 76], [77, None, 76, None, 72, None, 69, None],
        [79, None, 76, None, 72, 76, 79, None], [83, None, 81, None, 79, None, 74, None],
        [81, 79, 77, None, 72, None, 69, None], [71, None, 74, None, 79, None, None, None],
    ]
    melody_b = [
        [84, None, 79, None, 76, None, 79, None], [83, None, 79, None, 74, None, 79, None],
        [81, None, 76, None, 72, None, 76, None], [77, None, 81, None, 84, None, 81, None],
    ] + melody_a[4:]
    for b in range(bars):
        root, q = prog[b % 8]
        beat0 = b * 4
        notes = chord_notes(root, q)
        for off in (1.5, 3.5):  # off-beat strums
            for k, n in enumerate(notes):
                t.add(pluck(n, 0.6, 0.6), beat0 + off + k * 0.012, 0.16)
        for off, octave in ((0, -12), (2, -12), (2.5, 0)):
            t.add(bass(root + octave - 12, 0.5), beat0 + off, 0.42)
        t.add(kick(), beat0, 0.5)
        t.add(kick(), beat0 + 2, 0.45)
        t.add(clap(), beat0 + 1, 0.1)
        t.add(clap(), beat0 + 3, 0.1)
        for e in range(8):
            t.add(shaker(), beat0 + e * 0.5, 0.035 if e % 2 else 0.022)
        line = (melody_a if b < 8 else melody_b)[b % 8]
        for e, n in enumerate(line):
            if n is not None:
                t.add(marimba(n, 0.9), beat0 + e * 0.5, 0.34)
    return t.render()


def menu_track():
    """Gentle: 96 BPM in F major, ukulele arpeggios, a sparse marimba tune and soft bass."""
    bars = 16
    t = Track(96, bars)
    prog = [(53, ""), (48, ""), (50, "m"), (46, ""), (53, ""), (48, ""), (46, ""), (48, "")]
    melody = [
        [69, None, 72, None, 77, None, None, None], [76, None, 74, None, 72, None, None, None],
        [74, None, 77, None, 81, None, None, None], [79, None, 77, None, 74, None, None, None],
        [72, None, 77, None, 81, None, 84, None], [79, None, 76, None, 72, None, None, None],
        [74, None, 77, None, 82, None, 81, None], [79, None, None, None, 76, None, None, None],
    ]
    for b in range(bars):
        root, q = prog[b % 8]
        beat0 = b * 4
        r, third, fifth = chord_notes(root + 12, q)
        for e, n in enumerate([r, third, fifth, third, r + 12, fifth, third, fifth]):
            t.add(pluck(n, 0.8, 0.45), beat0 + e * 0.5, 0.2)
        t.add(bass(root - 12, 1.2), beat0, 0.4)
        t.add(bass(root - 12, 1.0), beat0 + 2, 0.3)
        # The tune rests for the first four bars of the second half, so the loop breathes.
        if b < 8 or b >= 12:
            for e, n in enumerate(melody[b % 8]):
                if n is not None:
                    t.add(marimba(n, 1.2), beat0 + e * 0.5, 0.3)
    return t.render()


def save(name, samples):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((samples * 32767).astype("<i2").tobytes())
    if shutil.which("ffmpeg"):
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "3", ogg], check=True)
        os.remove(wav)
        print("wrote", os.path.relpath(ogg))
    else:
        print("wrote", os.path.relpath(wav), "(install ffmpeg to encode OGG)")


if __name__ == "__main__":
    save("match", match_track())
    save("menu", menu_track())
