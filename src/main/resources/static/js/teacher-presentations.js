// State
let state = {
  decks: [], // Store server DTOs authoritatively
  targetArchiveDeck: null
};

// Initialize
document.addEventListener("DOMContentLoaded", () => {
  renderUnifiedSidebar("teacher-presentations");
  if (window.lucide) window.lucide.createIcons();
  
  verifyRoleAndLoad();
});

function verifyRoleAndLoad() {
  const user = JSON.parse(localStorage.getItem("user") || "{}");
  const role = (user.role || "STUDENT").toUpperCase();
  
  if (role !== "TEACHER" && role !== "ADMIN") {
    // Show permission screen
    document.getElementById("mainContent").innerHTML = `
      <div class="flex items-center justify-center h-full">
        <div class="text-center">
          <div class="w-16 h-16 rounded-full bg-rose-50 text-rose-500 border border-rose-100 flex items-center justify-center mx-auto mb-4">
            <i data-lucide="lock" class="h-8 w-8"></i>
          </div>
          <h2 class="text-xl font-extrabold text-slate-900 mb-2">Quyền truy cập bị từ chối</h2>
          <p class="text-slate-500 text-sm">Trang này dành riêng cho giảng viên và quản trị viên.</p>
        </div>
      </div>
    `;
    document.getElementById("mainContent").classList.remove("hidden");
    if (window.lucide) window.lucide.createIcons();
    return;
  }
  
  document.getElementById("mainContent").classList.remove("hidden");
  loadDecks();
}

async function loadDecks() {
  const loadingState = document.getElementById("loadingState");
  const emptyState = document.getElementById("emptyState");
  const deckList = document.getElementById("deckList");
  
  loadingState.classList.remove("hidden");
  emptyState.classList.add("hidden");
  deckList.classList.add("hidden");
  
  try {
    const pageData = await TeacherAPI.listPresentations(0, 50);
    state.decks = pageData.content || [];
    renderDecks();
  } catch (error) {
    loadingState.classList.add("hidden");
    UIErrors.handle(error);
  }
}

function renderDecks() {
  const loadingState = document.getElementById("loadingState");
  const emptyState = document.getElementById("emptyState");
  const deckList = document.getElementById("deckList");
  
  loadingState.classList.add("hidden");
  
  if (state.decks.length === 0) {
    emptyState.classList.remove("hidden");
    deckList.classList.add("hidden");
    return;
  }
  
  emptyState.classList.add("hidden");
  deckList.classList.remove("hidden");
  
  deckList.innerHTML = '';
  
  state.decks.forEach(deck => {
    const isArchived = deck.status === "ARCHIVED";
    const dateStr = new Date(deck.updatedAt).toLocaleDateString("vi-VN");
    
    // Create card element using DOM APIs to prevent XSS
    const card = document.createElement("div");
    card.className = "glass-3d-card border border-slate-200 rounded-3xl p-6 relative group flex flex-col h-full transition-all hover:shadow-lg";
    
    // Status Badge
    const badge = document.createElement("div");
    badge.className = `absolute top-6 right-6 text-[10px] font-extrabold uppercase px-2 py-1 rounded-md border ${
      isArchived ? "bg-slate-100 text-slate-500 border-slate-200" : "bg-indigo-50 text-indigo-600 border-indigo-100"
    }`;
    badge.textContent = deck.status;
    card.appendChild(badge);
    
    // Icon
    const iconWrapper = document.createElement("div");
    iconWrapper.className = `w-12 h-12 rounded-xl mb-4 flex items-center justify-center border shadow-sm ${
      isArchived ? "bg-slate-50 text-slate-400 border-slate-200" : "bg-indigo-50 text-indigo-500 border-indigo-200"
    }`;
    iconWrapper.innerHTML = `<i data-lucide="presentation" class="h-6 w-6"></i>`;
    card.appendChild(iconWrapper);
    
    // Title
    const title = document.createElement("h3");
    title.className = "text-lg font-extrabold text-slate-900 mb-1 line-clamp-1";
    title.textContent = deck.title;
    card.appendChild(title);
    
    // Desc
    const desc = document.createElement("p");
    desc.className = "text-slate-500 text-xs font-medium line-clamp-2 mb-4 flex-1";
    desc.textContent = deck.description || "Không có mô tả.";
    card.appendChild(desc);
    
    // Footer meta
    const footer = document.createElement("div");
    footer.className = "flex items-center justify-between mt-auto pt-4 border-t border-slate-100/50";
    
    const metaStr = document.createElement("span");
    metaStr.className = "text-[10px] text-slate-400 font-semibold uppercase tracking-wider";
    metaStr.textContent = `Cập nhật: ${dateStr} • v${deck.version}`;
    footer.appendChild(metaStr);
    
    // Actions
    const actionGroup = document.createElement("div");
    actionGroup.className = "flex gap-2";
    
    const btnOpen = document.createElement("a");
    btnOpen.href = `teacher-deck.html?deckId=${deck.id}`;
    btnOpen.className = "p-2 rounded-lg bg-indigo-50 text-indigo-600 hover:bg-indigo-100 transition-colors";
    btnOpen.title = "Mở bài giảng";
    btnOpen.innerHTML = `<i data-lucide="external-link" class="h-4 w-4"></i>`;
    actionGroup.appendChild(btnOpen);
    
    if (!isArchived) {
      const btnArchive = document.createElement("button");
      btnArchive.className = "p-2 rounded-lg bg-slate-50 text-slate-400 hover:text-rose-500 hover:bg-rose-50 transition-colors";
      btnArchive.title = "Lưu trữ";
      btnArchive.innerHTML = `<i data-lucide="archive" class="h-4 w-4"></i>`;
      btnArchive.onclick = () => openArchiveModal(deck);
      actionGroup.appendChild(btnArchive);
    }
    
    footer.appendChild(actionGroup);
    card.appendChild(footer);
    deckList.appendChild(card);
  });
  
  if (window.lucide) window.lucide.createIcons();
}

// Modals
async function openCreateModal() {
  document.getElementById("createModal").classList.remove("hidden");
  document.getElementById("deckTitle").focus();

  const select = document.getElementById("deckNotebook");
  select.innerHTML = '<option value="">-- Đang tải Sổ tay... --</option>';

  try {
    const notebooks = await ApiClient.get('/api/notebooks');
    select.innerHTML = '<option value="">-- Chọn Sổ Tay --</option>';
    if (notebooks && notebooks.length > 0) {
      notebooks.forEach(nb => {
        const opt = document.createElement("option");
        opt.value = nb.id;
        opt.textContent = nb.title || `Notebook #${nb.id}`;
        select.appendChild(opt);
      });
      select.selectedIndex = 1; // Default to first available notebook
    } else {
      select.innerHTML = '<option value="">Chưa có Sổ tay nào (Vui lòng tạo trước)</option>';
    }
  } catch (e) {
    select.innerHTML = '<option value="">Lỗi tải danh sách Sổ tay</option>';
  }
}

function closeCreateModal() {
  document.getElementById("createModal").classList.add("hidden");
  document.getElementById("createForm").reset();
}

function openArchiveModal(deck) {
  state.targetArchiveDeck = deck;
  document.getElementById("archiveModal").classList.remove("hidden");
}

function closeArchiveModal() {
  state.targetArchiveDeck = null;
  document.getElementById("archiveModal").classList.add("hidden");
}

// Actions
async function handleCreateDeck(event) {
  event.preventDefault();
  
  const title = document.getElementById("deckTitle").value;
  const description = document.getElementById("deckDesc").value;
  const notebookIdStr = document.getElementById("deckNotebook").value;
  const aspectRatio = document.getElementById("deckRatio").value;
  const themeKey = document.getElementById("deckTheme").value;
  
  const notebookId = notebookIdStr ? parseInt(notebookIdStr, 10) : null;
  
  const btn = document.getElementById("btnSubmitCreate");
  btn.disabled = true;
  btn.innerHTML = `<i data-lucide="loader-2" class="h-4 w-4 animate-spin"></i> Đang tạo...`;
  if (window.lucide) window.lucide.createIcons();
  
  try {
    const command = { notebookId, title, description, aspectRatio, themeKey };
    const newDeck = await TeacherAPI.createPresentation(command);
    
    // Add authoritatively to state
    state.decks.unshift(newDeck);
    renderDecks();
    
    closeCreateModal();
    UIErrors.showToast("Đã tạo bài giảng thành công.", "success");
  } catch (error) {
    UIErrors.handle(error);
  } finally {
    btn.disabled = false;
    btn.innerHTML = `Tạo bài giảng`;
  }
}

document.getElementById("btnConfirmArchive").addEventListener("click", async () => {
  if (!state.targetArchiveDeck) return;
  
  const deck = state.targetArchiveDeck;
  const btn = document.getElementById("btnConfirmArchive");
  btn.disabled = true;
  btn.innerHTML = `Đang xử lý...`;
  
  try {
    const updatedDeck = await TeacherAPI.archivePresentation(deck.id, deck.version);
    
    // Update authoritative state
    const index = state.decks.findIndex(d => d.id === deck.id);
    if (index !== -1) {
      state.decks[index] = updatedDeck;
    }
    renderDecks();
    closeArchiveModal();
    UIErrors.showToast("Đã lưu trữ bài giảng.", "success");
  } catch (error) {
    if (error.status === 409) {
      // Reload on conflict
      loadDecks();
      closeArchiveModal();
    }
    UIErrors.handle(error);
  } finally {
    btn.disabled = false;
    btn.innerHTML = `Xác nhận`;
  }
});
