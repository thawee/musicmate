"""Host-only metadata allocation comparison; requires exported core test classpath.

Usage: python3 tools/bench/run-flac-metadata-comparison.py SCRATCH
The baseline FlacDecoder source comes from HEAD; current source is compiled independently.
"""
import json
import pathlib
import statistics
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
scratch = pathlib.Path(sys.argv[1]).resolve()
scratch.mkdir(parents=True, exist_ok=True)
relative = "library/JustFLAC/src/java/io/nayuki/flac/decode/FlacDecoder.java"
classpath = (root / "build/bench/core-test-classpath.txt").read_text().strip()
baseline = scratch / "baseline/FlacDecoder.java"
baseline.parent.mkdir(exist_ok=True)
baseline.write_bytes(subprocess.check_output(["git", "show", "HEAD:" + relative], cwd=root))
revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
(scratch / "baseline-revision.txt").write_text(revision + "\n")
bench = scratch / "bench"
bench.mkdir(exist_ok=True)
subprocess.run(["javac", "-cp", classpath, "-d", str(bench),
                str(root / "tools/bench/FlacMetadataBenchmark.java")], check=True)
for label, source in (("before", baseline), ("after", root / relative)):
    output = scratch / label
    output.mkdir(exist_ok=True)
    subprocess.run(["javac", "-cp", classpath, "-d", str(output), str(source)], check=True)
rows = []
with (scratch / "results.jsonl").open("w") as out, (scratch / "stderr.log").open("w") as errors:
    for index, label in enumerate(("before", "after", "after", "before", "before", "after")):
        loaded = str(scratch / label) + ":" + str(bench) + ":" + classpath
        result = subprocess.run(["java", "-Xms128m", "-Xmx128m", "-cp", loaded,
                                 "FlacMetadataBenchmark", f"{label}-{index}", str(scratch / label)],
                                stdout=subprocess.PIPE, stderr=errors, text=True, timeout=60)
        if result.returncode:
            raise RuntimeError(f"{label} failed; inspect {scratch / 'stderr.log'}")
        parsed = [json.loads(line) for line in result.stdout.splitlines()]
        if len(parsed) != 3:
            raise RuntimeError(f"Expected three samples: {label}")
        out.write(result.stdout)
        rows.extend(parsed)
summary = {}
for label in ("before", "after"):
    selected = [r for r in rows if r["label"].startswith(label)]
    summary[label] = {"samples": len(selected), "processes": 3,
                      **{key: statistics.median(r[key] for r in selected)
                         for key in ("bytes_per_op", "nanos_per_op")}}
(scratch / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
