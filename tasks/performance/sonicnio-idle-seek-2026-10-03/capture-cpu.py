import os, pathlib, subprocess, time
root = pathlib.Path('/private/tmp/musicmate-idle-seek-20261003')
adb = ['/Users/thawee.p/Library/Android/sdk/platform-tools/adb', '-s', os.environ['ANDROID_SERIAL']]
package = 'apincer.android.mmate'
def device(*args):
    return subprocess.run(adb + list(args), check=True, capture_output=True, text=True, timeout=60).stdout.strip()
pid = device('shell', 'pidof', package)
remote = '/data/local/tmp/musicmate-idle-oncpu.data'
try:
    device('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    (root / 'oncpu-threads-before.txt').write_text(device('shell', 'ps', '-T', '-p', pid))
    with (root / 'oncpu-record.log').open('w') as log:
        proc = subprocess.Popen(adb + ['shell', 'simpleperf', 'record', '--app', package, '-p', pid, '--duration', '30', '-g', '-e', 'task-clock:u', '-f', '199', '-o', remote], stdout=log, stderr=subprocess.STDOUT)
        try:
            time.sleep(1)
            with (root / 'oncpu-workload.log').open('w') as workload:
                subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', 'apincer.music.room.PathIndexPhoneBenchmark', '-e', 'pathIndexBenchmark', 'true', '-e', 'idleOnly', 'true', '-e', 'clientTrace', 'true', 'musicmate.db.room.test/androidx.test.runner.AndroidJUnitRunner'], stdout=workload, stderr=subprocess.STDOUT, check=True, timeout=120)
            assert 'OK (1 test)' in (root / 'oncpu-workload.log').read_text()
            assert proc.wait(timeout=40) == 0
        finally:
            if proc.poll() is None:
                proc.terminate(); proc.wait(timeout=20)
    assert device('shell', 'pidof', package) == pid
    device('pull', remote, str(root / 'oncpu.data'))
    for label, flags in [('callgraph', ['-g', '--children', '--percent-limit', '0.1']), ('csv', ['--csv', '-n', '--sort', 'comm,pid,tid,dso,symbol'])]:
        (root / ('oncpu-' + label + ('.txt' if label == 'callgraph' else '.csv'))).write_text(device('shell', 'simpleperf', 'report', '-i', remote, *flags) + '\n')
    (root / 'oncpu-threads-after.txt').write_text(device('shell', 'ps', '-T', '-p', pid))
finally:
    device('shell', 'am', 'start', '-W', '-n', package + '/apincer.android.mmate.ui.MainActivity')
    path = device('shell', 'pm', 'path', package).splitlines()[0].removeprefix('package:')
    assert device('shell', 'sha256sum', path).split()[0] == '4ea87bafc3e90f24cf6d257cbd4665b9cb6e8d1facc94490e646bff8bf200d16'
print('CPU-only capture/workload passed; candidate restored')
