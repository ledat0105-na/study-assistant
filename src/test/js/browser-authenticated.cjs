const {chromium}=require('playwright');const fs=require('fs'),path=require('path');
const base='http://127.0.0.1:18080',out=path.resolve('build/qa-authenticated');fs.mkdirSync(out,{recursive:true});
(async()=>{const context=await chromium.launchPersistentContext(path.join(out,'profile'),{channel:'chrome',headless:true,timeout:30000});const results=[];
try{
 const username='qa_browser_'+Date.now(),password='QaTest123!';
 let response=await context.request.post(base+'/api/auth/register',{data:{name:'Kiểm thử Tiếng Việt',username,email:username+'@example.invalid',password}});if(!response.ok())throw Error('Registration '+response.status());
 let login=await context.request.post(base+'/api/auth/login',{data:{usernameOrEmail:username,password}});if(!login.ok())throw Error('Login '+login.status());const user=(await login.json()).user;
 fs.writeFileSync(path.join(out,'fixture.json'),JSON.stringify({username,password,user}));
 const pages=fs.readdirSync('src/main/resources/static').filter(f=>f.endsWith('.html')&&!f.startsWith('admin')&&!['login.html','register.html','index.html'].includes(f));
 for(const role of ['STUDENT','ADMIN']){
  if(role==='ADMIN'){login=await context.request.post(base+'/api/auth/login',{data:{usernameOrEmail:'admin',password:'Admin123!'}});assertOk(login,'admin login');}
  const current=role==='ADMIN'?(await login.json()).user:user;
  const seedPage=await context.newPage();await seedPage.goto(base+'/',{waitUntil:'domcontentloaded'});await seedPage.evaluate(value=>localStorage.setItem('user',JSON.stringify(value)),current);await seedPage.close();
  for(const file of role==='ADMIN'?fs.readdirSync('src/main/resources/static').filter(f=>f.startsWith('admin')&&f.endsWith('.html')):pages){
   const page=await context.newPage(),errors=[],consoleErrors=[],serverErrors=[];
   page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error'&&!m.text().includes('Failed to load resource'))consoleErrors.push(m.text().slice(0,200));});
   page.on('response',r=>{if(r.url().startsWith(base+'/api/')&&r.status()>=500)serverErrors.push(r.url().slice(base.length)+':'+r.status());});
   try{await page.setViewportSize({width:390,height:844});await page.goto(base+'/'+file,{waitUntil:'networkidle',timeout:45000});await page.evaluate(()=>document.fonts.ready);
    const metrics=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,text:document.body.innerText.length}));
    await page.screenshot({path:path.join(out,file.replace('.html','')+'-390.png'),fullPage:true});
    const responsive=[{...metrics}];
    for(const width of [768,1440]){await page.setViewportSize({width,height:1000});await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));responsive.push(await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth})));}
    const result={role,file,url:page.url().replace(base,''),errors,consoleErrors,serverErrors,...metrics,responsive};results.push(result);console.log(JSON.stringify(result));
   }catch(e){results.push({role,file,error:e.message.slice(0,200)});console.log('FAILED',file,e.message.slice(0,100));}finally{await page.close();}
  }
 }
 await require('./live-flows.cjs')(context,base,out);
 const flowResults=JSON.parse(fs.readFileSync(path.join(out,'flow-results.json'),'utf8'));
 if(flowResults.some(r=>r.status==='FAIL')||results.some(r=>r.error||r.errors?.length||r.consoleErrors?.length||r.serverErrors?.length||r.responsive?.some(v=>v.scroll>v.width+1)))process.exitCode=1;
}finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));await context.close();}
function assertOk(r,label){if(!r.ok())throw Error(label+' '+r.status());}
})().catch(e=>{console.error(e.message);process.exitCode=1});
