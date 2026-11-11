renderUnifiedSidebar('profile');
(function(){
 const query=new URLSearchParams(location.search);let code=(query.get('plan')||'PRO').toUpperCase();if(code==='PREMIUM')code='VIP';if(code!=='VIP')code='PRO';
 const months=document.getElementById('subscriptionMonths'),button=document.getElementById('submitSubscription');
 months.value=['1','3','12'].includes(query.get('months'))?query.get('months'):'1';let sequence=0,submitting=false;
 async function quote(){const seq=++sequence;button.disabled=true;
  try{const response=await fetch('/api/billing/quote?plan='+code+'&months='+months.value);if(!response.ok)throw Error();const value=await response.json();if(seq!==sequence)return;
   document.getElementById('planName').textContent=code;document.getElementById('planPrice').textContent=value.total.toLocaleString('vi-VN')+'đ / '+value.months+' tháng';
   document.getElementById('transferPrice').textContent=value.total.toLocaleString('vi-VN')+'đ';
   const hint=document.getElementById('planOriginalPrice');hint.classList.remove('hidden','line-through','text-slate-400');hint.classList.add('text-slate-600');hint.textContent=value.monthlyPrice.toLocaleString('vi-VN')+'đ/tháng';
   let user={};try{user=JSON.parse(localStorage.getItem('user')||'{}');}catch(_){}
   document.getElementById('transferSyntax').textContent='UPGRADE '+code+' '+months.value+'TH '+(user.username||'USER');button.disabled=false;
  }catch(_){if(seq===sequence)document.getElementById('planPrice').textContent='Chưa thể tải giá. Vui lòng thử lại.';}
 }
 months.addEventListener('change',quote);quote();
 fetch('/api/billing/payment-details').then(r=>r.ok?r.json():null).then(value=>{if(!value)return;document.getElementById('billingBankName').textContent=value.bankName||'Liên hệ quản trị viên';document.getElementById('billingBankAccount').textContent=value.account||'Chưa cấu hình';document.getElementById('billingBankHolder').textContent=value.holder||'Chưa cấu hình';if(value.configured)button.textContent='Xác nhận đã chuyển khoản';}).catch(()=>{});
 window.submitPayment=async function(){if(button.disabled||submitting)return;submitting=true;button.disabled=true;
  try{const response=await fetch('/api/payments/checkout',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({planId:code,months:months.value,paymentNote:document.getElementById('paymentNote').value})});
   const payment=await response.json();
   if(!response.ok)throw Error(payment.error);
   window.AccountAlerts.waitForPayment(payment);
  }catch(error){AppModal.alert(error.message || 'Chưa thể gửi yêu cầu. Vui lòng thử lại.');}finally{button.disabled=false;submitting=false;}
 };
 if(window.lucide)lucide.createIcons();
})();
