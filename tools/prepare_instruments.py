#!/usr/bin/env python3
"""Render the shared static instrument faces and extract the supplied gold badge (Pillow)."""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res/drawable-nodpi'
FONT = ROOT / 'app/src/main/assets/fonts/instrument.ttf'
SIZE, START, SWEEP, MAX = 228, 138, 264, 180


def mix(a, b, t):
    t = max(0, min(t, 1))
    return tuple(round(x + (y - x) * t) for x, y in zip(a, b))


def face(night):
    # The polished rim, engraved outer track and centre lens never need to be redrawn.
    # They are rendered ahead of time; only the live needle and readout are composited at runtime.
    scale = 3
    half = SIZE / 2
    image = Image.new('RGBA', (SIZE * scale, SIZE * scale))
    pixels = image.load()
    rim_dark, rim_light = ((31, 44, 59), (178, 204, 215)) if night else ((135, 116, 102), (255, 251, 236))
    for y in range(image.height):
        dy = (y + .5) / scale - half
        for x in range(image.width):
            dx = (x + .5) / scale - half
            r = math.hypot(dx, dy)
            if r >= 114:
                continue
            phi = math.atan2(dy, dx)
            shine = .54 + .27 * math.cos(phi + 1.26) + .12 * math.cos(phi * 3 - .3)
            if r >= 111:
                color = (10, 16, 25) if night else (86, 58, 43)
                opacity = round((114 - r) / 3 * (105 if night else 49))
            elif r >= 100:
                # Glints follow the bevel, not a flat monochrome outline.
                bevel = (r - 100) / 11
                polish = shine + .32 * math.exp(-((bevel - .79) / .14) ** 2) - .40 * math.exp(-((bevel - .24) / .17) ** 2)
                color, opacity = mix(rim_dark, rim_light, polish), 255
            else:
                upper = (17, 39, 57) if night else (245, 250, 251)
                lower = (6, 14, 26) if night else (174, 197, 215)
                color = mix(upper, lower, (dy + 100) / 200)
                if r >= 61:
                    ridges = .013 + .048 * (1 + math.sin(phi * 260))
                    color = mix(color, (81, 141, 173) if night else (59, 94, 124), ridges)
                else:
                    color = mix(color, (11, 29, 44) if night else (238, 247, 251), .52)
                if 59.1 < r < 61:
                    color = mix(color, (120, 173, 201) if night else (71, 109, 140), .46)
                if 99 < r < 100:
                    color = mix(color, rim_dark, .62)
                reflection = max(0, 1 - abs(dy + 38 + dx * .42) / 33) * (.057 if night else .23)
                color = mix(color, (93, 179, 218) if night else (255, 255, 255), reflection)
                opacity = 255
            pixels[x, y] = (*color, opacity)
    ink = ImageDraw.Draw(image)
    font = ImageFont.truetype(str(FONT), round(10.7 * scale))
    major_color = (161, 227, 248, 255) if night else (31, 66, 92, 255)
    minor_color = (82, 130, 164, 255) if night else (68, 104, 130, 255)
    def polar(radius, angle):
        return ((half + radius * math.cos(angle)) * scale, (half + radius * math.sin(angle)) * scale)
    for speed in range(0, MAX + 1, 5):
        angle = math.radians(START + speed / MAX * SWEEP)
        major = speed % 30 == 0
        ink.line((polar(95, angle), polar(83 if major else 90, angle)), fill=major_color if major else minor_color,
                 width=(2 * scale if major else scale))
        if major:
            ink.text(polar(73, angle), str(speed), anchor='mm', font=font, fill=major_color)
    image.resize((SIZE * 2, SIZE * 2), Image.Resampling.LANCZOS).save(
        ROOT / 'shared' / ('dial-night.webp' if night else 'dial-day.webp'), 'WEBP', quality=92, method=6)


def badge(path):
    # Preserve the photographed metal. Remove the white matte (including the holes),
    # then unmix white from soft edge pixels so the image also works on a dark surface.
    rgb = Image.open(path).convert('RGB')
    cut = Image.new('RGBA', rgb.size)
    def extract(c):
        distance = 255 - min(c)
        alpha = max(0, min(255, round((distance - 5) * 255 / 37)))
        if not alpha:
            return (0, 0, 0, 0)
        clean = tuple(max(0, min(255, round(255 - (255 - channel) * 255 / alpha))) for channel in c)
        return (*clean, alpha)
    cut.putdata([extract(c) for c in rgb.getdata()])
    bounds = cut.getbbox()
    if not bounds:
        raise ValueError('No badge was found in the supplied image')
    cut = cut.crop(bounds)
    cut.thumbnail((360, 310), Image.Resampling.LANCZOS)
    RES.mkdir(parents=True, exist_ok=True)
    cut.save(RES / 'honda_badge.webp', 'WEBP', quality=96, method=6)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--badge', type=Path, help='Gold Honda emblem on a white background')
    args = parser.parse_args()
    face(False)
    face(True)
    if args.badge:
        badge(args.badge)
