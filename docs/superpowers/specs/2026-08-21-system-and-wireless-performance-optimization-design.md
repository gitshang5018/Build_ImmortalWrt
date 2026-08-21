# 系统与无线性能深度优化设计规范 (Spec)

## 1. 概述与目标

本项目基于 ImmortalWrt 源码定制，覆盖 Qualcomm IPQ60xx、MediaTek MT7621、Qualcomm IPQ40xx 以及 X86_64 等多款硬件架构。
本次优化的目标是在**不增加固件体积、不引入复杂第三方守护进程**的前提下，对系统的**无线射频调度/快速漫游**与**系统内存自适应/网络数据包处理队列**进行全面深度的升级。

### 主要解决的痛点：
1. **无线空口并发与漫游协议链不全**：缺少完整的 Wi-Fi 6 上下行 OFDMA/MU-MIMO、Airtime Fairness (ATF) 调度支持；802.11r 缺少统一的 `mobility_domain` 与 `nasid`，导致多端漫游握手失败或回退。
2. **Go 语言后台服务内存限制失效**：原 `GOMEMLIMIT` 仅写在 `/etc/profile`，未被 OpenWrt procd 守护进程（MosDNS/Xray/Sing-box/Lucky）加载，小内存机型存在 OOM 风险。
3. **系统内核参数一刀切**：写死固定的 TCP 缓冲区和 Conntrack 大小，小内存（128MB）容易内存枯竭，大内存（X86 8GB+）无法跑满 2.5G/10G 极限并发。
4. **NAPI 队列瓶颈**：默认软中断单次轮询预算过低，突发大流量易丢包。

---

## 2. 影响文件与架构定位

| 文件路径 | 变更类型 | 核心职责 |
| :--- | :--- | :--- |
| `wrt_core/patches/992_set-wifi-uci.sh` | [MODIFY] | 补全 Wi-Fi 6 (802.11ax) 空口参数、ATF、防降速、802.11r 漫游域与 NASID |
| `wrt_core/patches/991_custom_settings` | [MODIFY] | 实现基于物理内存（MemTotal）的三档自适应网络栈、系统级的 Procd Go 内存控制、NAPI 预算调度 |
| `wrt_core/modules/system.sh` | [MODIFY] | 确保 UCI defaults 补丁在系统构建时正确安装与分发 |

---

## 3. 详细设计规格

### 3.1 无线性能与漫游协议链优化 (`992_set-wifi-uci.sh`)

在通用函数 `configure_wifi` 中扩展并完善以下配置：

1. **Wi-Fi 6 (802.11ax) 完整空口并发与波束赋形**：
   - `he_dlofdma='1'`：开启下行 OFDMA。
   - `he_ulofdma='1'`：开启上行 OFDMA。
   - `he_dl_mumimo='1'`：开启下行 MU-MIMO。
   - `he_ul_mumimo='1'`：开启上行 MU-MIMO。
   - `he_su_beamformer='1'`：开启显式单用户波束成形（Beamformer）。
   - `he_su_beamformee='1'`：开启显式单用户波束赋形接收（Beamformee）。
   - `he_mu_beamformer='1'`：开启显式多用户波束成形（MU-Beamformer）。
   - `he_twt='1'`：开启目标唤醒时间（Target Wake Time）。
   - `airtime_fairness='1'`：开启空口时间公平调度（ATF），压制低速旧设备霸占信道。

2. **频宽与射频抗干扰**：
   - 2.4G 频段（radio 对应 2.4G 时）设置 `noscan='1'` 与 `he_coext='0'`，防止强行回退至 20MHz。
   - 通用：设置 `dtim_period='2'` 与 `short_preamble='1'`。

3. **802.11r / k / v 漫游协议链补齐**：
   - `mobility_domain='e4a1'`：定义统一 16 进制 4 字符漫游域标识。
   - `nasid` 与 `r1_key_holder`：按 `radio${radio}` 或统一生成，确保 FT-PSK 密钥在本地正确关联。
   - 保留：`ieee80211k='1'`, `bss_transition='1'`, `ieee80211r='1'`, `ft_psk_generate_local='1'`, `ft_over_ds='1'`, `ieee80211w='1'`, `disassoc_low_ack='0'`, `multicast_to_unicast='1'`。

---

### 3.2 系统内存自适应分级引擎与网络栈调优 (`991_custom_settings`)

在系统开机初始化阶段（`/etc/uci-defaults/991_custom_settings`），根据 `/proc/meminfo` 的 `MemTotal`（kB）划分三档策略并动态配置：

```
MemTotal < 300000 kB (低内存档: 歌华链/竞斗云)
├── conntrack_max: 32768
├── tcp_mem: 16384 32768 65536
├── tcp_rmem / tcp_wmem: 4096 32768 1048576 (最大1MB)
├── somaxconn / netdev_max_backlog: 1024
├── vfs_cache_pressure: 150
├── Dnsmasq cachesize: 2500, min_cache_ttl: 120
└── Go 运行时: GOMEMLIMIT=48MiB, GOGC=30

300000 kB <= MemTotal <= 1500000 kB (中内存档: 京东云等 512MB~1GB)
├── conntrack_max: 65535
├── tcp_mem: 65536 131072 262144
├── tcp_rmem / tcp_wmem: 4096 87380 4194304 (最大4MB)
├── somaxconn / netdev_max_backlog: 4096
├── vfs_cache_pressure: 120
├── Dnsmasq cachesize: 10000, min_cache_ttl: 300
└── Go 运行时: GOMEMLIMIT=128MiB, GOGC=50

MemTotal > 1500000 kB (大内存档: X86 / 软路由 2GB+)
├── conntrack_max: 262144
├── tcp_mem: 262144 524288 1048576
├── tcp_rmem / tcp_wmem: 4096 131072 16777216 (最大16MB满速窗口)
├── somaxconn / netdev_max_backlog: 8192
├── vfs_cache_pressure: 100
├── Dnsmasq cachesize: 15000, min_cache_ttl: 600
└── Go 运行时: GOMEMLIMIT=512MiB, GOGC=80
```

#### NAPI 队列与网络处理调度（全平台通用）：
- `net.core.netdev_budget = 600`
- `net.core.netdev_budget_usecs = 4000`
- `net.core.default_qdisc = fq`
- `net.ipv4.tcp_congestion_control = bbr`
- `net.ipv4.tcp_fastopen = 3`
- `net.ipv4.tcp_tw_reuse = 1`

#### Go 语言后台服务全局环境注入：
- 写入 `/etc/environment`。
- 写入 `/etc/profile.d/gomem.sh`。
- 注入 `/etc/rc.local` 或 procd 系统初始环境变量，确保常驻后台服务（PassWall, MosDNS, Xray, Sing-box, Lucky）真正遵守内存边界与垃圾回收策略。

---

## 4. 异常处理与向下兼容设计

1. **向下兼容 Wi-Fi 4/5 硬件**：
   对于 MT7621 / IPQ4019 等 Wi-Fi 5 或更早设备，mac80211 驱动对于未支持的 `he_*` 选项会自动静默忽略，不会导致无线接口崩溃。
2. **UCI 幂等性与已有配置保护**：
   `992_set-wifi-uci.sh` 中对 `default_radio` 已经存在加密配置的场景保持保护，不强行覆盖用户自定义密码。
3. **低内存安全防爆**：
   限制了小内存设备上的 Conntrack 与 TCP 内存，配合 ZRAM Swap 保证系统极端压力下的存活性。

---

## 5. 验证计划

1. **语法与构建脚本校验**：
   - 对修改后的 `991_custom_settings` 与 `992_set-wifi-uci.sh` 执行 `bash -n` 语法检查。
2. **模拟不同内存场景验证**：
   - 编写本地测试脚本模拟 `MemTotal=128MB`、`1024MB`、`4096MB`，验证计算逻辑与 sysctl 输出分支正确无误。
3. **UCI 与参数格式验证**：
   - 验证无线 UCI 属性键值符合 OpenWrt / mac80211 标准规范。
