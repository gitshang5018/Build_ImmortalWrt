# IPQ6000 系列无线射频调度与多核网络栈深度优化设计规范 (Spec)

## 1. 概述与优化目标

本项目基于 ImmortalWrt 定制，支持高通 IPQ60xx（如京东云 AX1800 Pro / 亚瑟、AX6600 / 雅典娜、红米 AX5、360V6 等）等多款硬件架构。
本方案针对 IPQ6000 系列在无线射频性能、协议握手、多核中断均衡以及启动时序等方面存在的痛点进行深度系统化优化，在**不破坏向下兼容性、不引入多余守护进程**的前提下，充分释放高通 IPQ6018 / QCN5022 / QCN5052 / QCN9074 硬件芯片的全部潜力。

### 核心解决的痛点：
1. **BSS Coloring 静默失效**：现有脚本只配置了 `he_bss_color`，根据 OpenWrt ucode 架构 `hostapd.uc`，缺少 `he_bss_color_enabled` 会导致整个 BSS Color 及 OBSS-PD 空间复用配置被完全跳过。
2. **Wi-Fi 6 关键射频能力未激活**：仅开启了 Wi-Fi 5 的 `mu_beamformer`，缺少单用户/多用户显式波束成形（`he_su_beamformer`, `he_su_beamformee`, `he_mu_beamformer`）以及节能/防抢占目标唤醒时间（`he_twt_responder`）。
3. **802.11k/v 漫游辅助协议链不完整**：缺少 `rrm_neighbor_report`、`rrm_beacon_report` 与 `wnm_sleep_mode`，影响终端漫游切换决策质量。
4. **多核中断亲和性存在单核热点 (Hot CPU Core)**：现有 `smp_affinity` 将所有有线以太网 DMA 中断全部压在 CPU 3，与无线 RX/TX 环形队列重叠，导致有线转无线全速吞吐时 CPU 3 单核打满成为系统瓶颈。
5. **ath11k 驱动统计开销与调速器覆盖**：在高并发打流下 debugfs 统计计数造成软中断抖动；`qca-nss-pbuf` 在开机末期容易将预设的 `performance` 调速器覆盖为 `schedutil`。
6. **AX6600 (雅典娜) 独立电竞网卡 (QCN9074) 天线与频宽挖掘**：未显式声明 4x4 天线波束成形参数。

---

## 2. 影响文件与架构定位

| 文件路径 | 变更类型 | 核心职责 |
| :--- | :---: | :--- |
| `wrt_core/patches/992_set-wifi-uci.sh` | [MODIFY] | 修复 BSS Color，补全 Wi-Fi 6 波束成形/TWT，补全 11k/v 漫游，配置 AX6600 4x4 天线规格 |
| `wrt_core/patches/smp_affinity` | [MODIFY] | 重构 4 核中断绑核分布，分离有线以太网与无线收发环，消除 CPU 3 单核热点 |
| `wrt_core/patches/991_custom_settings` | [MODIFY] | 注入 ath11k stats_disable 调优命令与保持 performance 稳态调速器 |
| `wrt_core/modules/system.sh` | [MODIFY] | 清理废弃的 `pbuf.uci` 修改逻辑，理顺 `qca-nss-pbuf` 调速器策略 |
| `wrt_core/patches/tests/test_wifi_uci.sh` | [MODIFY] | 扩展测试用例，增加对新增 Wi-Fi 6 特性、天线规格、漫游参数的严格断言 |

---

## 3. 详细设计规格

### 3.1 无线配置与 hostapd.uc 映射优化 (`992_set-wifi-uci.sh`)

1. **BSS Coloring 与空间复用 (OBSS-PD)**：
   - 对 5G HE/EHT 射频段配置：
     - `he_bss_color='42'`
     - `he_bss_color_enabled='1'`（关键：触发 hostapd.uc 写入参数）
     - `he_spr_psr_enabled='1'`（Parameterized Spatial Reuse 空间复用）

2. **Wi-Fi 6 (HE) 显式波束成形与目标唤醒时间 (TWT)**：
   - 仅对 `htmode` 为 `HE*` 或 `EHT*` 的 radio 段配置：
     - `he_su_beamformer='1'`：开启 SU-Beamformer（发送端波束赋形，提升信号覆盖与穿墙）
     - `he_su_beamformee='1'`：开启 SU-Beamformee（接收端波束赋形）
     - `he_mu_beamformer='1'`：开启 MU-Beamformer（多用户下行波束成形）
     - `he_twt_responder='1'`：开启 TWT 响应端（支持设备低功耗与时隙调度）

3. **802.11k/v 漫游链路补齐**：
   - 在接口段（`default_radio${radio}`）补充：
     - `rrm_neighbor_report='1'`：启用 802.11k 邻居报告能力通告
     - `rrm_beacon_report='1'`：启用信标测量请求与报告
     - `wnm_sleep_mode='1'`：启用 802.11v WNM 睡眠模式协同

4. **JDC AX6600 (雅典娜) 专属优化**：
   - Radio 2（QCN9074 4x4 160MHz 5.2G 网卡）显式声明：
     - `beamformer_antennas='4'`
     - `beamformee_antennas='4'`

---

### 3.2 多核中断亲和性重构 (`smp_affinity`)

IPQ60xx 为 4 核心（CPU 0, 1, 2, 3）。将高频中断均衡到不同核心：
- **CPU 0**：`reo2host-destination-ring1` (无线主要接收流 1)
- **CPU 1**：`edma_rxdesc` (有线以太网接收描述符), `reo2host-destination-ring2`, `wbm2host-tx-completions-ring1`, `bam_dma 1`
- **CPU 2**：`edma_txcmpl` (有线以太网发送完成), `reo2host-destination-ring3`, `wbm2host-tx-completions-ring2`, `ppdu-end-interrupts-mac1`, `bam_dma 2`
- **CPU 3**：`edma_rxfill`, `edma_misc`, `reo2host-destination-ring4`, `wbm2host-tx-completions-ring3`, `ppdu-end-interrupts-mac2`, `ppdu-end-interrupts-mac3`
- **NSS 队列**：`nss_queue0` 分布在 `2-3`。
实现有线接收（CPU 1）与有线发送（CPU 2）彻底解耦，消除 CPU 3 单核冲顶 100% 的瓶颈。

---

### 3.3 驱动与内核调优 (`system.sh` / `991_custom_settings`)

1. **关闭 ath11k 调试统计**：
   在系统自定义任务或启动脚本中，针对 `/sys/kernel/debug/ath11k/*/stats_disable` 写入 `1`。
2. **清理废弃的 `pbuf.uci` 逻辑**：
   从 `system.sh` 中移除针对不存在的 `pbuf.uci` 的无用替换，改为针对 `qca-nss-pbuf.init` 中的调速器（如 `schedutil`）对齐全局 `performance` 调速器，防止开机后 CPU 调频策略被意外退回。

---

## 4. 验证与回归测试

1. 扩展 `wrt_core/patches/tests/test_wifi_uci.sh`，断言：
   - Wi-Fi 6 设备输出包含 `he_bss_color_enabled='1'`, `he_su_beamformer='1'`, `he_twt_responder='1'`；
   - 接口输出包含 `rrm_neighbor_report='1'`, `rrm_beacon_report='1'`, `wnm_sleep_mode='1'`；
   - AX6600 radio2 输出包含 `beamformer_antennas='4'`, `beamformee_antennas='4'`；
   - Wi-Fi 5 设备（歌华链等）依然严格跳过所有 HE/EHT 专有属性，避免 hostapd 解析报错。
2. 语法校验：对所有修改过的 shell 脚本执行 `bash -n`。
3. 单元测试运行：确保所有本地自动化测试全部通过。
