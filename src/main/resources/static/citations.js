function citationLocation(source, fallbackId) {
  const id = source.documentId ?? fallbackId;
  if (!/^\d+$/.test(String(id || ''))) return null;
  const match = String(source.pages || '').match(/\d+/);
  const page = source.pageStart ?? (match ? Number(match[0]) : null);
  const params = new URLSearchParams({ id: String(id) });
  if (Number.isInteger(Number(page)) && Number(page) > 0) params.set('page', String(page));
  return { href: `reader.html?${params}`, label: `${source.documentTitle || source.sectionTitle || 'Tài liệu'}${page ? ` · Trang ${page}${source.pageEnd > page ? `–${source.pageEnd}` : ''}` : ''}` };
}
function renderCitationSources(container, sources, docId) {
  const list = document.createElement('div'); list.className = 'citation-sources';
  for (const source of Array.isArray(sources) ? sources : []) {
    const location = citationLocation(source, docId);
    const link = document.createElement(location ? 'a' : 'span');
    if (location) { link.href = location.href; link.className = 'citation-link'; }
    link.textContent = `${source.sourceId || source.citationId || ''} ${location?.label || source.sectionTitle || 'Nguồn tài liệu'}`.trim();
    list.appendChild(link);
  }
  container.appendChild(list);
}
function renderAiMessageWithCitations(container, text, docId, sources = []) {
  container.replaceChildren(); container.classList.add('ai-message');
  const input = String(text ?? '');
  const refs = new Map((Array.isArray(sources) ? sources : []).map(s => [s.sourceId || s.citationId, s]));
  const pattern = /\[(S\d+|(?:Trang|Slide)\s+\d+)(?::\s*"([^"]*?)")?\]/g;
  let offset = 0;
  for (const match of input.matchAll(pattern)) {
    container.appendChild(document.createTextNode(input.slice(offset, match.index)));
    let source = refs.get(match[1]);
    if (!source && /^(Trang|Slide) /.test(match[1])) source = { documentId: docId, pageStart: Number(match[1].match(/\d+/)[0]) };
    const location = source && citationLocation(source, docId);
    if (location) {
      const link = document.createElement('a'); link.className = 'citation-link'; link.textContent = match[1];
      const params = new URLSearchParams(location.href.split('?')[1]);
      if (match[2]) params.set('q', match[2]);
      link.href = `reader.html?${params}`; container.appendChild(link);
    } else container.appendChild(document.createTextNode(match[0]));
    offset = match.index + match[0].length;
  }
  container.appendChild(document.createTextNode(input.slice(offset)));
}
