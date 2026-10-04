"""Preserve parser variants, all successful/failed-build evidence and calculated comparisons."""
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

directory = pathlib.Path(__file__).resolve().parent
root = directory.parents[2]
evidence = directory / "evidence"
scratch = pathlib.Path("/private/tmp/musicmate-parser-20261003")
confirmation = pathlib.Path("/private/tmp/musicmate-parser-confirmation-20261003")
evidence.mkdir(exist_ok=True)
for name in ("micro.jsonl", "staged.jsonl", "throughput.jsonl", "micro.stderr", "staged.stderr", "throughput.stderr", "phone.json", "framing-phone.json"):
    shutil.copy2(scratch / name, evidence / name)
shutil.copy2(confirmation / "phone.json", evidence / "phone-confirmation.json")
for phase in ("baseline", "copy-only"):
    (evidence / phase).mkdir(exist_ok=True)
    for name in ("NioHttpServer.java", "BoundedByteArrayOutputStream.java"):
        shutil.copy2(scratch / phase / name, evidence / phase / name)
for name in ("validation", "validation-fixed", "validation-final", "compile", "compile-staged", "micro-progress", "staged-progress", "throughput-progress", "phone-progress", "phone-confirmation-progress"):
    shutil.copy2(pathlib.Path("/private/tmp/musicmate-parser-" + name + ".log"), evidence / (name + ".log"))
micro = {kind: [json.loads(line) for line in (evidence / (kind + ".jsonl")).read_text().splitlines()] for kind in ("micro", "staged")}
assert all(len(rows) == 180 for rows in micro.values())
host = [json.loads(line) for line in (evidence / "throughput.jsonl").read_text().splitlines()]
assert len(host) == 24 and all(row["errors"] == 0 for row in host)
phone_series = {"initial": json.loads((evidence / "phone.json").read_text()),
                "confirmation": json.loads((evidence / "phone-confirmation.json").read_text())}
assert all(len(rows) == 6 for rows in phone_series.values())
phones = phone_series["initial"] + phone_series["confirmation"]
assert all(row["range_checks"] == 16 and row["parallel_hash_matches"] == 4 for row in phones)
assert len({row["single"]["sha256"] for row in phones}) == 1
assert len({row["converted"]["sha256"] for row in phones}) == 1
assert len({json.dumps(row["metadata_headers"], sort_keys=True) for row in phones}) == 1
warmups = [json.loads(pathlib.Path("/private/tmp/musicmate-phone-" + row["label"] + "-warmup.json").read_text()) for row in phones]
(evidence / "phone-warmups.json").write_text(json.dumps(warmups, indent=2) + "\n")
framing = json.loads((evidence / "framing-phone.json").read_text())
assert len(framing) == 17 and all(row["expected"] == row["actual"] and row["closed"] for row in framing)


def stats(values):
    return {"median": statistics.median(values), "min": min(values), "max": max(values), "samples": len(values)}


def pair(before, after):
    result = {"before": stats(before), "after": stats(after)}
    result["change_percent"] = (result["after"]["median"] / result["before"]["median"] - 1) * 100
    return result


summary = {"micro": {}, "staged": {}, "host": {}, "phone": {}}
for kind, rows in micro.items():
    for size in sorted({row["body_size"] for row in rows}):
        summary[kind][str(size)] = {}
        for metric in ("ns_per_op", "bytes_per_op"):
            values = {phase: stats([row[metric] for row in rows if row["body_size"] == size and
                                   ("parser-" + phase + "-") in row["label"]])
                      for phase in ("baseline", "copy-only", "after")}
            values["baseline_to_final_percent"] = (values["after"]["median"] / values["baseline"]["median"] - 1) * 100
            summary[kind][str(size)][metric] = values
for mode in sorted({row["mode"] for row in host}):
    summary["host"][mode] = {}
    for metric in ("MiB_s", "seek_p95_ms"):
        if metric == "seek_p95_ms" and mode == "file-single": continue
        summary["host"][mode][metric] = pair(*[[row[metric] for row in host if row["mode"] == mode and
                                               ("parser-" + phase + "-") in row["label"]] for phase in ("baseline", "after")])
for series, rows in list(phone_series.items()) + [("combined", phones)]:
    summary["phone"][series] = {}
    for metric in ("seek_p50_ms", "seek_p95_ms", "parallel_MB_s", "single.seconds", "single.ttfb_ms", "converted.seconds", "converted.ttfb_ms"):
        def value(row):
            for key in metric.split("."): row = row[key]
            return row
        summary["phone"][series][metric] = pair(*[[value(row) for row in rows if phase in row["label"]] for phase in ("before", "after")])
(evidence / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
tests = []
for module in ("core", "server-jupnp"):
    for path in sorted((root / module / "build/test-results/testDebugUnitTest").glob("TEST-*.xml")):
        for case in ET.parse(path).getroot().findall("testcase"):
            status = "failed" if case.find("failure") is not None or case.find("error") is not None else "skipped" if case.find("skipped") is not None else "passed"
            tests.append({"module": module, "class": case.get("classname"), "test": case.get("name"), "seconds": float(case.get("time", "0")), "status": status})
assert len(tests) == 219 and all(row["status"] == "passed" for row in tests)
(evidence / "junit-results.json").write_text(json.dumps(tests, indent=2) + "\n")
text = "# Parser optimization: individual regression results\n\n176 core + 43 UPnP = 219 cases pass; no failures/errors/skips.\n\n| Module | Class | Test | Seconds | Result |\n| --- | --- | --- | ---: | --- |\n"
for row in tests:
    text += f"| {row['module']} | {row['class']} | {row['test']} | {row['seconds']:.3f} | {row['status']} |\n"
(directory / "TEST_RESULTS.md").write_text(text)
for label, old, new in (("copy-only", scratch / "baseline", scratch / "copy-only"),
                        ("framing", scratch / "copy-only", root / "core/src/main/java/apincer/music/core/http"),
                        ("complete", scratch / "baseline", root / "core/src/main/java/apincer/music/core/http")):
    delta = ""
    for name in ("NioHttpServer.java", "BoundedByteArrayOutputStream.java"):
        delta += "".join(difflib.unified_diff((old / name).read_text().splitlines(True), (new / name).read_text().splitlines(True), fromfile="before/" + name, tofile="after/" + name))
    (evidence / (label + ".patch")).write_text(delta)
baseline = json.loads((evidence / "baseline.json").read_text())
assert all(hashlib.sha256((scratch / "baseline" / name).read_bytes()).hexdigest() == digest for name, digest in baseline["sources"].items())
manifest = {"recorded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "host": platform.platform(),
            "java": subprocess.run(["java", "-version"], capture_output=True, text=True, check=True).stderr.strip(),
            "head": subprocess.run(["git", "rev-parse", "HEAD"], capture_output=True, text=True, check=True).stdout.strip(),
            "worktree_dirty": True, "phone": "Galaxy S25 / SM-S931B / <device-serial>; USB forwarding 19000 to 9000",
            "apk_sha256": {phase: hashlib.sha256((scratch / (phase + ".apk")).read_bytes()).hexdigest() for phase in ("before", "after")}, "sha256": {}}
assert manifest["apk_sha256"]["before"] == baseline["apk_sha256"]
assert all(row["apk_sha256"] == manifest["apk_sha256"]["before" if "before" in row["label"] else "after"] for row in phones)
paths = list(evidence.rglob("*")) + list((root / "core/src/main/java/apincer/music/core/http").glob("*.java"))
paths += [directory / "REPORT.md", directory / "TEST_RESULTS.md", pathlib.Path(__file__).resolve(), root / "PERFORMANCE.md"]
paths += [root / "tools/bench" / name for name in ("ParserBenchmark.java", "ThroughputBenchmark.java", "run-parser-comparison.py", "run-metadata-phone.py", "check-phone-framing.py")]
paths += [root / "core/src/test/java/apincer/music/core/http" / name for name in ("HttpRequestTest.java", "NioHttpServerTest.java")]
for path in sorted(paths):
    if path.is_file() and path.name != "manifest.json":
        manifest["sha256"][str(path.relative_to(root))] = hashlib.sha256(path.read_bytes()).hexdigest()
(evidence / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print("360 extraction samples, 24 transfer samples, 12 phone runs, 17 phone framing checks and 219 tests archived")
