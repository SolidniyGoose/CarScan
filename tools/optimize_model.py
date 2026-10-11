"""
Облегчает car.glb без изменения картинки.

- Склеивает одинаковые вершины. Модель экспортирована «распакованной»: у каждого треугольника свои
  три вершины (1,08 млн вершин на 361 тыс. треугольников), поэтому видеокарта считала каждую
  вершину до шести раз.
- Удаляет текстурные координаты, если в модели нет текстур (17 МБ мёртвого груза).
- Делит общий буфер вершин по деталям: у каждой детали свои габариты, и при приближении камеры
  three.js не рисует то, что вне кадра. Индексы — 16-битные там, где хватает.

Склеиваются только побайтно одинаковые вершины (позиция + нормаль), так что форма и освещение
не меняются. Скрипт можно запускать повторно и после tools/add_car_parts.py.

Запуск (только стандартная библиотека Python 3):
    python tools/optimize_model.py app/src/main/assets/models/car.glb
"""
import json
import struct
import sys
from array import array

COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
TYPE_COUNT = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
TRIANGLES = 4


def read_glb(path):
    with open(path, "rb") as f:
        data = f.read()
    magic, _, _ = struct.unpack_from("<4sII", data, 0)
    assert magic == b"glTF", "не GLB"
    json_len, _ = struct.unpack_from("<I4s", data, 12)
    gltf = json.loads(data[20:20 + json_len])
    bin_offset = 20 + json_len
    bin_len, _ = struct.unpack_from("<I4s", data, bin_offset)
    return gltf, data[bin_offset + 8:bin_offset + 8 + bin_len]


def write_glb(path, gltf, binary):
    js = json.dumps(gltf, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    js += b" " * (-len(js) % 4)
    binary += b"\0" * (-len(binary) % 4)
    total = 12 + 8 + len(js) + 8 + len(binary)
    with open(path, "wb") as f:
        f.write(struct.pack("<4sII", b"glTF", 2, total))
        f.write(struct.pack("<I4s", len(js), b"JSON") + js)
        f.write(struct.pack("<I4s", len(binary), b"BIN\0") + binary)


def element_bytes(gltf, binary, index):
    """Элементы аксессора как список bytes (с учётом byteStride)."""
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    size = COMPONENT_SIZE[acc["componentType"]] * TYPE_COUNT[acc["type"]]
    stride = view.get("byteStride", size)
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    return [binary[start + i * stride:start + i * stride + size] for i in range(acc["count"])]


def read_indices(gltf, binary, index):
    acc = gltf["accessors"][index]
    code = {5121: "B", 5123: "H", 5125: "I"}[acc["componentType"]]
    view = gltf["bufferViews"][acc["bufferView"]]
    start = view.get("byteOffset", 0) + acc.get("byteOffset", 0)
    values = array(code)
    values.frombytes(binary[start:start + acc["count"] * values.itemsize])
    return values


def main(path):
    gltf, binary = read_glb(path)
    if gltf.get("animations") or gltf.get("skins"):
        sys.exit("Анимации и скелеты скрипт не поддерживает — модель не изменена")
    has_textures = bool(gltf.get("textures"))

    accessors, views, out = [], [], bytearray()

    def add_view(data, target):
        out.extend(b"\0" * (-len(out) % 4))
        views.append({"buffer": 0, "byteOffset": len(out), "byteLength": len(data), "target": target})
        out.extend(data)
        return len(views) - 1

    def add_accessor(template, data, count, **extra):
        acc = {k: v for k, v in template.items() if k in ("componentType", "type", "normalized")}
        acc.update(bufferView=add_view(data, 34962), count=count, **extra)
        accessors.append(acc)
        return len(accessors) - 1

    # Отметка tools/add_car_parts.py: где кончается машина и начинаются добавленные детали.
    # Нумерация данных меняется — пересчитываем её, иначе повторный запуск того скрипта сломается
    mark = gltf.get("asset", {}).get("extras", {}).get("carscanParts")

    before_vertices = after_vertices = 0
    seen_vertex_sets = set()
    for mesh_index, mesh in enumerate(gltf["meshes"]):
        if mark and mesh_index == mark["meshes"]:
            mark.update(accessors=len(accessors), bufferViews=len(views), binLength=len(out))
        for prim in mesh["primitives"]:
            if prim.get("mode", TRIANGLES) != TRIANGLES or prim.get("targets"):
                sys.exit("Поддерживаются только треугольники без morph targets — модель не изменена")
            names = [n for n in sorted(prim["attributes"]) if has_textures or not n.startswith("TEXCOORD_")]
            attrs = {n: element_bytes(gltf, binary, prim["attributes"][n]) for n in names}
            count = len(attrs["POSITION"])
            vertex_set = prim["attributes"]["POSITION"]
            if vertex_set not in seen_vertex_sets:
                seen_vertex_sets.add(vertex_set)
                before_vertices += count
            indices = read_indices(gltf, binary, prim["indices"]) if "indices" in prim else range(count)

            # Новые вершины только этой детали, одинаковые — одна
            remap, order, new_indices = {}, [], array("I")
            for i in indices:
                key = b"".join(attrs[n][i] for n in names)
                v = remap.get(key)
                if v is None:
                    v = remap[key] = len(order)
                    order.append(i)
                new_indices.append(v)
            after_vertices += len(order)

            new_attrs = {}
            for n in names:
                template = gltf["accessors"][prim["attributes"][n]]
                data = b"".join(attrs[n][i] for i in order)
                extra = {}
                if n == "POSITION":
                    xyz = array("f")
                    xyz.frombytes(data)
                    extra = {
                        "min": [min(xyz[k::3]) for k in range(3)],
                        "max": [max(xyz[k::3]) for k in range(3)],
                    }
                new_attrs[n] = add_accessor(template, data, len(order), **extra)
            prim["attributes"] = new_attrs

            if len(order) <= 0xFFFF:
                small = array("H", new_indices)
                idx_data, comp = small.tobytes(), 5123
            else:
                idx_data, comp = new_indices.tobytes(), 5125
            accessors.append({
                "bufferView": add_view(idx_data, 34963),
                "componentType": comp,
                "count": len(new_indices),
                "type": "SCALAR",
            })
            prim["indices"] = len(accessors) - 1

    gltf["accessors"] = accessors
    gltf["bufferViews"] = views
    gltf["buffers"] = [{"byteLength": len(out)}]
    write_glb(path, gltf, bytes(out))
    print(f"Вершин: {before_vertices} → {after_vertices}; бинарные данные: {len(binary) / 1e6:.1f} → {len(out) / 1e6:.1f} МБ")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
