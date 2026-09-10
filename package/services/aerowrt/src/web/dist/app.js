// AeroWrt WebUI Client Engine
let currentNodes = [];
let currentSubscriptions = [];
let activeNodeId = '';
let currentThemeMode = 'system'; // 'system' | 'dark' | 'light'
let currentImportTab = 'url';
let logInterval = null;

// ========== 主题管理 (跟随系统 / 暗黑 / 浅色) ==========
function initTheme() {
  const saved = localStorage.getItem('aerowrt-theme') || localStorage.getItem('v2rayn-theme') || 'system';
  setThemeMode(saved);

  // 监听系统深浅色切换
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (currentThemeMode === 'system') {
      applyTheme();
    }
  });
}

function cycleTheme() {
  if (currentThemeMode === 'system') {
    setThemeMode('dark');
  } else if (currentThemeMode === 'dark') {
    setThemeMode('light');
  } else {
    setThemeMode('system');
  }
}

function setThemeMode(mode) {
  currentThemeMode = mode;
  localStorage.setItem('aerowrt-theme', mode);
  applyTheme();
}

function applyTheme() {
  const root = document.documentElement;
  const themeText = document.getElementById('theme-text');

  if (currentThemeMode === 'dark') {
    root.setAttribute('data-theme', 'dark');
    if (themeText) themeText.textContent = '暗黑';
  } else if (currentThemeMode === 'light') {
    root.setAttribute('data-theme', 'light');
    if (themeText) themeText.textContent = '浅色';
  } else {
    root.removeAttribute('data-theme');
    if (themeText) themeText.textContent = '自动';
  }
}

// ========== 选项卡导航管理 ==========
function switchTab(tabId) {
  // 切换侧边栏高亮
  document.querySelectorAll('.nav-item').forEach(item => {
    if (item.getAttribute('data-tab') === tabId) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  });

  // 切换内容面板
  document.querySelectorAll('.tab-pane').forEach(pane => {
    if (pane.id === `tab-${tabId}`) {
      pane.classList.add('active');
    } else {
      pane.classList.remove('active');
    }
  });

  // 特殊 Tab 数据加载
  if (tabId === 'nodes') {
    renderNodesTable();
  } else if (tabId === 'subscription') {
    loadSubscriptions();
  } else if (tabId === 'dns') {
    loadSettings();
  } else if (tabId === 'logs') {
    loadLogs();
  }
}

// ========== 数据加载与渲染 ==========
async function loadStatus() {
  try {
    const res = await fetch('/api/status');
    const data = await res.json();

    activeNodeId = data.active_node || '';

    // 更新网关与状态卡片
    const gatewayStatus = document.getElementById('gateway-status');
    if (gatewayStatus) {
      if (data.core_running) {
        gatewayStatus.textContent = '🟢 运行中 (Sing-box 内核)';
        gatewayStatus.className = 'pill pill-success';
        gatewayStatus.style.cursor = 'default';
        gatewayStatus.onclick = null;
      } else {
        gatewayStatus.textContent = '🔴 内核未运行 (点击启动)';
        gatewayStatus.className = 'pill pill-danger';
        gatewayStatus.style.cursor = 'pointer';
        gatewayStatus.title = '点击尝试启动 / 重启 Sing-box 核心进程';
        gatewayStatus.onclick = () => restartCoreProcess();
      }
    }

    // 分流模式与策略模式
    const statsRouting = document.getElementById('stats-routing-mode');
    const statsStrategy = document.getElementById('stats-strategy-mode');
    const statsRoutingSub = document.getElementById('stats-routing-sub');

    if (statsRouting) {
      if (data.routing_mode === 'global') {
        statsRouting.textContent = '全局代理模式 (全部走代理)';
        if (statsRoutingSub) statsRoutingSub.textContent = '所有局域网流量均经由代理出口';
      } else if (data.routing_mode === 'direct') {
        statsRouting.textContent = '全局直连模式 (代理直通)';
        if (statsRoutingSub) statsRoutingSub.textContent = '所有局域网流量直连外网';
      } else {
        statsRouting.textContent = '绕过中国大陆 (推荐分流)';
        if (statsRoutingSub) statsRoutingSub.textContent = '国内直连 / 海外走代理';
      }
    }

    if (statsStrategy) {
      if (data.strategy_mode === 'urltest') {
        statsStrategy.textContent = '⚡ 自动优选最低延迟';
        statsStrategy.className = 'pill pill-success';
      } else {
        statsStrategy.textContent = '🎯 手动指定主节点';
        statsStrategy.className = 'pill pill-warning';
      }
    }

    const mosdnsBadge = document.getElementById('dns-badge');
    if (mosdnsBadge) {
      mosdnsBadge.textContent = `MosDNS (${data.mosdns_port || 5335})`;
    }

    const statsMosdns = document.getElementById('stats-mosdns-port');
    if (statsMosdns) {
      statsMosdns.textContent = `127.0.0.1:${data.mosdns_port || 5335}`;
    }

    const statsNodes = document.getElementById('stats-node-count');
    if (statsNodes) {
      statsNodes.textContent = `${data.total_nodes || 0} 个可用节点`;
    }

    const statsSubs = document.getElementById('stats-sub-count');
    if (statsSubs) {
      statsSubs.textContent = `${data.total_subs || 0} 个已保存订阅`;
    }

    updateActiveNodeDisplay();
  } catch (err) {
    console.error('Failed to load status:', err);
  }
}

async function loadNodes() {
  try {
    const res = await fetch('/api/nodes');
    currentNodes = await res.json();
    renderQuickNodes();
    renderNodesTable();
    updateActiveNodeDisplay();
  } catch (err) {
    console.error('Failed to load nodes:', err);
  }
}

function formatDelayBadge(delay) {
  if (delay > 0) {
    const cls = delay < 120 ? 'pill-success' : (delay < 280 ? 'pill-warning' : 'pill-danger');
    return `<span class="pill ${cls}">${delay} ms</span>`;
  }
  if (delay === -1) {
    return '<span class="pill pill-danger" title="无法通过代理连通目标网络或握手超时">超时 / 失败</span>';
  }
  return '<span class="text-muted">未测速</span>';
}

function updateActiveNodeDisplay() {
  const activeNode = currentNodes.find(n => n.id === activeNodeId) || currentNodes[0];
  const activeNameEl = document.getElementById('active-node-name');
  const activeDelayEl = document.getElementById('active-node-delay');
  const activeProtoEl = document.getElementById('active-node-proto');

  if (activeNode) {
    if (activeNameEl) activeNameEl.textContent = activeNode.tag || activeNode.server;
    if (activeDelayEl) {
      if (activeNode.delay_ms > 0) {
        activeDelayEl.textContent = `${activeNode.delay_ms} ms (真延迟)`;
        activeDelayEl.className = activeNode.delay_ms < 150 ? 'pill pill-success' : 'pill pill-warning';
      } else if (activeNode.delay_ms === -1) {
        activeDelayEl.textContent = '节点异常 (超时)';
        activeDelayEl.className = 'pill pill-danger';
      } else {
        activeDelayEl.textContent = '未测速';
        activeDelayEl.className = 'pill pill-warning';
      }
    }
    if (activeProtoEl) activeProtoEl.textContent = (activeNode.protocol || 'VLESS').toUpperCase();
  } else {
    if (activeNameEl) activeNameEl.textContent = '暂无节点 (请点击导入)';
    if (activeDelayEl) activeDelayEl.textContent = '-- ms';
  }
}

function renderQuickNodes() {
  const container = document.getElementById('nodes-container');
  if (!container) return;
  container.innerHTML = '';

  if (currentNodes.length === 0) {
    container.innerHTML = '<div style="grid-column: 1/-1; padding: 24px; text-align: center; color: var(--text-muted);">暂无代理节点，请点击右上角「+ 导入订阅 / 节点」导入</div>';
    return;
  }

  currentNodes.slice(0, 12).forEach(node => {
    const isActive = node.id === activeNodeId;
    const card = document.createElement('div');
    card.className = `node-card ${isActive ? 'active' : ''}`;
    card.onclick = () => switchNode(node.id);

    let delayBadge = formatDelayBadge(node.delay_ms);

    let chainBadge = '';
    if (node.chain_node) {
      const parentNode = currentNodes.find(n => n.id === node.chain_node);
      const parentName = parentNode ? (parentNode.tag || parentNode.server) : node.chain_node;
      chainBadge = `<span class="badge-chain" title="前置跳板代理: ${escapeHtml(parentName)}">🔗 ${escapeHtml(parentName)}</span>`;
    }

    card.innerHTML = `
      <div class="node-header">
        <span class="node-tag">${escapeHtml(node.tag || node.server)}</span>
        ${isActive ? '<span class="pill pill-success">当前选中</span>' : ''}
      </div>
      <div class="node-info">
        <span>${escapeHtml(node.server)}:${node.port}</span>
        ${chainBadge}
      </div>
      <div class="node-footer">
        <span class="pill">${(node.protocol || 'vless').toUpperCase()}</span>
        ${delayBadge}
      </div>
    `;
    container.appendChild(card);
  });
}

function renderNodesTable() {
  const tbody = document.getElementById('nodes-table-body');
  if (!tbody) return;
  tbody.innerHTML = '';

  if (currentNodes.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 24px; color: var(--text-muted);">暂无节点，请点击上方「+ 手动添加节点」或「导入订阅/文本」</td></tr>';
    return;
  }

  currentNodes.forEach(node => {
    const isActive = node.id === activeNodeId;
    const tr = document.createElement('tr');

    let chainDisplay = '<span class="text-muted" style="font-size: 11px;">直连出站</span>';
    if (node.chain_node) {
      const parentNode = currentNodes.find(n => n.id === node.chain_node);
      const parentName = parentNode ? (parentNode.tag || parentNode.server) : node.chain_node;
      chainDisplay = `<span class="badge-chain" title="前置跳板代理">🔗 经由: ${escapeHtml(parentName)}</span>`;
    }

    tr.innerHTML = `
      <td><strong>${escapeHtml(node.tag || node.server)}</strong></td>
      <td><span class="pill">${(node.protocol || 'vless').toUpperCase()}</span></td>
      <td><code>${escapeHtml(node.server)}</code></td>
      <td>${node.port}</td>
      <td>${formatDelayBadge(node.delay_ms)}</td>
      <td>${chainDisplay}</td>
      <td>${isActive ? '<span class="text-success" style="font-weight:600;">🟢 活跃中</span>' : '<span class="text-muted">备用</span>'}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="switchNode('${node.id}')" ${isActive ? 'disabled' : ''}>设为主</button>
        <button class="btn btn-secondary btn-sm" onclick="pingSingleNode('${node.id}', this)" title="测真延迟">⚡ 测速</button>
        <button class="btn btn-secondary btn-sm" onclick="openChainModal('${node.id}')" title="设置前置链式跳板代理">🔗 链式</button>
        <button class="btn btn-secondary btn-sm" onclick="openNodeEditorModal('${node.id}')" title="编辑节点参数">✏️</button>
        <button class="btn btn-secondary btn-sm" style="color: var(--danger);" onclick="deleteNode('${node.id}')" title="删除">🗑️</button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

async function loadSubscriptions() {
  try {
    const res = await fetch('/api/subscriptions');
    currentSubscriptions = await res.json();
    const tbody = document.getElementById('subs-table-body');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (!currentSubscriptions || currentSubscriptions.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; padding: 24px; color: var(--text-muted);">暂无已保存的订阅源</td></tr>';
      return;
    }

    currentSubscriptions.forEach(sub => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td><strong>${escapeHtml(sub.name)}</strong></td>
        <td><code style="word-break: break-all;">${escapeHtml(sub.url)}</code></td>
        <td><span class="pill pill-success">${sub.node_count} 节点</span></td>
        <td>${escapeHtml(sub.updated_at || '--')}</td>
        <td>
          <button class="btn btn-primary btn-sm" onclick="updateSubscription('${escapeHtml(sub.url)}')">🔄 同步更新</button>
          <button class="btn btn-secondary btn-sm" style="color: var(--danger); margin-left: 4px;" onclick="deleteSubscription('${escapeHtml(sub.id)}', '${escapeHtml(sub.url)}')">🗑️ 删除</button>
        </td>
      `;
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.error('Failed to load subscriptions:', err);
  }
}

async function loadLogs() {
  try {
    const res = await fetch('/api/logs');
    const data = await res.json();
    const logs = data.logs || [];

    const miniConsole = document.getElementById('log-console-mini');
    const fullConsole = document.getElementById('log-console-full');

    const html = logs.map(line => `<div class="log-line">${escapeHtml(line)}</div>`).join('');

    if (miniConsole) {
      miniConsole.innerHTML = html;
      miniConsole.scrollTop = miniConsole.scrollHeight;
    }
    if (fullConsole) {
      fullConsole.innerHTML = html;
      fullConsole.scrollTop = fullConsole.scrollHeight;
    }
  } catch (err) {
    console.error('Failed to load logs:', err);
  }
}

function refreshLogs() {
  loadLogs();
}

function clearLogsConsole() {
  const fullConsole = document.getElementById('log-console-full');
  if (fullConsole) fullConsole.innerHTML = '<div class="log-line text-muted">控制台日志已清空</div>';
}

// ========== 节点与服务交互 ==========
async function switchNode(nodeId) {
  try {
    const res = await fetch('/api/nodes/switch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ node_id: nodeId }),
    });
    const data = await res.json();
    if (data.success) {
      activeNodeId = nodeId;
      renderQuickNodes();
      renderNodesTable();
      updateActiveNodeDisplay();
      loadLogs();
    }
  } catch (err) {
    alert('切换节点失败: ' + err.message);
  }
}

async function deleteNode(nodeId) {
  if (!confirm('确定要删除此节点吗？')) return;
  try {
    const res = await fetch('/api/nodes/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: nodeId }),
    });
    const data = await res.json();
    if (data.success) {
      currentNodes = currentNodes.filter(n => n.id !== nodeId);
      renderQuickNodes();
      renderNodesTable();
      updateActiveNodeDisplay();
    }
  } catch (err) {
    alert('删除失败: ' + err.message);
  }
}

async function pingSingleNode(nodeId, btn) {
  // 支持临时覆盖测速 URL：按住 Alt 点击测速按钮，弹窗输入自定义 URL
  let customUrl = '';
  if (window.event && window.event.altKey) {
    const defUrl = (document.getElementById('setting-test-url')?.value || 'https://www.gstatic.com/generate_204');
    customUrl = prompt('自定义测速 URL（留空使用全局默认）', defUrl) || '';
    if (customUrl === null) customUrl = ''; // 用户取消
  }

  if (btn) {
    btn.disabled = true;
    btn.textContent = '⏳...';
  }
  try {
    const body = { id: nodeId };
    if (customUrl) body.test_url = customUrl;
    const res = await fetch('/api/nodes/ping', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    const results = await res.json();
    if (results[nodeId] !== undefined) {
      const node = currentNodes.find(n => n.id === nodeId);
      if (node) {
        node.delay_ms = results[nodeId];
        renderQuickNodes();
        renderNodesTable();
        updateActiveNodeDisplay();
      }
    }
  } catch (err) {
    alert('单节点测速失败: ' + err.message);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = '⚡ 测速';
    }
  }
}

async function pingAllNodes() {
  const btn = document.getElementById('btn-ping-all');
  if (btn) {
    btn.disabled = true;
    btn.textContent = '⏳ 真延迟测速中...';
  }

  try {
    const res = await fetch('/api/nodes/ping', { method: 'POST' });
    const results = await res.json();
    currentNodes.forEach(node => {
      if (results[node.id] !== undefined) {
        node.delay_ms = results[node.id];
      }
    });
    renderQuickNodes();
    renderNodesTable();
    updateActiveNodeDisplay();
  } catch (err) {
    alert('测速失败: ' + err.message);
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.textContent = '⚡ 批量真延迟测速';
    }
  }
}

// ========== 导入订阅 / 节点弹窗逻辑 ==========
function openImportModal() {
  const modal = document.getElementById('modal-import');
  const msgEl = document.getElementById('import-status-msg');
  if (msgEl) msgEl.textContent = '';
  if (modal) modal.classList.add('open');
}

function closeImportModal() {
  const modal = document.getElementById('modal-import');
  if (modal) modal.classList.remove('open');
}

function switchImportTab(tab) {
  currentImportTab = tab;
  const tabUrl = document.getElementById('tab-btn-url');
  const tabText = document.getElementById('tab-btn-text');
  const paneUrl = document.getElementById('import-pane-url');
  const paneText = document.getElementById('import-pane-text');

  if (tab === 'url') {
    tabUrl.classList.add('active');
    tabText.classList.remove('active');
    paneUrl.style.display = 'block';
    paneText.style.display = 'none';
  } else {
    tabUrl.classList.remove('active');
    tabText.classList.add('active');
    paneUrl.style.display = 'none';
    paneText.style.display = 'block';
  }
}

async function doImportSubscription() {
  const btn = document.getElementById('btn-do-import');
  const msgEl = document.getElementById('import-status-msg');

  let payload = {};
  if (currentImportTab === 'url') {
    const url = document.getElementById('import-sub-url').value.trim();
    const name = document.getElementById('import-sub-name').value.trim();
    if (!url) {
      if (msgEl) {
        msgEl.style.color = 'var(--danger)';
        msgEl.textContent = '请输入有效的订阅 URL 链接';
      }
      return;
    }
    payload = { url, name };
  } else {
    const content = document.getElementById('import-sub-content').value.trim();
    if (!content) {
      if (msgEl) {
        msgEl.style.color = 'var(--danger)';
        msgEl.textContent = '请粘贴节点链接或 Base64 订阅文本';
      }
      return;
    }
    payload = { content };
  }

  if (btn) btn.disabled = true;
  if (msgEl) {
    msgEl.style.color = 'var(--accent)';
    msgEl.textContent = '⏳ 正在拉取并解析节点数据，请稍候...';
  }

  try {
    const res = await fetch('/api/nodes/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const errText = await res.text();
      throw new Error(errText);
    }

    const data = await res.json();
    if (msgEl) {
      msgEl.style.color = 'var(--success)';
      msgEl.textContent = `✅ 成功解析并导入 ${data.imported} 个节点！`;
    }

    setTimeout(() => {
      closeImportModal();
      loadNodes();
      loadStatus();
      loadSubscriptions();
      loadLogs();
    }, 800);
  } catch (err) {
    if (msgEl) {
      msgEl.style.color = 'var(--danger)';
      msgEl.textContent = '❌ 导入失败: ' + err.message;
    }
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function updateSubscription(subUrl) {
  try {
    const res = await fetch('/api/nodes/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url: subUrl }),
    });
    const data = await res.json();
    if (data.success) {
      alert(`订阅更新成功！已同步 ${data.imported} 个节点。`);
      loadNodes();
      loadStatus();
      loadSubscriptions();
    }
  } catch (err) {
    alert('更新失败: ' + err.message);
  }
}

async function deleteSubscription(subId, subUrl) {
  const sub = (currentSubscriptions || []).find(s => s.id === subId);
  const displayName = sub ? `${sub.name} (${sub.node_count} 个节点)` : (subUrl || subId);
  if (!confirm(`确定要删除订阅源「${displayName}」吗？\n\n注意：仅删除订阅记录。该订阅此前导入的节点将保留在节点管理列表中，如需清理请到节点管理页手动删除。`)) return;
  try {
    const res = await fetch('/api/subscriptions/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: subId, url: subUrl }),
    });
    const data = await res.json();
    if (data.success) {
      loadSubscriptions();
      loadStatus();
    } else {
      alert('删除失败: ' + (data.error || '未知错误'));
    }
  } catch (err) {
    alert('删除请求失败: ' + err.message);
  }
}

// ========== 内核在线升级逻辑 ==========
function openUpgradeModal() {
  const modal = document.getElementById('modal-upgrade');
  if (modal) modal.classList.add('open');
  checkUpdateNow();
}

function closeUpgradeModal() {
  const modal = document.getElementById('modal-upgrade');
  if (modal) modal.classList.remove('open');
}

function handleProxyChange() {
  const sel = document.getElementById('proxy-select');
  const customGroup = document.getElementById('custom-proxy-group');
  if (sel && customGroup) {
    customGroup.style.display = sel.value === 'custom' ? 'flex' : 'none';
  }
}

function getSelectedProxy() {
  const sel = document.getElementById('proxy-select');
  if (!sel) return '';
  if (sel.value === 'custom') {
    const input = document.getElementById('custom-proxy-input');
    return input ? input.value.trim() : '';
  }
  return sel.value;
}

async function checkUpdateNow() {
  const latestEl = document.getElementById('modal-latest-version');
  const msgEl = document.getElementById('upgrade-status-msg');
  if (latestEl) latestEl.textContent = '正在检测最新版本...';
  if (msgEl) msgEl.textContent = '';

  const proxy = getSelectedProxy();
  try {
    const res = await fetch(`/api/core/check?core=sing-box&proxy=${encodeURIComponent(proxy)}`);
    const data = await res.json();
    if (latestEl) {
      latestEl.textContent = `${data.latest_version} (${data.has_update ? '发现更新' : '已是最新'})`;
    }
    if (msgEl && data.download_url) {
      msgEl.textContent = `下载地址: ${data.download_url}`;
    }
  } catch (err) {
    if (latestEl) latestEl.textContent = '检测超时，可选择 GitHub 镜像代理重试';
  }
}

async function triggerCoreUpgrade() {
  const btn = document.getElementById('btn-do-upgrade');
  const msgEl = document.getElementById('upgrade-status-msg');
  if (btn) btn.disabled = true;
  if (msgEl) msgEl.textContent = '正在向后台发送升级指令并拉取二进制包...';

  const proxy = getSelectedProxy();
  try {
    const res = await fetch('/api/core/upgrade', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ core: 'sing-box', proxy: proxy }),
    });
    const data = await res.json();
    if (msgEl) {
      msgEl.style.color = 'var(--success)';
      msgEl.textContent = data.message || '升级成功！';
    }
    setTimeout(() => {
      closeUpgradeModal();
      loadLogs();
    }, 1500);
  } catch (err) {
    if (msgEl) {
      msgEl.style.color = 'var(--danger)';
      msgEl.textContent = '升级触发失败: ' + err.message;
    }
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function restartCoreProcess() {
  const gatewayStatus = document.getElementById('gateway-status');
  if (gatewayStatus) gatewayStatus.textContent = '⏳ 启动内核中...';
  try {
    const res = await fetch('/api/core/restart', { method: 'POST' });
    const data = await res.json();
    if (data.success) {
      loadStatus();
      loadLogs();
    } else {
      alert('启动失败: ' + (data.error || '未知错误'));
      loadStatus();
    }
  } catch (err) {
    alert('请求失败: ' + err.message);
    loadStatus();
  }
}

// ========== 系统设置与分流规则管理 ==========
async function loadSettings() {
  try {
    const res = await fetch('/api/settings');
    const data = await res.json();

    const routingMode = document.getElementById('setting-routing-mode');
    if (routingMode && data.routing_mode) routingMode.value = data.routing_mode;

    const strategyMode = document.getElementById('setting-strategy-mode');
    if (strategyMode && data.strategy_mode) strategyMode.value = data.strategy_mode;

    const dnsMode = document.getElementById('setting-dns-mode');
    if (dnsMode && data.dns_mode) dnsMode.value = data.dns_mode;
    handleDnsModeChange();

    const customDnsServer = document.getElementById('setting-custom-dns');
    if (customDnsServer) {
      customDnsServer.value = (data.custom_dns_servers || []).join('\n');
    }

    const mosdnsPort = document.getElementById('setting-mosdns-port');
    if (mosdnsPort && data.mosdns_port) mosdnsPort.value = data.mosdns_port;

    const testUrl = document.getElementById('setting-test-url');
    if (testUrl && data.test_url) testUrl.value = data.test_url;

    const urltestInterval = document.getElementById('setting-urltest-interval');
    if (urltestInterval && data.urltest_interval_mins) urltestInterval.value = String(data.urltest_interval_mins);

    const subAutoUpdate = document.getElementById('setting-sub-auto-update');
    if (subAutoUpdate) subAutoUpdate.value = String(data.auto_update_sub_hours || 0);

    const directDomains = document.getElementById('setting-direct-domains');
    if (directDomains) directDomains.value = (data.direct_domains || []).join('\n');

    const proxyDomains = document.getElementById('setting-proxy-domains');
    if (proxyDomains) proxyDomains.value = (data.proxy_domains || []).join('\n');

    const directIps = document.getElementById('setting-direct-ips');
    if (directIps) directIps.value = (data.direct_ips || []).join('\n');

    const proxyIps = document.getElementById('setting-proxy-ips');
    if (proxyIps) proxyIps.value = (data.proxy_ips || []).join('\n');
  } catch (err) {
    console.error('Failed to load settings:', err);
  }
}

async function saveSettings() {
  const statusMsg = document.getElementById('settings-status-msg');
  if (statusMsg) {
    statusMsg.style.color = 'var(--accent)';
    statusMsg.textContent = '⏳ 正在保存并向 Sing-box 内核应用新配置...';
  }

  const parseLines = (id) => {
    const el = document.getElementById(id);
    if (!el) return [];
    return el.value
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0 && !s.startsWith('#'));
  };

  const payload = {
    routing_mode: document.getElementById('setting-routing-mode')?.value || 'bypass_cn',
    strategy_mode: document.getElementById('setting-strategy-mode')?.value || 'manual',
    dns_mode: document.getElementById('setting-dns-mode')?.value || 'mosdns',
    mosdns_port: parseInt(document.getElementById('setting-mosdns-port')?.value || '5335', 10),
    test_url: document.getElementById('setting-test-url')?.value?.trim() || 'https://www.gstatic.com/generate_204',
    urltest_interval_mins: parseInt(document.getElementById('setting-urltest-interval')?.value || '10', 10),
    auto_update_sub_hours: parseInt(document.getElementById('setting-sub-auto-update')?.value || '0', 10),
    custom_dns_servers: parseLines('setting-custom-dns'),
    direct_domains: parseLines('setting-direct-domains'),
    proxy_domains: parseLines('setting-proxy-domains'),
    direct_ips: parseLines('setting-direct-ips'),
    proxy_ips: parseLines('setting-proxy-ips'),
  };

  try {
    const res = await fetch('/api/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    if (data.success) {
      if (statusMsg) {
        statusMsg.style.color = 'var(--success)';
        statusMsg.textContent = '✅ 设置保存成功！核心配置已平滑重载生效';
        setTimeout(() => { statusMsg.textContent = ''; }, 4000);
      }
      loadStatus();
      loadLogs();
    } else {
      if (statusMsg) {
        statusMsg.style.color = 'var(--danger)';
        statusMsg.textContent = '❌ 保存失败: ' + (data.error || '未知错误');
      }
    }
  } catch (err) {
    if (statusMsg) {
      statusMsg.style.color = 'var(--danger)';
      statusMsg.textContent = '❌ 网络请求失败: ' + err.message;
    }
  }
}

function saveDnsSettings() {
  saveSettings();
}

// 切换 DNS 模式时显示/隐藏自定义 DNS 输入框，并在 mosdns 模式下提示端口必填
function handleDnsModeChange() {
  const mode = document.getElementById('setting-dns-mode')?.value || 'mosdns';
  const customGroup = document.getElementById('group-custom-dns');
  if (customGroup) {
    customGroup.style.display = (mode === 'custom') ? 'flex' : 'none';
  }
}

// ========== 链式前置跳板代理 (Detour) 管理 ==========
let currentChainTargetNodeId = '';

function openChainModal(nodeId) {
  const node = currentNodes.find(n => n.id === nodeId);
  if (!node) return;

  currentChainTargetNodeId = nodeId;
  const nameEl = document.getElementById('chain-target-node-name');
  const serverEl = document.getElementById('chain-target-node-server');
  const sel = document.getElementById('chain-select-parent');
  const msgEl = document.getElementById('chain-status-msg');

  if (nameEl) nameEl.textContent = node.tag || node.server;
  if (serverEl) serverEl.textContent = `${(node.protocol || 'vless').toUpperCase()} · ${node.server}:${node.port}`;
  if (msgEl) msgEl.textContent = '';

  if (sel) {
    sel.innerHTML = '<option value="">(直连 - 不使用前置跳板代理)</option>';
    currentNodes.forEach(n => {
      if (n.id !== nodeId) {
        const opt = document.createElement('option');
        opt.value = n.id;
        opt.textContent = `${n.tag || n.server} (${(n.protocol || '').toUpperCase()} · ${n.server}:${n.port})`;
        if (n.id === node.chain_node) {
          opt.selected = true;
        }
        sel.appendChild(opt);
      }
    });
  }

  const modal = document.getElementById('modal-chain');
  if (modal) modal.classList.add('open');
}

function closeChainModal() {
  const modal = document.getElementById('modal-chain');
  if (modal) modal.classList.remove('open');
}

async function doSaveChainProxy() {
  if (!currentChainTargetNodeId) return;
  const sel = document.getElementById('chain-select-parent');
  const parentId = sel ? sel.value : '';
  const msgEl = document.getElementById('chain-status-msg');
  const btn = document.getElementById('btn-save-chain');

  if (btn) btn.disabled = true;
  if (msgEl) {
    msgEl.style.color = 'var(--accent)';
    msgEl.textContent = '正在应用链式代理拓扑并重载核心...';
  }

  try {
    const res = await fetch('/api/nodes/chain', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        node_id: currentChainTargetNodeId,
        chain_node_id: parentId,
      }),
    });
    const data = await res.json();
    if (data.success) {
      if (msgEl) {
        msgEl.style.color = 'var(--success)';
        msgEl.textContent = '✅ 链式代理设置已更新！';
      }
      setTimeout(() => {
        closeChainModal();
        loadNodes();
        loadLogs();
      }, 500);
    } else {
      if (msgEl) {
        msgEl.style.color = 'var(--danger)';
        msgEl.textContent = '❌ ' + (data.error || '设置失败');
      }
    }
  } catch (err) {
    if (msgEl) {
      msgEl.style.color = 'var(--danger)';
      msgEl.textContent = '❌ 网络请求失败: ' + err.message;
    }
  } finally {
    if (btn) btn.disabled = false;
  }
}

// ========== 手动录入与编辑代理节点管理 ==========
function openNodeEditorModal(nodeId = '') {
  const titleEl = document.getElementById('node-editor-title');
  const msgEl = document.getElementById('node-editor-msg');
  if (msgEl) msgEl.textContent = '';

  const idInput = document.getElementById('node-edit-id');
  const tagInput = document.getElementById('node-edit-tag');
  const protoSel = document.getElementById('node-edit-proto');
  const serverInput = document.getElementById('node-edit-server');
  const portInput = document.getElementById('node-edit-port');
  const uuidInput = document.getElementById('node-edit-uuid');
  const pwdInput = document.getElementById('node-edit-password');
  const methodSel = document.getElementById('node-edit-method');
  const netSel = document.getElementById('node-edit-network');
  const pathInput = document.getElementById('node-edit-path');
  const secSel = document.getElementById('node-edit-security');
  const sniInput = document.getElementById('node-edit-sni');
  const pubkeyInput = document.getElementById('node-edit-publickey');
  const shortidInput = document.getElementById('node-edit-shortid');
  const chainSel = document.getElementById('node-edit-chain');

  if (chainSel) {
    chainSel.innerHTML = '<option value="">(直连 - 无前置跳板)</option>';
    currentNodes.forEach(n => {
      if (n.id !== nodeId) {
        const opt = document.createElement('option');
        opt.value = n.id;
        opt.textContent = `${n.tag || n.server} (${(n.protocol || '').toUpperCase()})`;
        chainSel.appendChild(opt);
      }
    });
  }

  if (nodeId) {
    const node = currentNodes.find(n => n.id === nodeId);
    if (!node) return;
    if (titleEl) titleEl.textContent = `✏️ 编辑节点 [${node.tag || node.server}]`;
    if (idInput) idInput.value = node.id;
    if (tagInput) tagInput.value = node.tag || '';
    if (protoSel) protoSel.value = node.protocol || 'vless';
    if (serverInput) serverInput.value = node.server || '';
    if (portInput) portInput.value = node.port || 443;
    if (uuidInput) uuidInput.value = node.uuid || '';
    if (pwdInput) pwdInput.value = node.password || '';
    if (methodSel && node.method) methodSel.value = node.method;
    if (netSel && node.network) netSel.value = node.network;
    if (pathInput) pathInput.value = node.path || '/';
    if (secSel && node.security) secSel.value = node.security;
    if (sniInput) sniInput.value = node.sni || '';
    if (pubkeyInput) pubkeyInput.value = node.public_key || '';
    if (shortidInput) shortidInput.value = node.short_id || '';
    if (chainSel) chainSel.value = node.chain_node || '';
  } else {
    if (titleEl) titleEl.textContent = '➕ 手动添加代理节点';
    if (idInput) idInput.value = '';
    if (tagInput) tagInput.value = '';
    if (protoSel) protoSel.value = 'vless';
    if (serverInput) serverInput.value = '';
    if (portInput) portInput.value = 443;
    if (uuidInput) uuidInput.value = '';
    if (pwdInput) pwdInput.value = '';
    if (pathInput) pathInput.value = '/';
    if (secSel) secSel.value = 'none';
    if (sniInput) sniInput.value = '';
    if (pubkeyInput) pubkeyInput.value = '';
    if (shortidInput) shortidInput.value = '';
    if (chainSel) chainSel.value = '';
  }

  handleProtocolChange();
  handleSecurityChange();

  const modal = document.getElementById('modal-node-editor');
  if (modal) modal.classList.add('open');
}

function closeNodeEditorModal() {
  const modal = document.getElementById('modal-node-editor');
  if (modal) modal.classList.remove('open');
}

function handleProtocolChange() {
  const proto = document.getElementById('node-edit-proto')?.value || 'vless';
  const groupUuid = document.getElementById('group-node-uuid');
  const groupPwd = document.getElementById('group-node-password');
  const groupMethod = document.getElementById('group-node-method');

  if (proto === 'vless' || proto === 'vmess') {
    if (groupUuid) groupUuid.style.display = 'flex';
    if (groupPwd) groupPwd.style.display = 'none';
  } else {
    if (groupUuid) groupUuid.style.display = 'none';
    if (groupPwd) groupPwd.style.display = 'flex';
  }

  if (proto === 'shadowsocks') {
    if (groupMethod) groupMethod.style.display = 'flex';
  } else {
    if (groupMethod) groupMethod.style.display = 'none';
  }
}

function handleSecurityChange() {
  const sec = document.getElementById('node-edit-security')?.value || 'none';
  const groupReality = document.getElementById('group-node-reality');
  if (groupReality) {
    groupReality.style.display = sec === 'reality' ? 'flex' : 'none';
  }
}

async function doSaveNode() {
  const id = document.getElementById('node-edit-id')?.value || '';
  const tag = document.getElementById('node-edit-tag')?.value?.trim() || '';
  const proto = document.getElementById('node-edit-proto')?.value || 'vless';
  const server = document.getElementById('node-edit-server')?.value?.trim() || '';
  const port = parseInt(document.getElementById('node-edit-port')?.value || '443', 10);
  const uuid = document.getElementById('node-edit-uuid')?.value?.trim() || '';
  const password = document.getElementById('node-edit-password')?.value?.trim() || '';
  const method = document.getElementById('node-edit-method')?.value || '';
  const network = document.getElementById('node-edit-network')?.value || 'tcp';
  const path = document.getElementById('node-edit-path')?.value?.trim() || '/';
  const security = document.getElementById('node-edit-security')?.value || 'none';
  const sni = document.getElementById('node-edit-sni')?.value?.trim() || '';
  const publicKey = document.getElementById('node-edit-publickey')?.value?.trim() || '';
  const shortId = document.getElementById('node-edit-shortid')?.value?.trim() || '';
  const chainNode = document.getElementById('node-edit-chain')?.value || '';

  const msgEl = document.getElementById('node-editor-msg');
  const btn = document.getElementById('btn-save-node');

  if (!server || isNaN(port) || port <= 0) {
    if (msgEl) {
      msgEl.style.color = 'var(--danger)';
      msgEl.textContent = '❌ 请填写合法的服务器地址与端口！';
    }
    return;
  }

  const payload = {
    id: id,
    tag: tag,
    protocol: proto,
    server: server,
    port: port,
    uuid: uuid,
    password: password,
    method: method,
    network: network,
    path: path,
    security: security,
    sni: sni,
    public_key: publicKey,
    short_id: shortId,
    chain_node: chainNode,
  };

  const endpoint = id ? '/api/nodes/edit' : '/api/nodes/add';

  if (btn) btn.disabled = true;
  if (msgEl) {
    msgEl.style.color = 'var(--accent)';
    msgEl.textContent = '正在保存节点并重新构建 Sing-box 配置...';
  }

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    if (data.success) {
      if (msgEl) {
        msgEl.style.color = 'var(--success)';
        msgEl.textContent = '✅ 保存成功！';
      }
      setTimeout(() => {
        closeNodeEditorModal();
        loadNodes();
        loadStatus();
        loadLogs();
      }, 500);
    } else {
      if (msgEl) {
        msgEl.style.color = 'var(--danger)';
        msgEl.textContent = '❌ ' + (data.error || '保存失败');
      }
    }
  } catch (err) {
    if (msgEl) {
      msgEl.style.color = 'var(--danger)';
      msgEl.textContent = '❌ 请求失败: ' + err.message;
    }
  } finally {
    if (btn) btn.disabled = false;
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ========== 出站分组管理 (Outbound Groups) ==========
let currentGroups = [];

async function loadGroups() {
  try {
    const res = await fetch('/api/groups');
    currentGroups = await res.json() || [];
    renderGroupsTable();
  } catch (err) {
    console.error('Failed to load groups:', err);
  }
}

function renderGroupsTable() {
  const tbody = document.getElementById('groups-table-body');
  if (!tbody) return;
  tbody.innerHTML = '';

  if (!currentGroups || currentGroups.length === 0) {
    tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; padding: 24px; color: var(--text-muted);">暂无分组，点击右上角「+ 新建分组」创建</td></tr>';
    return;
  }

  currentGroups.forEach(g => {
    const nodeNames = (g.nodes || []).map(nid => {
      const n = currentNodes.find(x => x.id === nid);
      return n ? (n.tag || n.server) : nid;
    });
    const typeBadge = {
      'urltest': '<span class="pill pill-success">URLTest 自动测速</span>',
      'selector': '<span class="pill pill-warning">Selector 手选</span>',
      'loadbalance': '<span class="pill pill-info">LoadBalance 负载均衡</span>',
      'failover': '<span class="pill pill-warning">Failover 故障转移</span>',
    }[g.type] || `<span class="pill">${escapeHtml(g.type)}</span>`;

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>group-${escapeHtml(g.tag)}</strong></td>
      <td>${typeBadge}</td>
      <td style="max-width: 340px; word-break: break-all;">
        <span class="pill pill-success">${nodeNames.length} 个</span>
        <span style="font-size: 11px; color: var(--text-muted); margin-left: 6px;">${escapeHtml(nodeNames.join('、'))}</span>
      </td>
      <td>${g.interval || 300}s</td>
      <td>${g.tolerance || 50}ms</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="openGroupModal('${escapeHtml(g.id)}')">✏️ 编辑</button>
        <button class="btn btn-secondary btn-sm" style="color: var(--danger); margin-left: 4px;" onclick="deleteGroup('${escapeHtml(g.id)}', '${escapeHtml(g.tag)}')">🗑️ 删除</button>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

function openGroupModal(groupId = '') {
  const titleEl = document.getElementById('group-modal-title');
  const msgEl = document.getElementById('group-modal-msg');
  const idInput = document.getElementById('group-edit-id');
  const tagInput = document.getElementById('group-edit-tag');
  const typeSel = document.getElementById('group-edit-type');
  const nodesSel = document.getElementById('group-edit-nodes');
  const intervalInput = document.getElementById('group-edit-interval');
  const toleranceInput = document.getElementById('group-edit-tolerance');

  if (msgEl) msgEl.textContent = '';

  // 渲染节点多选框
  if (nodesSel) {
    nodesSel.innerHTML = '';
    currentNodes.forEach(n => {
      const opt = document.createElement('option');
      opt.value = n.id;
      opt.textContent = `${n.tag || n.server} (${(n.protocol || '').toUpperCase()} · ${n.server}:${n.port})`;
      nodesSel.appendChild(opt);
    });
  }

  if (groupId) {
    const g = (currentGroups || []).find(x => x.id === groupId);
    if (!g) return;
    if (titleEl) titleEl.textContent = `✏️ 编辑分组 [${g.tag}]`;
    if (idInput) idInput.value = g.id;
    if (tagInput) tagInput.value = g.tag;
    if (typeSel) typeSel.value = g.type || 'urltest';
    if (intervalInput) intervalInput.value = g.interval || 300;
    if (toleranceInput) toleranceInput.value = g.tolerance || 50;
    if (nodesSel) {
      const nodeSet = new Set(g.nodes || []);
      Array.from(nodesSel.options).forEach(opt => {
        opt.selected = nodeSet.has(opt.value);
      });
    }
  } else {
    if (titleEl) titleEl.textContent = '➕ 新建出站分组';
    if (idInput) idInput.value = '';
    if (tagInput) tagInput.value = '';
    if (typeSel) typeSel.value = 'urltest';
    if (intervalInput) intervalInput.value = 300;
    if (toleranceInput) toleranceInput.value = 50;
    if (nodesSel) Array.from(nodesSel.options).forEach(o => o.selected = false);
  }

  const modal = document.getElementById('modal-group');
  if (modal) modal.classList.add('open');
}

function closeGroupModal() {
  const modal = document.getElementById('modal-group');
  if (modal) modal.classList.remove('open');
}

async function doSaveGroup() {
  const id = document.getElementById('group-edit-id')?.value || '';
  const tag = document.getElementById('group-edit-tag')?.value?.trim() || '';
  const type = document.getElementById('group-edit-type')?.value || 'urltest';
  const interval = parseInt(document.getElementById('group-edit-interval')?.value || '300', 10);
  const tolerance = parseInt(document.getElementById('group-edit-tolerance')?.value || '50', 10);
  const nodesSel = document.getElementById('group-edit-nodes');
  const nodes = nodesSel ? Array.from(nodesSel.selectedOptions).map(o => o.value) : [];

  const msgEl = document.getElementById('group-modal-msg');
  const btn = document.getElementById('btn-save-group');

  if (!tag) {
    if (msgEl) { msgEl.style.color = 'var(--danger)'; msgEl.textContent = '❌ 请填写分组名称 (Tag)'; }
    return;
  }
  if (nodes.length === 0) {
    if (msgEl) { msgEl.style.color = 'var(--danger)'; msgEl.textContent = '❌ 请至少选择一个节点'; }
    return;
  }

  const payload = { id, tag, type, nodes, interval, tolerance };

  if (btn) btn.disabled = true;
  if (msgEl) { msgEl.style.color = 'var(--accent)'; msgEl.textContent = '正在保存分组并重载核心...'; }

  try {
    const res = await fetch('/api/groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const data = await res.json();
    if (data.success) {
      if (msgEl) { msgEl.style.color = 'var(--success)'; msgEl.textContent = '✅ 分组已保存，sing-box 配置已重载！'; }
      setTimeout(() => {
        closeGroupModal();
        loadGroups();
        loadLogs();
      }, 500);
    } else {
      if (msgEl) { msgEl.style.color = 'var(--danger)'; msgEl.textContent = '❌ ' + (data.error || '保存失败'); }
    }
  } catch (err) {
    if (msgEl) { msgEl.style.color = 'var(--danger)'; msgEl.textContent = '❌ 请求失败: ' + err.message; }
  } finally {
    if (btn) btn.disabled = false;
  }
}

async function deleteGroup(groupId, groupTag) {
  if (!confirm(`确定要删除分组「group-${groupTag}」吗？\n\n该分组将从 sing-box 配置中移除，引用它的策略将回落到 auto-best。`)) return;
  try {
    const res = await fetch('/api/groups/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: groupId }),
    });
    const data = await res.json();
    if (data.success) {
      loadGroups();
      loadLogs();
    } else {
      alert('删除失败: ' + (data.error || '未知错误'));
    }
  } catch (err) {
    alert('删除请求失败: ' + err.message);
  }
}

// ========== 初始化入口 ==========
window.addEventListener('DOMContentLoaded', () => {
  initTheme();
  loadStatus();
  loadNodes();
  loadSettings();
  loadGroups();
  loadLogs();

  // 周期性拉取日志流
  logInterval = setInterval(loadLogs, 3000);
});
