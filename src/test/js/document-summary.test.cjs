const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const code = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/document-summary.js'), 'utf8');
function loader(fetch) {
  const context = vm.createContext({ fetch });
  vm.runInContext(code + ';globalThis.loader = DocumentSummaryLoader;', context);
  return { load: (id, doc = { id, status: 'READY', activeIndexVersion: 'v1' }) => context.loader.load(id, doc) };
}

test('cache miss 204 generates summary once for simultaneous callers', async () => {
  const calls = [];
  const api = loader(async (url,options) => {
    calls.push({url,options});
    return options.method ? {ok:true,json:async()=>({summary:'Kết quả'})} : {status:204};
  });
  const a=api.load(3710), b=api.load('3710');
  assert.equal(a,b); assert.equal((await a).summary,'Kết quả');
  assert.equal(calls.length,2); assert.equal(calls[1].options.method,'POST');
});
test('previous backend cache-miss JSON 404 still generates', async () => {
  let posts=0;
  const api=loader(async (_,options)=> options.method ? (posts++,{ok:true,json:async()=>({summary:'New'})}) :
    {status:404,json:async()=>({error:'No current Summary V2 cache'})});
  await api.load(3710); assert.equal(posts,1);
});
test('explicit owned-document cache state 200 generates exactly once', async () => {
  let posts=0;
  const api=loader(async(_,options)=> options.method ? (posts++,{ok:true,json:async()=>({docId:3719,summary:'New'})}) :
    {status:200,ok:true,json:async()=>({docId:3719,cached:false,summary:null})});
  const results=await Promise.all([api.load(3719),api.load('3719'),api.load(3719)]);
  assert.equal(posts,1); assert.equal(results[0].summary,'New');
});
test('cached result stops without POST', async () => {
  let calls=0;
  const api=loader(async()=>{calls++;return {status:200,ok:true,json:async()=>({docId:3719,cached:true,summary:'Saved'})};});
  assert.equal((await api.load(3719)).summary,'Saved'); assert.equal(calls,1);
});
test('ambiguous JSON 404 does not claim missing API and never generates', async () => {
  let calls=0;
  const api=loader(async()=>{calls++;return {status:404,json:async()=>({error:'Not Found'})};});
  await assert.rejects(api.load(3719),e=> e.code==='SUMMARY_NOT_FOUND' && !e.message.includes('API'));
  assert.equal(calls,1);
});
test('Spring missing-static-resource response proves the canonical route is unavailable', async () => {
  const api=loader(async()=>({status:404,json:async()=>({message:'No static resource api/documents/3719/summary.'})}));
  await assert.rejects(api.load(3719),e=>e.code==='SUMMARY_ENDPOINT_NOT_FOUND');
});
for (const doc of [
  {id:3719,status:'PROCESSING',activeIndexVersion:null},
  {id:3719,status:'FAILED',activeIndexVersion:null},
  {id:3719,status:'READY',activeIndexVersion:null},
  {id:3719,status:'READY'},
  {id:3719,status:'READY',activeIndexVersion:' '}
]) {
  test(`unready metadata ${JSON.stringify(doc)} prevents all summary requests`, async () => {
    let calls=0; const api=loader(async()=>{calls++;});
    await assert.rejects(api.load(3719,doc)); assert.equal(calls,0);
  });
}
test('document changed to PROCESSING while reading cache does not POST', async () => {
  let calls=0; const doc={id:3719,status:'READY',activeIndexVersion:'v1'};
  const api=loader(async()=>{calls++;doc.status='PROCESSING';return {status:200,ok:true,json:async()=>({docId:3719,cached:false,summary:null})};});
  await assert.rejects(api.load(3719,doc)); assert.equal(calls,1);
});
for(const code of ['DOCUMENT_NOT_FOUND','SUMMARY_ENDPOINT_NOT_FOUND']) {
  test(`${code} does not trigger generation or hide actual 404`, async () => {
    let count=0;
    const api=loader(async()=>{count++;return {status:404,json:async()=>({code})};});
    await assert.rejects(api.load(3710), e=> e.status===404 && e.code===code);
    assert.equal(count,1);
  });
}
test('Whitelabel missing resource 404 is shown as missing API, no generation attempt', async () => {
  let count=0;
  const api=loader(async()=>{count++;return {status:404,json:async()=>{throw Error('HTML');},text:async()=>"No static resource api/documents/3710/summary."};});
  await assert.rejects(api.load(3710),e=>e.code==='SUMMARY_ENDPOINT_NOT_FOUND');
  assert.equal(count,1);
});
test('failed generation releases shared request so user can retry and hides backend details', async () => {
  let failed=true;
  const api=loader(async(_,options)=> !options.method ? {status:204} : failed ?
    {status:502,ok:false,json:async()=>({error:'secret provider trace'})} : {ok:true,json:async()=>({summary:'Recovered'})});
  await assert.rejects(api.load(3710),e=>e.status===502 && !e.message.includes('secret'));
  failed=false;
  assert.equal((await api.load(3710)).summary,'Recovered');
});

test('reader uses shared generation flow and can retry failed summaries', async () => {
  const html=fs.readFileSync(path.join(__dirname,'../../main/resources/static/reader.html'),'utf8');
  assert.ok(html.includes('src="js/document-summary.js"'));
  const start=html.indexOf('    let summarized = false;');
  const end=html.indexOf('    function appendMessage(',start);
  let removed=0, calls=0; const messages=[], children=[];
  const context=vm.createContext({urlParams:new URLSearchParams(), SummaryView:{render:(_,data)=>messages.push(data.summary)}, docId:3710, summaryDocument:{id:3710,status:'READY',activeIndexVersion:'v1'},
    DocumentUI:{ready:()=>true}, DocumentSummaryLoader:{load:async()=>{calls++; if(calls===1)throw Error('offline'); return {summary:'Kết quả'};}},
    document:{createElement:()=>({remove(){removed++;},addEventListener(_,listener){this.click=listener;}}),
      getElementById:()=>({appendChild:child=>children.push(child)})},
    lucide:{createIcons(){}},appendMessage:(_,text)=>messages.push(text)
  });
  vm.runInContext(html.slice(start,end),context);
  await context.autoSummarize();
  const retry=children.at(-1); assert.equal(retry.textContent,'Thử tóm tắt lại');
  retry.click(); await new Promise(resolve=>setImmediate(resolve));
  assert.equal(calls,2); assert.equal(messages.at(-1),'Kết quả'); assert.ok(removed>=3);
});
