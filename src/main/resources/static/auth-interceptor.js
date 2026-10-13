// Same-origin session handling; external libraries must not trigger logout.
(function () {
  const originalFetch = window.fetch.bind(window);
  let redirecting = false;
  window.fetch = async function (input, options = {}) {
    const url = new URL(typeof input === 'string' ? input : input.url, window.location.href);
    const sameOrigin = url.origin === window.location.origin;
    const request = { ...options };
    if (sameOrigin && !request.credentials) request.credentials = typeof input === 'string' ? 'same-origin' : input.credentials || 'same-origin';
    const response = await originalFetch(input, request);
    if (sameOrigin && response.status === 401 && !redirecting) {
      const path = window.location.pathname;
      if (!/\/(?:login|register|index)\.html$/.test(path) && path !== '/') {
        redirecting = true; localStorage.removeItem('user'); window.location.href = 'login.html';
      }
    }
    return response;
  };
})();
