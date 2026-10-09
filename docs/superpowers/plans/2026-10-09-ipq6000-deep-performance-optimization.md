# IPQ6000 系列 NSS 硬件流控与无线射频深度优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 subagent-driven-development（推荐）或 executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复 IPQ6000 系列在 OpenWrt/ImmortalWrt 环境下的组播转单播失效、NSS VLAN 包名错误及调速器重启覆盖问题，重构四核中断亲和性流水线，释放 NSS 高频与 ECM 加速潜能，并为 512MB 设备注入核心服务 OOM 免疫保护。

**架构：** 通过对齐 OpenWrt `hostapd.uc` 真实 UCI 键名规范修正无线配置；通过重构 `smp_affinity` 建立 PCIe 网卡与有线以太网的物理核错峰流水线；通过构建期直接 patch 上游 `qca-nss-pbuf.init` 固化 `performance` 调速器；并在 `991_custom_settings` 注入 NSS 提频、ECM 延时收紧及核心进程 OOM 保护。

**技术栈：** Shell, OpenWrt uci / ucode (`hostapd.uc`), Linux sysctl, Qualcomm NSS 12.5 (`qca-nss-drv`, `qca-nss-ecm`), ath11k Wi-Fi 6驱动。

**规格：** [`docs/superpowers/specs/2026-10-09-ipq6000-deep-performance-optimization-design.md`](file:///C:/Users/ADMIN/.gemini/antigravity/worktrees/Build_ImmortalWrt/pull_latest_code/docs/superpowers/specs/2026-10-09-ipq6000-deep-performance-optimization-design.md)

---

## 全局约束

- 严格遵循 OpenWrt mac80211 与 hostapd 的实际解析逻辑，不引入任何未被驱动支持的伪参数。
- 保证 Wi-Fi 5 / Wi-Fi 4 架构机型（如歌华链 MT7621、P2W R619AC）严格向下兼容，HE 专有字段严禁泄露至非 HE 射频。
- 保持测试用例覆盖全部变更项，任何改动必须先有测试覆盖再进行代码实现。
- 每次任务变更必须执行单元测试并通过，且独立 commit。

---

## 审查重点（Review Focus）

1. `multicast_to_unicast_all` 必须在所有 Wi-Fi 射频段（无论 2.4G 还是 5G，无论 Wi-Fi 6 还是 Wi-Fi 5）正确生成，彻底替代已废弃的 `multicast_to_unicast`。
2. 2.4G 信道带宽从 `HE40` 改为 `HE20` 后，AX1800 Pro 与 AX6600 的 2.4G SSID 正常配置且 `noscan='1'` 依然保持。
3. `he_spr_non_srg_obss_pd_max_offset='20'` 仅在 5G HE 射频段注入，严禁注入至 2.4G 或 Wi-Fi 5（VHT/HT）设备。
4. `smp_affinity` 在没有 QCN9074（如纯双频的 AX1800 Pro 亚瑟）运行时不报错退出，能够静默跳过空匹配。
5. `system.sh` 中对 `qca-nss-pbuf.init` 的 patch 必须保持幂等性，重复执行不会破坏脚本语法。

---

### 任务 1：无线配置与测试套件对齐 (`992_set-wifi-uci.sh` & `test_wifi_uci.sh`)

**文件：**
- 修改：`wrt_core/patches/tests/test_wifi_uci.sh`
- 修改：`wrt_core/patches/992_set-wifi-uci.sh`

- [ ] **步骤 1：在 `test_wifi_uci.sh` 中先更新断言（红灯测试）**

更新断言：
1. 检查 `multicast_to_unicast_all='1'` 出现，反向断言 `multicast_to_unicast='1'` 不存在；
2. 检查 5G HE 射频包含 `he_spr_non_srg_obss_pd_max_offset='20'`；
3. 检查 AX1800 Pro 与 AX6600 的 Radio 1 (2.4G) htmode 为 `HE20`。

- [ ] **步骤 2：运行测试验证失败**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh`
预期：FAIL，断言未通过（因为 `992` 尚未修改）。

- [ ] **步骤 3：在 `992_set-wifi-uci.sh` 中实现配置修正**

1. 将 `set wireless.default_radio${radio}.multicast_to_unicast='1'` 替换为 `set wireless.default_radio${radio}.multicast_to_unicast_all='1'`；
2. 在 5G HE BSS Coloring 段中追加 `set wireless.radio${radio}.he_spr_non_srg_obss_pd_max_offset='20'`；
3. 将 `jdc_ax1800_pro_wifi_cfg()` 与 `jdc_ax6600_wifi_cfg()` 中的 radio 1 htmode 从 `HE40` 改为 `HE20`。

- [ ] **步骤 4：运行测试验证通过**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh`
预期：PASS（全机型信道与兼容性测试通过）。

- [ ] **步骤 5：Commit**

```bash
git add wrt_core/patches/992_set-wifi-uci.sh wrt_core/patches/tests/test_wifi_uci.sh
git commit -m "fix(wifi): align multicast_to_unicast_all, add OBSS_PD offset, set 2.4G to HE20"
```

---

### 任务 2：NSS 内核驱动与调速器开机固化 (`nss.config` & `system.sh`)

**文件：**
- 修改：`wrt_core/deconfig/nss.config`
- 修改：`wrt_core/modules/system.sh`

- [ ] **步骤 1：修正 `wrt_core/deconfig/nss.config`**

1. 将 `CONFIG_PACKAGE_kmod-qca-nss-drv-vlan=y` 替换为 `CONFIG_PACKAGE_kmod-qca-nss-drv-vlan-mgr=y`；
2. 移除 `CONFIG_PACKAGE_sqm-scripts-nss=y` 并添加说明注释。

- [ ] **步骤 2：优化 `system.sh` 中的调速器固化与路径清理**

1. 检查 `update_nss_pbuf_performance()`，移除对已不存在的 `pbuf.uci` 的修改，改为直接在构建期将 `package/kernel/mac80211/files/qca-nss-pbuf.init` 中的 `governor="schedutil"` 替换为 `governor="performance"`；
2. 修正 `update_script_priority()` 中 `qca-nss-drv.init` 的正确路径为 `package/qca-nss/qca-nss-drv/files/qca-nss-drv.init`，移除对不存在的 `nss_packages` feed 的查找；
3. 移除 `update_nss_diag()` 中对不存在的 `package/kernel/mac80211/files/nss_diag.sh` 的死代码。

- [ ] **步骤 3：语法检查**

运行：`& "C:\Program Files\Git\bin\bash.exe" -n wrt_core/modules/system.sh`
预期：无语法错误输出。

- [ ] **步骤 4：Commit**

```bash
git add wrt_core/deconfig/nss.config wrt_core/modules/system.sh
git commit -m "fix(nss): correct vlan-mgr package name and lock performance governor in pbuf init"
```

---

### 任务 3：四核中断亲和性流水线重构 (`patches/smp_affinity`)

**文件：**
- 修改：`wrt_core/patches/smp_affinity`

- [ ] **步骤 1：重构 `enable_affinity` 函数**

1. 移除对已下沉至 NSS 固件的无效中断匹配：`reo2host-destination-ring*`、`wbm2host-tx-completions-ring*`、`ppdu-end-interrupts-mac*`；
2. 增加对外挂 PCIe 网卡（QCN9074 5.2G 160MHz）的中断绑定至 CPU 0（匹配 `ath11k_pci` 与 `pci` 中断）；
3. 维持有线以太网解耦流水线：
   - `edma_rxdesc` 绑 CPU 1
   - `edma_txcmpl` 绑 CPU 2
   - `edma_rxfill` 绑 CPU 3
   - `edma_misc` 绑 CPU 3
4. `nss_queue0` 绑定 CPU `2-3`。

- [ ] **步骤 2：脚本语法检查**

运行：`& "C:\Program Files\Git\bin\bash.exe" -n wrt_core/patches/smp_affinity`
预期：无语法错误输出。

- [ ] **步骤 3：Commit**

```bash
git add wrt_core/patches/smp_affinity
git commit -m "perf(smp_affinity): bind PCIe wifi to CPU 0 and eliminate dead AHB ring matches"
```

---

### 任务 4：系统首启优化、NSS 提频与核心进程 OOM 免疫 (`991_custom_settings`)

**文件：**
- 修改：`wrt_core/patches/991_custom_settings`
- 修改：`wrt_core/patches/tests/test_custom_settings.sh`

- [ ] **步骤 1：在 `test_custom_settings.sh` 中增加测试用例**

增加测试：
1. 测试首启脚本中包含对 `nss_freq` 设置为 `high` 的逻辑；
2. 测试首启脚本中包含对 ECM `accel_delay_pkts` 收紧至 `1` 的逻辑；
3. 测试首启脚本中包含对关键守护进程（`hostapd`, `dnsmasq`, `netifd`）的 `oom_score_adj` 保护逻辑。

- [ ] **步骤 2：运行测试验证失败**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_custom_settings.sh`
预期：FAIL（新断言失败）。

- [ ] **步骤 3：在 `991_custom_settings` 中实现调优**

1. 注入 NSS 频率提升：若存在 `/etc/config/nss_freq`，设置 `nss_freq.settings.level='high'`；
2. 注入 ECM 加速时序：若存在 `/sys/kernel/debug/ecm/ecm_classifier_default`，将 `accel_delay_pkts` 设为 `1`；
3. 注入 OOM 免死保护：在首启与定时任务中将 `hostapd`, `dnsmasq`, `netifd` 的 `/proc/$pid/oom_score_adj` 置为 `-1000`；
4. 确认 `vm.swappiness = 60`。

- [ ] **步骤 4：运行测试验证通过**

运行：`& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_custom_settings.sh`
预期：PASS。

- [ ] **步骤 5：全量运行所有自动化测试**

运行：
1. `& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_wifi_uci.sh`
2. `& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_custom_settings.sh`
3. `& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_theme_athena.sh`
4. `& "C:\Program Files\Git\bin\bash.exe" wrt_core/patches/tests/test_aerowrt.sh`
预期：全线 PASS。

- [ ] **步骤 6：Commit**

```bash
git add wrt_core/patches/991_custom_settings wrt_core/patches/tests/test_custom_settings.sh
git commit -m "perf(system): set nss_freq to high, tighten accel_delay_pkts, and add OOM immunity"
```
