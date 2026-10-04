import csv, hashlib, json, pathlib, statistics, ast, re
root=pathlib.Path('/Users/thawee.p/Workspaces/github/musicmate')
dest=root/'tasks/performance/sonicnio-idle-seek-2026-10-03'
all_runs=[]
for mode in ('pilot','compare','confirm'):
    all_runs.extend(json.loads((dest/(mode+'-results.json')).read_text()))
for name in ('offcpu','oncpu'):
    all_runs.append({'name':name,'records':json.loads((dest/(name+'-records.json')).read_text())})
def stats(xs):
    xs=sorted(xs)
    def q(p):
        i=(len(xs)-1)*p; lo=int(i)
        return xs[lo]+(xs[min(lo+1,len(xs)-1)]-xs[lo])*(i-lo)
    return [len(xs),statistics.median(xs),q(.95),q(.99),max(xs)]
rows=[]; environments=[]
for run in all_runs:
    phases=[p for p in run['records'] if p['kind']=='phase']
    assert len(phases)==2 and all(len(p['requests'])==176 and not p['workers'] for p in phases)
    for kind in ('head','header','seek'):
        xs=[(r['headers_ns']-r['start_ns'])/1e6 for p in phases for r in p['requests'] if r['kind']==kind]
        rows.append([run['name'],kind,*stats(xs)])
    environments.append({'name':run['name'],'phases':[{'before':p['environment_before'],'after':p['environment_after']} for p in phases]})
with (dest/'all-latencies.csv').open('w') as f:
    writer=csv.writer(f);writer.writerow(['run','kind','n','median_ms','p95_ms','p99_ms','max_ms']);writer.writerows(rows)
(dest/'environments.json').write_text(json.dumps(environments,indent=2)+'\n')
for row in rows:
    if row[0] in ('oncpu','offcpu') and row[1]=='seek':print(row)
for mode in ('pilot','compare','confirm'):
    expected=json.loads((dest/(mode+'-analysis/summary.json')).read_text())
    for run in expected:
        for kind in ('head','header','seek'):
            row=next(r for r in rows if r[0]==run['name'] and r[1]==kind)
            for key,value in zip(['n','median_ms','p95_ms','p99_ms','max_ms'],row[2:]):
                assert abs(run['latency'][kind][key]-value)<1e-9
for name in ('analyze-idle-trace.py','trace-idle-phone.py','summarize.py','capture-cpu.py'):
    ast.parse((dest/name).read_text())
text=(dest/'REPORT.md').read_text()
for link in re.findall(r'\]\(([^)]+)\)',text):
    if not link.startswith('https:'):assert (dest/link).exists(),link
v=json.loads((dest/'verification.json').read_text())
v['recomputed_latency_distributions']='all 27 distributions match parsed records'
v['report_local_links']='pass'
(dest/'verification.json').write_text(json.dumps(v,indent=2)+'\n')
print('27 distributions, nine invocations, report links and Python syntax verified')
