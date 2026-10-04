import collections, csv, hashlib, io, json, pathlib, statistics
root = pathlib.Path('/private/tmp/musicmate-idle-seek-20261003')
text = (root / 'oncpu-csv.csv').read_text()
rows = list(csv.DictReader(io.StringIO(text[text.index('Overhead,'):])) )
threads, worker = collections.Counter(), collections.Counter()
for row in rows:
    period = int(row['EventCount'])
    threads[row['Command']] += period
    if row['Command'] == 'NIO-Worker':
        worker[row['Symbol']] += period
assert sum(threads.values()) == 7979898500
requests = sorted([r for r in json.loads((root / 'confirm-analysis/confirm-0-trace-requests.json').read_text()) if r['kind'] == 'seek'], key=lambda r:r['ttfb_ms'])
keys = ['ttfb_ms', 'workers_running_ms', 'workers_runnable_ms', 'workers_running_frequency_khz', 'selector_running_ms', 'selector_runnable_ms', 'client_running_ms', 'recorded_gc_pause_overlap_ms']
quartiles = {name:{key:statistics.median(r[key] for r in part) for key in keys} for name,part in [('fastest_24', requests[:24]), ('slowest_24',requests[-24:])]}
stack = {'all_event_period_ns':sum(threads.values()), 'thread_event_period_ns':dict(threads), 'worker_event_period_ns':sum(worker.values()), 'worker_top_self_symbols':[{'symbol':s, 'period_ns':p, 'percent_of_worker':p/sum(worker.values())*100} for s,p in worker.most_common(15)], 'selector_Thread_sleep_self_samples':[r for r in rows if r['Command']=='nio-webserver-r' and 'Thread.sleep' in r['Symbol']]}
(root / 'stack-summary.json').write_text(json.dumps(stack,indent=2)+'\n')
(root / 'quartiles.json').write_text(json.dumps(quartiles,indent=2)+'\n')
print(json.dumps(stack,indent=2))
print(json.dumps(quartiles,indent=2))
