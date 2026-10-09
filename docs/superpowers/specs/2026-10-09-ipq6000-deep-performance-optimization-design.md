# IPQ6000 系列 NSS 硬件流控与无线射频深度优化设计规范 (Spec)

## 1. 概述与优化目标

本项目基于 ImmortalWrt (`VIKINGYFY/immortalwrt@main`)，面向高通 IPQ60xx 系列（含京东云 AX1800 Pro / 亚瑟 `jdcloud,re-ss-01`，IPQ6000；京东云 AX6600 / 雅典娜 `jdcloud,re-cs-02`，IPQ6010 + QCN9074 等设备）。

经过对源码底层实现与运行时时序的深度复核，本方案旨在彻底解决以下**真实存在的代码断链、时序冲突及硬件潜能受限**问题：
1. **组播转单播键名失效**：`992_set-wifi-uci.sh` 中设置的 `multicast_to_unicast='1'` 不被 OpenWrt `hostapd.sh` 读取（上游读取 `multicast_to_unicast_all`），导致组播优化在驱动层完全未生效。
2. **NSS VLAN 硬件卸载包名错误**：`nss.config` 中声明为 `kmod-qca-nss-drv-vlan`，但真实 Makefile 子包名为 `kmod-qca-nss-drv-vlan-mgr`，缺少 `-mgr` 后缀导致 kconfig 静默忽略，802.1Q 硬件 VLAN 卸载从未被编入；同时清理不存在的 `sqm-scripts-nss`。
3. **开机调速器被上游覆盖**：`991_custom_settings`（uci-defaults）仅首启写一次 `performance`，而上游 `/etc/init.d/qca-nss-pbuf`（每次开机 START=89）硬编码 `governor="schedutil"`，重启后调速器被强行改回，产生首包升频延迟。
4. **中断亲和性脚本无效匹配与热点**：现有 `smp_affinity` 尝试 pin AHB 无线环（`reo2host-destination-ring*` 等），但在无线 NSS Offload 开启后这些环由 NSS 固件内部接管，在 Linux `/proc/interrupts` 中根本不注册；同时未显式隔离 QCN9074 PCIe MSI 中断。
5. **NSS 协处理器运行在默认中频 (mid)**：上游 `nss_freq` 默认 `mid` 档，未利用 `high` 档位高频算力。
6. **ECM 延迟卸载包数偏大**：默认 4 包延迟才推入 NSS 硬件卸载，高频短连接软中断开销偏高。
7. **BSS Color 空间复用参数不完整**：缺少 `he_spr_non_srg_obss_pd_max_offset` 导致 OBSS_PD 空间复用控制字无法生效。
8. **2.4G 40MHz 频宽争抢丢包**：国内家庭 2.4GHz 干扰严重，40MHz 容易频繁退避和丢包，应规范为 `HE20`。
9. **512MB 内存设备 OOM 脆弱性**：亚瑟扣除 NSS 与固件 DMA 预留后仅剩 ~380MB 可用内存，需对 `hostapd`、`dnsmasq`、`netifd` 等网络核心服务设置 OOM 免疫。

---

## 2. 影响文件与修改范围

| 序号 | 文件路径 | 变更类型 | 变更核心职责 |
| :--- | :--- | :---: | :--- |
| 1 | `wrt_core/patches/992_set-wifi-uci.sh` | [MODIFY] | 修正 `multicast_to_unicast_all='1'`；补全 `he_spr_non_srg_obss_pd_max_offset='20'`；AX1800 Pro/AX6600 2.4G 设为 `HE20` |
| 2 | `wrt_core/deconfig/nss.config` | [MODIFY] | 修正 `kmod-qca-nss-drv-vlan-mgr=y`；移除 `sqm-scripts-nss` |
| 3 | `wrt_core/patches/smp_affinity` | [MODIFY] | 清理不存在的 AHB REO 环匹配；QCN9074 PCIe 中断绑定 CPU 0；EDMA 保持 CPU 1/2/3 错峰分布 |
| 4 | `wrt_core/patches/991_custom_settings` | [MODIFY] | NSS 机型首启锁定 `nss_freq.settings.level='high'`；注入 ECM `accel_delay_pkts=1`；为核心进程注入 OOM 豁免 |
| 5 | `wrt_core/modules/system.sh` | [MODIFY] | 修复失效 sed 路径，确保构建期直接 patch 上游 `qca-nss-pbuf.init` 调速器为 `performance` |
| 6 | `wrt_core/patches/tests/test_wifi_uci.sh` | [MODIFY] | 同步断言 `multicast_to_unicast_all`、`he_spr_non_srg_obss_pd_max_offset` 及 2.4G `HE20` |

---

## 3. 详细设计规格

### 3.1 Wi-Fi 射频参数与驱动模板对齐 (`992_set-wifi-uci.sh`)

1. **组播转单播键名修正**：
   ```sh
   # 修正前:
   set wireless.default_radio${radio}.multicast_to_unicast='1'
   # 修正后 (对齐 hostapd.sh / hostapd.uc):
   set wireless.default_radio${radio}.multicast_to_unicast_all='1'
   ```
2. **BSS Color 空间复用 (SR/OBSS_PD) 参数补全**：
   ```sh
   if [ "$is_2g" -eq 0 ] && echo "$htmode" | grep -q "^HE"; then
       uci -q batch <<EOF
   set wireless.radio${radio}.he_bss_color='42'
   set wireless.radio${radio}.he_bss_color_enabled='1'
   set wireless.radio${radio}.he_spr_psr_enabled='1'
   set wireless.radio${radio}.he_spr_non_srg_obss_pd_max_offset='20'
   EOF
   fi
   ```
3. **2.4G 频宽规范为 HE20**：
   - `jdc_ax1800_pro_wifi_cfg()`: `configure_wifi 1 1 HE20 23 'JDC_AX1800PRO' '12345678'`
   - `jdc_ax6600_wifi_cfg()`: `configure_wifi 1 1 HE20 23 'JDC_AX6600' '12345678'`

### 3.2 NSS 内核驱动包名纠正 (`wrt_core/deconfig/nss.config`)

```ini
# 由 -vlan 修正为真实包名 -vlan-mgr，移除已废弃的 sqm-scripts-nss
CONFIG_PACKAGE_kmod-qca-nss-drv-vlan-mgr=y
# CONFIG_PACKAGE_sqm-scripts-nss is not set
```

### 3.3 中断亲和性流水线重构 (`wrt_core/patches/smp_affinity`)

将 4 颗 Cortex-A53 核心分配为专业流水线：
- **CPU 0**：专门分配给 PCIe 外挂无线网卡（`ath11k_pci` / `pci` 中断）
- **CPU 1**：有线以太网接收描述符 (`edma_rxdesc`)
- **CPU 2**：有线以太网发送完成 (`edma_txcmpl`)
- **CPU 3**：有线以太网补包与杂项 (`edma_rxfill`, `edma_misc`)
- **NSS 队列**：`nss_queue0` 分配至 `2-3`

清理无效的 `reo2host-destination-ring*`、`wbm2host-tx-completions-ring*`、`ppdu-end-interrupts-mac*`（无线 NSS 卸载下这些环在 Linux 中不注册，避免无意义遍历）。

### 3.4 NSS、ECM 与系统稳态调优 (`wrt_core/patches/991_custom_settings` & `system.sh`)

1. **NSS 主频提升至 `high`**：
   ```sh
   if [ -f "$TARGET_ETC/config/nss_freq" ] && command -v uci >/dev/null 2>&1; then
       uci -q set nss_freq.settings.level='high'
       uci commit nss_freq
   fi
   ```
2. **ECM 加速延迟时序收紧**：
   在系统启动任务中注入：
   ```sh
   if [ -d /sys/kernel/debug/ecm/ecm_classifier_default ]; then
       echo 1 > /sys/kernel/debug/ecm/ecm_classifier_default/accel_delay_pkts 2>/dev/null
   fi
   ```
3. **512MB 设备核心守护进程 OOM 防御**：
   在开机时通过 `pgrep` 将系统关键进程的 OOM 调整为免死：
   ```sh
   for proc in hostapd dnsmasq netifd; do
       for pid in $(pidof $proc 2>/dev/null); do
           echo -1000 > "/proc/$pid/oom_score_adj" 2>/dev/null
       done
   done
   ```
4. **构建期 patch 上游 `qca-nss-pbuf.init` 调速器**：
   在 `system.sh` 的 `update_nss_pbuf_performance()` 中：
   ```bash
   local pbuf_init="$BUILD_DIR/package/kernel/mac80211/files/qca-nss-pbuf.init"
   if [ -f "$pbuf_init" ]; then
       sed -i 's/governor="schedutil"/governor="performance"/g' "$pbuf_init"
   fi
   ```
   清理失效的 `pbuf.uci` 与不存在的 `package/feeds/nss_packages/...` sed 替换代码。

---

## 4. 验证与回归计划

1. **自动化单元测试**：
   - 运行 `test_wifi_uci.sh`，断言输出包含 `multicast_to_unicast_all='1'`、`he_spr_non_srg_obss_pd_max_offset='20'`、`HE20`（针对 2.4G）。
   - 运行 `test_custom_settings.sh`，验证 RAM 分级与 NSS 判定分支逻辑。
2. **静态语法检查**：
   - 所有变更的 Shell 脚本均通过 `bash -n` 校验，无语法错误。
3. **构建流程无损验证**：
   - 验证 `nss.config` 与 `system.sh` 在构建链路中的一致性。
