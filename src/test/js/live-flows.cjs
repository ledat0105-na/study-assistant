const fs=require('fs'),path=require('path'),assert=require('node:assert/strict');
const {Document,Paragraph,Packer}=require('docx');
const {request}=require('playwright');
module.exports=async function(context,base,out){
 const fixture=JSON.parse(fs.readFileSync(path.join(out,'fixture.json'),'utf8')),results=[];
 async function check(name,fn){try{const detail=await fn();results.push({name,status:'PASS',detail});console.log('FLOW PASS',name,JSON.stringify(detail||{}));return detail;}catch(e){results.push({name,status:'FAIL',error:e.message.slice(0,350)});console.log('FLOW FAIL',name,e.message.slice(0,350));}}
 const api=context.request;
 await check('real session login hides credentials',async()=>{const r=await api.post(base+'/api/auth/login',{data:{usernameOrEmail:fixture.username,password:fixture.password}});assert.equal(r.status(),200);const u=(await r.json()).user;assert.ok(!('password' in u)&&!('resetToken' in u));return u.id;});
 const seedPage=await context.newPage();await seedPage.goto(base+'/',{waitUntil:'domcontentloaded'});await seedPage.evaluate(value=>localStorage.setItem('user',JSON.stringify(value)),fixture.user);await seedPage.close();
 await check('profile submit persists after reload',async()=>{const page=await context.newPage();try{await page.goto(base+'/profile.html',{waitUntil:'networkidle'});await page.locator('#fullNameInput').fill('Học viên kiểm thử cập nhật');await page.locator('#profileForm button[type="submit"]').click();await page.locator('#profileStatus').filter({hasText:'thành công'}).waitFor();await page.reload({waitUntil:'networkidle'});assert.equal(await page.locator('#fullNameInput').inputValue(),'Học viên kiểm thử cập nhật');}finally{await page.close();}});
 let notebook;
 await check('create notebook and reject empty notebook',async()=>{assert.equal((await api.post(base+'/api/notebooks',{data:{title:' '}})).status(),400);const r=await api.post(base+'/api/notebooks',{data:{title:'Notebook kiểm thử end-to-end'}});assert.equal(r.status(),200);notebook=await r.json();return notebook.id;});
 let doc;
 await check('DOCX upload and real AI/Qdrant indexing',async()=>{
  const paragraphs=[
   'Quang hợp là quá trình thực vật chuyển đổi năng lượng ánh sáng thành năng lượng hóa học. Lục lạp là bào quan thực hiện quang hợp. Chất diệp lục hấp thụ ánh sáng mặt trời.',
   'Pha sáng diễn ra ở màng tilacoit, tạo ATP và NADPH. Quá trình quang phân li nước giải phóng oxy. Oxy được thải ra môi trường và cung cấp cho hô hấp của sinh vật.',
   'Chu trình Calvin diễn ra ở chất nền lục lạp. Carbon dioxide được cố định và chuyển thành carbohydrate nhờ năng lượng từ ATP và NADPH. Enzyme Rubisco tham gia cố định carbon.',
   'Cường độ ánh sáng, nồng độ carbon dioxide và nhiệt độ ảnh hưởng đến tốc độ quang hợp. Khi thiếu nước, khí khổng đóng lại làm giảm khả năng hấp thụ carbon dioxide.',
   'Hô hấp tế bào phân giải glucose để tạo ATP. Ty thể là nơi diễn ra nhiều phản ứng hô hấp hiếu khí. Quang hợp và hô hấp góp phần duy trì chu trình carbon trong hệ sinh thái.'
  ];
  const bytes=await Packer.toBuffer(new Document({sections:[{children:paragraphs.map(t=>new Paragraph(t))}]}));
  const r=await api.post(base+'/api/documents/upload',{multipart:{notebookId:String(notebook.id),file:{name:'qa-sinh-hoc.docx',mimeType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',buffer:bytes}},timeout:30000});assert.equal(r.status(),200);doc=await r.json();
  const deadline=Date.now()+120000;
  while(Date.now()<deadline){const status=await api.get(base+`/api/documents/${doc.id}/status`);const value=await status.json();if(value.status==='FAILED')throw Error('Indexing FAILED: '+(value.errorMessage||''));if(value.status==='READY'){doc=await(await api.get(base+`/api/documents/${doc.id}`)).json();return {id:doc.id,status:doc.status,indexed:Boolean(doc.activeIndexVersion)};}await new Promise(r=>setTimeout(r,1000));}throw Error('Indexing timeout');
 });
 if(doc){
  await check('ownership blocks other users',async()=>{const outsider=await request.newContext();try{assert.equal((await outsider.get(base+`/api/documents/${doc.id}`)).status(),401);const name='qa_other_'+Date.now();await outsider.post(base+'/api/auth/register',{data:{name:'Other QA',username:name,email:name+'@example.invalid',password:'QaTest123!'}});await outsider.post(base+'/api/auth/login',{data:{usernameOrEmail:name,password:'QaTest123!'}});assert.ok([403,404].includes((await outsider.get(base+`/api/documents/${doc.id}`)).status()));assert.ok([403,404].includes((await outsider.delete(base+`/api/documents/${doc.id}`)).status()));assert.equal((await api.get(base+`/api/documents/${doc.id}`)).status(),200);}finally{await outsider.dispose();}});
  if(doc.status==='READY'){
   for(const feature of ['summary','mind-map'])await check('live AI '+feature,async()=>{const r=await api.post(base+`/api/documents/${doc.id}/${feature}/generate`,{data:{},timeout:120000});assert.equal(r.status(),200,feature+' HTTP '+r.status());const body=await r.json();assert.ok(body&&JSON.stringify(body).length>50);return {status:r.status()};});
   await check('live AI flashcard generation',async()=>{const r=await api.post(base+`/api/documents/${doc.id}/flashcards/generate`,{data:{quickStart:true},timeout:120000});assert.equal(r.status(),200);const body=await r.json();assert.ok(body.persistedCount>0||body.duplicateSkippedCount>0);return {persisted:body.persistedCount};});
   await check('live AI grounded chat',async()=>{const r=await api.post(base+'/api/ai/chat',{data:{message:'Pha sáng quang hợp tạo ra những sản phẩm nào?',docIds:[doc.id]},timeout:120000});assert.equal(r.status(),200);const body=await r.json();assert.ok(JSON.stringify(body).length>30);return {status:r.status()};});
   await check('live AI quiz and submit replay',async()=>{
    let r=await api.post(base+'/api/quizzes/generate',{data:{documentId:doc.id,scopeType:'DOCUMENT',mode:'PRACTICE',allowedQuestionTypes:['MCQ_SINGLE'],questionCount:3},timeout:120000});assert.equal(r.status(),200);const generated=await r.json();assert.ok(generated.quizId);r=await api.post(base+`/api/quizzes/${generated.quizId}/attempts`,{data:{}});assert.equal(r.status(),200);const attempt=await r.json();const submission={submissionToken:require('node:crypto').randomUUID(),answers:attempt.questions.map(q=>({questionOrder:q.questionOrder,selectedOptionOrders:[q.options[0].optionOrder],shortAnswerText:null}))};
    const first=await api.post(base+`/api/quiz-attempts/${attempt.attemptId}/submit`,{data:submission});assert.equal(first.status(),200);const result=await first.json();
    const replay=await api.post(base+`/api/quiz-attempts/${attempt.attemptId}/submit`,{data:submission});assert.equal(replay.status(),200);assert.deepEqual(await replay.json(),result);
    return {quizId:generated.quizId,attempt:attempt.attemptId};
   });
  }
 }
 if(doc && doc.status==='READY')await check('viewer renders summary, map, flashcards and quiz on mobile',async()=>{
  const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
  try{
   await page.setViewportSize({width:390,height:844});await page.goto(base+`/viewer.html?id=${doc.id}`,{waitUntil:'networkidle',timeout:60000});
   for(const [tab,selector] of [['summary','#summaryInlineBox .summary-section'],['map','#mapInlineBox svg'],['flashcard','#fcContentText'],['quiz','#quizInlineBox .quiz-option']]){
    await page.locator('#tabBtn-'+tab).click();await page.locator(selector).first().waitFor({state:'visible',timeout:20000});
    if(tab==='summary'){
     const foreground=await page.locator('#summaryInlineBox .summary-section h3').first().evaluate(e=>getComputedStyle(e).color);
     const background=await page.locator('#summaryInlineBox').evaluate(e=>getComputedStyle(e).backgroundColor);
     const luminance=color=>{const c=color.match(/\d+/g).slice(0,3).map(Number).map(v=>{v/=255;return v<=0.04045?v/12.92:((v+0.055)/1.055)**2.4;});return .2126*c[0]+.7152*c[1]+.0722*c[2];};
     const a=luminance(foreground),b=luminance(background);assert.ok((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5,'Summary heading contrast is too low');
    }
    if(tab==='map')assert.ok(await page.locator('.kg-map-controls button').evaluateAll(nodes=>nodes.every(e=>e.getBoundingClientRect().height<=80)));
    await page.evaluate(()=>scrollTo(0,0));

    await page.screenshot({path:path.join(out,'live-'+tab+'-390.png'),fullPage:true});
   }
   const size=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));assert.ok(size.scroll<=size.width+1,JSON.stringify(size));assert.deepEqual(errors,[]);
   return size;
  }finally{await page.close();}
 });
 await check('ordinary user cannot access admin API',async()=>assert.equal((await api.get(base+'/api/admin/users')).status(),403));
 await check('invalid webhook rejected and next request works',async()=>{assert.equal((await api.post(base+'/api/payments/webhook/verify',{data:{transactionId:1,status:'SUCCESS'},headers:{'X-Webhook-Signature':'invalid'}})).status(),401);assert.equal((await api.get(base+'/api/plans')).status(),200);});
 await check('payment approval, rejection, webhook and subscription cancellation',async()=>{
  assert.equal((await api.post(base+'/api/payments/checkout',{data:{planId:'UNKNOWN'}})).status(),400);
  let response=await api.post(base+'/api/payments/checkout',{data:{planId:'PRO'}});assert.equal(response.status(),200);const transaction=await response.json();
  assert.equal((await api.get(base+'/api/payments/pending')).status(),403);
  assert.equal((await api.post(base+`/api/payments/${transaction.transactionId}/approve`)).status(),403);
  const admin=await request.newContext();
  try{
   assert.equal((await admin.post(base+'/api/auth/login',{data:{usernameOrEmail:'admin',password:'Admin123!'}})).status(),200);
   assert.equal((await admin.get(base+'/api/payments/pending')).status(),200);
   assert.equal((await admin.post(base+`/api/payments/${transaction.transactionId}/approve`)).status(),200);
   assert.equal((await admin.post(base+`/api/payments/${transaction.transactionId}/approve`)).status(),400);
   assert.equal((await(await api.get(base+'/api/auth/me')).json()).plan,'PRO');
   response=await api.post(base+'/api/payments/checkout',{data:{planId:'VIP'}});const rejected=await response.json();
   assert.equal((await admin.post(base+`/api/payments/${rejected.transactionId}/reject`)).status(),200);
   response=await api.post(base+'/api/payments/checkout',{data:{planId:'PRO'}});const signed=await response.json();
   const callbacks=await Promise.all([1,2].map(()=>api.post(base+'/api/payments/webhook/verify',{data:{transactionId:signed.transactionId,status:'SUCCESS'},headers:{'X-Webhook-Signature':'qa-local-test-only'}})));
   assert.deepEqual(callbacks.map(r=>r.status()).sort(),[200,400]);
   assert.equal((await api.post(base+'/api/subscription/cancel-auto-renew')).status(),200);
   return {approved:transaction.transactionId,rejected:rejected.transactionId,concurrentWebhook:signed.transactionId};
  }finally{await admin.dispose();}
 });
 if(doc)await check('delete document preserves service availability',async()=>{
  assert.equal((await api.delete(base+`/api/documents/${doc.id}`)).status(),200);
  assert.equal((await api.get(base+`/api/documents/${doc.id}`)).status(),404);
  assert.equal((await api.get(base+'/api/notebooks')).status(),200);
 });
 await check('logout invalidates session',async()=>{assert.equal((await api.post(base+'/api/auth/logout')).status(),200);assert.equal((await api.get(base+'/api/notebooks')).status(),401);});
 fs.writeFileSync(path.join(out,'flow-results.json'),JSON.stringify(results,null,2));

};
