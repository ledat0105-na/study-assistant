// Standard Component for Unified 3D Pearl Glassmorphism Sidebar Navigation
function renderUnifiedSidebar(activeNavKey) {
  // Ensure 3D CSS stylesheet is loaded
  if (!document.getElementById("style-3d-link")) {
    const link = document.createElement("link");
    link.id = "style-3d-link";
    link.rel = "stylesheet";
    link.href = "css/style-3d.css";
    document.head.appendChild(link);
  }

  // Inject 3D Orbs background container if missing
  if (!document.querySelector(".orb-container")) {
    const orbContainer = document.createElement("div");
    orbContainer.className = "orb-container";
    orbContainer.innerHTML = `
      <div class="orb-3d orb-1"></div>
      <div class="orb-3d orb-2"></div>
      <div class="orb-3d orb-3"></div>
    `;
    document.body.prepend(orbContainer);
  }

  const user = JSON.parse(localStorage.getItem("user") || "{}");
  if (!user.id && !window.location.pathname.endsWith("login.html") && !window.location.pathname.endsWith("register.html") && !window.location.pathname.endsWith("index.html")) {
    window.location.href = "login.html";
    return;
  }

  const isAdmin = (user.role || "FREE").toUpperCase() === "ADMIN";
  const userAvatarName = user.name ? user.name.split(" ").map(n => n[0]).join("").substring(0, 2).toUpperCase() : (isAdmin ? "AD" : "AI");
  const fullName = user.name || user.username || (isAdmin ? "Quản Trị Viên" : "Học viên AI");
  const email = user.email || "admin@study.ai";

  let navItems = [];

  if (isAdmin) {
    // Mỗi chức năng Quản trị là 1 trang HTML riêng biệt
    navItems = [
      { key: "admin-overview", label: "Tổng quan Quản trị 3D", icon: "shield-check", href: "admin.html" },
      { key: "admin-pricing", label: "Quản lý Bảng giá & SALE", icon: "tags", href: "admin-pricing.html" },
      { key: "admin-payments", label: "Phê duyệt Thanh toán", icon: "credit-card", href: "admin-payments.html" },
      { key: "admin-users", label: "Quản lý Người dùng", icon: "users", href: "admin-users.html" },
      { key: "admin-docs", label: "Quản lý Tài liệu hệ thống", icon: "folder-open", href: "admin-documents.html" },
      { key: "admin-audit", label: "Nhật ký Hệ thống Audit", icon: "clipboard-list", href: "admin-audit.html" },
      { key: "profile", label: "Hồ sơ Quản trị viên", icon: "user", href: "profile.html" }
    ];
  } else {
    // Menu dành riêng cho HỌC VIÊN / NGƯỜI DÙNG
    navItems = [
      { key: "dashboard", label: "Bảng điều khiển", icon: "layout-dashboard", href: "dashboard.html" },
      { key: "documents", label: "Tài liệu học tập", icon: "file-text", href: "documents.html" },
      { key: "map", label: "Sơ đồ kiến thức 3D", icon: "network", href: "map.html" },
      { key: "chat", label: "Trợ lý Chat AI", icon: "sparkles", href: "viewer.html" },
      { key: "flashcards", label: "Thẻ ghi nhớ 3D", icon: "layers", href: "flashcards.html" },
      { key: "quiz", label: "Trắc nghiệm AI", icon: "help-circle", href: "quiz.html" },
      { key: "progress", label: "Tiến độ & Thống kê", icon: "bar-chart-3", href: "progress.html" },
      { key: "profile", label: "Hồ sơ cá nhân", icon: "user", href: "profile.html" }
    ];
  }

  const navHtml = navItems.map(item => {
    const isActive = item.key === activeNavKey;
    const activeClass = isActive 
      ? "bg-gradient-to-r from-indigo-500/15 via-purple-500/15 to-indigo-500/10 text-indigo-700 border-l-4 border-indigo-600 font-extrabold shadow-md translate-x-1" 
      : "text-slate-600 hover:text-slate-900 hover:bg-slate-100/80 hover:translate-x-1 font-semibold";
    
    return `
      <a href="${item.href}" class="flex items-center gap-3.5 px-3.5 py-3 rounded-2xl text-sm transition-all duration-300 ${activeClass}">
        <i data-lucide="${item.icon}" class="h-5 w-5 ${isActive ? 'text-indigo-600 animate-pulse' : 'text-slate-500'}"></i>
        <span>${item.label}</span>
      </a>
    `;
  }).join("");

  const sidebarContainer = document.querySelector("aside");
  if (sidebarContainer) {
    sidebarContainer.className = "w-64 bg-white/80 backdrop-blur-2xl border-r border-slate-200/80 text-slate-800 flex flex-col justify-between p-4 sticky top-0 h-screen z-30 shrink-0 shadow-xl relative overflow-hidden";
    sidebarContainer.innerHTML = `
      <div class="relative z-10">
        <a href="${isAdmin ? 'admin.html' : 'dashboard.html'}" class="flex items-center gap-3 px-2 py-4 mb-6 border-b border-slate-200/80 hover:opacity-90 transition-opacity">
          <div class="bg-gradient-to-tr from-indigo-600 via-purple-600 to-cyan-500 p-2.5 rounded-2xl text-white shadow-lg shadow-indigo-500/30 transform hover:scale-105 transition-all duration-300">
            <i data-lucide="sparkles" class="h-6 w-6"></i>
          </div>
          <div>
            <h1 class="font-extrabold text-lg tracking-tight bg-gradient-to-r from-indigo-700 via-purple-700 to-cyan-700 bg-clip-text text-transparent">Study AI</h1>
            <span class="text-[10px] uppercase font-extrabold ${isAdmin ? 'text-rose-600 bg-rose-50 border-rose-200' : 'text-indigo-600 bg-indigo-50 border-indigo-200'} px-2 py-0.5 rounded-full border">
              ${isAdmin ? 'ADMIN CONTROL' : '3D Aurora Hub'}
            </span>
          </div>
        </a>

        <nav class="space-y-1.5">
          ${navHtml}
        </nav>
      </div>

      <div class="border-t border-slate-200/80 pt-4 mt-auto relative z-10">
        <div class="flex items-center gap-3 px-3 py-3 mb-3 bg-slate-50/90 rounded-2xl border border-slate-200 shadow-inner backdrop-blur-md">
          <div class="w-10 h-10 rounded-xl bg-gradient-to-tr ${isAdmin ? 'from-rose-600 to-purple-600' : 'from-indigo-600 to-purple-600'} text-white flex items-center justify-center font-extrabold text-sm shadow-md ring-2 ring-white">
            ${userAvatarName}
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-xs font-bold text-slate-900 truncate">${fullName}</p>
            <p class="text-[10px] text-slate-500 truncate">${email}</p>
          </div>
        </div>
        <button onclick="handleLogout()" class="w-full flex items-center justify-center gap-2.5 px-3 py-2.5 rounded-2xl text-xs font-extrabold text-rose-600 hover:text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 transition-all duration-300 shadow-sm">
          <i data-lucide="log-out" class="h-4 w-4"></i>
          <span>Đăng xuất</span>
        </button>
      </div>
    `;
  }
}

function handleLogout() {
  fetch("/api/auth/logout", { method: "POST" }).finally(() => {
    localStorage.removeItem("user");
    window.location.href = "login.html";
  });
}
