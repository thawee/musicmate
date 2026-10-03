"""Interleaved physical-phone comparison of preserved lazy-FLAC APKs.

Usage: python3 tools/bench/run-flac-phone-comparison.py SCRATCH ADB SERIAL
Requires before.apk/after.apk, physical-device and localhost access. Leaves after.apk installed.
Uses USB forwarding, not Wi-Fi. GC observations are bounded logcat records, not ART counters.
"""
import hashlib
import json
import math
import pathlib
import re
import statistics
import subprocess
import sys
import time
import urllib.request

root = pathlib.Path(__file__).resolve().parents[2]
scratch = pathlib.Path(sys.argv[1]).resolve()
adb = [sys.argv[2], "-s", sys.argv[3]]
package = "apincer.android.mmate"
base = "http://127.0.0.1:19000"
url = base + "/music/2122216336/file"
converted_headers = {"User-Agent": "LG webOS TV DLNADOC/1.50"}
harness = root / "tasks/performance/sonicnio-2026-10-03/harness/phone-check.py"
results = []
golden = None
next_request_at = 0.0


def device(*args):
    return subprocess.run(adb + list(args), check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60).stdout.strip()


def install(label):
    apk = scratch / (label + ".apk")
    device("install", "-r", str(apk))
    path = device("shell", "pm", "path", package).splitlines()[0].removeprefix("package:")
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    if device("shell", "sha256sum", path).split()[0] != expected:
        raise AssertionError("Installed APK hash mismatch")
    device("shell", "am", "start", "-W", "-n", package + "/apincer.android.mmate.ui.MainActivity")
    return expected


def fetch(method="GET", extra=None):
    global next_request_at
    # Stay below the app's 50 requests/second limit; pacing is outside request timing.
    delay = next_request_at - time.monotonic()
    if delay > 0:
        time.sleep(delay)
    next_request_at = time.monotonic() + .05
    headers = converted_headers | (extra or {})
    started = time.monotonic()
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers, method=method), timeout=20) as response:
        first = time.monotonic()
        body = response.read()
        length = int(response.headers["Content-Length"])
        if method == "HEAD":
            if body or response.status != 200 or response.headers.get("Content-Type") != "audio/wav":
                raise AssertionError("Invalid converted HEAD")
        elif len(body) != length:
            raise AssertionError("Incomplete body")
        return {"ttfb_ms": (first - started) * 1000, "seconds": time.monotonic() - started,
                "status": response.status, "declared_length": length,
                "content_range": response.headers.get("Content-Range")}, body


def timing(values):
    ordered = sorted(values)
    return {"count": len(values), "median_ms": statistics.median(values),
            "p95_ms": ordered[math.ceil(len(values) * .95) - 1],
            "p99_ms": ordered[math.ceil(len(values) * .99) - 1], "max_ms": ordered[-1]}


def snapshot(name):
    text = device("shell", "dumpsys", "meminfo", package)
    (scratch / (name + "-meminfo.txt")).write_text(text + "\n")
    pid = int(re.search(r"MEMINFO in pid (\d+)", text).group(1))
    total = re.search(r"TOTAL PSS:\s*(\d+)", text)
    heap = re.search(r"^\s*Dalvik Heap\s+(.+)$", text, re.MULTILINE)
    fields = heap.group(1).split()
    battery = device("shell", "dumpsys", "battery")
    def number(key):
        match = re.search(r"^\s*" + key + r":\s*(\d+)", battery, re.MULTILINE)
        return int(match.group(1)) if match else None
    return {"pid": pid, "pss_kib": int(total.group(1)), "dalvik_heap_alloc_kib": int(fields[-2]),
            "dalvik_heap_size_kib": int(fields[-3]), "battery_tenths_c": number("temperature"),
            "battery_percent": number("level")}


def gc_since(pid, started, name):
    text = device("logcat", "-d", "-v", "epoch", "-t", "2000", "--pid", str(pid))
    lines = []
    for line in text.splitlines():
        stamp = re.match(r"\s*(\d+\.\d+)", line)
        if stamp and float(stamp.group(1)) >= started and re.search(r"\bGC\b.*(?:freed|paused)|concurrent.*\bGC\b", line):
            lines.append(line)
    (scratch / (name + "-gc.log")).write_text("\n".join(lines) + "\n")
    return {"visible_gc_records": len(lines), "records": lines,
            "method": "last 2000 logcat records for process since device-epoch phase start; incomplete GC coverage"}


def workload(name):
    run = subprocess.run([sys.executable, str(harness), base, name], text=True,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
    (scratch / (name + "-stderr.log")).write_text(run.stderr)
    if run.returncode:
        raise RuntimeError(f"Workload failed: {name}; inspect saved stderr")
    result = json.loads(run.stdout)
    (scratch / (name + ".json")).write_text(json.dumps(result, indent=2) + "\n")
    return result


device("forward", "tcp:19000", "tcp:9000")
(scratch / "device.json").write_text(json.dumps({
    key: device("shell", "getprop", key) for key in
    ("ro.product.model", "ro.build.version.release", "ro.build.version.sdk", "ro.build.fingerprint")}, indent=2) + "\n")
try:
    for index, label in enumerate(("after", "before", "before", "after", "before", "after")):
        digest = install(label)
        name = f"lazy-flac-{label}-{index}"
        warmup = workload(name + "-warmup")
        result = workload(name)
        full_stats, full = fetch()
        if full[:4] != b"RIFF" or full[8:12] != b"WAVE":
            raise AssertionError("Not WAV")
        native_headers = {"Range": "bytes=0-0"}
        with urllib.request.urlopen(urllib.request.Request(url, headers=native_headers), timeout=20) as response:
            if response.status != 206 or len(response.read()) != 1:
                raise AssertionError("Native metadata probe failed")
            metadata = {key: response.headers.get(key) for key in
                        ("ETag", "Last-Modified", "Content-Range", "Content-Type")}
        identity = (result["single"]["sha256"], result["converted"]["sha256"],
                    hashlib.sha256(full).hexdigest(), metadata)
        if golden is None:
            golden = identity
        if identity != golden or warmup["single"]["sha256"] != identity[0] or warmup["converted"]["sha256"] != identity[1]:
            raise AssertionError("Response identity differs between variants/warmups")
        # Warm the dedicated paths, excluding all timing from retained samples.
        for _ in range(16):
            fetch("HEAD")
            _, header = fetch(extra={"Range": "bytes=0-43"})
            if header != full[:44]:
                raise AssertionError("WAV header mismatch")
        phase = {"before": snapshot(name + "-before")}
        pid = phase["before"]["pid"]
        epoch = float(device("shell", "date", "+%s.%N"))
        head_times, header_times = [], []
        for block in range(4):
            for _ in range(32):
                stats, _ = fetch("HEAD")
                if stats["declared_length"] != len(full):
                    raise AssertionError("HEAD length mismatch")
                head_times.append(stats["ttfb_ms"])
                stats, header = fetch(extra={"Range": "bytes=0-43"})
                if stats["status"] != 206 or header != full[:44] or stats["content_range"] != f"bytes 0-43/{len(full)}":
                    raise AssertionError("Header-only range mismatch")
                header_times.append(stats["ttfb_ms"])
            phase[f"block-{block}"] = snapshot(name + f"-block-{block}")
        gc = gc_since(pid, epoch, name + "-metadata")
        seek_times, ranges = [], []
        for seek in range(48):
            start = 44 + (seek * 313337) % (len(full) - 44 - 65536)
            stats, part = fetch(extra={"Range": f"bytes={start}-{start + 65535}"})
            if stats["status"] != 206 or part != full[start:start + 65536]:
                raise AssertionError("Converted seek bytes differ")
            seek_times.append(stats["ttfb_ms"])
            ranges.append(stats | {"offset": start})
        phase["after"] = snapshot(name + "-after")
        if any(value["pid"] != pid for value in phase.values()):
            raise AssertionError("Process restarted during comparison")
        result.update({"apk_sha256": digest, "metadata_headers": metadata,
                       "converted_head": timing(head_times), "converted_header_range": timing(header_times),
                       "converted_seek": timing(seek_times), "head_samples_ms": head_times,
                       "header_range_samples_ms": header_times, "converted_range_samples": ranges,
                       "memory": phase, "metadata_gc": gc})
        results.append(result)
        (scratch / "phone.json").write_text(json.dumps(results, indent=2) + "\n")
        print(f"{index + 1}/6 {label}: hashes/ranges verified; HEAD {result['converted_head']['median_ms']:.3f} ms, "
              f"converted seek p99 {result['converted_seek']['p99_ms']:.3f} ms, visible metadata GC {gc['visible_gc_records']}", flush=True)
finally:
    # Restore the candidate even if a measured phase fails; preserve the original exception.
    if results and results[-1]["label"] == "lazy-flac-after-5" and len(results) == 6:
        pass
    else:
        try:
            install("after")
        except Exception as restoration:
            print(f"Candidate restoration failed: {restoration}", file=sys.stderr)
print("Candidate APK installed and hash verified", flush=True)
