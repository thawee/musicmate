"""Interleave preserved APKs with one unreported full-workload warmup per install.

Usage: python3 tools/bench/run-metadata-phone.py SCRATCH ADB SERIAL
Uses the archived phone-check workload/track and USB forwarding at localhost:19000.
Requires explicit device/network access. Restores the optimized APK at completion.
"""
import hashlib
import json
import pathlib
import subprocess
import sys
import urllib.request

root = pathlib.Path(__file__).resolve().parents[2]
scratch = pathlib.Path(sys.argv[1]).resolve()
experiment = scratch.name.removeprefix("musicmate-").removesuffix("-20261003")
adb = [sys.argv[2], "-s", sys.argv[3]]
base = "http://127.0.0.1:19000"
harness = root / "tasks/performance/sonicnio-2026-10-03/harness/phone-check.py"
results = []
golden_headers = None
golden_hashes = None


def device(*args):
    return subprocess.run(adb + list(args), check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60).stdout.strip()


def install(label):
    apk = scratch / (label + ".apk")
    device("install", "-r", str(apk))
    path = device("shell", "pm", "path", "apincer.android.mmate").splitlines()[0].removeprefix("package:")
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    actual = device("shell", "sha256sum", path).split()[0]
    if actual != expected:
        raise AssertionError("installed APK differs from snapshot")
    device("shell", "am", "start", "-W", "-n", "apincer.android.mmate/apincer.android.mmate.ui.MainActivity")
    return expected


device("forward", "tcp:19000", "tcp:9000")
for index, label in enumerate(("after", "before", "before", "after", "before", "after")):
    digest = install(label)
    name = f"{experiment}-controlled-{label}-{index}"
    # No timing from startup or this warmup enters the retained measured samples.
    for suffix in ("-warmup", ""):
        run = subprocess.run([sys.executable, str(harness), base, name + suffix], text=True,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
        if run.returncode:
            (scratch / "phone-failure.log").write_text(run.stderr)
            raise RuntimeError(f"phone workload failed; inspect {scratch / 'phone-failure.log'}")
        result = json.loads(run.stdout)
        if not suffix:
            result["apk_sha256"] = digest
            with urllib.request.urlopen(urllib.request.Request(base + "/music/2122216336/file",
                                         headers={"Range": "bytes=0-0"}), timeout=15) as response:
                if response.status != 206 or len(response.read()) != 1:
                    raise AssertionError("header probe is not a one-byte range")
                result["metadata_headers"] = {key: response.headers.get(key) for key in
                                              ("ETag", "Last-Modified", "Content-Range", "Content-Type")}
            hashes = (result["single"]["sha256"], result["converted"]["sha256"])
            if golden_headers is None:
                golden_headers, golden_hashes = result["metadata_headers"], hashes
            if result["metadata_headers"] != golden_headers or hashes != golden_hashes:
                raise AssertionError("response metadata or body changed between APKs")
            results.append(result)
            (scratch / "phone.json").write_text(json.dumps(results, indent=2) + "\n")
    print(f"{index + 1}/6 {label}: hashes, metadata, 16 ranges and four concurrent bodies verified", flush=True)
print("Optimized APK installed and verified", flush=True)
