const UIErrors = (function() {
  function createToastContainer() {
    let container = document.getElementById('ui-toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'ui-toast-container';
      container.className = 'fixed bottom-4 right-4 z-50 flex flex-col gap-2';
      document.body.appendChild(container);
    }
    return container;
  }

  function showToast(message, type = 'error') {
    const container = createToastContainer();
    const toast = document.createElement('div');
    toast.className = `p-4 rounded-xl shadow-lg border text-sm font-semibold max-w-sm transition-all transform translate-y-full opacity-0 flex items-start gap-3 ${
      type === 'error' ? 'bg-red-50 text-red-700 border-red-200' : 
      type === 'success' ? 'bg-emerald-50 text-emerald-700 border-emerald-200' :
      'bg-slate-50 text-slate-700 border-slate-200'
    }`;

    const icon = document.createElement('i');
    icon.setAttribute('data-lucide', type === 'error' ? 'alert-circle' : type === 'success' ? 'check-circle' : 'info');
    icon.className = 'w-5 h-5 shrink-0 mt-0.5';
    toast.appendChild(icon);

    const textDiv = document.createElement('div');
    textDiv.textContent = message;
    toast.appendChild(textDiv);

    container.appendChild(toast);
    if (window.lucide) window.lucide.createIcons({ root: toast });

    // Animate in
    requestAnimationFrame(() => {
      toast.classList.remove('translate-y-full', 'opacity-0');
    });

    // Remove after 5s
    setTimeout(() => {
      toast.classList.add('opacity-0');
      setTimeout(() => toast.remove(), 300);
    }, 5000);
  }

  function handle(error) {
    let msg = 'Đã xảy ra lỗi.';
    if (error.status === 401) {
      // Auth interceptor redirects
      return;
    } else if (error.status === 403) {
      msg = 'Bạn không có quyền sử dụng chức năng này.';
    } else if (error.status === 404) {
      msg = 'Không tìm thấy tài nguyên hoặc bạn không có quyền truy cập.';
    } else if (error.status === 409) {
      msg = ApiClient.message(409);
    } else {
      msg = ApiClient.message(error.status);
    }
    showToast(msg, 'error');
  }

  return {
    handle,
    showToast
  };
})();
