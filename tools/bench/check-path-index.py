"""Compare exact path lookup on isolated SQLite copies; never changes the source.
Usage: python3 tools/bench/check-path-index.py SOURCE_DB NEW_OUTPUT_DIRECTORY
Source may have a matching WAL. Reports host timings, not Android streaming gains.
"""
import hashlib
import json
import pathlib
import sqlite3
import statistics
import sys
import time

source = pathlib.Path(sys.argv[1]).resolve()
out = pathlib.Path(sys.argv[2]).resolve()
out.mkdir(exist_ok=False)
original = sqlite3.connect('file:' + str(source) + '?mode=ro', uri=True)
assert original.execute('PRAGMA quick_check').fetchone() == ('ok',)
paths = [row[0] for row in original.execute('SELECT DISTINCT path FROM musictag WHERE path IS NOT NULL ORDER BY path')]
assert paths
probes = [paths[i * len(paths) // 48] for i in range(48)] + ['__missing_path_probe_' + str(i) for i in range(16)]
query = 'SELECT * FROM musictag WHERE path = ?'
expected = [original.execute(query, (path,)).fetchall() for path in probes]
result = {'scope': 'Host SQLite on copied phone data; no live database changes or app speedup claim',
          'sqlite': sqlite3.sqlite_version, 'tracks': original.execute('SELECT count(*) FROM musictag').fetchone()[0],
          'distinct_paths': len(paths), 'query': query, 'hits_per_batch': 48, 'misses_per_batch': 16,
          'operations_per_sample': 128, 'warmup_operations_per_sample': 64, 'samples': []}
for variant in ('before', 'indexed'):
    target = out / (variant + '.db')
    with sqlite3.connect(target) as con:
        original.backup(con)
        if variant == 'indexed':
            con.execute('CREATE INDEX index_musictag_path ON musictag(path)')
        con.commit()
        plan = con.execute('EXPLAIN QUERY PLAN ' + query, (probes[0],)).fetchall()
        result[variant] = {'plan': plan, 'page_count': con.execute('PRAGMA page_count').fetchone()[0],
                           'page_size': con.execute('PRAGMA page_size').fetchone()[0],
                           'free_pages': con.execute('PRAGMA freelist_count').fetchone()[0],
                           'results_match': all(con.execute(query, (path,)).fetchall() == rows for path, rows in zip(probes, expected))}
        assert result[variant]['results_match']
    con.close()
    result[variant]['database_bytes_after_close'] = target.stat().st_size
original.close()
for variant in ('before', 'indexed', 'indexed', 'before', 'before', 'indexed'):
    con = sqlite3.connect('file:' + str(out / (variant + '.db')) + '?mode=ro', uri=True)
    for path in probes:
        con.execute(query, (path,)).fetchall()
    start = time.perf_counter_ns()
    for i in range(128):
        con.execute(query, (probes[i % 64],)).fetchall()
    elapsed = time.perf_counter_ns() - start
    con.close()
    result['samples'].append({'variant': variant, 'us_per_lookup': elapsed / 128 / 1000})
for variant in ('before', 'indexed'):
    result[variant]['median_us_per_lookup'] = statistics.median(r['us_per_lookup'] for r in result['samples'] if r['variant'] == variant)
result['probe_results_sha256'] = hashlib.sha256(repr(expected).encode()).hexdigest()
(out / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
