# IPQ6000 系列无线射频调度与多核网络栈深度优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 对高通 IPQ6000 系列（AX1800 Pro、AX6600 等）在无线射频调度（HE 波束成形/TWT/BSS Color/11kv 漫游）、多核中断亲和性（消除 CPU 3 热点）以及驱动与系统启动时序上进行深度优化，提升高并发吞吐与稳定性。

**架构：** 修复 OpenWrt ucode 架构下 BSS Coloring 配置失效问题，补全 Wi-Fi 6 关键射频能力；重构 4 核心的中断绑定布局，解耦有线以太网与无线收发环；清理废弃的 pbuf.uci 逻辑并闭环 ath11k stats 统计开销。

**技术栈：** OpenWrt / ImmortalWrt, ucode (hostapd.uc / ap.uc), UCI, Qualcomm ath11k, NSS, Linux smp_affinity, Bash.

**规格：** `docs/superpowers/specs/2026-10-06-ipq6000-wireless-optimization-design.md`

## 全局约束

- 严格遵循 OpenWrt `wireless.wifi-device.json` 与 `wireless.wifi-iface.json` schema 规范，严禁注入 schema 之外的无效键值。
- 所有 Wi-Fi 6 / 7 专有属性（`he_*`, `airtime_*`）必须按 `htmode`（`HE*` / `EHT*`）安全隔离，确保 Wi-Fi 4/5 老旧设备（如歌华链、R619AC）不会被注入非法参数引发 hostapd 解析报错。
- 所有 shell 脚本修改必须保证语法严密（`bash -n` 通过），并通过完整的本地自动化测试套件。

## 审查重点（Review Focus）

1. Wi-Fi 5 设备（如歌华链）在执行 `992_set-wifi-uci.sh` 时，绝对不能包含任何 `he_bss_color_enabled`、`he_su_beamformer` 或 `airtime_mode`。
2. 5G Wi-Fi 6 设备输出必须同时包含 `he_bss_color='42'` 与 `he_bss_color_enabled='1'`，确保 hostapd.uc 生成 OBSS-PD 配置。
3. AX6600 (RE-CS-02) 的 Radio 2 (QCN9074) 必须包含 `beamformer_antennas='4'` 与 `beamformee_antennas='4'`。
4. 漫游协议参数 `rrm_neighbor_report='1'`, `rrm_beacon_report='1'`, `wnm_sleep_mode='1'` 必须安全写入 wifi-iface 段。
5. `smp_affinity` 中有线以太网接收 `edma_rxdesc` 必须脱离 CPU 3 分流至 CPU 1，`edma_txcmpl` 必须分流至 CPU 2。

---

### 任务 1：测试先行——扩展 `test_wifi_uci.sh` 断言

**文件：**
- 修改：`wrt_core/patches/tests/test_wifi_uci.sh`

- [x] **步骤 1：在 `test_wifi_uci.sh` 中添加对新增特性的断言并运行验证失败**

为 AX1800 Pro / AX6600 添加对 `he_bss_color_enabled='1'`, `he_su_beamformer='1'`, `he_su_beamformee='1'`, `he_mu_beamformer='1'`, `he_twt_responder='1'`, `rrm_neighbor_report='1'`, `rrm_beacon_report='1'`, `wnm_sleep_mode='1'` 的断言；为 AX6600 radio2 添加对 `beamformer_antennas='4'` 与 `beamformee_antennas='4'` 的断言；同时针对 Wi-Fi 5 设备确保无上述 HE 参数。

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh`
预期：FAIL（提示缺少相关新增配置）。

---

### 任务 2：实现无线配置优化 (`992_set-wifi-uci.sh`)

**文件：**
- 修改：`wrt_core/patches/992_set-wifi-uci.sh`

- [x] **步骤 2：在 `configure_wifi` 和 `jdc_ax6600_wifi_cfg` 中实现完整参数配置**

1. 在基础接口段添加 `rrm_neighbor_report='1'`, `rrm_beacon_report='1'`, `wnm_sleep_mode='1'`。
2. 在 `HE/EHT` 判断分支中，对 radio 段添加 `he_su_beamformer='1'`, `he_su_beamformee='1'`, `he_mu_beamformer='1'`, `he_twt_responder='1'`。
3. 在 5G HE 分支中，设置 `he_bss_color='42'` 的同时补齐 `he_bss_color_enabled='1'` 与 `he_spr_psr_enabled='1'`。
4. 在 `jdc_ax6600_wifi_cfg` 中，为 radio2 设置 `beamformer_antennas='4'` 与 `beamformee_antennas='4'`。

- [x] **步骤 3：运行测试验证通过**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh`
预期：PASS（AX1800 Pro、AX6600 三频及 Wi-Fi 5 兼容性断言全部通过）。

- [x] **步骤 4：Commit**

```bash
git add wrt_core/patches/992_set-wifi-uci.sh wrt_core/patches/tests/test_wifi_uci.sh
git commit -m "feat(wifi): optimize IPQ60xx Wi-Fi 6 beamforming, TWT, BSS color and roaming"
```

---

### 任务 3：重构多核中断亲和性 (`smp_affinity`)

**文件：**
- 修改：`wrt_core/patches/smp_affinity`

- [x] **步骤 1：重构 `enable_affinity` 函数分配策略**

1. 将有线以太网 DMA 中断从 CPU 3 解耦：
   - `edma_rxdesc` -> CPU 1
   - `edma_txcmpl` -> CPU 2
   - `edma_rxfill` -> CPU 3
   - `edma_misc` -> CPU 3
2. 保持无线 REO ring 1-4（CPU 0-3）、WBM completion 1-3（CPU 1-3）以及 PPDU 中断错峰配合。

- [x] **步骤 2：验证脚本语法**

运行：`& "C:\Program Files\Git\bin\bash.exe" -n wrt_core/patches/smp_affinity`
预期：无语法错误。

- [x] **步骤 3：Commit**

```bash
git add wrt_core/patches/smp_affinity
git commit -m "perf(nss): rebalance smp_affinity IRQs across 4 cores to eliminate CPU 3 bottleneck"
```

---

### 任务 4：系统内核与启动调优 (`system.sh` / `991_custom_settings`)

**文件：**
- 修改：`wrt_core/patches/991_custom_settings`
- 修改：`wrt_core/modules/system.sh`

- [x] **步骤 1：在 `991_custom_settings` 中注入 ath11k 统计禁用**

对高并发下减轻 CPU 软中断开销的逻辑：检查 `/sys/kernel/debug/ath11k`，存在则将 `stats_disable` 置 1。

- [x] **步骤 2：清理 `system.sh` 中的无效 pbuf.uci 替换代码**

更新 `update_nss_pbuf_performance`，移除针对已不存在的 `pbuf.uci` 的 sed 替换逻辑，直接针对 `qca-nss-pbuf.init` 确保其调速器设置为 `performance`，防止开机后被回退为 `schedutil`。

- [x] **步骤 3：验证脚本语法与运行测试**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_custom_settings.sh`
预期：PASS。

- [x] **步骤 4：Commit**

```bash
git add wrt_core/patches/991_custom_settings wrt_core/modules/system.sh
git commit -m "perf(system): disable ath11k stats overhead and align NSS pbuf governor to performance"
```

---

### 任务 5：全量自动化回归验证

**文件：**
- 运行：`wrt_core/patches/tests/test_wifi_uci.sh`
- 运行：`wrt_core/patches/tests/test_custom_settings.sh`
- 运行：`test_theme_athena.sh`
- 运行：`test_aerowrt.sh`

- [x] **步骤 1：执行全量测试套件**

运行：
```powershell
& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh
& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_custom_settings.sh
& "C:\Program Files\Git\bin\bash.exe" test_theme_athena.sh
& "C:\Program Files\Git\bin\bash.exe" test_aerowrt.sh
```
预期：所有测试全部 PASS。

- [x] **步骤 2：最终分支状态检查**

确保工作区干净，所有修改均已测试通过并提交。
