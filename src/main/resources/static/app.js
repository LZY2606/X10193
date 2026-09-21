'use strict';

const state = {
    uploads: [],
    order: [],
    schemes: [],
    current: null,
    currentId: null,
    playIndex: 0,
    playTimer: null,
};

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => Array.from(document.querySelectorAll(sel));

async function api(url, opts) {
    const res = await fetch(url, opts);
    if (!res.ok) {
        let msg = res.statusText;
        try { msg = (await res.json()).error || msg; } catch (e) {}
        throw new Error(msg);
    }
    return res.json();
}

function esc(s) {
    return String(s ?? '').replace(/[&<>"']/g, (c) =>
        ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
}

// --------------------------------------------------------------- 上传与列表

$('#uploadForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const res = await fetch('/api/uploads', { method: 'POST', body: fd });
    if (res.redirected) {
        // flash 信息在 redirect 后丢失（纯 API 场景），直接刷新数据即可
    }
    $('#fileInput').value = '';
    await refreshAll();
});

async function refreshAll() {
    state.uploads = await api('/api/uploads');
    state.schemes = await api('/api/schemes');
    // 保留已在 order 中仍存在的选择，新增对象追加到加载顺序尾部
    const valid = new Set(state.uploads.map(u => u.id));
    state.order = state.order.filter(id => valid.has(id));
    for (const u of state.uploads) {
        if (!state.order.includes(u.id)) state.order.push(u.id);
    }
    renderUploads();
    renderOrder();
    renderSchemes();
    renderCompareSelects();
}

function renderUploads() {
    const ul = $('#objectList');
    ul.innerHTML = '';
    for (const u of state.uploads) {
        const li = document.createElement('li');
        li.innerHTML = `<span class="badge ${u.kind}">${u.kind}</span>
            <span title="${esc(u.sha256)}">${esc(u.name)}</span>`;
        ul.appendChild(li);
    }
}

function renderOrder() {
    const ol = $('#loadOrder');
    ol.innerHTML = '';
    state.order.forEach((id, idx) => {
        const u = state.uploads.find(x => x.id === id);
        if (!u) return;
        const li = document.createElement('li');
        li.innerHTML = `<span class="badge ${u.kind}">${u.kind}</span>
            <span class="mono">${esc(u.name)}</span>
            <span style="flex:1"></span>
            <span class="move-btns">
                <button data-act="up">↑</button><button data-act="down">↓</button>
                <button data-act="del">×</button>
            </span>`;
        li.querySelector('[data-act=up]').onclick = () => move(idx, -1);
        li.querySelector('[data-act=down]').onclick = () => move(idx, 1);
        li.querySelector('[data-act=del]').onclick = () => {
            state.order.splice(idx, 1);
            renderOrder();
        };
        ol.appendChild(li);
    });
}

function move(idx, delta) {
    const j = idx + delta;
    if (j < 0 || j >= state.order.length) return;
    [state.order[idx], state.order[j]] = [state.order[j], state.order[idx]];
    renderOrder();
}

// --------------------------------------------------------------- 方案

$('#runBtn').onclick = async () => {
    const name = $('#schemeName').value.trim();
    if (!name) { alert('请填写方案名'); return; }
    const body = {
        name,
        policy: $('#policy').value,
        orderedUploadIds: state.order,
    };
    try {
        const created = await api('/api/schemes', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
        });
        await refreshAll();
        selectScheme(created.schemeId);
    } catch (e) {
        alert(e.message);
    }
};

function renderSchemes() {
    const ul = $('#schemeList');
    ul.innerHTML = '';
    const grouped = {};
    for (const s of state.schemes) (grouped[s.name] ||= []).push(s);
    for (const [name, versions] of Object.entries(grouped)) {
        for (const s of versions) {
            const li = document.createElement('li');
            const active = s.id === state.currentId ? 'border-color: var(--accent);' : '';
            li.style.cssText = active;
            li.innerHTML = `<span class="mono">${esc(name)} <span class="muted">v${s.versionNo}</span></span>
                <span class="badge ${s.policy === 'STATIC_LD' ? 'ELF' : 'ARCHIVE'}">${s.policy === 'STATIC_LD' ? '静态' : '动态'}</span>
                <span style="flex:1"></span><button>查看</button>`;
            li.querySelector('button').onclick = () => selectScheme(s.id);
            ul.appendChild(li);
        }
    }
}

async function selectScheme(id) {
    state.currentId = id;
    state.current = await api(`/api/schemes/${id}`);
    state.playIndex = 0;
    $('#schemeMeta').innerHTML = `<b>${esc(state.current.schemeName)}</b>
        v${state.current.policyOrdinalVersion} · ${esc(state.current.policy)} ·
        ${state.current.loadOrder.length} 个装载单元 ·
        <span style="color:var(--bad)">${state.current.unresolvedCount ?? '?'} 未解析</span> ·
        <span style="color:var(--amb)">${state.current.ambiguousCount ?? '?'} 歧义</span>`;
    renderDeps();
    renderSymbols();
    renderVersions();
    renderRelocs();
    renderPlay();
    renderSchemes();
}

// --------------------------------------------------------------- 依赖/COMDAT

function unitName(id) {
    const u = state.current?.loadOrder.find(x => x.unitId === id);
    return u ? u.name : id;
}

function renderDeps() {
    const d = state.current;
    const order = d.loadOrder.map((u, i) =>
        `<li><span class="muted mono">#${i}</span>
         <span class="badge ${u.kindLabel === undefined ? '' : ''}">${esc(u.kindLabel)}</span>
         <span class="mono">${esc(u.name)}</span>
         ${!u.active ? `<span class="pill">${esc(u.inactiveReason || 'inactive')}</span>` : ''}
         ${u.extracted ? '<span class="pill" style="color:var(--good);border-color:var(--good)">已抽取</span>' : ''}
        </li>`).join('');
    $('#loadOrderView').innerHTML = `<ol class="load-order">${order}</ol>`;

    const edges = d.dependencies.map(e => {
        const color = e.resolved ? 'var(--good)' : 'var(--bad)';
        return `<tr><td class="mono">${esc(unitName(e.fromUnitId))}</td>
            <td>→</td><td class="mono">${esc(e.needed)}</td>
            <td style="color:${color}">${e.resolved ? esc(unitName(e.toUnitId)) : '未找到（隔离警告）'}</td></tr>`;
    }).join('');
    $('#loadOrderView').innerHTML += `<table><thead><tr><th>来源</th><th></th><th>DT_NEEDED</th><th>解析</th></tr></thead>
        <tbody>${edges || '<tr><td colspan="4" class="muted">无 DT_NEEDED</td></tr>'}</tbody></table>`;

    const comdat = d.comdat.map(g => {
        const rows = g.instances.map(i =>
            `<tr class="${i.kept ? 'winner-row' : ''}"><td class="mono">${esc(i.unitName)}</td>
             <td>${i.kept ? '✅ 保留' : '🗑 丢弃'}</td><td class="muted">${esc(i.reason)}</td></tr>`).join('');
        return `<div class="ref-card"><div class="ref-head"><span class="ref-name">${esc(g.signature)}</span>
            <span class="status RESOLVED">COMDAT</span></div>
            <div class="ref-body" style="display:block"><table>
            <thead><tr><th>单元</th><th>裁决</th><th>原因</th></tr></thead><tbody>${rows}</tbody></table></div></div>`;
    }).join('');
    $('#comdatView').innerHTML = comdat || '<p class="muted">没有 COMDAT 组</p>';

    $('#badView').innerHTML = d.badObjects.length ? d.badObjects.map(b =>
        `<div class="reject">🛑 ${esc(b.name)}：${esc(b.error)}（已隔离，不影响其它对象）</div>`).join('<br/>')
        : '<p class="muted">无损坏对象</p>';
    $('#warnView').innerHTML = d.warnings.length
        ? d.warnings.map(w => `<div>⚠️ ${esc(w)}</div>`).join('')
        : '<p class="muted">无警告</p>';
}

// --------------------------------------------------------------- 符号候选

function visPills(p) {
    const pills = [];
    if (p.bind === 'WEAK') pills.push('<span class="pill WEAK">WEAK</span>');
    if (p.visibility !== 'DEFAULT') pills.push(`<span class="pill ${p.visibility}">${p.visibility}</span>`);
    if (p.common) pills.push('<span class="pill">COMMON</span>');
    if (p.comdat) pills.push(`<span class="pill">COMDAT:${esc(p.comdatSignature || '')}</span>`);
    if (p.fromSharedObject) pills.push('<span class="pill">SHARED</span>');
    if (p.copyRelocation) pills.push('<span class="pill" style="color:var(--warn);border-color:var(--warn)">COPY-RELOC</span>');
    if (p.versionName) pills.push(`<span class="pill ${p.nonDefaultVersion ? '' : ''}"
        style="${p.nonDefaultVersion ? 'color:var(--amb);border-color:var(--amb)' : ''}">
        @${esc(p.versionName)}${p.nonDefaultVersion ? ' (非默认)' : ''}</span>`);
    return pills.join('');
}

function renderSymbols() {
    const d = state.current;
    const filter = $('#symFilter').value.toLowerCase();
    const status = $('#statusFilter').value;
    const view = $('#symbolView');
    view.innerHTML = '';
    d.refs.forEach((r, idx) => {
        const hay = `${r.key.symbol} ${r.key.unitId} ${r.key.versionName || ''} ${r.status}`.toLowerCase();
        if (filter && !hay.includes(filter)) return;
        if (status && r.status !== status) return;

        const cands = r.candidates.map(c => {
            const p = c.provider;
            return `<tr class="${c.winner ? 'winner-row' : ''}">
                <td>${c.winner ? '🏆' : (c.eligible ? '○' : '✗')}</td>
                <td class="mono">${esc(p.unitName)}</td>
                <td>${esc(p.type)} ${visPills(p)}</td>
                <td class="muted">${esc(c.rankNote || (c.eligible ? '合格' : ''))}</td>
                <td class="${c.eligible ? '' : 'reject'}">${esc(c.eligible ? '' : c.rejectReason)}</td>
                <td class="mono muted">0x${(p.value || 0).toString(16)} size=${p.size}</td>
            </tr>`;
        }).join('');

        const relocs = r.affectedRelocs.map(a =>
            `<tr><td class="mono">${esc(a.unitName)}</td><td class="mono">${esc(a.section)}</td>
             <td class="mono">0x${(a.offset || 0).toString(16)}</td><td>${esc(a.type)}</td>
             <td class="muted">${a.copy ? '<b style="color:var(--warn)">COPY</b>' : ''}${a.hasAddend ? 'addend=0x'+a.addend.toString(16) : ''}</td></tr>`
        ).join('');

        const card = document.createElement('div');
        card.className = 'ref-card';
        card.innerHTML = `<div class="ref-head">
            <span class="ref-name">${esc(r.key.symbol)}
                ${r.key.versionName ? `<span class="pill" style="color:var(--accent);border-color:var(--accent)">@${esc(r.key.versionName)}</span>` : '<span class="pill">无版本</span>'}
                <span class="muted" style="font-weight:400">引用自 ${esc(unitName(r.key.unitId))} · ${r.binding}/${r.refType}</span>
            </span>
            <span class="status ${r.status}">${r.status}</span></div>
            <div class="ref-body">
                <div class="muted">${esc(r.detail || '')}</div>
                <table><thead><tr><th></th><th>提供者</th><th>属性</th><th>层级/备注</th><th>淘汰原因</th><th>值/尺寸</th></tr></thead>
                <tbody>${cands || '<tr><td colspan="6" class="reject">没有看到任何候选</td></tr>'}</tbody></table>
                <h3 style="margin-top:6px">受影响 relocation（${r.affectedRelocs.length}）</h3>
                <table><thead><tr><th>单元</th><th>节区</th><th>offset</th><th>类型</th><th></th></tr></thead>
                <tbody>${relocs || '<tr><td colspan="5" class="muted">该引用没有直接重定位条目</td></tr>'}</tbody></table>
            </div>`;
        card.querySelector('.ref-head').onclick = () => card.classList.toggle('open');
        if (r.status === 'UNRESOLVED' || r.status === 'AMBIGUOUS') card.classList.add('open');
        view.appendChild(card);
    });
}
$('#symFilter').oninput = () => state.current && renderSymbols();
$('#statusFilter').onchange = () => state.current && renderSymbols();

// --------------------------------------------------------------- 版本图

function renderVersions() {
    const d = state.current;
    // 收集每个单元的 verdef / verneed
    const rows = [];
    const edges = [];
    for (const u of d.loadOrder) {
        // 版本细节在 candidates / refs 已可见；这里从 refs 的候选反推提供侧版本，
        // 完整 verdef/verneed 需要原始对象信息——通过候选与依赖边重建图：
    }
    // 以候选中出现的版本为节点，以 refs 的引用版本 -> 提供版本为边
    const provNodes = new Map(); // key unitId|version -> {unitId, name, nonDefault}
    const needLinks = [];
    for (const r of d.refs) {
        if (r.key.versionName) {
            needLinks.push({ from: r.key.unitId, ver: r.key.versionName });
        }
        for (const c of r.candidates) {
            const p = c.provider;
            if (!p.versionName) continue;
            provNodes.set(`${p.unitId}|${p.versionName}`, {
                unitId: p.unitId, name: p.versionName, nonDefault: p.nonDefaultVersion,
                def: p.definition,
            });
        }
    }
    // 依赖边（needed 名字上的版本要求由 refs 携带）
    const html = [];
    html.push('<div class="muted">箭头：引用方要求版本 → 提供方定义版本；紫色为非默认版本。</div>');
    // 简化：两张表
    const defRows = [...provNodes.values()].map(n =>
        `<tr><td class="mono">${esc(unitName(n.unitId))}</td>
         <td class="mono">${esc(n.name)}</td>
         <td>${n.nonDefault ? '<span style="color:var(--amb)">非默认</span>' : '默认'}</td>
         <td>${n.def ? 'Verdef' : ''}</td></tr>`).join('');
    const needRows = [...new Map(needLinks.map(x => [`${x.from}|${x.ver}`, x])).values()]
        .map(n => `<tr><td class="mono">${esc(unitName(n.from))}</td>
         <td class="mono">${esc(n.ver)}</td><td>Verneed</td></tr>`).join('');
    html.push(`<table><thead><tr><th>提供单元</th><th>版本名</th><th>默认性</th><th>来源</th></tr></thead>
        <tbody>${defRows || '<tr><td colspan="4" class="muted">无带版本定义</td></tr>'}</tbody></table>`);
    html.push(`<h3>版本需求</h3><table><thead><tr><th>引用单元</th><th>要求版本</th><th></th></tr></thead>
        <tbody>${needRows || '<tr><td colspan="3" class="muted">无版本化引用</td></tr>'}</tbody></table>`);
    $('#versionView').innerHTML = html.join('');
}

// --------------------------------------------------------------- Relocation 总表

function renderRelocs() {
    const d = state.current;
    const all = [];
    for (const r of d.refs) {
        for (const a of r.affectedRelocs) {
            all.push({ a, sym: r.key.symbol, ver: r.key.versionName, status: r.status });
        }
    }
    const rows = all.map(x =>
        `<tr style="${x.a.copy ? 'background:rgba(232,176,75,0.08)' : ''}">
         <td class="mono">${esc(x.a.unitName)}</td>
         <td class="mono">${esc(x.a.section)}</td>
         <td class="mono">0x${(x.a.offset || 0).toString(16)}</td>
         <td>${esc(x.a.type)}</td>
         <td class="mono">${esc(x.sym)}${x.ver ? '@' + esc(x.ver) : ''}</td>
         <td>${x.a.copy ? '<span class="status COPY">COPY</span>' : esc(x.status)}</td>
         <td class="muted">${x.a.hasAddend ? '0x' + x.a.addend.toString(16) : ''}</td>
        </tr>`).join('');
    $('#relocView').innerHTML = `<table><thead><tr>
        <th>单元</th><th>节区</th><th>offset</th><th>类型</th><th>符号</th><th>裁决</th><th>addend</th>
        </tr></thead><tbody>${rows || '<tr><td colspan="7" class="muted">没有重定位</td></tr>'}</tbody></table>`;
}

// --------------------------------------------------------------- 播放器

function filteredSteps() {
    const phase = $('#playPhase').value;
    return state.current.steps
        .map((s, i) => ({ s, i }))
        .filter(x => !phase || x.s.phase === phase);
}

function renderPlay() {
    const steps = filteredSteps();
    const pos = state.playIndex;
    $('#playPos').textContent = `${Math.min(pos + 1, steps.length)} / ${steps.length}`;
    if (!steps.length) {
        $('#playStep').innerHTML = '<p class="muted">该阶段没有步骤</p>';
        $('#playSnapshot').innerHTML = '';
        return;
    }
    const cur = steps[Math.min(pos, steps.length - 1)].s;
    $('#playStep').innerHTML = `<div class="phase">步骤 ${cur.index} · ${cur.phase}</div>
        <h4>${esc(cur.title)}</h4><div>${esc(cur.description)}</div>`;
    const snap = Object.entries(cur.symbolTableSnapshot || {});
    $('#playSnapshot').innerHTML = snap.length
        ? `<h3>此刻全局符号表（${snap.length}）</h3>
           <div class="snap-table"><table><thead><tr><th>符号@版本</th><th>当前提供者（|| 分隔多个观测）</th></tr></thead>
           <tbody>${snap.map(([k, v]) => `<tr><td class="mono">${esc(k)}</td><td class="mono muted">${esc(v)}</td></tr>`).join('')}
           </tbody></table></div>`
        : '<p class="muted">该步无符号表快照</p>';
}

$('#playFirst').onclick = () => { state.playIndex = 0; renderPlay(); };
$('#playPrev').onclick = () => { state.playIndex = Math.max(0, state.playIndex - 1); renderPlay(); };
$('#playNext').onclick = () => {
    const n = filteredSteps().length;
    state.playIndex = Math.min(n - 1, state.playIndex + 1); renderPlay();
};
$('#playPhase').onchange = () => { state.playIndex = 0; renderPlay(); };
$('#playAuto').onclick = () => {
    if (state.playTimer) { clearInterval(state.playTimer); state.playTimer = null; $('#playAuto').textContent = '▶ 播放'; return; }
    $('#playAuto').textContent = '⏸ 暂停';
    state.playTimer = setInterval(() => {
        const n = filteredSteps().length;
        if (state.playIndex >= n - 1) {
            clearInterval(state.playTimer); state.playTimer = null; $('#playAuto').textContent = '▶ 播放'; return;
        }
        state.playIndex++; renderPlay();
    }, 900);
};

// --------------------------------------------------------------- 比较

function renderCompareSelects() {
    const opts = state.schemes.map(s =>
        `<option value="${s.id}">${esc(s.name)} v${s.versionNo} [${s.policy === 'STATIC_LD' ? '静态' : '动态'}]</option>`).join('');
    $('#compareA').innerHTML = opts;
    $('#compareB').innerHTML = opts;
    if (state.schemes.length >= 2) $('#compareB').selectedIndex = 1;
}

$('#compareBtn').onclick = async () => {
    try {
        const diffs = await api(`/api/compare?from=${encodeURIComponent($('#compareA').value)}&to=${encodeURIComponent($('#compareB').value)}`);
        const order = { PROVIDER_CHANGED: 0, STATUS_CHANGED: 1, NEW_REF: 2, GONE_REF: 3, ADDRESS_ONLY: 4, SAME_PROVIDER: 5, SAME: 6 };
        diffs.sort((a, b) => (order[a.category] ?? 9) - (order[b.category] ?? 9));
        const counts = {};
        for (const d of diffs) counts[d.category] = (counts[d.category] || 0) + 1;
        const summary = Object.entries(counts).map(([k, v]) =>
            `<span class="status ${k}" style="margin-right:6px">${k} × ${v}</span>`).join('');
        const rows = diffs.map(d =>
            `<tr><td class="mono">${esc(d.key)}</td>
             <td><span class="status ${d.category}">${d.category}</span></td>
             <td class="mono">${esc(d.fromProvider || '')}</td>
             <td class="mono">${esc(d.toProvider || '')}</td>
             <td class="muted">${esc(d.note)}</td></tr>`).join('');
        $('#compareView').innerHTML =
            `<div style="margin:8px 0">${summary}</div>
             <table><thead><tr><th>引用</th><th>类别</th><th>旧提供者</th><th>新提供者</th><th>说明</th></tr></thead>
             <tbody>${rows}</tbody></table>`;
    } catch (e) { alert(e.message); }
};

// --------------------------------------------------------------- tabs / 启动

$$('.tab').forEach(t => t.onclick = () => {
    $$('.tab').forEach(x => x.classList.remove('active'));
    $$('.tabpanel').forEach(x => x.classList.remove('active'));
    t.classList.add('active');
    $('#tab-' + t.dataset.tab).classList.add('active');
    if (t.dataset.tab === 'play') renderPlay();
});

refreshAll().catch(err => alert(err.message));
