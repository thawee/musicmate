"""Map device nanoTime requests into Perfetto and attribute thread states/frequency.
Usage: python3 tools/bench/analyze-idle-trace.py SCRATCH PROCESSOR pilot|compare|confirm
Client sections verify the mapping. PID comes from ADB, not optional process names.
"""
import bisect
import csv
import io
import json
import pathlib
import statistics
import subprocess
import sys

root = pathlib.Path(sys.argv[1]).resolve()
processor, mode = sys.argv[2:4]
out = root / (mode + '-analysis')
out.mkdir(exist_ok=True)
runs = json.loads((root / (mode + '-results.json')).read_text())
summary = []

def latency(values):
    values = sorted(values)
    def q(p):
        pos = (len(values) - 1) * p
        lo = int(pos)
        return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (pos - lo)
    return {'n': len(values), 'median_ms': statistics.median(values), 'p95_ms': q(.95), 'p99_ms': q(.99), 'max_ms': max(values)}

def overlap(ts, dur, start, end):
    return max(0, min(ts + dur, end) - max(ts, start))

for run in runs:
    measured = [s for r in run['records'] if r['kind'] == 'phase' for s in r['requests']]
    record = {'name': run['name'], 'instrumentation': run['instrumentation'],
              'latency': {k: latency([(s['headers_ns'] - s['start_ns']) / 1e6 for s in measured if s['kind'] == k]) for k in ('head', 'header', 'seek')}}
    if run['instrumentation'] != 'trace':
        summary.append(record)
        continue
    trace = root / (run['name'] + '.pftrace')
    pid = int(run['app_pid'])
    inventory = {}
    for suffix in ('before', 'after'):
        path = root / (run['name'] + '-threads-' + suffix + '.txt')
        if path.exists():
            for line in path.read_text().splitlines()[1:]:
                fields = line.split(None, 9)
                if len(fields) == 10 and int(fields[1]) == pid:
                    inventory[int(fields[2])] = fields[9]
    known_tids = ','.join(str(tid) for tid in inventory) or '-1'
    client_pids = "SELECT DISTINCT p.pid FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid) WHERE s.name='client-seek'"
    queries = {
        'clocks': "SELECT ts,clock_name,clock_value FROM clock_snapshot WHERE clock_name='MONOTONIC'",
        'bounds': 'SELECT start_ts,end_ts FROM trace_bounds',
        'markers': "SELECT p.pid,t.tid,s.ts,s.dur,s.name FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid) WHERE s.name GLOB 'client-*' ORDER BY s.ts",
        'states': f"SELECT p.pid,t.tid,t.name,s.ts,s.dur,s.state,s.cpu,s.blocked_function FROM thread_state s JOIN thread t USING(utid) LEFT JOIN process p USING(upid) WHERE (p.pid={pid} OR p.pid IN ({client_pids}) OR t.tid IN ({known_tids})) AND s.dur>0 ORDER BY s.ts",
        'frequency': "SELECT c.ts,c.value,t.cpu FROM counter c JOIN cpu_counter_track t ON c.track_id=t.id WHERE t.name='cpufreq' ORDER BY c.ts",
        'quality': "SELECT name,idx,severity,value FROM stats WHERE value>0 AND severity!='info'",
        'gc': f"SELECT s.ts,s.dur,s.name FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid) WHERE p.pid={pid} AND s.name='Mutator threads suspended for ScopedPause'",
    }
    data = {}
    for label, sql in queries.items():
        (out / (run['name'] + '-' + label + '.sql')).write_text(sql + ';\n')
        proc = subprocess.run([processor, 'query', str(trace), sql], text=True, capture_output=True, check=True, timeout=60)
        (out / (run['name'] + '-' + label + '.csv')).write_text(proc.stdout)
        (out / (run['name'] + '-' + label + '.stderr')).write_text(proc.stderr)
        data[label] = list(csv.DictReader(io.StringIO(proc.stdout)))
    assert data['clocks'] and len(data['bounds']) == 1
    offsets = [int(r['ts']) - int(r['clock_value']) for r in data['clocks']]
    base = int(statistics.median(offsets))
    markers = [{'pid': int(r['pid']), 'tid': int(r['tid']), 'ts': int(r['ts']), 'dur': int(r['dur']), 'name': r['name']} for r in data['markers']]
    assert len({m['pid'] for m in markers}) == 1
    client_pid = markers[0]['pid']
    client_tid = markers[0]['tid']
    states = []
    for r in data['states']:
        tid = int(r['tid'])
        owner = int(r['pid']) if r['pid'] != '[NULL]' else pid if tid in inventory else None
        assert tid not in inventory or owner == pid, (tid, owner)
        states.append({'pid': owner, 'tid': tid, 'name': inventory.get(tid, r['name']),
                       'ts': int(r['ts']), 'dur': int(r['dur']), 'state': r['state'],
                       'cpu': int(r['cpu']) if r['cpu'] != '[NULL]' else None})
    frequencies = {}
    for r in data['frequency']:
        frequencies.setdefault(int(r['cpu']), []).append((int(r['ts']), float(r['value'])))
    freq_times = {cpu: [t for t, _ in values] for cpu, values in frequencies.items()}
    requests = []
    for sample in measured:
        start, end = sample['start_ns'] + base, sample['headers_ns'] + base
        assert int(data['bounds'][0]['start_ts']) <= start <= end <= int(data['bounds'][0]['end_ts'])
        matching = [m for m in markers if m['name'] == 'client-' + sample['kind'] and m['ts'] <= start <= m['ts'] + m['dur'] and end <= m['ts'] + m['dur']]
        assert len(matching) == 1, (sample['kind'], start, end, matching)
        marker = matching[0]
        row = {'kind': sample['kind'], 'range': sample['range'], 'ttfb_ms': (end - start) / 1e6,
               'trace_start_ns': start, 'trace_headers_ns': end,
               'marker_lead_us': (start - marker['ts']) / 1000,
               'marker_trail_us': (marker['ts'] + marker['dur'] - end) / 1000}
        groups = {'selector': lambda s: s['pid'] == pid and s['name'] == 'nio-webserver-r',
                  'producer': lambda s: s['pid'] == pid and s['name'] == 'nio-stream-prod',
                  'workers': lambda s: s['pid'] == pid and s['name'] == 'NIO-Worker',
                  'client': lambda s: s['pid'] == client_pid and s['tid'] == client_tid}
        for name, pred in groups.items():
            relevant = [s for s in states if pred(s) and overlap(s['ts'], s['dur'], start, end) > 0]
            for group, allowed in [('running', ('Running',)), ('runnable', ('R', 'R+')), ('sleeping', ('S',)), ('uninterruptible', ('D',))]:
                row[name + '_' + group + '_ms'] = sum(overlap(s['ts'], s['dur'], start, end) for s in relevant if s['state'] in allowed) / 1e6
            weighted, covered, running = 0, 0, 0
            for s in relevant:
                if s['state'] != 'Running': continue
                a, b = max(s['ts'], start), min(s['ts'] + s['dur'], end)
                running += b - a
                cpu = s['cpu']
                if cpu not in frequencies: continue
                pos = bisect.bisect_right(freq_times[cpu], a) - 1
                while a < b:
                    nxt = frequencies[cpu][pos + 1][0] if pos + 1 < len(frequencies[cpu]) else b
                    until = min(b, nxt)
                    if pos >= 0:
                        weighted += (until - a) * frequencies[cpu][pos][1]
                        covered += until - a
                    a = until
                    pos += 1
            row[name + '_running_frequency_khz'] = weighted / covered if covered else None
            row[name + '_frequency_coverage_fraction'] = covered / running if running else None
        row['recorded_gc_pause_overlap_ms'] = sum(overlap(int(g['ts']), int(g['dur']), start, end) for g in data['gc']) / 1e6
        requests.append(row)
    (out / (run['name'] + '-requests.json')).write_text(json.dumps(requests, indent=2) + '\n')
    record.update({'clock_snapshot_offset_spread_ns': max(offsets) - min(offsets),
                   'worker_ownership_inventory_available': bool(inventory),
                   'worker_attribution_complete': bool(inventory),
                   'observed_worker_tids': sorted({s['tid'] for s in states if s['pid'] == pid and s['name'] == 'NIO-Worker'}),
                   'marker_alignment_verified_requests': len(requests), 'quality': data['quality'],
                   'seek_state_medians': {key: statistics.median(r[key] for r in requests if r['kind'] == 'seek' and r.get(key) is not None) for key in requests[0] if key.endswith('_ms') or key.endswith('_khz')},
                   'slow_seeks': sorted((r for r in requests if r['kind'] == 'seek'), key=lambda r: -r['ttfb_ms'])[:8]})
    summary.append(record)
(out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
