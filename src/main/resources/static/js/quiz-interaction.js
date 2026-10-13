(function (global) {
  function unanswered(questions, answers) {
    return (Array.isArray(questions) ? questions : []).filter(q => answers[q.questionId ?? q.id] === undefined);
  }
  function validate(quizId, questions, answers) {
    const validId = id => /^\d+$/.test(String(id)) && Number(id) > 0;
    if (!validId(quizId)) return 'Không xác định được bài trắc nghiệm. Vui lòng tải lại hoặc tạo bài mới.';
    if (!Array.isArray(questions) || !questions.length) return 'Bài trắc nghiệm chưa có câu hỏi. Vui lòng tạo bài mới.';
    const ids = new Set();
    for (const q of questions) {
      const id = q.questionId ?? q.id;
      if (!validId(id) || ids.has(String(id))) return 'Mã câu hỏi không hợp lệ. Vui lòng tải lại bài trắc nghiệm.';
      ids.add(String(id));
      if (answers[id] !== undefined) {
        const options = q.options || [q.answerA, q.answerB, q.answerC, q.answerD].filter(Boolean);
        if (!options.some((o,i) => (o.optionOrder ?? i) === answers[id])) return 'Lựa chọn đáp án không hợp lệ. Vui lòng chọn lại.';
      }
    }
    if (unanswered(questions, answers).length) return 'Bạn cần trả lời tất cả câu hỏi trước khi nộp bài.';
    return null;
  }
  function errorMessage(error) {
    if (error.status === 400 && error.stage === 'start') return 'Không thể bắt đầu lượt làm bài. Bài cũ có thể chưa được xuất bản; vui lòng bấm Tạo bài mới.';
    if (error.status === 400) return 'Máy chủ từ chối dữ liệu câu trả lời. Vui lòng tải lại bài hoặc tạo bài mới.';
    if (error.status === 401) return 'Phiên đăng nhập đã hết hạn.';
    if (error.status === 403 || error.status === 404) return 'Bạn không có quyền truy cập bài trắc nghiệm này.';
    if (error.status === 409) return 'Lượt làm bài đã hết hạn hoặc đã được nộp. Vui lòng bắt đầu lượt mới.';
    return 'Không thể chấm bài lúc này. Vui lòng thử lại.';
  }
  function render(box, state, onAnswer, onSubmit) {
    const scrollTop = box.scrollTop;
    box.replaceChildren();
    state.questions.forEach((q, index) => {
      const id = q.questionId ?? q.id;
      const section = document.createElement('section'); section.className = 'summary-section quiz-question';
      section.id = `quiz-question-${id}`; section.tabIndex = -1;
      const title = document.createElement('h3'); title.textContent = `Câu ${index + 1}: ${q.questionText || q.question || 'Câu hỏi'}`;
      section.appendChild(title);
      const result = state.result?.perQuestionResults?.find(r => r.questionOrder === q.questionOrder);
      const options = q.options || [q.answerA, q.answerB, q.answerC, q.answerD].filter(Boolean);
      options.forEach((option, optionIndex) => {
        const value = option.optionOrder ?? optionIndex;
        const selected = state.answers[id] === value;
        const correct = result?.correctOptionOrders?.includes(value);
        const button = document.createElement('button'); button.type = 'button';
        button.className = 'quiz-option' + (selected ? ' is-selected' : '')
          + (correct ? ' is-correct' : result && selected && !result.isCorrect ? ' is-wrong' : '');
        button.setAttribute('aria-pressed', String(selected)); button.disabled = !!state.result || state.busy || state.locked;
        button.textContent = `${correct ? '✓' : result && selected && !result.isCorrect ? '✕' : selected ? '●' : '○'} ${String.fromCharCode(65 + optionIndex)}. ${option.optionText ?? option}`;
        button.onclick = () => onAnswer(id, optionIndex, value); section.appendChild(button);
      });
      if (result) {
        const feedback = document.createElement('p'); feedback.className = result.isCorrect ? 'quiz-correct-text' : 'quiz-wrong-text';
        feedback.textContent = result.isCorrect ? '✓ ĐÚNG' : '✕ SAI'; section.appendChild(feedback);
        const chosen = options.find((o, i) => (o.optionOrder ?? i) === state.answers[id]);
        const selectedText = document.createElement('p'); selectedText.textContent = `Bạn chọn: ${chosen?.optionText ?? chosen ?? 'Chưa trả lời'}`; section.appendChild(selectedText);
        const correctText = document.createElement('p');
        correctText.textContent = 'Đáp án đúng: ' + (options.filter((o, i) => result.correctOptionOrders?.includes(o.optionOrder ?? i)).map(o => o.optionText ?? o).join('; ') || 'Chưa có dữ liệu đáp án từ máy chủ.');
        section.appendChild(correctText);
        const explanation = document.createElement('p'); explanation.textContent = `Giải thích: ${result.explanation || 'Chưa có giải thích từ máy chủ.'}`; section.appendChild(explanation);
      }
      box.appendChild(section);
    });
    if (state.result) {
      const total = state.result.totalQuestions, correct = state.result.correctAnswers;
      const overall = document.createElement('p'); overall.className = 'feature-notice';
      overall.textContent = `Điểm: ${state.result.score} / 100 · Tỷ lệ đúng: ${total ? Math.round(correct / total * 100) : 0}% · Số câu đúng: ${correct} · Số câu sai: ${total - correct}`;
      box.appendChild(overall);
    }
    const status = document.createElement('p'); status.id = 'quizInteractionStatus'; status.setAttribute('role', 'status'); status.textContent = state.busy ? 'Đang chuẩn bị lượt làm bài...' : ''; box.appendChild(status);
    const action = document.createElement('button'); action.type = 'button'; action.className = 'btn-3d btn-3d-primary p-3';
    action.textContent = state.result ? 'Đã chấm bài' : 'Nộp bài và chấm điểm'; action.disabled = state.busy || !!state.result || !state.questions.length;
    action.onclick = () => onSubmit(action, status); box.appendChild(action);
    box.scrollTop = scrollTop;
  }
  global.QuizInteraction = { render, unanswered, validate, errorMessage };
})(window);
