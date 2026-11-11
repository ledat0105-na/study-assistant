(function () {
  'use strict';
  const rules = [
    ['length', 'Ít nhất 8 ký tự', value => [...value].length >= 8],
    ['upper', 'Có chữ hoa (A–Z)', value => /\p{Lu}/u.test(value)],
    ['lower', 'Có chữ thường (a–z)', value => /\p{Ll}/u.test(value)],
    ['digit', 'Có chữ số (0–9)', value => /\p{Nd}/u.test(value)],
    ['special', 'Có ký tự đặc biệt (!@#...)', value => /[^\p{L}\p{N}\s]/u.test(value)]
  ];
  const message = 'Mật khẩu cần ít nhất 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt; tối đa 72 byte UTF-8.';
  window.PasswordRules = Object.freeze({message, valid: value => rules.every(rule => rule[2](value)) && new TextEncoder().encode(value).length <= 72});
  function bindRules(input, confirm, id) {
    const checklist = document.createElement('ul');checklist.id = id;checklist.className = 'password-checklist';
    for (const [key,label] of rules) { const item=document.createElement('li');item.dataset.rule=key;item.textContent='○ '+label;checklist.appendChild(item); }
    input.parentElement.after(checklist);
    function update() {
      rules.forEach(([key,label,test]) => {const ok=test(input.value);const item=checklist.querySelector('[data-rule="'+key+'"]');item.textContent=(ok?'✓ ':'○ ')+label;item.classList.toggle('fulfilled',ok);});
      input.setCustomValidity(input.value && !PasswordRules.valid(input.value)?message:'');
      confirm.setCustomValidity(confirm.value && input.value!==confirm.value?'Mật khẩu xác nhận không khớp.':'');
      confirm.setAttribute('aria-invalid',String(!!confirm.validationMessage && !!confirm.value));
    }
    input.addEventListener('input',update);confirm.addEventListener('input',update);
    input.form.addEventListener('reset', () => queueMicrotask(update));
    update();
    return update;
  }
  const register=document.getElementById('regPasswordInput');
  if (!register) return;
  bindRules(register,document.getElementById('regConfirmPasswordInput'),'registerPasswordRules');
  const form=document.getElementById('passwordRecoveryForm');
  const email=document.getElementById('recoveryEmail'),otp=document.getElementById('recoveryOtp');
  const password=document.getElementById('recoveryPassword'),confirm=document.getElementById('recoveryConfirm');
  const submit=document.getElementById('recoverySubmit'),resend=document.getElementById('resendRecoveryOtp');
  const status=document.getElementById('recoveryStatus'),fields=document.getElementById('recoveryVerifyFields');
  const updateRules=bindRules(password,confirm,'recoveryPasswordRules');
  let verifying=false,busy=false,nextSendAt=0, expiresAt=0;
  const expiry=document.getElementById('recoveryExpiry');
  function renderResend() {
    const seconds=Math.max(0,Math.ceil((nextSendAt-Date.now())/1000));
    const remaining=Math.max(0,Math.ceil((expiresAt-Date.now())/1000));
    expiry.textContent=verifying && busy && !expiresAt ? '' : verifying?(remaining?'OTP còn hiệu lực '+Math.floor(remaining/60)+':'+String(remaining%60).padStart(2,'0'):'OTP đã hết hạn. Vui lòng gửi lại mã.'):'';
    submit.disabled=busy || (verifying && !remaining);
    document.getElementById('changeRecoveryEmail').disabled=busy;
    resend.disabled=busy || seconds>0;resend.textContent=seconds?'Gửi lại sau '+seconds+' giây':'Gửi lại OTP';
  }
  setInterval(renderResend,1000);
  function stage(verify) {
    if(!verify){
      expiresAt=0;
      togglePasswordVisibility('recoveryPassword','recoveryPasswordEye',false);
      togglePasswordVisibility('recoveryConfirm','recoveryConfirmEye',false);
    }
    verifying=verify;fields.classList.toggle('hidden',!verify);email.readOnly=verify;
    [otp,password,confirm].forEach(input=>input.disabled=!verify);
    submit.textContent=verify?'Đổi mật khẩu':'Gửi mã OTP';
  }
  const originalSwitch=window.switchAuthTab;
  window.switchAuthTab=function(tab){form.classList.add('hidden');originalSwitch(tab);};
  document.getElementById('forgotPasswordLink').addEventListener('click',()=>{
    document.getElementById('modalLoginForm').classList.add('hidden');document.getElementById('modalRegisterForm').classList.add('hidden');
    document.getElementById('authAlert').classList.add('hidden');form.classList.remove('hidden');email.focus();
  });
  document.getElementById('recoveryBack').addEventListener('click',()=>switchAuthTab('login'));
  document.getElementById('changeRecoveryEmail').addEventListener('click',()=>{if(busy)return;stage(false);otp.value='';password.value='';confirm.value='';updateRules();status.textContent='Nhập email để nhận mã mới.';email.focus();});
  async function post(url,data) {
    const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});
    let value={};try{value=await response.json();}catch(_){}
    if(!response.ok)throw Error(value.error || value.detail || value.message || 'Chưa thể xử lý yêu cầu. Vui lòng thử lại sau.');
    return value;
  }
  async function send() {
    const initial = !verifying;
    if (initial) stage(true);
    submit.textContent = 'Đang gửi mã OTP…';
    status.textContent = 'Đang gửi mã OTP. Vui lòng chờ trong giây lát.';
    renderResend();
    try {
      const value = await post('/api/auth/forgot-password', {email: email.value.trim()});
      const now = Date.now();
      nextSendAt = now + value.retryAfterSeconds * 1000;
      expiresAt = now + value.expiresInSeconds * 1000;
      otp.value = '';
      status.textContent = value.message;
      if (!form.classList.contains('hidden')) otp.focus();
    } catch (error) {
      if (initial) stage(false);
      status.textContent = error.message;
    } finally {
      submit.textContent = verifying ? 'Đổi mật khẩu' : 'Gửi mã OTP';
    }
  }
  resend.addEventListener('click',async()=>{
    if(busy || Date.now()<nextSendAt)return;busy=true;submit.disabled=true;renderResend();
    try{await send();}finally{busy=false;renderResend();}
  });
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(busy || (verifying && Date.now()>=expiresAt) || !form.reportValidity())return;
    busy=true;submit.disabled=true;renderResend();
    try {
      if(!verifying){await send();return;}
      const savedEmail=email.value;
      const value=await post('/api/auth/reset-password',{email:email.value.trim(),otp:otp.value,password:password.value});
      form.reset();stage(false);updateRules();localStorage.removeItem('user');
      await AppModal.alert(value.message,{tone:'success'});switchAuthTab('login');
      document.getElementById('loginPasswordInput').value='';document.getElementById('loginUsernameInput').value=savedEmail;
    }catch(error){
      if (verifying) await AppModal.alert(error.message, {tone:'error', title:'Chưa thể đổi mật khẩu'});
      else status.textContent=error.message;
    }
    finally{busy=false;renderResend();}
  });
  const style=document.createElement('style');style.textContent=`
    .auth-modal-card{max-height:calc(100dvh - 32px);overflow-y:auto;overscroll-behavior:contain}
    .password-checklist{display:grid;grid-template-columns:1fr 1fr;gap:5px 12px;margin:10px 0 0;padding:0;list-style:none;font-size:11px;line-height:1.5;color:#64748b}
    .password-checklist .fulfilled{color:#047857;font-weight:650}
    #recoveryEmail[readonly]{background:#f1f5f9;color:#475569}
    @media(max-width:420px){.auth-modal-card{padding:24px 20px}.password-checklist{grid-template-columns:1fr}}
  `;document.head.appendChild(style);
})();
