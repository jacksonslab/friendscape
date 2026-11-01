"""Build the original gold-and-teal Bank Key model in OSRS's legacy model format."""
from pathlib import Path
import math
import struct

vertices, faces, colours = [], [], []


def prism(points, colour, depth=4):
    base = len(vertices)
    n = len(points)
    vertices.extend((x, y, z) for z in (-depth, depth) for x, y in points)
    for i in range(1, n - 1):
        faces.extend(((base, base + i + 1, base + i),
                      (base + n, base + n + i, base + n + i + 1)))
        colours.extend((colour, colour))
    for i in range(n):
        j = (i + 1) % n
        faces.extend(((base + i, base + j, base + n + j),
                      (base + i, base + n + j, base + n + i)))
        colours.extend((colour, colour))


GOLD = (8 << 10) | (6 << 7) | 70
TEAL = (29 << 10) | (6 << 7) | 60
# A hollow octagonal bow, long shaft, and two asymmetric teeth.
for i in range(8):
    a, b = 2 * math.pi * i / 8, 2 * math.pi * (i + 1) / 8
    prism([(round(r * math.cos(t)), round(-24 + r * math.sin(t)))
           for r, t in ((20, a), (20, b), (12, b), (12, a))], GOLD)
prism([(-4, -8), (4, -8), (4, 43), (-4, 43)], GOLD)
prism([(4, 23), (16, 23), (16, 30), (4, 30)], GOLD)
prism([(4, 36), (13, 36), (13, 43), (4, 43)], GOLD)
prism([(-5, -43), (5, -43), (5, -37), (-5, -37)], TEAL, 6)

ground_y = max(y for _, y, _ in vertices)
vertices = [(x, y - ground_y, z) for x, y, z in vertices]


def smart(value):
    return bytes([value + 64]) if -64 <= value < 64 else struct.pack('>H', value + 49152)


flags, xs, ys, zs, indices = (bytearray() for _ in range(5))
previous = (0, 0, 0)
for vertex in vertices:
    delta = [v - p for v, p in zip(vertex, previous)]
    flags.append(sum(1 << i for i, d in enumerate(delta) if d))
    for stream, d in zip((xs, ys, zs), delta):
        if d:
            stream.extend(smart(d))
    previous = vertex
last = 0
for a, b, c in faces:
    for value in (a - last, b - a, c - b):
        indices.extend(smart(value))
    last = c
footer = struct.pack('>HHBBBBBBHHHH', len(vertices), len(faces), 0, 0, 0, 0, 0, 0,
                     len(xs), len(ys), len(zs), len(indices))
data = (flags + bytes([1] * len(faces)) + indices +
        b''.join(struct.pack('>H', c) for c in colours) + xs + ys + zs + footer)
root = Path(__file__).resolve().parents[2]
target = root / 'content/other/bank-key/pack/src/main/resources/pack/models/friendscape_bank_key.dat'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(data)
print(f'{len(vertices)} vertices, {len(faces)} triangles, {len(data)} bytes: {target}')
