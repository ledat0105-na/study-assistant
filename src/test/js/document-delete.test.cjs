const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/viewer.html'), 'utf8');
const code = html.slice(html.indexOf('    async function deleteDocument('), html.indexOf('    function toggleDocument('));

function fixture(fetch) {
  const notices = [];
  let refreshed = 0;
  const context = vm.createContext({ fetch, selectedDocIds: [1, 2], confirm: () => true,
    ApiClient: { message: () => 'Không tìm thấy tài liệu hoặc bạn không có quyền xóa.' }, alert: message => notices.push(message), console: { error() {} },
    saveSelectedDocIds() {}, loadDocuments: async () => { refreshed++; } });
  vm.runInContext(code, context);
  return { context, notices, refreshed: () => refreshed };
}

test('successful delete removes selected source and reloads documents', async () => {
  const f = fixture(async (url, options) => {
    assert.equal(url, '/api/documents/1'); assert.equal(options.method, 'DELETE');
    return { ok: true };
  });
  await f.context.deleteDocument(1);
  assert.deepEqual(Array.from(f.context.selectedDocIds), [2]);
  assert.equal(f.refreshed(), 1);
});

test('failed delete displays friendly error and keeps selected documents', async () => {
  const f = fixture(async () => ({ ok: false, json: async () => ({ error: 'Không có quyền xóa' }) }));
  await f.context.deleteDocument(1);
  assert.deepEqual(f.notices, ['Không tìm thấy tài liệu hoặc bạn không có quyền xóa.']);
  assert.deepEqual(Array.from(f.context.selectedDocIds), [1, 2]);
  assert.equal(f.refreshed(), 0);
});

test('network failure displays Vietnamese connection error', async () => {
  const f = fixture(async () => { throw new Error('offline'); });
  await f.context.deleteDocument(1);
  assert.match(f.notices[0], /Lỗi kết nối/);
});
