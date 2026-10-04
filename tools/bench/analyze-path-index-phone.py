"""Recompute opt-in Android lookup and streaming-contention measurements.
Usage: python3 tools/bench/analyze-path-index-phone.py SCRATCH
Requires completed phone-benchmark.log and phone-benchmark-confirmation.log.
"""
import json
import pathlib
import statistics
import sys

root = pathlib.Path(sys.argv[1]).resolve()

def quantile(values, p):
    values = sorted(values)
    position = (len(values) - 1) * p
    lo = int(position)
    return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (position - lo)

def latency(values):
    return {'n': len(values), 'median_ms': statistics.median(values), 'p95_ms': quantile(values, .95),
            'p99_ms': quantile(values, .99), 'max_ms': max(values)}

all_records = []
phases = []
micro = []
for series, name in (('initial', 'phone-benchmark.log'), ('confirmation', 'phone-benchmark-confirmation.log')):
    text = (root / name).read_text()
    assert 'OK (1 test)' in text and 'INSTRUMENTATION_CODE: -1' in text
    records = [json.loads(line.removeprefix('BENCHJSON ')) for line in text.splitlines() if line.startswith('BENCHJSON ')]
    assert len(records) == 13
    for record in records:
        record['series'] = series
        all_records.append(record)
        if record['kind'] == 'lookup':
            micro.append(record | {'wall_us_per_lookup': record['wall_ns'] / record['operations'] / 1000,
                                   'cpu_us_per_lookup': record['cpu_ns'] / record['operations'] / 1000})
        elif record['kind'] == 'phase':
            assert len(record['requests']) == 176
            workers = record['workers']
            assert not workers or len(workers) == 4 and sum(w['operations'] for w in workers) == 8192
            phases.append({'series': series, 'variant': record['variant'],
                           'worker_cpu_s': sum(w['cpu_ns'] for w in workers) / 1e9,
                           'lookup_completion_s': (max(w['end_ns'] for w in workers) - min(w['start_ns'] for w in workers)) / 1e9 if workers else 0,
                           'worker_checksum': sum(w['checksum'] for w in workers),
                           'requests_started_during_lookup_work': sum(s['active_lookup_workers_at_start'] > 0 for s in record['requests']),
                           'seeks_started_during_lookup_work': sum(s['active_lookup_workers_at_start'] > 0 and s['kind'] == 'seek' for s in record['requests']),
                           'latency': {kind: latency([(s['headers_ns'] - s['start_ns']) / 1e6 for s in record['requests'] if s['kind'] == kind]) for kind in ('head', 'header', 'seek')},
                           'environment_before': record['environment_before'], 'environment_after': record['environment_after']})
assert len({p['worker_checksum'] for p in phases if p['variant'] != 'idle'}) == 1
assert len({r['checksum'] for r in micro}) == 1
summary = {'micro_samples': micro, 'phases': phases, 'pooled': {}}
for variant in ('idle', 'before', 'indexed'):
    matching = [r for r in all_records if r['kind'] == 'phase' and r['variant'] == variant]
    summary['pooled'][variant] = {kind: latency([(s['headers_ns'] - s['start_ns']) / 1e6 for r in matching for s in r['requests'] if s['kind'] == kind]) for kind in ('head', 'header', 'seek')}
    if variant != 'idle':
        samples = [r for r in micro if r['variant'] == variant]
        summary['pooled'][variant]['lookup_median_us'] = statistics.median(r['wall_us_per_lookup'] for r in samples)
        summary['pooled'][variant]['worker_cpu_median_s'] = statistics.median(r['worker_cpu_s'] for r in phases if r['variant'] == variant)
        summary['pooled'][variant]['completion_median_s'] = statistics.median(r['lookup_completion_s'] for r in phases if r['variant'] == variant)
(root / 'phone-records.json').write_text(json.dumps(all_records, indent=2) + '\n')
(root / 'phone-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary['pooled'], indent=2))
