(function () {
  renderUnifiedSidebar('profile');
  if (window.lucide) window.lucide.createIcons();
  const form = document.getElementById('profileForm');
  const status = document.getElementById('profileStatus');
  function display(user) {
    document.getElementById('profileNameLabel').textContent = user.name || user.username || '';
    document.getElementById('profileEmailLabel').textContent = user.email || '';
    document.getElementById('fullNameInput').value = user.name || '';
    document.getElementById('emailInput').value = user.email || '';
    document.getElementById('tierLabel').textContent = 'GÓI ' + (user.plan || 'FREE');
    localStorage.setItem('user', JSON.stringify(user));
  }
  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true; status.textContent = '';
    try {
      const response = await fetch('/api/auth/profile', {method:'PATCH', headers:{'Content-Type':'application/json'},
        body:JSON.stringify({name:document.getElementById('fullNameInput').value.trim(), email:document.getElementById('emailInput').value.trim()})});
      if (!response.ok) {
        status.textContent = response.status === 400 || response.status === 409
          ? 'Thông tin không hợp lệ hoặc email đã được sử dụng.' : 'Chưa thể lưu thay đổi. Vui lòng thử lại.';
        return;
      }
      display(await response.json()); status.textContent = 'Đã lưu thông tin thành công.';
    } catch (_) { status.textContent = 'Không thể kết nối đến máy chủ.'; }
    finally { button.disabled = false; }
  });
  fetch('/api/auth/me').then(async response => {
    if (response.ok) display(await response.json());
    else if (response.status !== 401) status.textContent = 'Chưa thể tải thông tin cá nhân.';
  }).catch(function () { status.textContent = 'Không thể kết nối đến máy chủ.'; });
})();
