"""Run precompiled before.jar/after.jar metadata harnesses in standalone ART on a phone.

Usage: python3 tools/bench/run-flac-android-micro.py SCRATCH ADB SERIAL
Run after application measurements so the extra ART processes cannot perturb them.
SCRATCH contains the DEX jars and receives raw samples, logs and checked device hashes.
"""
import hashlib
import json
import pathlib
import statistics
import subprocess
import sys

scratch = pathlib.Path(sys.argv[1]).resolve()
adb = [sys.argv[2], "-s", sys.argv[3]]
remote = "/data/local/tmp/musicmate-lazy-flac-metadata-20261003"


def device(*args):
    return subprocess.run(adb + list(args), check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60).stdout.strip()


device("shell", "mkdir", "-p", remote)
hashes = {}
for label in ("before", "after"):
    jar = scratch / (label + ".jar")
    device("push", str(jar), remote + "/" + jar.name)
    expected = hashlib.sha256(jar.read_bytes()).hexdigest()
    actual = device("shell", "sha256sum", remote + "/" + jar.name).split()[0]
    if actual != expected:
        raise AssertionError("Device DEX jar hash differs")
    hashes[label] = expected
(scratch / "device-dex-hashes.json").write_text(json.dumps(hashes, indent=2) + "\n")
rows = []
with (scratch / "android-results.jsonl").open("w") as out:
    for index, label in enumerate(("before", "after", "after", "before", "before", "after")):
        result = subprocess.run(adb + ["shell", "dalvikvm", "-cp", remote + "/" + label + ".jar",
                                      "FlacMetadataAndroidBenchmark", f"{label}-{index}", remote],
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
        (scratch / f"{label}-{index}.stderr").write_text(result.stderr)
        (scratch / f"{label}-{index}.stdout").write_text(result.stdout)
        if result.returncode:
            raise RuntimeError(f"ART benchmark failed: {label}-{index}; inspect saved stderr")
        parsed = [json.loads(line) for line in result.stdout.splitlines() if line.startswith("{")]
        if len(parsed) != 3:
            raise AssertionError("Expected three ART samples")
        for row in parsed:
            out.write(json.dumps(row) + "\n")
        out.flush()
        rows.extend(parsed)
        print(f"{index + 1}/6 {label}: ART allocation/GC samples saved", flush=True)
summary = {}
for label in ("before", "after"):
    selected = [r for r in rows if r["label"].startswith(label)]
    summary[label] = {"samples": len(selected), "processes": 3,
                      "measured_gc_count": sum(r["gc_count_delta"] for r in selected),
                      "measured_gc_time_ms": sum(r["gc_time_ms_delta"] for r in selected),
                      **{key: statistics.median(r[key] for r in selected)
                         for key in ("bytes_per_op", "nanos_per_op")}}
(scratch / "android-summary.json").write_text(json.dumps(summary, indent=2) + "\n")
print(json.dumps(summary, indent=2))
