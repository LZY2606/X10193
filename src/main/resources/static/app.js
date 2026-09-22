let objects = [];
let order = [];
let plans = [];
let currentPlanId = null;
let report = null;
let playIndex = 0;

const api = async (path, opts) => {
  const r = await fetch(path, opts);
  if (!r.ok) throw new Error(await r.text());
  return r.json();
};

async function init() {
  objects = await api('/api/objects');
  plans = await api('/api/plans');
  renderObjects();
  renderPlans();
}

async function uploadFiles() {
  const files = document.getElementById('fileInput').files;
  for (const f of files) {
    const fd = new FormData();
    fd.append('file', f);
    await api('/api/objects', { method: 'POST', body: fd });
  }
  objects = await api('/api/objects');
  renderObjects();
}

function renderObjects() {
  const el = document.getElementById('objectList');
  el.innerHTML = '';
  if (!objects.length) {
    el.innerHTML = '<div class="muted">尚无对象</div>';
    return;
  }
  objects.forEach(o => {
    const div = document.createElement('div');
    div.className = 'obj-item';
    const inOrder = order.some(e => e.sha === o.sha256);
    div.innerHTML = `<b>${o.note || o.filename || o.sha256.slice(0, 12)}</b>`
      + `<div class="muted">${o.kind} · ${o.sha256.slice(0, 16)}…</div>`;
    if (!inOrder) {
      div.style.borderColor = 'var(--accent)';
      div.onclick = () => { order.push({ sha: o.sha256, label: o.note || o.sha256.slice(0, 10) }); renderObjects(); renderOrder(); };
    } else {
      div.classList.add('dim');
    }
    el.appendChild(div);
  });
}

function renderOrder() {
  const el = document.getElementById('orderList');
  el.innerHTML = '';
  order.forEach((e, i) => {
    const div = document.createElement('div');
    div.className = 'obj-item active';
    div.innerHTML = `<span class="pill">${i + 1}</span> ${e.label}`
      + ` <a onclick="moveOrder(${i},-1)" style="cursor:pointer">↑</a>`
      + ` <a onclick="moveOrder(${i},1)" style="cursor:pointer">↓</a>`
      + ` <a onclick="removeOrder(${i})" style="cursor:pointer;color:var(--bad)">✕</a>`;
    el.appendChild(div);
  });
}
function moveOrder(i, d) {
  const j = i + d;
  if (j < 0 || j >= order.length) return;
  [order[i], order[j]] = [order[j], order[i]];
  renderObjects(); renderOrder();
}
function removeOrder(i) { order.splice(i, 1); renderObjects(); renderOrder(); }
function clearOrder() { order = []; renderObjects(); renderOrder(); }

async function createPlan() {
  if (!order.length) { alert('请先把对象加入加载顺序'); return; }
  const body = {
    name: document.getElementById('planName').value || 'demo',
    targetPlatform: document.getElementById('platform').value,
    linkModel: document.getElementById('linkModel').value,
    tiePolicy: document.getElementById('tiePolicy').value,
    allowInterposition: document.getElementById('interp').checked,
    entries: order.map((e, i) => ({ sha256: e.sha, order: i, label: e.label }))
  };
  const created = await api('/api/plans', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  currentPlanId = created.planId;
  report = created.report;
  plans = await api('/api/plans');
  renderPlans();
  showReport();
}

function renderPlans() {
  const el = document.getElementById('planList');
  el.innerHTML = '';
  plans.forEach(p => {
    const div = document.createElement('div');
    div.className = 'obj-item' + (p.id === currentPlanId ? ' active' : '');
    div.innerHTML = `<b>${p.name}</b> <span class="muted">v${p.latestVersion}</span>
      <div class="muted">${p.targetPlatform}</div>`;
    div.onclick = () => loadPlan(p.id);
    el.appendChild(div);
  });
}

async function loadPlan(id) {
  currentPlanId = id;
  const vs = await api(`/api/plans/${id}/versions`);
  const v = vs[vs.length - 1];
  report = await api(`/api/plans/${id}/versions/${v.version}`);
  renderPlans();
  populateCompare(vs);
  showReport();
}

function populateCompare(vs) {
  const a = document.getElementById('cmpA');
  const b = document.getElementById('cmpB');
  a.innerHTML = b.innerHTML = '';
  vs.forEach(v => {
    a.innerHTML += `<option value="${v.version}">v${v.version} ${v.linkModel}/${v.tiePolicy}</option>`;
    b.innerHTML += `<option value="${v.version}">v${v.version} ${v.linkModel}/${v.tiePolicy}</option>`;
  });
  if (vs.length > 1) a.value = vs[0].version;
  if (vs.length) b.value = vs[vs.length - 1].version;
}

function showTab(name) {
  ['overview','deps','symbols','versions','relocs','player','compare'].forEach(t => {
    document.getElementById('tab-' + t).classList.toggle('hidden-view', t !== name);
  });
  document.querySelectorAll('.tabs button').forEach(b =>
    b.classList.toggle('active', b.dataset.tab === name));
  if (name === 'player') resetPlayer();
}

function showReport() {
  document.getElementById('emptyHint').classList.add('hidden-view');
  document.getElementById('reportArea').classList.remove('hidden-view');
  renderSummary();
  renderDeps();
  renderDecisions();
  renderVersions();
  renderRelocs();
  resetPlayer();
  api(`/api/plans/${currentPlanId}/versions`).then(populateCompare);
}

function esc(s) {
  return String(s ?? '').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
}

function bindingPills(sym) {
  let cls = 'local';
  if (sym.binding === 1) cls = 'strong';
  if (sym.binding === 2) cls = 'weak';
  if (sym.common) cls = 'common';
  let vis = '';
  if (sym.visibility === 2) vis = '<span class="pill hidden">HIDDEN</span>';
  else if (sym.visibility === 3) vis = '<span class="pill protected">PROTECTED</span>';
  return `<span class="pill ${cls}">${sym.bindingName}</span>`
    + (sym.common ? '<span class="pill common">COMMON</span>' : '') + vis
    + (sym.version && sym.version.flavor && sym.version.flavor !== 'NONE' && sym.version.flavor !== 'GLOBAL'
        ? `<span class="pill">${esc(sym.version.display ? sym.version.display() : sym.version.name)}</span>` : '');
}

function renderSummary() {
  const d = report.decisions;
  const amb = d.filter(x => x.status === 'AMBIGUOUS').length;
  const unres = d.filter(x => x.status === 'UNRESOLVED').length;
  const common = d.filter(x => x.status === 'COMMON_MERGE').length;
  document.getElementById('summary').innerHTML = `
    <div>方案 <b>${report.planId}</b> · 版本 <b>v${report.version}</b>
      · ${esc(report.targetPlatform)} · ${report.policy.linkModel} / ${report.policy.tiePolicy}</div>
    <div style="margin-top:6px">
      <span class="badge ok">${d.length} 引用</span>
      <span class="badge ${amb ? 'err':'ok'}">${amb} 歧义</span>
      <span class="badge ${unres ? 'err':'ok'}">${unres} 未解析</span>
      <span class="badge ok">${common} COMMON 合并</span>
      <span class="badge ok">${report.steps.length} 个播放步骤</span>
    </div>`;
  const pe = document.getElementById('parseErrors');
  pe.innerHTML = report.parseErrors.length
    ? '<h2 style="margin-top:12px">损坏对象隔离（仅自身）</h2>'
      + report.parseErrors.map(e => `<div class="badge err">${esc(e)}</div>`).join('')
    : '';
}

function candSummary(c) {
  return `<b>${esc(c.candidate.providerLabel)}</b>
    ${bindingPills(c.candidate.symbol)}
    <span class="muted">shndx=${c.candidate.symbol.shndx} size=${c.candidate.symbol.size}
    顺序#${c.candidate.loadOrder}${c.candidate.archiveMember ? ' (归档成员)' : ''}</span>`;
}

function decisionCard(dec) {
  const ref = dec.reference;
  const relInfo = ref.relocations.length
    ? `${ref.relocations.length} 个重定位 (${esc(ref.relocations[0].typeName)})`
    : '无重定位（动态符号）';
  const rows = dec.candidates.map(c => {
    const tag = c.state === 'WINNER' ? '<span class="tag-win">✔ 选中</span>'
      : c.state === 'TIE' ? '<span class="tag-tie">≡ 同等候选</span>'
      : '<span class="tag-rej">✗ 淘汰</span>';
    return `<div class="cand-row">${tag}<div style="flex:1">${candSummary(c)}
        <div class="muted">${esc(c.reason)}</div></div></div>`;
  }).join('');
  const tieNote = dec.status === 'AMBIGUOUS'
    ? `<div class="muted">同等候选: ${dec.ties.map(t => esc(t.providerLabel)).join('、')} —— 保留歧义</div>` : '';
  return `<details class="decision">
    <summary>
      <span class="status-${dec.status}">●</span>
      <b>${esc(ref.symbol)}</b>${ref.version ? ' <span class="pill">'+esc(ref.version)+'</span>' : ''}
      ${ref.weakRef ? '<span class="pill weak">weak ref</span>' : ''}
      ${ref.copyReloc ? '<span class="pill common">COPY reloc</span>' : ''}
      <span class="muted">— 引用自 ${esc(ref.providerLabel)} (${esc(relInfo)})</span>
      <span class="badge ${dec.status==='UNRESOLVED'?'err':'ok'}">${dec.status}</span>
      <span class="muted">→ ${dec.winner ? esc(dec.winner.providerLabel) : '无'}</span>
    </summary>
    <div class="cand-row" style="border-top:1px solid var(--line)">
      <div style="flex:1"><b>裁决理由</b><div class="muted">${esc(dec.rationale)}</div>${tieNote}
      ${dec.simulatedAddress >= 0 ? `<div class="muted">模拟地址: 0x${(dec.simulatedAddress>>>0).toString(16)}</div>`:''}
      </div>
    </div>
    ${rows || '<div class="cand-row muted">无候选</div>'}
  </details>`;
}

function renderDecisions() {
  const q = (document.getElementById('symFilter')?.value || '').toLowerCase();
  const list = report.decisions.filter(d => {
    if (!q) return true;
    const hay = [d.reference.symbol, d.reference.providerLabel, d.status,
      d.winner ? d.winner.providerLabel : ''].join(' ').toLowerCase();
    return hay.includes(q);
  });
  document.getElementById('decisionList').innerHTML =
    `<table><thead><tr><th>引用</th><th>来源</th><th>状态</th><th>提供者</th></tr></thead><tbody>`
    + list.map(d => `<tr>
        <td><b>${esc(d.reference.symbol)}</b>${d.reference.version ? ' @'+esc(d.reference.version):''}</td>
        <td>${esc(d.reference.providerLabel)}</td>
        <td class="status-${d.status}">${d.status}</td>
        <td>${d.winner ? esc(d.winner.providerLabel) : '<span class="muted">—</span>'}</td>
      </tr>`).join('') + '</tbody></table>';
  document.getElementById('symbolDecisions').innerHTML =
    list.map(decisionCard).join('') || '<div class="muted">无匹配</div>';
}

function renderDeps() {
  const el = document.getElementById('depsGraph');
  el.innerHTML = report.dependencyGraph.map(n => {
    const needs = (n.needed || []).map(x => `<div class="arrow">↳ DT_NEEDED ${esc(x)}</div>`).join('');
    return `<div class="graph-node">
      <span class="pill">${n.kind}</span> <b>${esc(n.label)}</b>
      <span class="muted">#${n.order} ${n.id}</span>
      ${n.extracted ? '' : '<span class="badge err">未抽取</span>'}
      ${n.archiveOf ? `<span class="muted"> 来自归档 ${esc(n.archiveOf)}</span>`:''}
      ${needs}
    </div>`;
  }).join('') || '<div class="muted">无已载入对象</div>';
}

function renderVersions() {
  const el = document.getElementById('versionGraph');
  if (!report.versionGraph.length) {
    el.innerHTML = '<div class="muted">这些对象没有版本节区(.gnu.version*)</div>';
    return;
  }
  el.innerHTML = report.versionGraph.map(g => `<div class="graph-node">
    <b>${esc(g.providerLabel)}</b>
    ${g.definitions.map(d => `<div class="arrow">定义 v${d.index} <b>${esc(d.name)}</b>
      ${d.base?'<span class="pill">BASE</span>':''}
      ${d.aliases.length?'别名 '+d.aliases.map(esc).join(', '):''}</div>`).join('')}
    ${g.needs.map(n => `<div class="arrow">需求 v${n.index} <b>${esc(n.name)}</b>
      <span class="muted">from ${esc(n.fromSoname)}${n.weak?' (weak)':''}</span></div>`).join('')}
  </div>`).join('');
}

function renderRelocs() {
  const rows = report.decisions.flatMap(d =>
    d.reference.relocations.map(r => ({ d, r })));
  document.getElementById('relocList').innerHTML =
    `<table><thead><tr><th>类型</th><th>符号</th><th>节区</th><th>offset</th>
      <th>addend</th><th>裁决提供者</th></tr></thead><tbody>`
    + rows.map(({d,r}) => `<tr>
      <td>${esc(r.typeName)}${d.reference.copyReloc?' <span class="pill common">COPY</span>':''}</td>
      <td>${esc(d.reference.symbol)}</td>
      <td>${esc(r.section || d.reference.requestedBy)}</td>
      <td>0x${(r.offset>>>0).toString(16)}</td>
      <td>${r.addend}</td>
      <td>${d.winner ? esc(d.winner.providerLabel) : '<span class="status-UNRESOLVED">未解析</span>'}</td>
    </tr>`).join('') + '</tbody></table>'
    + '<div class="muted" style="margin-top:8px">COPY relocation 以独立语义处理（拷贝到可执行对象），不被普通覆盖规则吞并。</div>';
}

// --------------------------------------------------------------- step player

function resetPlayer() {
  playIndex = 0;
  renderPlayer();
}
function playStep(d) {
  playIndex = Math.max(0, Math.min(report.steps.length - 1, playIndex + d));
  renderPlayer();
}
function playToEnd() { playIndex = report.steps.length - 1; renderPlayer(); }

function renderPlayer() {
  const steps = report.steps;
  document.getElementById('stepPos').textContent =
    `步骤 ${playIndex + 1} / ${steps.length}`;
  const s = steps[playIndex];
  const kindName = { SCAN:'载入对象', ARCHIVE_EXTRACT:'归档惰性抽取', COMDAT:'COMDAT 去重',
    DECISION:'符号裁决', FINISH:'完成' }[s.kind] || s.kind;
  document.getElementById('stepDetail').innerHTML =
    `<div><span class="pill">${kindName}</span> <b>${esc(s.title)}</b></div>
     <div class="muted">${esc(s.detail)}</div>`;
  const active = s.activeProviders;
  let html = '<h2>当前已载入/抽取的提供者</h2>';
  html += report.dependencyGraph.map(n => {
    const inSet = active ? active.includes(n.id) : n.extracted;
    return `<div class="graph-node ${inSet?'':'dim'}">
      <span class="pill">${n.kind}</span> ${esc(n.label)}
      ${n.archiveOf ? '<span class="muted">抽取自 '+esc(n.archiveOf)+'</span>':''}
    </div>`;
  }).join('');
  if (s.tableCounts) {
    html += '<h2 style="margin-top:10px">符号表(对外定义计数)</h2><div>';
    html += Object.entries(s.tableCounts).map(([k,v]) =>
      `<span class="pill">${esc(k)}: ${v}</span>`).join(' ');
    html += '</div>';
  }
  if (s.decision) {
    html += '<h2 style="margin-top:10px">本步裁决</h2>' + decisionCard(s.decision);
  }
  document.getElementById('playState').innerHTML = html;
}

// --------------------------------------------------------------- comparison

async function runCompare() {
  const a = document.getElementById('cmpA').value;
  const b = document.getElementById('cmpB').value;
  const diff = await api(`/api/plans/${currentPlanId}/compare?a=${a}&b=${b}`);
  const color = { PROVIDER_CHANGED:'var(--bad)', ADDRESS_ONLY:'var(--warn)',
    RESOLUTION_CHANGED:'var(--tie)', NEW_REFERENCE:'var(--accent)',
    REMOVED_REFERENCE:'var(--muted)' };
  const label = { PROVIDER_CHANGED:'换了提供者', ADDRESS_ONLY:'仅地址变化',
    RESOLUTION_CHANGED:'解析状态变化', NEW_REFERENCE:'新增引用',
    REMOVED_REFERENCE:'引用消失' };
  document.getElementById('compareResult').innerHTML = `
    <div>v${a} → v${b}：
      <span class="badge err">${diff.providerChanged} 换提供者</span>
      <span class="badge ok">${diff.addressOnly} 仅地址</span>
      <span class="badge err">${diff.resolutionChanged} 状态变化</span>
      <span class="badge ok">+${diff.newReferences} / -${diff.removedReferences}</span>
    </div>
    <table style="margin-top:10px"><thead><tr><th>类别</th><th>符号</th><th>来源对象</th>
      <th>v${a} 提供者</th><th>v${b} 提供者</th></tr></thead><tbody>`
    + diff.changes.map(c => `<tr>
      <td style="color:${color[c.kind]}">${label[c.kind]}</td>
      <td><b>${esc(c.symbol)}</b>${c.requestedVersion?' @'+esc(c.requestedVersion):''}</td>
      <td>${esc(c.sourceProvider)}</td>
      <td>${esc(c.fromProvider || c.fromStatus || '')}
        ${c.fromAddress>=0?'<span class="muted"> 0x'+(c.fromAddress>>>0).toString(16)+'</span>':''}</td>
      <td>${esc(c.toProvider || c.toStatus || '')}
        ${c.toAddress>=0?'<span class="muted"> 0x'+(c.toAddress>>>0).toString(16)+'</span>':''}</td>
    </tr>`).join('') + '</tbody></table>'
    + (diff.changes.length === 0 ? '<div class="muted">两个版本的裁决完全一致（提供者与地址均未变）。</div>' : '');
}

init();
