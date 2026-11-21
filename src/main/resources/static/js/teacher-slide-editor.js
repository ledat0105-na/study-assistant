// State
let state = {
  deckId: null,
  slideId: null,
  deck: null,
  slide: null,
  selectedElementId: null,
  moveMode: false,
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

  document.getElementById("elementForm").addEventListener("input", () => renderCanvas(true));
  await loadSlideData();
  if (state.slide && urlParams.get("insertImage") === "1") {
    handleAddElement("IMAGE");
  }
});

async function loadSlideData() {
  try {
    state.deck = await TeacherAPI.getPresentation(state.deckId);
    const slide = await TeacherAPI.getSlide(state.deckId, state.slideId);
    const notesInput = document.getElementById("inputSpeakerNotes");
    const draftNotes = state.slide && notesInput.value !== (state.slide.speakerNotes || "")
      ? notesInput.value : null;
    state.slide = slide;
    state.elementPositionDraft = null;
    setElementMoveMode(false);
    state.isArchived = state.deck.status === "ARCHIVED";

    if (state.isArchived) {
      document.getElementById("editorArchivedNotice").classList.remove("hidden");
      disableEditorMutations();
    }

    renderEditorHeader();
    renderCanvas();
    populateSlideMetaForm();
    if (draftNotes !== null && !state.isArchived) notesInput.value = draftNotes;

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
  document.getElementById("inputSpeakerNotes").value = state.slide.speakerNotes || "";
}

function disableEditorMutations() {
  document.getElementById("inputSpeakerNotes").readOnly = true;
  const btnIds = ["btnSaveNotes", "btnAddText", "btnAddShape", "btnAddImage", "btnSaveElem", "btnMoveElem", "btnDeleteElem"];
  btnIds.forEach(id => {
    const btn = document.getElementById(id);
    if (btn) {
      btn.disabled = true;
      btn.classList.add("opacity-50", "cursor-not-allowed");
    }
  });
}

// Canvas Rendering
function renderCanvas(previewForm = false) {
  const container = document.getElementById("canvasContainer");
  container.innerHTML = "";
  const canvasColor = /^#[\da-f]{6}$/i.test(state.deck.themeKey) ? state.deck.themeKey : "#ffffff";

  container.style.backgroundColor = canvasColor;

  const elements = (state.slide.elements || []).map(saved => {
    const draft = state.elementPositionDraft;
    const element = draft?.id === saved.id ? { ...saved, ...draft } : saved;
    if (!previewForm || element.id !== state.selectedElementId) return element;
    const options = readElementForm(element);
    for (const field of ["x", "y", "width", "height", "rotation", "zIndex"]) {
      if (!Number.isFinite(options[field])) options[field] = element[field];
    }
    return { ...element, ...options };
  }).sort((a, b) => (a.zIndex || 0) - (b.zIndex || 0));

  elements.forEach(elem => {
    const el = document.createElement("div");
    const position = elem;
    el.style.position = "absolute";
    el.style.left = `${(position.x / 960) * 100}%`;
    el.style.top = `${(position.y / 540) * 100}%`;
    el.style.width = `${(elem.width / 960) * 100}%`;
    el.style.height = `${(elem.height / 540) * 100}%`;
    if (elem.rotation) {
      el.style.transform = `rotate(${elem.rotation}deg)`;
    }
    el.style.zIndex = elem.zIndex || 1;

    const isSelected = elem.id === state.selectedElementId;
    el.className = `cursor-pointer ${
      isSelected ? 'ring-2 ring-indigo-600 ring-offset-2 ring-offset-white' : 'hover:outline hover:outline-1 hover:outline-indigo-300'
    }`;

    // Click handler for selection
    el.onclick = (evt) => {
      evt.stopPropagation();
      if (state.selectedElementId !== elem.id) selectElement(elem);
    };

    if (elem.elementType === "TEXT") {
      const text = elem.payload ? elem.payload.text || "" : "";
      el.className += " slide-canvas-text text-slate-800 select-none";
      el.dataset.slideFontSize = elem.style?.fontSizePt || 18;
      el.style.color = presentationTextColor(elem, elements, canvasColor);
      if (elem.style) {
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
      el.textContent = elem.payload?.text || "";
    } else if (elem.elementType === "IMAGE") {
      if (elem.contentUrl) {
        const img = document.createElement("img");
        img.src = elem.contentUrl;
        img.className = "w-full h-full object-contain pointer-events-none";
        el.appendChild(img);
      } else {
        el.className += " bg-slate-200 rounded-lg flex items-center justify-center text-xs font-bold text-slate-500 select-none";
        el.textContent = "IMAGE (Chưa chọn ảnh)";
      }
    }

    if (isSelected && state.moveMode) enableElementDrag(el, elem, container);
    container.appendChild(el);
  });
  fitPresentationCanvasText(container);

  // Canvas click to deselect
  container.onclick = event => {
    if (event.target === container) deselectElement();
  };
}


function setElementMoveMode(enabled) {
  state.moveMode = enabled;
  const button = document.getElementById("btnMoveElem");
  button.setAttribute("aria-pressed", String(enabled));
  button.textContent = enabled ? "Tắt chế độ di chuyển" : "Di chuyển phần tử";
}

function toggleElementMoveMode() {
  if (state.isArchived || !state.selectedElementId) return;
  setElementMoveMode(!state.moveMode);
  renderCanvas(true);
}

function enableElementDrag(box, element, canvas) {
  if (state.isArchived) return;
  let gesture;
  box.style.touchAction = "none";
  box.style.cursor = "grab";
  box.addEventListener("pointerdown", event => {
    if (event.button !== 0) return;
    gesture = { id: event.pointerId, x: event.clientX, y: event.clientY, moved: false };
    box.setPointerCapture(event.pointerId);
  });
  box.addEventListener("pointermove", event => {
    if (gesture?.id !== event.pointerId) return;
    const dx = event.clientX - gesture.x, dy = event.clientY - gesture.y;
    if (!gesture.moved && Math.hypot(dx, dy) < 4) return;
    gesture.moved = true;
    const x = Math.max(0, Math.min(960 - element.width, Math.round(element.x + dx * 960 / canvas.clientWidth)));
    const y = Math.max(0, Math.min(540 - element.height, Math.round(element.y + dy * 540 / canvas.clientHeight)));
    Object.assign(gesture, { nextX: x, nextY: y });
    box.style.left = (x / 960 * 100) + "%";
    box.style.top = (y / 540 * 100) + "%";
  });
  box.addEventListener("pointerup", event => {
    if (gesture?.id !== event.pointerId || !gesture.moved) return;
    state.elementPositionDraft = { id: element.id, x: gesture.nextX, y: gesture.nextY };
    if (state.selectedElementId === element.id) {
      document.getElementById("elemX").value = gesture.nextX;
      document.getElementById("elemY").value = gesture.nextY;
      renderCanvas(true);
    } else {
      selectElement(element);
    }
  });
  box.addEventListener("pointercancel", () => {
    if (gesture?.moved) renderCanvas();
    gesture = null;
  });
}

// Element Inspector Selection
function selectElement(elem) {
  if (state.selectedElementId !== elem.id) setElementMoveMode(false);
  state.selectedElementId = elem.id;

  document.getElementById("noElementSelected").classList.add("hidden");
  document.getElementById("elementForm").classList.remove("hidden");
  if (!state.isArchived) {
    document.getElementById("btnDeleteElem").classList.remove("hidden");
  }

  document.getElementById("inspectorType").textContent = elem.elementType;
  const draft = state.elementPositionDraft;
  const position = draft?.id === elem.id ? draft : elem;
  document.getElementById("elemX").value = position.x;
  document.getElementById("elemY").value = position.y;
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
    const primitive = document.getElementById("shapePrimitive");
    const savedPrimitive = elem.payload?.primitive;
    primitive.value = Array.from(primitive.options).some(option => option.value === savedPrimitive)
      ? savedPrimitive : savedPrimitive === "CIRCLE" ? "ELLIPSE" : "RECTANGLE";
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
  setElementMoveMode(false);
  state.selectedElementId = null;
  document.getElementById("noElementSelected").classList.remove("hidden");
  document.getElementById("elementForm").classList.add("hidden");
  document.getElementById("btnDeleteElem").classList.add("hidden");
  renderCanvas();
}

// Speaker Notes
async function handleSaveSpeakerNotes() {
  if (state.isArchived || !state.slide || state.savingSlideMeta) return;

  const notesInput = document.getElementById("inputSpeakerNotes");
  const speakerNotes = notesInput.value;
  state.savingSlideMeta = true;

  try {
    const patchReq = {
      speakerNotes,
      expectedSlideVersion: state.slide.version
    };

    state.slide = await TeacherAPI.patchSlide(state.deckId, state.slideId, patchReq);
    UIErrors.showToast("Đã lưu ghi chú.", "success");
    renderEditorHeader();
    if (notesInput.value === speakerNotes) notesInput.value = state.slide.speakerNotes || "";
    notesInput.closest("details").open = true;
    const selected = (state.slide.elements || []).find(element => element.id === state.selectedElementId);
    if (selected) selectElement(selected);
    else renderCanvas();
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  } finally {
    state.savingSlideMeta = false;
  }
}

// Add New Element
async function handleAddElement(elementType, selectedMedia = null) {
  if (state.isArchived || !state.slide) return;
  if (elementType === "IMAGE" && !selectedMedia) {
    TeacherMediaModal.open(state.deckId, media => handleAddElement("IMAGE", media));
    return;
  }

  let request = {
    elementType,
    expectedSlideVersion: state.slide.version,
    x: 100,
    y: 100,
    width: 300,
    height: 100,
    rotation: 0,
    zIndex: Math.max(0, ...(state.slide.elements || []).map(element => element.zIndex || 0)) + 1,
    mediaAssetId: null,
    payload: {},
    style: {}
  };

  if (elementType === "TEXT") {
    request.payload = { text: "Nhập nội dung văn bản..." };
    request.style = { fontSizePt: 24, color: "#1e293b", bold: false, italic: false, textAlign: "LEFT" };
  } else if (elementType === "SHAPE") {
    request.payload = { primitive: "RECTANGLE", text: "" };
    request.style = { fillColor: "#e0e7ff", strokeColor: "#6366f1", strokeWidthPt: 1 };
  } else if (elementType === "IMAGE") {
    const ratio = Number(selectedMedia.width) / Number(selectedMedia.height);
    if (Number.isFinite(ratio) && ratio > 0) {
      // Slide geometry accepts at most two decimal places.
      request.width = Number(Math.max(1, Math.min(300, 350 * ratio)).toFixed(2));
      request.height = Number(Math.max(1, Math.min(350, 300 / ratio)).toFixed(2));
    } else {
      request.height = 200;
    }
    request.mediaAssetId = selectedMedia.id;
    request.payload = {};
    request.style = {};
  }

  try {
    const newElem = await TeacherAPI.addElement(state.deckId, state.slideId, request);
    UIErrors.showToast(`Đã thêm phần tử ${elementType}.`, "success");
    
    // Refetch slide to get authoritative versions
    state.selectedElementId = newElem.id;
    await loadSlideData();
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

  const updateReq = readElementForm(currentElem);

  try {
    await TeacherAPI.updateElement(state.deckId, state.slideId, currentElem.id, updateReq);
    UIErrors.showToast("Đã cập nhật phần tử.", "success");
    await loadSlideData();
  } catch (err) {
    if (err.status === 409) await loadSlideData();
    UIErrors.handle(err);
  }
}

function readElementForm(currentElem) {
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
      textAlign: currentElem.style?.textAlign || "LEFT"
    };
  } else if (currentElem.elementType === "SHAPE") {
    payload = {
      primitive: document.getElementById("shapePrimitive").value,
      text: document.getElementById("shapeText").value
    };
    style = {
      strokeWidthPt: currentElem.style?.strokeWidthPt ?? 1,
      fillColor: document.getElementById("shapeFillColor").value,
      strokeColor: document.getElementById("shapeStrokeColor").value
    };
  }

  return {
    elementType: currentElem.elementType,
    expectedSlideVersion: state.slide.version,
    expectedElementVersion: currentElem.version,
    x, y, width, height, rotation, zIndex,
    mediaAssetId,
    payload,
    style
  };
}

// Delete Element
async function handleDeleteElement() {
  if (state.isArchived || !state.selectedElementId || state.deletingElement) return;

  const currentElem = (state.slide.elements || []).find(e => e.id === state.selectedElementId);
  if (!currentElem) return;

  const deleteReq = {
    expectedSlideVersion: state.slide.version,
    expectedElementVersion: currentElem.version
  };

  state.deletingElement = true;
  try {
    const confirmed = await AppModal.confirm("Bạn có chắc muốn xóa phần tử này khỏi slide?", {
      title: "Xóa phần tử",
      tone: "warning",
      buttonLabel: "Xóa phần tử"
    });
    if (!confirmed) return;

    await TeacherAPI.deleteElement(state.deckId, state.slideId, currentElem.id, deleteReq);
    UIErrors.showToast("Đã xóa phần tử.", "success");
    deselectElement();
    await loadSlideData();
  } catch (err) {
    if (err.status === 409) {
      await loadSlideData();
    }
    UIErrors.handle(err);
  } finally {
    state.deletingElement = false;
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
