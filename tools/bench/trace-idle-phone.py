"""Matched client-marker controls and Perfetto captures of idle streaming seeks.
Usage: python3 tools/bench/trace-idle-phone.py SCRATCH ADB SERIAL pilot|compare|confirm
Requires profile.pbtxt and installed opt-in db-room test APK with private v2 asset.
Leaves the current server APK unchanged; restores its activity.
"""
import json
import pathlib
import subprocess
import sys
import time

root = pathlib.Path(sys.argv[1]).resolve()
adb = [sys.argv[2], '-s', sys.argv[3]]
mode = sys.argv[4]
assert mode in ('pilot', 'compare', 'confirm')
package = 'apincer.android.mmate'
expected = '4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16'


def device(*args, input=None):
    return subprocess.run(adb + list(args), input=input, text=True, capture_output=True,
                          check=True, timeout=60).stdout.strip()


def installed():
    path = device('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
    assert device('shell', 'sha256sum', path).split()[0] == expected


results = []
installed()
try:
    for index, phase in enumerate(('trace',) if mode == 'pilot' else ('control', 'trace', 'trace', 'control') if mode == 'compare' else ('trace', 'control')):
        name = f'{mode}-{index}-{phase}'
        device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        app_pid = device('shell', 'pidof', package)
        (root / (name + '-threads-before.txt')).write_text(device('shell', 'ps', '-T', '-p', app_pid) + '\n')
        trace_pid = None
        remote = '/data/misc/perfetto-traces/musicmate-idle-' + name + '.pftrace'
        try:
            if phase == 'trace':
                trace_pid = device('shell', 'perfetto', '--background-wait', '--txt', '-c', '-', '-o', remote,
                                   input=(root / 'profile.pbtxt').read_text())
                assert trace_pid.isdigit(), trace_pid
                (root / (name + '-trace-ack.txt')).write_text(trace_pid + '\n')
            command = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                             'apincer.music.room.PathIndexPhoneBenchmark', '-e', 'pathIndexBenchmark', 'true',
                             '-e', 'idleOnly', 'true', '-e', 'clientTrace', 'true',
                             'musicmate.db.room.test/androidx.test.runner.AndroidJUnitRunner']
            with (root / (name + '.log')).open('w') as log:
                subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=120)
            text = (root / (name + '.log')).read_text()
            assert 'OK (1 test)' in text and 'INSTRUMENTATION_CODE: -1' in text
            records = [json.loads(line.removeprefix('BENCHJSON ')) for line in text.splitlines() if line.startswith('BENCHJSON ')]
            phases = [r for r in records if r['kind'] == 'phase']
            assert len(phases) == 2 and all(len(p['requests']) == 176 and p['variant'] == 'idle' for p in phases)
            (root / (name + '-threads-after.txt')).write_text(device('shell', 'ps', '-T', '-p', app_pid) + '\n')
            assert device('shell', 'pidof', package) == app_pid
            results.append({'name': name, 'instrumentation': phase, 'app_pid': int(app_pid),
                            'apk_sha256': expected, 'records': records})
            (root / (mode + '-results.json')).write_text(json.dumps(results, indent=2) + '\n')
            print(name + ': 352 requests verified; app PID unchanged', flush=True)
        finally:
            if trace_pid is not None:
                device('shell', 'kill', '-TERM', trace_pid)
                time.sleep(1)
                device('pull', remote, str(root / (name + '.pftrace')))
finally:
    device('shell', 'am', 'start', '-W', '-n', package + '/apincer.android.mmate.ui.MainActivity')
    installed()
print('Server candidate identity/activity restored', flush=True)
