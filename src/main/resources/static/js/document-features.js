(function (global) {
  const pending = new Map();
  function run(kind, doc, action) {
    try { DocumentUI.requireReady(doc); } catch (error) { return Promise.reject(error); }
    const version = doc.activeIndexVersion, key = `${kind}:${doc.id}:${version}`;
    if (pending.has(key)) return pending.get(key);
    const check = () => DocumentUI.assertCurrent(doc, version);
    const request = action(check).then(data => { check(); return data; }).finally(() => pending.delete(key));
    pending.set(key, request); return request;
  }
  const cards = data => (Array.isArray(data) ? data : data?.cards || []).filter(card => !card.deletedAt);
  const questions = data => Array.isArray(data) ? data : data?.questions || [];
  function flashcards(doc, force = false) {
    return run('flashcard', doc, async check => {
      // These compatibility GET routes only read stored Phase 10 data; generation is always POST.
      let data = cards(await ApiClient.get(`/api/flashcards/document/${doc.id}`)), generation;
      if (force || !data.length) {
        check();
        generation = await ApiClient.post(`/api/documents/${doc.id}/flashcards/generate`, { quickStart: true });
        check(); data = cards(await ApiClient.get(`/api/flashcards/document/${doc.id}`));
      }
      return { cards: data, generation };
    });
  }
  function quiz(doc, force = false) {
    return run('quiz', doc, async check => {
      let saved = force ? null : await ApiClient.get(`/api/quizzes/document/${doc.id}`), generation;
      if (force || !questions(saved).length) {
        check();
        generation = await ApiClient.post('/api/quizzes/generate', {
          documentId: doc.id, scopeType: 'DOCUMENT', mode: 'PRACTICE', allowedQuestionTypes: ['MCQ_SINGLE']
        });
        check(); saved = await ApiClient.get(generation.quizId ? `/api/quizzes/${generation.quizId}/questions` : `/api/quizzes/document/${doc.id}`);
      }
      return { questions: questions(saved).map(q => ({ ...q, questionId: q.questionId ?? q.id })),
        quizId: generation?.quizId ?? saved?.quizId, generation };
    });
  }
  function chat(docs, message) {
    try { if (!docs.length) DocumentUI.requireReady(null); docs.forEach(DocumentUI.requireReady); }
    catch (error) { return Promise.reject(error); }
    // The existing grounded chat endpoint currently scopes retrieval to the first document's notebook.
    // Never use the free-conversation endpoint as a fallback for notebook-less documents.
    return run(`chat:${docs.map(doc => `${doc.id}:${doc.activeIndexVersion}`).join(",")}:${message}`, docs[0], async () => {
      const versions = docs.map(doc => doc.activeIndexVersion);
      const data = await ApiClient.post('/api/ai/chat', { message, docIds: docs.map(doc => doc.id) });
      docs.forEach((doc, i) => DocumentUI.assertCurrent(doc, versions[i]));
      return { text: data.hasSufficientEvidence === false || data.sufficientEvidence === false || !(data.sources || data.citations || []).length
        ? 'Chưa có đủ bằng chứng trong tài liệu để trả lời câu hỏi này.' : data.response || data.answer || '',
        citations: data.sources || data.citations || [], docId: data.docId || docs[0].id };
    });
  }
  function startAttempt(quizId) { return ApiClient.post(`/api/quizzes/${quizId}/attempts`, {}); }
  function submitAttempt(attempt, answers, token) {
    return ApiClient.post(`/api/quiz-attempts/${attempt.attemptId}/submit`, {
      submissionToken: token,
      answers: attempt.questions.map(q => ({ questionOrder: q.questionOrder,
        selectedOptionOrders: answers[q.questionId] === undefined ? [] : [Number(answers[q.questionId])], shortAnswerText: null }))
    });
  }
  global.DocumentFeatures = { flashcards, quiz, chat, startAttempt, submitAttempt };
})(window);
