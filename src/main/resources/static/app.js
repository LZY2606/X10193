let schemes = [];
let current = null;
let visibleStep = Number.MAX_SAFE_INTEGER;
let timer = null;

const $ = id => document.getElementById(id);
const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

async function api(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error(await response.text());
  return response.json();
}

async function refresh(selectId) {
  schemes = await api('/api/schemes');
  for (const id of ['schemeSelect','leftScheme','rightScheme']) {
    $(id).innerHTML = schemes.map(s => `<option value="${s.id}">${esc(s.createdAt)} · ${esc(s.name)} · ${esc(s.policyName)}</option>`).join('');
  }
  if (selectId) $(selectId).value = selectId === 'schemeSelect' ? selectId : selectId;
  if (schemes.length) await loadScheme($('schemeSelect').value);
}

async function loadScheme(id) {
  current = await api(`/api/schemes/${id}`);
  visibleStep = current.events.length;
  render();
}

function render() {
  if (!current) return;
  $('schemeMeta').textContent = `${current.policyName} 策略 · ${current.modules.length} 个模块 · ${current.resolutions.length} 个符号引用`;
  renderModules();
  renderEvents();
  renderResolutions();
  renderGraph();
}

function renderModules() {
  $('modules').innerHTML = current.modules.map(module => `
    <div class="module">
      <div><span class="badge ${module.shared ? 'shared' : ''}">${esc(module.kind)}</span><strong>${esc(module.displayName)}</strong></div>
      <div class="dep">加载序 ${module.loadOrder}${module.archiveExtracted ? ' · archive 已抽取' : ''}</div>
      <div class="dep">${module.needed.length ? 'NEEDED: ' + module.needed.map(esc).join(', ') : '无动态依赖'}</div>
    </div>`).join('');
}

function renderEvents() {
  const max = current.events.length;
  visibleStep = Math.min(visibleStep, max);
  $('stepLabel').textContent = `${visibleStep} / ${max}`;
  $('events').innerHTML = current.events.map(event => `
    <li class="${event.step <= visibleStep ? '' : 'dim'}">
      <span class="badge">${event.step}</span><strong>${esc(event.title)}</strong>
      <div>${esc(event.detail)}</div><small>${esc(event.kind)} · ${esc(event.module || '')} ${event.symbol ? '· ' + esc(event.symbol) : ''}</small>
    </li>`).join('');
}

function renderResolutions() {
  $('resolutions').innerHTML = current.resolutions.map(r => `
    <tr>
      <td><strong>${esc(r.key.name)}${r.key.version ? '@' + esc(r.key.version) : ''}</strong></td>
      <td><span class="badge ${esc(r.outcome)}">${esc(r.outcome)}</span></td>
      <td>${r.selected ? esc(r.selected.module.display()) + `<div class="dep">${esc(r.selected.binding)} / ${esc(r.selected.visibility)} / ${esc(r.selected.kind)}</div>` : '—'}</td>
      <td>${r.candidates.map(c => `<div class="candidate ${c.eliminationReason === 'KEPT' ? 'kept' : 'out'}">
        <strong>${esc(c.module.display)}</strong> ${esc(c.binding)} ${esc(c.versionName ? '@' + c.versionName : '')}
        <div class="dep">${esc(c.eliminationReason)} · ${esc(c.eliminationDetail)}</div></div>`).join('')}</td>
      <td>${r.relocations.map(rel => `<div class="rel">${esc(rel.sectionName)}+0x${(rel.offset || 0).toString(16)} type=${rel.type}${rel.copy ? ' COPY' : ''}${rel.pcrel ? ' PCREL' : ''}</div>`).join('')}</td>
      <td>${esc(r.reason)}</td>
    </tr>`).join('');
}

function renderGraph() {
  const graph = current.versionGraph;
  const positions = new Map();
  const byKind = {};
  graph.nodes.forEach(n => { byKind[n.kind] = (byKind[n.kind] || 0) + 1; });
  const counters = {};
  graph.nodes.forEach((node, index) => {
    const column = node.kind === 'object' ? 0 : node.kind === 'definition' || node.kind === 'requirement' ? 1 : 2;
    counters[column] = counters[column] || 0;
    positions.set(node.id, {x: 50 + column * 250, y: 35 + counters[column]++ * 58});
  });
  const edges = graph.edges.map(edge => {
    const a = positions.get(edge.from), b = positions.get(edge.to);
    if (!a || !b) return '';
    return `<line x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}" stroke="#b9aa9b" marker-end="url(#arrow)"/>`;
  }).join('');
  const nodes = graph.nodes.map(node => {
    const p = positions.get(node.id);
    const color = node.kind === 'object' ? '#8b3f22' : node.kind.includes('version') || node.kind === 'definition' ? '#28525b' : '#806c58';
    return `<g><circle cx="${p.x}" cy="${p.y}" r="7" fill="${color}"/><text x="${p.x + 12}" y="${p.y + 4}" font-size="12">${esc(node.label)}</text></g>`;
  }).join('');
  $('versionGraph').innerHTML = `<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#b9aa9b"/></marker></defs>${edges}${nodes}`;
}

$('uploadForm').addEventListener('submit', async event => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const response = await fetch('/api/schemes', {method: 'POST', body: form});
  if (!response.ok) return alert(await response.text());
  const created = await response.json();
  await refresh();
  $('schemeSelect').value = created.id;
  await loadScheme(created.id);
});
$('sampleBtn').onclick = async () => { const s = await api('/api/sample', {method:'POST'}); await refresh(); $('schemeSelect').value = s.id; await loadScheme(s.id); };
$('schemeSelect').onchange = e => loadScheme(e.target.value);
$('prevBtn').onclick = () => { visibleStep = Math.max(1, visibleStep - 1); renderEvents(); };
$('nextBtn').onclick = () => { visibleStep = Math.min(current.events.length, visibleStep + 1); renderEvents(); };
$('playBtn').onclick = () => {
  if (timer) { clearInterval(timer); timer = null; return; }
  visibleStep = 0; renderEvents();
  timer = setInterval(() => {
    if (visibleStep >= current.events.length) { clearInterval(timer); timer = null; return; }
    visibleStep++; renderEvents();
  }, 650);
};
$('compareBtn').onclick = async () => {
  const result = await api(`/api/compare?left=${encodeURIComponent($('leftScheme').value)}&right=${encodeURIComponent($('rightScheme').value)}`);
  $('comparison').innerHTML = `<p><span class="badge">${result.changedProviders} 换提供者</span><span class="badge">${result.addressOnly} 仅地址变化</span></p>` +
    result.changes.map(change => `<div class="change ${change.change === 'address-only' ? 'address' : ''}">
      <strong>${esc(change.key.name)}${change.key.version ? '@' + esc(change.key.version) : ''}</strong> — ${esc(change.change)}
      <div>${esc(change.fromProvider || '无')} → ${esc(change.toProvider || '无')}</div><small>${esc(change.detail)}</small></div>`).join('');
};
refresh();
