const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const root=path.join(__dirname,'../../main/resources/static');
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
for(const file of walk(root).filter(x=>/\.(html|js|css)$/.test(x))){
 const name=path.relative(root,file),source=fs.readFileSync(file,'utf8');
 test(`${name}: valid UTF-8 without replacement characters`,()=>{
  assert.equal(new TextDecoder('utf-8',{fatal:true}).decode(fs.readFileSync(file)),source);
  assert.ok(!source.includes('\uFFFD'),'Broken replacement character');
 });
 if(file.endsWith('.js'))test(`${name}: JavaScript parses`,()=>{new vm.Script(source,{filename:name});});
 if(!file.endsWith('.html'))continue;
 test(`${name}: UTF-8 metadata, script syntax and local assets`,()=>{
  assert.match(source,/<meta\s+charset=["']?utf-8/i);
  for(const [i,m] of [...source.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)].entries()){
   if(!/\bsrc\s*=/.test(m[1])&&!/application\/(?:json|ld\+json)/.test(m[1]))new vm.Script(m[2],{filename:name+':'+i});
  }
  for(const m of source.matchAll(/(?:src|href)\s*=\s*["']([^"'#?]+)["']/gi)){
   if(/^(?:https?:|data:|mailto:|tel:|javascript:|\/api\/|\$|#)/.test(m[1]))continue;
   assert.ok(fs.existsSync(path.resolve(path.dirname(file),m[1].replace(/^\//,''))),`Missing asset: ${m[1]}`);
  }
 });
}
function client(fetch){const c=vm.createContext({fetch,FormData,Blob});vm.runInContext(fs.readFileSync(path.join(root,'js/api-client.js'),'utf8'),c);return vm.runInContext('ApiClient',c);}
test('download with malformed encoded filename still succeeds',async()=>{
 const api=client(async()=>new Response('pptx',{headers:{'content-disposition':"attachment; filename*=UTF-8''bad%ZZ.pptx"}}));
 const result=await api.get('/export',{}, {responseType:'blob'});
 assert.equal(result.filename,'presentation.pptx');assert.equal(await result.blob.text(),'pptx');
});
test('null network rejection is normalized instead of crashing error handler',async()=>{
 const api=client(async()=>{throw null;});await assert.rejects(api.get('/test'),e=>e.status===503&&e.code==='NETWORK_ERROR');
});
test('server error details never reach the user',async()=>{
 const api=client(async()=>new Response(JSON.stringify({error:'private SQL password'}),{status:500,headers:{'content-type':'application/json'}}));
 await assert.rejects(api.get('/test'),e=>e.status===500&&!e.message.includes('private'));
});
test('no-content and Vietnamese JSON responses retain their data',async()=>{
 assert.equal(await client(async()=>new Response(null,{status:204})).get('/test'),null);
 const result=await client(async()=>new Response(JSON.stringify({text:'Tiếng Việt: học tập'}),{headers:{'content-type':'application/json'}})).get('/test');
 assert.equal(result.text,'Tiếng Việt: học tập');
});

test('all translation keys exist and translations do not erase nested styled markup',()=>{
 const c=vm.createContext({window:{addEventListener(){}}});
 vm.runInContext(fs.readFileSync(path.join(root,'i18n.js'),'utf8'),c);
 const dictionary=vm.runInContext('translations',c);
 for(const file of walk(root).filter(x=>x.endsWith('.html'))){
  const source=fs.readFileSync(file,'utf8');
  for(const m of source.matchAll(/data-i18n(?:-placeholder)?="([^"]+)"/g))assert.ok(dictionary[m[1]],`${file}: missing translation ${m[1]}`);
  for(const m of source.matchAll(/<([\w-]+)\b[^>]*\bdata-i18n="([^"]+)"[^>]*>([\s\S]*?)<\/\1>/g))assert.ok(!/<[a-z]/i.test(m[3]),`${file}: translation ${m[2]} would erase child elements`);
 }
});

test('browser error reporter redacts query strings, limits reports and handles reporting failures',async()=>{
 const listeners={},calls=[];
 const context=vm.createContext({Date,URL,Number,location:{href:'http://localhost/viewer.html?key=secret',origin:'http://localhost'},window:{addEventListener:(name,fn)=>listeners[name]=fn},fetch:(url,options)=>{calls.push(JSON.parse(options.body));return Promise.reject(new Error('offline'));}});
 vm.runInContext(fs.readFileSync(path.join(root,'js/runtime-errors.js'),'utf8'),context);
 for(let i=0;i<9;i++)listeners.error({filename:'http://localhost/js/test.js?token=private',lineno:12,colno:4,message:'password=secret'});
 await new Promise(r=>setImmediate(r));
 assert.equal(calls.length,5);
 assert.equal(calls[0].source,'/js/test.js');
 assert.equal(calls[0].line,12);
 assert.ok(!JSON.stringify(calls).includes('secret'));
 assert.ok(!JSON.stringify(calls).includes('private'));
});
