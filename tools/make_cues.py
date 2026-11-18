#!/usr/bin/env python3
"""Generate the small original Welcome and Good Bye assets; no audio dependencies."""
import math
import struct
import wave
from pathlib import Path

RATE = 16000
OUT = Path(__file__).resolve().parents[1] / 'shared' / 'audio'


def cue(name, length, keys, chord):
    samples = [0.0] * round(length * RATE)

    def pluck(note, start, volume, decay, bright):
        hz = 440 * 2 ** ((note-69) / 12)
        for i in range(round(start*RATE), len(samples)):
            t = i/RATE - start
            phase = math.tau * hz * t
            envelope = (1 - math.exp(-95*t)) * math.exp(-decay*t)
            samples[i] += volume * envelope * (math.sin(phase) + .22*bright*math.sin(2*phase) + .11*bright*math.sin(3*phase))

    for i, (note, at, level) in enumerate(keys):
        pluck(note, at, level, 3.8, 1)
        pluck(note, at+.105, level*.19, 4.2, .7)
    for i, note in enumerate(chord):
        pluck(note, .04 + i*.075, .11, 2.5, .25)
    peak = max(abs(v) for v in samples)
    scale = 32767 * .27 / peak
    with wave.open(str(OUT / f'{name}.wav'), 'wb') as wav:
        wav.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
        pcm = [int(v * scale * min(1, (length - i / RATE) / .28)) for i, v in enumerate(samples)]
        wav.writeframes(struct.pack(f'<{len(pcm)}h', *pcm))


if __name__ == '__main__':
    OUT.mkdir(parents=True, exist_ok=True)
    cue('welcome', 2.20, [(62,.01,.57),(69,.21,.54),(74,.43,.48),(78,.66,.42),(81,.90,.30)], [50,57,64])
    cue('goodbye', 2.48, [(78,.02,.47),(74,.24,.51),(69,.47,.48),(64,.70,.43),(62,.98,.48)], [50,57,66])
