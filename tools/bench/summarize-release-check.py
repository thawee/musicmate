"""Summarize one successful idle-only Android release check; no comparison claim.

Usage: python3 tools/bench/summarize-release-check.py PHONE_LOG OUTPUT_JSON
"""
import json
import pathlib
import statistics
import sys

raw = pathlib.Path(sys.argv[1]).read_text()
assert 'OK (1 test)' in raw and 'FAILURES!!!' not in raw
records = [json.loads(line.removeprefix('BENCHJSON ')) for line in raw.splitlines()
           if line.startswith('BENCHJSON ')]
phases = [record for record in records if record['kind'] == 'phase']
assert len(phases) == 2
requests = []
for phase in phases:
    assert phase['variant'] == 'idle' and len(phase['requests']) == 176
    assert not phase['workers']
    for request in phase['requests']:
        assert request['status'] == (200 if request['kind'] == 'head' else 206)
        assert request['start_ns'] <= request['headers_ns'] <= request['end_ns']
        requests.append(request)

def distribution(values):
    values = sorted(values)
    def percentile(fraction):
        position = (len(values) - 1) * fraction
        lo = int(position)
        return values[lo] + (values[min(lo + 1, len(values) - 1)] - values[lo]) * (position - lo)
    return {'n': len(values), 'median_ms': statistics.median(values),
            'p95_ms': percentile(.95), 'p99_ms': percentile(.99), 'max_ms': max(values)}

summary = {'measured_requests': len(requests), 'latency_to_headers': {},
           'interpretation': 'Single retained-only APK correctness check; no matched baseline or throughput comparison.'}
for kind, expected in [('head', 128), ('header', 128), ('seek', 96)]:
    matching = [request for request in requests if request['kind'] == kind]
    assert len(matching) == expected
    summary['latency_to_headers'][kind] = distribution(
        [(request['headers_ns'] - request['start_ns']) / 1e6 for request in matching])
pathlib.Path(sys.argv[2]).write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
