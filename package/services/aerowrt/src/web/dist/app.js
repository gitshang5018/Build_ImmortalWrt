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
      gatewayStatus.textContent = data.core_running ? '运行中 (Sing-box)' : '就绪 (Sing-box)';
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

function updateActiveNodeDisplay() {
  const activeNode = currentNodes.find(n => n.id === activeNodeId) || currentNodes[0];
  const activeNameEl = document.getElementById('active-node-name');
  const activeDelayEl = document.getElementById('active-node-delay');
  const activeProtoEl = document.getElementById('active-node-proto');

  if (activeNode) {
    if (activeNameEl) activeNameEl.textContent = activeNode.tag || activeNode.server;
    if (activeDelayEl) {
      activeDelayEl.textContent = activeNode.delay_ms > 0 ? `${activeNode.delay_ms} ms` : '未测速';
      activeDelayEl.className = activeNode.delay_ms > 0 && activeNode.delay_ms < 150 ? 'pill pill-success' : 'pill pill-warning';
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

    let delayBadge = '<span class="pill pill-warning">--</span>';
    if (node.delay_ms > 0) {
      const cls = node.delay_ms < 100 ? 'pill-success' : (node.delay_ms < 250 ? 'pill-warning' : 'pill-danger');
      delayBadge = `<span class="pill ${cls}">${node.delay_ms} ms</span>`;
    }

    card.innerHTML = `
      <div class="node-header">
        <span class="node-tag">${escapeHtml(node.tag || node.server)}</span>
        ${isActive ? '<span class="pill pill-success">当前选中</span>' : ''}
      </div>
      <div class="node-info">
        <span>${escapeHtml(node.server)}:${node.port}</span>
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
    tbody.innerHTML = '<tr><td colspan="7" style="text-align: center; padding: 24px; color: var(--text-muted);">暂无节点，请点击导入</td></tr>';
    return;
  }

  currentNodes.forEach(node => {
    const isActive = node.id === activeNodeId;
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td><strong>${escapeHtml(node.tag || node.server)}</strong></td>
      <td><span class="pill">${(node.protocol || 'vless').toUpperCase()}</span></td>
      <td><code>${escapeHtml(node.server)}</code></td>
      <td>${node.port}</td>
      <td>${node.delay_ms > 0 ? `<span class="text-success">${node.delay_ms} ms</span>` : '<span class="text-muted">未测速</span>'}</td>
      <td>${isActive ? '<span class="text-success" style="font-weight:600;">🟢 活跃中</span>' : '<span class="text-muted">备用</span>'}</td>
      <td>
        <button class="btn btn-secondary btn-sm" onclick="switchNode('${node.id}')" ${isActive ? 'disabled' : ''}>设为主节点</button>
        <button class="btn btn-secondary btn-sm" style="color: var(--danger); margin-left: 6px;" onclick="deleteNode('${node.id}')">删除</button>
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

async function pingAllNodes() {
  const btn = document.getElementById('btn-ping-all');
  if (btn) {
    btn.disabled = true;
    btn.textContent = '⏳ 正在测速中...';
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

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ========== 初始化入口 ==========
window.addEventListener('DOMContentLoaded', () => {
  initTheme();
  loadStatus();
  loadNodes();
  loadLogs();

  // 周期性拉取日志流
  logInterval = setInterval(loadLogs, 3000);
});
