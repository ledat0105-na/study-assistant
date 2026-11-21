(function () {
 const originalFetch=window.fetch.bind(window);let showing=false;
 function quotaNotice(data){
  if(showing)return;showing=true;
  const box=document.createElement('div');box.setAttribute('role','dialog');box.setAttribute('aria-modal','true');
  box.className='billing-upgrade-dialog';
  const panel=document.createElement('div');panel.className='billing-upgrade-panel';
  const title=document.createElement('h2');title.textContent='Đã hết hạn mức sử dụng';
  const text=document.createElement('p');text.textContent=data.message||'Vui lòng nâng cấp gói để tiếp tục.';
  const link=document.createElement('a');const next=data.upgradePlan==='VIP'?'VIP':'PRO';link.href='payment.html?plan='+next;link.textContent='Nâng cấp '+next;link.className='btn-3d btn-3d-primary';
  const close=document.createElement('button');close.textContent='Đóng';close.className='btn-3d btn-3d-secondary';close.onclick=()=>{box.remove();showing=false;};
  panel.append(title,text,link,close);box.append(panel);document.body.append(box);link.focus();
 }
 window.fetch=async function(input,options){
  const response=await originalFetch(input,options);
  if(response.status===429){try{const data=await response.clone().json();if(data.code==='QUOTA_EXCEEDED')quotaNotice(data);}catch(_){} }
  return response;
 };
 const labels={UPLOAD:'Tài liệu',CHAT:'Chat AI',QUIZ:'Quiz',FLASHCARD:'Lượt tạo flashcard',SUMMARY:'Tóm tắt',MIND_MAP:'Bản đồ',OUTLINE:'Dàn ý bài giảng',SLIDES:'Lượt tạo slide'};
 async function refresh(){
  let user;try{user=JSON.parse(localStorage.getItem('user')||'null');}catch(_){return;}
  if(!user?.id || user.role==="ADMIN")return;
  try{
   const response=await fetch('/api/billing/status');if(!response.ok)return;const data=await response.json();
   user.plan=data.currentPlan;localStorage.setItem('user',JSON.stringify(user));
   document.querySelectorAll('[data-plan-badge]').forEach(el=>{el.textContent=data.currentPlan;el.className='plan-badge plan-badge-'+data.currentPlan.toLowerCase();});
   const choices = document.getElementById('profilePlanChoices');
   if (choices) {
    const pro = document.getElementById('profileProLink');
    const vip = document.getElementById('profileVipLink');
    document.getElementById('profileProChoice').hidden = data.currentPlan === 'VIP';
    pro.textContent = data.currentPlan === 'PRO' ? 'Gia hạn PRO' : 'Nâng cấp PRO';
    vip.textContent = data.currentPlan === 'VIP' ? 'Gia hạn VIP' : 'Nâng cấp VIP';
    choices.classList.remove('hidden');
   }
   const host=document.getElementById('billingQuota');if(!host)return;host.replaceChildren();
   const heading=document.createElement('h3');heading.textContent='Gói '+data.currentPlan;host.append(heading);
   const expiry=document.createElement('p');expiry.textContent=data.currentPlan==='FREE'?'Tài khoản FREE · Hạn mức làm mới hàng tháng':data.expiresAt?'Hết hạn: '+new Date(data.expiresAt).toLocaleString('vi-VN',{timeZone:'Asia/Ho_Chi_Minh'}):'Không có thông tin ngày hết hạn';host.append(expiry);
   const period=document.createElement('p');period.textContent='Hạn mức tháng · Làm mới ngày '+data.resetsAt;host.append(period);
   const grid=document.createElement('div');grid.className='billing-quota-grid';
   Object.entries(data.features||{}).forEach(([key,value])=>{const item=document.createElement('div');item.textContent=(labels[key]||key)+': '+value.used+' / '+(value.limit<0?'Không giới hạn':value.limit);grid.append(item);});host.append(grid);
  }catch(_){}
 }
 const style=document.createElement('style');style.textContent='.plan-badge{display:inline-flex;padding:3px 10px;border-radius:999px;font-size:11px;font-weight:800;white-space:nowrap}.plan-badge-free{background:#e2e8f0;color:#334155}.plan-badge-pro{background:#e0e7ff;color:#4338ca}.plan-badge-vip{background:#fef3c7;color:#92400e}.billing-upgrade-dialog{position:fixed;inset:0;background:#0f172a99;z-index:9999;display:grid;place-items:center;padding:16px}.billing-upgrade-panel{background:white;color:#0f172a;border-radius:20px;padding:24px;max-width:440px;display:flex;gap:16px;flex-wrap:wrap}.billing-upgrade-panel h2{font-size:20px;font-weight:bold;width:100%}.billing-quota-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin:16px 0}.billing-quota-grid>div{padding:12px;border-radius:12px;background:#eef2ff;color:#312e81}';document.head.append(style);
 window.BillingUI={refresh};window.addEventListener('DOMContentLoaded',refresh);
})();
