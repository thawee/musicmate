import pathlib, subprocess, sys, time
classpath=pathlib.Path('/private/tmp/musicmate-nio-classpath.txt').read_text()
started=time.monotonic()
for run in range(1,21):
 log=pathlib.Path(f'/private/tmp/musicmate-nio-repeat-{run:02d}.log')
 with log.open('w') as output:
  result=subprocess.run(['java','-Djava.util.logging.config.file=/private/tmp/musicmate-nio-logging.properties','-cp',classpath,'org.junit.runner.JUnitCore','apincer.music.core.http.NioHttpServerTest'],stdout=output,stderr=subprocess.STDOUT,timeout=120)
 if result.returncode:
  print(f'Run {run} failed; {log}',flush=True)
  print('\n'.join(log.read_text().splitlines()[-45:]),flush=True)
  sys.exit(result.returncode)
 print(f'Run {run}/20 passed',flush=True)
print(f'20 runs passed in {time.monotonic()-started:.1f}s',flush=True)
