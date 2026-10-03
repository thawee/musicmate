"""Bounded app CPU-stack attribution with verified converted HTTP ranges.
Usage: python3 tools/bench/profile-stream-workers.py SCRATCH ADB SERIAL APK
Reinstalls the same APK once; restores the app activity after foreground/background captures.
Simpleperf sampling is diagnostic, not a baseline performance comparison.
"""
import hashlib
import json
import pathlib
import subprocess
import sys
import time
import urllib.request

root = pathlib.Path(sys.argv[1]).resolve()
root.mkdir(exist_ok=True)
adb = [sys.argv[2], '-s', sys.argv[3]]
apk = pathlib.Path(sys.argv[4]).resolve()
package = 'apincer.android.mmate'
activity = package + '/apincer.android.mmate.ui.MainActivity'
headers = {'User-Agent': 'LG webOS TV DLNADOC/1.50'}
url = 'http://127.0.0.1:19000/music/2122216336/file'
next_request = 0.0


def device(*args):
    result = subprocess.run(adb + list(args), text=True, capture_output=True, check=True, timeout=60)
    return result.stdout.strip()


def installed_hash():
    path = device('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
    return device('shell', 'sha256sum', path).split()[0]


def fetch(method='GET', byte_range=None):
    global next_request
    time.sleep(max(0, next_request - time.monotonic()))
    next_request = time.monotonic() + .05
    req = urllib.request.Request(url, method=method, headers=headers | ({'Range': byte_range} if byte_range else {}))
    start = time.monotonic_ns()
    with urllib.request.urlopen(req, timeout=20) as response:
        first = time.monotonic_ns()
        body = response.read()
        if method == 'HEAD':
            assert response.status == 200 and not body and int(response.headers['Content-Length']) == len(golden)
        elif byte_range:
            lo, hi = map(int, byte_range.removeprefix('bytes=').split('-'))
            assert response.status == 206 and body == golden[lo:hi + 1]
        else:
            assert response.status == 200 and len(body) == int(response.headers['Content-Length'])
        return {'method': method, 'range': byte_range, 'ttfb_ms': (first - start) / 1e6,
                'duration_ms': (time.monotonic_ns() - start) / 1e6, 'status': response.status}, body


device('forward', 'tcp:19000', 'tcp:9000')
expected = hashlib.sha256(apk.read_bytes()).hexdigest()
device('install', '-r', str(apk))
assert installed_hash() == expected
results = []
try:
    device('shell', 'am', 'start', '-W', '-n', activity)
    _, golden = fetch()
    assert golden[:4] == b'RIFF' and golden[8:12] == b'WAVE'
    assert hashlib.sha256(golden).hexdigest() == 'edb1ab13ad8e8dc03e8e283a40dd30d6344672671b0186a2b72a58bb01f1b676'
    for _ in range(64):
        fetch('HEAD')
    for seek in range(16):
        lo = 44 + seek * 313337 % (len(golden) - 44 - 65536)
        fetch(byte_range=f'bytes={lo}-{lo + 65535}')
    pid = device('shell', 'pidof', package)
    assert pid.isdigit()
    for phase in ('foreground-first', 'background', 'foreground-restored'):
        if phase == 'background':
            device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        else:
            device('shell', 'am', 'start', '-W', '-n', activity)
        (root / (phase + '-threads-before.txt')).write_text(device('shell', 'ps', '-T', '-p', pid) + '\n')
        remote = '/data/local/tmp/musicmate-workers-' + phase + '.data'
        cmd = adb + ['shell', 'simpleperf', 'record', '--app', package, '-p', pid,
                     '--duration', '12', '-g', '-e', 'task-clock:u', '-f', '199', '-o', remote]
        samples = []
        with (root / (phase + '-record.log')).open('w') as log:
            proc = subprocess.Popen(cmd, stdout=log, stderr=subprocess.STDOUT)
            try:
                time.sleep(1)
                for _ in range(64):
                    stats, _ = fetch('HEAD'); samples.append(stats | {'kind': 'head'})
                    stats, _ = fetch(byte_range='bytes=0-43'); samples.append(stats | {'kind': 'header'})
                for seek in range(48):
                    lo = 44 + seek * 313337 % (len(golden) - 44 - 65536)
                    stats, _ = fetch(byte_range=f'bytes={lo}-{lo + 65535}'); samples.append(stats | {'kind': 'seek'})
                code = proc.wait(timeout=20)
                if code:
                    raise RuntimeError(f'Simpleperf failed; inspect {phase}-record.log')
            finally:
                if proc.poll() is None:
                    proc.terminate(); proc.wait(timeout=20)
        assert device('shell', 'pidof', package) == pid
        device('pull', remote, str(root / (phase + '.data')))
        report = device('shell', 'simpleperf', 'report', '-i', remote, '--csv', '-n', '--sort', 'comm,pid,tid,dso,symbol')
        (root / (phase + '-report.csv')).write_text(report + '\n')
        calls = device('shell', 'simpleperf', 'report', '-i', remote, '-g', '--children', '--percent-limit', '0.5')
        (root / (phase + '-callgraph.txt')).write_text(calls + '\n')
        results.append({'phase': phase, 'pid': int(pid), 'apk_sha256': expected, 'samples': samples})
        (root / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
        print(phase + ': 176 requests passed; CPU record/report saved', flush=True)
finally:
    device('shell', 'am', 'start', '-W', '-n', activity)
    assert installed_hash() == expected
print('Candidate activity restored; APK hash verified', flush=True)
