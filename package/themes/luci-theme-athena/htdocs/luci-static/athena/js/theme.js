/**
 * luci-theme-athena - Interactive Engine
 * (Dark Mode Toggle, Realtime Traffic Waveform, Tri-band Temperature & Memory Drop Caches)
 */

(function () {
  'use strict';

  // ========== 1. 主题与明暗模式控制器 ==========
  const ThemeManager = {
    KEY: 'athena-theme-preference',

    init() {
      const saved = localStorage.getItem(this.KEY) || 'auto';
      this.apply(saved);

      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
        if (localStorage.getItem(this.KEY) === 'auto') {
          this.apply('auto');
        }
      });

      document.addEventListener('DOMContentLoaded', () => {
        const toggleBtn = document.getElementById('athena-theme-btn');
        if (toggleBtn) {
          toggleBtn.addEventListener('click', () => this.toggle());
        }
      });
    },

    apply(mode) {
      let isDark = mode === 'dark';
      if (mode === 'auto') {
        isDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      }
      document.documentElement.setAttribute('data-theme', isDark ? 'dark' : 'light');
      
      const icon = document.getElementById('athena-theme-icon');
      if (icon) {
        icon.innerHTML = isDark ? '☀️' : '🌙';
        icon.setAttribute('title', isDark ? '切换至明亮模式' : '切换至暗黑模式');
      }
    },

    toggle() {
      const current = document.documentElement.getAttribute('data-theme') === 'dark';
      const next = current ? 'light' : 'dark';
      localStorage.setItem(this.KEY, next);
      this.apply(next);
    }
  };

  ThemeManager.init();

  // ========== 2. 移动端侧边栏交互 ==========
  document.addEventListener('DOMContentLoaded', () => {
    const menuBtn = document.getElementById('athena-menu-btn');
    const sidebar = document.querySelector('.main-left');
    const backdrop = document.getElementById('athena-backdrop');

    if (menuBtn && sidebar && backdrop) {
      menuBtn.addEventListener('click', () => {
        sidebar.classList.toggle('open');
        backdrop.classList.toggle('active');
      });

      backdrop.addEventListener('click', () => {
        sidebar.classList.remove('open');
        backdrop.classList.remove('active');
      });
    }
  });

  // ========== 3. 状态概览页仪表盘增强 (波形图/三频温控/释放缓存) ==========
  const DashboardEnhancer = {
    historyRx: new Array(30).fill(0),
    historyTx: new Array(30).fill(0),
    lastRxBytes: 0,
    lastTxBytes: 0,
    lastTime: Date.now(),

    init() {
      document.addEventListener('DOMContentLoaded', () => {
        this.injectDashboardCards();
        this.startPoll();
      });
    },

    injectDashboardCards() {
      const container = document.querySelector('.container') || document.getElementById('maincontent');
      if (!container || !location.pathname.includes('/admin/status/overview')) return;

      // 仅在概览页顶部注入增强网速波形与雅典娜三频温控卡片
      if (document.getElementById('athena-enhanced-dashboard')) return;

      const dashboardHtml = `
        <div id="athena-enhanced-dashboard" class="athena-grid">
          <!-- 实时网速波形看板 -->
          <div class="athena-stat-card" style="grid-column: 1 / -1;">
            <div class="athena-stat-header">
              <span class="athena-stat-title">实时流量监测 (实时双向波形)</span>
              <div style="display: flex; gap: 16px; font-size: 12px; font-weight: 600;">
                <span style="color: #1E88E5;">▼ 下载: <span id="athena-rx-speed">0.0 KB/s</span></span>
                <span style="color: #00BCD4;">▲ 上传: <span id="athena-tx-speed">0.0 KB/s</span></span>
              </div>
            </div>
            <div class="traffic-chart-box">
              <canvas id="athena-traffic-canvas" style="width: 100%; height: 100%;"></canvas>
            </div>
          </div>

          <!-- CPU 温控卡片 -->
          <div class="athena-stat-card">
            <div class="athena-stat-header">
              <span class="athena-stat-title">CPU 处理器核心</span>
              <span id="athena-cpu-badge" class="temp-pill cool">正常</span>
            </div>
            <div class="athena-stat-value" id="athena-cpu-temp">-- °C</div>
            <div class="athena-stat-subtitle">SoC 实时传感器温度</div>
          </div>

          <!-- 雅典娜 2.4G 频段 -->
          <div class="athena-stat-card">
            <div class="athena-stat-header">
              <span class="athena-stat-title">2.4GHz 基础覆盖</span>
              <span class="temp-pill cool" id="athena-w24-badge">Radio1</span>
            </div>
            <div class="athena-stat-value" id="athena-w24-temp">-- °C</div>
            <div class="athena-stat-subtitle">IPQ6010 内置 QCN5022</div>
          </div>

          <!-- 雅典娜 5.8G 频段 -->
          <div class="athena-stat-card">
            <div class="athena-stat-header">
              <span class="athena-stat-title">5.8GHz 日常影音</span>
              <span class="temp-pill cool" id="athena-w58-badge">Radio0</span>
            </div>
            <div class="athena-stat-value" id="athena-w58-temp">-- °C</div>
            <div class="athena-stat-subtitle">IPQ6010 内置 QCN5052</div>
          </div>

          <!-- 雅典娜 5.2G 电竞频段 -->
          <div class="athena-stat-card">
            <div class="athena-stat-header">
              <span class="athena-stat-title">5.2GHz 电竞高频宽</span>
              <span class="temp-pill cool" id="athena-w52-badge">Radio2</span>
            </div>
            <div class="athena-stat-value" id="athena-w52-temp">-- °C</div>
            <div class="athena-stat-subtitle">QCN9074 4x4 160MHz</div>
          </div>

          <!-- 内存与一键清理 -->
          <div class="athena-stat-card" style="grid-column: 1 / -1; display: flex; flex-direction: row; align-items: center; justify-content: space-between;">
            <div>
              <div style="font-weight: 700; font-size: 14px; margin-bottom: 4px;">Linux 内核缓存智能管理</div>
              <div style="font-size: 12px; color: var(--text-secondary);">释放系统 PageCache / Slab 临时占用，提升高负载可用空间</div>
            </div>
            <button id="athena-drop-cache-btn" class="cbi-button" style="border-radius: 8px;">
              🧹 立即释放缓存
            </button>
          </div>
        </div>
      `;

      const firstChild = container.firstChild;
      const wrap = document.createElement('div');
      wrap.innerHTML = dashboardHtml;
      container.insertBefore(wrap.firstElementChild, firstChild);

      const dropBtn = document.getElementById('athena-drop-cache-btn');
      if (dropBtn) {
        dropBtn.addEventListener('click', () => this.dropCaches(dropBtn));
      }
    },

    dropCaches(btn) {
      btn.disabled = true;
      btn.innerHTML = '正在释放...';
      fetch('/cgi-bin/luci/admin/system/drop_caches', { method: 'POST' })
        .catch(() => {})
        .finally(() => {
          // 备用 ubus 执行 drop_caches
          if (window.L && L.ws) {
            L.ws.call('file', 'exec', { command: '/bin/sh', params: ['-c', 'sync && echo 3 > /proc/sys/vm/drop_caches'] });
          }
          setTimeout(() => {
            btn.disabled = false;
            btn.innerHTML = '✅ 释放完成';
            setTimeout(() => { btn.innerHTML = '🧹 立即释放缓存'; }, 2000);
          }, 600);
        });
    },

    startPoll() {
      this.fetchTemperatures();
      this.fetchTraffic();
      setInterval(() => {
        this.fetchTemperatures();
        this.fetchTraffic();
      }, 3000);
    },

    fetchTemperatures() {
      // 优先调用 luci / luci-rpc 的 getTempInfo
      const handleTempStr = (str) => {
        if (!str) return;
        const cpuM = str.match(/CPU[:\s]+([0-9.]+)/i);
        if (cpuM) {
          const c = parseFloat(cpuM[1]);
          const el = document.getElementById('athena-cpu-temp');
          if (el) el.innerText = `${c.toFixed(1)} °C`;
          const badge = document.getElementById('athena-cpu-badge');
          if (badge) {
            badge.className = `temp-pill ${c > 80 ? 'hot' : c > 65 ? 'warm' : 'cool'}`;
            badge.innerText = c > 80 ? '过热' : c > 65 ? '温热' : '正常';
          }
        }

        const wifiSection = str.substring(str.toLowerCase().indexOf('wifi:'));
        const wNums = wifiSection.match(/([0-9.]+)/g) || [];
        if (wNums.length >= 3) {
          // Athena: phy0=5.8G, phy1=2.4G, phy2=5.2G
          const el58 = document.getElementById('athena-w58-temp');
          if (el58) el58.innerText = `${parseFloat(wNums[0]).toFixed(1)} °C`;
          const el24 = document.getElementById('athena-w24-temp');
          if (el24) el24.innerText = `${parseFloat(wNums[1]).toFixed(1)} °C`;
          const el52 = document.getElementById('athena-w52-temp');
          if (el52) el52.innerText = `${parseFloat(wNums[2]).toFixed(1)} °C`;
        } else if (wNums.length >= 1) {
          const el24 = document.getElementById('athena-w24-temp');
          if (el24) el24.innerText = `${parseFloat(wNums[0]).toFixed(1)} °C`;
        }
      };

      if (window.L && L.resolveDefault && L.ws) {
        L.resolveDefault(L.ws.call('luci', 'getTempInfo'), {})
          .then((res) => handleTempStr(res.tempinfo || res.result || res.temp))
          .catch(() => {});
      }
    },

    fetchTraffic() {
      if (!window.L || !L.resolveDefault || !L.ws) return;
      L.resolveDefault(L.ws.call('network.interface', 'dump'), {}).then((res) => {
        let rxTotal = 0;
        let txTotal = 0;
        if (res && res.interface) {
          res.interface.forEach((iface) => {
            if (iface.interface !== 'loopback' && iface.statistics) {
              rxTotal += iface.statistics.rx_bytes || 0;
              txTotal += iface.statistics.tx_bytes || 0;
            }
          });
        }

        const now = Date.now();
        const dt = Math.max((now - this.lastTime) / 1000, 1);
        if (this.lastRxBytes > 0) {
          const rxRate = Math.max(0, (rxTotal - this.lastRxBytes) / dt);
          const txRate = Math.max(0, (txTotal - this.lastTxBytes) / dt);

          this.historyRx.shift();
          this.historyRx.push(rxRate);
          this.historyTx.shift();
          this.historyTx.push(txRate);

          const rxLabel = document.getElementById('athena-rx-speed');
          if (rxLabel) rxLabel.innerText = this.formatSpeed(rxRate);
          const txLabel = document.getElementById('athena-tx-speed');
          if (txLabel) txLabel.innerText = this.formatSpeed(txRate);

          this.drawWaveform();
        }

        this.lastRxBytes = rxTotal;
        this.lastTxBytes = txTotal;
        this.lastTime = now;
      }).catch(() => {});
    },

    formatSpeed(bps) {
      if (bps >= 1024 * 1024 * 1024) return (bps / (1024 * 1024 * 1024)).toFixed(2) + ' GB/s';
      if (bps >= 1024 * 1024) return (bps / (1024 * 1024)).toFixed(2) + ' MB/s';
      if (bps >= 1024) return (bps / 1024).toFixed(1) + ' KB/s';
      return Math.round(bps) + ' B/s';
    },

    drawWaveform() {
      const canvas = document.getElementById('athena-traffic-canvas');
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();

      canvas.width = rect.width * dpr;
      canvas.height = rect.height * dpr;
      ctx.scale(dpr, dpr);

      const w = rect.width;
      const h = rect.height;
      ctx.clearRect(0, 0, w, h);

      const maxVal = Math.max(...this.historyRx, ...this.historyTx, 10240); // 最小刻度 10KB/s

      // 绘制平滑贝塞尔填充
      this.drawBezierPath(ctx, this.historyRx, w, h, maxVal, '#1E88E5', 'rgba(30, 136, 229, 0.15)');
      this.drawBezierPath(ctx, this.historyTx, w, h, maxVal, '#00BCD4', 'rgba(0, 188, 212, 0.12)');
    },

    drawBezierPath(ctx, points, w, h, maxVal, strokeColor, fillColor) {
      if (points.length < 2) return;
      const step = w / (points.length - 1);

      ctx.beginPath();
      ctx.moveTo(0, h - (points[0] / maxVal) * (h - 10));

      for (let i = 0; i < points.length - 1; i++) {
        const x0 = i * step;
        const y0 = h - (points[i] / maxVal) * (h - 10);
        const x1 = (i + 1) * step;
        const y1 = h - (points[i + 1] / maxVal) * (h - 10);

        const cx = (x0 + x1) / 2;
        ctx.bezierCurveTo(cx, y0, cx, y1, x1, y1);
      }

      ctx.strokeStyle = strokeColor;
      ctx.lineWidth = 2.5;
      ctx.stroke();

      ctx.lineTo(w, h);
      ctx.lineTo(0, h);
      ctx.closePath();
      ctx.fillStyle = fillColor;
      ctx.fill();
    }
  };

  DashboardEnhancer.init();
})();
