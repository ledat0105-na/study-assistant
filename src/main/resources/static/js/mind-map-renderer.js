(function (global) {
  const NS = 'http://www.w3.org/2000/svg';
  const WIDTH = 280, HEIGHT = 162;
  const relationNames = { PART_OF: 'là thành phần của', IS_A: 'là một loại',
    PREREQUISITE_OF: 'là điều kiện trước của', RELATED_TO: 'liên quan đến', HAS_PART: 'bao gồm' };
  let sequence = 0;
  const preferences = new WeakMap();

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
      node.x = 50 + depth * 310; node.y = 50 + (row + (rows - level.length) / 2) * 205;
    }));
    const isolated = nodes.filter(node => !placed.has(node.key));
    isolated.forEach((node, index) => {
      node.x = 50 + (index % 3) * 310; node.y = 50 + rows * 205 + Math.floor(index / 3) * 205;
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


  function pdfFromJpeg(jpeg, width, height) {
    const encoder = new TextEncoder();
    const chunks = [], offsets = [0];
    let length = 0;
    function append(value) {
      const bytes = typeof value === 'string' ? encoder.encode(value) : value;
      chunks.push(bytes);
      length += bytes.byteLength;
    }
    function object(id, ...parts) {
      offsets[id] = length;
      append(id + ' 0 obj\n');
      parts.forEach(append);
      append('\nendobj\n');
    }
    const scale = 780 / Math.max(width, height);
    const w = (width * scale).toFixed(2), h = (height * scale).toFixed(2);
    const drawing = 'q ' + w + ' 0 0 ' + h + ' 0 0 cm /Map Do Q\n';
    append('%PDF-1.4\n');
    object(1, '<< /Type /Catalog /Pages 2 0 R >>');
    object(2, '<< /Type /Pages /Kids [3 0 R] /Count 1 >>');
    object(3, '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ' + w + ' ' + h
      + '] /Resources << /XObject << /Map 4 0 R >> >> /Contents 5 0 R >>');
    object(4, '<< /Type /XObject /Subtype /Image /Width ' + width + ' /Height ' + height
      + ' /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length '
      + jpeg.byteLength + ' >>\nstream\n', jpeg, '\nendstream');
    object(5, '<< /Length ' + drawing.length + ' >>\nstream\n' + drawing + 'endstream');
    const xref = length;
    append('xref\n0 6\n0000000000 65535 f \n');
    offsets.slice(1).forEach(offset => append(String(offset).padStart(10, '0') + ' 00000 n \n'));
    append('trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF\n');
    return new Blob(chunks, { type: 'application/pdf' });
  }

  async function downloadMap(svg, data, format) {
    const copy = svg.cloneNode(true);
    const properties = ['fill', 'stroke', 'stroke-width', 'stroke-linejoin', 'paint-order',
      'font-family', 'font-size', 'font-weight'];
    const originals = svg.querySelectorAll('rect, path, text');
    copy.querySelectorAll('rect, path, text').forEach((element, index) => {
      const style = global.getComputedStyle(originals[index]);
      properties.forEach(property => element.style.setProperty(property, style.getPropertyValue(property)));
    });
    copy.setAttribute('viewBox', '0 0 ' + data.width + ' ' + data.height);
    copy.setAttribute('width', data.width);
    copy.setAttribute('height', data.height);
    const scale = Math.min(3, 3840 / Math.max(data.width, data.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(data.width * scale);
    canvas.height = Math.round(data.height * scale);
    const background = global.getComputedStyle(svg.closest('.kg-map')).backgroundColor;
    const source = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(copy)],
      { type: 'image/svg+xml;charset=utf-8' }));
    try {
      const mapImage = new Image();
      mapImage.src = source;
      await mapImage.decode();
      const context = canvas.getContext('2d');
      context.fillStyle = background;
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(mapImage, 0, 0, canvas.width, canvas.height);
    } finally {
      URL.revokeObjectURL(source);
    }
    const image = await new Promise((resolve, reject) => {
      canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Image export failed')),
        format === 'png' ? 'image/png' : 'image/jpeg', 0.94);
    });
    const file = format === 'pdf'
      ? pdfFromJpeg(new Uint8Array(await image.arrayBuffer()), canvas.width, canvas.height) : image;
    const url = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'so-do-tu-duy.' + format;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  function render(container, graph, focusId, page = 0, options = {}) {
    container.replaceChildren();
    const settings = preferences.get(container) || { theme: 'dark', expanded: false };
    preferences.set(container, settings);
    const data = options.exportLayout || branch(graph, focusId, page);
    if (options.collapsed && data.focus) {
      data.nodes = [data.focus]; data.edges = []; data.focus.x = 20; data.focus.y = 35; data.width = 320; data.height = 240;
    }
    if (!data.nodes.length) { container.textContent = 'Chưa có khái niệm trong tài liệu.'; return; }
    const frame = document.createElement('div'); frame.className = 'kg-map';
    frame.setAttribute('data-theme', settings.theme);
    if (settings.expanded) frame.className += ' kg-map-expanded';
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
    if (!options.exportLayout) populate();
    search.addEventListener('input', () => populate(search.value));
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
    const divider = document.createElement('div');
    divider.className = 'kg-map-divider';
    divider.tabIndex = 0;
    divider.setAttribute('role', 'separator');
    divider.setAttribute('aria-label', 'Điều chỉnh chiều cao phần mô tả');
    divider.setAttribute('aria-orientation', 'horizontal');
    let resize;
    function resizeDetails(height) {
      const max = Math.max(44, Math.min(480, Math.floor(global.innerHeight * 0.55)));
      settings.detailsHeight = Math.max(44, Math.min(max, Math.round(height)));
      details.style.setProperty('--kg-details-height', settings.detailsHeight + 'px');
      divider.setAttribute('aria-valuemin', '44');
      divider.setAttribute('aria-valuemax', String(max));
      divider.setAttribute('aria-valuenow', String(settings.detailsHeight));
    }
    divider.setAttribute('aria-valuemin', '44');
    divider.setAttribute('aria-valuemax', '480');
    divider.setAttribute('aria-valuenow', String(settings.detailsHeight ?? 96));
    if (settings.detailsHeight != null) resizeDetails(settings.detailsHeight);
    divider.addEventListener('pointerdown', event => {
      if (event.button !== 0) return;
      resize = { id: event.pointerId, y: event.clientY, height: details.offsetHeight };
      divider.setPointerCapture(event.pointerId);
    });
    divider.addEventListener('pointermove', event => {
      if (resize?.id === event.pointerId) resizeDetails(resize.height + resize.y - event.clientY);
    });
    divider.addEventListener('lostpointercapture', () => { resize = null; });
    divider.addEventListener('keydown', event => {
      if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
      event.preventDefault();
      resizeDetails(details.offsetHeight + (event.key === 'ArrowUp' ? 24 : -24));
    });
    frame.append(toolbar, navigation, instruction, viewport, divider, details); container.appendChild(frame);
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
      if (!options.exportLayout && node.key === data.focus.key) select();
    }
    if (options.exportLayout) return svg;
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
      return button;
    }

    const expand = control('⛶', () => {
      const expanded = settings.expanded = !settings.expanded;
      frame.className = expanded ? 'kg-map kg-map-expanded' : 'kg-map';
      if (frame.showPopover) {
        if (expanded) {
          frame.setAttribute('popover', 'manual');
          frame.showPopover();
        } else {
          frame.hidePopover();
          frame.removeAttribute('popover');
        }
      }
      expand.focus();
      expand.textContent = expanded ? '↙' : '⛶';
      expand.setAttribute('aria-pressed', String(expanded));
    });
    expand.setAttribute('aria-label', 'Mở rộng sơ đồ');
    expand.title = 'Mở rộng hoặc thu gọn sơ đồ';
    expand.setAttribute('aria-pressed', String(settings.expanded));
    if (settings.expanded) {
      expand.textContent = '↙';
      if (frame.showPopover) {
        frame.setAttribute('popover', 'manual');
        frame.showPopover();
        expand.focus();
      }
    }
    frame.addEventListener('keydown', event => {
      if (event.key === 'Escape' && settings.expanded) expand.click();
    });

    const exportControls = document.createElement('div');
    exportControls.className = 'kg-map-controls kg-map-export';
    navigation.appendChild(exportControls);
    function dropdown(label, choices, selectedValue) {
      const select = document.createElement('select');
      select.setAttribute('aria-label', label);
      for (const [value, name] of choices) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = name;
        select.appendChild(option);
      }
      select.value = selectedValue;
      exportControls.appendChild(select);
      return select;
    }
    const theme = dropdown('Giao diện sơ đồ', [['dark', 'Tối'], ['light', 'Sáng'], ['ocean', 'Xanh biển']],
      settings.theme);
    theme.addEventListener('change', () => {
      settings.theme = theme.value;
      frame.setAttribute('data-theme', theme.value);
    });
    const format = dropdown('Định dạng tải sơ đồ', [['pdf', 'PDF'], ['png', 'PNG'], ['jpg', 'JPG']], 'pdf');
    const download = control('Tải sơ đồ', async () => {
      download.disabled = true;
      const exportHost = document.createElement('div');
      exportHost.style.cssText = 'position:fixed;left:-10000px;top:0;visibility:hidden';
      document.body.appendChild(exportHost);
      try {
        const exportLayout = layout(graph);
        exportLayout.all = exportLayout;
        exportLayout.focus = exportLayout.nodes.find(node => node.key === data.focus.key);
        exportLayout.page = 0;
        exportLayout.pages = 1;
        preferences.set(exportHost, { theme: settings.theme, expanded: false });
        const exportSvg = render(exportHost, graph, undefined, 0, { exportLayout });
        await downloadMap(exportSvg, exportLayout, format.value);
      } catch (_) {
        details.textContent = 'Không thể tải sơ đồ. Vui lòng thử lại.';
      } finally {
        exportHost.remove();
        download.disabled = false;
      }
    });
    exportControls.appendChild(download);
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
