'use strict';

const state = {
  files: [],      // {fileId, originalName, size, kind, selected}
  schemes: [],
  current: null,  // SchemeView
  events: [],
  pbTimer: null,
  pbStep: 0,
};

const $ = (sel) => document.querySelector(sel);
const api = async (path, opts) => {
  const res = await fetch(path, opts);
  if (!res.ok) {
    const t = await res.text();
    throw new Error(t);
  }
  return res.status === 200 ? res.json() : null;
};

// ---------------- Import ----------------
$('#upload-btn').onclick = async () => {
  const input = $('#file-input');
  for (const f of input.files) {
    const fd = new FormData();
    fd.append('file', f);
    const r = await api('/api/binaries', { method: 'POST', body: fd });
    state.files.push({ ...r, selected: true });
  }
  renderFiles();
};

function renderFiles() {
  $('#file-list').innerHTML = '';
  state.files.forEach((f, i) => {
    const li = document.createElement('li');
    const cb = document.createElement('input');
    cb.type = 'checkbox'; cb.checked = f.selected;
    cb.onchange = () => { f.selected = cb.checked; renderOrder(); };
    li.appendChild(cb);
    const b = document.createElement('span'); b.className = 'badge ' + (f.kind || '');
    b.textContent = f.kind || '?';
    li.appendChild(b);
    const name = document.createElement('span'); name.textContent = f.originalName;
    li.appendChild(name);
    const size = document.createElement('span'); size.className = 'muted';
    size.textContent = f.size + ' B';
    li.appendChild(size);
    const up = document.createElement('button'); up.textContent = '↑';
    up.onclick = () => moveFile(i, -1);
    const down = document.createElement('button'); down.textContent = '↓';
    down.onclick = () => moveFile(i, 1);
    li.append(up, down);
    $('#file-list').appendChild(li);
  });
  renderOrder();
}

function moveFile(i, d) {
  const j = i + d;
  if (j < 0 || j >= state.files.length) return;
  [state.files[i], state.files[j]] = [state.files[j], state.files[i]];
  renderFiles();
}

function renderOrder() {
  const box = $('#order-list');
  box.innerHTML = '<span class="muted">显式加载顺序：</span>';
  state.files.filter(f => f.selected).forEach((f, i) => {
    const chip = document.createElement('span');
    chip.className = 'badge ' + (f.kind || '');
    chip.textContent = (i + 1) + '. ' + f.originalName;
    box.appendChild(chip);
  });
}

// ---------------- Arbitrate ----------------
$('#arbitrate-btn').onclick = async () => {
  const ordered = state.files.filter(f => f.selected).map((f, i) => ({
    fileId: f.fileId, displayName: f.originalName, kind: f.kind, ordinal: i,
  }));
  const policy = {
    allowMultipleDefinition: $('#p-multidef').checked,
    allowCopyAgainstProtected: !$('#p-copyprot').checked,
    protectedNonInterposableProducer: $('#p-prodnprot').checked,
    archiveStrategy: $('#p-archive').value,
    tiePolicy: $('#p-tie').value,
  };
  const parentId = $('#parent-scheme').value || null;
  const view = await api('/api/schemes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      name: $('#scheme-name').value || '方案',
      parentId,
      files: ordered,
      policy,
    }),
  });
  state.current = view;
  $('#result-panel').hidden = false;
  await refreshSchemes();
  renderSchemeMeta(view);
  renderAll();
};

async function refreshSchemes() {
  state.schemes = await api('/api/schemes');
  for (const id of ['parent-scheme', 'diff-a', 'diff-b']) {
    const sel = $('#' + id);
    const keepFirst = id === 'parent-scheme';
    const first = keepFirst ? '<option value="">新方案族</option>' : '';
    sel.innerHTML = first + state.schemes.map(s =>
      `<option value="${s.id}">${s.name} v${s.version}</option>`).join('');
  }
  if (state.current) {
    $('#diff-a').value = state.current.parentId || '';
    $('#diff-b').value = state.current.id;
  }
}

function renderSchemeMeta(v) {
  $('#scheme-meta').innerHTML =
    `方案族 ${v.lineageId.slice(0, 8)} · 版本 <b>v${v.version}</b>` +
    (v.parentId ? ` · 父版本 ${v.parentId.slice(0, 8)}` : ' · （首版，结果冻结，不漂移）');
}

document.querySelectorAll('.tabs button').forEach(btn => {
  btn.onclick = () => {
    document.querySelectorAll('.tabs button').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    document.querySelectorAll('.tab').forEach(t =>
      t.hidden = t.dataset.tab !== btn.dataset.tab);
  };
});

function renderAll() {
  renderDeps();
  renderSymbols();
  renderVersions();
  renderRelocs();
  initPlayback();
}

const esc = (s) => String(s ?? '').replace(/[&<>"]/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// ---------------- Dependencies ----------------
function renderDeps() {
  const r = state.current.result;
  $('#deps-view').innerHTML = r.units.map(u => {
    if (!u.parseOk) {
      return `<div class="dep-card corrupt"><div class="title">${esc(u.displayName)}
        <span class="badge">${u.kind}</span></div>
        <div class="err">⚠ 对象已隔离：${esc(u.parseError)}</div></div>`;
    }
    const elf = u; // structural info lives on result
    const needed = elfDeps(u);
    const groups = elfGroups(u);
    return `<div class="dep-card"><div class="title">${esc(u.displayName)}
        <span class="badge ${u.kind}">${u.kind}</span>
        ${u.extractedBy ? '<span class="badge ARCHIVE">惰性抽取</span>' : ''}</div>
      <ul>
        ${needed.length ? '<li>NEEDED: ' + needed.map(esc).join(', ') + '</li>' : ''}
        ${groups.length ? '<li>COMDAT 组: ' + groups.map(esc).join(', ') + '</li>' : ''}
        <li>载入序: ${u.loadIndex}</li>
      </ul></div>`;
  }).join('');
}

// Units in result JSON carry elf-derived fields via events/records; derive what we stored.
function elfDeps(u) {
  const r = state.current.result;
  const node = Object.values(r.versionGraph).find(n => n.kind === 'object' || n.kind === 'shared' || n.kind === 'exec')
    && r.versionGraph['u:' + u.unitId];
  if (!node) return [];
  return node.edges.filter(e => e.startsWith('dep:')).map(e => e.slice(4));
}
function elfGroups(u) { return []; }
