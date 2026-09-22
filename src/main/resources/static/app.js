let binaries = [];
let schemes = [];
let selected = new Set();
let scheme = null;
let playTimer = null;
let playStep = 0;

const $ = (id) => document.getElementById(id);
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
const hex = (value) => value === null || value === undefined ? '' : '0x' + Number(value).toString(16);

async function refreshBinaries() {
  binaries = await fetch('/api/binaries').then(r => r.json());
  $('binaryList').innerHTML = binaries.map(b => `
    <label class="binary-item"><input type="checkbox" value="${esc(b.id)}" ${selected.has(b.id) ? 'checked' : ''}>
    <span>${esc(b.file_name)} <code>${esc(b.id)}</code><br><span class="small muted">${esc(b.parse_status)} ${esc(b.parse_error || '')}</span></span></label>`).join('');
  document.querySelectorAll('#binaryList input').forEach(input => input.onchange = () => {
    input.checked ? selected.add(input.value) : selected.delete(input.value);
  });
}

async function refreshSchemes() {
  schemes = await fetch('/api/schemes').then(r => r.json());
  $('schemeList').innerHTML = '<h3>历史方案（冻结）</h3>' + schemes.map(s =>
    `<button data-id="${esc(s.id)}">${esc(s.name)} <span class="muted">${esc(s.policy)}</span></button>`).join('');
  document.querySelectorAll('#schemeList button[data-id]').forEach(button => button.onclick = () => loadScheme(button.dataset.id));
}

async function loadScheme(id) {
  scheme = await fetch(`/api/schemes/${id}`).then(r => r.json());
  renderScheme();
}

function renderScheme() {
  if (!scheme) return;
  const active = scheme.activeUnits?.length || 0;
  const decisions = scheme.decisions?.length || 0;
  const unresolved = scheme.decisions?.filter(d => d.status.includes('UNRESOLVED') || d.status.startsWith('AMBIGUOUS')).length || 0;
  $('summary').className = 'summary';
  $('summary').innerHTML = `<b>${esc(scheme.name)}</b>　<span class="badge">${esc(scheme.policy)}</span>
    <span class="badge">活跃对象 ${active}</span><span class="badge">裁决 ${decisions}</span><span class="badge">异常/歧义 ${unresolved}</span>
    <div class="small muted">${(scheme.warnings || []).map(esc).join('<br>')}</div>`;
  renderDependencies();
  renderDecisions();
  renderVersions();
  renderRelocations();
  renderTimeline();
  renderCompare();
}

function unitName(unit) { return unit.memberName ? `${unit.sourceFile}(${unit.memberName})` : unit.sourceFile; }

function renderDependencies() {
  const explicit = scheme.explicitUnits || [];
  const active = scheme.activeUnits || [];
  const members = scheme.availableMembers || [];
  $('dependencies').innerHTML = `<div class="card"><h3>显式加载</h3>${explicit.map((u, i) =>
    `<div>${i + 1}. <b>${esc(unitName(u))}</b> <span class="muted">${formatKind(u)}</span></div>`).join('')}
    <h3>依赖与抽取结果</h3>${active.map(u => `<div>● ${esc(unitName(u))} ${u.extracted ? '<span class="badge">archive 抽取</span>' : ''}</div>`).join('')}
    <h3>未抽取 archive 成员</h3>${members.filter(u => !active.some(a => a.id === u.id)).map(u =>
      `<div class="muted">○ ${esc(unitName(u))}</div>`).join('')}</div>`;
}

function formatKind(unit) {
  const b = unit.binary || {};
  return b.format === 'ELF_SHARED' ? 'shared object' : b.format === 'ELF_EXECUTABLE' ? 'executable' : 'relocatable object';
}

function renderDecisions() {
  $('symbols').innerHTML = (scheme.decisions || []).map(d => `<article class="card ${esc(d.status.toLowerCase())}">
    <h3>${esc(d.name)}${d.requestedVersion ? '@' + esc(d.requestedVersion) : ''} <span class="badge">${esc(d.status)}</span></h3>
    <p>${esc(d.rationale)}</p>
    <b>最终选择</b>${(d.chosen || []).map(p => `<div class="choice">${esc(providerText(p))}　<span class="muted">${esc(p.rankReason)}</span></div>`).join('') || '<div class="muted">无</div>'}
    <details open><summary>候选提供者与淘汰原因</summary><table><thead><tr><th>候选</th><th>裁决</th></tr></thead><tbody>
    ${(d.rejected || []).map(r => `<tr><td>${esc(providerText(r.provider))}</td><td class="reject">淘汰：${esc(r.reason)}</td></tr>`).join('')}
    </tbody></table></details>
    <details><summary>受影响 relocation（${(d.references || []).length}）</summary>${(d.references || []).map(refText).join('<br>')}</details>
  </article>`).join('');
}

function providerText(p) {
  const s = p.symbol || {};
  return `${unitName(p.unit)}::${s.name}${p.versionName ? '@' + p.versionName : ''} [${binding(s)} ${visibility(s)} ${s.sectionName || ''} ${hex(s.value)}]`;
}

function binding(s) { return ['LOCAL','GLOBAL','WEAK'][s.binding] || s.binding; }
function visibility(s) { return ['DEFAULT','INTERNAL','HIDDEN','PROTECTED'][s.visibility] || s.visibility; }
function refText(r) { return `<code>${esc(unitName(r.unit))} ${esc(r.relocation.sectionName)}+${hex(r.relocation.offset)} ${esc(r.relocation.typeName || r.relocation.type)}${r.copy ? ' COPY' : ''}</code>`; }

function renderVersions() {
  const nodes = [];
  for (const u of scheme.activeUnits || []) {
    const b = u.binary || {};
    (b.versionDefinitions || []).forEach(v => nodes.push(`${unitName(u)} ${v.name}${v.defaultVersion ? '（默认）' : '（非默认）'}`));
    (b.versionNeeds || []).forEach(n => n.requirements.forEach(v => nodes.push(`${unitName(u)} 需要 ${n.library} ${v.name}`));
  }
  $('versions').innerHTML = `<div class="card"><h3>版本定义与需求图</h3>${nodes.map(n => `<span class="graph-node">${esc(n)}</span>`).join('<span class="arrow">→</span>') || '<p class="muted">无版本信息</p>'}</div>`;
}

function renderRelocations() {
  const rows = [];
  for (const u of scheme.activeUnits || []) {
    for (const r of u.binary?.relocations || []) rows.push({u, r});
  }
  $('relocations').innerHTML = `<div class="card"><table><thead><tr><th>对象</th><th>Section</th><th>Offset</th><th>符号</th><th>类型</th><th>动态</th></tr></thead><tbody>
  ${rows.map(x => `<tr><td>${esc(unitName(x.u))}</td><td>${esc(x.r.sectionName)}</td><td>${hex(x.r.offset)}</td><td>${esc(x.r.symbolName)}</td><td>${x.r.copy ? 'COPY' : esc(x.r.typeName)}</td><td>${x.r.dynamicSymbol ? '是' : '否'}</td></tr>`).join('')}
  </tbody></table></div>`;
}

function renderTimeline() {
  playStep = 0;
  const events = scheme.extractionEvents || [];
  $('timeline').innerHTML = `<div class="player"><button id="play">逐步播放</button><button id="pause">暂停</button><span class="muted">展示 archive member 惰性抽取和符号表更新</span></div><div id="events"></div>`;
  $('play').onclick = () => { clearInterval(playTimer); playTimer = setInterval(() => {
    playStep = Math.min(playStep + 1, events.length); drawEvents(events); if (playStep >= events.length) clearInterval(playTimer);
  }, 850); };
  $('pause').onclick = () => clearInterval(playTimer);
  drawEvents(events);
}

function drawEvents(events) {
  $('events').innerHTML = events.slice(0, playStep).map(e => `<div class="card"><b>${e.action === 'EXTRACTED' ? '抽取' : '跳过'} ${esc(e.archive)}(${esc(e.member)})</b>
    <div>触发符号：${e.demandedSymbols.map(s => `<code>${esc(s)}</code>`).join(' ')}</div><div class="muted">${esc(e.reason)}</div></div>`).join('') || '<p class="muted">无 archive 事件</p>';
}

function renderCompare() {
  $('compare').innerHTML = `<div class="card"><select id="beforeScheme">${schemeOptions()}</select><select id="afterScheme">${schemeOptions()}</select><button id="runCompare">比较两个冻结方案</button><div id="compareResult" class="muted">选择方案后查看：哪些换了提供者，哪些只是地址变化。</div></div>`;
  $('runCompare').onclick = async () => {
    const result = await fetch('/api/compare', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({beforeSchemeId:$('beforeScheme').value, afterSchemeId:$('afterScheme').value})}).then(r => r.json());
    $('compareResult').innerHTML = `<h3>提供者变化</h3>${comparisonRows(result.providerChanges)}<h3>仅地址变化</h3>${comparisonRows(result.addressChanges)}<h3>状态变化</h3>${comparisonRows(result.statusChanges)}`;
  };
}

function schemeOptions() { return schemes.map(s => `<option value="${esc(s.id)}" ${s.id === scheme.id ? 'selected' : ''}>${esc(s.name)}</option>`).join(''); }
function comparisonRows(rows) { return !rows?.length ? '<p class="muted">无</p>' : `<table><tbody>${rows.map(r => `<tr><td><code>${esc(r.symbol)}</code></td><td>${esc(r.beforeProvider)} → ${esc(r.afterProvider)}</td><td>${hex(r.beforeAddress)} → ${hex(r.afterAddress)}</td></tr>`).join('')}</tbody></table>`; }

$('uploadForm').onsubmit = async (event) => {
  event.preventDefault();
  await fetch('/api/binaries', {method:'POST', body:new FormData(event.target)});
  event.target.reset();
  await refreshBinaries();
};
$('schemeForm').onsubmit = async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  const body = {name: form.get('name'), policy: form.get('policy'), binaryIds: [...selected]};
  const result = await fetch('/api/schemes', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)}).then(r => r.json());
  scheme = result.scheme;
  await refreshSchemes();
  renderScheme();
};
$('loadDemo').onclick = async () => { scheme = await fetch('/api/demo').then(r => r.json()); renderScheme(); };
document.querySelectorAll('.tabs button').forEach(button => button.onclick = () => {
  document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
  button.classList.add('active');
  $(button.dataset.tab).classList.add('active');
});
refreshBinaries();
refreshSchemes();
