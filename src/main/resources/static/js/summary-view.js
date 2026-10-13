(function (global) {
  function render(container, data) {
    container.replaceChildren();
    const content = data.content;
    const refs = data.citations || [];
    function block(title, texts) {
      if (!texts.length) return;
      const section = document.createElement('section'); section.className = 'summary-section';
      const heading = document.createElement('h3'); heading.textContent = title; section.appendChild(heading);
      for (const value of texts) {
        const text = document.createElement('p');
        renderAiMessageWithCitations(text, value, data.docId, refs); section.appendChild(text);
      }
      container.appendChild(section);
    }
    if (content && typeof content === 'object') {
      block('Tổng quan', content.overview ? [content.overview] : []);
      block('Ý chính', (content.keyPoints || []).map(p => typeof p === 'string' ? p : `${p.text || ''} ${(p.sourceRefs || []).map(r => `[${r}]`).join(' ')}`));
      block('Thuật ngữ quan trọng', (content.importantTerms || []).map(t => `${t.term || ''}: ${t.definition || ''} ${(t.sourceRefs || []).map(r => `[${r}]`).join(' ')}`));
      block('Kết luận', content.conclusion ? [content.conclusion] : []);
    }
    if (!container.children.length) {
      const text = document.createElement('p'); renderAiMessageWithCitations(text, data.summary || '', data.docId, refs); container.appendChild(text);
    }
    if (refs.length) {
      const section = document.createElement('section'); section.className = 'summary-section';
      const heading = document.createElement('h3'); heading.textContent = 'Nguồn tham chiếu'; section.appendChild(heading);
      renderCitationSources(section, refs, data.docId); container.appendChild(section);
    }
  }
  global.SummaryView = { render };
})(window);
