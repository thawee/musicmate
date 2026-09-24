const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('app/src/main/assets/webui/index.html', 'utf8');
function renderer(name, ui) {
    const context = vm.createContext({ui, document: {querySelector: () => null}, URL,
        window: {location: {href: 'http://localhost:9000/'}}, getQualityBadge: () => '', formatTime: () => '0:01'});
    for (const fn of ['escapeHtml', 'safeImageUrl', name]) {
        const start = source.indexOf(`        function ${fn}(`);
        if (start < 0) continue;
        const end = source.indexOf('\n        function ', start + 1);
        vm.runInContext(source.slice(start, end < 0 ? source.length : end), context);
    }
    return context[name];
}
const payload = '<img src=x onerror="window.injected=true">';
test('song metadata is rendered as text, including quoted attributes', () => {
    const ui = {songList: {innerHTML: ''}};
    renderer('updateSongList', ui)([{type:'song', trackId:'1', title:payload, artist:payload, album:payload}]);
    assert.ok(!ui.songList.innerHTML.includes(payload));
    assert.ok(ui.songList.innerHTML.includes('&lt;img'));
});
test('renderer names cannot break out of attributes or insert markup', () => {
    const ui = {rendererDropdownList: {innerHTML: ''}};
    renderer('updateRendererList', ui)([{targetId:'player', name:payload}]);
    assert.ok(!ui.rendererDropdownList.innerHTML.includes(payload));
    assert.ok(ui.rendererDropdownList.innerHTML.includes('&quot;'));
});
test('queue metadata and artwork attributes are escaped', () => {
    const ui = {queueGrid: {innerHTML: ''}};
    renderer('updateQueueGrid', ui)([{trackId:1, title:payload, artUrl:'x" onload="window.injected=true'}]);
    assert.ok(!ui.queueGrid.innerHTML.includes(payload));
    assert.ok(!ui.queueGrid.innerHTML.includes('src="x" onload='));
});
