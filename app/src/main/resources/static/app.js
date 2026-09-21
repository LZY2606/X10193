let plans = [];
let currentPlan = null;
let playbackPosition = 0;
let timer = null;

const $ = (selector) => document.querySelector(selector);
const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (char) =>
  ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[char]));
const hex = (value) => '0x' + Number(value ?? 0).toString(16);

async function refreshPlans(selectId) {
  plans = await fetch('/api/plans').then(response => response.json());
  $('#plans').innerHTML = plans.map(plan =>
    `<button data-plan="${esc(plan.id)}">${esc(plan.name)}<br><small>${esc(plan.policy)} · ${esc(plan.createdAt)}</small></button>`
  ).join('');
  document.querySelectorAll('#plans button').forEach(button => button.addEventListener('click', () =>
    loadPlan(button.dataset.plan)));
  for (const id of ['#compare-left', '#compare-right']) {
    const selected = $(id).value;
    $(id).innerHTML = plans.map(plan => `<option value="${esc(plan.id)}">${esc(plan.name)}</option>`).join('');
    if (selected) $(id).value = selected;
  }
  if (!currentPlan && plans.length) await loadPlan(plans[0].id);
  if (selectId && plans.some(plan => plan.id === selectId)) await loadPlan(selectId);
}

async function loadPlan(id) {
  currentPlan = await fetch(`/api/plans/${encodeURIComponent(id)}`).then(response => response.json());
  playbackPosition = 0;
  renderPlan();
}

function renderPlan() {
  if (!currentPlan) return;
  const result = currentPlan.result;
  $('#plan-title').textContent = currentPlan.name;
  $('#plan-meta').textContent = `策略 ${currentPlan.policy} · 创建于 ${currentPlan.createdAt} · ${result.notices.length} 条隔离/提示`;
  $('#object-count').textContent = result.objects.length;
  $('#decision-count').textContent = result.decisions.length;
  $('#extract-count').textContent = result.extractedMembers.length;
  $('#event-count').textContent = result.events.length;
  renderDependencies();
  renderSymbols();
  renderVersions();
  renderRelocations();
  renderPlayback();
}

function renderDependencies() {
  const result = currentPlan.result;
  const objects = result.objects.map(object => `
    <tr>
      <td><strong>${esc(object.displayName)}</strong><br><span class="reason">${esc(object.machine)} · ELF${object.bits} · ${esc(object.elfType)}</span></td>
      <td>${object.symbols.length}</td>
      <td>${object.references.length}</td>
      <td>${object.groups.length}</td>
      <td>${object.healthy ? '正常' : `<span class="status STRONG_CONFLICT">隔离</span> ${esc(object.parseError)}`}</td>
    </tr>`).join('');
  const edges = result.dependencyEdges.map(edge => `
    <tr><td>${esc(edge.fromName)}</td><td>${esc(edge.toName)}</td><td><code>${esc(edge.requestedName)}</code></td>
    <td><span class="status ${edge.satisfied ? 'RESOLVED' : 'UNRESOLVED'}">${edge.satisfied ? '满足' : '未满足'}</span></td></tr>`).join('');
  $('#dependencies').innerHTML = `
    <h2>对象依赖</h2><div class="table-wrap"><table><thead><tr><th>对象</th><th>符号</th><th>引用</th><th>COMDAT</th><th>状态</th></tr></thead><tbody>${objects}</tbody></table></div>
    <h2>DT_NEEDED / 版本需求</h2><div class="table-wrap"><table><thead><tr><th>来源</th><th>目标</th><th>需求</th><th>状态</th></tr></thead><tbody>${edges}</tbody></table></div>
    ${result.notices.length ? `<h2>隔离与提示</h2><ul>${result.notices.map(n => `<li>${esc(n)}</li>`).join('')}</ul>` : ''}`;
}

function renderSymbols() {
  const decisions = currentPlan.result.decisions.map((decision, index) => {
    const candidates = decision.candidates.map(candidate => `
      <div class="candidate ${decision.chosenProviderIds.includes(candidate.providerId) ? 'winner' : candidate.accepted ? 'loser' : 'loser'}">
        <strong>${esc(candidate.objectName)}${candidate.memberName ? `(${esc(candidate.memberName)})` : ''}</strong>
        <code>${esc(candidate.symbolName)}${candidate.versionName ? '@' + esc(candidate.versionName) : ''}</code>
        <span class="reason">${esc(candidate.binding)} · ${esc(candidate.visibility)} · section ${esc(candidate.sectionName)} · order ${candidate.loadOrder} · value ${hex(candidate.value)}</span>
        <p class="reason">${candidate.accepted ? (decision.chosenProviderIds.includes(candidate.providerId) ? '最终候选' : '候选但被更高优先级淘汰') : `淘汰：${esc(candidate.eliminationReason)}`}</p>
      </div>`).join('');
    const relocations = decision.affectedRelocations.map(rel =>
      `<li>${esc(rel.objectName)} ${esc(rel.sectionName)}+${hex(rel.offset)} <code>${esc(rel.type)}</code>${rel.copy ? ' · COPY' : ''}</li>`).join('');
    return `<details ${index < 3 ? 'open' : ''}>
      <summary><span class="status ${esc(decision.status)}">${esc(decision.status)}</span>
      <strong>${esc(decision.symbolName)}</strong> ${decision.requestedVersion ? `<code>@${esc(decision.requestedVersion)}</code>` : ''}
      · ${decision.candidates.length} 个候选 · ${decision.affectedRelocations.length} 个 relocation</summary>
      <p>${esc(decision.reason)}</p><h4>候选提供者</h4>${candidates || '<p class="reason">无候选</p>'}
      <h4>受影响 relocation</h4><ul>${relocations || '<li>定义冲突，无直接 relocation</li>'}</ul></details>`;
  }).join('');
  $('#symbols').innerHTML = `<h2>符号候选与淘汰理由</h2>${decisions || '<p>没有需要裁决的引用。</p>'}`;
}

function renderVersions() {
  const result = currentPlan.result;
  const nodes = result.versionNodes.map(node => `
    <div class="node ${node.kind === 'DEFINITION' ? 'definition' : 'requirement'}">
      <strong>${esc(node.name)}</strong>
      <p class="reason">${node.kind} · index ${node.index} · ${node.defaultVersion ? '默认版本' : '非默认版本'} · ${esc(node.dependencyName || '')}</p>
      ${node.aliases.length > 1 ? `<p class="reason">别名：${node.aliases.map(esc).join(', ')}</p>` : ''}
    </div>`).join('');
  const edges = result.versionEdges.map(edge => `<tr><td>${esc(edge.sourceId)}</td><td>${esc(edge.label)}</td><td>${esc(edge.targetId)}</td></tr>`).join('');
  $('#versions').innerHTML = `<h2>版本图</h2><div class="graph">${nodes}</div>
    <h2>版本边</h2><div class="table-wrap"><table><thead><tr><th>来源</th><th>关系</th><th>目标</th></tr></thead><tbody>${edges}</tbody></table></div>`;
}

function renderRelocations() {
  const rows = currentPlan.result.objects.flatMap(object => object.references.map(ref => `
    <tr><td>${esc(ref.objectName)}</td><td><code>${esc(ref.relocationType)}</code>${ref.copy ? ' <span class="status COPY_RELOCATION">COPY</span>' : ''}</td>
    <td><strong>${esc(ref.symbolName)}</strong>${ref.requestedVersion ? `<code>@${esc(ref.requestedVersion)}</code>` : ''}</td>
    <td>${esc(ref.sectionName)}+${hex(ref.offset)}</td><td>${hex(ref.addend)}</td><td>${esc(ref.binding)}</td></tr>`)).join('');
  $('#relocations').innerHTML = `<h2>Relocation 列表</h2><div class="table-wrap"><table><thead><tr><th>对象</th><th>类型</th><th>符号</th><th>位置</th><th>addend</th><th>binding</th></tr></thead><tbody>${rows}</tbody></table></div>`;
}

function renderPlayback() {
  const events = currentPlan.result.events;
  const visible = events.slice(0, playbackPosition).map(event => `
    <div class="event"><strong>#${event.sequence} ${esc(event.title)}</strong>
    <div>${esc(event.detail)}</div><div class="event-meta">${esc(event.type)} · ${esc(JSON.stringify(event.state))}</div></div>`).join('');
  $('#playback').innerHTML = `<h2>Archive 抽取与符号表更新</h2>
    <button id="step-back" class="secondary">上一步</button>
    <button id="step-next">下一步</button>
    <button id="play" class="secondary">播放</button>
    <button id="reset" class="secondary">重置</button>
    <p class="reason">${playbackPosition} / ${events.length}</p>${visible || '<p class="reason">点击“下一步”开始。</p>'}`;
  $('#step-back').onclick = () => { playbackPosition = Math.max(0, playbackPosition - 1); renderPlayback(); };
  $('#step-next').onclick = () => { playbackPosition = Math.min(events.length, playbackPosition + 1); renderPlayback(); };
  $('#reset').onclick = () => { playbackPosition = 0; stop(); renderPlayback(); };
  $('#play').onclick = () => timer ? stop() : play();
}

function play() {
  $('#play').textContent = '暂停';
  timer = setInterval(() => {
    if (playbackPosition >= currentPlan.result.events.length) return stop();
    playbackPosition++;
    renderPlayback();
  }, 850);
}

function stop() {
  clearInterval(timer);
  timer = null;
  const button = $('#play');
  if (button) button.textContent = '播放';
}

$('#analyze-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const response = await fetch('/api/plans/analyze', { method: 'POST', body: form });
  if (!response.ok) return alert('裁决失败：' + await response.text());
  const plan = await response.json();
  await refreshPlans(plan.id);
});

$('#compare-button').addEventListener('click', async () => {
  const response = await fetch('/api/plans/compare', {
    method: 'POST', headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({leftPlanId: $('#compare-left').value, rightPlanId: $('#compare-right').value})
  });
  const comparison = await response.json();
  const changes = comparison.differences.map(diff => `<tr>
    <td><span class="status ${diff.change === 'PROVIDER_CHANGED' ? 'STRONG_CONFLICT' : 'AMBIGUOUS'}">${esc(diff.change)}</span></td>
    <td>${esc(diff.oldBinding?.symbolName)}</td><td>${esc(diff.oldBinding?.providerName)}</td>
    <td>${esc(diff.newBinding?.providerName)}</td><td>${esc(diff.explanation)}</td></tr>`).join('');
  const addressOnly = comparison.addressOnlyChanges.map(item => `<li>${esc(item)}</li>`).join('');
  $('#comparison').innerHTML = `<p><strong>${comparison.sameBindings ? '提供者完全一致' : '存在语义变化'}</strong></p>
    <div class="table-wrap"><table><tbody>${changes}</tbody></table></div>
    ${addressOnly ? `<details><summary>仅地址变化</summary><ul>${addressOnly}</ul></details>` : ''}`;
});

document.querySelectorAll('.tabs button').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach(item => item.classList.remove('active'));
  document.querySelectorAll('.tab-panel').forEach(item => item.classList.remove('active'));
  button.classList.add('active');
  $('#' + button.dataset.tab).classList.add('active');
}));

refreshPlans();
