(function (global) {
  const NS = 'http://www.w3.org/2000/svg';
  const WIDTH = 280, HEIGHT = 162;
  const relationNames = { PART_OF: 'là thành phần của', IS_A: 'là một loại',
    PREREQUISITE_OF: 'là điều kiện trước của', RELATED_TO: 'liên quan đến', HAS_PART: 'bao gồm' };
  let sequence = 0;

  function layout(graph) {
    const nodes = [], byId = new Map();
    for (const node of graph.nodes || []) {
      const id = String(node.conceptId ?? node.id);
      if (id === 'undefined' || byId.has(id)) continue;
      const item = { ...node, key: id, neighbors: [], incoming: 0 };
      nodes.push(item); byId.set(id, item);
    }
    const edges = (graph.edges || []).filter(edge => byId.has(String(edge.sourceConceptId))
      && byId.has(String(edge.targetConceptId))).map(edge => ({ ...edge,
      source: byId.get(String(edge.sourceConceptId)), target: byId.get(String(edge.targetConceptId)) }));
    for (const edge of edges) {
      const reverse = ['PART_OF', 'IS_A'].includes(edge.relationType);
      const parent = reverse ? edge.target : edge.source, child = reverse ? edge.source : edge.target;
      parent.neighbors.push(child); child.incoming++;
    }
    const placed = new Set(), levels = [];
    function visit(root) {
      const queue = [{ node: root, depth: 0 }];
      placed.add(root.key);
      for (let index = 0; index < queue.length; index++) {
        const { node, depth } = queue[index];
        (levels[depth] ||= []).push(node);
        for (const neighbor of node.neighbors) {
          if (!placed.has(neighbor.key)) {
            placed.add(neighbor.key); queue.push({ node: neighbor, depth: depth + 1 });
          }
        }
      }
    }
    // Roots first, then cycles. Every node remains visible, including isolated concepts.
    for (const node of nodes.filter(node => node.incoming === 0 && node.neighbors.length)) visit(node);
    for (const node of nodes.filter(node => node.incoming || node.neighbors.length)) if (!placed.has(node.key)) visit(node);
    let rows = Math.max(0, ...levels.map(level => level.length));
    levels.forEach((level, depth) => level.forEach((node, row) => {
      node.x = 50 + depth * 310; node.y = 50 + (row + (rows - level.length) / 2) * 125;
    }));
    const isolated = nodes.filter(node => !placed.has(node.key));
    isolated.forEach((node, index) => {
      node.x = 50 + (index % 3) * 310; node.y = 50 + rows * 125 + Math.floor(index / 3) * 125;
    });
    return { nodes, edges, width: Math.max(400, ...nodes.map(node => node.x + WIDTH + 50)),
      height: Math.max(240, ...nodes.map(node => node.y + HEIGHT + 50)) };
  }

  function branch(graph, focusId, page = 0) {
    const all = layout(graph);
    const focus = all.nodes.find(node => node.key === String(focusId)) || all.nodes.slice().sort((a, b) =>
      (b.incoming + b.neighbors.length) - (a.incoming + a.neighbors.length))[0];
    if (!focus) return { ...all, all, pages: 1, page: 0 };
    const incident = all.edges.filter(edge => edge.source.key === focus.key || edge.target.key === focus.key);
    const relatedIds = [...new Set(incident.map(edge => edge.source.key === focus.key ? edge.target.key : edge.source.key))]
      .filter(id => id !== focus.key);
    const pages = Math.max(1, Math.ceil(relatedIds.length / 4));
    page = Math.max(0, Math.min(page, pages - 1));
    const visibleIds = new Set(relatedIds.slice(page * 4, page * 4 + 4));
    const related = all.nodes.filter(node => visibleIds.has(node.key));
    focus.x = related.length ? 420 : 20; focus.y = related.length > 2 ? 145 : 35;
    related.forEach((node, index) => {
      node.x = index % 2 ? 820 : 20; node.y = 35 + Math.floor(index / 2) * 235;
    });
    return { all, focus, nodes: [focus, ...related], edges: incident.filter(edge =>
      visibleIds.has(edge.source.key) || visibleIds.has(edge.target.key)), pages, page,
      width: related.length ? 1140 : 320, height: related.length > 2 ? 475 : 240 };
  }

  function render(container, graph, focusId, page = 0, options = {}) {
    container.replaceChildren();
    const data = branch(graph, focusId, page);
    if (options.collapsed && data.focus) {
      data.nodes = [data.focus]; data.edges = []; data.focus.x = 20; data.focus.y = 35; data.width = 320; data.height = 240;
    }
    if (!data.nodes.length) { container.textContent = 'Chưa có khái niệm trong tài liệu.'; return; }
    const frame = document.createElement('div'); frame.className = 'kg-map';
    const toolbar = document.createElement('div'); toolbar.className = 'kg-map-toolbar';
    const count = document.createElement('span');
    count.textContent = `Đang xem ${data.nodes.length}/${data.all.nodes.length} khái niệm · Nhánh ${data.page + 1}/${data.pages}`;
    toolbar.appendChild(count);
    const controls = document.createElement('div'); controls.className = 'kg-map-controls'; toolbar.appendChild(controls);
    const navigation = document.createElement('div'); navigation.className = 'kg-map-navigation';
    const search = document.createElement('input'); search.type = 'search'; search.placeholder = 'Tìm khái niệm...';
    search.setAttribute('aria-label', 'Tìm khái niệm');
    const picker = document.createElement('select'); picker.setAttribute('aria-label', 'Chọn khái niệm trung tâm');
    function populate(query = '') {
      picker.replaceChildren();
      for (const node of data.all.nodes.filter(node => (node.name || node.canonicalName || '').toLocaleLowerCase().includes(query.toLocaleLowerCase()))) {
        const option = document.createElement('option'); option.value = node.key;
        option.textContent = node.name || node.canonicalName; picker.appendChild(option);
      }
      picker.value = data.focus.key;
    }
    populate(); search.addEventListener('input', () => populate(search.value));
    picker.addEventListener('change', () => { if (picker.value) render(container, graph, picker.value, 0, options); });
    navigation.append(search, picker);
    const instruction = document.createElement('p'); instruction.className = 'kg-map-instruction';
    instruction.textContent = 'Chọn nút để đọc mô tả; bấm “Mở nhánh này” để khám phá tiếp. Lăn chuột để cuộn; giữ Ctrl/Cmd và lăn chuột để phóng to hoặc thu nhỏ.';
    const viewport = document.createElement('div'); viewport.className = 'kg-map-viewport';
    const details = document.createElement('div'); details.className = 'kg-map-details';
    details.setAttribute('aria-live', 'polite');
    const hint = data.edges.length ? 'Chọn khái niệm để xem mô tả. Kéo nền để di chuyển sơ đồ.'
      : 'Chưa có quan hệ được xác thực giữa các khái niệm. Chọn một nút để xem mô tả.';
    details.textContent = hint;
    frame.append(toolbar, navigation, instruction, viewport, details); container.appendChild(frame);
    function svgElement(tag, attributes, parent) {
      const element = document.createElementNS(NS, tag);
      for (const [key, value] of Object.entries(attributes || {})) element.setAttribute(key, value);
      if (parent) parent.appendChild(element);
      return element;
    }
    const svg = svgElement('svg', { viewBox: `0 0 ${data.width} ${data.height}`, role: 'group',
      'aria-label': 'Sơ đồ khái niệm và quan hệ từ tài liệu' }, viewport);
    const markerId = `kg-arrow-${++sequence}`;
    const defs = svgElement('defs', {}, svg);
    const marker = svgElement('marker', { id: markerId, viewBox: '0 0 10 10', refX: 9, refY: 5,
      markerWidth: 6, markerHeight: 6, orient: 'auto-start-reverse' }, defs);
    svgElement('path', { d: 'M 0 0 L 10 5 L 0 10 z', fill: '#a78bfa' }, marker);
    const edgeLayer = svgElement('g', {}, svg);
    const nodeLayer = svgElement('g', {}, svg);
    for (const edge of data.edges) {
      const forward = edge.target.x > edge.source.x;
      const sx = edge.source.x + (forward ? WIDTH : 0), sy = edge.source.y + HEIGHT / 2;
      const tx = edge.target.x + (forward ? 0 : WIDTH), ty = edge.target.y + HEIGHT / 2;
      const middle = (sx + tx) / 2;
      const path = svgElement('path', { d: `M ${sx} ${sy} C ${middle} ${sy}, ${middle} ${ty}, ${tx} ${ty}`,
      fill: 'none', stroke: '#a78bfa', 'stroke-width': 2, 'marker-end': `url(#${markerId})`, class: 'kg-map-edge' }, edgeLayer);
      svgElement('title', {}, path).textContent = `${edge.source.name || edge.source.canonicalName} → ${edge.target.name || edge.target.canonicalName}: ${edge.relationType || ''}`;
      {
        const label = svgElement('text', { x: (sx + tx) / 2, y: (sy + ty) / 2 - 9,
          'text-anchor': 'middle', class: 'kg-map-edge-label' }, edgeLayer);
        label.textContent = relationNames[edge.relationType] || 'có quan hệ';
      }
    }
    let selected;
    for (const node of data.nodes) {
      const group = svgElement('g', { transform: `translate(${node.x} ${node.y})`, class: 'kg-map-node',
        role: 'button', tabindex: 0, 'aria-label': node.name || node.canonicalName || 'Khái niệm', 'aria-pressed': 'false' }, nodeLayer);
      svgElement('rect', { width: WIDTH, height: HEIGHT, rx: 17 }, group);
      const title = node.name || node.canonicalName || 'Khái niệm';
      svgElement('title', {}, group).textContent = title;
      const text = svgElement('text', { x: 18, y: 30, class: 'kg-map-title' }, group);
      // Wrap titles at word boundaries; full title remains in accessible label and details.
      const lines = [], words = title.match(/\S{1,24}/g) || [];
      for (const word of words) {
        if (!lines.length || lines[lines.length - 1].length + word.length + 1 > 24) lines.push(word);
        else lines[lines.length - 1] += ` ${word}`;
      }
      lines.slice(0, 2).forEach((line, index) => {
        svgElement('tspan', { x: 18, dy: index ? 21 : 0 }, text).textContent =
          index === 1 && lines.length > 2 ? `${line.slice(0, 21)}…` : line;
      });
      const descriptionText = svgElement('text', { x: 18, y: 80, class: 'kg-map-description' }, group);
      const descriptionWords = (node.description || 'Chọn nút để xem chi tiết.').match(/\S{1,34}/g) || [];
      const descriptionLines = [];
      for (const word of descriptionWords) {
        if (!descriptionLines.length || descriptionLines[descriptionLines.length - 1].length + word.length + 1 > 34) descriptionLines.push(word);
        else descriptionLines[descriptionLines.length - 1] += ` ${word}`;
      }
      descriptionLines.slice(0, 3).forEach((line, index) => {
        svgElement('tspan', { x: 18, dy: index ? 19 : 0 }, descriptionText).textContent =
          index === 2 && descriptionLines.length > 3 ? `${line.slice(0, 31)}…` : line;
      });
      svgElement('text', { x: 18, y: 145, class: 'kg-map-node-hint' }, group).textContent =
        node.key === data.focus.key ? 'Khái niệm trung tâm' : 'Chọn để đọc và mở nhánh';
      function select() {
        if (selected) selected.setAttribute('aria-pressed', 'false');
        selected = group; group.setAttribute('aria-pressed', 'true');
        details.replaceChildren();
        const heading = document.createElement('strong'); heading.textContent = title;
        const description = document.createElement('p'); description.textContent = node.description || 'Chưa có mô tả cho khái niệm này.';
        details.append(heading, description);
        if (Array.isArray(node.sources) && node.sources.length && global.renderCitationSources) {
          global.renderCitationSources(details, node.sources, graph.documentId);
        } else if (node.sourceCount) {
          const sources = document.createElement('p');
          sources.textContent = `Có ${node.sourceCount} đoạn tài liệu làm bằng chứng. Chưa có thông tin trang từ API.`;
          details.appendChild(sources);
        }
        if (options.onSelect) options.onSelect(node);
        const relationList = document.createElement('ul');
        for (const edge of data.edges.filter(edge => edge.source.key === node.key || edge.target.key === node.key)) {
          const item = document.createElement('li');
          item.textContent = `${edge.source.name || edge.source.canonicalName} ${relationNames[edge.relationType] || 'có quan hệ với'} ${edge.target.name || edge.target.canonicalName}.`;
          relationList.appendChild(item);
        }
        if (relationList.children.length) details.appendChild(relationList);
        const open = document.createElement('button'); open.type = 'button'; open.textContent = 'Mở nhánh này';
        open.addEventListener('click', () => render(container, graph, node.key, 0, options)); details.appendChild(open);
      }
      group.addEventListener('click', select);
      group.addEventListener('keydown', event => {
        if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); }
      });
      if (node.key === data.focus.key) select();
    }
    let view = { x: 0, y: 0, width: data.width, height: data.height }, drag;
    function update() { svg.setAttribute('viewBox', `${view.x} ${view.y} ${view.width} ${view.height}`); }
    function zoom(factor) {
      const ratio = view.width / data.width;
      if (ratio * factor < 0.08 || ratio * factor > 4) return;
      view.x += view.width * (1 - factor) / 2; view.y += view.height * (1 - factor) / 2;
      view.width *= factor; view.height *= factor; update();
    }
    function control(label, action) {
      const button = document.createElement('button'); button.type = 'button'; button.textContent = label;
      button.setAttribute('aria-label', label === '+' ? 'Phóng to' : label === '−' ? 'Thu nhỏ' : label);
      button.addEventListener('click', action); controls.appendChild(button);
    }
    control('−', () => zoom(1.25)); control('+', () => zoom(0.8));
    control('Vừa khung', () => { view = { x: 0, y: 0, width: data.width, height: data.height }; update(); });
    control(options.collapsed ? 'Mở nhánh' : 'Thu nhánh', () => render(container, graph, data.focus.key, data.page, { ...options, collapsed: !options.collapsed }));
    if (options.onRegenerate) control('Tạo lại bằng tiếng Việt', options.onRegenerate);
    if (data.pages > 1) {
      control('Nhánh trước', () => render(container, graph, data.focus.key, (data.page + data.pages - 1) % data.pages, options));
      control('Nhánh tiếp', () => render(container, graph, data.focus.key, (data.page + 1) % data.pages, options));
    }
    svg.addEventListener('wheel', event => {
      // Normal wheel movement scrolls the surrounding tool panel/page.
      if (!event.ctrlKey && !event.metaKey) return;
      event.preventDefault();
      zoom(event.deltaY > 0 ? 1.15 : 0.87);
    }, { passive: false });
    svg.addEventListener('pointerdown', event => {
      if (event.target.closest('.kg-map-node') || event.button !== 0) return;
      const ctm = svg.getScreenCTM(); if (!ctm) return;
      drag = { id: event.pointerId, x: event.clientX, y: event.clientY, viewX: view.x, viewY: view.y, scale: ctm.a };
      svg.setPointerCapture(event.pointerId);
    });
    svg.addEventListener('pointermove', event => {
      if (!drag || event.pointerId !== drag.id) return;
      view.x = drag.viewX - (event.clientX - drag.x) / drag.scale;
      view.y = drag.viewY - (event.clientY - drag.y) / drag.scale; update();
    });
    function endDrag() { drag = null; }
    svg.addEventListener('pointerup', endDrag); svg.addEventListener('pointercancel', endDrag);
  }
  global.MindMapRenderer = { render, layout, branch };
})(window);
