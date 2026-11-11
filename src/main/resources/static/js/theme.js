(function () {
  const key = "study-assistant-theme";
  const system = matchMedia("(prefers-color-scheme: dark)");
  let preference;
  let toggle;

  function usePreference(value) {
    preference = value === "light" || value === "dark" ? value : undefined;
    applyTheme();
  }

  function applyTheme() {
    const theme = preference || (system.matches ? "dark" : "light");
    document.documentElement.dataset.theme = theme;
    if (!toggle) return;
    const dark = theme === "dark";
    toggle.textContent = dark ? "☾" : "☀";
    toggle.title = dark ? "Chuyển sang giao diện sáng" : "Chuyển sang giao diện tối";
    toggle.setAttribute("aria-label", toggle.title);
  }

  try { usePreference(localStorage.getItem(key)); }
  catch (_) { applyTheme(); }

  system.addEventListener("change", applyTheme);
  window.addEventListener("storage", event => {
    if (event.key === key || event.key === null) usePreference(event.newValue);
  });
  document.addEventListener("DOMContentLoaded", () => {
    toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "app-theme-control";
    toggle.addEventListener("click", () => {
      usePreference(document.documentElement.dataset.theme === "dark" ? "light" : "dark");
      try { localStorage.setItem(key, preference); } catch (_) {}
    });
    document.body.append(toggle);
    applyTheme();
  });
})();
