function renderAiMessageWithCitations(container, text, docId) {
  const escaped = String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

  if (!docId) {
    container.textContent = text;
    return;
  }

  const linked = escaped.replace(/\[(Trang|Slide)\s+(\d+)(?::\s*"([^"]*?)")?\]/g, (match, label, num, quote) => {
    const params = new URLSearchParams({ id: docId, page: num });
    if (quote) params.set("q", quote);
    return `<a href="reader.html?${params.toString()}" class="citation-link inline-flex items-center gap-1 text-indigo-300 hover:text-indigo-100 underline underline-offset-2 font-bold text-[11px] align-middle">${label} ${num}</a>`;
  });

  container.innerHTML = linked;
}
