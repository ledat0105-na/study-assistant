const TeacherMediaModal = (function() {
  let currentDeckId = null;
  let onSelectCallback = null;
  let modalElement = null;

  function initModal() {
    if (document.getElementById('teacher-media-modal')) return;

    modalElement = document.createElement('div');
    modalElement.id = 'teacher-media-modal';
    modalElement.className = 'fixed inset-0 z-50 hidden bg-slate-900/50 backdrop-blur-sm flex items-center justify-center';
    modalElement.innerHTML = `
      <div class="bg-white rounded-3xl shadow-2xl border border-slate-200 w-full max-w-3xl mx-4 overflow-hidden flex flex-col max-h-[85vh]">
        <div class="p-6 border-b border-slate-100 flex justify-between items-center bg-slate-50">
          <div>
            <h3 class="text-lg font-extrabold text-slate-900">Thư viện phương tiện (Media Assets)</h3>
            <p class="text-xs text-slate-500 font-semibold mt-0.5">Tải lên hoặc chọn hình ảnh để chèn vào bài giảng</p>
          </div>
          <button onclick="TeacherMediaModal.close()" class="text-slate-400 hover:text-slate-600 focus:outline-none">
            <i data-lucide="x" class="h-5 w-5"></i>
          </button>
        </div>

        <div class="p-6 border-b border-slate-100 bg-slate-50/50 flex flex-wrap items-center justify-between gap-4">
          <label class="btn-3d btn-3d-primary px-4 py-2 text-xs font-extrabold cursor-pointer flex items-center gap-2">
            <i data-lucide="upload" class="h-4 w-4"></i>
            <span>Tải ảnh mới lên</span>
            <input type="file" id="mediaFileInput" accept="image/png,image/jpeg,image/webp" class="hidden" onchange="TeacherMediaModal.handleUpload(event)">
          </label>
          <span class="text-xs text-slate-400 font-medium">Định dạng hỗ trợ: PNG, JPEG, WebP</span>
        </div>

        <div class="p-6 overflow-y-auto flex-1">
          <div id="mediaLoading" class="text-center py-10 hidden">
            <i data-lucide="loader-2" class="h-8 w-8 animate-spin mx-auto text-indigo-600 mb-2"></i>
            <p class="text-xs text-slate-500 font-semibold">Đang tải danh sách phương tiện...</p>
          </div>
          <div id="mediaEmpty" class="text-center py-12 hidden">
            <i data-lucide="image-off" class="h-10 w-10 mx-auto text-slate-300 mb-2"></i>
            <p class="text-sm font-bold text-slate-600">Chưa có hình ảnh nào trong thư viện</p>
            <p class="text-xs text-slate-400 mt-1">Hãy nhấn Tải ảnh mới lên để thêm hình ảnh cho bài giảng.</p>
          </div>
          <div id="mediaGrid" class="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            <!-- Media items rendered here -->
          </div>
        </div>

        <div class="p-4 border-t border-slate-100 bg-slate-50 flex justify-end">
          <button onclick="TeacherMediaModal.close()" class="px-5 py-2 text-xs font-extrabold text-slate-600 hover:bg-slate-200/60 rounded-xl transition-all">Đóng</button>
        </div>
      </div>
    `;

    document.body.appendChild(modalElement);
    if (window.lucide) window.lucide.createIcons({ root: modalElement });
  }

  async function open(deckId, onSelect = null) {
    currentDeckId = deckId;
    onSelectCallback = onSelect;
    initModal();

    modalElement.classList.remove('hidden');
    await loadMediaList();
  }

  function close() {
    if (modalElement) {
      modalElement.classList.add('hidden');
    }
    currentDeckId = null;
    onSelectCallback = null;
  }

  async function loadMediaList() {
    const loading = document.getElementById('mediaLoading');
    const empty = document.getElementById('mediaEmpty');
    const grid = document.getElementById('mediaGrid');

    loading.classList.remove('hidden');
    empty.classList.add('hidden');
    grid.innerHTML = '';

    try {
      const mediaList = await TeacherAPI.listMedia(currentDeckId);
      loading.classList.add('hidden');

      if (!mediaList || mediaList.length === 0) {
        empty.classList.remove('hidden');
        return;
      }

      mediaList.forEach(item => {
        const card = document.createElement('div');
        card.className = 'group relative bg-white border border-slate-200 rounded-2xl p-2 shadow-sm hover:shadow-md transition-all flex flex-col justify-between';

        const imgContainer = document.createElement('div');
        imgContainer.className = 'w-full h-28 bg-slate-100 rounded-xl overflow-hidden mb-2 relative flex items-center justify-center';
        
        const img = document.createElement('img');
        img.src = TeacherAPI.getMediaContentUrl(currentDeckId, item.id);
        img.alt = item.originalFilename || 'Media asset';
        img.className = 'w-full h-full object-cover';
        imgContainer.appendChild(img);

        const filename = document.createElement('p');
        filename.className = 'text-[11px] font-bold text-slate-700 truncate px-1';
        filename.textContent = item.originalFilename;

        const meta = document.createElement('p');
        meta.className = 'text-[10px] text-slate-400 font-medium px-1 mb-2';
        meta.textContent = `${item.width || '?'}x${item.height || '?'} • ${Math.round((item.byteSize || 0) / 1024)} KB`;

        const btnGroup = document.createElement('div');
        btnGroup.className = 'flex gap-1.5 mt-auto';

        if (onSelectCallback) {
          const btnSelect = document.createElement('button');
          btnSelect.className = 'flex-1 py-1.5 bg-indigo-50 text-indigo-600 hover:bg-indigo-600 hover:text-white rounded-lg text-xs font-extrabold transition-all';
          btnSelect.textContent = 'Chọn ảnh';
          btnSelect.onclick = () => {
            onSelectCallback(item);
            close();
          };
          btnGroup.appendChild(btnSelect);
        }

        const btnDel = document.createElement('button');
        btnDel.className = 'p-1.5 bg-slate-50 text-slate-400 hover:bg-rose-50 hover:text-rose-600 rounded-lg transition-all';
        btnDel.title = 'Xóa file';
        btnDel.innerHTML = `<i data-lucide="trash-2" class="h-3.5 w-3.5"></i>`;
        btnDel.onclick = () => handleDelete(item.id);
        btnGroup.appendChild(btnDel);

        card.appendChild(imgContainer);
        card.appendChild(filename);
        card.appendChild(meta);
        card.appendChild(btnGroup);

        grid.appendChild(card);
      });

      if (window.lucide) window.lucide.createIcons({ root: grid });
    } catch (err) {
      loading.classList.add('hidden');
      UIErrors.handle(err);
    }
  }

  async function handleUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    // Validate type
    const validTypes = ['image/png', 'image/jpeg', 'image/webp'];
    if (!validTypes.includes(file.type)) {
      UIErrors.showToast('Vui lòng chọn định dạng ảnh PNG, JPEG hoặc WebP.', 'error');
      event.target.value = '';
      return;
    }

    try {
      UIErrors.showToast('Đang tải hình ảnh lên...', 'info');
      await TeacherAPI.uploadMedia(currentDeckId, file);
      UIErrors.showToast('Tải ảnh thành công!', 'success');
      event.target.value = '';
      await loadMediaList();
    } catch (err) {
      event.target.value = '';
      UIErrors.handle(err);
    }
  }

  async function handleDelete(mediaId) {
    if (!confirm('Bạn có chắc chắn muốn xóa hình ảnh này khỏi thư viện?')) return;

    try {
      await TeacherAPI.deleteMedia(currentDeckId, mediaId);
      UIErrors.showToast('Đã xóa hình ảnh.', 'success');
      await loadMediaList();
    } catch (err) {
      UIErrors.handle(err);
    }
  }

  return {
    open,
    close,
    handleUpload
  };
})();
