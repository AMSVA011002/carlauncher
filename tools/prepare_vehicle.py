#!/usr/bin/env python3
"""Prepare aligned lights-off/on frames for the supplied day and night photographs."""
import argparse
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]


def illuminate(frame, *, day):
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

    if day:
        # Lens coordinates for the outdoor 1024×572 reference. Keep the
        # background and body unchanged, with restrained light in daylight.
        glow((221, 234, 255), 52, 7, ellipse=(762, 308, 819, 351))
        glow((219, 233, 255), 106, 1.5, polygon=[(724,310),(752,310),(780,312),(815,334),
                                                (806,345),(776,340),(743,331)])
        glow((240, 246, 255), 227, 1.3, polygon=[(772,318),(786,318),(799,325),(805,337),
                                                 (799,341),(783,338),(774,330)])
        glow((255, 255, 245), 244, 1.8, ellipse=(782, 323, 795, 334))
        glow((235, 245, 255), 152, 1, polygon=[(729,327),(775,342),(804,346),(800,348),(771,344)])
        glow((255, 217, 170), 84, 4, ellipse=(804, 406, 831, 431))
        glow((255, 244, 208), 222, 1.3, ellipse=(809, 410, 822, 424))
        glow((255, 253, 230), 240, 1, ellipse=(812, 413, 819, 421))
        glow((249, 55, 47), 124, 1.65, polygon=[(102,279),(119,278),(129,281),(119,290),(98,295)])
    else:
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
    parser.add_argument('source', type=Path, help='The matching reference image for this photo pair')
    parser.add_argument('--day', action='store_true', help='Use the supplied 1024×572 daylight image; leave the night pair unchanged')
    args = parser.parse_args()
    source = args.source.read_bytes()
    with Image.open(BytesIO(source)) as frame:
        size = (1024, 572) if args.day else (1024, 576)
        if frame.size != size or frame.n_frames != 1 or (not args.day and frame.format != 'WEBP'):
            parser.error(f'Expected the original {size[0]}×{size[1]} reference still; the light masks are registered to it.')
        lit = illuminate(frame, day=args.day)
        if args.day:
            frame.convert('RGB').save(ROOT / 'shared' / 'vezel-photo-day.webp', quality=94, method=6)
        else:
            (ROOT / 'shared' / 'vezel-photo.webp').write_bytes(source)
    name = 'vezel-photo-day-lit.webp' if args.day else 'vezel-photo-lit.webp'
    lit.save(ROOT / 'shared' / name, quality=94, method=6)


if __name__ == '__main__':
    main()
