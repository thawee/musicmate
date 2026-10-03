"""Archive this session's successful measurements, tests, source delta and digests."""
import datetime
import difflib
import hashlib
import json
import pathlib
import platform
import shutil
import statistics
import subprocess
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[3]
destination = pathlib.Path(__file__).resolve().parent
evidence = destination / "evidence"
scratch = pathlib.Path("/private/tmp/musicmate-metadata-20261003")
(evidence / "baseline").mkdir(parents=True, exist_ok=True)
shutil.copy2(scratch / "baseline/FileResponse.java", evidence / "baseline/FileResponse.java")
for name in ("micro.jsonl", "throughput.jsonl", "phone.json", "micro.stderr", "throughput.stderr"):
    shutil.copy2(scratch / name, evidence / name)
for name in ("validation", "micro-progress", "throughput-progress", "phone-progress"):
    shutil.copy2(pathlib.Path("/private/tmp/musicmate-metadata-" + name + ".log"), evidence / (name + ".log"))
micro = [json.loads(line) for line in (evidence / "micro.jsonl").read_text().splitlines()]
throughput = [json.loads(line) for line in (evidence / "throughput.jsonl").read_text().splitlines()]
phone = json.loads((evidence / "phone.json").read_text())
assert len(micro) == 120 and len(throughput) == 24 and len(phone) == 6
assert all(row["errors"] == 0 for row in throughput)
assert all(row["range_checks"] == 16 and row["parallel_hash_matches"] == 4 for row in phone)
assert len({row["single"]["sha256"] for row in phone}) == 1
assert len({row["converted"]["sha256"] for row in phone}) == 1
assert len({json.dumps(row["metadata_headers"], sort_keys=True) for row in phone}) == 1
warmups = [json.loads(pathlib.Path("/private/tmp/musicmate-phone-" + row["label"] + "-warmup.json").read_text()) for row in phone]
exploratory = [json.loads(pathlib.Path(f"/private/tmp/musicmate-phone-metadata-before-{index}.json").read_text()) for index in (1, 2, 3)]
(evidence / "phone-warmups.json").write_text(json.dumps(warmups, indent=2) + "\n")
(evidence / "phone-exploratory-before.json").write_text(json.dumps(exploratory, indent=2) + "\n")


def compare(before, after):
    first, second = statistics.median(before), statistics.median(after)
    return {"before_median": first, "after_median": second,
            "change_percent": (second / first - 1) * 100,
            "before_min": min(before), "before_max": max(before),
            "after_min": min(after), "after_max": max(after),
            "samples_per_phase": len(before)}


summary = {"micro": {}, "host": {}, "phone": {}}
for mode in sorted({row["mode"] for row in micro}):
    summary["micro"][mode] = {}
    for metric in ("ns_per_op", "bytes_per_op"):
        summary["micro"][mode][metric] = compare(*[
            [row[metric] for row in micro if row["mode"] == mode and phase in row["label"]]
            for phase in ("before", "after")])
for mode in sorted({row["mode"] for row in throughput}):
    summary["host"][mode] = {}
    for metric in ("MiB_s", "seek_p95_ms"):
        if metric == "seek_p95_ms" and mode == "file-single":
            continue
        summary["host"][mode][metric] = compare(*[
            [row[metric] for row in throughput if row["mode"] == mode and phase in row["label"]]
            for phase in ("before", "after")])
for metric in ("seek_p50_ms", "seek_p95_ms", "parallel_MB_s", "single.seconds", "single.ttfb_ms", "converted.seconds", "converted.ttfb_ms"):
    def value(row):
        result = row
        for key in metric.split("."):
            result = result[key]
        return result
    summary["phone"][metric] = compare(*[[value(row) for row in phone if phase in row["label"]]
                                          for phase in ("before", "after")])
(evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")

results = []
for module in ("core", "server-jupnp"):
    for path in sorted((root / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        for case in suite.findall("testcase"):
            outcome = "failed" if case.find("failure") is not None or case.find("error") is not None else "skipped" if case.find("skipped") is not None else "passed"
            results.append({"module": module, "class": case.get("classname"), "test": case.get("name"),
                            "seconds": float(case.get("time", "0")), "outcome": outcome})
assert len(results) == 207 and all(row["outcome"] == "passed" for row in results)
(evidence / "junit-results.json").write_text(json.dumps(results, indent=2) + "\n")
text = "# Metadata optimization: individual regression results\n\n164 core and 43 UPnP cases passed; zero failures/errors/skips. Timings are JUnit-reported seconds.\n\n| Module | Class | Test | Seconds | Result |\n| --- | --- | --- | ---: | --- |\n"
for row in results:
    text += f"| {row['module']} | {row['class']} | {row['test']} | {row['seconds']:.3f} | {row['outcome']} |\n"
(destination / "TEST_RESULTS.md").write_text(text)
source = root / "core/src/main/java/apincer/music/core/http/FileResponse.java"
baseline = scratch / "baseline/FileResponse.java"
delta = "".join(difflib.unified_diff(baseline.read_text().splitlines(True), source.read_text().splitlines(True),
                                    fromfile="before/FileResponse.java", tofile="after/FileResponse.java"))
(evidence / "metadata-only.patch").write_text(delta)
baseline_info = json.loads((evidence / "baseline.json").read_text())
assert hashlib.sha256(baseline.read_bytes()).hexdigest() == baseline_info["source_sha256"]
assert hashlib.sha256((scratch / "before.apk").read_bytes()).hexdigest() == baseline_info["apk_sha256"]
manifest = {"recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
            "host": platform.platform(), "java": subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr.strip(),
            "head": subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True, check=True).stdout.strip(),
            "worktree_dirty": True, "phone": "Samsung SM-S931B / <device-serial>; USB ADB tcp:19000 to tcp:9000",
            "apk_sha256": {phase: hashlib.sha256((scratch / (phase + ".apk")).read_bytes()).hexdigest() for phase in ("before", "after")},
            "sha256": {}}
assert all(row["apk_sha256"] == manifest["apk_sha256"]["before" if "before" in row["label"] else "after"] for row in phone)
paths = list(evidence.rglob("*")) + [destination / "TEST_RESULTS.md", destination / "REPORT.md",
                                    root / "PERFORMANCE.md", pathlib.Path(__file__).resolve()]
paths += list((root / "core/src/main/java/apincer/music/core/http").glob("*.java"))
paths += [root / "core/src/test/java/apincer/music/core/http/FileResponseTest.java"]
paths += [root / "tools/bench" / name for name in ("MetadataBenchmark.java", "ThroughputBenchmark.java", "run-metadata-comparison.py", "run-metadata-phone.py", "test-classpath.gradle")]
for path in sorted(paths):
    if path.is_file() and path.name != "manifest.json":
        manifest["sha256"][str(path.relative_to(root))] = hashlib.sha256(path.read_bytes()).hexdigest()
(evidence / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(json.dumps(summary, indent=2))
