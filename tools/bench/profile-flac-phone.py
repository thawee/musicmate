"""Matched traced/untraced seek workloads on preserved eager/lazy APKs.

Usage: python3 tools/bench/profile-flac-phone.py SCRATCH ADB SERIAL pilot|compare
Requires before.apk, after.apk and profile.pbtxt. Leaves candidate APK installed.
Perfetto captures scheduling, ART slices and process counters; app code is unchanged.
"""
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.request

scratch = pathlib.Path(sys.argv[1]).resolve()
adb = [sys.argv[2], "-s", sys.argv[3]]
mode = sys.argv[4]
if mode not in ("pilot", "compare"):
    raise ValueError("Mode must be pilot or compare")
package = "apincer.android.mmate"
url = "http://127.0.0.1:19000/music/2122216336/file"
headers = {"User-Agent": "LG webOS TV DLNADOC/1.50"}
next_request = 0.0
results = []
golden = None


def device(*args, input=None):
    result = subprocess.run(adb + list(args), input=input, check=True, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
    return result.stdout.strip()


def install(label):
    apk = scratch / (label + ".apk")
    device("install", "-r", str(apk))
    path = device("shell", "pm", "path", package).splitlines()[0].removeprefix("package:")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    if device("shell", "sha256sum", path).split()[0] != digest:
        raise AssertionError("APK hash mismatch")
    device("shell", "am", "start", "-W", "-n", package + "/apincer.android.mmate.ui.MainActivity")
    return digest


def calibrate():
    samples = []
    for _ in range(5):
        start = time.time_ns()
        stamp = device("shell", "date", "+%s.%N")
        end = time.time_ns()
        seconds, fraction = stamp.split(".")
        device_epoch = int(seconds) * 1_000_000_000 + int(fraction.ljust(9, "0"))
        samples.append({"offset_ns": device_epoch - (start + end) // 2,
                        "uncertainty_ns": (end - start) // 2, "host_start_ns": start, "host_end_ns": end})
    return {"best": min(samples, key=lambda r: r["uncertainty_ns"]), "samples": samples}


def fetch(method="GET", byte_range=None):
    global next_request
    delay = next_request - time.monotonic()
    if delay > 0:
        time.sleep(delay)
    next_request = time.monotonic() + .05
    request_headers = headers | ({"Range": byte_range} if byte_range else {})
    started = time.monotonic_ns()
    epoch = time.time_ns()
    with urllib.request.urlopen(urllib.request.Request(url, method=method, headers=request_headers), timeout=20) as response:
        first = time.monotonic_ns()
        epoch_first = time.time_ns()
        body = response.read()
        if method == "HEAD":
            if response.status != 200 or body or int(response.headers["Content-Length"]) != len(golden):
                raise AssertionError("Invalid converted HEAD")
        elif len(body) != int(response.headers["Content-Length"]):
            raise AssertionError("Incomplete response")
        if byte_range:
            start, end = map(int, byte_range.removeprefix("bytes=").split("-"))
            if response.status != 206 or body != golden[start:end + 1]:
                raise AssertionError("Converted range mismatch")
        return {"epoch_start_ns": epoch, "epoch_headers_ns": epoch_first,
                "ttfb_ms": (first - started) / 1e6, "duration_ms": (time.monotonic_ns() - started) / 1e6,
                "status": response.status, "method": method, "range": byte_range, "size": len(body)}, body


def memory(name):
    text = device("shell", "dumpsys", "meminfo", "--local", package)
    (scratch / (name + "-memory.txt")).write_text(text + "\n")
    return {"pid": int(re.search(r"MEMINFO in pid (\d+)", text).group(1)),
            "pss_kib": int(re.search(r"TOTAL PSS:\s*(\d+)", text).group(1)),
            "java_heap_pss_kib": int(re.search(r"Java Heap:\s*(\d+)", text).group(1))}


device("forward", "tcp:19000", "tcp:9000")
order = ("after",) if mode == "pilot" else ("before", "after", "after", "before")
try:
    for index, label in enumerate(order):
        digest = install(label)
        _, reference = fetch()
        if reference[:4] != b"RIFF" or reference[8:12] != b"WAVE":
            raise AssertionError("Not converted WAV")
        if golden is None:
            golden = reference
        elif reference != golden:
            raise AssertionError("WAV changed between APKs")
        # Equal dedicated warmup before either measured phase, below rate limit.
        for _ in range(64):
            fetch("HEAD")
        for seek in range(16):
            start = 44 + seek * 313337 % (len(golden) - 44 - 65536)
            fetch(byte_range=f"bytes={start}-{start + 65535}")
        phases = ("control", "trace") if index % 2 == 0 else ("trace", "control")
        for phase in phases:
            name = f"{mode}-{label}-{index}-{phase}"
            before = memory(name + "-before")
            calibration = calibrate()
            trace_pid = None
            remote = f"/data/misc/perfetto-traces/musicmate-{name}.pftrace"
            try:
                if phase == "trace":
                    ack = device("shell", "perfetto", "--background-wait", "--txt", "-c", "-", "-o", remote,
                                 input=(scratch / "profile.pbtxt").read_text())
                    (scratch / (name + "-trace-ack.txt")).write_text(ack + "\n")
                    if not ack.isdigit():
                        raise AssertionError("Unexpected background trace PID: " + ack)
                    trace_pid = ack
                samples = []
                for _ in range(64):
                    stats, _ = fetch("HEAD")
                    samples.append(stats | {"kind": "head"})
                    stats, _ = fetch(byte_range="bytes=0-43")
                    samples.append(stats | {"kind": "header"})
                for seek in range(48):
                    start = 44 + seek * 313337 % (len(golden) - 44 - 65536)
                    stats, _ = fetch(byte_range=f"bytes={start}-{start + 65535}")
                    samples.append(stats | {"kind": "seek", "offset": start})
                after = memory(name + "-after")
                if after["pid"] != before["pid"]:
                    raise AssertionError("App process restarted")
                result = {"name": name, "variant": label, "phase": phase, "apk_sha256": digest,
                          "samples": samples, "memory_before": before, "memory_after": after,
                          "clock_before": calibration, "clock_after": calibrate(),
                          "wav_sha256": hashlib.sha256(golden).hexdigest()}
            finally:
                if trace_pid is not None:
                    device("shell", "kill", "-TERM", trace_pid)
                    time.sleep(1)  # Allow the background trace to flush after graceful shutdown.
                    device("pull", remote, str(scratch / (name + ".pftrace")))
            results.append(result)
            (scratch / (mode + "-results.json")).write_text(json.dumps(results, indent=2) + "\n")
            print(f"{name}: {len(samples)} requests verified; external PSS {before['pss_kib']} → {after['pss_kib']} KiB", flush=True)
finally:
    install("after")
print("Candidate installed and hash verified", flush=True)
