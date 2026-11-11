// Same-origin session handling; external libraries must not trigger logout.
(function () {
  const originalFetch = window.fetch.bind(window);
  let redirecting = false;
  window.fetch = async function (input, options = {}) {
    const url = new URL(input?.url || input, window.location.href);
    const sameOrigin = url.origin === window.location.origin;
    const request = { ...options };
    if (sameOrigin && !request.credentials) request.credentials = typeof input === 'string' ? 'same-origin' : input.credentials || 'same-origin';
    const method = (options.method || input?.method || 'GET').toUpperCase();
    const essential = sameOrigin && ((method === 'GET'
      && /^\/api\/(auth\/me|admin\/dashboard|progress\/dashboard|notebooks|documents)$/.test(url.pathname))
      || (method === 'POST' && url.pathname === '/api/auth/login'));
    function openMaintenance() {
      if (redirecting || window.location.pathname === '/errors.html') return;
      redirecting = true;
      const current = window.location.pathname + window.location.search;
      window.location.replace('/errors.html?retry=' + encodeURIComponent(current));
    }
    let response;
    try {
      response = await originalFetch(input, request);
    } catch (error) {
      if (essential && error.name !== 'AbortError') openMaintenance();
      throw error;
    }
    if (essential && response.status >= 500) openMaintenance();
    if (sameOrigin && response.status === 401 && !redirecting) {
      const path = window.location.pathname;
      if (!/\/(?:login|register|index)\.html$/.test(path) && path !== '/') {
        redirecting = true; localStorage.removeItem('user'); window.location.href = 'login.html';
      }
    }
    return response;
  };
})();
