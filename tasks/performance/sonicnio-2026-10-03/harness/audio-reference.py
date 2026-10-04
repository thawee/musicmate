import hashlib, io, json, pathlib, shlex, sqlite3, subprocess, tempfile, urllib.request, wave
adb='/Users/thawee.p/Library/Android/sdk/platform-tools/adb'
db=sqlite3.connect('file:/private/tmp/musicmate-baseline.db?mode=ro',uri=True)
results=[]
with tempfile.TemporaryDirectory(prefix='musicmate-audio-',dir='/private/tmp') as directory:
 for track in [2122216336,5563167]:
  path,bits=db.execute('select path,audioBitsDepth from musictag where id=?',(track,)).fetchone()
  source=pathlib.Path(directory)/('source-'+str(track)+pathlib.Path(path).suffix)
  with source.open('wb') as output:
   subprocess.run([adb,'exec-out','cat '+shlex.quote(path)],stdout=output,check=True,timeout=30)
  native=source.read_bytes()
  req=urllib.request.Request(f'http://127.0.0.1:19000/music/{track}/file',headers={'User-Agent':'LG webOS TV DLNADOC/1.50'})
  with urllib.request.urlopen(req,timeout=30) as response:
   converted=response.read()
   assert len(converted)==int(response.headers['Content-Length'])
  with wave.open(io.BytesIO(converted)) as wav:
   params=wav.getparams(); pcm=wav.readframes(params.nframes)
  if path.endswith('.flac') and any(native[26:42]):
   assert native[:4]==b'fLaC' and native[4]&127==0
   assert hashlib.md5(pcm).digest()==native[26:42]
   reference='FLAC STREAMINFO MD5'
  else:
   reference_file=pathlib.Path(directory)/'reference.wav'
   subprocess.run(['/usr/bin/afconvert','-f','WAVE','-d','LEI16',str(source),str(reference_file)],check=True,timeout=30)
   with wave.open(str(reference_file)) as wav:
    expected=wav.readframes(wav.getnframes())
   assert pcm==expected
   reference='Apple afconvert PCM'
  results.append({'track':track,'reference':reference,'pcm_bytes':len(pcm),'channels':params.nchannels,'bits':params.sampwidth*8,'rate':params.framerate,'match':True})
pathlib.Path('/private/tmp/musicmate-audio-reference.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results))
