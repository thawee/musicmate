"""Analyze preserved Perfetto traces and matched HTTP controls; no device required.
Usage: python3 tools/bench/analyze-flac-profile.py SCRATCH TRACE_PROCESSOR
Outputs app-scoped SQL/CSV evidence and reproducible summary.json in SCRATCH/analysis.
"""
import csv
import io
import json
import pathlib
import statistics
import subprocess
import sys

root = pathlib.Path(sys.argv[1]).resolve()
processor = sys.argv[2]
out = root / 'analysis'
out.mkdir(exist_ok=True)
queries = {
    'clocks': "SELECT ts,clock_value FROM clock_snapshot WHERE clock_name='REALTIME'",
    'gc': "SELECT s.ts,s.dur,s.name FROM slice s JOIN thread_track tt ON s.track_id=tt.id JOIN thread t USING(utid) JOIN process p USING(upid) WHERE p.name='apincer.android.mmate' AND (s.name GLOB '*concurrent*GC' OR s.name='Mutator threads suspended for ScopedPause') ORDER BY s.ts",
    'states': "SELECT t.tid,t.name,s.ts,s.dur,s.state FROM thread_state s JOIN thread t USING(utid) JOIN process p USING(upid) WHERE p.name='apincer.android.mmate' AND t.name IN ('nio-webserver-r','nio-stream-prod','NIO-Worker') AND s.dur>0 ORDER BY s.ts",
    'counters': "SELECT c.ts,c.value,t.name FROM counter c JOIN process_counter_track t ON c.track_id=t.id JOIN process p USING(upid) WHERE p.name='apincer.android.mmate' ORDER BY c.ts",
    'cpu': "SELECT t.name,COUNT(*) AS n,SUM(s.dur)/1e6 AS cpu_ms,MAX(s.dur)/1e6 AS max_slice_ms FROM sched s JOIN thread t USING(utid) JOIN process p USING(upid) WHERE p.name='apincer.android.mmate' AND s.dur>0 GROUP BY t.name ORDER BY cpu_ms DESC",
    'quality': "SELECT name,idx,severity,value FROM stats WHERE value>0 AND severity!='info'",
}
for name, sql in queries.items():
    (out / (name + '.sql')).write_text(sql + ';\n')

def query(trace, name):
    proc = subprocess.run([processor, 'query', str(trace), queries[name]], text=True,
                          capture_output=True, check=True, timeout=60)
    (out / (trace.stem + '-' + name + '.csv')).write_text(proc.stdout)
    (out / (trace.stem + '-' + name + '.stderr')).write_text(proc.stderr)
    return list(csv.DictReader(io.StringIO(proc.stdout)))

def percentile(values, p):
    values = sorted(values)
    position = (len(values) - 1) * p
    lo = int(position)
    return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (position - lo)

def latency(values):
    return {'n': len(values), 'median_ms': statistics.median(values),
            'p95_ms': percentile(values, .95), 'p99_ms': percentile(values, .99), 'max_ms': max(values)}

def overlap(ts, dur, start, end):
    return max(0, min(ts + dur, end) - max(ts, start)) / 1e6

runs = json.loads((root / 'compare-results.json').read_text())
summary = {'phases': [], 'traces': [], 'pooled': {}, 'interpretation': 'Request overlaps are nominal estimates, not causal attribution. Calibration uncertainty is comparable to seek latency. CPU is aggregated by Linux thread name; NIO-Worker may include multiple servers. GC counts cover whole captures, including boundary sampling.'}
for run in runs:
    assert len(run['samples']) == 176
    assert run['memory_before']['pid'] == run['memory_after']['pid']
    record = {k: run[k] for k in ('name', 'variant', 'phase', 'memory_before', 'memory_after')}
    record['latency'] = {kind: latency([s['ttfb_ms'] for s in run['samples'] if s['kind'] == kind])
                         for kind in ('head', 'header', 'seek')}
    summary['phases'].append(record)
    if run['phase'] != 'trace':
        continue
    trace = root / (run['name'] + '.pftrace')
    data = {name: query(trace, name) for name in queries}
    clocks = [int(r['ts']) - int(r['clock_value']) for r in data['clocks']]
    assert clocks, 'No real-time clock snapshots'
    base = int(statistics.median(clocks))
    before, after = run['clock_before']['best'], run['clock_after']['best']
    offset = (before['offset_ns'] + after['offset_ns']) // 2
    # Conservative calibration envelope; shell timestamp can occur anywhere in RTT.
    uncertainty = max(before['uncertainty_ns'], after['uncertainty_ns']) + abs(before['offset_ns'] - after['offset_ns']) // 2 + max(abs(c - base) for c in clocks)
    gc = [{'ts': int(r['ts']), 'dur': int(r['dur']), 'name': r['name']} for r in data['gc']]
    states = [{'ts': int(r['ts']), 'dur': int(r['dur']), 'name': r['name'], 'state': r['state']} for r in data['states']]
    requests = []
    for sample in run['samples']:
        start = sample['epoch_start_ns'] + offset + base
        end = sample['epoch_headers_ns'] + offset + base
        pauses = [g for g in gc if g['name'] == 'Mutator threads suspended for ScopedPause']
        concurrent = [g for g in gc if g['name'] != 'Mutator threads suspended for ScopedPause']
        entry = {k: sample[k] for k in ('kind', 'range', 'ttfb_ms')}
        entry.update({'trace_start_ns': start, 'trace_headers_ns': end,
                      'pause_overlap_ms': sum(overlap(g['ts'], g['dur'], start, end) for g in pauses),
                      'possible_pause_overlap_with_clock_uncertainty': any(overlap(g['ts'], g['dur'], start - uncertainty, end + uncertainty) > 0 for g in pauses),
                      'concurrent_gc_overlap_ms': sum(overlap(g['ts'], g['dur'], start, end) for g in concurrent)})
        for name in ('nio-webserver-r', 'nio-stream-prod', 'NIO-Worker'):
            for state_group, allowed in (('running', ('Running',)), ('runnable', ('R', 'R+'))):
                entry[name + '_' + state_group + '_ms'] = sum(overlap(s['ts'], s['dur'], start, end) for s in states if s['name'] == name and s['state'] in allowed)
        requests.append(entry)
    (out / (run['name'] + '-requests.json')).write_text(json.dumps(requests, indent=2) + '\n')
    gc_summary = {}
    for name in sorted({g['name'] for g in gc}):
        durations = [g['dur'] / 1e6 for g in gc if g['name'] == name]
        gc_summary[name] = {'n': len(durations), 'total_ms': sum(durations), 'max_ms': max(durations)}
    counters = {}
    for name in sorted({r['name'] for r in data['counters']}):
        values = [float(r['value']) for r in data['counters'] if r['name'] == name]
        counters[name] = {'n': len(values), 'min': min(values), 'max': max(values)}
    summary['traces'].append({'name': run['name'], 'gc': gc_summary, 'counters': counters,
                              'quality': data['quality'], 'clock_uncertainty_ms': uncertainty / 1e6,
                              'clock_offset_drift_ms': abs(before['offset_ns'] - after['offset_ns']) / 1e6,
                              'top_seeks': sorted((r for r in requests if r['kind'] == 'seek'), key=lambda r: r['ttfb_ms'], reverse=True)[:5],
                              'app_cpu': data['cpu'][:12]})
for variant in ('before', 'after'):
    summary['pooled'][variant] = {}
    for phase in ('control', 'trace'):
        matching = [r for r in runs if r['variant'] == variant and r['phase'] == phase]
        summary['pooled'][variant][phase] = {kind: latency([s['ttfb_ms'] for r in matching for s in r['samples'] if s['kind'] == kind]) for kind in ('head', 'header', 'seek')}
(out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary['pooled'], indent=2))
for trace in summary['traces']:
    print(trace['name'], 'clock uncertainty ms', trace['clock_uncertainty_ms'], 'GC', trace['gc'], 'quality', trace['quality'])
