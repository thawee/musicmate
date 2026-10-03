import concurrent.futures, hashlib, json, time, urllib.request
base='http://127.0.0.1:19000'
def read(path,headers=None):
 with urllib.request.urlopen(urllib.request.Request(base+path,headers=headers or {}),timeout=15) as response:
  body=response.read()
  assert len(body)==int(response.headers['Content-Length'])
  return body
file='/music/2122216336/file'
expected=hashlib.sha256(read(file)).hexdigest()
started=time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
 audio=[pool.submit(read,file) for _ in range(4)]
 art=[pool.submit(read,'/coverart/435a461d927d26b340962655f9550893.jpg') for _ in range(32)]
 assert all(hashlib.sha256(f.result()).hexdigest()==expected for f in audio)
 covers=[f.result() for f in art]
 assert all(x==covers[0] and len(x)>0 for x in covers)
mp3=read('/music/2843233123/file')
parts=[]
for at in [0,10000,100000]:
 part=read('/music/2843233123/file',{'Range':f'bytes={at}-{at+65535}'})
 assert part==mp3[at:at+65536]
 parts.append(at)
result={'audio_transfers':4,'artwork_transfers':32,'seconds':time.monotonic()-started,'audio_hashes_match':True,'artwork_bytes':len(covers[0]),'mp3_bytes':len(mp3),'mp3_range_checks':len(parts)}
open('/private/tmp/musicmate-artwork-burst.json','w').write(json.dumps(result,indent=2))
print(json.dumps(result))
