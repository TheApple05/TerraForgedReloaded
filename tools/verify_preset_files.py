"""Check packaged Fabric preset imports and safe file replacement without a game window."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = ROOT / "tests/client/PresetFileProbe.java"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True, type=Path)
    parser.add_argument("--production-jar", required=True, type=Path)
    parser.add_argument("--classpath-file", type=Path, default=ROOT / "build/qa/target/compile-classpath.txt")
    parser.add_argument("--report", type=Path, default=ROOT / "build/qa/preset/report.json")
    args = parser.parse_args()
    production = args.production_jar.resolve()
    production_sha256 = hashlib.sha256(production.read_bytes()).hexdigest()
    classpath = str(production) + os.pathsep + args.classpath_file.read_text(encoding="utf-8").strip()
    base = ROOT / "build/qa/preset"
    base.mkdir(parents=True, exist_ok=True)
    suffix = ".exe" if os.name == "nt" else ""
    with tempfile.TemporaryDirectory(prefix="probe-", dir=base) as work:
        compile_run = subprocess.run([str(args.java_home / "bin" / ("javac" + suffix)), "--release", "25", "-proc:none",
            "-encoding", "UTF-8", "-cp", classpath, "-d", work, str(PROBE)], capture_output=True, text=True, cwd=ROOT, timeout=90)
        if compile_run.returncode:
            print(compile_run.stdout + compile_run.stderr, file=sys.stderr)
            return compile_run.returncode
        files = Path(work) / "files"
        files.mkdir()
        run = subprocess.run([str(args.java_home / "bin" / ("java" + suffix)), "-cp", work + os.pathsep + classpath,
            "raccoonman.reterraforged.client.gui.screen.presetconfig.PresetFileProbe", str(files), str(production)],
            capture_output=True, text=True, cwd=ROOT, timeout=90)
        log = run.stdout + run.stderr
        (base / "output.log").write_text(log, encoding="utf-8")
        markers = [line.split("PRESET_FILES_QA_JSON=", 1)[1] for line in run.stdout.splitlines() if "PRESET_FILES_QA_JSON=" in line]
        report = json.loads(markers[-1]) if markers else {"status": "fail"}
        report.update({"schemaVersion": 1, "runAtUtc": datetime.now(timezone.utc).isoformat(), "processExit": run.returncode,
            "compileExit": compile_run.returncode, "productionJar": str(production),
            "productionSha256": production_sha256,
            "probeSha256Lf": hashlib.sha256(PROBE.read_bytes().replace(b"\r\n", b"\n")).hexdigest(),
            "notTested": ["Rendered editor and user input", "Every numeric setting extreme", "All historical preset schemas", "Real world generation"]})
        report["artifactUnchanged"] = hashlib.sha256(production.read_bytes()).hexdigest() == production_sha256
        if run.returncode or not report["artifactUnchanged"]:
            report["status"] = "fail"
            print(log[-9000:], file=sys.stderr)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=2))
        return 0 if report["status"] == "pass" else 1


if __name__ == "__main__":
    raise SystemExit(main())
