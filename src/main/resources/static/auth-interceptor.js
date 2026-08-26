// Global Fetch Wrapper to automatically attach session credential and X-User-Id header
(function() {
  const originalFetch = window.fetch;
  window.fetch = async function(url, options = {}) {
    options = options || {};
    options.headers = options.headers || {};

    // Get user from localStorage
    const userJson = localStorage.getItem("user");
    if (userJson) {
      try {
        const u = JSON.parse(userJson);
        if (u && u.id) {
          if (options.headers instanceof Headers) {
            options.headers.set("X-User-Id", u.id.toString());
          } else {
            options.headers["X-User-Id"] = u.id.toString();
          }
        }
      } catch (e) {}
    }

    // Ensure credentials (cookies/session) are sent
    options.credentials = options.credentials || "same-origin";

    const response = await originalFetch(url, options);

    // If unauthorized and not on login/register/index page, redirect to login
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
