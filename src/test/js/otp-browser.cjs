const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{chromium}=require('playwright');
const base=process.env.OTP_QA_BASE_URL||'http://127.0.0.1:18084';
if(base!=='http://127.0.0.1:18084')throw Error('Use the dedicated OTP QA server');
(async()=>{
 const browser=await chromium.launch({channel:'chrome',headless:true});
 const context=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'});
 const page=await context.newPage();let nativeDialogs=0;page.on('dialog',async d=>{nativeDialogs++;await d.dismiss();});
 const out=path.resolve('build/otp-browser');fs.mkdirSync(out,{recursive:true});
 const username='otp_browser_'+Date.now(),email=username+'@test.invalid',oldPassword='OldPassword123!',newPassword='NewPassword456!';
 try{
  await page.goto(base+'/index.html?modal=register',{waitUntil:'networkidle'});
  await page.locator('#regPasswordInput').fill('weak');await page.locator('#regConfirmPasswordInput').fill('different');
  assert.equal(await page.locator('#regPasswordInput').evaluate(e=>e.checkValidity()),false);assert.equal(await page.locator('#regConfirmPasswordInput').evaluate(e=>e.checkValidity()),false);
  await page.locator('#regPasswordInput').fill(oldPassword);await page.locator('#regConfirmPasswordInput').fill(oldPassword);
  assert.equal(await page.locator('#registerPasswordRules .fulfilled').count(),6);
  for(const width of [390,768,1440]){await page.setViewportSize({width,height:844});const b=await page.locator('.auth-modal-card').boundingBox();assert.ok(b.x>=0&&b.x+b.width<=width+1&&b.height<=844);await page.screenshot({path:path.join(out,'register-'+width+'.png')});}
  console.log('PASS live password rules, confirmation and responsive registration');
  await page.locator('#regNameInput').fill('Kiểm thử khôi phục');await page.locator('#regUsernameInput').fill(username);await page.locator('#regEmailInput').fill(email);
  await page.locator('#modalRegisterForm button[type=submit]').click();await page.waitForURL('**/dashboard.html');
  assert.equal((await context.request.get(base+'/api/auth/me')).status(),200);console.log('PASS registration creates an authenticated FREE session');
  await context.request.post(base+'/api/auth/logout');await page.goto(base+'/index.html?modal=login',{waitUntil:'networkidle'});
  await page.locator('#forgotPasswordLink').click();await page.locator('#recoveryEmail').fill(email);await page.locator('#recoverySubmit').click();
  await page.locator('#recoveryOtp').waitFor({state:'visible'});assert.equal(await page.locator('#resendRecoveryOtp').isDisabled(),true);
  const mailbox=await(await context.request.get('http://127.0.0.1:18085/latest?email='+encodeURIComponent(email))).json();assert.match(mailbox.otp,/^[0-9]{6}$/);
  await page.locator('#recoveryOtp').fill(mailbox.otp==='000000'?'111111':'000000');await page.locator('#recoveryPassword').fill(newPassword);await page.locator('#recoveryConfirm').fill(newPassword);await page.locator('#recoverySubmit').click();
  await page.waitForFunction(()=>document.getElementById('recoveryStatus').textContent.includes('không đúng'));
  console.log('PASS SMTP delivery, resend cooldown and incorrect OTP feedback');
  await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'otp-390.png')});
  await page.locator('#recoveryOtp').fill(mailbox.otp);await page.locator('#recoverySubmit').click();
  await page.getByRole('dialog',{name:'Thành công'}).getByRole('button',{name:'Đã hiểu'}).click();await page.locator('#loginPasswordInput').waitFor({state:'visible'});
  assert.equal((await context.request.post(base+'/api/auth/login',{data:{usernameOrEmail:email,password:oldPassword}})).status(),401);
  assert.equal((await context.request.post(base+'/api/auth/reset-password',{data:{email,otp:mailbox.otp,password:'AnotherPass789!'}})).status(),400);
  await page.locator('#loginUsernameInput').fill(email.toUpperCase());await page.locator('#loginPasswordInput').fill(newPassword);await page.locator('#modalLoginForm button[type=submit]').click();await page.waitForURL('**/dashboard.html');
  assert.equal((await context.request.get(base+'/api/auth/me')).status(),200);assert.equal(nativeDialogs,0);
  console.log('PASS one-time reset, old password rejected and email login with new password');
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
