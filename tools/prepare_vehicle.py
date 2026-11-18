#!/usr/bin/env python3
"""Pack matching unlit/lit captures of the generic 3D showroom for the 2D fallback."""
import argparse
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('unlit', help='Unlit showroom with sample plates, without dashboard controls')
    parser.add_argument('lit', help='Same angle with headlights and fog lights on')
    args = parser.parse_args()
    for state, source in [('off', args.unlit), ('on', args.lit)]:
        with Image.open(source) as image:
            frame = ImageOps.fit(image.convert('RGB'), (864,488), Image.Resampling.LANCZOS)
        frame.save(ROOT / 'shared' / f'vezel-{state}.jpg', quality=89, optimize=True, progressive=True)


if __name__ == '__main__':
    main()
