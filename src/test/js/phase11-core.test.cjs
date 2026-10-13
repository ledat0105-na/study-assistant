const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const root = path.join(__dirname, '../../main/resources/static');
const read = name => fs.readFileSync(path.join(root, name), 'utf8');
class Element {
  constructor(tag='div') { this.tag=tag; this.children=[]; this.attrs={}; this.events={}; this.classList={add(){},remove(){}}; }
  set textContent(value) { this.text=String(value); this.children=[]; }
  get textContent() { return (this.text || '')+this.children.map(c=>c.textContent).join(''); }
  set innerHTML(_) { throw Error('HTML rendering forbidden in safe renderer tests'); }
  appendChild(c) { this.children.push(c); c.parentElement=this; return c; }
  append(...c) { c.forEach(e=>this.appendChild(e)); }
  replaceChildren(...c) { this.children=[]; this.text=''; this.append(...c); }
  setAttribute(k,v) { this.attrs[k]=String(v); }
  addEventListener(k,f) { this.events[k]=f; }
}
const descendants = (node,tag) => node.children.flatMap(c=>[...(c.tag===tag?[c]:[]),...descendants(c,tag)]);
function setup(fetch=async()=>new Response('{}', {headers:{'content-type':'application/json'}})) {
  const context=vm.createContext({fetch,URL,URLSearchParams,FormData,Blob,Response,crypto:require('node:crypto').webcrypto,
    document:{createElement:tag=>new Element(tag),createTextNode:text=>Object.assign(new Element('#text'),{textContent:text})}});
  context.window=context;
  for (const name of ['js/api-client.js','js/document-ui.js','js/document-features.js','citations.js','js/summary-view.js','js/document-summary.js']) vm.runInContext(read(name),context);
  context.DocumentSummaryLoader = vm.runInContext('DocumentSummaryLoader',context);
  return context;
}
const response = data => new Response(JSON.stringify(data),{headers:{'content-type':'application/json'}});
const ready = () => ({id:17,status:'READY',activeIndexVersion:'v1'});
for (const doc of [null,{id:17,status:'PROCESSING'}, {id:17,status:'FAILED'}, {id:17,status:'READY',activeIndexVersion:''}]) {
  test(`all shared AI loaders reject ${doc?.status || 'MISSING'} without fetch`, async()=>{
    let calls=0; const c=setup(async()=>{calls++;});
    await assert.rejects(c.DocumentFeatures.flashcards(doc));
    await assert.rejects(c.DocumentFeatures.quiz(doc));
    await assert.rejects(c.DocumentFeatures.chat([doc],'Question'));
    assert.equal(calls,0);
  });
}
test('flashcard single flight shares GET/POST/GET and preserves quick batch behavior',async()=>{
  let release; const wait=new Promise(r=>release=r),calls=[];
  const c=setup(async(url,options)=>{
    calls.push([url,options]);
    if(options.method==='POST') {await wait;return response({persistedCount:1});}
    return response(calls.length===1?[]:[{id:1},{id:2,deletedAt:'deleted'}]);
  });
  const doc=ready(),a=c.DocumentFeatures.flashcards(doc),b=c.DocumentFeatures.flashcards(doc);
  assert.equal(a,b); await new Promise(r=>setImmediate(r)); release();
  const data=await a; assert.equal(data.cards.length,1); assert.equal(calls.length,3);
  assert.equal(calls[1][0],'/api/documents/17/flashcards/generate');
  assert.deepEqual(JSON.parse(calls[1][1].body),{quickStart:true});
  assert.ok(calls.every(([,o])=>o.credentials==='same-origin'));
});
test('existing stored quiz uses only read endpoint and returns real quiz ID',async()=>{
  const calls=[]; const c=setup(async(url,o)=>{calls.push(o.method);return response({quizId:9,questions:[{id:1}]});});
  const stored = await c.DocumentFeatures.quiz(ready());
  assert.equal(stored.quizId,9); assert.equal(stored.questions[0].questionId,1);
  assert.deepEqual(calls,['GET']);
});
test('version changing during empty read prevents any generation POST',async()=>{
  const doc=ready(),calls=[];
  const c=setup(async(url,o)=>{calls.push(o.method);doc.activeIndexVersion='v2';return response([]);});
  await assert.rejects(c.DocumentFeatures.flashcards(doc)); assert.deepEqual(calls,['GET']);
});
test('summary version changes do not reuse an old in-flight cache request',async()=>{
  const doc=ready(); let release;const wait=new Promise(r=>release=r);let calls=0;
  const c=setup(async()=>{calls++; await wait;return response({summary:'Old summary',indexVersion:'v1'});});
  const a=c.DocumentSummaryLoader.load(17,doc);doc.activeIndexVersion='v2';const b=c.DocumentSummaryLoader.load(17,doc);
  assert.notEqual(a,b);release();await assert.rejects(a);await assert.rejects(b);assert.equal(calls,2);
});
for (const status of [401,403,404,409,500,502]) {
  test(`shared API HTTP ${status} hides HTML stack trace and JSON exception text`,async()=>{
    const c=setup(async()=>new Response('<html>SQL secret stack trace</html>',{status,headers:{'content-type':'text/html'}}));
    await assert.rejects(vm.runInContext("ApiClient.get('/test')",c),error=>error.status===status&&!error.message.includes('SQL'));
  });
}
test('structured summary renders every field and source link as safe DOM',()=>{
  const c=setup(),box=new Element();
  c.SummaryView.render(box,{docId:17,summary:'fallback',content:{overview:'<img onerror=alert(1)>',keyPoints:[{text:'Ý chính',sourceRefs:['S1']}],
    importantTerms:[{term:'Activity',definition:'Một màn hình'}],conclusion:'Kết luận'},
    citations:[{sourceId:'S1',documentId:17,pageStart:3,pageEnd:4,sectionTitle:'<script>bad</script>'}]});
  for(const label of ['Tổng quan','Ý chính','Thuật ngữ quan trọng','Kết luận','Nguồn tham chiếu']) assert.ok(box.textContent.includes(label));
  assert.ok(box.textContent.includes('<img onerror=alert(1)>'));assert.equal(descendants(box,'img').length,0);
  assert.equal(descendants(box,'script').length,0);assert.ok(descendants(box,'a').some(a=>a.href==='reader.html?id=17&page=3'));
});
test('summary string fallback and quoted citations preserve literal text safely',()=>{
  const c=setup(),box=new Element();
  c.SummaryView.render(box,{docId:17,summary:'<b>literal</b> [Trang 2: "x & y"]'});
  assert.equal(descendants(box,'b').length,0);assert.equal(descendants(box,'a')[0].href,'reader.html?id=17&page=2&q=x+%26+y');
  const unsafe=new Element();c.renderCitationSources(unsafe,[{documentId:'javascript:alert(1)',pageStart:1}],null);
  assert.equal(descendants(unsafe,'a').length,0);
});
test('chat checks every selected document and uses only grounded endpoint and safe insufficient-evidence text',async()=>{
  const calls=[]; const c=setup(async(url,o)=>{calls.push(url);return response({response:'uncited',sources:[]});});
  await assert.rejects(c.DocumentFeatures.chat([ready(),{id:18,status:'PROCESSING'}],'Question'));
  assert.equal(calls.length,0);
  const data=await c.DocumentFeatures.chat([ready()],'Question');assert.deepEqual(calls,['/api/ai/chat']);
  assert.ok(data.text.includes('đủ bằng chứng'));
});
test('quiz attempts submit actual snapshot option orders with unchanged idempotency token',async()=>{
  const calls=[];const c=setup(async(url,o)=>{calls.push([url,JSON.parse(o.body)]);return response({attemptId:6});});
  await c.DocumentFeatures.startAttempt(9);
  const attempt={attemptId:6,questions:[{questionId:31,questionOrder:8},{questionId:32,questionOrder:9}]};
  await c.DocumentFeatures.submitAttempt(attempt,{31:5},'same-token');
  await c.DocumentFeatures.submitAttempt(attempt,{31:5},'same-token');
  assert.equal(calls[0][0],'/api/quizzes/9/attempts');assert.equal(calls[1][0],'/api/quiz-attempts/6/submit');
  assert.deepEqual(calls[1][1].answers,[{questionOrder:8,selectedOptionOrders:[5],shortAnswerText:null},{questionOrder:9,selectedOptionOrders:[],shortAnswerText:null}]);
  assert.deepEqual(calls[1][1],calls[2][1]);
  assert.deepEqual(Object.keys(calls[1][1]).sort(),['answers','submissionToken']);
});
test('auth redirects once only for same-origin 401; supports Request objects',async()=>{
  const calls=[],removed=[];let href='http://localhost/viewer.html';const location={origin:'http://localhost',pathname:'/viewer.html',get href(){return href;},set href(value){href=new URL(value,href).href;}};const window={location,
    fetch:async(input,o)=>{calls.push(o);return new Response('',{status:401});}};
  vm.runInNewContext(read('auth-interceptor.js'),{window,URL,localStorage:{removeItem:key=>removed.push(key)}});
  await window.fetch('https://cdn.example/library.js');assert.equal(removed.length,0);
  await window.fetch(new Request('http://localhost/api/documents'));await window.fetch('/api/documents');
  assert.deepEqual(removed,['user']);assert.equal(window.location.href,'http://localhost/login.html');assert.equal(calls[1].credentials,'same-origin');
});
test('all frontend scripts parse; entry points load shared dependencies; legacy AI generation paths absent',()=>{
  for(const name of fs.readdirSync(root).filter(n=>n.endsWith('.html'))) {
    for(const script of read(name).matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(script[1],{filename:name});
  }
  for(const name of ['viewer','reader','map','documents','upload','detail','flashcards','quiz']) {
    const html=read(`${name}.html`);assert.ok(html.includes('js/document-ui.js'));assert.ok(html.includes('js/document-features.js'));
    assert.ok(!html.includes('/api/topics/document'));assert.ok(!html.includes('/api/ai/summary'));
  }
  assert.ok(!read('quiz.html').includes('/api/quizzes/${quizId}/submit'));
});
