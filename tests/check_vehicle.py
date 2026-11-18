"""Check both offline vehicle photo pairs without Pillow or a graphics runtime."""
from pathlib import Path
import struct

SHARED = Path(__file__).resolve().parents[1] / 'shared'


def pixels(content):
    assert content[:4] == b'RIFF' and content[8:12] == b'WEBP', 'Expected a WebP still'
    assert struct.unpack_from('<I', content, 4)[0] + 8 == len(content), 'Incomplete WebP'
    tag = content[12:16]
    if tag == b'VP8X':
        assert not content[20] & 2, 'Expected a still, not an animation'
        return tuple(int.from_bytes(content[i:i + 3], 'little') + 1 for i in (24, 27))
    if tag == b'VP8L':
        assert content[20] == 0x2f
        bits = struct.unpack_from('<I', content, 21)[0]
        return (bits & 0x3fff) + 1, ((bits >> 14) & 0x3fff) + 1
    assert tag == b'VP8 ' and content[23:26] == b'\x9d\x01\x2a'
    return tuple(n & 0x3fff for n in struct.unpack_from('<HH', content, 26))


for label, stem, height, budget in [('Day', 'vezel-photo-day', 572, 220_000), ('Night', 'vezel-photo', 576, 150_000)]:
    source = (SHARED / f'{stem}.webp').read_bytes()
    lit = (SHARED / f'{stem}-lit.webp').read_bytes()
    assert pixels(source) == pixels(lit) == (1024, height), f'{label} lights must use the same uncropped composition'
    assert source != lit, f'{label} lights need a distinct frame'
    assert len(source) + len(lit) < budget, f'Keep the {label.lower()} photo assets lightweight'
assert not list(SHARED.rglob('registration.txt')), 'The default build must not include a personal registration'
assert not list(SHARED.rglob('*.mesh')), 'Photo edition does not need a 3D asset'
print('Day and night reference photos, lighting states and asset budgets passed.')
