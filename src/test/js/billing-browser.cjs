const fs=require('fs'),path=require('path'),assert=require('node:assert/strict'),{chromium,request}=require('playwright'),{spawnSync}=require('child_process');
const base=process.env.BILLING_QA_BASE_URL||'http://127.0.0.1:18082';
if(!/^http:\/\/127\.0\.0\.1:1808[0-9]$/.test(base))throw Error('Use a dedicated local QA server');
const out=path.resolve('build/billing-browser');fs.mkdirSync(out,{recursive:true});
(async()=>{
 const context=await chromium.launchPersistentContext(path.join(out,'profile'),{channel:'chrome',headless:true});const results=[];
 async function check(name,fn){await fn();results.push({name,status:'PASS'});console.log('PASS',name);}
 try{
  const username='billing_browser_'+Date.now(),password='QaBilling123!';
  await check('new account starts FREE even if VIP was supplied',async()=>{
   const r=await context.request.post(base+'/api/auth/register',{data:{name:'Kiểm thử gói sử dụng',username,email:username+'@test.invalid',password,plan:'VIP'}});assert.equal(r.status(),200);assert.equal((await r.json()).user.plan,'FREE');
  });
  const page=await context.newPage();page.on('dialog',d=>d.accept());await page.setViewportSize({width:390,height:844});
  await page.goto(base+'/index.html?modal=login',{waitUntil:'networkidle'});await page.locator('#loginUsernameInput').fill(username);await page.locator('#loginPasswordInput').fill(password);await page.locator('#modalLoginForm button[type="submit"]').click();await page.waitForURL('**/dashboard.html');
  await check('FREE badge and profile limits',async()=>{
   await page.goto(base+'/profile.html',{waitUntil:'networkidle'});await page.waitForFunction(()=>document.querySelector('#billingQuota')?.textContent.includes('20'));
   assert.match(await page.locator('#billingQuota').textContent(),/Gói FREE/);assert.ok(await page.locator('main').evaluate(e=>e.getBoundingClientRect().width>=innerWidth*.85),'Mobile sidebar must not squeeze profile content');await page.screenshot({path:path.join(out,'free-390.png'),fullPage:true});for(const width of [768,1440]){await page.setViewportSize({width,height:1000});if(width<1024)assert.ok(await page.locator('main').evaluate(e=>e.getBoundingClientRect().width>=innerWidth*.85));await page.screenshot({path:path.join(out,'free-'+width+'.png'),fullPage:true});}await page.setViewportSize({width:390,height:844});
  });
  function seed(feature,count){const r=spawnSync('python',['scripts/billing-qa-seed.py','usage',username,feature,String(count)],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);}
  async function blocked(next){const status=await page.evaluate(async()=>{const r=await fetch('/api/ai/chat',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({message:'quota check'})});return r.status;});assert.equal(status,429);await page.locator('.billing-upgrade-dialog').waitFor({state:'visible'});assert.match(await page.locator('.billing-upgrade-panel a').getAttribute('href'),new RegExp('plan='+next));await page.locator('.billing-upgrade-panel button').click();}
  await check('FREE exhausted requires PRO and cannot bypass via API',async()=>{seed('CHAT',20);await blocked('PRO');});
  await check('all six public prices and mobile layout',async()=>{
   await page.goto(base+'/index.html#pricing',{waitUntil:'networkidle'});
   for(const [term,pro,vip] of [['1','50.000','100.000'],['3','120.000','270.000'],['12','360.000','960.000']]){
    await page.selectOption('#pricingTermSelect',term);await page.waitForFunction(({pro,vip})=>{const t=document.getElementById('pricingPlansContainer').textContent;return t.includes(pro)&&t.includes(vip);},{pro,vip});
   }
   for(const width of [390,768,1440]){await page.setViewportSize({width,height:1000});const size=await page.evaluate(()=>({w:innerWidth,scroll:document.documentElement.scrollWidth}));assert.ok(size.scroll<=size.w+1,JSON.stringify(size));await page.screenshot({path:path.join(out,'pricing-'+width+'.png'),fullPage:true});}
   await page.setViewportSize({width:390,height:844});
  });
  const admin=await request.newContext();assert.equal((await admin.post(base+'/api/auth/login',{data:{usernameOrEmail:'admin',password:'Admin123!'}})).status(),200);
  async function checkout(plan,months,amount){
   await page.goto(base+`/payment.html?plan=${plan}&months=${months}`,{waitUntil:'networkidle'});await page.waitForFunction(()=>!document.getElementById('submitSubscription').disabled);
   assert.match(await page.locator('#planPrice').textContent(),new RegExp(amount.toLocaleString('vi-VN').replaceAll('.','\\.')));
   const size=await page.evaluate(()=>({w:innerWidth,scroll:document.documentElement.scrollWidth,main:document.querySelector('main').getBoundingClientRect().width}));assert.ok(size.scroll<=size.w+1&&size.main>=size.w*.85,JSON.stringify(size));
   await page.screenshot({path:path.join(out,'checkout-'+plan+'-'+months+'.png'),fullPage:true});await page.locator('#submitSubscription').click();await page.getByRole('dialog', {name:'Thành công'}).getByRole('button', {name:'Đã hiểu'}).click();await page.waitForURL('**/profile.html');
   const history=await(await context.request.get(base+'/api/payments/history')).json();const payment=history.filter(p=>p.plan===plan&&p.status==='PENDING').sort((a,b)=>b.id-a.id)[0];assert.equal(payment.amount,amount);assert.equal(payment.durationMonths,months);
   assert.equal((await admin.post(base+`/api/payments/${payment.id}/approve`)).status(),200);await page.reload({waitUntil:'networkidle'});await page.waitForFunction(plan=>document.querySelector('#billingQuota')?.textContent.includes('Gói '+plan),plan);
  }
  await check('PRO 12 months costs 360k and activates only after approval',async()=>{await checkout('PRO',12,360000);const status=await(await context.request.get(base+'/api/billing/status')).json();assert.equal(status.currentPlan,'PRO');assert.equal(status.features.CHAT.limit,500);assert.equal(status.features.UPLOAD.limit,50);});
  await check('PRO exhausted requires VIP',async()=>{seed('CHAT',500);await blocked('VIP');});
  await check('VIP 3 months costs 270k and has no usage cap',async()=>{await checkout('VIP',3,270000);seed('CHAT',100000);const r=await context.request.post(base+'/api/ai/chat',{data:{}});assert.equal(r.status(),400);const status=await(await context.request.get(base+'/api/billing/status')).json();assert.equal(status.currentPlan,'VIP');assert.equal(status.features.CHAT.limit,-1);assert.equal(status.features.UPLOAD.limit,-1);await page.reload({waitUntil:'networkidle'});await page.screenshot({path:path.join(out,'vip-390.png'),fullPage:true});});
  await check('expiry automatically returns to FREE',async()=>{const r=spawnSync('python',['scripts/billing-qa-seed.py','expire',username],{encoding:'utf8'});assert.equal(r.status,0,r.stderr);await page.reload({waitUntil:'networkidle'});await page.waitForFunction(()=>document.querySelector('#billingQuota')?.textContent.includes('Gói FREE'));const status=await(await context.request.get(base+'/api/billing/status')).json();assert.equal(status.currentPlan,'FREE');assert.equal(status.subscriptionStatus,'EXPIRED');});
  await admin.dispose();
 }finally{fs.writeFileSync(path.join(out,'results.json'),JSON.stringify(results,null,2));await context.close();}
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
