(function (global) {
  const pending = new Map();
  const attempted = new Set();
  const messages = {
    DOCUMENT_NOT_READY: 'Tài liệu đang được xử lý, vui lòng chờ trong giây lát.',
    DOCUMENT_FAILED: 'Không thể xử lý tài liệu. Vui lòng thử tải lại hoặc kiểm tra tệp.',
    NO_ACTIVE_INDEX: 'Tài liệu chưa có chỉ mục hoạt động. Vui lòng xử lý lại tài liệu.',
    NO_ACTIVE_EVIDENCE: 'Chỉ mục hiện tại chưa có đoạn tài liệu làm bằng chứng.',
    STALE_INDEX: 'Tài liệu đã thay đổi. Vui lòng nạp lại sơ đồ.',
    GRAPH_TOO_LARGE: 'Sơ đồ vượt giới hạn hiển thị. Hãy dùng tài liệu nhỏ hơn.',
    AI_PROVIDER_ERROR: 'Dịch vụ AI tạm thời không khả dụng. Vui lòng thử lại sau.',
    AI_SCHEMA_ERROR: 'AI trả dữ liệu sơ đồ không hợp lệ. Vui lòng thử lại.',
    GRAPH_PERSISTENCE_ERROR: 'Không thể lưu sơ đồ. Cần kiểm tra cấu hình dữ liệu trên máy chủ.',
    NO_CONCEPTS: 'Chưa tìm thấy khái niệm có bằng chứng trong tài liệu này.',
    UNAUTHENTICATED: 'Vui lòng đăng nhập lại.',
    NOT_FOUND: 'Không tìm thấy tài liệu hoặc bạn không có quyền truy cập.',
    PARTIAL_GRAPH: 'Đã hiển thị các khái niệm có bằng chứng; một phần khái niệm hoặc quan hệ chưa tạo được.'
  };
  const fallbackCodes = { 401: 'UNAUTHENTICATED', 403: 'NOT_FOUND', 404: 'NOT_FOUND',
    409: 'DOCUMENT_NOT_READY', 413: 'GRAPH_TOO_LARGE', 422: 'AI_SCHEMA_ERROR',
    500: 'GRAPH_PERSISTENCE_ERROR', 502: 'AI_PROVIDER_ERROR', 503: 'AI_PROVIDER_ERROR' };
  function failure(code) {
    const error = new Error(messages[code] || 'Chưa thể tạo sơ đồ tư duy. Vui lòng thử lại sau.');
    error.code = code;
    return error;
  }
  async function check(response) {
    if (response.ok) return;
    let body;
    try { body = await response.json(); } catch (_) { /* never display raw server output */ }
    throw failure(body && Object.hasOwn(messages, body.code) ? body.code : fallbackCodes[response.status]);
  }
  function load(doc, retry = false) {
    const version = doc.activeIndexVersion;
    const key = `${doc.id}:${version}`;
    if (pending.has(key)) return pending.get(key);
    const request = (async () => {
      if (doc.status === 'FAILED') throw failure('DOCUMENT_FAILED');
      if (doc.status !== 'READY') throw failure('DOCUMENT_NOT_READY');
      if (typeof version !== 'string' || !version.trim()) throw failure('NO_ACTIVE_INDEX');
      const url = `/api/documents/${doc.id}/mind-map`;
      function assertVersion(graph) {
        if (doc.activeIndexVersion !== version || doc.status !== 'READY'
            || (graph && graph.activeIndexVersion && graph.activeIndexVersion !== version)) {
          throw failure('STALE_INDEX');
        }
      }
      async function read() {
        const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
        await check(response);
        const graph = await response.json();
        if (!Array.isArray(graph.nodes) || !Array.isArray(graph.edges)) throw failure('AI_SCHEMA_ERROR');
        assertVersion(graph);
        return graph;
      }
      let graph = await read();
      if (retry || (graph.nodes.length === 0 && !attempted.has(key))) {
        attempted.add(key);
        assertVersion(graph);
        const response = await fetch(`${url}/generate`, { method: 'POST', credentials: 'same-origin' });
        await check(response);
        const generated = await response.json();
        assertVersion(generated);
        graph = await read();
        if (generated.generationStatus) {
          graph.generationStatus = generated.generationStatus;
          graph.code = generated.code;
          graph.degradationCodes = generated.degradationCodes;
        }
      }
      return graph;
    })().finally(() => pending.delete(key));
    pending.set(key, request);
    return request;
  }
  function showStatus(container, graph) {
    if (graph.generationStatus !== 'PARTIAL_SUCCESS' && graph.code !== 'PARTIAL_GRAPH') return;
    const notice = global.document.createElement('p');
    notice.className = 'mind-map-partial-notice';
    notice.setAttribute('role', 'status');
    notice.textContent = messages.PARTIAL_GRAPH;
    container.appendChild(notice);
  }
  global.DocumentMindMap = { load, showStatus };
})(window);
