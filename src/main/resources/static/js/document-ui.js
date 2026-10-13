(function (global) {
  const messages = {
    PROCESSING: 'Tài liệu đang được xử lý, vui lòng chờ trong giây lát.',
    FAILED: 'Không thể xử lý tài liệu. Vui lòng thử tải lại hoặc kiểm tra tệp.',
    MISSING: 'Tài liệu không tồn tại hoặc bạn không có quyền truy cập.',
    UNINDEXED: 'Tài liệu chưa sẵn sàng. Vui lòng chờ xử lý hoàn tất.',
    READY: 'Tài liệu đã sẵn sàng.',
    DOCUMENT_NOTEBOOK_REQUIRED: 'Hỏi đáp cần tài liệu thuộc một notebook. Vui lòng chọn tài liệu trong notebook hoặc gắn notebook khi tải lên.'
  };
  function state(doc) {
    if (!doc || ['DELETED', 'FORBIDDEN', 'MISSING'].includes(doc.status)) return 'MISSING';
    if (doc.status === 'FAILED') return 'FAILED';
    if (['UPLOADING', 'PROCESSING'].includes(doc.status)) return 'PROCESSING';
    return doc.status === 'READY' && typeof doc.activeIndexVersion === 'string' && doc.activeIndexVersion.trim()
      ? 'READY' : 'UNINDEXED';
  }
  function requireReady(doc) {
    const current = state(doc);
    if (current === 'READY') return;
    const error = new Error(messages[current]);
    error.code = current; error.status = current === 'MISSING' ? 404 : 409;
    throw error;
  }
  function assertCurrent(doc, version) {
    requireReady(doc);
    if (doc.activeIndexVersion !== version) throw Object.assign(new Error('Tài liệu đã thay đổi. Vui lòng tải lại.'), { status: 409 });
  }
  function notice(container, message, retry) {
    container.replaceChildren();
    const text = global.document.createElement('p');
    text.className = 'feature-notice'; text.setAttribute('role', 'status'); text.textContent = message;
    container.appendChild(text);
    if (retry) {
      const button = global.document.createElement('button');
      button.type = 'button'; button.className = 'btn-3d btn-3d-primary feature-retry'; button.textContent = 'Thử lại';
      button.addEventListener('click', async () => { button.disabled = true; try { await retry(); } finally { button.disabled = false; } });
      container.appendChild(button);
    }
  }
  function setBusy(container, busy) { container.setAttribute('aria-busy', String(!!busy)); }
  function errorMessage(error) {
    return error?.code && Object.hasOwn(messages, error.code) ? messages[error.code] : ApiClient.message(error?.status);
  }
  function escape(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  global.DocumentUI = { state, requireReady, assertCurrent, notice, setBusy, escape,
    ready: doc => state(doc) === 'READY', message: doc => messages[state(doc)], errorMessage };
})(window);
