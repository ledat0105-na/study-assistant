const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/mind-map-renderer.js'), 'utf8');
class Element {
  constructor(tag) { this.tag = tag; this.children = []; this.attrs = {}; this.events = {}; }
  setAttribute(key, value) { this.attrs[key] = String(value); }
  appendChild(child) { this.children.push(child); return child; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.children = children; }
  addEventListener(key, callback) { this.events[key] = callback; }
}
function setup() {
  const context = vm.createContext({ window: {}, document: {
    createElement: tag => new Element(tag), createElementNS: (_, tag) => new Element(tag),
  } });
  vm.runInContext(source, context);
  return context.window.MindMapRenderer;
}
function descendants(element, tag) {
  return element.children.flatMap(child => [ ...(child.tag === tag ? [child] : []), ...descendants(child, tag) ]);
}
const graph = { nodes: [{ id: 1, name: 'Activity', description: '<img src=x onerror=alert(1)>' },
  { conceptId: '2', name: 'onCreate()' }, { id: 3, name: 'Service' }],
  edges: [{ sourceConceptId: '1', targetConceptId: 2, relationType: 'HAS_PART' }] };
test('graph layout keeps all nodes, string/number IDs and only grounded edges', () => {
  const data = setup().layout({ ...graph, edges: [...graph.edges, { sourceConceptId: 1, targetConceptId: 999 }] });
  assert.equal(data.nodes.length, 3); assert.equal(data.edges.length, 1);
  assert.ok(data.nodes.find(node => node.key === '2').x > data.nodes.find(node => node.key === '1').x);
  assert.equal(new Set(data.nodes.map(node => `${node.x}:${node.y}`)).size, 3);
});
test('cyclic graph terminates and places each concept once', () => {
  const data = setup().layout({ nodes: graph.nodes, edges: [
    { sourceConceptId: 1, targetConceptId: 2 }, { sourceConceptId: 2, targetConceptId: 1 }] });
  assert.equal(data.nodes.length, 3); assert.equal(new Set(data.nodes.map(node => `${node.x}:${node.y}`)).size, 3);
});
test('renderer draws arrows, selectable nodes and safe description text', () => {
  const container = new Element('div'); setup().render(container, graph);
  assert.equal(descendants(container, 'marker').length, 1);
  const nodes = descendants(container, 'g').filter(node => node.attrs.role === 'button');
  assert.equal(nodes.length, 2);
  nodes[0].events.click();
  assert.equal(nodes[0].attrs['aria-pressed'], 'true');
  assert.ok(descendants(container, 'p').some(item => item.textContent === graph.nodes[0].description));
  assert.equal(descendants(container, 'img').length, 0);
  nodes[1].events.keydown({ key: 'Enter', preventDefault() {} });
  assert.equal(nodes[0].attrs['aria-pressed'], 'false'); assert.equal(nodes[1].attrs['aria-pressed'], 'true');
});
test('zoom and fit controls change then restore the viewBox', () => {
  const container = new Element('div'); setup().render(container, graph);
  const svg = descendants(container, 'svg')[0], original = svg.attrs.viewBox;
  const buttons = descendants(container, 'button');
  buttons.find(button => button.textContent === '+').events.click();
  assert.notEqual(svg.attrs.viewBox, original);
  buttons.find(button => button.textContent === 'Vừa khung').events.click();
  assert.equal(svg.attrs.viewBox, original);
});
test('isolated nodes do not receive fabricated relation paths', () => {
  const container = new Element('div'); setup().render(container, { nodes: graph.nodes, edges: [] });
  assert.equal(descendants(container, 'path').filter(item => item.attrs.class === 'kg-map-edge').length, 0);
});

test('dragging canvas updates viewBox and releasing ends the drag', () => {
  const container = new Element('div'); setup().render(container, graph);
  const svg = descendants(container, 'svg')[0];
  svg.getScreenCTM = () => ({ a: 1 }); svg.setPointerCapture = () => {};
  const original = svg.attrs.viewBox;
  svg.events.pointerdown({ target: { closest: () => null }, button: 0, pointerId: 1, clientX: 10, clientY: 10 });
  svg.events.pointermove({ pointerId: 1, clientX: 40, clientY: 50 });
  assert.notEqual(svg.attrs.viewBox, original);
  const moved = svg.attrs.viewBox;
  svg.events.pointerup();
  svg.events.pointermove({ pointerId: 1, clientX: 90, clientY: 90 });
  assert.equal(svg.attrs.viewBox, moved);
});

test('large graph shows a bounded branch and pagination reaches all neighbors', () => {
  const renderer = setup();
  const large = { nodes: Array.from({ length: 56 }, (_, id) => ({ id, name: `Concept ${id}` })),
    edges: Array.from({ length: 54 }, (_, index) => ({ sourceConceptId: 0, targetConceptId: index + 1, relationType: 'PART_OF' })) };
  const visited = new Set();
  for (let page = 0; page < 14; page++) {
    const data = renderer.branch(large, 0, page);
    assert.ok(data.nodes.length <= 5); assert.ok(data.edges.length <= 4);
    data.nodes.forEach(node => visited.add(node.key));
  }
  assert.equal(visited.size, 55);
  assert.equal(renderer.branch(large, 55).nodes.length, 1);
});

test('branch details use Vietnamese relation sentences and opening a branch changes focus', () => {
  const container = new Element('div'), renderer = setup();
  const data = { ...graph, edges: [{ sourceConceptId: 1, targetConceptId: 2, relationType: 'PART_OF' }] };
  renderer.render(container, data);
  assert.ok(descendants(container, 'li')[0].textContent.includes('là thành phần của'));
  let nodes = descendants(container, 'g').filter(node => node.attrs.role === 'button');
  nodes[1].events.click();
  descendants(container, 'button').find(button => button.textContent === 'Mở nhánh này').events.click();
  const picker = descendants(container, 'select')[0];
  assert.equal(picker.value, '2');
  assert.ok(descendants(container, 'tspan').some(text => text.textContent));
});

test('Vietnamese regeneration action survives branch navigation', () => {
  const container = new Element('div'); let calls = 0;
  setup().render(container, graph, undefined, 0, { onRegenerate: () => { calls++; } });
  const nodes = descendants(container, 'g').filter(node => node.attrs.role === 'button');
  nodes[1].events.click();
  descendants(container, 'button').find(button => button.textContent === 'Mở nhánh này').events.click();
  descendants(container, 'button').find(button => button.textContent === 'Tạo lại bằng tiếng Việt').events.click();
  assert.equal(calls, 1);
});

test('normal wheel scrolls the page while Ctrl wheel zooms the map', () => {
  const container = new Element('div');
  setup().render(container, graph);
  const svg = descendants(container, 'svg')[0];
  const before = svg.attrs.viewBox;
  let prevented = false;
  svg.events.wheel({ deltaY: 100, preventDefault() { prevented = true; } });
  assert.equal(prevented, false);
  assert.equal(svg.attrs.viewBox, before);
  svg.events.wheel({ deltaY: 100, ctrlKey: true, preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.notEqual(svg.attrs.viewBox, before);
});
