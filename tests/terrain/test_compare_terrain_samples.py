"""Parity evidence must reject incomplete inputs as well as unequal fields."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "tools/qa/compare_terrain_samples.py"
SPEC = importlib.util.spec_from_file_location("compare_terrain_samples", SCRIPT)
COMPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(COMPARE)


class TerrainComparisonTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        self.data = {"customTerrainValidated": True, "qaResult": {"status": "passed",
                     "generation": {"seed": 8675309, "minY": -64, "maxY": 319,
                                    "samples": [{"x": -16, "z": 0, "terrain": "plains",
                                                 "cellBiome": "GRASSLAND", "biome": "minecraft:plains",
                                                 "cellFloatBits": dict.fromkeys(COMPARE.CELL_FIELDS, 0)}]}}}

    def write(self, name, data):
        path = self.root / name
        path.write_text(json.dumps(data), encoding="utf-8")
        return path

    def test_rejects_missing_or_invalid_sample_values(self):
        mutations = [
            ("cellFloatBits", {}), ("cellFloatBits", None),
            ("terrain", None), ("cellBiome", ""), ("biome", " "),
            ("x", None), ("z", True),
        ]
        for field, value in mutations:
            with self.subTest(field=field, value=value):
                data = copy.deepcopy(self.data)
                data["qaResult"]["generation"]["samples"][0][field] = value
                with self.assertRaises(ValueError):
                    COMPARE.load(self.write("invalid.json", data))

    def test_requires_boolean_custom_terrain_acceptance(self):
        for value in (False, None, "false", "true", 1):
            with self.subTest(value=value):
                data = copy.deepcopy(self.data)
                data["customTerrainValidated"] = value
                with self.assertRaises(ValueError):
                    COMPARE.load(self.write("invalid-acceptance.json", data))

    def test_rejects_missing_or_nonfinite_raw_fields(self):
        for value in (None, True, 1.0, 1 << 31, 0x7f800000, -8388608, 0x7fc00000):
            with self.subTest(value=value):
                data = copy.deepcopy(self.data)
                data["qaResult"]["generation"]["samples"][0]["cellFloatBits"]["height"] = value
                with self.assertRaises(ValueError):
                    COMPARE.load(self.write("invalid.json", data))
        del self.data["qaResult"]["generation"]["samples"][0]["cellFloatBits"]["height"]
        with self.assertRaises(ValueError):
            COMPARE.load(self.write("missing.json", self.data))

    def test_rejects_missing_bounds_and_duplicate_coordinates(self):
        for field in ("seed", "minY", "maxY"):
            data = copy.deepcopy(self.data)
            del data["qaResult"]["generation"][field]
            with self.subTest(field=field), self.assertRaises(ValueError):
                COMPARE.load(self.write("missing.json", data))
        data = copy.deepcopy(self.data)
        data["qaResult"]["generation"]["minY"] = 320
        with self.assertRaises(ValueError):
            COMPARE.load(self.write("inverted-bounds.json", data))
        self.data["qaResult"]["generation"]["samples"] *= 2
        with self.assertRaises(ValueError):
            COMPARE.load(self.write("duplicate.json", self.data))

    def test_cli_retains_signed_zero_and_detects_terrain_changes(self):
        sample = self.data["qaResult"]["generation"]["samples"][0]
        sample["cellFloatBits"]["height"] = -(1 << 31)
        left = self.write("left.json", self.data)
        for changed in (False, True):
            sample["surfaceY"] = 80
            if changed:
                sample["cellFloatBits"]["height"] = 0
            right = self.write("right.json", self.data)
            report = self.root / "comparison.json"
            result = subprocess.run([sys.executable, str(SCRIPT), str(left), str(right),
                                     "--report", str(report)], capture_output=True, text=True)
            with self.subTest(changed=changed):
                self.assertEqual(result.returncode, int(changed), result.stderr)
                evidence = json.loads(report.read_text())
                self.assertEqual(evidence["status"], "failed" if changed else "passed")
                self.assertEqual(len(evidence["postFeatureObservationsDiffer"]), 1)


if __name__ == "__main__":
    unittest.main()
