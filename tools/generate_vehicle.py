#!/usr/bin/env python3
"""Rebuild the shared original mesh and the default decal atlas (Pillow + fontTools)."""
import argparse
import re
from pathlib import Path

from vehicle.decals import make_atlas
from vehicle.mesh import build_model

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'shared' / 'vehicle'
PERSONAL = ROOT / 'private-assets' / 'vehicle' / 'local-decals.png'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plate', help='Two blocks of uppercase letters or digits. Saved only to the Git-ignored local atlas.')
    parser.add_argument('--province', default='LK', help='Optional two-letter province marker for the private plates')
    args = parser.parse_args()
    logo = ROOT / 'shared' / 'honda-gold.svg'
    if args.plate:
        plate = ' '.join(args.plate.upper().split())
        province = args.province.strip().upper()
        if not re.fullmatch(r'[A-Z0-9]{1,6} [A-Z0-9]{1,6}', plate) or not re.fullmatch(r'[A-Z]{2}', province):
            parser.error('Plate must be two alphanumeric blocks and province must be two letters.')
        PERSONAL.parent.mkdir(parents=True, exist_ok=True)
        make_atlas(PERSONAL, logo, plate, province)
        print('Generated Git-ignored personal decal atlas.')
    else:
        OUT.mkdir(parents=True, exist_ok=True)
        build_model(OUT / 'vezel.mesh')
        make_atlas(OUT / 'decals.png', logo)


if __name__ == '__main__':
    main()
