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
