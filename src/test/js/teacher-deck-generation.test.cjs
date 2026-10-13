const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/teacher-deck.js'), 'utf8');
const functions = code.slice(code.indexOf('async function handleGenerateSlides()'), code.indexOf('// Progress Bar Helper'));

for (const action of ['handleGenerateSlides', 'handleRetrySlide']) {
  test(`${action} waits ten minutes and refreshes partial slides after a failed job`, async () => {
    const failure = { code: 'PARTIAL_SLIDE_FAILURE', message: 'Một slide lỗi' };
    let refreshed = 0, hidden = 0, seen;
    const context = vm.createContext({
      state: { deckId: 1, deck: { approvedOutlineRevisionId: 2 }, outlines: [{ id: 2, contentHash: 'hash' }] },
      crypto: { randomUUID: () => 'key' },
      TeacherAPI: { generateSlides: async () => ({ jobId: 3 }), retrySlide: async () => ({ jobId: 3 }), getJob() {} },
      JobPoller: { pollJob: async (_, options) => { assert.equal(options.timeout, 600000); throw failure; } },
      UIErrors: { handle: error => { seen = error; }, showToast() {} },
      showJobProgress() {}, updateJobProgress() {}, hideJobProgress: () => hidden++,
      loadDeck: async () => refreshed++
    });
    vm.runInContext(functions, context);
    await context[action]({ id: 4, slideOrder: 1 });
    assert.equal(refreshed, 1);
    assert.equal(hidden, 1);
    assert.equal(seen, failure);
  });
}
