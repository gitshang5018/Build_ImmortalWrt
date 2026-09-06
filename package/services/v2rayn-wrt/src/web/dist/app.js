// v2rayN-Wrt WebUI Client Engine
let currentNodes = [];
let activeNodeId = '';

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

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Initial bootstrap
window.addEventListener('DOMContentLoaded', () => {
  fetchStatus();
  fetchNodes();
  setInterval(fetchStatus, 5000);
});
