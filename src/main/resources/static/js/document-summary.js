const DocumentSummaryLoader = (() => {
  const requests = new Map();

  function failure(status, code) {
    let message = 'Chưa thể tạo tóm tắt. Vui lòng thử lại.';
    if (status === 401) message = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    else if (status === 403 || code === 'DOCUMENT_NOT_FOUND') message = 'Tài liệu không tồn tại hoặc bạn không có quyền truy cập.';
    else if (status === 404 && code === 'SUMMARY_ENDPOINT_NOT_FOUND') message = 'Không thể truy cập dịch vụ tóm tắt. Vui lòng thử lại sau.';
    else if (status === 404) message = 'Không thể đọc bản tóm tắt của tài liệu. Vui lòng tải lại trang và thử lại.';
    else if (code === 'DOCUMENT_PROCESSING') message = 'Tài liệu đang được xử lý, vui lòng chờ trong giây lát.';
    else if (code === 'DOCUMENT_FAILED') message = 'Không thể xử lý tài liệu. Vui lòng thử tải lại hoặc kiểm tra tệp.';
    else if (status === 409) message = 'Tài liệu chưa sẵn sàng hoặc đã thay đổi. Vui lòng tải lại trang rồi thử lại.';
    else if (status === 502) message = 'AI chưa tạo được bản tóm tắt. Vui lòng thử lại sau.';
    const error = new Error(message);
    error.status = status;
    error.code = code;
    return error;
  }

  async function readError(response) {
    const copy = response.clone ? response.clone() : response;
    try { return await response.json() || {}; } catch (_) {
      try {
        const text = await copy.text();
        return { routeUnavailable: /NoResourceFoundException|No static resource api\/documents\/\d+\/summary/.test(text) };
      } catch (_) { return {}; }
    }
  }

  function requireReady(docId, document) {
    if (!document || String(document.id) !== String(docId) || document.status !== 'READY'
        || typeof document.activeIndexVersion !== 'string' || !document.activeIndexVersion.trim()) {
      throw failure(409, document?.status === 'PROCESSING' ? 'DOCUMENT_PROCESSING'
        : document?.status === 'FAILED' ? 'DOCUMENT_FAILED' : 'DOCUMENT_NOT_READY');
    }
  }

  function load(docId, document) {
    try { requireReady(docId, document); } catch (error) { return Promise.reject(error); }
    const key = String(docId);
    const version = document.activeIndexVersion;
    const requestKey = `${key}:${version}`;
    const assertCurrent = () => {
      requireReady(docId, document);
      if (document.activeIndexVersion !== version) throw failure(409, 'STALE_INDEX');
    };
    if (requests.has(requestKey)) return requests.get(requestKey);
    const request = (async () => {
      let response = await fetch(`/api/documents/${encodeURIComponent(key)}/summary`, { credentials: 'same-origin' });
      let missing = response.status === 204;
      let data;
      if (response.status === 404) {
        const body = await readError(response);
        // Support the previous backend cache-miss response during upgrades.
        missing = body.code === 'SUMMARY_NOT_GENERATED' || body.error === 'No current Summary V2 cache';
        if (!missing) {
          const unavailable = body.routeUnavailable || /No static resource api\/documents\/\d+\/summary/.test(body.message || '');
          const code = body.code || (body.error === 'Tài liệu không tồn tại hoặc bạn không có quyền truy cập'
            ? 'DOCUMENT_NOT_FOUND' : unavailable ? 'SUMMARY_ENDPOINT_NOT_FOUND' : 'SUMMARY_NOT_FOUND');
          throw failure(404, code);
        }
      } else if (response.ok && !missing) {
        try { data = await response.json(); } catch (_) { throw failure(502, 'INVALID_SUMMARY_RESPONSE'); }
        missing = data && String(data.docId) === key && data.cached === false && data.summary === null;
      }
      if (missing) {
        assertCurrent();
        response = await fetch(`/api/documents/${encodeURIComponent(key)}/summary/generate`, {
          method: 'POST', credentials: 'same-origin'
        });
        data = undefined;
      }
      if (!response.ok) {
        const body = await readError(response);
        throw failure(response.status, body.code);
      }
      if (data === undefined) {
        try { data = await response.json(); } catch (_) { throw failure(502, 'INVALID_SUMMARY_RESPONSE'); }
      }
      if (!data || typeof data.summary !== 'string' || !data.summary.trim()) throw failure(502, 'INVALID_SUMMARY_RESPONSE');
      assertCurrent();
      if (data.indexVersion && data.indexVersion !== version) throw failure(409, 'STALE_INDEX');
      return data;
    })().finally(() => requests.delete(requestKey));
    requests.set(requestKey, request);
    return request;
  }

  return { load };
})();
