#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$TMP_DIR/tmp/sysinfo" "$TMP_DIR/bin" "$TMP_DIR/etc/init.d"
echo "jdcloud,ax1800-pro" > "$TMP_DIR/tmp/sysinfo/board_name"

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

# 运行待测脚本 (传入模拟 sysinfo 路径)
sed "s#/tmp/sysinfo/board_name#$TMP_DIR/tmp/sysinfo/board_name#g; s#/etc/init.d/network#$TMP_DIR/etc/init.d/network#g" wrt_core/patches/992_set-wifi-uci.sh > "$TMP_DIR/test_run.sh"

bash "$TMP_DIR/test_run.sh"

echo "=== 检查生成的 UCI 输出 ==="
grep -q "set wireless.radio0.he_dlofdma='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_dlofdma"; exit 1; }
grep -q "set wireless.radio0.he_ulofdma='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_ulofdma"; exit 1; }
grep -q "set wireless.radio0.he_dl_mumimo='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_dl_mumimo"; exit 1; }
grep -q "set wireless.radio0.he_ul_mumimo='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_ul_mumimo"; exit 1; }
grep -q "set wireless.radio0.he_su_beamformer='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_su_beamformer"; exit 1; }
grep -q "set wireless.radio0.he_twt='1'" "$UCI_OUT" || { echo "FAIL: 缺少 he_twt"; exit 1; }
grep -q "set wireless.radio0.airtime_fairness='1'" "$UCI_OUT" || { echo "FAIL: 缺少 airtime_fairness"; exit 1; }
grep -q "set wireless.default_radio0.mobility_domain='e4a1'" "$UCI_OUT" || { echo "FAIL: 缺少 mobility_domain"; exit 1; }
grep -q "set wireless.default_radio0.nasid='radio0'" "$UCI_OUT" || { echo "FAIL: 缺少 nasid"; exit 1; }
grep -q "set wireless.radio1.noscan='1'" "$UCI_OUT" || { echo "FAIL: 缺少 2.4G noscan"; exit 1; }

echo "PASS: test_wifi_uci"
