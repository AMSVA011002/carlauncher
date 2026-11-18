#!/usr/bin/env python3
"""Preserve the supplied 1024×576 reference and bake a matching lights-on photograph."""
import argparse
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]


def illuminate(frame):
    lit = frame.convert('RGBA')

    def glow(color, opacity, blur, *, ellipse=None, polygon=None):
        mask = Image.new('L', lit.size)
        draw = ImageDraw.Draw(mask)
        if ellipse is not None:
            draw.ellipse(ellipse, fill=opacity)
        else:
            draw.polygon(polygon, fill=opacity)
        layer = Image.new('RGBA', lit.size, color + (0,))
        layer.putalpha(mask.filter(ImageFilter.GaussianBlur(blur)))
        lit.alpha_composite(layer)

    # The camera sees the near headlamp, fog lamp, and part of the rear lens.
    # These masks are registered to this photograph, not projected over the dashboard.
    glow((226, 231, 255), 63, 13, ellipse=(755, 311, 829, 357))
    glow((220, 232, 255), 105, 2.4, polygon=[(685,311),(734,315),(782,326),(808,339),
                                              (802,346),(761,342),(719,332)])
    glow((239, 245, 255), 229, 1.3, polygon=[(774,320),(788,319),(806,337),(798,341),(780,337),(772,330)])
    glow((255, 255, 247), 246, 2, ellipse=(781, 324, 794, 334))
    glow((219, 231, 255), 123, 1.5, polygon=[(735,334),(773,343),(805,346),(804,349),(767,345)])
    glow((255, 214, 162), 92, 6, ellipse=(801, 407, 832, 434))
    glow((255, 236, 197), 228, 1.3, ellipse=(809, 414, 822, 426))
    glow((255, 251, 223), 222, 1.15, ellipse=(811, 416, 818, 423))
    glow((246, 54, 30), 147, 2, polygon=[(96,290),(112,285),(129,281),(121,292),(104,296)])
    glow((236, 58, 30), 60, 8, ellipse=(94, 279, 130, 307))
    glow((201, 219, 255), 38, 16, polygon=[(929,420),(1018,421),(1024,469),(942,445)])
    glow((255, 206, 149), 67, 18, ellipse=(899, 477, 1050, 512))
    return lit.convert('RGB')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='The unaltered Wine Red reference webp')
    source = parser.parse_args().source.read_bytes()
    with Image.open(BytesIO(source)) as frame:
        if frame.format != 'WEBP' or frame.size != (1024, 576) or frame.n_frames != 1:
            parser.error('Expected the original 1024×576 reference WebP; the light masks are registered to it.')
        lit = illuminate(frame)
    (ROOT / 'shared' / 'vezel-photo.webp').write_bytes(source)
    lit.save(ROOT / 'shared' / 'vezel-photo-lit.webp', quality=94, method=6)


if __name__ == '__main__':
    main()
