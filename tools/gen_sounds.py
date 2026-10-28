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


def voice(f0_curve, seconds, cutoff_curve, vibrato=0.0, breath=0.0):
    """A cartoon animal voice: a buzzy pitched tone through a moving low-pass filter."""
    total = n(seconds)
    phase, raw, cut = 0.0, [], []
    for i in range(total):
        p = i / total
        f = f0_curve(p) * (1 + vibrato * math.sin(2 * math.pi * 5.5 * i / RATE))
        phase += f / RATE
        saw = 2 * (phase % 1.0) - 1
        raw.append(saw + breath * rng.uniform(-1, 1))
        cut.append(cutoff_curve(p))
    return lowpass(lowpass(raw, cut), cut)


def woof():
    one = voice(lambda p: 330 - 140 * p, 0.17, lambda p: 1500 - 900 * p, breath=0.35)
    one = apply_env(one, 0.06, 1.6)
    gap = [0.0] * n(0.05)
    two = apply_env(voice(lambda p: 300 - 120 * p, 0.15, lambda p: 1300 - 800 * p, breath=0.35), 0.06, 1.6)
    return one + gap + gain(two, 0.8)


def meow():
    def f0(p):
        return 480 + 220 * math.sin(math.pi * min(1.0, p * 1.4)) - 120 * max(0.0, p - 0.6)

    def cutoff(p):
        return 700 + 2400 * math.sin(math.pi * min(1.0, p * 1.2))

    return apply_env(voice(f0, 0.5, cutoff, vibrato=0.02), 0.15, 0.8)


def squeak(base):
    return apply_env(sweep(base, base * 1.6, 0.16, soft_square), 0.02, 1.2)


def chirp():
    return squeak(1500) + [0.0] * n(0.04) + squeak(1800)


def place_at(track, clip, seconds, g=1.0):
    """Adds [clip] into [track] starting at [seconds], growing the track if needed."""
    start = n(seconds)
    if len(track) < start + len(clip):
        track.extend([0.0] * (start + len(clip) - len(track)))
    for i, s in enumerate(clip):
        track[start + i] += s * g


def crash():
    """Cardboard and wood giving way: a thump, a woody knock and a run of cracks."""
    out = apply_env(sweep(150, 55, 0.3), 0.005, 2.4)
    place_at(out, apply_env(sweep(420, 300, 0.1, soft_square), 0.002, 2.5), 0.0, 0.5)
    for k in range(7):
        crack = apply_env(lowpass(noise(0.045), [2600 + rng.uniform(-900, 900)] * n(0.045)), 0.0, 3.0)
        place_at(out, crack, rng.uniform(0.0, 0.22), 0.9 - k * 0.08)
    return out


def shatter():
    """A flower pot or gnome breaking: a crack, then bits of clay tinkling down."""
    total = n(0.65)
    bright = noise(0.65)
    dull = lowpass(bright, [1200] * total)
    out = apply_env([b - d for b, d in zip(bright, dull)], 0.0, 4.0)
    for k in range(9):
        f = rng.uniform(2200, 4800)
        ping = apply_env(sweep(f, f * 0.97, 0.09), 0.001, 3.0)
        place_at(out, ping, 0.04 + k * 0.05 + rng.uniform(0, 0.03), 0.45 - k * 0.035)
    return out


def kaboom():
    """The paint bomb: a deep cartoon boom with a whoosh of air."""
    total = n(1.0)
    boom = apply_env(sweep(95, 32, 1.0), 0.004, 2.2)
    rush = apply_env(lowpass(noise(1.0), [max(160, 3200 * (1 - i / total) ** 2) for i in range(total)]), 0.0, 1.6)
    return mix(boom, gain(rush, 0.9))


def crackle():
    """A firework: a quick whistle, then a shower of crackles."""
    out = apply_env(sweep(2300, 1500, 0.12), 0.01, 1.0)
    out = gain(out, 0.35)
    for k in range(26):
        click = apply_env(lowpass(noise(0.018), [6000] * n(0.018)), 0.0, 2.0)
        t = 0.1 + (k / 26) ** 1.4 * 0.7 + rng.uniform(0, 0.02)
        place_at(out, click, t, 1.0 - k * 0.03)
    return out


def powerup():
    """A bright rising chime."""
    out = []
    for f in (660, 880, 1320):
        out += apply_env(sweep(f, f * 1.02, 0.07, soft_square), 0.004, 1.2)
    tail = apply_env(sweep(1760, 1800, 0.22), 0.01, 1.4)
    return out + gain(tail, 0.6)


def squish():
    """A watermelon bursting: a wet, wobbly blorp."""
    total = n(0.4)
    wet = lowpass(noise(0.4), [700 + 600 * math.sin(2 * math.pi * 11 * i / RATE) for i in range(total)])
    blorp = apply_env(sweep(230, 85, 0.3), 0.01, 2.0)
    return mix(apply_env(wet, 0.02, 1.8), gain(blorp, 0.9))


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
    write("woof.wav", woof(), 0.75)
    write("meow.wav", meow(), 0.7)
    write("squeak.wav", squeak(950), 0.55)
    write("chirp.wav", chirp(), 0.55)
    write("crash.wav", crash(), 0.75)
    write("shatter.wav", shatter(), 0.6)
    write("kaboom.wav", kaboom(), 0.9)
    write("crackle.wav", crackle(), 0.6)
    write("powerup.wav", powerup(), 0.5)
    write("squish.wav", squish(), 0.75)
