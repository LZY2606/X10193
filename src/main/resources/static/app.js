'use strict';
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
let currentReport = null;
let currentSchemeId = null;
let selectedFiles = [];
let playIndex = 0, playTimer = null;

async function api(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) {
    let msg = res.statusText;
    try { msg = (await res.json()).error || msg; } catch (e) {}
    throw new Error(msg);
  }
  return res.status === 204 ? null : res.json();
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"]/g, c =>
    ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
}
function addr(a) { return a == null ? '—' : '0x' + Number(a).toString(16); }
function outcomeBadge(o) {
  const map = {
    RESOLVED: ['good', '已裁决'], UNRESOLVED: ['bad', '未定义'],
    AMBIGUOUS: ['bad', '歧义'], UNMET_VERSION: ['warn', '版本未满足']
  };
  const [cls, label] = map[o] || ['muted', o];
  return `<span class="badge ${cls}">${label}</span>`;
}

// ---- file ordering -----------------------------------------------------
$('#fileInput').addEventListener('change', e => {
  selectedFiles = Array.from(e.target.files);
  renderFileOrder();
});
function renderFileOrder() {
  const ol = $('#fileOrder');
  ol.innerHTML = '';
  selectedFiles.forEach((f, i) => {
    const li = document.createElement('li');
    li.textContent = `${i + 1}. ${f.name}`;
    const up = document.createElement('button');
    up.textContent = '↑';
    up.onclick = () => { if (i > 0) { [selectedFiles[i - 1], selectedFiles[i]] = [selectedFiles[i], selectedFiles[i - 1]]; renderFileOrder(); } };
    const dn = document.createElement('button');
    dn.textContent = '↓';
    dn.onclick = () => { if (i < selectedFiles.length - 1) { [selectedFiles[i + 1], selectedFiles[i]] = [selectedFiles[i], selectedFiles[i + 1]]; renderFileOrder(); } };
    li.append(up, dn);
    ol.appendChild(li);
  });
}

// ---- upload ------------------------------------------------------------
$('#uploadForm').addEventListener('submit', async e => {
  e.preventDefault();
  const fd = new FormData(e.target);
  const files = $('#fileInput').files;
  // re-append files in displayed order
  const dt = new DataTransfer();
  selectedFiles.forEach(f => dt.items.add(f));
  const order = selectedFiles.map(f => f.name);
  const payload = new FormData();
  payload.set('name', fd.get('name'));
  payload.set('machine', fd.get('machine'));
  payload.set('archivePolicy', fd.get('archivePolicy'));
  payload.set('tiePolicy', fd.get('tiePolicy'));
  Array.from(dt.files).forEach(f => payload.append('files', f));
  order.forEach(o => payload.append('order', o));
  try {
    const r = await api('/api/schemes', { method: 'POST', body: payload });
    await refreshSchemes();
    await openScheme(r.id);
  } catch (err) { alert('裁决失败：' + err.message); }
});

async function runDemo(which) {
  try {
    const r = await api('/api/demo/' + which, { method: 'POST' });
    await refreshSchemes();
    await openScheme(r.id);
  } catch (err) { alert('演示载入失败：' + err.message); }
}
$('#btnDemoA').onclick = () => runDemo('A');
$('#btnDemoB').onclick = () => runDemo('B');
$('#btnDemoUnmet').onclick = () => runDemo('unmet');

// ---- scheme list -------------------------------------------------------
async function refreshSchemes() {
  const list = await api('/api/schemes');
  const ul = $('#schemeList');
  ul.innerHTML = '';
  ['#cmpA', '#cmpB'].forEach((sel, idx) => {
    $(sel).innerHTML = '';
  });
  list.forEach((s, i) => {
    const li = document.createElement('li');
    if (s.id === currentSchemeId) li.className = 'active';
    li.innerHTML = `<span>${esc(s.name)}</span>
      <span><span class="parent">${s.parentId ? '↳ v父 ' + s.parentId + ' ' : ''}</span>
      <span class="ver">v${s.version}</span></span>`;
    li.onclick = () => openScheme(s.id);
    ul.appendChild(li);
    ['#cmpA', '#cmpB'].forEach(sel => {
      const o = document.createElement('option');
      o.value = s.id;
      o.textContent = `${s.name} v${s.version} (#${s.id})`;
      $(sel).appendChild(o);
    });
  });
  if (list.length >= 2) { $('#cmpB').value = list[0].id; $('#cmpA').value = list[1].id; }
}

async function openScheme(id) {
  currentSchemeId = id;
  const data = await api('/api/schemes/' + id);
  currentReport = data.report;
  await refreshSchemes();
  renderAll(data.scheme, data.report);
}

$('#btnCompare').onclick = async () => {
  const a = $('#cmpA').value, b = $('#cmpB').value;
  if (!a || !b || a === b) { $('#compareOut').textContent = '请选择两个不同的方案。'; return; }
  const d = await api(`/api/compare?a=${a}&b=${b}`);
  renderCompare(d);
};

// ---- rendering ---------------------------------------------------------
function renderAll(scheme, r) {
  renderSummary(scheme, r);
  renderDeps(r);
  renderSymbols(r);
  renderVersions(r);
  renderRelocs(r);
  setupPlay(r);
}

function renderSummary(scheme, r) {
  const c = r.counts || {};
  const outcomes = {};
  r.verdicts.forEach(v => { outcomes[v.outcome] = (outcomes[v.outcome] || 0) + 1; });
  const warn = (r.warnings || []).map(w => `<div>⚠ ${esc(w)}</div>`).join('');
  $('#summary').innerHTML = `
    <div><b>${esc(scheme.name)}</b> <span class="badge purple">v${scheme.version}</span>
      ${scheme.parentId ? `<span class="muted">派生自 #${scheme.parentId}</span>` : ''}
    </div>
    <div class="policy">${esc(r.policySummary)}</div>
    <div class="stats">
      <span>参与单元 <b>${(c.units || r.units.length)}</b></span>
      <span>裁决 <b>${r.verdicts.length}</b></span>
      <span class="badge good">已裁决 ${outcomes.RESOLVED || 0}</span>
      <span class="badge bad">歧义 ${outcomes.AMBIGUOUS || 0}</span>
      <span class="badge bad">未定义 ${outcomes.UNRESOLVED || 0}</span>
      <span class="badge warn">版本未满足 ${outcomes.UNMET_VERSION || 0}</span>
    </div>
    ${warn ? `<div class="warning-box">${warn}</div>` : ''}`;
}

function renderDeps(r) {
  const panes = r.units.map(u => {
    const badges = [];
    if (u.fromArchive) badges.push('<span class="pill archive">归档成员</span>');
    if (u.extracted) badges.push('<span class="pill archive">惰性抽取</span>');
    if (u.corrupt) badges.push('<span class="pill bad">损坏·已隔离</span>');
    if (u.incompatible) badges.push('<span class="pill warn">平台不匹配</span>');
    if (u.discardedComdat) badges.push('<span class="pill warn">COMDAT折叠</span>');
    const deps = (u.needed || []).map(n => {
      const hit = (r.dependencyGraph || []).some(e => e.from === u.displayName && e.to === n && e.resolved);
      return `<div class="graph-edge"><span>${esc(u.displayName)}</span><span class="arrow">→</span>
        <span>${esc(n)}</span> ${hit ? '<span class="badge good">已满足</span>' : '<span class="badge warn">库不在输入中</span>'}</div>`;
    }).join('');
    return `<div class="unit-card">
      <div class="head"><span class="name">${esc(u.displayName)}</span>
        <span class="pill">${esc(u.kind || '?')}</span>${badges.join('')}</div>
      <div class="kvs">提供者 ${u.providers.length} · 引用 ${u.references.length} · 加载序号 ${u.resolvedOrder}</div>
      ${u.corrupt ? `<div class="kvs" style="color:var(--bad)">${esc(u.error)}</div>` : ''}
      ${deps}
    </div>`;
  }).join('');
  $('#tab-deps').innerHTML = panes || '<p class="muted">无单元。</p>';
}

function renderSymbols(r) {
  const html = r.verdicts.map(v => {
    const cands = v.candidates.map(c => `
      <div class="cand-row ${c.status === 'WINNER' ? 'winner' : c.status === 'AMBIGUOUS' ? 'tie' : 'eliminated'}">
        <span class="mono">${esc(c.displayName)}</span>
        <span>${candidateStatus(c.status)}</span>
        <span class="mono">${esc(c.binding || '')}</span>
        <span class="mono">${esc(c.version ? c.version : '默认/无版本')}</span>
        <span class="reason">${esc(c.reason || '')} ${addr(c.predictedAddress)}</span>
      </div>`).join('');
    const relocs = v.affectedReferences.map(ref =>
      `<div class="reloc-line">↳ ${esc(ref.displayName)} · ${esc(ref.binding)}`
      + (ref.fromRelocation ? ` · ${esc(ref.relocType)} @0x${(ref.relocOffset || 0).toString(16)} in ${esc(ref.relocSection || '')}` : ' · 未定义符号')
      + (ref.copyRelocation ? ' · <b style="color:var(--warn)">COPY</b>' : '')
      + `</div>`).join('');
    const ver = v.requestedVersion ? `@<span class="mono" style="color:var(--purple)">${esc(v.requestedVersion)}</span>` : '';
    return `<details class="verdict" ${v.outcome === 'RESOLVED' ? '' : 'open'}>
      <summary>
        <span class="sym">${esc(v.name)}</span>${ver}
        ${outcomeBadge(v.outcome)}
        ${v.weakReference ? '<span class="badge muted">弱引用</span>' : ''}
        ${v.copyRelocation ? '<span class="badge warn">COPY reloc</span>' : ''}
        ${v.winner ? `<span class="badge good">胜：${esc(v.winner.displayName)} ${addr(v.predictedAddress)}</span>` : ''}
        <span class="sum">${esc(v.summary)}</span>
      </summary>
      <div class="cand-row head"><span>候选提供者</span><span>结论</span><span>binding</span><span>版本</span><span>淘汰原因 / 依据</span></div>
      ${cands || '<div class="reloc-line">无候选</div>'}
      <div class="reloc-line" style="padding-top:6px">受影响引用：</div>
      ${relocs}
    </details>`;
  }).join('');
  $('#tab-symbols').innerHTML = html || '<p class="muted">无符号引用。</p>';
}

function candidateStatus(s) {
  if (s === 'WINNER') return '<span class="badge good">选中</span>';
  if (s === 'AMBIGUOUS') return '<span class="badge bad">歧义</span>';
  if (s === 'TIE') return '<span class="badge info">同票</span>';
  return '<span class="badge muted">淘汰</span>';
}

function renderVersions(r) {
  const defs = [], needs = [];
  (r.versionGraph || []).forEach(n => {
    if (n.kind === 'DEF') defs.push(n); else needs.push(n);
  });
  const d = defs.map(n =>
    `<span class="ver-node def">${esc(n.file)}: <b>${esc(n.name)}</b> v${n.index}`
    + (n.aliases && n.aliases.length ? ` 别名[${n.aliases.map(esc).join(',')}]` : '') + `</span>`).join('');
  const nd = needs.map(n =>
    `<div class="graph-edge"><span>${esc(n.file)}</span><span class="arrow">需要→</span>
       <span class="ver-node need">${esc(n.from)}::<b>${esc(n.name)}</b>${n.weak ? ' weak' : ''}</span></div>`).join('');
  $('#tab-versions').innerHTML =
    `<div class="panel"><h3>版本定义（Verdef，非默认版本）</h3>${d || '<span class="muted">无</span>'}</div>
     <div class="panel"><h3>版本需求（Verneed）</h3>${nd || '<span class="muted">无</span>'}</div>`;
}

function renderRelocs(r) {
  const rows = [];
  r.units.forEach(u => {
    (u.references || []).forEach(ref => {
      if (!ref.fromRelocation) return;
      rows.push(`<tr>
        <td class="mono">${esc(ref.name)}${ref.version ? '@' + esc(ref.version) : ''}</td>
        <td class="mono">${esc(ref.relocType)}</td>
        <td class="mono">0x${(ref.relocOffset || 0).toString(16)}</td>
        <td class="mono">${esc(ref.relocSection || '')}</td>
        <td>${esc(ref.displayName)}</td>
        <td>${ref.copyRelocation ? '<span class="badge warn">COPY</span>'
            : ref.jumpSlot ? '<span class="badge info">JUMP_SLOT</span>'
            : ref.globDat ? '<span class="badge info">GLOB_DAT</span>' : '<span class="badge muted">普通</span>'}</td>
      </tr>`);
    });
  });
  $('#tab-relocs').innerHTML = `<div class="panel"><table>
    <thead><tr><th>符号</th><th>类型</th><th>偏移</th><th>节</th><th>来源单元</th><th>类别</th></tr></thead>
    <tbody>${rows.join('') || '<tr><td colspan="6" class="muted">无动态重定位（fixture 内 REL 引用以未定义符号呈现）。</td></tr>'}</tbody>
    </table></div>`;
}

// ---- player ------------------------------------------------------------
function setupPlay(r) {
  playIndex = 0;
  if (playTimer) { clearInterval(playTimer); playTimer = null; }
  const ol = $('#playList');
  ol.innerHTML = '';
  r.steps.forEach((st, i) => {
    const li = document.createElement('li');
    li.dataset.i = i;
    li.innerHTML = `<div><span class="phase">${esc(st.phase)}</span><span class="t">${esc(st.title)}</span></div>
      <div class="d">${esc(st.detail || '')}</div>
      ${st.symbol ? `<div class="meta">${esc(st.symbol)}</div>` : ''}`;
    ol.appendChild(li);
  });
  $('#playPos').textContent = `0 / ${r.steps.length}`;
}
function showPlay(n) {
  const items = $$('#playList li');
  items.forEach((li, i) => li.classList.toggle('shown', i < n));
  $('#playPos').textContent = `${n} / ${items.length}`;
  if (n > 0 && items[n - 1]) items[n - 1].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
}
$('#playStep').onclick = () => {
  const total = $$('#playList li').length;
  playIndex = Math.min(playIndex + 1, total);
  showPlay(playIndex);
};
$('#playReset').onclick = () => {
  playIndex = 0; showPlay(0);
  if (playTimer) { clearInterval(playTimer); playTimer = null; }
};
$('#playAuto').onclick = () => {
  if (playTimer) { clearInterval(playTimer); playTimer = null; return; }
  playTimer = setInterval(() => {
    const total = $$('#playList li').length;
    if (playIndex >= total) { clearInterval(playTimer); playTimer = null; return; }
    playIndex++; showPlay(playIndex);
  }, 900);
};

// ---- compare -----------------------------------------------------------
function changeList(items, cls, title) {
  if (!items.length) return '';
  return `<div class="grp"><h4>${title}（${items.length}）</h4>` + items.map(c =>
    `<div style="margin:3px 0">
       <span class="badge ${cls}">${esc(c.classification || '')}</span>
       <span class="mono">${esc(c.name)}${c.version ? '@' + esc(c.version) : ''}</span>
       <div class="muted" style="padding-left:6px">${esc(c.note || '')}</div>
     </div>`).join('') + '</div>';
}
function renderCompare(d) {
  $('#compareOut').innerHTML =
    `<div class="muted" style="margin-bottom:6px">${esc(d.schemeA)} ⇄ ${esc(d.schemeB)}</div>
     ${d.notes.map(n => `<div class="muted">${esc(n)}</div>`).join('')}
     ${changeList(d.providerChanged, 'bad', '换了提供者（真实语义变化）')}
     ${changeList(d.addressOnly, 'warn', '仅地址变化（提供者不变）')}
     ${changeList(d.stateChanged, 'info', '解析状态变化')}
     ${changeList(d.added, 'muted', '新增引用')}
     ${changeList(d.removed, 'muted', '消失引用')}`;
}

// ---- tabs --------------------------------------------------------------
$$('.tab').forEach(t => t.onclick = () => {
  $$('.tab').forEach(x => x.classList.remove('active'));
  $$('.tabpane').forEach(x => x.classList.remove('active'));
  t.classList.add('active');
  $('#tab-' + t.dataset.tab).classList.add('active');
});

// init
refreshSchemes().catch(() => {});
