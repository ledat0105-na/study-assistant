const TeacherAPI = (function() {
  const BASE_URL = '/api/teacher/presentations';

  return {
    // 1. Decks
    listPresentations: (page = 0, size = 20) => {
      return ApiClient.get(`${BASE_URL}?page=${page}&size=${size}`);
    },

    getPresentation: (deckId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}`);
    },

    createPresentation: (command) => {
      return ApiClient.post(BASE_URL, command);
    },

    patchPresentation: (deckId, command, version) => {
      return ApiClient.patch(`${BASE_URL}/${deckId}`, command, {
        'If-Match': `"${version}"`
      });
    },

    archivePresentation: (deckId, version) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/archive`, null, {
        'If-Match': `"${version}"`
      });
    },

    // 2. Outlines
    getOutlines: (deckId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/outlines`);
    },

    generateOutline: (deckId, command, idempotencyKey) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/outlines/generate`, command, {
        'Idempotency-Key': idempotencyKey || crypto.randomUUID()
      });
    },

    approveOutline: (deckId, revisionId, version) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/outlines/${revisionId}/approve`, null, {
        'If-Match': `"${version}"`
      });
    },

    // 3. Slides
    listSlides: (deckId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/slides`);
    },

    getSlide: (deckId, slideId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/slides/${slideId}`);
    },

    patchSlide: (deckId, slideId, request) => {
      return ApiClient.patch(`${BASE_URL}/${deckId}/slides/${slideId}`, request);
    },

    generateSlides: (deckId, idempotencyKey) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/slides/generate`, null, {
        'Idempotency-Key': idempotencyKey || crypto.randomUUID()
      });
    },

    retrySlide: (deckId, slideId, command, idempotencyKey) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/slides/${slideId}/retry`, command, {
        'Idempotency-Key': idempotencyKey || crypto.randomUUID()
      });
    },

    // 4. Elements
    addElement: (deckId, slideId, request) => {
      return ApiClient.post(`${BASE_URL}/${deckId}/slides/${slideId}/elements`, request);
    },

    updateElement: (deckId, slideId, elementId, request) => {
      return ApiClient.patch(`${BASE_URL}/${deckId}/slides/${slideId}/elements/${elementId}`, request);
    },

    deleteElement: (deckId, slideId, elementId, request) => {
      return ApiClient.del(`${BASE_URL}/${deckId}/slides/${slideId}/elements/${elementId}`, request);
    },

    // 5. Media
    uploadMedia: (deckId, file) => {
      const formData = new FormData();
      formData.append('file', file);
      return ApiClient.post(`${BASE_URL}/${deckId}/media`, formData);
    },

    listMedia: (deckId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/media`);
    },

    getMedia: (deckId, mediaId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/media/${mediaId}`);
    },

    getMediaContentUrl: (deckId, mediaId) => {
      return `${BASE_URL}/${deckId}/media/${mediaId}/content`;
    },

    deleteMedia: (deckId, mediaId) => {
      return ApiClient.del(`${BASE_URL}/${deckId}/media/${mediaId}`);
    },

    // 6. Job & Export
    getJob: (deckId, jobId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/jobs/${jobId}`);
    },

    exportPptx: (deckId) => {
      return ApiClient.get(`${BASE_URL}/${deckId}/export/pptx`, {}, { responseType: 'blob' });
    }
  };
})();

const presentationCanvasObservers = new WeakMap();

function fitPresentationCanvasText(canvas) {
  function fit() {
    if (!canvas.clientWidth) return;
    const scale = canvas.clientWidth / 960;
    for (const box of canvas.querySelectorAll('.slide-canvas-text')) {
      const desiredSize = (Number(box.dataset.slideFontSize) || 18) * scale;
      let low = Math.min(1, desiredSize), high = desiredSize;
      box.style.fontSize = high + 'px';
      if (box.scrollHeight <= box.clientHeight && box.scrollWidth <= box.clientWidth) continue;
      for (let step = 0; step < 9; step++) {
        const size = (low + high) / 2;
        box.style.fontSize = size + 'px';
        if (box.scrollHeight <= box.clientHeight && box.scrollWidth <= box.clientWidth) low = size;
        else high = size;
      }
      box.style.fontSize = low + 'px';
    }
  }

  presentationCanvasObservers.get(canvas)?.disconnect();
  fit();
  const observer = new ResizeObserver(fit);
  observer.observe(canvas);
  presentationCanvasObservers.set(canvas, observer);
}
// Use the visible background under the text to keep the editor and preview readable.
function presentationTextColor(element, elements, canvasColor) {
  const foreground = element.style?.color || "#1e293b";
  const x = element.x + element.width / 2;
  const y = element.y + element.height / 2;
  const backdrop = elements.slice().reverse().find(shape =>
    shape.elementType === "SHAPE" && shape.zIndex < element.zIndex &&
    /^#[0-9a-f]{6}$/i.test(shape.style?.fillColor) &&
    x >= shape.x && x <= shape.x + shape.width &&
    y >= shape.y && y <= shape.y + shape.height);
  function luminance(color) {
    if (!/^#[0-9a-f]{6}$/i.test(color)) return null;
    const channels = color.slice(1).match(/../g).map(value => {
      const channel = parseInt(value, 16) / 255;
      return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
  }
  const background = luminance(backdrop?.style.fillColor || canvasColor);
  const ink = luminance(foreground);
  if (background === null || ink === null) return foreground;
  const ratio = (Math.max(background, ink) + 0.05) / (Math.min(background, ink) + 0.05);
  if (ratio >= 4.5) return foreground;
  return background > 0.179 ? "#000000" : "#ffffff";
}
