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

# 确保移除了导致连接拒绝或 hostapd 语法报错的无效参数
! grep -q "ieee80211r='1'" "$UCI_OUT" || { echo "FAIL: 包含导致客户端拒绝连接的 ieee80211r"; exit 1; }
! grep -q "he_dlofdma='1'" "$UCI_OUT" || { echo "FAIL: 包含无效 UCI 选项 he_dlofdma"; exit 1; }

# 2. 测试 AX6600 Athena (RE-CS-02 三频)
echo "jdcloud,re-cs-02" > "$TMP_DIR/tmp/sysinfo/board_name"
> "$UCI_OUT"
bash "$TMP_DIR/test_run.sh"

echo "=== 检查 AX6600 Athena 三频输出 ==="
grep -q "set wireless.radio0.channel=\"44\"" "$UCI_OUT" || { echo "FAIL: Athena radio0 5.2G 应分配低频信道 (如 44)"; exit 1; }
grep -q "set wireless.radio1.channel=\"1\"" "$UCI_OUT" || { echo "FAIL: Athena radio1 2.4G 应分配信道 1"; exit 1; }
grep -q "set wireless.radio2.channel=\"149\"" "$UCI_OUT" || { echo "FAIL: Athena radio2 5.8G 应分配高频信道 (如 149)"; exit 1; }

echo "PASS: test_wifi_uci (全机型信道与兼容性测试通过)"
