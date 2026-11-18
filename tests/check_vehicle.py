"""Validate the shared binary format and asset budgets without GPU or Pillow."""
import math
import struct
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / 'shared' / 'vehicle'
STRIDE = 28
mesh = (ASSETS / 'vezel.mesh').read_bytes()
magic, version, vertices, indices = struct.unpack_from('<4s3I', mesh)
start = 16 + vertices * STRIDE
assert (magic, version) == (b'VZ3D', 1), 'Shared mesh layout differs from the renderers'
assert 0 < indices <= 90_000 and indices % 3 == 0, 'Keep the model below 30K triangles'
assert 0 < vertices <= 24_000 and start + indices * 2 == len(mesh), 'Invalid mesh size'
assert max(i for i, in struct.iter_unpack('<H', mesh[start:])) < vertices, 'Invalid vertex index'
assert len(mesh) < 700_000, 'Keep the upload size small on 1 GB hardware'

front, rear, side, chassis, fog = 0, 0, 0, 0, 0
for vertex in struct.iter_unpack('<3f4b4B2H4B', mesh[16:start]):
    x, y, z = vertex[:3]
    assert all(math.isfinite(v) for v in (x, y, z)), 'Invalid position'
    if vertex[10] == 0:  # Floor, not part of the vehicle bounds.
        assert abs(y) < .02
    else:
        assert abs(x) < 2.35 and abs(z) < 1.05 and -.002 <= y < 1.70
        chassis += 1
    if vertex[-1] == 255:  # Decals live on the mesh, not over the screen.
        front += x > 2.12
        rear += x < -2.12
        side += abs(x) <= 2.12 and abs(z) > .82
    if 160 < vertex[-2] < 181:  # Fog lenses (warm white, independent from main/lamp materials).
        assert x > 2 and .36 < y < .48 and .60 < abs(z) < .74
        fog += 1
assert (front, rear, side) == (8, 24, 8) and chassis == vertices - 4
assert 40 < fog < 400, 'Both recessed fog lamps must be drawn'

atlas = (ASSETS / 'decals.png').read_bytes()
assert atlas[:8] == b'\x89PNG\r\n\x1a\n' and atlas[12:16] == b'IHDR'
assert struct.unpack_from('>2I2B', atlas, 16) == (512, 512, 8, 6), 'Use a mipmappable RGBA atlas'
assert len(atlas) < 90_000, 'Keep the decal atlas small'
assert not (ASSETS / 'local-decals.png').exists(), 'Never put the personal atlas in shared assets'
print('Vehicle mesh, decals and asset budgets passed.')
