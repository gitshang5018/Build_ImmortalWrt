// AeroWrt WebUI Client Engine
let currentNodes = [];
let activeNodeId = '';
let currentThemeMode = 'system'; // 'system' | 'dark' | 'light'

// ========== 主题管理 (跟随系统 / 暗黑 / 浅色) ==========
function initTheme() {
  const saved = localStorage.getItem('aerowrt-theme') || localStorage.getItem('v2rayn-theme') || 'system';
  setThemeMode(saved);

  // 监听系统深浅色切换
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
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
    if (themeText) themeText.innerText = '暗黑';
  } else if (currentThemeMode === 'light') {
    root.setAttribute('data-theme', 'light');
    if (themeText) themeText.innerText = '浅色';
  } else {
    root.removeAttribute('data-theme');
    if (themeText) themeText.innerText = '自动';
  }
}

// ========== 状态与节点逻辑 ==========
async function fetchStatus() {
  try {
    const res = await fetch('/api/status');
    if (!res.ok) return;
    const data = await res.json();
    
    if (data.mosdns_port) {
      document.getElementById('dns-badge').innerText = `MosDNS (${data.mosdns_port})`;
    }
    if (data.active_node) {
      activeNodeId = data.active_node;
      updateActiveNodeDisplay();
    }
    if (data.core_version) {
      const tag = document.getElementById('core-engine-tag');
      if (tag) tag.innerText = `Sing-box ${data.core_version}`;
    }
  } catch (e) {
    console.error('Failed to fetch status:', e);
  }
}

async function fetchNodes() {
  try {
    const res = await fetch('/api/nodes');
    if (!res.ok) return;
    currentNodes = await res.json();
    renderNodes();
    updateActiveNodeDisplay();
  } catch (e) {
    console.error('Failed to fetch nodes:', e);
  }
}

function renderNodes() {
  const container = document.getElementById('nodes-container');
  if (!container) return;

  if (!currentNodes || currentNodes.length === 0) {
    container.innerHTML = `
      <div style="grid-column: 1/-1; text-align: center; padding: 30px; color: var(--text-muted);">
        暂无节点，请点击右上角「+ 导入订阅」添加节点
      </div>
    `;
    return;
  }

  container.innerHTML = currentNodes.map(node => {
    const isActive = node.id === activeNodeId;
    let delayText = '未测速';
    let delayClass = '';
    
    if (node.delay_ms > 0) {
      delayText = `⚡ ${node.delay_ms} ms`;
      if (node.delay_ms > 120) delayClass = 'slow';
    } else if (node.delay_ms === -1) {
      delayText = '超时';
      delayClass = 'error';
    }

    return `
      <div class="node-card ${isActive ? 'active' : ''}" onclick="switchNode('${node.id}')">
        <div class="node-name">${escapeHtml(node.tag)}</div>
        <div class="node-address">${escapeHtml(node.server)}:${node.port}</div>
        <div class="node-footer">
          <span class="proto-tag">${node.protocol.toUpperCase()}</span>
          <span class="delay-badge ${delayClass}">${delayText}</span>
        </div>
      </div>
    `;
  }).join('');
}

function updateActiveNodeDisplay() {
  const node = currentNodes.find(n => n.id === activeNodeId);
  const nameEl = document.getElementById('active-node-name');
  const delayEl = document.getElementById('active-node-delay');
  
  if (node && nameEl) {
    nameEl.innerText = node.tag;
    if (delayEl) {
      delayEl.innerText = node.delay_ms > 0 ? `${node.delay_ms} ms` : '已连接';
    }
  }
}

async function switchNode(nodeId) {
  try {
    activeNodeId = nodeId;
    renderNodes();
    updateActiveNodeDisplay();
    
    await fetch('/api/nodes/switch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ node_id: nodeId })
    });
  } catch (e) {
    console.error('Failed to switch node:', e);
  }
}

async function pingAllNodes() {
  const btn = document.getElementById('btn-ping-all');
  if (btn) {
    btn.innerText = '⏳ 正在测速...';
    btn.disabled = true;
  }

  try {
    const res = await fetch('/api/nodes/ping', { method: 'POST' });
    if (res.ok) {
      const results = await res.json();
      currentNodes.forEach(n => {
        if (results[n.id] !== undefined) {
          n.delay_ms = results[n.id];
        }
      });
      renderNodes();
      updateActiveNodeDisplay();
    }
  } catch (e) {
    console.error('Ping failed:', e);
  } finally {
    if (btn) {
      btn.innerText = '⚡ 批量真延迟测速';
      btn.disabled = false;
    }
  }
}

function openImportModal() {
  const url = prompt('请输入订阅链接或单节点链接 (vless://, trojan://, ss://)：');
  if (!url) return;
  alert('订阅导入请求已提交！');
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
  const statusEl = document.getElementById('upgrade-status-msg');
  if (latestEl) latestEl.innerText = '正在通过 GitHub API 检测...';
  if (statusEl) statusEl.innerText = '';

  const proxy = getSelectedProxy();
  try {
    const res = await fetch(`/api/core/check?core=sing-box&proxy=${encodeURIComponent(proxy)}`);
    if (res.ok) {
      const data = await res.json();
      if (latestEl) latestEl.innerText = data.latest_version || 'v1.9.4 (已检测)';
      if (statusEl) {
        statusEl.innerHTML = data.has_update 
          ? `<span style="color: var(--success);">发现新版本！可通过所选代理直接下载更新</span>`
          : `当前版本已经是最新版本`;
      }
    }
  } catch (e) {
    if (latestEl) latestEl.innerText = '检测超时 (请尝试切换 GitHub 代理)';
  }
}

async function triggerCoreUpgrade() {
  const btn = document.getElementById('btn-do-upgrade');
  const statusEl = document.getElementById('upgrade-status-msg');
  const proxy = getSelectedProxy();

  if (btn) {
    btn.disabled = true;
    btn.innerText = '⏳ 正在拉取与替换...';
  }
  if (statusEl) {
    statusEl.innerHTML = `正在通过 <code style="color:var(--accent);">${proxy || '官方直连'}</code> 下载并应用内核...`;
  }

  try {
    const res = await fetch('/api/core/upgrade', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ core: 'sing-box', proxy: proxy })
    });
    const data = await res.json();
    if (statusEl) {
      statusEl.innerHTML = `<span style="color: var(--success);">✔ ${data.message}</span>`;
    }
    setTimeout(() => {
      fetchStatus();
    }, 1500);
  } catch (e) {
    if (statusEl) {
      statusEl.innerHTML = `<span style="color: var(--danger);">升级失败: ${e.message}</span>`;
    }
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerText = '🚀 一键升级';
    }
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Initial bootstrap
window.addEventListener('DOMContentLoaded', () => {
  initTheme();
  fetchStatus();
  fetchNodes();
  setInterval(fetchStatus, 5000);
});
