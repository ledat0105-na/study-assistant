const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const path=require('node:path');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({reducedMotion:'reduce'});let nativeAlerts=0;page.on('dialog',async d=>{nativeAlerts++;await d.dismiss();});
  await page.setContent('<!doctype html><html lang="vi"><head><meta charset="UTF-8"></head><body><button id="trigger">Mở thông báo</button></body></html>');
  await page.addScriptTag({path:path.resolve('src/main/resources/static/js/app-modal.js')});
  await page.locator('#trigger').focus();
  await page.evaluate(()=>{window.finished=false;AppModal.alert('Lưu thông tin thành công!').then(()=>window.finished=true);});
  const dialog=page.getByRole('dialog',{name:'Thành công'});await dialog.waitFor();assert.equal(await page.evaluate(()=>finished),false);
  await page.keyboard.press('Tab');assert.equal(await page.evaluate(()=>document.activeElement.textContent),'Đã hiểu');
  await page.keyboard.press('Enter');await page.waitForFunction(()=>finished);assert.equal(await page.evaluate(()=>document.activeElement.id),'trigger');
  console.log('PASS waits for acknowledgement, traps focus and restores focus');
  await page.evaluate(()=>{window.order=[];AppModal.alert('Lỗi kết nối máy chủ.').then(()=>order.push(1));AppModal.alert('<img src=x onerror="window.injected=true">').then(()=>order.push(2));});
  await page.getByRole('dialog',{name:'Có lỗi xảy ra'}).waitFor();assert.equal(await page.locator('dialog').count(),1);await page.keyboard.press('Escape');
  await page.getByRole('dialog',{name:'Thông báo'}).waitFor();assert.match(await page.locator('dialog p').textContent(),/<img/);assert.equal(await page.locator('dialog img').count(),0);
  await page.getByRole('button',{name:'Đã hiểu'}).click();await page.waitForFunction(()=>order.length===2);assert.deepEqual(await page.evaluate(()=>order),[1,2]);
  console.log('PASS queued messages, Escape and safe plain-text rendering');
  for(const width of [390,768,1440]){
   await page.setViewportSize({width,height:844});await page.evaluate(()=>AppModal.alert('Vui lòng kiểm tra thông tin. '+ 'Nội dung thông báo dài '.repeat(60)) && undefined);
   await page.getByRole('dialog',{name:'Cần lưu ý'}).waitFor();const box=await page.locator('dialog').boundingBox();assert.ok(box.x>=0&&box.x+box.width<=width&&box.height<=844);
   await page.screenshot({path:`build/modal-${width}.png`});await page.keyboard.press('Escape');
  }
  assert.equal(nativeAlerts,0);console.log('PASS mobile/tablet/desktop layout, long content, no native alerts');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
