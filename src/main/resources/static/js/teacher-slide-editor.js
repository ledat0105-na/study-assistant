// State
let state = {
  deckId: null,
  slideId: null,
  deck: null,
  slide: null,
  selectedElementId: null,
  isArchived: false
};

// Initialize
document.addEventListener("DOMContentLoaded", async () => {
  if (window.lucide) window.lucide.createIcons();

  const urlParams = new URLSearchParams(window.location.search);
  const deckId = urlParams.get("deckId");
  const slideId = urlParams.get("slideId");

  if (!deckId || !slideId || isNaN(deckId) || isNaN(slideId)) {
    window.location.href = "teacher-presentations.html";
    return;
  }

  state.deckId = parseInt(deckId, 10);
  state.slideId = parseInt(slideId, 10);

  document.getElementById("btnBackToDeck").href = `teacher-deck.html?deckId=${state.deckId}`;

  await loadSlideData();
});

async function loadSlideData() {
  try {
    state.deck = await TeacherAPI.getPresentation(state.deckId);
    state.slide = await TeacherAPI.getSlide(state.deckId, state.slideId);
    state.isArchived = state.deck.status === "ARCHIVED";

    if (state.isArchived) {
      document.getElementById("editorArchivedNotice").classList.remove("hidden");
      disableEditorMutations();
    }

    renderEditorHeader();
    renderCanvas();
    populateSlideMetaForm();

    if (state.selectedElementId) {
      const selected = (state.slide.elements || []).find(e => e.id === state.selectedElementId);
      if (selected) {
        selectElement(selected);
      } else {
        deselectElement();
      }
    }
  } catch (err) {
    UIErrors.handle(err);
  }
}

function renderEditorHeader() {
  document.getElementById("editorSlideTitle").textContent = state.slide.title || `Slide #${state.slide.slideOrder}`;
  document.getElementById("editorSlideMeta").textContent = `Tiêu chuẩn Canvas: 960x540 • Slide v${state.slide.version} • ${state.slide.generationState}`;
}

function populateSlideMetaForm() {
  document.getElementById("inputSlideTitle").value = state.slide.title || "";
  document.getElementById("inputSpeakerNotes").value = state.slide.speakerNotes || "";
}

function disableEditorMutations() {
  const btnIds = ["btnSaveMeta", "btnAddText", "btnAddShape", "btnAddImage", "btnSaveElem", "btnDeleteElem"];
  btnIds.forEach(id => {
    const btn = document.getElementById(id);
    if (btn) {
      btn.disabled = true;
      btn.classList.add("opacity-50", "cursor-not-allowed");
    }
  });
}

// Canvas Rendering
function renderCanvas() {
  const container = document.getElementById("canvasContainer");
  container.innerHTML = "";

  const elements = (state.slide.elements || []).slice().sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0));

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

    const isSelected = elem.id === state.selectedElementId;
    el.className = `cursor-pointer transition-all ${
      isSelected ? 'ring-2 ring-indigo-600 ring-offset-2 ring-offset-white' : 'hover:outline hover:outline-1 hover:outline-indigo-300'
    }`;

    // Click handler for selection
    el.onclick = (evt) => {
      evt.stopPropagation();
      selectElement(elem);
    };

    if (elem.elementType === "TEXT") {
      const text = elem.payload ? elem.payload.text || "" : "";
      el.className += " flex items-center p-2 text-slate-800 font-semibold select-none overflow-hidden";
      if (elem.style) {
        if (elem.style.fontSizePt) el.style.fontSize = `${elem.style.fontSizePt}px`;
        if (elem.style.color) el.style.color = elem.style.color;
        if (elem.style.bold) el.style.fontWeight = "bold";
        if (elem.style.italic) el.style.fontStyle = "italic";
        if (elem.style.textAlign) el.style.textAlign = elem.style.textAlign.toLowerCase();
      }
      el.textContent = text;
    } else if (elem.elementType === "SHAPE") {
      el.className += " border border-indigo-400 bg-indigo-50/50 rounded-lg flex items-center justify-center text-xs font-bold text-indigo-700 select-none overflow-hidden";
      if (elem.style) {
        if (elem.style.fillColor) el.style.backgroundColor = elem.style.fillColor;
        if (elem.style.strokeColor) el.style.borderColor = elem.style.strokeColor;
      }
      el.textContent = elem.payload ? elem.payload.text || elem.payload.primitive || "SHAPE" : "SHAPE";
    } else if (elem.elementType === "IMAGE") {
      if (elem.contentUrl) {
        const img = document.createElement("img");
        img.src = elem.contentUrl;
        img.className = "w-full h-full object-cover rounded-lg pointer-events-none";
        el.appendChild(img);
      } else {
        el.className += " bg-slate-200 rounded-lg flex items-center justify-center text-xs font-bold text-slate-500 select-none";
        el.textContent = "IMAGE (Chưa chọn ảnh)";
      }
    }

    container.appendChild(el);
  });

  // Canvas click to deselect
  container.onclick = () => {
    deselectElement();
  };
}

// Element Inspector Selection
function selectElement(elem) {
  state.selectedElementId = elem.id;

  document.getElementById("noElementSelected").classList.add("hidden");
  document.getElementById("elementForm").classList.remove("hidden");
  if (!state.isArchived) {
    document.getElementById("btnDeleteElem").classList.remove("hidden");
  }

  document.getElementById("inspectorType").textContent = elem.elementType;
  document.getElementById("elemX").value = elem.x;
  document.getElementById("elemY").value = elem.y;
  document.getElementById("elemW").value = elem.width;
  document.getElementById("elemH").value = elem.height;
  document.getElementById("elemRotation").value = elem.rotation || 0;
  document.getElementById("elemZIndex").value = elem.zIndex || 1;

  // Hide type specific sections
  document.getElementById("fieldsText").classList.add("hidden");
  document.getElementById("fieldsShape").classList.add("hidden");
  document.getElementById("fieldsImage").classList.add("hidden");

  if (elem.elementType === "TEXT") {
    document.getElementById("fieldsText").classList.remove("hidden");
    document.getElementById("textPayloadContent").value = elem.payload ? elem.payload.text || "" : "";
    document.getElementById("textFontSize").value = elem.style ? elem.style.fontSizePt || 24 : 24;
    document.getElementById("textColor").value = elem.style ? elem.style.color || "#1e293b" : "#1e293b";
    document.getElementById("textBold").checked = elem.style ? !!elem.style.bold : false;
    document.getElementById("textItalic").checked = elem.style ? !!elem.style.italic : false;
  } else if (elem.elementType === "SHAPE") {
    document.getElementById("fieldsShape").classList.remove("hidden");
    document.getElementById("shapePrimitive").value = elem.payload ? elem.payload.primitive || "RECTANGLE" : "RECTANGLE";
    document.getElementById("shapeText").value = elem.payload ? elem.payload.text || "" : "";
    document.getElementById("shapeFillColor").value = elem.style ? elem.style.fillColor || "#e0e7ff" : "#e0e7ff";
    document.getElementById("shapeStrokeColor").value = elem.style ? elem.style.strokeColor || "#6366f1" : "#6366f1";
  } else if (elem.elementType === "IMAGE") {
    document.getElementById("fieldsImage").classList.remove("hidden");
    document.getElementById("imageAssetFilename").textContent = elem.originalFilename || (elem.mediaAssetId ? `Media #${elem.mediaAssetId}` : "Chưa chọn ảnh");
  }

  renderCanvas();
}

function deselectElement() {
  state.selectedElementId = null;
  document.getElementById("noElementSelected").classList.remove("hidden");
  document.getElementById("elementForm").classList.add("hidden");
  document.getElementById("btnDeleteElem").classList.add("hidden");
  renderCanvas();
}

// Slide Title & Speaker Notes Patch
async function handleSaveSlideMeta() {
  if (state.isArchived) return;

  const title = document.getElementById("inputSlideTitle").value;
  const speakerNotes = document.getElementById("inputSpeakerNotes").value;

  try {
    const patchReq = {
      title,
      speakerNotes,
      expectedSlideVersion: state.slide.version
    };

    state.slide = await TeacherAPI.patchSlide(state.deckId, state.slideId, patchReq);
    UIErrors.showToast("Đã lưu thông tin Slide.", "success");
    renderEditorHeader();
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  }
}

// Add New Element
async function handleAddElement(elementType) {
  if (state.isArchived) return;

  let request = {
    elementType,
    expectedSlideVersion: state.slide.version,
    x: 100,
    y: 100,
    width: 300,
    height: 100,
    rotation: 0,
    zIndex: (state.slide.elements || []).length + 1,
    mediaAssetId: null,
    payload: {},
    style: {}
  };

  if (elementType === "TEXT") {
    request.payload = { text: "Nhập nội dung văn bản..." };
    request.style = { fontSizePt: 24, color: "#1e293b", bold: false, italic: false, textAlign: "LEFT" };
  } else if (elementType === "SHAPE") {
    request.payload = { primitive: "RECTANGLE", text: "SHAPE" };
    request.style = { fillColor: "#e0e7ff", strokeColor: "#6366f1", strokeWidthPt: 1 };
  } else if (elementType === "IMAGE") {
    request.width = 300;
    request.height = 200;
    request.payload = {};
    request.style = {};
  }

  try {
    const newElem = await TeacherAPI.addElement(state.deckId, state.slideId, request);
    UIErrors.showToast(`Đã thêm phần tử ${elementType}.`, "success");
    
    // Refetch slide to get authoritative versions
    await loadSlideData();
    selectElement(newElem);
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  }
}

// Update Existing Element
async function handleUpdateElement() {
  if (state.isArchived || !state.selectedElementId) return;

  const currentElem = (state.slide.elements || []).find(e => e.id === state.selectedElementId);
  if (!currentElem) return;

  const x = parseFloat(document.getElementById("elemX").value);
  const y = parseFloat(document.getElementById("elemY").value);
  const width = parseFloat(document.getElementById("elemW").value);
  const height = parseFloat(document.getElementById("elemH").value);
  const rotation = parseFloat(document.getElementById("elemRotation").value);
  const zIndex = parseInt(document.getElementById("elemZIndex").value, 10);

  let payload = {};
  let style = {};
  let mediaAssetId = currentElem.mediaAssetId;

  if (currentElem.elementType === "TEXT") {
    payload = { text: document.getElementById("textPayloadContent").value };
    style = {
      fontSizePt: parseInt(document.getElementById("textFontSize").value, 10),
      color: document.getElementById("textColor").value,
      bold: document.getElementById("textBold").checked,
      italic: document.getElementById("textItalic").checked,
      textAlign: "LEFT"
    };
  } else if (currentElem.elementType === "SHAPE") {
    payload = {
      primitive: document.getElementById("shapePrimitive").value,
      text: document.getElementById("shapeText").value
    };
    style = {
      fillColor: document.getElementById("shapeFillColor").value,
      strokeColor: document.getElementById("shapeStrokeColor").value
    };
  }

  const updateReq = {
    elementType: currentElem.elementType,
    expectedSlideVersion: state.slide.version,
    expectedElementVersion: currentElem.version,
    x, y, width, height, rotation, zIndex,
    mediaAssetId,
    payload,
    style
  };

  try {
    const updated = await TeacherAPI.updateElement(state.deckId, state.slideId, currentElem.id, updateReq);
    UIErrors.showToast("Đã cập nhật phần tử.", "success");
    await loadSlideData();
    selectElement(updated);
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  }
}

// Delete Element
async function handleDeleteElement() {
  if (state.isArchived || !state.selectedElementId) return;
  if (!confirm("Bạn có chắc chắn muốn xóa phần tử này khỏi slide?")) return;

  const currentElem = (state.slide.elements || []).find(e => e.id === state.selectedElementId);
  if (!currentElem) return;

  const deleteReq = {
    expectedSlideVersion: state.slide.version,
    expectedElementVersion: currentElem.version
  };

  try {
    await TeacherAPI.deleteElement(state.deckId, state.slideId, currentElem.id, deleteReq);
    UIErrors.showToast("Đã xóa phần tử.", "success");
    deselectElement();
    await loadSlideData();
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  }
}

// Media Picker Modal Callback for IMAGE Elements
function openMediaPickerForImage() {
  if (state.isArchived || !state.selectedElementId) return;

  TeacherMediaModal.open(state.deckId, async (selectedMedia) => {
    const currentElem = (state.slide.elements || []).find(e => e.id === state.selectedElementId);
    if (!currentElem || currentElem.elementType !== "IMAGE") return;

    const updateReq = {
      elementType: "IMAGE",
      expectedSlideVersion: state.slide.version,
      expectedElementVersion: currentElem.version,
      x: currentElem.x,
      y: currentElem.y,
      width: currentElem.width,
      height: currentElem.height,
      rotation: currentElem.rotation,
      zIndex: currentElem.zIndex,
      mediaAssetId: selectedMedia.id,
      payload: {},
      style: {}
    };

    try {
      const updated = await TeacherAPI.updateElement(state.deckId, state.slideId, currentElem.id, updateReq);
      UIErrors.showToast("Đã gắn hình ảnh vào Slide.", "success");
      await loadSlideData();
      selectElement(updated);
    } catch (err) {
      if (err.status === 409) {
        await loadSlideData();
      }
      UIErrors.handle(err);
    }
  });
}
