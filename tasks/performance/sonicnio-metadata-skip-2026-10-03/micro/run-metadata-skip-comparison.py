"""Host metadata micro test, not audio throughput or an Android benchmark.

Usage: python3 tools/bench/run-metadata-skip-comparison.py SCRATCH PRESERVED_DECODER
An explicit preserved baseline is required; this runner never reads a HEAD baseline.
"""
import hashlib
import json
import pathlib
import platform
import shutil
import statistics
import struct
import subprocess
import sys

ORDER = ("before", "after", "after", "before", "before", "after")
METRICS = ("bytes_per_op", "nanos_per_op")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def execute(command, stdout, stderr, timeout=60):
    with stdout.open("w") as out, stderr.open("w") as errors:
        result = subprocess.run(command, stdout=out, stderr=errors, text=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError(f"Command exited {result.returncode}; inspect {stderr}")


def main():
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    root = pathlib.Path(__file__).resolve().parents[2]
    scratch = pathlib.Path(sys.argv[1]).resolve()
    if scratch.exists() and any(scratch.iterdir()):
        raise SystemExit(f"Refusing to overwrite existing evidence: {scratch}")
    scratch.mkdir(parents=True, exist_ok=True)
    baseline = pathlib.Path(sys.argv[2]).resolve(strict=True)
    candidate = root / "library/JustFLAC/src/java/io/nayuki/flac/decode/FlacDecoder.java"
    classpath_file = root / "build/bench/core-test-classpath.txt"
    classpath = classpath_file.read_text().strip()
    shutil.copy2(classpath_file, scratch / "classpath.txt")
    java_source = root / "tools/bench/FlacMetadataSkipBenchmark.java"
    shutil.copy2(java_source, scratch / java_source.name)
    shutil.copy2(pathlib.Path(__file__), scratch / pathlib.Path(__file__).name)
    sources = {}
    outputs = {}
    for label, original in (("before", baseline), ("after", candidate)):
        source = scratch / label / "source/FlacDecoder.java"
        source.parent.mkdir(parents=True)
        shutil.copy2(original, source)
        sources[label] = source
        outputs[label] = scratch / label / "classes"
        outputs[label].mkdir()
    fixture_dir = scratch / "fixtures"
    fixture_dir.mkdir()
    # Valid 34-byte STREAMINFO: 4096 blocks, unknown frame sizes/MD5, stereo 24/96.
    packed = (96000 << 44) | (1 << 41) | (23 << 36) | 96000
    streaminfo = struct.pack(">HH", 4096, 4096) + bytes(6) + struct.pack(">Q", packed) + bytes(16)
    fixtures = {}
    for name, padding in (("streaminfo-only", 0), ("unused-padding-1mib", 1 << 20)):
        data = b"fLaC" + bytes((0 if padding else 0x80, 0, 0, 34)) + streaminfo
        if padding:
            data += bytes((0x81,)) + padding.to_bytes(3, "big") + bytes(padding)
        path = fixture_dir / f"{name}.flac"
        path.write_bytes(data)
        fixtures[name] = path
    metadata = {
        "description": "Host metadata micro test; synthetic metadata-only fixtures, no audio frames",
        "baseline_original": str(baseline), "candidate_original": str(candidate),
        "order_per_fixture": ORDER, "warmups": 64, "samples_per_process": 3,
        "operations_per_sample": 128, "processes_per_variant_per_fixture": 3,
        "jvm_flags": ["-Xms128m", "-Xmx128m"], "host": platform.platform(),
        "limits": ["Host JVM and filesystem cache, not Android runtime or phone playback",
                   "Three fresh JVMs per variant; 64 warmups may leave compilation noise",
                   "Timing includes open/close, reflection, and equal correctness checks",
                   "Padding payload only; not a representative distribution of music metadata"],
        "sha256": {str(path.relative_to(scratch)): digest(path)
                   for path in [*sources.values(), *fixtures.values(), scratch / java_source.name,
                                scratch / pathlib.Path(__file__).name, scratch / "classpath.txt"]},
    }
    (scratch / "manifest.json").write_text(json.dumps(metadata, indent=2) + "\n")
    execute(["java", "-version"], scratch / "java-version.stdout", scratch / "java-version.stderr")
    bench = scratch / "bench"
    bench.mkdir()
    execute(["javac", "-cp", classpath, "-d", str(bench), str(scratch / java_source.name)],
            scratch / "compile-bench.stdout", scratch / "compile-bench.stderr")
    for label in ORDER[:2]:
        execute(["javac", "-cp", classpath, "-d", str(outputs[label]), str(sources[label])],
                scratch / f"compile-{label}.stdout", scratch / f"compile-{label}.stderr")
    rows = []
    commands = []
    raw = scratch / "raw"
    raw.mkdir()
    for name, fixture in fixtures.items():
        for index, label in enumerate(ORDER):
            identifier = f"{name}-{index}-{label}"
            loaded = str(outputs[label]) + ":" + str(bench) + ":" + classpath
            command = ["java", *metadata["jvm_flags"], "-cp", loaded,
                       "FlacMetadataSkipBenchmark", identifier, str(fixture),
                       "legacy" if label == "before" else "streaming", str(outputs[label])]
            commands.append(command)
            stdout = raw / f"{identifier}.jsonl"
            execute(command, stdout, raw / f"{identifier}.stderr")
            parsed = [json.loads(line) for line in stdout.read_text().splitlines()]
            if len(parsed) != 3 or [row["sample"] for row in parsed] != [0, 1, 2]:
                raise RuntimeError(f"Expected three ordered samples: {stdout}")
            for row in parsed:
                if pathlib.Path(row["decoder_origin"]).resolve() != outputs[label].resolve():
                    raise RuntimeError(f"Unexpected decoder origin in {stdout}")
                if row["checksum"] != (96000 + fixture.stat().st_size) * 128:
                    raise RuntimeError(f"Unexpected checksum in {stdout}")
                row.update(variant=label, fixture_name=name, process_index=index)
            rows.extend(parsed)
    (scratch / "commands.json").write_text(json.dumps(commands, indent=2) + "\n")
    (scratch / "results.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows))
    summary = {}
    for name in fixtures:
        result = {}
        for label in ("before", "after"):
            selected = [r for r in rows if r["variant"] == label and r["fixture_name"] == name]
            processes = sorted({row["process_index"] for row in selected})
            process_medians = [{metric: statistics.median(
                row[metric] for row in selected if row["process_index"] == index)
                for metric in METRICS} for index in processes]
            result[label] = {"processes": len(processes), "samples": len(selected),
                             "process_medians": process_medians,
                             **{metric: statistics.median(p[metric] for p in process_medians)
                                for metric in METRICS},
                             "sample_range": {metric: [min(r[metric] for r in selected),
                                                       max(r[metric] for r in selected)]
                                              for metric in METRICS}}
        result["percent_reduction"] = {
            metric: 100 * (1 - result["after"][metric] / result["before"][metric])
            for metric in METRICS}
        summary[name] = result
    (scratch / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    for name, result in summary.items():
        print(name + ": " + json.dumps({label: {metric: result[label][metric] for metric in METRICS}
                                        for label in ("before", "after")}))
    print(f"Raw evidence: {scratch}")


if __name__ == "__main__":
    main()
