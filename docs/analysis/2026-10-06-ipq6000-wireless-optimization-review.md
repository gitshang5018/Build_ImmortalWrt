# IPQ6000 系列（ipq60xx）无线优化空间评估

**评估对象**：本仓库 `jdcloud_ipq60xx_immwrt` 目标（京东云亚瑟 AX1800 Pro / `jdcloud,re-ss-01`，IPQ6000；京东云雅典娜 AX6600 / `jdcloud,re-cs-02`，IPQ6010 + QCN9074）
**评估日期**：2026-10-06
**构建源**：`VIKINGYFY/immortalwrt@main`（`wrt_core/compilecfg/jdcloud_ipq60xx_immwrt.ini`，BUILD_DIR=`imm-nss`），内核 6.18，NSS 12.5 retail

---

## 0. 结论摘要

**有优化空间，但空间不在“再加几个 Wi-Fi 6 开关”，而在“修正三处无效/失效的配置、回收两处被自己覆盖掉的上游改进、把无线性能链路（NSS offload → pbuf/N2H → IRQ 亲和 → 调速器）的实际状态先测出来”。**

按证据强度分三档：

| 档位 | 内容 | 说明 |
| :--- | :--- | :--- |
| **A. 已证实无效或失效，可直接修** | `multicast_to_unicast` 写错键名（真正生效的是 `multicast_to_unicast_all`）；`qam256` 不是 UCI 选项；`update_nss_pbuf_performance()` / `update_nss_diag()` / `update_script_priority()` 的 drv 半段指向已不存在的文件路径（静默空转）；自有 `smp_affinity` 的 REO/PPDU 环 pin 在 NSS offload 下结构性失效，且覆盖/删除了上游 `smp_affinity` + `set-irq-affinity`（丢掉 GRO/校验和与 RPS/XPS） | 全部有上游源码实证 |
| **B. 有效但需设备实测后启用** | Wi-Fi NSS offload 默认开启（已证实），但需确认运行态真的卸载成功；pbuf/N2H 是否按上游 MemTotal 分档下发；调速器实测值（上游 `qca-nss-pbuf.init` 每次开机写 `schedutil`，会覆盖本项目的 `performance` 意图）；NSS 时钟档位 | 需在设备上取运行态 |
| **C. 不建议照抄旧文档再做** | 2026-08-21 设计文档里的 `he_dlofdma` / `he_ulofdma` / `he_dl_mumimo` / `he_ul_mumimo` / `he_twt` / `airtime_fairness` / `he_coext` **都不是 UCI 选项**；`ieee80211r` 那次回滚把 PMF 和 11r 一起改了，根因未定位 | 有回滚提交实证 |

---

## 1. 项目无线链路现状（数据流）

```
build.sh jdcloud_ipq60xx_immwrt
  └─ wrt_core/update.sh
       ├─ remove_unwanted_packages()   → 删除上游 qualcommax/etc/uci-defaults/99*.sh（含 991_set-network.sh、992_set-nss-load.sh）
       ├─ remove_something_nss_kmod()   → 从 qualcommax/Makefile 删 kmod-qca-nss-drv-{match,mirror,map-t,gre,eogremgr,tun6rd,tunipip6,vxlanmgr} 等
       ├─ update_affinity_script()      → 删除上游 set-irq-affinity / smp_affinity，安装 wrt_core/patches/smp_affinity
       ├─ update_nss_pbuf_performance() → 改 package/kernel/mac80211/files/pbuf.uci（上游已无此文件 → 空转）
       ├─ update_nss_diag()             → 安装 patches/nss_diag.sh 到 mac80211/files/nss_diag.sh（上游已无此文件 → 空转）
       ├─ update_script_priority()      → qca-nss-drv START=88（路径不存在 → 空转）、qca-nss-pbuf START=89（生效）、mosdns START=94
       └─ fix_ath11k_nss_timer_api()    → 适配内核高版本定时器 API
  └─ apply_config() 合并 nss.config（kmod-qca-nss-* / kmod-ath11k / wpad-mesh-openssl / sqm-scripts-nss）

运行时（固件内）
  /etc/uci-defaults/991_custom_settings  ← 首启一次：sysctl 分档、conntrack、Go 内存、CPU performance、packet_steering=1
  /etc/uci-defaults/992_set-wifi-uci.sh  ← 首启一次：信道/带宽/国家/功率/k-v/he_bss_color/noscan/qam256
  /etc/init.d/qca-nss-pbuf (START=89)    ← 每次开机：NSS pbuf/N2H 分档、auto_scale 0、RPS bitmap、ath11k stats_disable、schedutil、reload_wifi
  /etc/init.d/smp_affinity (START=93)    ← 本项目旧版脚本（pin REO/PPDU 环；NSS offload 下这些环不注册）
```

关键含义：**`991` / `992` 是 uci-defaults（仅首次启动执行且执行后删除），`qca-nss-pbuf.init` 是每次开机执行**。所以“每次开机生效的稳态策略”由上游 init 决定，而不是由本项目 patch 决定。

---

## 2. 已证实的事实（含证据）

### 2.1 项目侧

| # | 事实 | 证据 |
| :-- | :--- | :--- |
| P1 | `992` 设置的 `multicast_to_unicast='1'` 不是 hostapd 指令的来源；hostapd.sh 只读 `multicast_to_unicast_all` | [hostapd.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/network/config/wifi-scripts/files/lib/netifd/hostapd.sh)：`config_add_boolean multicast_to_unicast multicast_to_unicast_all …`，但 `json_get_vars … multicast_to_unicast_all …` + `set_default multicast_to_unicast_all 0` 才 `append bss_conf "multicast_to_unicast=…"` |
| P2 | `qam256`、`he_coext`、`he_dlofdma`、`he_ulofdma`、`he_dl_mumimo`、`he_ul_mumimo`、`he_twt`、`airtime_fairness` 均不被任何脚本读取 | [mac80211.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/network/config/wifi-scripts/files/lib/netifd/wireless/mac80211.sh) 的 `config_add_boolean` / `config_add_int` 列表与 `mac80211_add_he_capabilities` 调用点；hostapd.sh 全文无这些名字 |
| P3 | 真实存在的 HE 旋钮只有 8 个：`he_su_beamformer`、`he_su_beamformee`、`he_mu_beamformer`（默认均 1）、`he_twt_required`（默认 0）、`he_bss_color`（默认 128）、`he_bss_color_enabled`、`he_spr_sr_control`、`he_spr_psr_enabled`、`he_spr_non_srg_obss_pd_max_offset` | mac80211.sh `json_get_vars he_su_beamformer:1 he_su_beamformee:1 he_mu_beamformer:1 he_twt_required:0 he_spr_sr_control:3 … he_bss_color:128` |
| P4 | ATF（空口公平）真正对应的是 `airtime_mode`（radio）+ `airtime_bss_weight` / `airtime_bss_limit` / `airtime_sta_weight`（iface） | hostapd.sh `config_add_int airtime_mode` / `[ "$airtime_mode" -gt 0 ] && append base_cfg "airtime_mode=$airtime_mode"`；`airtime_bss_weight`、`airtime_sta_weight` 段 |
| P5 | `update_nss_pbuf_performance()` 的目标文件 `package/kernel/mac80211/files/pbuf.uci` 已不存在，函数静默跳过 | 上游该目录只剩 `disable_mesh_chksum_ath11k.hotplug`、`qca-nss-pbuf.init`（[目录列表](https://api.github.com/repos/VIKINGYFY/immortalwrt/contents/package/kernel/mac80211/files)）；本地 `wrt_core/modules/system.sh:302-311` |
| P6 | 上游把 pbuf/N2H 调优整体搬进了 `qca-nss-pbuf.init`：按 MemTotal 三档写 `extra_pbuf_core0` / `n2h_high_water_core0` / `n2h_wifi_pool_buf`，`auto_scale 0`、`n2h_queue_limit_* 2048`、RPS hash_bitmap、ath11k `stats_disable=1`，**并把调速器设为 `schedutil`**，最后 `reload_wifi` | [qca-nss-pbuf.init](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/files/qca-nss-pbuf.init) |
| P7 | 上游 `smp_affinity` 已重写：只 pin `edma_rxdesc`→CPU1、`edma_txcmpl`→CPU2，并 `ethtool -K` 打开 eth* 的 GRO/RX/TX 校验和；IRQ 名支持 `name_` 前缀后缀匹配 | [上游 smp_affinity](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/smp_affinity)（START=94） |
| P8 | 本项目 `patches/smp_affinity`（START=93）是旧版 ipq807x 风格：pin `reo2host-destination-ring1..4`、`wbm2host-tx-completions-ring1..3`、`ppdu-end-interrupts-mac1..3`、`edma_*`→CPU3、`nss_queue0`→2-3、`bam_dma`；**没有** GRO/校验和配置 | 本地 `wrt_core/patches/smp_affinity`；`wrt_core/modules/system.sh:132-140` 会先删除上游脚本再安装它 |
| P9 | 上游 qualcommax `DEFAULT_PACKAGES` 含 `cpufreq`（`package/emortal/cpufreq`，提供 `/etc/init.d/cpufreq` 与 `10-cpufreq`），本项目 `remove_something_nss_kmod()` 把它从目标 Makefile 删掉 | [qualcommax Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/Makefile)；[cpufreq/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/emortal/cpufreq/Makefile)；本地 `wrt_core/modules/system.sh:128` |
| P10 | 现有测试通过，但只覆盖 2 个机型，且断言的是字符串而非“选项是否被上游读取”；两个测试都没有接入任何 workflow | 本地 `test_wifi_uci.sh` / `test_custom_settings.sh`（本次已用 Git Bash 实跑，均 exit 0）；`.github/workflows/*` 只在 aerowrt/theme 中调用 tests |
| P11 | 2026-08-21 的“系统与无线性能优化”设计/计划文档仍在仓库内且任务全为未勾选状态，但其方案大部分已在 `b65ca6d` 实现又在 `70e75e7` 回滚 | `docs/superpowers/specs|plans/2026-08-21-system-and-wireless-performance-optimization*`；`git show 70e75e7 -- wrt_core/patches/992_set-wifi-uci.sh` |
| P12 | **Wi-Fi NSS offload 在本构建里默认开启**：`config ATH11K_NSS_SUPPORT` 对 `TARGET_qualcommax` **`default y`**，因此 NSS 补丁系列会被应用、`qca-nss-pbuf.init` 会被安装，且 ath11k 模块以 `MODPARAMS.ath11k:=nss_offload=1 frame_mode=2` 自动加载 | [ath.mk](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/ath.mk)：`KernelPackage/ath11k/config` 中 `config ATH11K_NSS_SUPPORT … default y`；`KernelPackage/ath11k` 的 `ifdef` 段；[mac80211/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/Makefile) 的 `ifdef CONFIG_ATH11K_NSS_SUPPORT` 段 |
| P13 | `update_script_priority()` 里 `qca-nss-drv` 的目标路径 `package/feeds/nss_packages/qca-nss-drv/files/qca-nss-drv.init` 与实际不符：本树的 NSS 包是**源码树内置**（`package/qca-nss/qca-nss-drv/…`），`feeds.conf.default` 里根本没有 `nss_packages` 这个 feed。因此 `START=88` 那半段同样是静默空转（pbuf 的 `START=89` 半段路径真实存在，能生效） | 本地 `wrt_core/modules/system.sh:366-376`；[feeds.conf.default](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/feeds.conf.default)（仅 packages/luci/routing/telephony/video）；[package/qca-nss 目录](https://api.github.com/repos/VIKINGYFY/immortalwrt/contents/package/qca-nss) |
| P14 | 启用 `ATH11K_NSS_SUPPORT` 会连带强制内核 `ATH11K_MEM_PROFILE_512M`（`config-$(CONFIG_ATH11K_NSS_SUPPORT) += … ATH11K_MEM_PROFILE_512M`），即 ath11k 固件按 512 MB 档分配内存；512 MB 机型合适，1 GB 机型偏保守 | ath.mk 同上 |
| P15 | **NSS offload 生效时 ath11k 根本不会注册 REO/PPDU 目的环中断**：`199-003` 给 `ath11k_ahb_config_ext_irq()` 加了 `if (!nss_offload && …ring_mask…)` 守卫，注释写明 “TCL Completion, REO Dest, ERR, Exception and h2rxdma rings are offloaded”；而 `nss_offload` 由 ath.mk 默认置 1 | [199-003-ath11k-add-nss-support.patch](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/patches/nss/ath11k/199-003-ath11k-add-nss-support.patch) |
| P16 | 上游 qualcommax 的 IRQ 脚本职责已重划：`set-irq-affinity`（START=99）**不 pin 任何 IRQ 名**，只做 RPS/XPS（`rps_cpus`/`xps_cpus` 全核掩码、`rps_flow_cnt=8192`、`rps_sock_flow_entries=65535`）；`smp_affinity`（START=94）只 pin `edma_rxdesc`→CPU1、`edma_txcmpl`→CPU2，外加 `ethtool -K gro/rx/tx on` | [set-irq-affinity](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/set-irq-affinity)、[smp_affinity](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/smp_affinity) |
| P17 | 上游 qualcommax 首启脚本 `991_set-network.sh` 设 `network.globals.packet_steering='0'`，但本项目 `remove_unwanted_packages()` 用 `find … -name "99*.sh" -exec rm` 把 `991_set-network.sh`、`992_set-nss-load.sh` 一并删掉了；因此本项目 `991` 里的 `packet_steering='1'` 会保留下来（与上游取向相反） | 本地 `wrt_core/modules/packages.sh:76-78`、`wrt_core/patches/991_custom_settings:167-171`；[上游 991_set-network.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/uci-defaults/991_set-network.sh) |
| P18 | 本项目把 NSS 驱动特征包大幅裁剪（删 `kmod-qca-nss-drv-{match,mirror,map-t,gre,eogremgr,tun6rd,tunipip6,vxlanmgr}` 与 `kmod-qca-nss-macsec`，12 行中的 9 行）。上游 `qca-nss-drv/Makefile` 的 `DisableNssDrvFeatureWithoutPackages(WIFIOFFLOAD, match netlink wifi-meshmgr)` 是 **OR 判定**（`ifeq ($(strip …list…),)`），而 `netlink` 仍在 DEFAULT_PACKAGES、`wifi-meshmgr` 由 `kmod-ath11k` 依赖带入，再加上 `+@(PACKAGE_…netlink):NSS_DRV_WIFIOFFLOAD_ENABLE` 这类条件 select，**所以 Wi-Fi offload 不会被强制关闭**；但 `MATCH`/`MIRROR` 等特征确实被编译掉 | 本地 `wrt_core/modules/system.sh:106-129`；[qca-nss-drv/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/qca-nss/qca-nss-drv/Makefile) |
| P19 | **QCN9074（雅典娜 radio2 的 5.2G 4x4）没有 NSS offload 证据**：`199-003` 的 `pci.c` 改动只加 `ab->mem_pa = pci_resource_start(...)`，NSS 中断卸载逻辑全在 AHB 路径；即雅典娜只有 IPQ6010 内置的 AHB 射频（radio0/radio1）可能吃到 NSS offload | 同上 patch；`target/linux/qualcommax/image/ipq60xx.mk` |
| P20 | ipq6018 的 NSS 预留内存是固定的 64 MiB `no-map` `nss_region`，且 `q6_region` 由 85 MiB 缩到 64 MiB；另外 `re-cs-02` 的 DTS 通过 `qcom,ath11k-fw-memory-mode = <1>` 调 ath11k 固件内存档，而 `re-ss-01`（亚瑟）没有该属性 | [0103-arm64-dts-ipq6018-add-reserved-memory-nodes.patch](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/patches-6.18/0103-arm64-dts-ipq6018-add-reserved-memory-nodes.patch)、[ipq6010-re-cs-02.dts](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/dts/ipq6010-re-cs-02.dts) |
| P21 | 上游 `15_nss_cleanup.sh` 首启删除 `/etc/config/{ecm,nss,pbuf,skb_recycler,smp_affinity}` —— UCI 形式的 pbuf/NSS 配置已被上游废弃，改为 `qca-nss-pbuf.init` 纯 sysctl | [15_nss_cleanup.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/uci-defaults/15_nss_cleanup.sh) |


### 2.2 回滚提交告诉我们的两件事

`70e75e7`（“fix client association reject”）一次性删除了：

- `airtime_fairness`、`short_preamble`、`dtim_period`
- `he_su_beamformer/-formee`、`he_mu_beamformer`、`he_dlofdma`、`he_ulofdma`、`he_dl_mumimo`、`he_ul_mumimo`、`he_twt`
- `ieee80211r`、`mobility_domain`、`nasid`、`r1_key_holder`、`ft_psk_generate_local`、`ft_over_ds`
- `time_advertisement`、`time_zone`、`wnm_sleep_mode`、`wnm_sleep_mode_no_keys`
- 同时把 `ieee80211w` 从 `1` 改成 `0`，把 `he_coext='0'` 删除

**同时改了两个可能致因（11r 体系 vs PMF 取值），属于混杂修复，根因未定位。** 另外按 hostapd.sh，PSK 场景下 `ft_psk_generate_local` 默认就是 1，此时 `r1_key_holder` / `r0kh` / `r1kh` 分支根本不走，`mobility_domain` 缺省也会用 `md5(ssid)[0:4]` 自动生成——也就是说，旧方案里“统一漫游域 + nasid + r1_key_holder”大部分是冗余的，它同时把 `ieee80211w` 改成 PMF capable，才是更可疑的变量。

---

## 3. IPQ6000 无线优化清单（按优先级）

### A 档：确定可做

**A1. 修 `multicast_to_unicast` 键名（正确性）**
现状 `992` 第 45 行写 `multicast_to_unicast='1'`，测试第 45 行还把它当通过条件。上游只认 `multicast_to_unicast_all`，所以“组播转单播消除丢包”这个目标实际没有达成；VIKINGYFY 树还额外提供 `na_mcast_to_ucast`（IPv6 NA）。
建议：改为 `multicast_to_unicast_all='1'`（如需 IPv6 一并 `na_mcast_to_ucast='1'`），同步更新测试断言；若确实想保留旧键用于 bridge snooping，请显式注释它的真实作用。

**A2. 删除 `qam256='1'`**
不是 UCI 选项，VHT/HE 的 256-QAM 由 `htmode` + 能力位决定，无开关。留着只会误导后来者（测试也未覆盖）。同理，`he_coext` 已在回滚中删除，不要再加回。

**A3. 让三个死掉的上游 patch 生效或直接删掉**
`update_nss_pbuf_performance()`（system.sh:302-311）、`update_nss_diag()`（system.sh:320-326）、`update_script_priority()` 中的 `qca-nss-drv` 半段（system.sh:366-371，见 P13）目标文件/路径均不存在，属于静默空转的“假优化”。二选一：
- 若仍想强制性能策略 → 改成改 `qca-nss-pbuf.init`（见 A4），并把 `qca-nss-drv` 的路径改为 `package/qca-nss/qca-nss-drv/...`；
- 若不再需要 → 删除函数与 `update.sh` 调用，避免“看起来做了优化”的错觉。

**A4. 调速器意图与实现冲突（本项目声明与运行态不一致）**
- `991`（首启一次）写 `performance` 并 `scaling_min_freq = cpuinfo_max_freq`；
- 上游 `qca-nss-pbuf.init`（每次开机）写 `schedutil`，且发生在 uci-defaults 之后（项目改到 START=89，上游为 27，两者都在 uci-defaults 的 boot 阶段之后）；
- 由 P12 可知该 init 的入口条件（`nss_offload=1`）在本构建里默认成立，所以**只要运行态 offload 正常，每次开机都会被改回 `schedutil`**；而 `991` 的 `performance` 只在首启那一次短暂生效。
- `991` 第 205-208 行注释写“两处取值必须保持一致（当前均为 performance）”，其依据的 `pbuf.uci` 已被上游删除，注释与事实双重过期。
建议：若要 performance，应在构建期 patch `qca-nss-pbuf.init` 的 `governor="schedutil"`（这才是每次开机会执行、且带 fallback 检查的位置），并显式接受功耗/温度代价；若保留 schedutil，则删掉 `991` 里的 `performance` 写入或改为可配置项，并修正注释。


**A5. 重做 IRQ 亲和脚本（当前实现在 IPQ6000 上结构性失效，且丢掉了上游的 RPS/XPS）**
三层问题：
1. 本项目 `patches/smp_affinity` pin 的是 `reo2host-destination-ring1..4`、`wbm2host-tx-completions-ring1..3`、`ppdu-end-interrupts-mac1..3`。但由 P15，**NSS offload 生效时 ath11k 不再注册 REO/PPDU 目的环中断**，这些名字在 `/proc/interrupts` 里根本不存在，脚本会静默全部不命中。
2. 上游自身的 `smp_affinity` 只 pin `edma_rxdesc`/`edma_txcmpl` 并打开 `ethtool -K` GRO/校验和；本项目用旧版覆盖它，等于反向上游。
3. `update_affinity_script()` 还删掉了上游 `set-irq-affinity`（P16），连带丢掉 RPS/XPS 配置（`rps_flow_cnt=8192`、`rps_sock_flow_entries=65535`、`rps_cpus`/`xps_cpus` 全核掩码）——这部分与 `qca-nss-pbuf.init` 设置的 `dev.nss.rps.hash_bitmap` 是配套的。
建议：删除自有 `smp_affinity`，改用上游两个脚本（或只在上游基础上补差异），并按第 4 节命令核对 `/proc/interrupts` 实际名字后再决定是否额外 pin。

**A6. 复核 `packet_steering` 取值（上游取向相反）**
上游 qualcommax 明确在首启脚本里设 `network.globals.packet_steering='0'`；本项目的 `991` 设 `'1'`，且因为 `99*.sh` 清理把上游脚本删掉了，所以 `'1'` 会真正生效。鉴于 NSS offload 已经接管转发路径、`qca-nss-pbuf.init` 又设了 RPS hash bitmap，软件 packet steering 是否反而增加负载需要 A/B；两种取值都要给出理由，不要继承“越多越好”的直觉。

**A7. 复核 NSS 特征包裁剪的连带影响**
`remove_something_nss_kmod()` 删掉了 `match`/`mirror`/`map-t`/`gre`/`eogremgr`/`tun6rd`/`tunipip6`/`vxlanmgr`/`macsec` 共 9 个包。由 P18，Wi-Fi offload 不会被强制关闭（OR 判定 + netlink/meshmgr 仍在），但 `NSS_DRV_MATCH_ENABLE` 等被编译掉；ECM/加速路径是否依赖 match 需在设备上核对（`nss_stats`、`/sys/kernel/debug/qca-nss-drv/stats/*`），必要时把 `kmod-qca-nss-drv-match` 加回做对照。


### B 档：有效但需设备实测

**B1. Wi-Fi NSS offload：默认开启，但需确认“运行态真的卸起来了”（最高价值）**
构建侧已证实默认开启（P12：`ATH11K_NSS_SUPPORT` 对 qualcommax `default y` + `nss_offload=1`），所以**“是否启用”不是优化空间，“是否真正生效”才是**：ath11k 的 NSS 补丁系列（`199-002/199-003` 引入 NSS 驱动接口、`207-ath11k-Enable-256_512MB-profiles`、`999-906` 补档、`999-802` 温度节流修正、`999-800` threaded NAPI）与 NSS 固件版本必须匹配；若固件/驱动在启动时协商失败，模块参数仍是 1，但 `qca-nss-pbuf.init` 的 `ath11k_nss_offload_enabled` 判断会继续走下去而实际没有硬件卸载，表现为 CPU 占用高、无线吞吐上不去。
先跑：`logread | grep -iE 'qca-nss-pbuf|nss_offload'`、`cat /sys/module/ath11k/parameters/nss_offload`、`dmesg | grep -i nss`；再与“offload 正常”的基线做吞吐/CPU 对比。若确认 offload 未生效，优先排查 NSS 固件与 `ath11k-firmware-ipq6018-ddwrt` 的版本配套（本项目通过 `update_ath11k_fw()` 从固定 commit 拉取 Makefile，见 `wrt_core/modules/system.sh:159-182`），而不是继续加 UCI 开关。


**B2. pbuf/N2H 分档是否命中预期档位**
上游按 `MemTotal` 分三档（≤256 MB / ≤512 MB / 其它）。亚瑟 AX1800 Pro 512 MB 恰好落在中档（`extra_pbuf_core0=8000000`、`n2h_wifi_pool_buf=16384`），雅典娜 1 GB 落顶档；同时 ath11k 侧被固定为 `ATH11K_MEM_PROFILE_512M`（P14），1 GB 机型在固件内存档上偏保守。注意上游日志明确：`extra_pbuf_core0` **一旦分配就无法再改**，因此开机早期若内存吃紧（本固件同时装了 PassWall/Xray + Nikki/mihomo + aerowrt/sing-box + MosDNS 四套常驻栈）可能导致分配偏小并锁定整个开机周期。
建议：核对 `dev.nss.n2hcfg.*` 实际值；512 MB 机型考虑精简代理栈，保证 offload 缓冲区足额分配。

**B3. ATF：用 `airtime_mode` 而不是 `airtime_fairness`**
建议以 `airtime_mode='1'`（radio 级）做 A/B：混合速率客户端（老旧 2.4G 设备）场景收益明显，纯高速场景可能略降峰值。同时可评估 `airtime_bss_weight` / `airtime_bss_limit`（多 BSS 场景）。ath11k 固件是否完整支持需实测（`iw` / 吞吐对比）。

**B4. `he_twt_required`（TWT）**
radio 级、默认 0，是当前唯一“默认为关且有实际意义”的 HE 开关。对电池类客户端/IoT 可省电，代价是可能增加时延与兼容性风险。建议默认不开，作为可选开关暴露或只对 2.4G 开启并 A/B。

**B5. 802.11r 重做（一次只改一个变量）**
用真实键名：`ieee80211r='1'` + 可选 `mobility_domain`（PSK 场景可省略，自动取 SSID 摘要）+ `ft_over_ds='1'`；**不要**设 `nasid`/`r1_key_holder`（仅在 `ft_psk_generate_local=0` 时才有意义）。关键：这次不要同时动 `ieee80211w`，并用上次“拒连”的具体客户端型号复现/回归，保留 `/var/run/hostapd-*.conf` 与 `logread` 作为判据。若无法复现失败，宁可维持现状（k/v-only）也不要盲开。

**B6. `he_bss_color` 与空间复用**
现在 5G 固定 `he_bss_color='42'`（radio 级，默认 128）。若目标是密集环境 OBSS 空间复用，仅设颜色收益有限，通常还要配 `he_spr_non_srg_obss_pd_max_offset`（>0 时 mac80211 会自动把 `he_spr_sr_control` bit2 置位）。多 AP 场景应保证同频 BSS 颜色互异，固定 42 会让“和邻居撞色”时空间复用失效。

**B7. 2.4G 带宽策略**
亚瑟/雅典娜 2.4G 现在是 `HE40`（信道 1，23 dBm）。2.4G 频段拥挤时 40 MHz 会占满整段频谱，实际吞吐与稳定性常不如 `HE20`（峰值降为约一半）。建议按部署环境二选一，或对 2.4G 采用 ACS + `HE20` 作为默认、40 MHz 作为可选。

**B8. 监管与功率（合规，非性能）**
全机型硬编码 `country='US'`（会同时下发 `country_code` / `ieee80211d=1` / `ieee80211h=1` 并 `iw reg set US`），2.4G 23 dBm、5G 24-25 dBm 已超过国内 2.4G 100 mW EIRP 限制。若固件面向国内用户，建议评估 `CN`/`00` 或提供机型化国家码，避免合规风险与 DFS 行为异常。

**B9. NSS 时钟档位（被忽略的“隐形旋钮”）**
上游 qualcommax 还提供 `/etc/init.d/nss_freq`（START=95，[源码](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/nss_freq)），它读 `config nss_freq settings level`，合法值 `mid|high`，默认 **mid**，然后调用 `/usr/bin/nss_freq <level>`。而 `qca-nss-pbuf.init` 又把 `dev.nss.clock.auto_scale` 关掉（固定时钟）。两者叠加的含义是：**NSS 时钟被固定在 mid 档**。本项目完全未涉及这个开关。对 Wi-Fi/LAN 硬件转发吞吐来说，评估 `nss_freq.settings.level=high` 是值得做的 A/B（代价是功耗与温度，IPQ6000 机型多为无风扇被动散热）。注意：`nss_freq` 的存在与调用已在源码中确认，但 NSS 各档位对 IPQ6000 的实际频率/吞吐影响未在本仓库内验证。

**B10. 雅典娜的 5.2G 4x4（QCN9074）大概率走软件路径（需实测确认）**
由 P19，NSS Wi-Fi offload 的代码只在 AHB 路径；雅典娜 radio2 是 PCIe 的 QCN9074。也就是说：雅典娜用户能吃到 NSS 卸载的只有 IPQ6010 内置的两个 AHB 射频，4x4 HE160 那块卡仍靠 CPU 中断处理——这解释了“为什么高带宽场景 CPU 会先顶住”。验证方式：在 radio2 上跑大流量，对比 `mpstat -P ALL 1` 与 `/sys/kernel/debug/qca-nss-drv/stats/wifili` 是否有计数增长。若确实无卸载，则 4x4 卡的优化重点应转向 IRQ 亲和/中断合并（RPS/XPS，见 A5），而不是调 NSS pbuf。

**B11. 亚瑟（re-ss-01）的 ath11k 固件内存档可调**
`re-cs-02` 的 DTS 里有 `qcom,ath11k-fw-memory-mode = <1>`，`re-ss-01`（亚瑟，512 MB）没有该属性，即走默认档（默认 `num_peers` 更大、占内存更多）。在 512 MB + 64 MiB NSS 预留 + 四套代理常驻的组合下，评估为亚瑟补一个 `qcom,ath11k-fw-memory-mode = <1>`（或用内核 patch 方式）来换内存余量，是有依据的候选；代价是并发客户端/对端数量能力下降，需实测。

**B12. `nss_freq` 之外，还可用 `nss_stats`/debugfs 做归因**
排查无线性能时用 `/sys/kernel/debug/qca-nss-drv/stats/{cpu_load_ubi,wifili,n2h}` 与 `/usr/bin/nss_stats` 判断瓶颈在 NSS、在 CPU、还是在 pbuf；本项目 `additional` 的 `nss_diag.sh` 已经不落地（A3），可以直接用上游自带的 `nss_stats`。

### C 档：不要照抄旧文档

**C1. 关闭/修订 2026-08-21 设计文档与计划。** 其中 `he_dlofdma`、`he_ulofdma`、`he_dl_mumimo`、`he_ul_mumimo`、`he_twt`、`airtime_fairness`、`he_coext` 均为无效选项；`he_su_beamformer` 等三个本来就默认 1，显式写不产生行为变化。若该计划被再次当作“待办”执行，会重演 `b65ca6d` 的问题。建议在文档头部标注“已被 70e75e7 回滚，方案需按本评估改写”，或移入 `docs/archive/`。

**C2. 测试与 CI。** 现有测试的写法是“断言我们想要的字符串”，无法发现“选项上游根本不读”。建议：
- 把 `test_wifi_uci.sh`、`test_custom_settings.sh` 接入 `build_wrt.yml`（aerowrt/theme 已有先例）；
- 增加一个“选项白名单校验”（从固定清单校验键名），或至少在 `992` 内注明每个选项的 hostapd 落点；
- 覆盖全部 10 个机型 profile，而不只是 ax1800-pro / re-cs-02；
- 新增设备端诊断脚本（见第 4 节），把无线实际生效值打印出来。

---

## 4. 建议在真机上先跑的验证命令

```sh
# 1) Wi-Fi NSS offload 与 pbuf/N2H 实际状态（nss_offload 期望为 1；重点看是否真的跑起来了）
cat /sys/module/ath11k/parameters/nss_offload 2>/dev/null
logread | grep -iE 'qca-nss-pbuf|nss_offload|pbuf'; dmesg | grep -i nss | tail -20
for f in /proc/sys/dev/nss/n2hcfg/*; do echo "$f = $(cat $f)"; done
cat /proc/sys/dev/nss/rps/hash_bitmap 2>/dev/null

# 1b) NSS 时钟档位（隐形旋钮，见 B9）
uci show nss_freq 2>/dev/null; cat /proc/sys/dev/nss/clock/* 2>/dev/null; ls /usr/bin/nss_freq 2>/dev/null

# 2) 调速器到底是谁（验证 A4）
for p in /sys/devices/system/cpu/cpufreq/policy*; do
  echo "$p: $(cat $p/scaling_governor) min=$(cat $p/scaling_min_freq) max=$(cat $p/scaling_max_freq)"
done

# 3) IRQ / RPS / XPS 实际状态（验证 A5：NSS offload 下 REO/PPDU 环应当不存在）
grep -E 'reo2host|wbm2host|ppdu-end|edma_|nss_queue|bam_dma' /proc/interrupts
for i in /sys/class/net/eth*/queues/rx-*/rps_cpus; do echo "$i=$(cat $i)"; done 2>/dev/null
cat /proc/sys/net/core/rps_sock_flow_entries 2>/dev/null

# 3b) packet steering 与 NSS 特征编译结果（验证 A6/A7）
uci get network.@globals[0].packet_steering
strings /lib/modules/$(uname -r)/qca-nss-drv.ko 2>/dev/null | grep -iE 'NSS_DRV_(WIFI|MATCH|MIRROR)' | head
ls /sys/kernel/debug/qca-nss-drv/stats/ 2>/dev/null
nss_stats 2>/dev/null | head -30

# 4) 无线配置是否真的落到 hostapd（验证 P1/ATF/HE 选项落点）
uci show wireless | grep -E 'multicast|airtime|he_|twt|bss_color|noscan|qam256'
grep -E 'multicast_to_unicast|airtime_mode|he_bss_color|he_twt' /var/run/hostapd-*.conf
iw dev wlan0 info; iw phy phy0 info | head -40

# 4b) 各射频走的是 AHB 还是 PCIe（验证 B10：雅典娜 4x4 卡是否吃到 NSS 卸载）
ls -l /sys/class/ieee80211/; for p in /sys/class/ieee80211/phy*; do echo "$p -> $(cat $p/device/uevent 2>/dev/null | head -2)"; done

# 5) Go 常驻服务是否真的拿到内存限制（设计文档断言 /etc/environment 生效，待证）
for n in xray sing-box mosdns mihomo; do
  p=$(pidof $n 2>/dev/null | awk '{print $1}'); [ -n "$p" ] && { echo "== $n ($p)"; tr '\0' '\n' < /proc/$p/environ | grep -E 'GOMEMLIMIT|GOGC'; }
done

# 6) 参考基线（改动前后各跑一次）
iwinfo; cat /proc/loadavg; free -m
```

---

## 5. 参考来源

- VIKINGYFY/immortalwrt（构建源）
  - [target/linux/qualcommax/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/Makefile)、[ipq60xx/target.mk](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/ipq60xx/target.mk)、[image/ipq60xx.mk](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/image/ipq60xx.mk)
  - [package/kernel/mac80211/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/Makefile)、[ath.mk](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/ath.mk)、[files/qca-nss-pbuf.init](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/kernel/mac80211/files/qca-nss-pbuf.init)
  - [package/qca-nss/qca-nss-drv/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/qca-nss/qca-nss-drv/Makefile)、[Config.in](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/qca-nss/qca-nss-drv/Config.in)
  - [package/network/config/wifi-scripts/.../mac80211.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/network/config/wifi-scripts/files/lib/netifd/wireless/mac80211.sh)、[hostapd.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/network/config/wifi-scripts/files/lib/netifd/hostapd.sh)
  - [target/linux/qualcommax/base-files/etc/init.d/smp_affinity](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/smp_affinity)、[set-irq-affinity](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/set-irq-affinity)、[nss_freq](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/init.d/nss_freq)
  - [base-files/etc/uci-defaults/15_nss_cleanup.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/uci-defaults/15_nss_cleanup.sh)、[991_set-network.sh](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/target/linux/qualcommax/base-files/etc/uci-defaults/991_set-network.sh)
  - [package/emortal/cpufreq/Makefile](https://raw.githubusercontent.com/VIKINGYFY/immortalwrt/main/package/emortal/cpufreq/Makefile)
  - ath11k NSS patch 系列：`package/kernel/mac80211/patches/nss/ath11k/`（`199-002`、`199-003`、`207-ath11k-Enable-256_512MB-profiles`、`999-906`、`999-802`、`999-800`）+ `patches/nss/subsys/`（`300` mesh、`235-001` AP_VLAN）
  - 预留内存与 DT：`patches-6.18/0103-arm64-dts-ipq6018-add-reserved-memory-nodes.patch`、`dts/ipq6010-re-cs-02.dts`
- 本仓库：`wrt_core/patches/992_set-wifi-uci.sh`、`wrt_core/patches/991_custom_settings`、`wrt_core/patches/smp_affinity`、`wrt_core/modules/system.sh`、`wrt_core/modules/packages.sh`、`wrt_core/deconfig/nss.config`、`wrt_core/deconfig/jdcloud_ipq60xx_immwrt.config`、`wrt_core/patches/tests/`
- 提交历史：`b65ca6d`（引入无效 HE/11r 配置）、`70e75e7`（回滚并修拒连）、`dd74f31`（BSS color）、`de9234d`（GB 档 / NSS qdisc / QCN9074 noscan / performance 调速器）

> 说明：本评估中“选项是否被上游读取”“NSS offload 开关链路”“IRQ/RPS 脚本职责”均以上游源码为准（未依赖论坛/百科）；仍标注“未验证”的条目：ath11k 固件对 ATF/`he_twt_required` 的完整支持、`he_bss_color` 取值范围、ipq60xx 运行态 `/proc/interrupts` 实际名字、`packet_steering` 取值优劣、删除 `kmod-qca-nss-drv-match` 对 ECM/加速路径的运行时影响、Go 环境变量是否到达 procd 服务——这些都需要在真机或构建产物上确认。
