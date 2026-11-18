#!/usr/bin/env python3
"""Prefill a registration label for local, personal APK builds only."""
import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plate', required=True, help='Two blocks, e.g. ABC 0123')
    parser.add_argument('--province', default='', help='Optional two-letter province prefix')
    args = parser.parse_args()
    plate = ' '.join(args.plate.upper().split())
    province = args.province.strip().upper()
    if not re.fullmatch(r'[A-Z0-9]{1,6} [A-Z0-9]{1,6}', plate) or not re.fullmatch(r'(?:[A-Z]{2})?', province):
        parser.error('Enter two registration blocks and an optional two-letter province.')
    output = ROOT / 'private-assets' / 'photo' / 'vehicle' / 'registration.txt'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(f'{province} {plate}'.strip() + '\n', encoding='ascii')
    print('Prepared the Git-ignored registration label.')


if __name__ == '__main__':
    main()
