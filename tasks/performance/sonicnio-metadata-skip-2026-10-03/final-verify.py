import ast, hashlib, json, pathlib, re, statistics, subprocess, shutil
root=pathlib.Path('/Users/thawee.p/Workspaces/github/musicmate')
dest=root/'tasks/performance/sonicnio-metadata-skip-2026-10-03'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
before={name:digest(dest/name) for name in ('phone-summary.json','every-phase.csv')}
subprocess.run(['python3',str(root/'tools/bench/analyze-metadata-skip-phone.py'),str(dest)],check=True,stdout=subprocess.DEVNULL,timeout=30)
assert all(digest(dest/name)==sha for name,sha in before.items())
rows=[json.loads(line) for line in (dest/'micro/results.jsonl').read_text().splitlines()]
assert len(rows)==36
summary=json.loads((dest/'micro/summary.json').read_text())
for fixture, data in summary.items():
    if fixture not in ('streaminfo-only','unused-padding-1mib'):continue
    for variant in ('before','after'):
        selected=[r for r in rows if r['label'].startswith(fixture+'-') and r['api']==('legacy' if variant=='before' else 'streaming')]
        assert len(selected)==9
        labels={r['label'] for r in selected}
        assert len(labels)==3
        for metric in ('bytes_per_op','nanos_per_op'):
            value=statistics.median(statistics.median(r[metric] for r in selected if r['label']==label) for label in labels)
            assert abs(value-data[variant][metric])<1e-6, (fixture,variant,metric,value)
for manifest in ('source-manifest.json','unchanged-source-manifest.json'):
    for name,sha in json.loads((dest/manifest).read_text()).items():
        assert digest(root/name)==sha,name
old=json.loads((root/'tasks/performance/sonicnio-idle-seek-2026-10-03/production-source-verification.json').read_text())
for name,path in [('FlacDecoder.java','library/JustFLAC/src/java/io/nayuki/flac/decode/FlacDecoder.java'),('FlacToWav.java','core/src/main/java/apincer/music/core/codec/FlacToWav.java')]:
    assert digest(dest/('before-'+name))==old[path]
for path in dest.glob('*.py'):ast.parse(path.read_text())
for link in re.findall(r'\]\(([^)]+)\)',(dest/'REPORT.md').read_text()):
    if not link.startswith('https:'):assert (dest/link).exists(),link
verification=json.loads((dest/'verification.json').read_text())
verification.update(recomputed_phone_summary='identical JSON and CSV',recomputed_micro_summary='all four fixture/variant medians match raw samples',baseline_sources_match_preceding_candidate=True,report_local_links='pass',source_manifests='pass')
(dest/'verification.json').write_text(json.dumps(verification,indent=2)+'\n')
shutil.copy2(pathlib.Path(__file__),dest/'final-verify.py')
files=sorted(p for p in dest.rglob('*') if p.is_file() and p!=dest/'manifest.json')
manifest={str(p.relative_to(dest)):digest(p) for p in files}
(dest/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
assert all(digest(dest/name)==sha for name,sha in manifest.items())
print(len(manifest),'artifacts verified; host/phone summaries independently recomputed')
