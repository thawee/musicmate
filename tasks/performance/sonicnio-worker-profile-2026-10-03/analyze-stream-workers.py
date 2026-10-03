"""Recompute phase quantiles and sampled thread shares from preserved Simpleperf CSV.
Usage: python3 tools/bench/analyze-stream-workers.py SCRATCH
"""
import csv
import io
import json
import pathlib
import re
import statistics
import sys

root = pathlib.Path(sys.argv[1]).resolve()
runs = json.loads((root / 'results.json').read_text())
summary = []


def quantile(values, fraction):
    values = sorted(values)
    pos = (len(values) - 1) * fraction
    lo = int(pos)
    return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (pos - lo)


for run in runs:
    phase = run['phase']
    text = (root / (phase + '-report.csv')).read_text()
    rows = list(csv.DictReader(io.StringIO(text[text.index('Overhead,'):])) )
    total = sum(int(row['EventCount']) for row in rows)
    assert total == int(re.search(r'Event count: (\d+)', text).group(1))
    assert len(run['samples']) == 176
    threads = {}
    for row in rows:
        threads[row['Command']] = threads.get(row['Command'], 0) + int(row['EventCount'])
    latencies = {}
    for kind in ('head', 'header', 'seek'):
        values = [s['ttfb_ms'] for s in run['samples'] if s['kind'] == kind]
        latencies[kind] = {'n': len(values), 'median_ms': statistics.median(values),
                           'p95_ms': quantile(values, .95), 'p99_ms': quantile(values, .99), 'max_ms': max(values)}
    log = (root / (phase + '-record.log')).read_text()
    recorded = re.search(r'Samples recorded: ([\d,]+). Samples lost: ([\d,]+)', log)
    assert recorded
    summary.append({'phase': phase, 'samples_recorded': int(recorded[1].replace(',', '')),
                    'samples_lost': int(recorded[2].replace(',', '')),
                    'sampled_task_clock_event_s': total / 1e9,
                    'pool_7_share_percent': sum(v for k, v in threads.items() if k.startswith('pool-7-')) / total * 100,
                    'thread_shares_percent': {k: v / total * 100 for k, v in sorted(threads.items(), key=lambda pair: -pair[1])},
                    'scan_worker_in_callgraph': 'ScanAudioFileWorker' in (root / (phase + '-callgraph.txt')).read_text(),
                    'latency': latencies})
(root / 'worker-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
