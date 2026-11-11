const fs=require('node:fs'),path=require('node:path'),http=require('node:http'),assert=require('node:assert/strict');
const {chromium}=require('playwright');
const root=path.resolve(__dirname,'../../main/resources/static');
const out=path.resolve(__dirname,'../../../build/ui-smoke');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 if(url.pathname.startsWith('/api/')){res.writeHead(503,{'content-type':'application/json'});res.end('{"code":"QA_BACKEND_UNAVAILABLE"}');return;}
 const file=path.resolve(root,'.'+(url.pathname==='/'?'/index.html':url.pathname));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
 const type={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'}[path.extname(file)]||'application/octet-stream';
 res.writeHead(200,{'content-type':type});fs.createReadStream(file).pipe(res);
});
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 let context;
 try{
  context=await chromium.launchPersistentContext(path.join(out,'profile'),{channel:process.env.QA_BROWSER||'chrome',headless:true,timeout:30000});
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  const base=`http://127.0.0.1:${server.address().port}`;
  for(const viewport of [{width:1440,height:1000},{width:390,height:844}]){
   await page.setViewportSize(viewport);
   await page.goto(base+'/index.html',{waitUntil:'networkidle',timeout:60000});
   await page.evaluate(()=>document.fonts.ready);
   assert.equal(await page.locator('h1 [data-i18n="hero_title_highlight"]').textContent(),'Sơ Đồ Kiến Thức 3D');
   const metrics=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,font:getComputedStyle(document.body).fontFamily}));
   assert.ok(metrics.scroll<=metrics.width+1,`Horizontal overflow: ${JSON.stringify(metrics)}`);
   const overlaps=await page.evaluate(()=>Array.from(document.querySelectorAll('#pricing .glass-3d-card')).filter(card=>{
    const badge=card.querySelector('.badge-3d'),title=card.querySelector('h3');
    if(!badge||!title)return false;
    const a=badge.getBoundingClientRect(),b=title.getBoundingClientRect();
    return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;
   }).length);
   assert.equal(overlaps,0,'Pricing badge covers plan title');
   await page.screenshot({path:path.join(out,`home-${viewport.width}.png`),fullPage:true});
   for(const modal of ['login','register']){
    await page.goto(base+`/index.html?modal=${modal}`,{waitUntil:'networkidle',timeout:60000});
    assert.ok(await page.locator('input[type="password"]:visible').count()>0,`${modal} password input missing`);
    await page.screenshot({path:path.join(out,`${modal}-${viewport.width}.png`),fullPage:true});
   }
   console.log(`PASS home, login, register at ${viewport.width}px (backend unavailable fallback)`);
  }
  assert.deepEqual(errors,[],'Browser runtime errors');
 }finally{if(context)await context.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;server.close();});
