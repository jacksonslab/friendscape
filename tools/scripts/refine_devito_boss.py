"""Blender repair: integrate torso details into its surface and add the Kick action."""
import math
from pathlib import Path
import sys

import bpy
from mathutils import Vector, Quaternion
from mathutils.geometry import delaunay_2d_cdt
import numpy as np


def refine_surface(obj):
    data = obj.data
    data.calc_loop_triangles()
    coords = [v.co.copy() for v in data.vertices]
    weights = [{obj.vertex_groups[g.group].name: g.weight for g in v.groups if g.weight > 0}
               for v in data.vertices]
    adjacency = [set() for _ in coords]
    for edge in data.edges:
        a, b = edge.vertices
        adjacency[a].add(b)
        adjacency[b].add(a)
    components, seen = [], set()
    for start in range(len(coords)):
        if start in seen:
            continue
        pending, component = [start], set()
        while pending:
            i = pending.pop()
            if i in component:
                continue
            component.add(i)
            pending.extend(adjacency[i] - component)
        seen.update(component)
        components.append(component)
    body = next(c for c in components if {p.material_index for p in data.polygons
                                         if p.vertices[0] in c} == {0, 1})
    triangles = list(data.loop_triangles)
    front = [t for t in triangles if t.vertices[0] in body and t.material_index == 0
             and t.normal.y < -.0001 and max(coords[i].y for i in t.vertices) < .0001]
    removed = [t for t in triangles if t.material_index in (6, 12)
               and max(coords[i].z for i in t.vertices) < 1.9]
    detail_vertices = {i for t in removed for i in t.vertices}
    assert len(detail_vertices) == 92, len(detail_vertices)
    front_vertices = sorted({i for t in front for i in t.vertices})
    index = {v: i for i, v in enumerate(front_vertices)}
    points = [Vector((coords[i].x, coords[i].z)) for i in front_vertices]
    faces = []
    original = []
    for triangle in front:
        face = [index[i] for i in triangle.vertices]
        a, b, c = [points[i] for i in face]
        if (b - a).cross(c - a) < 0:
            face.reverse()
        faces.append(face)
        original.append(list(triangle.vertices))
    hair_polygons, edges = [], []
    for sign in (-1, 1):
        for j in range(3):
            a = Vector((sign * (.04 + j * .043), 1.64 - j * .026))
            b = a + Vector((sign * .031, -.036))
            direction = (b - a).normalized()
            normal = Vector((-direction.y, direction.x)) * .006
            polygon = [a + normal, b + normal, b - normal, a - normal]
            hair_polygons.append(polygon)
            base = len(points)
            points.extend(polygon)
            edges.extend((base + k, base + (k + 1) % 4) for k in range(4))
    center = Vector((-.20, 1.485))
    navel = [center + Vector((math.cos(i * math.tau / 8) * .018,
                              math.sin(i * math.tau / 8) * .014)) for i in range(8)]
    base = len(points)
    points.extend(navel)
    edges.extend((base + i, base + (i + 1) % 8) for i in range(8))
    points.append(center)
    cdt_points, _, cdt_faces, orig_points, _, _ = delaunay_2d_cdt(points, edges, faces, 1, 1e-7, True)

    def barycentric(point, triangle):
        a, b, c = [Vector((coords[i].x, coords[i].z)) for i in triangle]
        denominator = (b - a).cross(c - a)
        if abs(denominator) < 1e-10:
            return None
        v = (point - a).cross(c - a) / denominator
        w = (b - a).cross(point - a) / denominator
        result = (1 - v - w, v, w)
        return result if min(result) > -1e-4 else None

    def inside(point, polygon):
        signs = [(polygon[(i + 1) % len(polygon)] - polygon[i]).cross(point - polygon[i])
                 for i in range(len(polygon))]
        return min(signs) >= -1e-8 or max(signs) <= 1e-8

    keep = [i for i in range(len(coords)) if i not in detail_vertices]
    remap = {v: i for i, v in enumerate(keep)}
    new_coords = [coords[i].copy() for i in keep]
    new_weights = [weights[i].copy() for i in keep]
    body_weight_sets = sorted({tuple(sorted(weights[i].items())) for i in body})
    bone_names = sorted({name for i in body for name in weights[i]})
    body_weight_vectors = np.array([[dict(w).get(b, 0) for b in bone_names] for w in body_weight_sets])
    point_map = []
    for point, originals in zip(cdt_points, orig_points):
        source = next((front_vertices[i] for i in originals if i < len(front_vertices)), None)
        if source is not None:
            point_map.append(remap[source])
            continue
        match = next(((tri, b) for tri in original if (b := barycentric(point, tri)) is not None), None)
        if match is None:
            raise ValueError(f'Point outside torso surface: {point}')
        triangle, bary = match
        position = sum((coords[i] * weight for i, weight in zip(triangle, bary)), Vector())
        desired = {b: sum(weights[i].get(b, 0) * weight for i, weight in zip(triangle, bary))
                   for b in bone_names}
        selected = int(np.argmin(np.sum((body_weight_vectors - np.array([desired[b] for b in bone_names])) ** 2, axis=1)))
        if (point - center).length < 1e-5:
            position.y += .006
        point_map.append(len(new_coords))
        new_coords.append(position)
        new_weights.append(dict(body_weight_sets[selected]))
    replace = {t.index for t in front + removed}
    new_faces = [tuple(remap[i] for i in t.vertices) for t in triangles if t.index not in replace]
    materials = [t.material_index for t in triangles if t.index not in replace]
    for face in cdt_faces:
        if len(face) != 3:
            raise ValueError('Expected triangulated torso')
        point = sum((cdt_points[i] for i in face), Vector((0, 0))) / 3
        new_faces.append(tuple(point_map[i] for i in face))
        materials.append(12 if any(inside(point, polygon) for polygon in hair_polygons)
                         else 6 if inside(point, navel) else 0)
    replacement = bpy.data.meshes.new('Boss integrated skin details')
    replacement.from_pydata(new_coords, [], new_faces)
    for material in data.materials:
        replacement.materials.append(material)
    for polygon, material in zip(replacement.polygons, materials):
        polygon.material_index = material
        polygon.use_smooth = False
    group_names = [group.name for group in obj.vertex_groups]
    obj.data = replacement
    for name in group_names:
        if obj.vertex_groups.get(name) is None:
            obj.vertex_groups.new(name=name)
    for i, vertex_weights in enumerate(new_weights):
        for name, weight in vertex_weights.items():
            obj.vertex_groups[name].add([i], weight, 'REPLACE')
    uv = replacement.uv_layers.new(name='UVMap')
    for polygon in replacement.polygons:
        for loop in polygon.loop_indices:
            co = new_coords[replacement.loops[loop].vertex_index]
            uv.data[loop].uv = ((co.x + 1.5) / 3, co.z / 3)
    replacement.update()
    obj['integrated_torso_details'] = True
    print(f'Integrated details: {len(coords)} -> {len(new_coords)} vertices; {len(new_faces)} triangles; '
          f'{materials.count(12)} hair faces, {materials.count(6)} crease faces')


def add_kick(rig):
    if bpy.data.actions.get('Kick'):
        bpy.data.actions.remove(bpy.data.actions['Kick'])
    action = bpy.data.actions.new('Kick')
    action.use_fake_user = True
    action['loop'] = False
    action['description'] = 'Right front kick, anticipation, extension and recovery.'
    rig.animation_data.action = action
    for track in rig.animation_data.nla_tracks:
        track.mute = True

    def rotate(name, axis, degrees):
        q = rig.data.bones[name].matrix_local.to_quaternion()
        rig.pose.bones[name].rotation_quaternion = q.inverted() @ Quaternion(Vector(axis), math.radians(degrees)) @ q

    def point(name, direction):
        bpy.context.view_layer.update()
        pose = rig.pose.bones[name]
        bone = pose.bone
        baseline = pose.parent.matrix @ pose.parent.bone.matrix_local.inverted() @ bone.matrix_local
        q = baseline.to_quaternion()
        delta = (q @ Vector((0, 1, 0))).rotation_difference(Vector(direction).normalized())
        pose.rotation_quaternion = q.inverted() @ delta @ q

    for frame, charge, strike in [(1, 0, 0), (7, 1, 0), (14, 0, 1), (18, 0, 1), (26, .6, 0), (35, 0, 0)]:
        for pose in rig.pose.bones:
            pose.location = (0, 0, 0)
            pose.rotation_quaternion = (1, 0, 0, 0)
            pose.scale = (1, 1, 1)
        rotate('spine', (1, 0, 0), -6 * charge - 9 * strike)
        rotate('chest', (0, 0, 1), -6 * strike)
        rig.pose.bones['root'].location.x = .06 * (charge + strike)
        point('thigh.R', (-.17, -.015 - .20 * charge - .45 * strike, -.48 + .25 * charge + .40 * strike))
        point('shin.R', (-.05, .015 + .20 * charge - .46 * strike, -.37 + .18 * charge + .26 * strike))
        rotate('foot.R', (1, 0, 0), 12 * strike)
        rotate('upper_arm.L', (1, 0, 0), -15 * strike)
        rotate('upper_arm.R', (1, 0, 0), 18 * strike)
        for pose in rig.pose.bones:
            for channel in ('location', 'rotation_quaternion', 'scale'):
                pose.keyframe_insert(channel, frame=frame, group=pose.name)
    for layer in action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for key in curve.keyframe_points:
                        key.interpolation = 'LINEAR'
    rig.animation_data.action = bpy.data.actions['Idle']
    if rig.animation_data.action.slots:
        rig.animation_data.action_slot = rig.animation_data.action.slots[0]
    bpy.context.scene.frame_set(1)


obj, rig = bpy.data.objects['Boss_Mesh'], bpy.data.objects['Boss_Rig']
if not obj.get('integrated_torso_details'):
    refine_surface(obj)
add_kick(rig)
destination = Path(sys.argv[sys.argv.index('--') + 1])
bpy.ops.wm.save_as_mainfile(filepath=str(destination))
