const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../../main/resources/static');
const source = fs.readFileSync(path.join(root, 'js/document-mind-map.js'), 'utf8');
function setup(fetch) {
  const window = {};
  vm.runInNewContext(source, { window, fetch });
  return window.DocumentMindMap;
}
const doc = { id: 3710, status: 'READY', activeIndexVersion: 'v1' };
const response = graph => ({ ok: true, json: async () => graph });
test('empty map shares one generation POST then reloads GET', async () => {
  const calls = []; let release;
  const wait = new Promise(resolve => { release = resolve; });
  const loader = setup(async (url, options) => {
    calls.push(options.method || 'GET');
    if (options.method) { await wait; return response({}); }
    return response({ nodes: calls.length === 1 ? [] : [{ id: 1, name: 'Java' }], edges: [] });
  });
  const first = loader.load(doc); const second = loader.load(doc);
  assert.equal(first, second);
  await new Promise(resolve => setImmediate(resolve)); release();
  assert.equal((await first).nodes.length, 1);
  assert.deepEqual(calls, ['GET', 'POST', 'GET']);
});
test('existing grounded map performs no POST', async () => {
  let calls = 0; const loader = setup(async () => { calls++; return response({ nodes: [{ id: 1 }], edges: [] }); });
  await loader.load(doc); assert.equal(calls, 1);
});

test('explicit regeneration refreshes existing descriptions and concurrent callers share the POST', async () => {
  const calls = [];
  const loader = setup(async (url, options) => {
    calls.push(options.method || 'GET');
    return response({ nodes: [{ id: 1, description: calls.length < 3 ? 'English' : 'Giải thích tiếng Việt' }], edges: [] });
  });
  const first = loader.load(doc, true), second = loader.load(doc, true);
  assert.equal(first, second);
  assert.equal((await first).nodes[0].description, 'Giải thích tiếng Việt');
  assert.deepEqual(calls, ['GET', 'POST', 'GET']);
});
test('empty result attempts automatically once; explicit retry remains available', async () => {
  let posts = 0; const loader = setup(async (url, options) => {
    if (options.method) posts++; return response({ nodes: [], edges: [] });
  });
  await loader.load(doc); await loader.load(doc); assert.equal(posts, 1);
  await loader.load(doc, true); assert.equal(posts, 2);
});
for (const status of ['PROCESSING', 'FAILED', 'UPLOADING']) {
  test(`${status} performs no read or generation`, async () => {
    let calls = 0; const loader = setup(async () => { calls++; });
    await assert.rejects(loader.load({ ...doc, status })); assert.equal(calls, 0);
  });
}
test('provider technical error is hidden and later explicit retry works', async () => {
  let posts = 0; const loader = setup(async (url, options) => {
    if (options.method && ++posts === 1) return { ok: false, json: async () => ({ error: 'stack trace' }) };
    return response({ nodes: [], edges: [] });
  });
  await assert.rejects(loader.load(doc), error => !error.message.includes('stack trace'));
  await loader.load(doc, true); assert.equal(posts, 2);
});
test('both pages parse and use the canonical shared loader', () => {
  for (const file of ['map.html', 'viewer.html']) {
    const html = fs.readFileSync(path.join(root, file), 'utf8');
    for (const script of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(script[1]);
    assert.ok(html.includes('js/document-mind-map.js'));
    assert.ok(html.includes('DocumentMindMap.load(doc, retry)'));
    assert.ok(!html.includes('/api/topics/document'));
  }
});

test('new active version gets a new single automatic attempt', async () => {
  let posts = 0;
  const loader = setup(async (url, options) => { if (options.method) posts++; return response({ nodes: [], edges: [] }); });
  await loader.load(doc); await loader.load(doc);
  assert.equal(posts, 1);
  await loader.load({ ...doc, activeIndexVersion: 'v2' });
  await loader.load({ ...doc, activeIndexVersion: 'v2' });
  assert.equal(posts, 2);
});
test('pending versions are independent and stale response cannot generate for changed doc', async () => {
  let release; let posts = 0;
  const mutable = { ...doc };
  const loader = setup(async (url, options) => {
    if (options.method) posts++;
    await new Promise(resolve => { release = resolve; });
    return response({ nodes: [], edges: [] });
  });
  const pending = loader.load(mutable);
  mutable.activeIndexVersion = 'v2'; release();
  await assert.rejects(pending, error => error.code === 'STALE_INDEX');
  assert.equal(posts, 0);
});
test('partial generation keeps grounded nodes and adds a nonblocking Vietnamese notice', async () => {
  let calls = 0;
  const partial = { nodes: [{ id: 1 }], edges: [], generationStatus: 'PARTIAL_SUCCESS', code: 'PARTIAL_GRAPH' };
  const window = { document: { createElement: () => ({ setAttribute() {} }) } };
  vm.runInNewContext(source, { window, fetch: async (url, options) => {
    calls++;
    return response(options.method ? partial : { nodes: calls === 1 ? [] : partial.nodes, edges: [] });
  } });
  const graph = await window.DocumentMindMap.load(doc);
  assert.equal(graph.nodes.length, 1);
  assert.equal(graph.generationStatus, 'PARTIAL_SUCCESS');
  const children = [{ id: 'existing-rendered-node' }];
  window.DocumentMindMap.showStatus({ appendChild: node => children.push(node) }, graph);
  assert.equal(children[0].id, 'existing-rendered-node');
  assert.match(children[1].textContent, /khái niệm có bằng chứng/);
  for (const file of ['map.html','viewer.html']) {
    assert.ok(fs.readFileSync(path.join(root,file), 'utf8').includes('DocumentMindMap.showStatus('));
  }
});
for (const [status, code] of [[401,'UNAUTHENTICATED'],[404,'NOT_FOUND'],[409,'DOCUMENT_NOT_READY'],
  [413,'GRAPH_TOO_LARGE'],[422,'AI_SCHEMA_ERROR'],[500,'GRAPH_PERSISTENCE_ERROR'],[502,'AI_PROVIDER_ERROR']]) {
  test(`safe HTTP ${status} classification`, async () => {
    const loader = setup(async () => ({ ok: false, status, json: async () => ({ error: 'secret SQL or provider key' }) }));
    await assert.rejects(loader.load(doc), error => error.code === code && !error.message.includes('secret'));
  });
}
