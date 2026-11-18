#!/usr/bin/env python3
"""Bake the two matched vehicle states from the Vezel studio source (requires Pillow)."""
import argparse
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageOps

ROOT = Path(__file__).resolve().parents[1]


def prepare(source):
    # Lamp and floor coordinates are registered to this particular studio composition.
    base = ImageOps.fit(Image.open(source).convert('RGB'), (1568, 882), Image.Resampling.LANCZOS)
    lit = base.convert('RGBA')

    def glow(x, y, rx, ry, color, opacity):
        size = (2 * rx, 2 * ry)
        layer = Image.new('RGBA', size, color + (0,))
        alpha = Image.new('L', size)
        pixels = alpha.load()
        for j in range(size[1]):
            for i in range(size[0]):
                q = ((i - rx) / rx) ** 2 + ((j - ry) / ry) ** 2
                if q < 1:
                    pixels[i, j] = round(opacity * (1 - q) ** 3)
        layer.putalpha(alpha)
        lit.alpha_composite(layer, (x - rx, y - ry))

    def soft_shape(points, color, opacity, blur):
        mask = Image.new('L', base.size)
        ImageDraw.Draw(mask).polygon(points, fill=opacity)
        layer = Image.new('RGBA', base.size, color + (0,))
        layer.putalpha(mask.filter(ImageFilter.GaussianBlur(blur)))
        lit.alpha_composite(layer)

    # Thin airborne streak and a wide falloff where the low beam meets the studio floor.
    soft_shape([(1210, 495), (1330, 520), (1568, 576), (1568, 719), (1420, 658), (1287, 560)],
               (208, 222, 255), 26, 25)
    glow(1557, 750, 282, 95, (213, 224, 255), 116)
    glow(1559, 760, 176, 44, (238, 242, 255), 67)
    glow(1215, 511, 127, 80, (249, 225, 196), 62)
    glow(1207, 506, 77, 52, (235, 237, 255), 167)
    soft_shape([(1143, 485), (1171, 487), (1214, 492), (1235, 500), (1244, 511), (1236, 521),
                (1207, 516), (1187, 510), (1170, 506)], (253, 245, 232), 225, 4)
    glow(1208, 505, 33, 23, (255, 255, 244), 245)
    glow(1169, 495, 49, 17, (252, 218, 177), 105)
    glow(172, 436, 72, 43, (250, 41, 32), 145)
    soft_shape([(154, 439), (166, 430), (197, 432), (192, 437), (166, 444), (152, 449)],
               (255, 67, 48), 226, 1.5)
    glow(161, 439, 19, 13, (255, 158, 85), 187)
    for name, image in [('off', base), ('on', lit.convert('RGB'))]:
        frame = ImageOps.fit(image, (864, 502), Image.Resampling.LANCZOS)
        frame.save(ROOT / 'shared' / f'vezel-{name}.jpg', quality=89, optimize=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source')
    prepare(parser.parse_args().source)
