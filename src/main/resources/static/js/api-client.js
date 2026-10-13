const ApiClient = (function() {
  function message(status) {
    if (status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
    if (status === 403) return 'Bạn không có quyền truy cập tài nguyên hoặc sử dụng chức năng này.';
    if (status === 404) return 'Không tìm thấy tài liệu hoặc dữ liệu bạn yêu cầu.';
    if (status === 409) return 'Dữ liệu đã thay đổi hoặc chưa sẵn sàng. Vui lòng tải lại rồi thử lại.';
    if (status === 429) return 'Đã đạt giới hạn yêu cầu. Vui lòng chờ rồi thử lại.';
    if (status === 413) return 'Dữ liệu vượt giới hạn cho phép. Vui lòng chọn tệp nhỏ hơn.';
    if (status >= 500) return 'Dịch vụ tạm thời không khả dụng. Vui lòng thử lại sau.';
    return 'Chưa thể hoàn tất yêu cầu. Vui lòng kiểm tra lựa chọn rồi thử lại.';
  }
  async function request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : endpoint;
    const fetchOptions = {
      ...options,
      headers: {
        ...options.headers
      }
    };
    
    // Set credentials
    fetchOptions.credentials = fetchOptions.credentials || 'same-origin';

    // Handle body serialization & headers:
    // If body is FormData, do NOT set Content-Type so browser sets boundary automatically
    if (fetchOptions.body) {
      if (fetchOptions.body instanceof FormData) {
        // Do not set Content-Type header
        if (fetchOptions.headers && fetchOptions.headers['Content-Type']) {
          delete fetchOptions.headers['Content-Type'];
        }
      } else if (typeof fetchOptions.body === 'object' && !(fetchOptions.body instanceof Blob)) {
        fetchOptions.headers['Content-Type'] = fetchOptions.headers['Content-Type'] || 'application/json';
        fetchOptions.body = JSON.stringify(fetchOptions.body);
      }
    }

    try {
      const response = await fetch(url, fetchOptions);

      // 401 handled by auth-interceptor or fallback redirect
      if (response.status === 401) {
        throw { status: 401, code: 'UNAUTHORIZED', message: 'Phiên đăng nhập hết hạn.' };
      }

      // Handle 204 No Content
      if (response.status === 204) {
        return null;
      }

      // Handle Blob / Binary downloads (e.g. PPTX export)
      if (options.responseType === 'blob') {
        if (!response.ok) {
          const errorText = await response.text().catch(() => null);
          let errorJson = null;
          try { errorJson = JSON.parse(errorText); } catch (e) {}
          throw {
            status: response.status,
            code: errorJson?.code || 'EXPORT_ERROR',
            message: message(response.status),
            details: null
          };
        }
        const blob = await response.blob();
        const contentDisposition = response.headers.get('content-disposition') || '';
        let filename = 'presentation.pptx';
        const filenameMatch = contentDisposition.match(/filename\*?=(?:UTF-8'')?["']?([^"';\n]+)["']?/i);
        if (filenameMatch && filenameMatch[1]) {
          filename = decodeURIComponent(filenameMatch[1]);
        }
        return { blob, filename };
      }

      const contentType = response.headers.get('content-type') || '';
      let data = null;
      
      if (contentType.includes('application/json')) {
        data = await response.json().catch(() => null);
      } else {
        data = await response.text().catch(() => null);
      }

      if (!response.ok) {
        throw { status: response.status,
          code: data?.error === 'Tài liệu chưa được xử lý để trò chuyện (thiếu Notebook).' ? 'DOCUMENT_NOTEBOOK_REQUIRED' : typeof data?.code === 'string' && /^[A-Z_]{1,80}$/.test(data.code) ? data.code : 'API_ERROR',
          message: message(response.status) };
      }

      return data;
    } catch (error) {
      if (error.status) throw error; // Already normalized
      throw {
        status: 503,
        code: 'NETWORK_ERROR',
        message: 'Lỗi kết nối đến máy chủ.',
        details: error
      };
    }
  }

  return {
    request, message,
    get: (url, headers = {}, options = {}) => request(url, { method: 'GET', headers, ...options }),
    post: (url, body, headers = {}, options = {}) => request(url, { method: 'POST', body, headers, ...options }),
    patch: (url, body, headers = {}, options = {}) => request(url, { method: 'PATCH', body, headers, ...options }),
    del: (url, body, headers = {}, options = {}) => request(url, { method: 'DELETE', body, headers, ...options })
  };
})();
