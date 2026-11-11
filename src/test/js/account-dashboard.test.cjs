const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.join(__dirname,'../../main/resources/static');
test('dashboard displays zero flashcards without ReferenceError or stopping document loading',async()=>{
 const elements=new Map(),errors=[],calls=[];
 const document={getElementById:id=>{if(!elements.has(id))elements.set(id,{textContent:'',innerHTML:''});return elements.get(id);}};
 const c=vm.createContext({document,localStorage:{getItem:()=>'{"id":7}'},renderUnifiedSidebar(){},lucide:{createIcons(){}},console:{error:(...args)=>errors.push(args)},fetch:async url=>{calls.push(url);return {ok:true,json:async()=>url.endsWith('/dashboard')?{totalDocuments:0,totalTopics:0,totalFlashcards:0,averageAccuracy:'37.5%'}:[]};}});
 const html=fs.readFileSync(path.join(root,'dashboard.html'),'utf8');
 const inline=[...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/g)].filter(m=>m[1].trim());
 vm.runInContext(inline.at(-1)[1],c);await new Promise(r=>setImmediate(r));
 assert.deepEqual(errors,[]);assert.equal(elements.get('statFlashcards').textContent,0);assert.ok(calls.includes('/api/documents'));assert.ok(calls.includes('/api/progress/dashboard'));assert.equal(elements.get('statAccuracy').textContent,'37.5%');
});
test('profile save sends PATCH, updates local state and restores the submit button',async()=>{
 const elements=new Map(),requests=[],saved=[];let submit;
 const button={disabled:false};
 const document={getElementById:id=>{if(!elements.has(id))elements.set(id,{value:'',textContent:'',addEventListener:(name,fn)=>{if(name==='submit')submit=fn;},querySelector:()=>button});return elements.get(id);}};
 const c=vm.createContext({document,renderUnifiedSidebar(){},window:{},localStorage:{setItem:(key,value)=>saved.push(JSON.parse(value))},fetch:async(url,options)=>{requests.push([url,options]);return {ok:true,json:async()=>options?{name:'Tên mới',email:'new@example.com',plan:'FREE'}:{name:'Tên cũ',email:'old@example.com',plan:'FREE'}};}});
 vm.runInContext(fs.readFileSync(path.join(root,'js/profile.js'),'utf8'),c);await new Promise(r=>setImmediate(r));
 elements.get('fullNameInput').value='Tên mới';elements.get('emailInput').value='new@example.com';
 await submit({preventDefault(){}});
 assert.equal(requests[1][0],'/api/auth/profile');assert.equal(requests[1][1].method,'PATCH');
 assert.deepEqual(JSON.parse(requests[1][1].body),{name:'Tên mới',email:'new@example.com'});
 assert.equal(saved.at(-1).name,'Tên mới');assert.equal(button.disabled,false);assert.match(elements.get('profileStatus').textContent,/thành công/);
});
