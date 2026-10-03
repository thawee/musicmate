"""Compare preserved/current FileResponse against the same exported runtime.

Usage: python3 tools/bench/run-metadata-comparison.py SCRATCH compile|micro|throughput
SCRATCH/baseline/FileResponse.java must be saved before editing production source.
Run the Gradle classpath export first. Socket measurements require localhost access.
"""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
scratch = pathlib.Path(sys.argv[1]).resolve()
action = sys.argv[2]
experiment = scratch.name.removeprefix("musicmate-").removesuffix("-20261003")
classpath = (root / "build/bench/core-test-classpath.txt").read_text().strip()
bench = scratch / "bench"
origins = {label: scratch / label for label in ("before", "after")}

if action == "compile":
    bench.mkdir(exist_ok=True)
    subprocess.run(["javac", "-cp", classpath, "-d", str(bench),
                    str(root / "tools/bench/MetadataBenchmark.java"),
                    str(root / "tools/bench/ThroughputBenchmark.java")], check=True)
    for label, source in (("before", scratch / "baseline/FileResponse.java"),
                          ("after", root / "core/src/main/java/apincer/music/core/http/FileResponse.java")):
        origins[label].mkdir(exist_ok=True)
        subprocess.run(["javac", "-cp", classpath, "-d", str(origins[label]), str(source)], check=True)
    print("Compiled isolated before/after classes and benchmark harnesses", flush=True)
elif action in ("micro", "throughput"):
    confirmation = action == "throughput" and len(sys.argv) > 3
    if confirmation and sys.argv[3] not in ("file-single", "file-four", "pcm-four"):
        raise ValueError(sys.argv[3])
    order = ["before", "after", "after", "before", "before", "after"] if action == "micro" or confirmation else ["before", "after", "after", "before"]
    series = action + ("-confirmation" if confirmation else "")
    output = scratch / (series + ".jsonl")
    with output.open("w") as out, (scratch / (series + ".stderr")).open("w") as errors:
        for index, label in enumerate(order):
            loaded = str(origins[label]) + ":" + str(bench) + ":" + classpath
            modes = [sys.argv[3]] if confirmation else [None] if action == "micro" else ["file-single", "file-four"]
            for mode in modes:
                name = "apincer.music.core.http." + ("MetadataBenchmark" if action == "micro" else "ThroughputBenchmark")
                args = [f"{experiment}-{'confirmation-' if confirmation else ''}{label}-{index}"]
                if mode:
                    args += ["262144", "true", mode]
                result = subprocess.run(["java", "-Xms256m", "-Xmx256m", "-cp", loaded, name] + args,
                                        stdout=subprocess.PIPE, stderr=errors, text=True, timeout=90)
                if result.returncode:
                    raise RuntimeError(f"{label}/{mode} failed: {result.returncode}; inspect {errors.name}")
                rows = [json.loads(line) for line in result.stdout.splitlines()]
                expected = 20 if action == "micro" else 3
                if len(rows) != expected:
                    raise RuntimeError(f"expected {expected} rows, received {len(rows)}")
                if action == "micro" and any(str(origins[label]) not in row["class_origin"] for row in rows):
                    raise RuntimeError("wrong FileResponse class loaded")
                out.write(result.stdout)
                out.flush()
                print(f"{index + 1}/{len(order)} {label} {mode or 'metadata'}: {len(rows)} samples", flush=True)
    print(str(output), flush=True)
else:
    raise ValueError(action)
