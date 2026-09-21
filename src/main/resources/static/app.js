const state = { binaries: [], order: [], schemes: [], result: null, step: 0, timer: null };

const $ = (id) => document.getElementById(id);
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));

async function api(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error((await response.json()).error || response.statusText);
  return response.json();
}

async function refresh() {
  state.binaries = await api('/api/binaries');
  state.schemes = await api('/api/schemes');
  renderBinaries(); renderSchemes();
}

function renderBinaries() {
  $('binaryList').innerHTML = state.binaries.map(binary => {
    const selected = state.order.includes(binary.id);
    const badClass = binary.parseOk ? 'kind' : 'kind bad';
    return `<div class="binary ${selected ? 'selected' : ''}" data-id="${binary.id}">
      <div><strong>#${binary.id} ${escapeHtml(binary.name)}</strong><br><span class="muted">${binary.kind} · ${binary.size} bytes</span></div>
      <span class="${badClass}">${binary.parseOk ? 'OK' : '隔离: ' + escapeHtml(binary.parseError || '')}</span>
    </div>`;
  }).join('');
  document.querySelectorAll('.binary').forEach(node => node.onclick = () => {
    const id = Number(node.dataset.id);
    state.order = state.order.includes(id) ? state.order.filter(x => x !== id) : [...state.order, id];
    renderBinaries();
  });
}

function renderSchemes() {
  $('schemeList').innerHTML = state.schemes.map(scheme => `<div class="scheme" data-id="${scheme.id}">
    <div><strong>#${scheme.id} ${escapeHtml(scheme.label)}</strong><br><span class="muted">${scheme.policy} · ${scheme.orderedBinaryIds.join(' → ')}</span></div>
    <button class="secondary">查看</button>
  </div>`).join('');
  document.querySelectorAll('.scheme').forEach(node => node.onclick = () => loadResult(Number(node.dataset.id)));
}

$('upload').onclick = async () => {
  const file = $('file').files[0];
  if (!file) return;
  const form = new FormData();
  form.append('file', file);
  $('uploadStatus').textContent = '正在解析并只读保存...';
  try {
    const parsed = await api('/api/binaries', { method: 'POST', body: form });
    $('uploadStatus').textContent = `已导入 #${parsed.id}：${parsed.kind}${parsed.parseError ? '，' + parsed.parseError : ''}`;
    await refresh();
  } catch (ex) { $('uploadStatus').textContent = ex.message; }
};

$('createScheme').onclick = async () => {
  if (!state.order.length) return;
  const payload = { label: $('label').value, policy: $('policy').value, orderedBinaryIds: state.order };
  const scheme = await api('/api/schemes', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(payload) });
  await refresh();
  await loadResult(scheme.id);
};

async function loadResult(id) {
  state.result = await api(`/api/schemes/${id}/result`);
  state.step = 0;
  renderResult();
}

function renderResult() {
  const result = state.result;
  if (!result) return;
  renderGraphs(result); renderResolutions(result.resolutions); renderRelocations(result.units);
  $('stepCounter').textContent = `${state.step + 1} / ${result.steps.length}`;
  const step = result.steps[state.step];
  $('stepCard').innerHTML = `<h3>${escapeHtml(step.phase)}：${escapeHtml(step.title)}</h3>
    <p>${escapeHtml(step.detail)}</p><div class="muted">活动对象：${step.activeUnits.map(escapeHtml).join(' → ')}</div>`;
}

function renderResolutions(resolutions) {
  $('resolutionList').innerHTML = resolutions.map((resolution, index) => `
    <div class="resolution-card">
      <div class="resolution-head" onclick="document.getElementById('cand-${index}').hidden=!document.getElementById('cand-${index}').hidden">
        <div><strong>${escapeHtml(resolution.symbol)}${resolution.requestedVersion ? '@' + escapeHtml(resolution.requestedVersion) : ''}</strong>
        <span class="muted"> from ${escapeHtml(resolution.requesterName)}${resolution.copyRelocation ? ' · COPY' : ''}</span></div>
        <span class="status ${resolution.status}">${resolution.status}</span>
      </div>
      <div id="cand-${index}" class="candidates" hidden>
        <p class="muted">${escapeHtml(resolution.reason)}</p>
        ${resolution.candidates.map(candidate => `<div class="candidate ${candidate.selected ? 'selected' : ''}">
          <div><strong>${escapeHtml(candidate.unitName)}</strong> · ${candidate.binding}/${candidate.visibility} · ${candidate.versionName || 'BASE'}${candidate.defaultVersion ? ' (default)' : ''}</div>
          <div>${candidate.selected ? '胜出' : '淘汰'}</div>
          <div class="reasons">${(candidate.rejectionReasons || []).map(escapeHtml).join('；')}</div>
        </div>`).join('')}
        <div class="relocations">受影响 relocation：${resolution.affectedRelocations.map(r => `${r.typeName}@${r.offset.toString(16)}`).join(', ') || '无'}</div>
      </div>
    </div>`).join('');
}

function renderRelocations(units) {
  const rows = units.flatMap(unit => unit.relocations.map(r => `<tr>
    <td>${escapeHtml(unit.unitName)}</td><td>${escapeHtml(r.symbol)}</td><td>${escapeHtml(r.requestedVersion || '')}</td>
    <td>${r.typeName}${r.copyRelocation ? ' (COPY)' : ''}</td><td>0x${r.offset.toString(16)}</td><td>${r.hasAddend ? r.addend : ''}</td><td>${escapeHtml(r.sectionName || '')}</td>
  </tr>`));
  $('relocationList').innerHTML = rows.join('');
}

function graph(svgId, edges, label) {
  const svg = $(svgId);
  const nodes = [...new Set(edges.flatMap(edge => [edge.fromUnitId || edge.source, edge.toLibrary || edge.target]))];
  const position = Object.fromEntries(nodes.map((node, i) => [node, {x: 70 + (i % 2) * 270, y: 45 + Math.floor(i / 2) * 75}]));
  svg.innerHTML = `<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto"><path d="M0,0 L0,6 L9,3 z" fill="#667085"/></marker></defs>`
    + edges.map(edge => {
      const a = position[edge.fromUnitId || edge.source]; const b = position[edge.toLibrary || edge.target];
      if (!a || !b) return '';
      return `<line class="edge" x1="${a.x + 70}" y1="${a.y + 18}" x2="${b.x}" y2="${b.y + 18}"/>`;
    }).join('')
    + nodes.map(node => { const p = position[node]; return `<rect class="node" x="${p.x}" y="${p.y}" width="170" height="38" rx="8"/><text class="node-text" x="${p.x + 8}" y="${p.y + 24}">${escapeHtml(label(node))}</text>`; }).join('');
}

function renderGraphs(result) {
  graph('dependencyGraph', result.dependencyEdges, node => node);
  graph('versionGraph', result.versionEdges, node => node);
}

$('stepNext').onclick = () => { if (state.result && state.step < state.result.steps.length - 1) { state.step++; renderResult(); } };
$('stepBack').onclick = () => { if (state.result && state.step > 0) { state.step--; renderResult(); } };
$('stepPlay').onclick = () => {
  if (!state.result) return;
  $('stepPlay').disabled = true;
  state.timer = setInterval(() => {
    if (state.step >= state.result.steps.length - 1) { clearInterval(state.timer); $('stepPlay').disabled = false; return; }
    state.step++; renderResult();
  }, 800);
};
$('compare').onclick = async () => {
  const a = Number($('schemeA').value), b = Number($('schemeB').value);
  const comparison = await api(`/api/schemes/compare?a=${a}&b=${b}`);
  $('comparison').innerHTML = [
    comparison.changedProviders.map(change => `<div class="change">换提供者：${escapeHtml(change.symbol)} ${escapeHtml(change.before)} → ${escapeHtml(change.after)}</div>`).join(''),
    comparison.addressOnly.map(change => `<div class="change address">仅地址变化：${escapeHtml(change.symbol)} ${change.beforeAddress?.toString(16)} → ${change.afterAddress?.toString(16)}</div>`).join('')
  ].join('') || '<div class="muted">两个方案没有检测到差异</div>';
};

refresh().catch(ex => { console.error(ex); });
