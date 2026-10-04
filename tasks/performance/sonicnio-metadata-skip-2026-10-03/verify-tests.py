import json, pathlib, xml.etree.ElementTree as ET
root=pathlib.Path('/Users/thawee.p/Workspaces/github/musicmate')
rows=[]
for module in ('core','server-jupnp'):
    for path in sorted((root/module/'build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
        tree=ET.parse(path).getroot()
        row={k:tree.attrib[k] for k in ('name','tests','failures','errors','skipped')}
        assert int(row['failures'])==int(row['errors'])==0, row
        rows.append({'module':module,**row})
assert rows
out=pathlib.Path('/private/tmp/musicmate-metadata-skip-20261003')
(out/'test-results.json').write_text(json.dumps(rows,indent=2)+'\n')
print(sum(int(row['tests']) for row in rows),'core/UPnP tests passed')
