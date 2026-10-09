#!/bin/sh

board_name=$(cat /tmp/sysinfo/board_name)

configure_wifi() {
	local radio=$1
	local channel=$2
	local htmode=$3
	local txpower=$4
	local ssid=$5
	local key=$6
	local encryption=${7:-"psk2+ccmp"} # 如果为空则默认为 psk2+ccmp
	local now_encryption=$(uci get wireless.default_radio${radio}.encryption 2>/dev/null)
	if [ -n "$now_encryption" ] && [ "$now_encryption" != "none" ]; then
		return 0
	fi

	local is_2g=0
	if [ "$channel" -le 14 ] 2>/dev/null; then
		is_2g=1
	fi

	uci -q batch <<EOF
set wireless.radio${radio}.channel="${channel}"
set wireless.radio${radio}.htmode="${htmode}"
set wireless.radio${radio}.country='US'
set wireless.radio${radio}.txpower="${txpower}"
set wireless.radio${radio}.cell_density='0'
set wireless.radio${radio}.disabled='0'
set wireless.radio${radio}.mu_beamformer='1'

# 基础接口配置
set wireless.default_radio${radio}.ssid="${ssid}"
set wireless.default_radio${radio}.encryption="${encryption}"
set wireless.default_radio${radio}.key="${key}"

# 802.11k/v 漫游辅助 (兼顾快速漫游与全客户端兼容，避免 11r 导致的连接拒绝)
set wireless.default_radio${radio}.ieee80211k='1'
set wireless.default_radio${radio}.bss_transition='1'
set wireless.default_radio${radio}.rrm_neighbor_report='1'
set wireless.default_radio${radio}.rrm_beacon_report='1'
set wireless.default_radio${radio}.wnm_sleep_mode='1'

# 管理帧保护与稳定防踢、组播转单播消除丢包 (对齐 hostapd.sh / hostapd.uc 的 multicast_to_unicast_all)
# ieee80211w 设置为 0 确保旧设备与智能家居设备能够正常连接
set wireless.default_radio${radio}.ieee80211w='0'
set wireless.default_radio${radio}.disassoc_low_ack='0'
set wireless.default_radio${radio}.multicast_to_unicast_all='1'
EOF

	# ATF (Airtime Fairness) 空口公平调度与 Wi-Fi 6 (HE/EHT) 专属射频调度:
	# 避免多设备争抢时慢速终端拖慢整体 Wi-Fi 6 协商吞吐.
	# 层级规范: airtime_mode/he_* 属 wifi-device(radio) 段 (由 hostapd.uc device 级读取),
	# 配套的 airtime_bss_weight 属 wifi-iface 段 (由 ap.uc 读取).
	# 对 Wi-Fi 5 / 4 (HT/VHT) 不强开, 避免基础版 hostapd 解析未知配置项报错.
	if echo "$htmode" | grep -qE '^(HE|EHT)'; then
		uci -q batch <<EOF
set wireless.radio${radio}.airtime_mode='2'
set wireless.default_radio${radio}.airtime_bss_weight='1'
set wireless.radio${radio}.he_su_beamformer='1'
set wireless.radio${radio}.he_su_beamformee='1'
set wireless.radio${radio}.he_mu_beamformer='1'
set wireless.radio${radio}.he_twt_responder='1'
EOF
	fi

	# 2.4G 防降速: 锁定 noscan 跳过启动信道扫描, 减少切换延迟
	if [ "$is_2g" -eq 1 ]; then
		uci -q batch <<EOF
set wireless.radio${radio}.noscan='1'
EOF
        fi
    # 5G BSS Coloring 与空间复用：密集环境下降低邻居 AP 同频干扰 (OBSS_PD)
    # 必须显式开启 he_bss_color_enabled='1'，并配置 he_spr_non_srg_obss_pd_max_offset 激活 SR 控制字
    if [ "$is_2g" -eq 0 ] && echo "$htmode" | grep -q "^HE"; then
            uci -q batch <<EOF
set wireless.radio${radio}.he_bss_color='42'
set wireless.radio${radio}.he_bss_color_enabled='1'
set wireless.radio${radio}.he_spr_psr_enabled='1'
set wireless.radio${radio}.he_spr_non_srg_obss_pd_max_offset='20'
EOF
    fi
}

jdc_ax1800_pro_wifi_cfg() {
	configure_wifi 0 149 HE80 24 'JDC_AX1800PRO_5G' '12345678'
	# 2.4G 锁定 HE20 集中频谱能量并提升抗干扰能力，根治 40MHz 邻频退避与智能家居丢包
	configure_wifi 1 1 HE20 23 'JDC_AX1800PRO' '12345678'
}

jdc_ax6600_wifi_cfg() {
	# Radio0: IPQ6010 内置 QCN5052 5.8GHz 频段 (2x2 80MHz 1201Mbps, 149~165 信道)
	configure_wifi 0 149 HE80 24 'JDC_AX6600_5G1' '12345678'
	# Radio1: IPQ6010 内置 QCN5022 2.4GHz 频段 (2x2 287Mbps, 锁定 HE20 保证穿墙与 IoT 稳定)
	configure_wifi 1 1 HE20 23 'JDC_AX6600' '12345678'
	# Radio2: QCN9074 外挂 5.2GHz 电竞高频宽独立网卡 (4x4 160MHz 4804Mbps, 36~64 信道)
	configure_wifi 2 44 HE160 25 'JDC_AX6600_5G2' '12345678'
    # QCN9074 5.2GHz 固定信道 44 (非 DFS)，跳过信道扫描降低延迟并声明 4x4 物理天线波束成形规格
    uci -q batch <<EOF
set wireless.radio2.noscan='1'
set wireless.radio2.beamformer_antennas='4'
set wireless.radio2.beamformee_antennas='4'
EOF
}

redmi_ax5_wifi_cfg() {
	configure_wifi 0 149 HE80 20 'Redmi_AX5_5G' '12345678'
	configure_wifi 1 1 HE20 20 'Redmi_AX5' '12345678'
}

aliyun_ap8220_wifi_cfg() {
	configure_wifi 0 149 HE80 26 'Aliyun_AP8220_5G' '12345678'
	configure_wifi 1 1 HE20 23 'Aliyun_AP8220' '12345678'
}

cmcc_rax3000m_wifi_cfg() {
	configure_wifi 0 1 HE20 23 'CMCC_RAX3000M' '12345678'
	configure_wifi 1 44 HE160 25 'CMCC_RAX3000M_5G' '12345678'
}

redmi_ax6_wifi_cfg() {
	configure_wifi 0 149 HE80 22 'Redmi_AX6_5G' '12345678'
	configure_wifi 1 1 HE20 21 'Redmi_AX6' '12345678'
}

qihoo_360v6_wifi_cfg() {
	configure_wifi 0 1 HE80 20 'Qihoo_360V6' '12345678'
	configure_wifi 1 149 HE20 20 'Qihoo_360V6_5G' '12345678'
}

linksys_mx4x00_wifi_cfg() {
	configure_wifi 0 1 HE20 22 'Linksys_MX4X00' '12345678'
	configure_wifi 1 149 HE80 21 'Linksys_MX4X00_5G1' '12345678'
	configure_wifi 2 44 HE80 21 'Linksys_MX4X00_5G2' '12345678'
}

gemtek_w1701k_wifi_cfg() {
	configure_wifi 0 1 EHT20 23 'Gemtek_W1701K' '12345678'
	configure_wifi 1 44 EHT160 23 'Gemtek_W1701K_5G' '12345678'
	configure_wifi 2 1 EHT320 23 'Gemtek_W1701K_6G' '12345678' 'sae'
    uci set wireless.radio2.disabled='1'
}

link_nn6000_wifi_cfg() {
    configure_wifi 0 149 HE80 19 'Link_NN6000_5G' '12345678'
	configure_wifi 1 1 HT20 19 'Link_NN6000' '12345678'
}

p2w_r619ac_wifi_cfg() {
	local r0_band
	r0_band=$(uci get wireless.radio0.band 2>/dev/null || uci get wireless.radio0.hwmode 2>/dev/null)
	if [ "$r0_band" = "5g" ] || [ "$r0_band" = "11a" ]; then
		configure_wifi 0 149 VHT80 23 'P2W_R619AC_5G' '12345678'
		configure_wifi 1 6 HT40 22 'P2W_R619AC' '12345678'
	else
		configure_wifi 0 6 HT40 22 'P2W_R619AC' '12345678'
		configure_wifi 1 149 VHT80 23 'P2W_R619AC_5G' '12345678'
	fi
}

gehua_ghl_r001_wifi_cfg() {
	local r0_band
	r0_band=$(uci get wireless.radio0.band 2>/dev/null || uci get wireless.radio0.hwmode 2>/dev/null)
	# 2.4G MT7603E 锁定 HT20 (20MHz) + 19dBm 彻底根治 40MHz 频宽频繁退避、丢包与智能家居断流问题
	if [ "$r0_band" = "5g" ] || [ "$r0_band" = "11a" ]; then
		configure_wifi 0 149 VHT80 20 'Gehua_GHL_5G' '12345678'
		configure_wifi 1 1 HT20 19 'Gehua_GHL' '12345678'
	else
		configure_wifi 0 1 HT20 19 'Gehua_GHL' '12345678'
		configure_wifi 1 149 VHT80 20 'Gehua_GHL_5G' '12345678'
	fi
}

case "${board_name}" in
jdcloud,ax1800-pro | \
	jdcloud,re-ss-01)
	jdc_ax1800_pro_wifi_cfg
	;;
jdcloud,ax6600 | \
	jdcloud,re-cs-02)
	jdc_ax6600_wifi_cfg
	;;
redmi,ax5 | \
	redmi,ax5-jdcloud)
	redmi_ax5_wifi_cfg
	;;
aliyun,ap8220)
	aliyun_ap8220_wifi_cfg
	;;
cmcc,rax3000m)
	cmcc_rax3000m_wifi_cfg
	;;
redmi,ax6 | \
	redmi,ax6-stock)
	redmi_ax6_wifi_cfg
	;;
qihoo,360v6)
	qihoo_360v6_wifi_cfg
	;;
linksys,mx4200v1 | \
	linksys,mx4200v2 | \
	linksys,mx4300)
	linksys_mx4x00_wifi_cfg
	;;
gemtek,w1701k)
	gemtek_w1701k_wifi_cfg
	;;
link,nn6000-v2)
    link_nn6000_wifi_cfg
    ;;
p2w,r619ac | \
p2w,r619ac-128m)
	p2w_r619ac_wifi_cfg
	;;
gehua,ghl-r-001)
	gehua_ghl_r001_wifi_cfg
	;;
*)
	exit 0
	;;
esac

uci commit wireless
/etc/init.d/network restart
