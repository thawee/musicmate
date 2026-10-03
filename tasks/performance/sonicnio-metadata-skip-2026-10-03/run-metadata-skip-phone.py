"""Compare version-3 metadata baseline/candidate APKs with verified Android workloads.
Usage: python3 tools/bench/run-metadata-skip-phone.py SCRATCH ADB SERIAL initial|confirmation
Requires before.apk, after.apk and the installed opt-in PathIndexPhoneBenchmark test APK.
No private database downgrade; restores the candidate APK/activity in finally.
"""
import hashlib
import json
import pathlib
import subprocess
import sys
import time

root = pathlib.Path(sys.argv[1]).resolve()
adb = [sys.argv[2], '-s', sys.argv[3]]
mode = sys.argv[4]
assert mode in ('initial', 'confirmation')
package = 'apincer.android.mmate'
activity = package + '/apincer.android.mmate.ui.MainActivity'
hashes = {variant: hashlib.sha256((root / (variant + '.apk')).read_bytes()).hexdigest()
          for variant in ('before', 'after')}
assert hashes['before'] == '4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16'

def device(*args):
    return subprocess.run(adb + list(args), check=True, capture_output=True,
                          text=True, timeout=120).stdout.strip()

def installed():
    path = device('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
    return device('shell', 'sha256sum', path).split()[0]

def install(variant):
    device('install', '-r', str(root / (variant + '.apk')))
    assert installed() == hashes[variant]
    device('shell', 'am', 'start', '-W', '-n', activity)
    time.sleep(3)

results = []
try:
    order = ('before', 'after', 'after', 'before') if mode == 'initial' else ('after', 'before', 'before', 'after')
    for index, variant in enumerate(order):
        install(variant)
        device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        name = f'{mode}-{index}-{variant}'
        pid = device('shell', 'pidof', package)
        command = adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                         'apincer.music.room.PathIndexPhoneBenchmark', '-e', 'pathIndexBenchmark', 'true',
                         '-e', 'clientTrace', 'false', '-e', 'idleOnly',
                         'true' if mode == 'confirmation' else 'false',
                         '-e', 'reverseOrder', 'true' if index % 2 else 'false',
                         'musicmate.db.room.test/androidx.test.runner.AndroidJUnitRunner']
        with (root / (name + '.log')).open('w') as log:
            subprocess.run(command, check=True, timeout=180, stdout=log, stderr=subprocess.STDOUT)
        raw = (root / (name + '.log')).read_text()
        assert 'OK (1 test)' in raw and 'INSTRUMENTATION_CODE: -1' in raw
        records = [json.loads(line.removeprefix('BENCHJSON ')) for line in raw.splitlines()
                   if line.startswith('BENCHJSON ')]
        phases = [r for r in records if r['kind'] == 'phase']
        assert len(phases) == (6 if mode == 'initial' else 2)
        assert all(len(p['requests']) == 176 for p in phases)
        assert device('shell', 'pidof', package) == pid
        assert installed() == hashes[variant]
        results.append({'name': name, 'variant': variant, 'app_pid': int(pid),
                        'apk_sha256': hashes[variant], 'records': records})
        (root / (mode + '-results.json')).write_text(json.dumps(results, indent=2) + '\n')
        print(name + ': ' + str(len(phases) * 176) + ' requests verified', flush=True)
finally:
    install('after')
    state = {'apk_sha256': installed(), 'pid': int(device('shell', 'pidof', package)),
             'activity': activity, 'restored_variant': 'after'}
    assert state['apk_sha256'] == hashes['after']
    (root / 'final-device-state.json').write_text(json.dumps(state, indent=2) + '\n')
print('Metadata-skip candidate restored', flush=True)
