// Global Fetch Wrapper - đảm bảo cookie session luôn được gửi kèm.
(function() {
  const originalFetch = window.fetch;
  window.fetch = async function(url, options = {}) {
    options = options || {};
    options.headers = options.headers || {};

    options.credentials = options.credentials || "same-origin";

    const response = await originalFetch(url, options);

    if (response.status === 401) {
      const currentPath = window.location.pathname;
      if (!currentPath.endsWith("login.html") && !currentPath.endsWith("register.html") && !currentPath.endsWith("index.html") && currentPath !== "/") {
        localStorage.removeItem("user");
        window.location.href = "login.html";
      }
    }

    return response;
  };
})();
