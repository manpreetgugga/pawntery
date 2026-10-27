#!/usr/bin/env python3
"""Synthesizes the game's sound effects into assets/sounds/ as 16-bit mono WAV files.

Standard library only, so it runs anywhere: python3 tools/gen_sounds.py
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets", "sounds")
rng = random.Random(7)


def n(seconds):
    return int(seconds * RATE)


def envelope(i, total, attack, release_curve=1.0):
    t = i / total
    a = min(1.0, t / attack) if attack > 0 else 1.0
    return a * (1.0 - t) ** release_curve


def lowpass(samples, cutoffs):
    """One-pole low-pass filter; cutoffs is a per-sample list of cutoff frequencies in Hz."""
    out, y = [], 0.0
    for x, fc in zip(samples, cutoffs):
        alpha = 1.0 - math.exp(-2.0 * math.pi * fc / RATE)
        y += alpha * (x - y)
        out.append(y)
    return out


def sweep(f0, f1, seconds, shape=lambda p: math.sin(2 * math.pi * p)):
    total, phase, out = n(seconds), 0.0, []
    for i in range(total):
        f = f0 + (f1 - f0) * (i / total)
        phase += f / RATE
        out.append(shape(phase % 1.0))
    return out


def noise(seconds):
    return [rng.uniform(-1, 1) for _ in range(n(seconds))]


def mix(*tracks):
    length = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(length)]


def apply_env(samples, attack, curve):
    total = len(samples)
    return [s * envelope(i, total, attack, curve) for i, s in enumerate(samples)]


def gain(samples, g):
    return [s * g for s in samples]


def write(name, samples, peak=0.85):
    top = max(abs(s) for s in samples) or 1.0
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(s / top * peak * 32767)) for s in samples))
    print("wrote", os.path.relpath(path))


def square(p):
    return 1.0 if p < 0.5 else -1.0


def soft_square(p):
    return math.tanh(3.0 * math.sin(2 * math.pi * p))


def throw():
    total = n(0.38)
    cut = [300 + 2600 * math.sin(math.pi * i / total) for i in range(total)]
    return apply_env(lowpass(noise(0.38), cut), 0.45, 1.2)


def splat():
    body = apply_env(lowpass(noise(0.22), [1800 - 7000 * i / n(0.22) if 1800 - 7000 * i / n(0.22) > 250 else 250 for i in range(n(0.22))]), 0.02, 2.0)
    thump = apply_env(sweep(170, 55, 0.18), 0.01, 2.5)
    return mix(body, gain(thump, 0.8))


def thud():
    knock = apply_env(sweep(150, 90, 0.2), 0.005, 3.0)
    click = apply_env(lowpass(noise(0.04), [2500] * n(0.04)), 0.0, 2.0)
    return mix(knock, gain(click, 0.5))


def bonk():
    tone = apply_env(sweep(720, 210, 0.28, soft_square), 0.01, 1.6)
    return mix(tone, gain(apply_env(noise(0.03), 0.0, 2.0), 0.3))


def pop():
    burst = apply_env(lowpass(noise(0.12), [5000] * n(0.12)), 0.0, 2.5)
    bloop = apply_env(sweep(260, 820, 0.22), 0.02, 1.5)
    drips = apply_env(lowpass(noise(0.35), [1200] * n(0.35)), 0.1, 2.0)
    return mix(burst, gain(bloop, 0.7), gain(drips, 0.35))


def place():
    return apply_env(sweep(420, 300, 0.12, soft_square), 0.005, 2.5)


def click():
    return apply_env(sweep(980, 900, 0.05, soft_square), 0.002, 2.0)


def win():
    notes = [523.25, 659.25, 783.99, 1046.5]
    out = []
    for k, f in enumerate(notes):
        length = 0.16 if k < len(notes) - 1 else 0.5
        out += apply_env(sweep(f, f, length, soft_square), 0.01, 1.4 if k < len(notes) - 1 else 1.0)
    return out


def boing():
    total = n(0.35)
    out, phase = [], 0.0
    for i in range(total):
        t = i / RATE
        f = 220 + 120 * math.sin(2 * math.pi * 14 * t) * (1 - i / total)
        phase += f / RATE
        out.append(math.sin(2 * math.pi * phase))
    return apply_env(out, 0.01, 1.3)


if __name__ == "__main__":
    write("throw.wav", throw(), 0.6)
    write("splat.wav", splat())
    write("thud.wav", thud(), 0.75)
    write("bonk.wav", bonk(), 0.7)
    write("pop.wav", pop())
    write("place.wav", place(), 0.6)
    write("click.wav", click(), 0.5)
    write("win.wav", win(), 0.6)
    write("boing.wav", boing(), 0.6)
