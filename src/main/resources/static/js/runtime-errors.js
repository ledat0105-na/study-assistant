// Report locations only: never transmit error text, passwords, API keys or query strings.
(function () {
  let count = 0, windowStart = Date.now();
  function report(kind, event) {
    if (Date.now() - windowStart >= 60000) { count = 0; windowStart = Date.now(); }
    if (count >= 5) return;
    count++;
    let source = '';
    try {
      const url = new URL(event.filename || '', location.href);
      if (url.origin === location.origin) source = url.pathname.slice(0, 200);
    } catch (_) {}
    const payload = {kind, source, line: Number(event.lineno) || 0, column: Number(event.colno) || 0};
    try {
      // A failed report must never produce another unhandled rejection.
      void fetch('/api/client-errors', {
        method: 'POST', credentials: 'same-origin', keepalive: true,
        headers: {'Content-Type': 'application/json'}, body: JSON.stringify(payload)
      }).catch(function () {});
    } catch (_) {}
  }
  window.addEventListener('error', function (event) { report('SCRIPT_ERROR', event); });
  window.addEventListener('unhandledrejection', function () { report('UNHANDLED_REJECTION', {}); });
})();
