"""Compile preserved baseline/copy-only/current variants, then compare micro or socket workloads."""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
scratch = pathlib.Path(sys.argv[1]).resolve()
action = sys.argv[2]
classpath = (root / "build/bench/core-test-classpath.txt").read_text().strip()
variants = {name: scratch / (name + "-classes") for name in ("baseline", "copy-only", "after")}
bench = scratch / "bench"
if action == "compile":
    bench.mkdir(exist_ok=True)
    subprocess.run(["javac", "-cp", classpath, "-d", str(bench), str(root / "tools/bench/ParserBenchmark.java"),
                    str(root / "tools/bench/ThroughputBenchmark.java")], check=True)
    for name, destination in variants.items():
        destination.mkdir(exist_ok=True)
        source = root / "core/src/main/java/apincer/music/core/http" if name == "after" else scratch / name
        subprocess.run(["javac", "-cp", classpath, "-d", str(destination)] +
                       [str(source / file) for file in ("NioHttpServer.java", "BoundedByteArrayOutputStream.java")], check=True)
    print("Three isolated variants compiled", flush=True)
elif action in ("micro", "staged", "throughput"):
    order = ("baseline", "copy-only", "after", "after", "copy-only", "baseline", "baseline", "copy-only", "after") if action != "throughput" else ("baseline", "after", "after", "baseline")
    with (scratch / (action + ".jsonl")).open("w") as output, (scratch / (action + ".stderr")).open("w") as errors:
        for index, name in enumerate(order):
            modes = (None,) if action != "throughput" else ("file-single", "file-four")
            for mode in modes:
                main = "ParserBenchmark" if mode is None else "ThroughputBenchmark"
                args = [f"parser-{name}-{index}"] + (["262144", "true", mode] if mode else [])
                if action == "staged": args += ["staged"]
                process = subprocess.run(["java", "-Xms256m", "-Xmx256m", "-cp",
                                          str(variants[name]) + ":" + str(bench) + ":" + classpath,
                                          "apincer.music.core.http." + main] + args,
                                         stdout=subprocess.PIPE, stderr=errors, text=True, timeout=120)
                if process.returncode:
                    raise RuntimeError(f"{name} failed; inspect {errors.name}")
                rows = [json.loads(line) for line in process.stdout.splitlines()]
                if len(rows) != (20 if mode is None else 3):
                    raise RuntimeError("incomplete results")
                if mode is None and any(str(variants[name]) not in row["class_origin"] for row in rows):
                    raise RuntimeError("wrong class origin")
                output.write(process.stdout); output.flush()
                print(f"{index + 1}/{len(order)} {name}/{mode or 'micro'}: {len(rows)} samples", flush=True)
else:
    raise ValueError(action)
