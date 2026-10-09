#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/tmp/sysinfo" "$TMP_DIR/bin" "$TMP_DIR/etc/init.d"

# 模拟 uci 命令
cat <<'EOF' > "$TMP_DIR/bin/uci"
#!/bin/bash
if [ "$1" = "get" ]; then
    echo "none"
    exit 0
fi
if [ "$1" = "-q" ] && [ "$2" = "batch" ]; then
    cat >> "$UCI_OUT"
    exit 0
fi
if [ "$1" = "-q" ] && [ "$2" = "set" ]; then
    echo "set $3" >> "$UCI_OUT"
    exit 0
fi
if [ "$1" = "commit" ]; then
    exit 0
fi
exit 0
EOF
chmod +x "$TMP_DIR/bin/uci"

# 模拟 network restart
cat <<'EOF' > "$TMP_DIR/etc/init.d/network"
#!/bin/bash
exit 0
EOF
chmod +x "$TMP_DIR/etc/init.d/network"

export PATH="$TMP_DIR/bin:$PATH"
export UCI_OUT="$TMP_DIR/uci_out.txt"

# 1. 测试 AX1800 Pro / Arthur
echo "jdcloud,ax1800-pro" > "$TMP_DIR/tmp/sysinfo/board_name"
sed "s#/tmp/sysinfo/board_name#$TMP_DIR/tmp/sysinfo/board_name#g; s#/etc/init.d/network#$TMP_DIR/etc/init.d/network#g" wrt_core/patches/992_set-wifi-uci.sh > "$TMP_DIR/test_run.sh"

> "$UCI_OUT"
bash "$TMP_DIR/test_run.sh"

echo "=== 检查 AX1800 Pro UCI 输出 ==="
grep -q "set wireless.radio0.channel=\"149\"" "$UCI_OUT" || { echo "FAIL: radio0 channel 错误"; exit 1; }
grep -q "set wireless.radio1.channel=\"1\"" "$UCI_OUT" || { echo "FAIL: radio1 channel 错误"; exit 1; }
grep -q "set wireless.radio0.mu_beamformer='1'" "$UCI_OUT" || { echo "FAIL: 缺少 mu_beamformer"; exit 1; }
grep -q "set wireless.default_radio0.ieee80211k='1'" "$UCI_OUT" || { echo "FAIL: 缺少 ieee80211k"; exit 1; }
grep -q "set wireless.default_radio0.bss_transition='1'" "$UCI_OUT" || { echo "FAIL: 缺少 bss_transition"; exit 1; }
grep -q "set wireless.default_radio0.ieee80211w='0'" "$UCI_OUT" || { echo "FAIL: ieee80211w 应为 0 保证全设备兼容"; exit 1; }
grep -q "set wireless.radio1.noscan='1'" "$UCI_OUT" || { echo "FAIL: 缺少 2.4G noscan"; exit 1; }

# Wi-Fi 6 空口公平调度 (ATF).
# 层级是关键: airtime_mode 属 wifi-device(radio) 段, 写在 wifi-iface 段 hostapd 不会读
# (见 files-ucode/usr/share/ucode/wifi/hostapd.uc 的 device 级 generate()).
grep -q "set wireless.radio0.airtime_mode=" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 airtime_mode (ATF, 必须写在 wifi-device 段)"; exit 1; }
grep -q "set wireless.radio1.airtime_mode=" "$UCI_OUT" || { echo "FAIL: 缺少 radio1 airtime_mode (ATF, 必须写在 wifi-device 段)"; exit 1; }
! grep -q "set wireless.default_radio0.airtime_mode=" "$UCI_OUT" || { echo "FAIL: airtime_mode 写在 wifi-iface 段不会生效"; exit 1; }
! grep -q "set wireless.default_radio1.airtime_mode=" "$UCI_OUT" || { echo "FAIL: airtime_mode 写在 wifi-iface 段不会生效"; exit 1; }
# 配套的 BSS 权重属 wifi-iface 段, 由 ap.uc 读取
grep -q "set wireless.default_radio0.airtime_bss_weight='1'" "$UCI_OUT" || { echo "FAIL: 缺少 airtime_bss_weight=1 (ATF BSS 权重, wifi-iface 段)"; exit 1; }
grep -q "set wireless.default_radio1.airtime_bss_weight='1'" "$UCI_OUT" || { echo "FAIL: 缺少 2.4G airtime_bss_weight=1"; exit 1; }

# Wi-Fi 6 (HE) 显式波束成形与目标唤醒时间 (TWT)
grep -q "set wireless.radio0.he_su_beamformer='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_su_beamformer"; exit 1; }
grep -q "set wireless.radio0.he_su_beamformee='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_su_beamformee"; exit 1; }
grep -q "set wireless.radio0.he_mu_beamformer='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_mu_beamformer"; exit 1; }
grep -q "set wireless.radio0.he_twt_responder='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_twt_responder"; exit 1; }

# 5G BSS Coloring 与空间复用 (必须包含 he_bss_color_enabled='1' 触发 hostapd.uc 写入, 包含 he_spr_non_srg_obss_pd_max_offset 激活 SR 控制字)
grep -q "set wireless.radio0.he_bss_color='42'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_bss_color"; exit 1; }
grep -q "set wireless.radio0.he_bss_color_enabled='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_bss_color_enabled (必要开关)"; exit 1; }
grep -q "set wireless.radio0.he_spr_psr_enabled='1'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_spr_psr_enabled"; exit 1; }
grep -q "set wireless.radio0.he_spr_non_srg_obss_pd_max_offset='20'" "$UCI_OUT" || { echo "FAIL: 缺少 radio0 he_spr_non_srg_obss_pd_max_offset"; exit 1; }

# 组播转单播真实键名: 必须是对齐 hostapd.sh / hostapd.uc 的 multicast_to_unicast_all
grep -q "set wireless.default_radio0.multicast_to_unicast_all='1'" "$UCI_OUT" || { echo "FAIL: 缺少 multicast_to_unicast_all='1'"; exit 1; }
! grep -q "set wireless.default_radio0.multicast_to_unicast='1'" "$UCI_OUT" || { echo "FAIL: 包含无效键名 multicast_to_unicast"; exit 1; }

# 2.4G 带宽策略: AX1800 Pro 2.4G 应锁定 HE20 避免 40MHz 频宽争抢丢包
grep -q "set wireless.radio1.htmode=\"HE20\"" "$UCI_OUT" || { echo "FAIL: AX1800 Pro 2.4G 应为 HE20"; exit 1; }

# 802.11k/v 漫游辅助链补全
grep -q "set wireless.default_radio0.rrm_neighbor_report='1'" "$UCI_OUT" || { echo "FAIL: 缺少 rrm_neighbor_report"; exit 1; }
grep -q "set wireless.default_radio0.rrm_beacon_report='1'" "$UCI_OUT" || { echo "FAIL: 缺少 rrm_beacon_report"; exit 1; }
grep -q "set wireless.default_radio0.wnm_sleep_mode='1'" "$UCI_OUT" || { echo "FAIL: 缺少 wnm_sleep_mode"; exit 1; }

# 确保移除了导致连接拒绝或 hostapd 语法报错的无效参数
! grep -q "ieee80211r='1'" "$UCI_OUT" || { echo "FAIL: 包含导致客户端拒绝连接的 ieee80211r"; exit 1; }
! grep -q "he_dlofdma='1'" "$UCI_OUT" || { echo "FAIL: 包含无效 UCI 选项 he_dlofdma"; exit 1; }
# qam256 不在 wireless.wifi-device.json schema 中, ath11k 也不消费该 UCI 键,
# 写入只会留下永不生效的死配置并误导排查.
! grep -q "qam256" "$UCI_OUT" || { echo "FAIL: 包含 schema 不存在的无效选项 qam256"; exit 1; }

# 2. 测试 AX6600 Athena (RE-CS-02 三频)
echo "jdcloud,re-cs-02" > "$TMP_DIR/tmp/sysinfo/board_name"
> "$UCI_OUT"
bash "$TMP_DIR/test_run.sh"

echo "=== 检查 AX6600 Athena 三频输出 ==="
grep -q "set wireless.radio0.channel=\"149\"" "$UCI_OUT" || { echo "FAIL: Athena radio0 5.8G 应分配高频信道 (如 149)"; exit 1; }
grep -q "set wireless.radio0.htmode=\"HE80\"" "$UCI_OUT" || { echo "FAIL: Athena radio0 5.8G 应配置 HE80 (2x2 1201Mbps)"; exit 1; }
grep -q "set wireless.radio1.channel=\"1\"" "$UCI_OUT" || { echo "FAIL: Athena radio1 2.4G 应分配信道 1"; exit 1; }
grep -q "set wireless.radio1.htmode=\"HE20\"" "$UCI_OUT" || { echo "FAIL: Athena radio1 2.4G 应配置 HE20"; exit 1; }
grep -q "set wireless.radio2.channel=\"44\"" "$UCI_OUT" || { echo "FAIL: Athena radio2 5.2G 应分配低频信道 (如 44)"; exit 1; }
grep -q "set wireless.radio2.htmode=\"HE160\"" "$UCI_OUT" || { echo "FAIL: Athena radio2 5.2G 应开启 HE160 (4x4 4804Mbps)"; exit 1; }
# AX6600 radio2 QCN9074 4x4 规格
grep -q "set wireless.radio2.beamformer_antennas='4'" "$UCI_OUT" || { echo "FAIL: Athena radio2 应声明 beamformer_antennas=4"; exit 1; }
grep -q "set wireless.radio2.beamformee_antennas='4'" "$UCI_OUT" || { echo "FAIL: Athena radio2 应声明 beamformee_antennas=4"; exit 1; }

# 3. 测试 Wi-Fi 5 设备 (如歌华链 / R619AC: HT40/VHT80, 不应开启 Wi-Fi 6 专有特性)
echo "gehua,ghl-r-001" > "$TMP_DIR/tmp/sysinfo/board_name"
> "$UCI_OUT"
bash "$TMP_DIR/test_run.sh"

echo "=== 检查 Wi-Fi 5 设备输出 (应跳过 ATF 及 HE 专有特性) ==="
! grep -q "airtime_mode" "$UCI_OUT" || { echo "FAIL: Wi-Fi 5 设备不应开启 airtime_mode 避免 hostapd 解析报错"; exit 1; }
! grep -q "airtime_bss_weight" "$UCI_OUT" || { echo "FAIL: Wi-Fi 5 设备不应开启 airtime_bss_weight"; exit 1; }
! grep -q "he_bss_color_enabled" "$UCI_OUT" || { echo "FAIL: Wi-Fi 5 设备不应配置 he_bss_color_enabled"; exit 1; }
! grep -q "he_su_beamformer" "$UCI_OUT" || { echo "FAIL: Wi-Fi 5 设备不应配置 he_su_beamformer"; exit 1; }
! grep -q "he_twt_responder" "$UCI_OUT" || { echo "FAIL: Wi-Fi 5 设备不应配置 he_twt_responder"; exit 1; }
grep -q "HT20" "$UCI_OUT" || { echo "FAIL: 歌华链 2.4G 应配置 HT20 根治断流"; exit 1; }
grep -q "VHT80" "$UCI_OUT" || { echo "FAIL: 歌华链 5G 应配置 VHT80"; exit 1; }

echo "PASS: test_wifi_uci (全机型信道与兼容性测试通过)"
