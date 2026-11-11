const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{chromium}=require('playwright');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const page=await browser.newPage({viewport:{width:390,height:844},reducedMotion:'reduce'});let sends=0,resets=0;
  await page.route('http://qa.local/**',async route=>{
   const request=route.request(),url=new URL(request.url());
   if(url.pathname.startsWith('/api/')){
    if(url.pathname==='/api/auth/forgot-password'){sends++;return route.fulfill({json:{message:'Nếu email đã đăng ký, mã OTP sẽ được gửi tới hộp thư.'}});}
    if(url.pathname==='/api/auth/reset-password'){resets++;const body=request.postDataJSON();return route.fulfill({status:body.otp==='123456'?200:400,json:body.otp==='123456'?{message:'Đã đổi mật khẩu. Vui lòng đăng nhập bằng mật khẩu mới.'}:{error:'Mã OTP không đúng, đã hết hạn hoặc đã dùng.'}});}
    return route.fulfill({json:url.pathname==='/api/plans'?[]:{}});
   }
   const file=path.resolve('src/main/resources/static','.'+url.pathname);if(!file.startsWith(path.resolve('src/main/resources/static')+path.sep)||!fs.existsSync(file))return route.fulfill({status:404,body:''});
   return route.fulfill({body:fs.readFileSync(file),contentType:file.endsWith('.html')?'text/html':file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'application/octet-stream'});
  });
  await page.goto('http://qa.local/index.html?modal=register',{waitUntil:'networkidle'});
  await page.locator('#regPasswordInput').fill('weak');await page.locator('#regConfirmPasswordInput').fill('different');assert.equal(await page.locator('#regPasswordInput').evaluate(e=>e.checkValidity()),false);assert.equal(await page.locator('#regConfirmPasswordInput').evaluate(e=>e.checkValidity()),false);
  await page.locator('#regPasswordInput').fill('GoodPassword123!');await page.locator('#regConfirmPasswordInput').fill('GoodPassword123!');assert.equal(await page.locator('#registerPasswordRules .fulfilled').count(),6);
  fs.mkdirSync('build/otp-ui-browser',{recursive:true});for(const width of [390,768,1440]){await page.setViewportSize({width,height:844});const b=await page.locator('.auth-modal-card').boundingBox();assert.ok(b.x>=0&&b.x+b.width<=width+1&&b.height<=844);await page.screenshot({path:`build/otp-ui-browser/register-${width}.png`});}
  console.log('PASS actual registration password checklist, mismatch and responsive layout');
  await page.locator('#tabLoginBtn').click();await page.locator('#forgotPasswordLink').click();await page.locator('#recoveryEmail').fill('qa@test.invalid');await page.locator('#recoverySubmit').click();await page.locator('#recoveryOtp').waitFor({state:'visible'});assert.equal(sends,1);assert.equal(await page.locator('#resendRecoveryOtp').isDisabled(),true);
  await page.locator('#recoveryOtp').fill('000000');await page.locator('#recoveryPassword').fill('NewPassword456!');await page.locator('#recoveryConfirm').fill('Mismatch123!');assert.equal(await page.locator('#recoveryConfirm').evaluate(e=>e.checkValidity()),false);
  await page.locator('#recoveryConfirm').fill('NewPassword456!');await page.locator('#recoverySubmit').click();await page.waitForFunction(()=>document.getElementById('recoveryStatus').textContent.includes('không đúng'));assert.equal(resets,1);
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:'build/otp-ui-browser/recovery-390.png'});
  console.log('PASS forgot-password form, OTP entry, cooldown and server error feedback');
  await page.locator('#recoveryOtp').fill('123456');await page.locator('#recoverySubmit').click();await page.getByRole('dialog',{name:'Thành công'}).getByRole('button',{name:'Đã hiểu'}).click();await page.locator('#modalLoginForm').waitFor({state:'visible'});assert.equal(await page.locator('#loginUsernameInput').inputValue(),'qa@test.invalid');assert.equal(resets,2);
  console.log('PASS reset success uses modal and returns to email login');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
