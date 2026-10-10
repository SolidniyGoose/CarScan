"""
Добавляет в car.glb процедурные 3D-модели двигателя V8 и седловидного топливного бака.

Детали строятся прямо в координатах машины (по габаритам модели), поэтому видны
на своих местах в рентген-режиме viewer.html. Скрипт идемпотентный: при повторном
запуске сначала убирает ранее добавленные детали, затем строит заново.

Запуск (только стандартная библиотека Python 3):
    python tools/add_car_parts.py app/src/main/assets/models/car.glb
"""
import json
import math
import struct
import sys

# ---------------------------------------------------------------- геометрия

def add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def length(a):
    return math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2)


def norm(a):
    n = length(a)
    return (a[0] / n, a[1] / n, a[2] / n) if n > 1e-12 else (0.0, 1.0, 0.0)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def mat_apply(m, v):
    return (
        m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
        m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
        m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2],
    )


def rot_z(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return ((c, -s, 0), (s, c, 0), (0, 0, 1))


def rot_x(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return ((1, 0, 0), (0, c, -s), (0, s, c))


IDENTITY = ((1, 0, 0), (0, 1, 0), (0, 0, 1))


def rot_y_to(direction):
    """Поворот, переводящий ось Y в direction (формула Родрига)."""
    d = norm(direction)
    y = (0.0, 1.0, 0.0)
    c = d[1]
    if c > 0.999999:
        return IDENTITY
    if c < -0.999999:
        return ((1, 0, 0), (0, -1, 0), (0, 0, -1))
    k = norm(cross(y, d))
    s = math.sqrt(1 - c * c)
    kx, ky, kz = k
    t = 1 - c
    return (
        (c + kx * kx * t, kx * ky * t - kz * s, kx * kz * t + ky * s),
        (ky * kx * t + kz * s, c + ky * ky * t, ky * kz * t - kx * s),
        (kz * kx * t - ky * s, kz * ky * t + kx * s, c + kz * kz * t),
    )


class Geometry:
    """Набор вершин/нормалей/индексов одного материала."""

    def __init__(self):
        self.pos, self.nrm, self.idx = [], [], []

    def extend(self, pos, nrm, idx, rot=IDENTITY, offset=(0, 0, 0)):
        base = len(self.pos)
        self.pos += [add(mat_apply(rot, p), offset) for p in pos]
        self.nrm += [norm(mat_apply(rot, n)) for n in nrm]
        self.idx += [i + base for i in idx]


def rounded_box(sx, sy, sz, r, k=3):
    """Параллелепипед со скруглёнными рёбрами радиуса r (гладкие нормали)."""
    h = (sx / 2, sy / 2, sz / 2)
    r = min(r, *h)
    inner = (h[0] - r, h[1] - r, h[2] - r)
    faces = [  # нормаль, ось U, ось V (U x V = нормаль)
        ((1, 0, 0), (0, 0, -1), (0, 1, 0)), ((-1, 0, 0), (0, 0, 1), (0, 1, 0)),
        ((0, 1, 0), (1, 0, 0), (0, 0, -1)), ((0, -1, 0), (1, 0, 0), (0, 0, 1)),
        ((0, 0, 1), (1, 0, 0), (0, 1, 0)), ((0, 0, -1), (-1, 0, 0), (0, 1, 0)),
    ]

    def axis_half(axis):
        return h[[abs(c) for c in axis].index(1)]

    def coords(half):
        # Сгущаем сетку в зонах скругления, плоская середина — одним отрезком
        zone = [(-half + r * i / k) for i in range(k + 1)] + [(half - r + r * i / k) for i in range(k + 1)]
        return sorted(set(round(c, 6) for c in zone))

    pos, nrm, idx = [], [], []
    for n_axis, u_axis, v_axis in faces:
        us, vs = coords(axis_half(u_axis)), coords(axis_half(v_axis))
        base = len(pos)
        for v in vs:
            for u in us:
                q = add(add(mul(n_axis, axis_half(n_axis)), mul(u_axis, u)), mul(v_axis, v))
                clamped = tuple(max(-inner[i], min(inner[i], q[i])) for i in range(3))
                d = sub(q, clamped)
                n = norm(d) if length(d) > 1e-9 else n_axis
                pos.append(add(clamped, mul(n, r)))
                nrm.append(n)
        w = len(us)
        for j in range(len(vs) - 1):
            for i in range(w - 1):
                a, b = base + j * w + i, base + j * w + i + 1
                c, d = base + (j + 1) * w + i + 1, base + (j + 1) * w + i
                idx += [a, b, c, a, c, d]
    return pos, nrm, idx


def frustum(r0, r1, height, segs=28, caps=True):
    """Цилиндр/усечённый конус вдоль оси Y, центр в начале координат."""
    pos, nrm, idx = [], [], []
    y0, y1 = -height / 2, height / 2
    slope = (r0 - r1) / height
    for i in range(segs + 1):
        a = 2 * math.pi * i / segs
        c, s = math.cos(a), math.sin(a)
        n = norm((c, slope, s))
        pos += [(r0 * c, y0, r0 * s), (r1 * c, y1, r1 * s)]
        nrm += [n, n]
    for i in range(segs):
        a, b, c, d = 2 * i, 2 * i + 1, 2 * i + 3, 2 * i + 2
        idx += [a, b, c, a, c, d]
    if caps:
        for y, r, ny in ((y0, r0, -1), (y1, r1, 1)):
            center = len(pos)
            pos.append((0, y, 0))
            nrm.append((0, ny, 0))
            for i in range(segs + 1):
                a = 2 * math.pi * i / segs
                pos.append((r * math.cos(a), y, r * math.sin(a)))
                nrm.append((0, ny, 0))
            for i in range(segs):
                p, q = center + 1 + i, center + 2 + i
                idx += [center, q, p] if ny > 0 else [center, p, q]
    return pos, nrm, idx


class Assembly:
    """Детали узла в локальных сантиметрах, сгруппированные по материалам."""

    def __init__(self, origin, scale):
        self.origin, self.scale, self.parts = origin, scale, {}

    def _geo(self, material):
        return self.parts.setdefault(material, Geometry())

    def _place(self, material, mesh, rot, center):
        pos, nrm, idx = mesh
        pos = [mul(p, self.scale) for p in pos]
        world = add(self.origin, mul(center, self.scale))
        self._geo(material).extend(pos, nrm, idx, rot, world)

    def box(self, material, size, center, r=2.0, rot=IDENTITY):
        self._place(material, rounded_box(*size, r), rot, center)

    def cylinder(self, material, radius, height, center, axis=(0, 1, 0), r_top=None, segs=28):
        self._place(material, frustum(radius, radius if r_top is None else r_top, height, segs), rot_y_to(axis), center)

    def pipe(self, material, p0, p1, radius, segs=16):
        d = sub(p1, p0)
        self._place(material, frustum(radius, radius, length(d), segs), rot_y_to(d), mul(add(p0, p1), 0.5))


# ---------------------------------------------------------------- детали

def build_engine(eng):
    """V8 с развалом 90°, продольно; ось Z — вперёд по ходу машины, начало — низ поддона."""
    B, H, C, P, X = "CS_EngineBlock", "CS_EngineHead", "CS_EngineCover", "CS_EnginePulley", "CS_Exhaust"
    eng.box(B, (44, 12, 56), (0, 6, -2), r=3)        # масляный поддон
    eng.box(B, (56, 22, 66), (0, 23, 0), r=3)        # блок цилиндров
    for side in (1, -1):
        tilt = rot_z(-side * 45)
        pivot = (side * 8, 30, 0)

        def at(local, pivot=pivot, tilt=tilt):
            return add(pivot, mat_apply(tilt, local))

        eng.box(B, (24, 22, 62), at((0, 11, 0)), r=2.5, rot=tilt)       # ряд цилиндров
        eng.box(H, (28, 9, 64), at((0, 26.5, 0)), r=2.5, rot=tilt)      # головка
        eng.box(C, (25, 6, 62), at((0, 34, 0)), r=3, rot=tilt)          # крышка клапанов
        for k in range(4):                                               # катушки зажигания
            eng.box(C, (8, 3, 6), at((-5, 38, -21 + k * 14)), r=1, rot=tilt)
        # Выпускной коллектор: трубы от каждого цилиндра в общую трубу снаружи блока
        eng.pipe(X, (side * 36, 24, -30), (side * 36, 24, 26), 3.4)
        for k in range(4):
            z = -21 + k * 14
            eng.pipe(X, at((side * 13, 20, z)), (side * 36, 24, z), 2.2)
        eng.pipe(X, (side * 36, 24, -30), (side * 30, 6, -46), 3.4)    # приёмная труба
    eng.box(B, (22, 12, 58), (0, 50, 0), r=3)        # впускной коллектор в развале
    for side in (1, -1):
        for k in range(4):
            z = -21 + k * 14
            eng.pipe(B, (side * 8, 52, z), (side * 17, 46, z), 2.4)
    eng.box(C, (34, 5, 44), (0, 59, 4), r=2.5)       # декоративная крышка двигателя
    eng.cylinder(B, 5, 10, (0, 53, 33), axis=(0, 0, 1))              # дроссель
    eng.pipe(B, (0, 53, 38), (12, 58, 52), 5)                         # воздуховод
    eng.box(B, (52, 44, 6), (0, 30, 34), r=3)        # крышка ГРМ
    for radius, width, center in (
        (9.5, 4, (0, 14, 39)),       # шкив коленвала
        (6.5, 3, (0, 40, 38.5)),     # помпа
        (3.5, 3, (19, 48, 39)),      # шкив генератора
        (5.5, 3, (-20, 22, 38.5)),   # шкив кондиционера
        (3.5, 3, (-12, 52, 38.5)),   # ролики
        (3.5, 3, (10, 30, 38.5)),
    ):
        eng.cylinder(P, radius, width, center, axis=(0, 0, 1))
    eng.cylinder(P, 7.5, 14, (19, 48, 30), axis=(0, 0, 1))           # генератор
    eng.cylinder(P, 6.5, 15, (-20, 22, 29), axis=(0, 0, 1))          # компрессор кондиционера
    eng.cylinder(C, 4, 10, (24, 18, 18))                              # масляный фильтр
    eng.cylinder(B, 24, 12, (0, 26, -39), axis=(0, 0, -1), r_top=19)  # колокол АКПП


def build_tank(tank, slices):
    """Седловидный бак: две половины над карданным туннелем. Начало — низ бака, Z — вперёд."""
    T, D = "CS_FuelTank", "CS_TankDetail"
    for side in (1, -1):
        tank.box(T, (50, 24, 62), (side * 32, 12, 0), r=7)
    tank.box(T, (26, 9, 50), (0, 19.5, 0), r=3)                       # перемычка над туннелем
    tank.cylinder(D, 7, 2, (24, 24.6, 8))                             # фланец топливного насоса
    tank.cylinder(D, 4, 3, (24, 26.5, 8))
    # Заливная горловина — к лючку на правом заднем крыле (справа по ходу = -X)
    tank.pipe(D, (-48, 20, -20), (-70, 45, -40), 2.8)
    tank.pipe(D, (-70, 45, -40), (-86, 76, -52), 2.8)
    for z in (-18, 18):                                                # хомуты крепления
        tank.box(D, (138, 1.2, 4), (0, -0.6, z), r=0.5)
    # «Топливо» слоями: viewer.html показывает столько слоёв, сколько топлива в баке
    layer = 20.0 / slices
    for k in range(slices):
        for side in (1, -1):
            tank.box(f"CS_Fuel_{k}", (44, layer, 56), (side * 32, 2 + layer * (k + 0.5), 0), r=0.4)


MATERIALS = {
    "CS_EngineBlock": dict(color=[0.42, 0.43, 0.45, 1], metal=0.85, rough=0.45),
    "CS_EngineHead": dict(color=[0.58, 0.59, 0.61, 1], metal=0.9, rough=0.35),
    "CS_EngineCover": dict(color=[0.07, 0.07, 0.08, 1], metal=0.3, rough=0.3),
    "CS_EnginePulley": dict(color=[0.24, 0.25, 0.27, 1], metal=0.9, rough=0.3),
    "CS_Exhaust": dict(color=[0.47, 0.34, 0.25, 1], metal=0.7, rough=0.6),
    "CS_FuelTank": dict(color=[0.07, 0.07, 0.08, 1], metal=0.0, rough=0.7),
    "CS_TankDetail": dict(color=[0.22, 0.22, 0.24, 1], metal=0.6, rough=0.5),
}
FUEL_SLICES = 8
for _k in range(FUEL_SLICES):
    MATERIALS[f"CS_Fuel_{_k}"] = dict(color=[0.95, 0.72, 0.22, 1], metal=0.0, rough=0.2, mask=True)

# ---------------------------------------------------------------- GLB

def read_glb(path):
    with open(path, "rb") as f:
        data = f.read()
    magic, version, _ = struct.unpack_from("<III", data, 0)
    assert magic == 0x46546C67 and version == 2, "не GLB 2.0"
    json_len, json_type = struct.unpack_from("<II", data, 12)
    assert json_type == 0x4E4F534A
    gltf = json.loads(data[20:20 + json_len])
    off = 20 + json_len
    bin_len, bin_type = struct.unpack_from("<II", data, off)
    assert bin_type == 0x004E4942
    return gltf, bytearray(data[off + 8:off + 8 + bin_len])


def write_glb(path, gltf, binary):
    js = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    js += b" " * (-len(js) % 4)
    binary = bytes(binary) + b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(js) + 8 + len(binary)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        f.write(struct.pack("<II", len(binary), 0x004E4942) + binary)


def mat4_mul(a, b):
    """Произведение матриц 4×4 в порядке столбцов (column-major, как в glTF)."""
    return [sum(a[k * 4 + r] * b[c * 4 + k] for k in range(4)) for c in range(4) for r in range(4)]


def node_matrix(node):
    if "matrix" in node:
        return node["matrix"]
    t = node.get("translation", [0, 0, 0])
    s = node.get("scale", [1, 1, 1])
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    r = [
        1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
        2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
        2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
        0, 0, 0, 1,
    ]
    for c in range(3):
        for rr in range(3):
            r[c * 4 + rr] *= s[c]
    r[12:15] = t
    return r


def world_bounds(gltf):
    """Габариты всех мешей сцены с учётом матриц узлов (column-major, как в glTF)."""
    lo, hi = [math.inf] * 3, [-math.inf] * 3

    def visit(index, parent):
        node = gltf["nodes"][index]
        m = mat4_mul(parent, node_matrix(node))
        if "mesh" in node:
            for prim in gltf["meshes"][node["mesh"]]["primitives"]:
                acc = gltf["accessors"][prim["attributes"]["POSITION"]]
                for cx in (acc["min"][0], acc["max"][0]):
                    for cy in (acc["min"][1], acc["max"][1]):
                        for cz in (acc["min"][2], acc["max"][2]):
                            p = [m[r] * cx + m[4 + r] * cy + m[8 + r] * cz + m[12 + r] for r in range(3)]
                            for i in range(3):
                                lo[i], hi[i] = min(lo[i], p[i]), max(hi[i], p[i])
        for child in node.get("children", []):
            visit(child, m)

    identity = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    for root in gltf["scenes"][gltf.get("scene", 0)]["nodes"]:
        visit(root, identity)
    return lo, hi


def strip_previous(gltf, binary):
    """Убирает детали, добавленные прошлым запуском (счётчики сохранены в asset.extras)."""
    mark = gltf.get("asset", {}).get("extras", {}).pop("carscanParts", None)
    if not mark:
        return binary
    for key in ("accessors", "bufferViews", "meshes", "materials", "nodes"):
        del gltf[key][mark[key]:]
    for scene in gltf["scenes"]:
        scene["nodes"] = [n for n in scene["nodes"] if n < mark["nodes"]]
    gltf["buffers"][0]["byteLength"] = mark["binLength"]
    if not gltf["asset"]["extras"]:
        del gltf["asset"]["extras"]
    return binary[:mark["binLength"]]


def append_assembly(gltf, binary, name, assembly):
    def add_view(data, target):
        binary.extend(b"\0" * (-len(binary) % 4))
        gltf["bufferViews"].append({"buffer": 0, "byteOffset": len(binary), "byteLength": len(data), "target": target})
        binary.extend(data)
        return len(gltf["bufferViews"]) - 1

    def add_accessor(view, comp, count, kind, **extra):
        gltf["accessors"].append({"bufferView": view, "componentType": comp, "count": count, "type": kind, **extra})
        return len(gltf["accessors"]) - 1

    primitives = []
    for material, geo in assembly.parts.items():
        if material not in material_index:
            spec = MATERIALS[material]
            mat = {
                "name": material,
                "pbrMetallicRoughness": {
                    "baseColorFactor": spec["color"], "metallicFactor": spec["metal"], "roughnessFactor": spec["rough"],
                },
            }
            if spec.get("mask"):
                mat.update(alphaMode="MASK", alphaCutoff=0.5)
            gltf["materials"].append(mat)
            material_index[material] = len(gltf["materials"]) - 1
        flat_pos = [c for p in geo.pos for c in p]
        flat_nrm = [c for n in geo.nrm for c in n]
        pos_view = add_view(struct.pack(f"<{len(flat_pos)}f", *flat_pos), 34962)
        nrm_view = add_view(struct.pack(f"<{len(flat_nrm)}f", *flat_nrm), 34962)
        big = len(geo.pos) > 65535
        idx_view = add_view(struct.pack(f"<{len(geo.idx)}{'I' if big else 'H'}", *geo.idx), 34963)
        mins = [min(p[i] for p in geo.pos) for i in range(3)]
        maxs = [max(p[i] for p in geo.pos) for i in range(3)]
        primitives.append({
            "attributes": {
                "POSITION": add_accessor(pos_view, 5126, len(geo.pos), "VEC3", min=mins, max=maxs),
                "NORMAL": add_accessor(nrm_view, 5126, len(geo.nrm), "VEC3"),
            },
            "indices": add_accessor(idx_view, 5125 if big else 5123, len(geo.idx), "SCALAR"),
            "material": material_index[material],
        })
    gltf["meshes"].append({"name": name, "primitives": primitives})
    gltf["nodes"].append({"name": name, "mesh": len(gltf["meshes"]) - 1})
    gltf["scenes"][gltf.get("scene", 0)]["nodes"].append(len(gltf["nodes"]) - 1)
    return sum(len(g.idx) // 3 for g in assembly.parts.values())


material_index = {}


def main(path):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    gltf, binary = read_glb(path)
    binary = strip_previous(gltf, binary)
    mark = {key: len(gltf[key]) for key in ("accessors", "bufferViews", "meshes", "materials", "nodes")}
    mark["binLength"] = len(binary)

    lo, hi = world_bounds(gltf)
    center_x = (lo[0] + hi[0]) / 2
    car_length = hi[2] - lo[2]
    # Габарит модели по длине ≈ 521 см (Mercedes GLS X167): сантиметры → единицы модели
    cm = car_length / 521.0
    front = hi[2]  # перед машины смотрит в +Z (проверено по рендеру)

    # Двигатель: низ поддона ~30 см от земли, центр ~1.25 м от переднего бампера (за передней осью)
    engine = Assembly(origin=(center_x, lo[1] + 30 * cm, front - 125 * cm), scale=cm)
    build_engine(engine)
    # Бак: под задним сиденьем перед задней осью, низ ~23 см от земли, центр ~3.6 м от бампера
    tank = Assembly(origin=(center_x, lo[1] + 23 * cm, front - 360 * cm), scale=cm)
    build_tank(tank, FUEL_SLICES)

    tri_engine = append_assembly(gltf, binary, "CarScan_Engine", engine)
    tri_tank = append_assembly(gltf, binary, "CarScan_FuelTank", tank)
    gltf["buffers"][0]["byteLength"] = len(binary)
    gltf.setdefault("asset", {}).setdefault("extras", {})["carscanParts"] = mark
    write_glb(path, gltf, binary)
    print(f"двигатель: {tri_engine} треугольников, бак: {tri_tank}; масштаб {cm:.3f} ед/см")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/models/car.glb")
