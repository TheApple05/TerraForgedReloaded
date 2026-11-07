"""Compare recorded terrain fields/biomes across loader runs without hiding feature differences."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

FIELDS = ("terrain", "cellBiome", "cellFloatBits", "biome")
CELL_FIELDS = {"height", "gradient", "temperature", "moisture", "erosion", "weirdness",
               "riverMask", "heightErosion", "sediment"}


def load(path: Path) -> tuple[dict, dict]:
    data = json.loads(path.read_text(encoding="utf-8"))
    qa = data.get("qaResult") or data.get("runtimeQa") or {}
    if qa.get("status") != "passed" or data.get("customTerrainValidated") is not True:
        raise ValueError(f"Not a passing custom-terrain report: {path}")
    generation = qa.get("generation") or {}
    if (type(generation.get("seed")) is not int
            or any(type(generation.get(bound)) is not int for bound in ("minY", "maxY"))
            or generation["minY"] > generation["maxY"]):
        raise ValueError(f"Missing or invalid seed/dimension bounds: {path}")
    samples = generation.get("samples") or []
    if not samples or not all(all(field in sample for field in FIELDS) for sample in samples):
        raise ValueError(f"Missing terrain fields: {path}")
    for sample in samples:
        bits = sample["cellFloatBits"]
        if (any(type(sample.get(axis)) is not int for axis in ("x", "z"))
                or any(not isinstance(sample[field], str) or not sample[field].strip()
                       for field in ("terrain", "cellBiome", "biome"))
                or not isinstance(bits, dict) or not CELL_FIELDS <= bits.keys()
                or any(type(value) is not int or not -(1 << 31) <= value < (1 << 31)
                       or (value & 0x7f800000) == 0x7f800000 for value in bits.values())):
            raise ValueError(f"Missing or invalid terrain sample: {path}")
    by_position = {(sample["x"], sample["z"]): sample for sample in samples}
    if len(by_position) != len(samples):
        raise ValueError(f"Duplicate sample coordinates: {path}")
    return generation, by_position


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left", type=Path)
    parser.add_argument("right", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    left_generation, left = load(args.left)
    right_generation, right = load(args.right)
    if left_generation["seed"] != right_generation["seed"] or left.keys() != right.keys():
        raise ValueError("Comparisons require the same seed and exact coordinate set")
    if any(left_generation.get(bound) != right_generation.get(bound) for bound in ("minY", "maxY")):
        raise ValueError("Comparisons require the same dimension height bounds")
    differences = []
    feature_differences = []
    canonical = []
    for position in sorted(left):
        a, b = left[position], right[position]
        for field in FIELDS:
            if a[field] != b[field]:
                differences.append({"position": position, "field": field, "left": a[field], "right": b[field]})
        changed = [field for field in ("surfaceY", "surfaceBlock", "columnSha256", "oceanFloorWgY",
                                      "columnBlockStatesSha256", "terrainFloorY")
                   if a.get(field) != b.get(field)]
        if changed:
            feature_differences.append({"position": position, "fields": changed})
        canonical.append({"x": position[0], "z": position[1], **{field: a[field] for field in FIELDS}})
    report = {"schemaVersion": 1, "status": "passed" if not differences else "failed",
              "inputs": [{"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
                         for path in (args.left, args.right)],
              "seed": left_generation["seed"], "sampleCount": len(left), "fields": FIELDS,
              "differences": differences, "postFeatureObservationsDiffer": feature_differences,
              "terrainFieldDigest": hashlib.sha256(json.dumps(canonical, sort_keys=True,
                  separators=(",", ":")).encode()).hexdigest(),
              "scope": "Exact recorded field/classification/biome comparison; not complete block, visual or all-seed parity"}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "sampleCount": len(left),
                      "terrainDifferences": len(differences), "postFeatureDifferences": len(feature_differences)}))
    return 0 if not differences else 1


if __name__ == "__main__":
    raise SystemExit(main())
