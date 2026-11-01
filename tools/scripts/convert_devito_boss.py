"""Run with Blender: blender -b devito_boss.blend -P this.py -- --output PACK_DIR.

Bakes the supplied weighted FK mesh to OSRS legacy transform groups. Each distinct
weight combination gets a fitted rigid transform; the report measures the resulting
deformation and angle/integer quantization error against Blender's evaluated mesh.
"""
import argparse
import colorsys
import itertools
import json
import math
from pathlib import Path
import struct
import sys

import bpy
import numpy as np
from mathutils import Matrix


SCALE = 256
SKELETON = 63070
ARCHIVE = 30000
CLIPS = [('Idle', 49, 100, True), ('Walk', 25, 50, True),
         ('Attack', 33, 67, False), ('Death', 61, 120, False), ('Kick', 35, 70, False)]


def smart(n):
    n = int(n)
    if not -16384 <= n < 16384:
        raise ValueError(f'Signed smart out of range: {n}')
    return bytes([n + 64]) if -64 <= n < 64 else struct.pack('>H', n + 49152)


def encode_model(vertices, faces, colors, groups):
    flags, xs, ys, zs, indices = (bytearray() for _ in range(5))
    previous = (0, 0, 0)
    for vertex in vertices:
        delta = [int(v) - int(p) for v, p in zip(vertex, previous)]
        flags.append(sum(1 << i for i, d in enumerate(delta) if d))
        for stream, d in zip((xs, ys, zs), delta):
            if d:
                stream.extend(smart(d))
        previous = vertex
    last = 0
    for a, b, c in faces:
        for d in (a - last, b - a, c - b):
            indices.extend(smart(d))
        last = c
    footer = struct.pack('>HHBBBBBBHHHH', len(vertices), len(faces), 0,
                         1, 0, 0, 1, 1, len(xs), len(ys), len(zs), len(indices))
    return (flags + bytes([1] * len(faces)) + bytes(len(faces)) +
            bytes([1] * len(faces)) + bytes(groups) + indices +
            b''.join(struct.pack('>H', c) for c in colors) + xs + ys + zs + footer)


def color(rgb):
    srgb = [12.92 * c if c <= .0031308 else 1.055 * c ** (1 / 2.4) - .055 for c in rgb]
    h, l, s = colorsys.rgb_to_hls(*srgb)
    if l > .95:
        s /= 16
    elif l > .9:
        s /= 8
    elif l > .85:
        s /= 4
    elif l > .7:
        s /= 2
    return min(63, int(h * 64)) << 10 | min(7, int(s * 8)) << 7 | min(127, int(l * 128))


SIN = np.array([int(math.sin(i * math.tau / 2048) * 65536) for i in range(2048)])
COS = np.array([int(math.cos(i * math.tau / 2048) * 65536) for i in range(2048)])


def rotate(v, angles):
    v = v.copy().astype(np.int64)
    for axis, value in ((2, angles[2]), (0, angles[0]), (1, angles[1])):
        s, c = SIN[(int(value) & 255) * 8], COS[(int(value) & 255) * 8]
        x, y, z = v.T.copy()
        if axis == 2:
            v[:, 0], v[:, 1] = (y * s + x * c) >> 16, (y * c - x * s) >> 16
        elif axis == 0:
            v[:, 1], v[:, 2] = (y * c - z * s) >> 16, (y * s + z * c) >> 16
        else:
            v[:, 0], v[:, 2] = (z * s + x * c) >> 16, (z * c - x * s) >> 16
    return v


def fit(source, target, rotation):
    u, _, vt = np.linalg.svd(rotation)
    r = u @ vt
    if np.linalg.det(r) < 0:
        u[:, -1] *= -1
        r = u @ vt
    euler = Matrix(r.tolist()).to_euler('ZXY')
    guess = np.rint(np.array([euler.x, euler.y, -euler.z]) * 256 / math.tau).astype(int)
    best = None
    for delta in itertools.product((-1, 0, 1), repeat=3):
        angles = (guess + delta) % 256
        rotated = rotate(source, angles)
        translation = np.rint(np.mean(target - rotated, axis=0)).astype(int)
        posed = rotated + translation
        error = np.sum((posed - target) ** 2)
        if best is None or error < best[0]:
            best = error, angles, translation, posed
    return best[1:]


def main():
    if not 0 <= ARCHIVE < 32768:
        raise ValueError('Legacy frame archive must fit the client signed frame-ID lookup')
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args(sys.argv[sys.argv.index('--') + 1:])
    out = args.output
    for directory in ('models', 'animations', 'configs'):
        (out / directory).mkdir(parents=True, exist_ok=True)
    mesh, rig = bpy.data.objects['Boss_Mesh'], bpy.data.objects['Boss_Rig']
    data = mesh.data
    data.calc_loop_triangles()
    weights = [tuple(sorted((mesh.vertex_groups[g.group].name, round(g.weight, 6))
                            for g in v.groups if g.weight > 0)) for v in data.vertices]
    combinations = list(dict.fromkeys(weights))
    labels = [combinations.index(w) for w in weights]
    count = 2 + len(combinations) * 2
    if count > 255:
        raise ValueError(f'{count} transforms exceed legacy limit')
    groups = [np.where(np.array(labels) == i)[0] for i in range(len(combinations))]
    basis = np.array([[-1, 0, 0], [0, 0, -1], [0, 1, 0]])
    source = np.array([v.co[:] for v in data.vertices]) @ basis.T * SCALE
    vertices = np.rint(source).astype(int)
    faces = [(t.vertices[0], t.vertices[2], t.vertices[1]) for t in data.loop_triangles]
    palette = [color(m.diffuse_color[:3]) for m in data.materials]
    colors = [palette[t.material_index] for t in data.loop_triangles]
    (out / 'models/devito_boss.dat').write_bytes(encode_model(vertices, faces, colors, labels))
    types = bytes([0] + [x for _ in combinations for x in (2, 1)] + [5])
    lengths = bytes([0] + [1] * (count - 1))
    group_ids = bytes(i for i in range(len(combinations)) for _ in range(2)) + bytes([0])
    (out / 'animations/devito_boss.skeleton').write_bytes(bytes([count]) + types + lengths + group_ids)
    frames, config, report = [], [], {'scale': SCALE, 'vertices': len(vertices),
        'triangles': len(faces), 'weight_groups': len(combinations), 'transforms': count, 'clips': {}}
    for track in rig.animation_data.nla_tracks:
        track.mute = True
    for name, end, duration, loop in CLIPS:
        rig.animation_data.action = bpy.data.actions[name]
        action = rig.animation_data.action
        if action.slots:
            rig.animation_data.action_slot = action.slots[0]
        n = math.ceil(duration / 2)
        delays = [2] * n
        delays[-1] = duration - 2 * (n - 1)
        ids, errors = [], []
        for i in range(n):
            # Include the final pose of one-shot actions; loops omit their duplicate endpoint.
            fraction = i / n if loop else i / (n - 1)
            frame = 1 + (end - 1) * fraction
            bpy.context.scene.frame_set(int(frame), subframe=frame % 1)
            bpy.context.view_layer.update()
            evaluated = mesh.evaluated_get(bpy.context.evaluated_depsgraph_get())
            posed_mesh = evaluated.to_mesh()
            target = np.array([v.co[:] for v in posed_mesh.vertices]) @ basis.T * SCALE
            evaluated.to_mesh_clear()
            matrices = {b.name: basis @ np.array((rig.pose.bones[b.name].matrix @ b.matrix_local.inverted()).to_3x3()) @ basis.T
                        for b in rig.data.bones}
            flags, values = bytearray([1]), bytearray(smart(0))
            native = vertices.copy()
            for indices, combination in zip(groups, combinations):
                rotation = sum(matrices[b] * weight for b, weight in combination)
                angles, translation, result = fit(vertices[indices], target[indices], rotation)
                native[indices] = result
                for vector in (angles, translation):
                    flag = sum(1 << axis for axis, v in enumerate(vector) if v)
                    flags.append(flag)
                    for value in vector:
                        if value:
                            values.extend(smart(value))
            flags.append(0)
            payload = struct.pack('>HB', SKELETON, count) + flags + values
            ids.append((ARCHIVE << 16) | len(frames))
            frames.append(payload)
            errors.extend(np.linalg.norm(native - target, axis=1).tolist())
            if name == 'Death' and i == n - 1:
                (out / 'models/devito_boss_remains.dat').write_bytes(encode_model(native, faces, colors, labels))
        report['clips'][name] = {'frames': n, 'client_ticks': sum(delays),
            'duration_seconds': sum(delays) / 50, 'max_error_units': max(errors),
            'rms_error_units': float(np.sqrt(np.mean(np.square(errors))))}
        config.extend(['[[animation]]', f'id = "seq.devito_boss_{name.lower()}"',
                       f'frameIDs = {ids}', f'frameDelays = {delays}',
                       f'frameStep = {n if loop else -1}', 'forcedPriority = 5',
                       'precedenceAnimating = 0', 'priority = 0',
                       f'maxLoops = {99 if loop else 1}', ''])
    ids = []
    for alpha in range(0, 33):
        ids.append((ARCHIVE << 16) | len(frames))
        payload = (struct.pack('>HB', SKELETON, count) + bytes(count - 1) +
                   bytes([1]) + smart(alpha))
        frames.append(payload)
    config.extend(['[[animation]]', 'id = "seq.devito_boss_disintegrate"',
                   f'frameIDs = {ids}', f'frameDelays = {[4] * 12 + [3] * 20 + [12]}',
                   'forcedPriority = 5', 'maxLoops = 1', ''])
    config.extend(['[[animation]]', 'id = "seq.devito_boss_remains_idle"',
                   f'frameIDs = {[ids[0]]}', 'frameDelays = [100]', 'frameStep = 1',
                   'precedenceAnimating = 0', 'priority = 0', 'maxLoops = 99', ''])
    rsanim = bytearray(b'RSAN' + struct.pack('>BHH', 1, ARCHIVE, len(frames)))
    for i, payload in enumerate(frames):
        rsanim.extend(struct.pack('>HH', i, len(payload)) + payload)
    (out / 'animations/devito_boss.rsanim').write_bytes(rsanim)
    (out / 'configs/devito_boss_sequences.toml').write_text('\n'.join(config), encoding='utf-8', newline='\n')
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2), encoding='utf-8', newline='\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
