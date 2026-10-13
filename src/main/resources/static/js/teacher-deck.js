// State
let state = {
  deckId: null,
  deck: null,
  outlines: [],
  slides: [],
  activeTab: 'outlines',
  previewCurrentIndex: 0
};

// Initialize
document.addEventListener("DOMContentLoaded", async () => {
  renderUnifiedSidebar("teacher-presentations");
  if (window.lucide) window.lucide.createIcons();

  const urlParams = new URLSearchParams(window.location.search);
  const deckId = urlParams.get("deckId");
  if (!deckId || isNaN(deckId)) {
    window.location.href = "teacher-presentations.html";
    return;
  }

  state.deckId = parseInt(deckId, 10);
  await loadDeck();
});

async function loadDeck() {
  try {
    state.deck = await TeacherAPI.getPresentation(state.deckId);
    renderDeckInfo();

    // Check if archived
    if (state.deck.status === "ARCHIVED") {
      document.getElementById("archivedBanner").classList.remove("hidden");
      disableMutationsForArchived();
    }

    document.getElementById("mainContent").classList.remove("hidden");

    // Initial tab load
    await loadOutlines();
    await loadSlides();
  } catch (err) {
    UIErrors.handle(err);
  }
}

function renderDeckInfo() {
  const d = state.deck;
  document.getElementById("deckTitle").textContent = d.title;
  document.getElementById("deckDescription").textContent = d.description || "Không có mô tả.";
  document.getElementById("deckStatusBadge").textContent = d.status;
  document.getElementById("deckRatio").textContent = d.aspectRatio || "16:9";
  document.getElementById("deckTheme").textContent = d.themeKey || "DEFAULT";
  document.getElementById("deckVersion").textContent = d.version;
  document.getElementById("deckUpdatedAt").textContent = new Date(d.updatedAt).toLocaleString("vi-VN");
}

function disableMutationsForArchived() {
  const mutationBtns = ["btnGenOutline", "btnGenSlides"];
  mutationBtns.forEach(id => {
    const btn = document.getElementById(id);
    if (btn) {
      btn.disabled = true;
      btn.classList.add("opacity-50", "cursor-not-allowed");
    }
  });
}

// Tab Switching
function switchTab(tabName) {
  state.activeTab = tabName;
  const tabOutlines = document.getElementById("tabContentOutlines");
  const tabSlides = document.getElementById("tabContentSlides");
  const btnOutlines = document.getElementById("tabBtnOutlines");
  const btnSlides = document.getElementById("tabBtnSlides");

  if (tabName === "outlines") {
    tabOutlines.classList.remove("hidden");
    tabSlides.classList.add("hidden");
    btnOutlines.className = "pb-3 border-b-2 border-indigo-600 text-indigo-600 font-extrabold text-sm flex items-center gap-2";
    btnSlides.className = "pb-3 border-b-2 border-transparent text-slate-500 hover:text-slate-800 font-extrabold text-sm flex items-center gap-2";
  } else {
    tabOutlines.classList.add("hidden");
    tabSlides.classList.remove("hidden");
    btnOutlines.className = "pb-3 border-b-2 border-transparent text-slate-500 hover:text-slate-800 font-extrabold text-sm flex items-center gap-2";
    btnSlides.className = "pb-3 border-b-2 border-indigo-600 text-indigo-600 font-extrabold text-sm flex items-center gap-2";
  }
}

// Outlines
async function loadOutlines() {
  const loading = document.getElementById("outlinesLoading");
  const empty = document.getElementById("outlinesEmpty");
  const list = document.getElementById("outlinesList");

  loading.classList.remove("hidden");
  empty.classList.add("hidden");
  list.innerHTML = "";

  try {
    state.outlines = await TeacherAPI.getOutlines(state.deckId);
    loading.classList.add("hidden");

    if (!state.outlines || state.outlines.length === 0) {
      empty.classList.remove("hidden");
      return;
    }

    renderOutlines();
  } catch (err) {
    loading.classList.add("hidden");
    UIErrors.handle(err);
  }
}

function renderOutlines() {
  const list = document.getElementById("outlinesList");
  list.innerHTML = "";

  const isArchived = state.deck.status === "ARCHIVED";

  state.outlines.forEach(rev => {
    const card = document.createElement("div");
    card.className = "glass-3d-card border border-slate-200 rounded-3xl p-6 relative";

    // Header
    const header = document.createElement("div");
    header.className = "flex items-center justify-between border-b border-slate-100 pb-4 mb-4";

    const titleGroup = document.createElement("div");
    titleGroup.className = "flex items-center gap-3";

    const badge = document.createElement("span");
    badge.className = `text-[10px] font-extrabold uppercase px-2 py-1 rounded-md border ${
      rev.status === "APPROVED" ? "bg-emerald-50 text-emerald-600 border-emerald-200" :
      rev.status === "SUPERSEDED" ? "bg-slate-100 text-slate-400 border-slate-200" :
      "bg-indigo-50 text-indigo-600 border-indigo-200"
    }`;
    badge.textContent = rev.status;

    const revNum = document.createElement("h4");
    revNum.className = "text-base font-extrabold text-slate-900";
    revNum.textContent = `Phiên bản Dàn ý #${rev.revisionNumber}`;

    titleGroup.appendChild(badge);
    titleGroup.appendChild(revNum);

    const metaGroup = document.createElement("div");
    metaGroup.className = "flex items-center gap-4 text-xs font-semibold text-slate-400";
    metaGroup.textContent = `Tạo lúc: ${new Date(rev.createdAt).toLocaleString("vi-VN")}`;

    if (rev.status !== "APPROVED" && !isArchived) {
      const btnApprove = document.createElement("button");
      btnApprove.className = "btn-3d btn-3d-secondary px-3 py-1.5 text-xs font-extrabold shadow-sm";
      btnApprove.textContent = "Phê duyệt (Approve)";
      btnApprove.onclick = () => handleApproveOutline(rev.id);
      metaGroup.appendChild(btnApprove);
    }

    header.appendChild(titleGroup);
    header.appendChild(metaGroup);
    card.appendChild(header);

    // Parse outlineJson safely
    let parsed = null;
    try {
      parsed = typeof rev.outlineJson === 'string' ? JSON.parse(rev.outlineJson) : rev.outlineJson;
    } catch (e) {}

    const contentDiv = document.createElement("div");
    contentDiv.className = "space-y-4 text-sm";

    if (!parsed) {
      const rawText = document.createElement("pre");
      rawText.className = "bg-slate-50 p-4 rounded-xl text-xs text-slate-600 overflow-x-auto";
      rawText.textContent = rev.outlineJson || "Dữ liệu dàn ý rỗng.";
      contentDiv.appendChild(rawText);
    } else {
      if (parsed.title) {
        const t = document.createElement("h5");
        t.className = "font-bold text-slate-800 text-base mb-1";
        t.textContent = parsed.title;
        contentDiv.appendChild(t);
      }

      if (parsed.sections && Array.isArray(parsed.sections)) {
        parsed.sections.forEach((sec, idx) => {
          const secBox = document.createElement("div");
          secBox.className = "bg-slate-50 border border-slate-200/80 rounded-2xl p-4";

          const secTitle = document.createElement("h6");
          secTitle.className = "font-extrabold text-slate-900 text-sm mb-1";
          secTitle.textContent = `${idx + 1}. ${sec.title || "Phần " + (idx + 1)}`;
          secBox.appendChild(secTitle);

          if (sec.purpose) {
            const secPurp = document.createElement("p");
            secPurp.className = "text-xs text-slate-500 mb-3 italic";
            secPurp.textContent = `Mục tiêu: ${sec.purpose}`;
            secBox.appendChild(secPurp);
          }

          if (sec.slides && Array.isArray(sec.slides)) {
            const slidesList = document.createElement("ul");
            slidesList.className = "space-y-2 pl-2";

            sec.slides.forEach((sl, sIdx) => {
              const li = document.createElement("li");
              li.className = "text-xs bg-white p-3 rounded-xl border border-slate-200/60";

              const st = document.createElement("p");
              st.className = "font-bold text-slate-800";
              st.textContent = `• Slide ${sIdx + 1}: ${sl.title || "Không tiêu đề"}`;
              li.appendChild(st);

              if (sl.purpose) {
                const sp = document.createElement("p");
                sp.className = "text-slate-500 mt-0.5";
                sp.textContent = sl.purpose;
                li.appendChild(sp);
              }

              if (sl.keyPoints && Array.isArray(sl.keyPoints)) {
                const kpList = document.createElement("div");
                kpList.className = "mt-1 flex flex-wrap gap-1";
                sl.keyPoints.forEach(kp => {
                  const tag = document.createElement("span");
                  tag.className = "bg-indigo-50 text-indigo-600 px-2 py-0.5 rounded text-[10px] font-semibold";
                  tag.textContent = kp;
                  kpList.appendChild(tag);
                });
                li.appendChild(kpList);
              }

              slidesList.appendChild(li);
            });
            secBox.appendChild(slidesList);
          }

          contentDiv.appendChild(secBox);
        });
      }
    }

    card.appendChild(contentDiv);
    list.appendChild(card);
  });
}

function openGenerateOutlineModal() {
  document.getElementById("genOutlineModal").classList.remove("hidden");
}

function closeGenerateOutlineModal() {
  document.getElementById("genOutlineModal").classList.add("hidden");
  document.getElementById("genOutlineForm").reset();
}

async function handleGenerateOutlineSubmit(event) {
  event.preventDefault();

  const topic = document.getElementById("outlineTopic").value;
  const teacherInstruction = document.getElementById("outlineInstruction").value;
  const targetSlideCount = parseInt(document.getElementById("outlineSlideCount").value, 10);
  const audience = document.getElementById("outlineAudience").value;
  const language = document.getElementById("outlineLanguage").value;

  closeGenerateOutlineModal();

  const command = { topic, teacherInstruction, targetSlideCount, audience, language, documentIds: null };
  const idempotencyKey = crypto.randomUUID();

  showJobProgress("Đang biên soạn Dàn ý AI...");

  try {
    const jobDto = await TeacherAPI.generateOutline(state.deckId, command, idempotencyKey);
    
    // Poll job until complete
    await JobPoller.pollJob(() => TeacherAPI.getJob(state.deckId, jobDto.jobId), {
      timeout: 600000,
      onProgress: updateJobProgress
    });

    hideJobProgress();
    UIErrors.showToast("Đã tạo Dàn ý AI thành công!", "success");
    await loadDeck();
  } catch (err) {
    hideJobProgress();
    UIErrors.handle(err);
  }
}

async function handleApproveOutline(revisionId) {
  if (!confirm("Bạn có chắc chắn muốn phê duyệt phiên bản Dàn ý này? Dàn ý này sẽ được dùng để tạo các trang Slide.")) return;

  try {
    await TeacherAPI.approveOutline(state.deckId, revisionId, state.deck.version);
    UIErrors.showToast("Đã phê duyệt dàn ý thành công.", "success");
    await loadDeck();
  } catch (err) {
    if (err.status === 409) {
      await loadDeck();
    }
    UIErrors.handle(err);
  }
}

// Slides
async function loadSlides() {
  const loading = document.getElementById("slidesLoading");
  const empty = document.getElementById("slidesEmpty");

  loading.classList.remove("hidden");
  empty.classList.add("hidden");

  try {
    state.slides = await TeacherAPI.listSlides(state.deckId);
    loading.classList.add("hidden");

    if (!state.slides || state.slides.length === 0) {
      empty.classList.remove("hidden");
      return;
    }

    renderSlides();
  } catch (err) {
    loading.classList.add("hidden");
    UIErrors.handle(err);
  }
}

function renderSlides() {
  const grid = document.getElementById("slidesGrid");
  grid.innerHTML = "";

  const isArchived = state.deck.status === "ARCHIVED";

  state.slides.forEach(slide => {
    const card = document.createElement("div");
    card.className = "glass-3d-card border border-slate-200 rounded-3xl p-5 flex flex-col justify-between hover:shadow-lg transition-all";

    // Header
    const top = document.createElement("div");
    top.className = "flex justify-between items-start mb-3";

    const orderBadge = document.createElement("span");
    orderBadge.className = "text-xs font-extrabold px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg";
    orderBadge.textContent = `Slide #${slide.slideOrder}`;

    const stateBadge = document.createElement("span");
    stateBadge.className = `text-[10px] font-extrabold uppercase px-2 py-0.5 rounded border ${
      slide.generationState === 'GENERATED' ? 'bg-emerald-50 text-emerald-600 border-emerald-200' :
      slide.generationState === 'EDITED' ? 'bg-indigo-50 text-indigo-600 border-indigo-200' :
      slide.generationState === 'FAILED' ? 'bg-rose-50 text-rose-600 border-rose-200' :
      'bg-slate-100 text-slate-500 border-slate-200'
    }`;
    stateBadge.textContent = slide.generationState || 'EMPTY';

    top.appendChild(orderBadge);
    top.appendChild(stateBadge);
    card.appendChild(top);

    // Title & info
    const title = document.createElement("h4");
    title.className = "font-extrabold text-slate-900 text-base mb-1 line-clamp-1";
    title.textContent = slide.title || "Chưa có tiêu đề";
    card.appendChild(title);

    const notes = document.createElement("p");
    notes.className = "text-xs text-slate-500 font-medium line-clamp-2 mb-4 flex-1";
    notes.textContent = slide.speakerNotes || "Không có ghi chú.";
    card.appendChild(notes);

    // Elements summary
    const elemCount = slide.elements ? slide.elements.length : 0;
    const elemMeta = document.createElement("p");
    elemMeta.className = "text-[11px] text-slate-400 font-semibold mb-4";
    elemMeta.textContent = `Thành phần: ${elemCount} phần tử • v${slide.version}`;
    card.appendChild(elemMeta);

    // Actions
    const actions = document.createElement("div");
    actions.className = "flex gap-2 border-t border-slate-100 pt-3";

    const btnEdit = document.createElement("a");
    btnEdit.href = `teacher-slide-editor.html?deckId=${state.deckId}&slideId=${slide.id}`;
    btnEdit.className = "flex-1 py-2 bg-indigo-50 hover:bg-indigo-600 hover:text-white text-indigo-600 font-extrabold text-xs rounded-xl transition-all text-center flex items-center justify-center gap-1.5";
    btnEdit.innerHTML = `<i data-lucide="edit-3" class="h-3.5 w-3.5"></i> ${isArchived ? 'Xem' : 'Chỉnh sửa'}`;
    actions.appendChild(btnEdit);

    if (slide.generationState === 'FAILED' && !isArchived) {
      const btnRetry = document.createElement("button");
      btnRetry.className = "py-2 px-3 bg-rose-50 hover:bg-rose-600 hover:text-white text-rose-600 font-extrabold text-xs rounded-xl transition-all flex items-center gap-1";
      btnRetry.title = "Thử lại sinh Slide";
      btnRetry.innerHTML = `<i data-lucide="refresh-cw" class="h-3.5 w-3.5"></i> Thử lại`;
      btnRetry.onclick = () => handleRetrySlide(slide);
      actions.appendChild(btnRetry);
    }

    card.appendChild(actions);
    grid.appendChild(card);
  });

  if (window.lucide) window.lucide.createIcons({ root: grid });
}

async function handleGenerateSlides() {
  if (!state.deck.approvedOutlineRevisionId) {
    UIErrors.showToast("Bạn cần phê duyệt (Approve) một Dàn ý trước khi tạo Slide.", "error");
    return;
  }

  showJobProgress("Đang tạo tất cả Slide tự động...");

  try {
    const jobDto = await TeacherAPI.generateSlides(state.deckId, crypto.randomUUID());
    
    await JobPoller.pollJob(() => TeacherAPI.getJob(state.deckId, jobDto.jobId), {
      timeout: 600000,
      onProgress: updateJobProgress
    });

    hideJobProgress();
    UIErrors.showToast("Đã tạo tất cả Slide thành công!", "success");
    await loadDeck();
  } catch (err) {
    hideJobProgress();
    UIErrors.handle(err);
    await loadDeck();
  }
}

async function handleRetrySlide(slide) {
  if (!state.deck.approvedOutlineRevisionId) {
    UIErrors.showToast("Không tìm thấy Dàn ý đã phê duyệt để thử lại.", "error");
    return;
  }

  // Find approved revision contentHash
  const approvedOutline = state.outlines.find(o => o.id === state.deck.approvedOutlineRevisionId);
  const contentHash = approvedOutline ? approvedOutline.contentHash : null;

  if (!contentHash) {
    UIErrors.showToast("Không tìm thấy hash dàn ý để thử lại.", "error");
    return;
  }

  const command = {
    idempotencyKey: crypto.randomUUID(),
    outlineRevisionId: state.deck.approvedOutlineRevisionId,
    outlineContentHash: contentHash
  };

  showJobProgress(`Đang sinh lại Slide #${slide.slideOrder}...`);

  try {
    const jobDto = await TeacherAPI.retrySlide(state.deckId, slide.id, command, command.idempotencyKey);
    
    await JobPoller.pollJob(() => TeacherAPI.getJob(state.deckId, jobDto.jobId), {
      timeout: 600000,
      onProgress: updateJobProgress
    });

    hideJobProgress();
    UIErrors.showToast(`Đã sinh lại Slide #${slide.slideOrder} thành công!`, "success");
    await loadDeck();
  } catch (err) {
    hideJobProgress();
    UIErrors.handle(err);
    await loadDeck();
  }
}

// Progress Bar Helper
function showJobProgress(title) {
  const container = document.getElementById("jobProgressContainer");
  document.getElementById("jobProgressTitle").textContent = title;
  document.getElementById("jobProgressText").textContent = "0 / 0";
  document.getElementById("jobProgressBar").style.width = "0%";
  document.getElementById("jobProgressStatus").textContent = "Trạng thái: QUEUED";
  container.classList.remove("hidden");
}

function updateJobProgress(job) {
  const current = job.progressCurrent || 0;
  const total = job.progressTotal || 1;
  const percent = Math.min(100, Math.round((current / total) * 100));

  document.getElementById("jobProgressText").textContent = `${current} / ${total}`;
  document.getElementById("jobProgressBar").style.width = `${percent}%`;
  document.getElementById("jobProgressStatus").textContent = `Trạng thái: ${job.status}`;
}

function hideJobProgress() {
  document.getElementById("jobProgressContainer").classList.add("hidden");
}

// Media Modal
function openMediaModal() {
  TeacherMediaModal.open(state.deckId);
}

// Preview Modal
function openPreviewModal() {
  if (!state.slides || state.slides.length === 0) {
    UIErrors.showToast("Chưa có slide nào để xem trước.", "error");
    return;
  }
  state.previewCurrentIndex = 0;
  renderPreviewSlide();
  document.getElementById("previewModal").classList.remove("hidden");
}

function closePreviewModal() {
  document.getElementById("previewModal").classList.add("hidden");
}

function prevPreviewSlide() {
  if (state.previewCurrentIndex > 0) {
    state.previewCurrentIndex--;
    renderPreviewSlide();
  }
}

function nextPreviewSlide() {
  if (state.previewCurrentIndex < state.slides.length - 1) {
    state.previewCurrentIndex++;
    renderPreviewSlide();
  }
}

function renderPreviewSlide() {
  const slide = state.slides[state.previewCurrentIndex];
  document.getElementById("previewPageIndicator").textContent = `Slide ${state.previewCurrentIndex + 1} / ${state.slides.length}`;
  document.getElementById("previewNotes").textContent = slide.speakerNotes || "Không có ghi chú người nói.";

  const canvas = document.getElementById("previewCanvas");
  canvas.innerHTML = "";

  // Render elements ordered by zIndex
  const elements = (slide.elements || []).slice().sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0));

  elements.forEach(elem => {
    const el = document.createElement("div");
    el.style.position = "absolute";
    el.style.left = `${(elem.x / 960) * 100}%`;
    el.style.top = `${(elem.y / 540) * 100}%`;
    el.style.width = `${(elem.width / 960) * 100}%`;
    el.style.height = `${(elem.height / 540) * 100}%`;
    if (elem.rotation) {
      el.style.transform = `rotate(${elem.rotation}deg)`;
    }
    el.style.zIndex = elem.zIndex || 1;

    if (elem.elementType === "TEXT") {
      const text = elem.payload ? elem.payload.text || "" : "";
      el.className = "flex items-center p-2 text-slate-800 font-semibold";
      if (elem.style) {
        if (elem.style.fontSizePt) el.style.fontSize = `${elem.style.fontSizePt}px`;
        if (elem.style.color) el.style.color = elem.style.color;
        if (elem.style.bold) el.style.fontWeight = "bold";
        if (elem.style.italic) el.style.fontStyle = "italic";
        if (elem.style.textAlign) el.style.textAlign = elem.style.textAlign.toLowerCase();
      }
      el.textContent = text;
    } else if (elem.elementType === "SHAPE") {
      el.className = "border border-indigo-400 bg-indigo-50/50 rounded-lg flex items-center justify-center text-xs font-bold text-indigo-700";
      if (elem.style) {
        if (elem.style.fillColor) el.style.backgroundColor = elem.style.fillColor;
        if (elem.style.strokeColor) el.style.borderColor = elem.style.strokeColor;
      }
      el.textContent = elem.payload ? elem.payload.text || elem.payload.primitive || "SHAPE" : "SHAPE";
    } else if (elem.elementType === "IMAGE") {
      if (elem.contentUrl) {
        const img = document.createElement("img");
        img.src = elem.contentUrl;
        img.className = "w-full h-full object-cover rounded-lg";
        el.appendChild(img);
      } else {
        el.className = "bg-slate-200 rounded-lg flex items-center justify-center text-xs font-bold text-slate-500";
        el.textContent = "IMAGE";
      }
    }

    canvas.appendChild(el);
  });
}

// PPTX Export
async function handleExportPptx() {
  const btn = document.getElementById("btnExportPptx");
  btn.disabled = true;
  UIErrors.showToast("Đang kết xuất và tải xuống file PPTX...", "info");

  try {
    const { blob, filename } = await TeacherAPI.exportPptx(state.deckId);
    
    // Create temporary object URL and trigger download
    const blobUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = blobUrl;
    link.download = filename || `presentation-${state.deckId}.pptx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(blobUrl);

    UIErrors.showToast("Tải xuống PPTX hoàn tất!", "success");
  } catch (err) {
    UIErrors.handle(err);
  } finally {
    btn.disabled = false;
  }
}
