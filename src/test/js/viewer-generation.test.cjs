const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const html = fs.readFileSync(path.join(__dirname, '../../main/resources/static/viewer.html'), 'utf8');
const summaryLoader = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/document-summary.js'), 'utf8');
const helper = html.slice(html.indexOf('    let summarizedDocIds'), html.indexOf('    async function autoSummarizeActiveDoc'));
const studio = html.slice(html.indexOf('    async function loadStudioTabData('), html.indexOf('    async function loadStudioTabDataOnce('));
const studioOnce = html.slice(html.indexOf('    async function loadStudioTabDataOnce('), html.indexOf('    async function retryDocumentProcessing('));

function fixture(status = 'READY', fetch = async () => ({ ok: true, json: async () => ({ summary: 'result' }) })) {
  const notices = [];
  class Element {
    constructor() { this.children = []; this.attrs = {}; this.classList = { add() {}, remove() {} }; }
    set textContent(value) { this.text = String(value); this.children = []; }
    get textContent() { return (this.text || '') + this.children.map(c => c.textContent || '').join(''); }
    setAttribute(key,value) { this.attrs[key] = value; }
    addEventListener(event, listener) { this[event] = listener; }
    appendChild(child) { this.children.push(child); return child; }
    append(...children) { children.forEach(c => this.appendChild(c)); }
    replaceChildren() { this.children = []; this.text = ''; }
    querySelector() { return notices.find(n => n.className?.includes('processing-notice')); }
    remove() {}
  }
  const box = new Element();
  box.appendChild = child => { box.children.push(child); notices.push(child); return child; };
  box.prepend = child => { box.children.unshift(child); notices.push(child); };
  const context = vm.createContext({
    documents: [{ id: 3710, status, activeIndexVersion: 'v1' }], selectedDocIds: [3710], currentQuizId: null,
    URLSearchParams, FormData, Blob,
    fetch: async (...args) => { const result = await fetch(...args); if (result) result.headers ||= {get: () => 'application/json'}; return result; },
    document: { getElementById: () => box, createElement: () => new Element(), createTextNode: text => ({textContent:text}),
      querySelectorAll: () => [], querySelector: () => null },
    loadStudioTabDataOnce: async () => {},
  });
  context.window = context;
  for (const file of ['js/api-client.js','js/document-ui.js','js/document-features.js','citations.js','js/summary-view.js']) {
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static', file), 'utf8'),context);
  }
  vm.runInContext(summaryLoader + helper + studio, context);
  return { context, notices };
}

test('viewer inline JavaScript parses', () => {
  for (const match of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) {
    new vm.Script(match[1]);
  }
});

test('three simultaneous summary consumers share one GET and one generation POST', async () => {
  const calls = [];
  let release;
  const blocked = new Promise(resolve => { release = resolve; });
  const { context } = fixture('READY', async (url, options) => {
    calls.push({ url, options });
    if (!options.method) return { status: 204 };
    await blocked;
    return { ok: true, json: async () => ({ summary: 'generated' }) };
  });
  const results = [context.loadDocumentSummary(3710), context.loadDocumentSummary('3710'), context.loadDocumentSummary(3710)];
  assert.equal(results[0], results[1]);
  assert.equal(results[1], results[2]);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 2);
  assert.equal(calls.filter(call => call.options.method === 'POST').length, 1);
  release();
  for (const result of await Promise.all(results)) assert.equal(result.summary, 'generated');
});

test('existing summary does not trigger generation', async () => {
  let calls = 0;
  const { context } = fixture('READY', async () => {
    calls++;
    return { ok: true, json: async () => ({ summary: 'saved' }) };
  });
  assert.equal((await context.loadDocumentSummary(3710)).summary, 'saved');
  assert.equal(calls, 1);
});

test('failed shared request is released so a later request can retry', async () => {
  let calls = 0;
  const { context } = fixture('READY', async () => {
    calls++;
    if (calls === 1) throw new Error('network failure');
    return { ok: true, json: async () => ({ summary: 'retry' }) };
  });
  await assert.rejects(context.loadDocumentSummary(3710));
  assert.equal((await context.loadDocumentSummary(3710)).summary, 'retry');
});

for (const status of ['PROCESSING', 'UPLOADING', 'FAILED']) {
  test(`${status} prevents Summary, Flashcard and Quiz requests`, async () => {
    let calls = 0;
    const { context, notices } = fixture(status, async () => { calls++; });
    context.loadStudioTabDataOnce = async () => { calls++; };
    await assert.rejects(context.loadDocumentSummary(3710));
    await context.loadStudioTabData('flashcard');
    await context.loadStudioTabData('quiz');
    assert.equal(calls, 0);
    assert.ok(notices[0].textContent.includes(status === 'FAILED' ? 'Không thể xử lý' : 'đang được xử lý'));
  });
}

test('missing metadata or explicitly absent active index blocks generation', async () => {
  const { context } = fixture();
  context.documents[0].activeIndexVersion = null;
  await assert.rejects(context.loadDocumentSummary(3710));
  context.documents = [];
  await assert.rejects(context.loadDocumentSummary(3710));
});

test('new upload polling copies activated index before both summary consumers start', async () => {
  let posts=0;
  const { context } = fixture('PROCESSING',async(url,options={}) => {
    if (url.endsWith('/status')) return {ok:true,json:async()=>({id:3710,status:'READY',activeIndexVersion:'v2'})};
    if (options.method==='POST') { posts++; return {ok:true,json:async()=>({summary:'New upload summary'})}; }
    return {ok:true,status:200,json:async()=>({docId:3710,cached:false,summary:null})};
  });
  context.documents[0].activeIndexVersion=null;
  context.isPollingDocumentStatuses=false; context.documentStatusPollTimer=null; context.updateActiveDocumentDetails=()=>{};
  context.window={clearInterval(){}}; context.renderSources=()=>{}; context.activeStudioTab='summary';
  const requests=[];
  context.autoSummarizeActiveDoc=()=>requests.push(context.loadDocumentSummary(3710));
  context.loadStudioTabData=()=>requests.push(context.loadDocumentSummary(3710));
  const polling=html.slice(html.indexOf('    function isDocumentProcessing('),html.indexOf('    let summarizedDocIds'));
  vm.runInContext(polling,context);
  await context.pollDocumentStatuses(); await Promise.all(requests);
  assert.equal(context.documents[0].activeIndexVersion,'v2');
  assert.equal(requests.length,2); assert.equal(requests[0],requests[1]); assert.equal(posts,1);
});

test('actual Summary tab blocks PROCESSING and renders friendly text without backend errors', async () => {
  let calls = 0;
  const { context, notices } = fixture('PROCESSING', async () => { calls++; });
  context.documents[0].errorMessage = 'Document must be READY and have an active index';
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('summary');
  assert.equal(calls, 0);
  assert.ok(notices[0].textContent.includes('đang được xử lý'));
  assert.ok(!notices[0].textContent.includes('active index'));
});

test('actual READY Summary tab renders the canonical result', async () => {
  const { context, notices } = fixture();
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('summary');
  assert.equal(notices[0].textContent, 'result');
});

test('Summary shows pending state until delayed AI result arrives without resize or F12', async () => {
  let release;
  const blocked = new Promise(resolve => { release = resolve; });
  const { context, notices } = fixture('READY', async () => {
    await blocked;
    return { ok: true, json: async () => ({ summary: '<script>untrusted</script>\nresult' }) };
  });
  vm.runInContext(studioOnce, context);
  const pending = context.loadStudioTabData('summary');
  assert.ok(context.document.getElementById('summaryInlineBox').textContent.includes('Vui lòng chờ'));
  release();
  await pending;
  assert.equal(notices[0].textContent, '<script>untrusted</script>\nresult');
  assert.equal(notices[0].innerHTML, undefined);
});

test('Summary retry button recovers after failure', async () => {
  let calls = 0;
  const { context, notices } = fixture('READY', async () => {
    if (++calls === 1) return { ok: false, status: 502 };
    return { ok: true, json: async () => ({ summary: 'recovered' }) };
  });
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('summary');
  assert.equal(notices[1].textContent, 'Thử tóm tắt lại');
  await notices[1].click();
  assert.equal(notices[2].textContent, 'recovered');
});

test('late Summary result cannot replace newly selected document content', async () => {
  let release;
  const blocked = new Promise(resolve => { release = resolve; });
  const { context, notices } = fixture('READY', async () => {
    await blocked;
    return { ok: true, json: async () => ({ summary: 'previous document' }) };
  });
  vm.runInContext(studioOnce, context);
  const pending = context.loadStudioTabData('summary');
  context.selectedDocIds = [3711];
  release();
  await pending;
  assert.equal(notices.length, 0);
});

test('changing active document starts chat summary without reopening the page', () => {
  const { context } = fixture();
  let summaries = 0;
  context.autoSummarizeActiveDoc = () => { summaries++; };
  context.loadStudioTabData = () => {};
  context.activeStudioTab = 'summary';
  const details = html.slice(html.indexOf('    function updateActiveDocumentDetails()'),
    html.indexOf('    // Dynamic Grid Panel Resizing Logic'));
  vm.runInContext(details, context);
  context.updateActiveDocumentDetails();
  assert.equal(summaries, 1);
});

test('READY indexed studio calls are available and share repeated tab loads', async () => {
  const { context } = fixture();
  context.documents[0].activeIndexVersion = 'v1';
  let calls = 0;
  let release;
  context.loadStudioTabDataOnce = () => { calls++; return new Promise(resolve => { release = resolve; }); };
  const first = context.loadStudioTabData('flashcard');
  const second = context.loadStudioTabData('flashcard');
  assert.equal(calls, 1);
  release();
  await Promise.all([first, second]);
});

test('Quiz sends V2 enum values and reads saved answer options after generation', async () => {
  const calls = [];
  let reads = 0;
  const { context } = fixture('READY', async (url, options) => {
    calls.push({ url, options });
    if (options?.method === 'POST') {
      const body = JSON.parse(options.body);
      assert.equal(body.mode, 'PRACTICE');
      assert.deepEqual(body.allowedQuestionTypes, ['MCQ_SINGLE']);
      assert.equal(Object.hasOwn(body, 'questionCount'), false);
      return { ok: true, json: async () => ({ questions: [{ questionId: 1 }] }) };
    }
    return { ok: true, json: async () => ({ questions: ++reads === 1 ? [] : [{ id: 1, question: 'Q', options: ['A', 'B'] }] }) };
  });
  context.renderQuizInline = () => {};
  vm.runInContext('let currentQuizQuestions = [];', context);
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('quiz');
  assert.equal(calls.length, 3);
  assert.equal(vm.runInContext('currentQuizQuestions[0].options.length', context), 2);
});

test('Flashcard viewer requests a quick batch without fixing count and reloads active cards', async () => {
  let reads = 0;
  const { context } = fixture('READY', async (url, options) => {
    if (options?.method === 'POST') {
      assert.deepEqual(JSON.parse(options.body), { quickStart: true });
      return { ok: true, json: async () => ({ requestedCount: 50, persistedCount: 1, duplicateSkippedCount: 0 }) };
    }
    return { ok: true, json: async () => ++reads === 1 ? [] : [{ id: 1, question: 'Q', answer: 'A' }, { id: 2, deletedAt: 'date' }] };
  });
  context.renderFlashcard = () => {};
  vm.runInContext('let currentFlashcards = []; let currentFcIndex = 0; let isFcShowingFront = true;', context);
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('flashcard');
  assert.equal(reads, 2);
  assert.equal(vm.runInContext('currentFlashcards.length', context), 1);
});

test('generate more cards shares concurrent POST and preserves old cards when generation fails', async () => {
  let posts = 0, release;
  const gate = new Promise(resolve => { release = resolve; });
  const { context } = fixture('READY', async (url, options) => {
    if (options?.method === 'POST') { posts++; await gate; return { ok: false }; }
    return { ok: true, json: async () => [{ id: 1 }] };
  });
  context.renderFlashcard = () => {};
  vm.runInContext('let currentFlashcards = [{id: 1}];', context);
  vm.runInContext(studioOnce, context);
  const first = context.generateMoreFlashcards(); const second = context.generateMoreFlashcards();
  await Promise.resolve(); await Promise.resolve(); release(); await Promise.all([first, second]);
  assert.equal(posts, 1); assert.equal(vm.runInContext('currentFlashcards[0].id', context), 1);
  assert.ok(context.document.getElementById('flashcardGenerationStatus').textContent.includes('thử lại'));
});

test('Quiz generation failure shows an error instead of silently rendering an empty quiz', async () => {
  const { context } = fixture('READY', async (url, options) => options?.method === 'POST'
    ? { ok: false, status: 400 }
    : { ok: true, json: async () => ({ questions: [] }) });
  context.renderQuizInline = () => { throw new Error('must not render empty quiz'); };
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('quiz');
  assert.ok(context.document.getElementById('quizInlineBox').textContent.includes('thử lại'));
});

test('creating a new quiz bypasses old quiz, shares concurrent requests and reads the generated quiz', async () => {
  const calls = [];
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { context } = fixture('READY', async (url, options) => {
    calls.push(url);
    if (options?.method === 'POST') {
      await gate;
      return { ok: true, json: async () => ({ quizId: 42, requestedCount: 40, generatedCount: 1 }) };
    }
    assert.equal(url, '/api/quizzes/42/questions');
    return { ok: true, json: async () => [{ id: 42, question: 'Bài mới', options: ['A', 'B'] }] };
  });
  context.renderQuizInline = () => {};
  vm.runInContext('let currentQuizQuestions = [];', context);
  vm.runInContext(studioOnce, context);
  const first = context.regenerateDocumentQuiz();
  const second = context.regenerateDocumentQuiz();
  assert.equal(calls.length, 1);
  assert.ok(context.document.getElementById('quizInlineBox').textContent.includes('Đang tạo câu hỏi'));
  release(); await Promise.all([first, second]);
  assert.deepEqual(calls, ['/api/quizzes/generate', '/api/quizzes/42/questions']);
  assert.equal(vm.runInContext('currentQuizQuestions[0].id', context), 42);
  assert.ok(context.document.getElementById('quizCounter').textContent.includes('1/40'));
  assert.equal(context.currentQuizId,42);
  assert.equal(vm.runInContext('currentQuizQuestions[0].questionId',context),42);
});

test('late generated quiz cannot replace a newly selected document', async () => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const { context } = fixture('READY', async (url, options) => {
    if (options?.method === 'POST') { await gate; return { ok: true, json: async () => ({ quizId: 42 }) }; }
    return { ok: true, json: async () => [{ id: 42 }] };
  });
  context.renderQuizInline = () => { throw new Error('stale quiz must not render'); };
  vm.runInContext('let currentQuizQuestions = [];', context);
  vm.runInContext(studioOnce, context);
  const pending = context.regenerateDocumentQuiz();
  context.selectedDocIds = [999]; release(); await pending;
  assert.equal(vm.runInContext('currentQuizQuestions.length', context), 0);
});

test('both actual summary consumers use shared loader; raw backend errors are not rendered', () => {
  assert.equal((html.match(/await loadDocumentSummary\(docId\)/g) || []).length, 2);
  assert.equal((summaryLoader.match(/summary\/generate/g) || []).length, 1);
  assert.ok(html.includes('src="js/document-summary.js"'));
  assert.ok(!html.includes('"Không thể tự động tóm tắt: " + (error.error'));
  assert.ok(!html.includes('Xử lý tài liệu thất bại: ${doc.errorMessage'));
});

test('actual viewer map tab passes canonical graph to diagram renderer', async () => {
  const { context } = fixture();
  const graph = { nodes: [{ id: 1, name: 'Activity' }], edges: [] };
  context.DocumentMindMap = { load: async () => graph, showStatus() {} };
  let rendered;
  context.MindMapRenderer = { render: (container, data) => { rendered = data; } };
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('map');
  assert.equal(rendered, graph);
});

test('actual viewer renders partial graph and its notice together', async () => {
  const { context } = fixture();
  const graph = { nodes: [{ id: 1, name: 'Activity' }], edges: [], generationStatus: 'PARTIAL_SUCCESS' };
  const actions = [];
  context.DocumentMindMap = { load: async () => graph, showStatus: (box, data) => actions.push(['notice',box,data]) };
  context.MindMapRenderer = { render: (box, data) => actions.push(['render',box,data]) };
  vm.runInContext(studioOnce, context);
  await context.loadStudioTabData('map');
  assert.deepEqual(actions.map(x=>x[0]), ['render','notice']);
  assert.equal(actions[0][1], actions[1][1]);
  assert.equal(actions[1][2], graph);
});
