"""Check the two offline photo assets without Pillow or a graphics runtime."""
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


source = (SHARED / 'vezel-photo.webp').read_bytes()
lit = (SHARED / 'vezel-photo-lit.webp').read_bytes()
assert pixels(source) == pixels(lit) == (1024, 576), 'Lighting states must use the same uncropped composition'
assert source != lit, 'Lights need a distinct frame'
assert len(source) + len(lit) < 150_000, 'Keep the photo assets lightweight'
assert not list(SHARED.rglob('registration.txt')), 'The default build must not include a personal registration'
assert not list(SHARED.rglob('*.mesh')), 'Photo edition does not need a 3D asset'
print('Reference photo, lighting states and asset budgets passed.')
