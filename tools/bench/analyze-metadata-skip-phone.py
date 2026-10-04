"""Recompute all APK/condition latency distributions from verified phone records.
Usage: python3 tools/bench/analyze-metadata-skip-phone.py SCRATCH
"""
import csv
import json
import pathlib
import statistics
import sys

root = pathlib.Path(sys.argv[1]).resolve()

def latency(values):
    values = sorted(values)
    def q(p):
        pos = (len(values) - 1) * p
        lo = int(pos)
        return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (pos - lo)
    return {'n': len(values), 'median_ms': statistics.median(values),
            'p95_ms': q(.95), 'p99_ms': q(.99), 'max_ms': max(values)}

runs, phases, csv_rows = [], [], []
for series in ('initial', 'confirmation'):
    for run in json.loads((root / (series + '-results.json')).read_text()):
        raw = (root / (run['name'] + '.log')).read_text()
        assert 'OK (1 test)' in raw and 'INSTRUMENTATION_CODE: -1' in raw
        parsed = [json.loads(line.removeprefix('BENCHJSON ')) for line in raw.splitlines()
                  if line.startswith('BENCHJSON ')]
        assert parsed == run['records']
        run['series'] = series
        runs.append(run)
        measured = [p for p in run['records'] if p['kind'] == 'phase']
        assert len(measured) == (6 if series == 'initial' else 2)
        for index, phase in enumerate(measured):
            assert len(phase['requests']) == 176
            workers = phase['workers']
            assert not workers or len(workers) == 4 and sum(w['operations'] for w in workers) == 8192
            result = {'run': run['name'], 'series': series, 'apk_variant': run['variant'],
                      'condition': phase['variant'], 'phase': index,
                      'worker_cpu_s': sum(w['cpu_ns'] for w in workers) / 1e9,
                      'seeks_started_during_lookup_work': sum(r['kind'] == 'seek' and r['active_lookup_workers_at_start'] > 0 for r in phase['requests']),
                      'environment_before': phase['environment_before'],
                      'environment_after': phase['environment_after'],
                      'latency': {kind: latency([(r['headers_ns'] - r['start_ns']) / 1e6 for r in phase['requests'] if r['kind'] == kind]) for kind in ('head', 'header', 'seek')}}
            phases.append(result)
            for kind, stats in result['latency'].items():
                csv_rows.append([run['name'], run['variant'], phase['variant'], index, kind, *stats.values()])
summary = {'phases': phases, 'pooled': {}}
for series in ('initial', 'confirmation', 'combined'):
    summary['pooled'][series] = {}
    for variant in ('before', 'after'):
        matching = [r for r in runs if r['variant'] == variant and (series == 'combined' or r['series'] == series)]
        summary['pooled'][series][variant] = {}
        for condition in ('idle', 'before', 'indexed'):
            requests = [r for run in matching for p in run['records'] if p['kind'] == 'phase' and p['variant'] == condition for r in p['requests']]
            if not requests: continue
            summary['pooled'][series][variant][condition] = {kind: latency([(r['headers_ns'] - r['start_ns']) / 1e6 for r in requests if r['kind'] == kind]) for kind in ('head', 'header', 'seek')}
summary['verified_requests'] = sum(len(p['requests']) for run in runs for p in run['records'] if p['kind'] == 'phase')
assert summary['verified_requests'] == 5632
(root / 'phone-summary.json').write_text(json.dumps(summary, indent=2) + '\n')
with (root / 'every-phase.csv').open('w') as f:
    writer = csv.writer(f)
    writer.writerow(['run', 'apk_variant', 'condition', 'phase', 'kind', 'n', 'median_ms', 'p95_ms', 'p99_ms', 'max_ms'])
    writer.writerows(csv_rows)
print(json.dumps(summary['pooled'], indent=2))
