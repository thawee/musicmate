import concurrent.futures, hashlib, json, statistics, sys, time, urllib.request
base=sys.argv[1]; label=sys.argv[2]
track='2122216336'; url=base+'/music/'+track+'/file'
def fetch(path=url, headers=None):
 start=time.monotonic()
 with urllib.request.urlopen(urllib.request.Request(path,headers=headers or {}),timeout=15) as r:
  first=time.monotonic(); body=r.read()
  expected=int(r.headers['Content-Length'])
  assert len(body)==expected,(len(body),expected)
  return {'size':len(body),'ttfb_ms':(first-start)*1000,'seconds':time.monotonic()-start,'sha256':hashlib.sha256(body).hexdigest(),'status':r.status},body
single,body=fetch()
ranges=[]
for i in range(12):
 at=(i*313337)%(len(body)-262144)
 stats,part=fetch(headers={'Range':f'bytes={at}-{at+262143}'})
 assert stats['status']==206 and part==body[at:at+262144]
 ranges.append(stats['ttfb_ms'])
start=time.monotonic()
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 fulls=[f.result()[0] for f in [pool.submit(fetch) for _ in range(4)]]
assert all(s['sha256']==single['sha256'] for s in fulls)
parallel_seconds=time.monotonic()-start
pcm,pcm_body=fetch(headers={'User-Agent':'LG webOS TV DLNADOC/1.50'})
assert pcm_body[:4]==b'RIFF' and pcm_body[8:12]==b'WAVE'
for at in [0,44,12345,262144]:
 _,part=fetch(headers={'User-Agent':'LG webOS TV DLNADOC/1.50','Range':f'bytes={at}-{at+65535}'})
 assert part==pcm_body[at:at+65536]
result={'label':label,'single':single,'seek_p50_ms':statistics.median(ranges),'seek_p95_ms':sorted(ranges)[-1], 'parallel_MB_s':sum(s['size'] for s in fulls)/parallel_seconds/1048576,'converted':pcm,'range_checks':16,'parallel_hash_matches':4}
open('/private/tmp/musicmate-phone-'+label+'.json','w').write(json.dumps(result,indent=2))
print(json.dumps(result))
