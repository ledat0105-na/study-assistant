/* Shared accessible notification modal. Messages are always rendered as plain text. */
(function () {
  'use strict';
  const queue = [];
  let active = false;
  const themes = {
    success: {title: 'Thành công', icon: '✓', color: '#047857', background: '#d1fae5'},
    error: {title: 'Có lỗi xảy ra', icon: '!', color: '#be123c', background: '#ffe4e6'},
    warning: {title: 'Cần lưu ý', icon: '!', color: '#a16207', background: '#fef3c7'},
    info: {title: 'Thông báo', icon: 'i', color: '#4f46e5', background: '#e0e7ff'}
  };
  function inferTone(message) {
    if (/không thành công|chưa thành công/i.test(message)) return 'error';
    if (/thành công|đã gửi|đã đổi trạng thái/i.test(message)) return 'success';
    if (/lỗi|thất bại|không thành công|không thể|chưa thể|từ chối|không có quyền/i.test(message)) return 'error';
    if (/vui lòng|giới hạn|hết lượt/i.test(message)) return 'warning';
    return 'info';
  }
  function next() {
    if (active || !queue.length) return;
    if (!document.body) { document.addEventListener('DOMContentLoaded', next, {once: true}); return; }
    active = true;
    const {message, options, resolve, confirming} = queue.shift();
    const tone = themes[options.tone] ? options.tone : inferTone(message);
    const theme = themes[tone];
    let autoClose;
    const previousFocus = document.activeElement;
    const dialog = document.createElement('dialog');
    dialog.className = 'app-notification';
    dialog.setAttribute('aria-labelledby', 'appNotificationTitle');
    dialog.setAttribute('aria-describedby', 'appNotificationMessage');
    dialog.innerHTML = '<div class="app-notification-icon" aria-hidden="true"></div><h2 id="appNotificationTitle"></h2><p id="appNotificationMessage"></p><button type="button">Đã hiểu</button>';
    dialog.style.setProperty('--notification-color', theme.color);
    dialog.style.setProperty('--notification-background', theme.background);
    dialog.querySelector('.app-notification-icon').textContent = theme.icon;
    dialog.querySelector('h2').textContent = options.title || theme.title;
    dialog.querySelector('p').textContent = message;
    const button = dialog.querySelector('button');
    button.textContent = options.buttonLabel || 'Đã hiểu';
    let initialFocus = button;
    if (confirming) {
      const actions = document.createElement('div');
      actions.className = 'app-notification-actions';
      const cancel = document.createElement('button');
      cancel.type = 'button';
      cancel.className = 'app-notification-cancel';
      cancel.textContent = options.cancelLabel || 'Hủy';
      cancel.addEventListener('click', () => dialog.close());
      button.replaceWith(actions);
      actions.append(cancel, button);
      initialFocus = cancel;
    } else {
      dialog.addEventListener('keydown', event => {
        if (event.key === 'Tab') { event.preventDefault(); button.focus(); }
      });
    }
    button.addEventListener('click', () => dialog.close('confirmed'));
    dialog.addEventListener('close', () => {
      clearTimeout(autoClose);
      dialog.remove();
      if (previousFocus && previousFocus.isConnected) previousFocus.focus({preventScroll: true});
      active = false;
      resolve(confirming ? dialog.returnValue === 'confirmed' : undefined);
      next();
    }, {once: true});
    document.body.appendChild(dialog);
    dialog.showModal();
    initialFocus.focus({preventScroll: true});
    if (!confirming && tone === 'success') autoClose = setTimeout(() => dialog.close(), 1000);
  }
  window.AppModal = Object.freeze({
    alert(message, options = {}) {
      return new Promise(resolve => {
        queue.push({message: String(message ?? ''), options, resolve});
        next();
      });
    },
    confirm(message, options = {}) {
      return new Promise(resolve => {
        queue.push({message: String(message ?? ''), options, resolve, confirming: true});
        next();
      });
    }
  });
  const style = document.createElement('style');
  style.textContent = `
    dialog.app-notification { box-sizing:border-box; width:420px; max-width:calc(100vw - 32px); max-height:calc(100dvh - 32px); overflow:auto; margin:auto; padding:30px; border:1px solid #e2e8f0; border-radius:24px; background:#fff; color:#0f172a; box-shadow:0 24px 80px #0f172a38; font-family:Inter,system-ui,-apple-system,"Segoe UI",sans-serif; text-align:center; }
    dialog.app-notification::backdrop { background:#0f172a80; backdrop-filter:blur(5px); }
    .app-notification-icon { display:grid; place-items:center; width:56px; height:56px; margin:0 auto 18px; border-radius:18px; background:var(--notification-background); color:var(--notification-color); font-size:28px; font-weight:750; }
    .app-notification h2 { margin:0 0 10px; font-size:22px; font-weight:750; line-height:1.35; }
    .app-notification p { margin:0 0 24px; font-size:15px; line-height:1.7; color:#475569; white-space:pre-wrap; overflow-wrap:anywhere; max-height:45dvh; overflow-y:auto; }
    .app-notification button { width:100%; min-height:46px; padding:12px 20px; border:0; border-radius:12px; background:#4f46e5; color:#fff; font:inherit; font-weight:650; cursor:pointer; }
    .app-notification button:hover { background:#4338ca; }
    .app-notification-actions { display:flex; gap:10px; }
    .app-notification button.app-notification-cancel { background:#e2e8f0; color:#0f172a; }
    .app-notification button.app-notification-cancel:hover { background:#cbd5e1; }
    .app-notification button:focus-visible { outline:3px solid #a5b4fc; outline-offset:3px; }
    @media (prefers-reduced-motion:no-preference) { dialog.app-notification[open] { animation:notification-enter .18s ease-out; } @keyframes notification-enter { from { opacity:0; transform:translateY(10px) scale(.98); } to { opacity:1; transform:none; } } }
  `;
  document.head.appendChild(style);
})();
