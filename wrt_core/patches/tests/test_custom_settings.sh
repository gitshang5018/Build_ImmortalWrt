#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# uci 桩: 记录 set 调用, get 恒返回 none (模拟未配置)
mkdir -p "$TMP_DIR/bin"
cat <<'EOF' > "$TMP_DIR/uci_stub"
#!/bin/bash
if [ "$1" = "get" ]; then
    echo "none"
    exit 0
fi
if [ "$1" = "-q" ] && [ "$2" = "set" ]; then
    echo "set $3" >> "$UCI_OUT"
    exit 0
fi
if [ "$1" = "-q" ] && [ "$2" = "batch" ]; then
    cat >> "$UCI_OUT"
    exit 0
fi
if [ "$1" = "commit" ]; then
    exit 0
fi
if [ "$1" = "show" ]; then
    exit 0
fi
exit 0
EOF
chmod +x "$TMP_DIR/uci_stub"
export UCI_OUT="$TMP_DIR/uci_out.txt"
mkdir -p "$TMP_DIR/sys/module"

echo "=== 测试 1: 小内存设备 (< 300MB, 如歌华链 128MB) ==="
mkdir -p "$TMP_DIR/proc1" "$TMP_DIR/etc1"
echo "MemTotal:         128000 kB" > "$TMP_DIR/proc1/meminfo"
touch "$TMP_DIR/etc1/sysctl.conf"
touch "$TMP_DIR/etc1/profile"

MEMINFO_FILE="$TMP_DIR/proc1/meminfo" SYSCTL_CONF="$TMP_DIR/etc1/sysctl.conf" TARGET_ETC="$TMP_DIR/etc1" "$BASH" wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 32768" "$TMP_DIR/etc1/sysctl.conf" || { echo "FAIL: 小内存 conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=48MiB" "$TMP_DIR/etc1/environment" || { echo "FAIL: 小内存 GOMEMLIMIT 不正确"; exit 1; }
grep -q "net.core.netdev_budget = 600" "$TMP_DIR/etc1/sysctl.conf" || { echo "FAIL: 小内存 netdev_budget 不正确"; exit 1; }

echo "=== 测试 2: 中内存设备 (300MB~768MB, 如京东云 AX1800 Pro 512MB) ==="
mkdir -p "$TMP_DIR/proc2" "$TMP_DIR/etc2"
echo "MemTotal:         524288 kB" > "$TMP_DIR/proc2/meminfo"
touch "$TMP_DIR/etc2/sysctl.conf"
touch "$TMP_DIR/etc2/profile"

MEMINFO_FILE="$TMP_DIR/proc2/meminfo" SYSCTL_CONF="$TMP_DIR/etc2/sysctl.conf" TARGET_ETC="$TMP_DIR/etc2" "$BASH" wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 65535" "$TMP_DIR/etc2/sysctl.conf" || { echo "FAIL: 中内存 512MB conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=128MiB" "$TMP_DIR/etc2/environment" || { echo "FAIL: 中内存 512MB GOMEMLIMIT 不正确"; exit 1; }

echo "=== 测试 2.5: 高端档设备 (768MB~1.25GB, 如 AX6600 1024MB) ==="
mkdir -p "$TMP_DIR/proc2_5" "$TMP_DIR/etc2_5"
echo "MemTotal:        1048576 kB" > "$TMP_DIR/proc2_5/meminfo"
touch "$TMP_DIR/etc2_5/sysctl.conf"
touch "$TMP_DIR/etc2_5/profile"

MEMINFO_FILE="$TMP_DIR/proc2_5/meminfo" SYSCTL_CONF="$TMP_DIR/etc2_5/sysctl.conf" TARGET_ETC="$TMP_DIR/etc2_5" "$BASH" wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 131072" "$TMP_DIR/etc2_5/sysctl.conf" || { echo "FAIL: 高端档 1024MB conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=160MiB" "$TMP_DIR/etc2_5/environment" || { echo "FAIL: 高端档 1024MB GOMEMLIMIT 不正确"; exit 1; }

echo "=== 测试 3: 大内存设备 (> 1.5GB, 如 X86 4096MB) ==="
mkdir -p "$TMP_DIR/proc3" "$TMP_DIR/etc3"
echo "MemTotal:        4194304 kB" > "$TMP_DIR/proc3/meminfo"
touch "$TMP_DIR/etc3/sysctl.conf"
touch "$TMP_DIR/etc3/profile"

MEMINFO_FILE="$TMP_DIR/proc3/meminfo" SYSCTL_CONF="$TMP_DIR/etc3/sysctl.conf" TARGET_ETC="$TMP_DIR/etc3" "$BASH" wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 262144" "$TMP_DIR/etc3/sysctl.conf" || { echo "FAIL: 大内存 conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=512MiB" "$TMP_DIR/etc3/environment" || { echo "FAIL: 大内存 GOMEMLIMIT 不正确"; exit 1; }

echo "=== 测试 4: NSS 机型不得强开 packet_steering (与 ECM 的 disable_packet_steering 冲突) ==="
mkdir -p "$TMP_DIR/etc4/init.d" "$TMP_DIR/proc4"
echo "MemTotal:        1048576 kB" > "$TMP_DIR/proc4/meminfo"
touch "$TMP_DIR/etc4/sysctl.conf" "$TMP_DIR/etc4/profile"
touch "$TMP_DIR/etc4/init.d/qca-nss-ecm"
mkdir -p "$TMP_DIR/network"
: > "$TMP_DIR/network/config"

# 让 991 通过 PATH 命中 uci 桩
export PATH="$TMP_DIR/bin:$PATH"
cp "$TMP_DIR/uci_stub" "$TMP_DIR/bin/uci"

# 4a. NSS 固件 (静态存在 qca-nss-ecm init 脚本) -> 必须跳过 packet_steering
: > "$UCI_OUT"
MEMINFO_FILE="$TMP_DIR/proc4/meminfo" SYSCTL_CONF="$TMP_DIR/etc4/sysctl.conf" TARGET_ETC="$TMP_DIR/etc4" \
    SYS_MODULE_DIR="$TMP_DIR/sys/module" NETWORK_CONFIG="$TMP_DIR/network/config" \
    "$BASH" wrt_core/patches/991_custom_settings

grep -q "packet_steering" "$UCI_OUT" && { echo "FAIL: NSS 机型不应把 packet_steering 设为 1"; exit 1; }
grep -q "packet_steering" "$TMP_DIR/etc4/sysctl.conf" && { echo "FAIL: NSS 机型不应通过 sysctl 强开 packet_steering"; exit 1; }

echo "=== 测试 4b: 非 NSS 机型 (ECM 未配置) 保持原有 packet_steering=1 行为 ==="
rm -rf "$TMP_DIR/etc4/init.d/qca-nss-ecm" "$TMP_DIR/sys/module/ecm" "$TMP_DIR/etc4/sysctl.conf"
touch "$TMP_DIR/etc4/sysctl.conf"
: > "$UCI_OUT"

MEMINFO_FILE="$TMP_DIR/proc4/meminfo" SYSCTL_CONF="$TMP_DIR/etc4/sysctl.conf" TARGET_ETC="$TMP_DIR/etc4" \
    SYS_MODULE_DIR="$TMP_DIR/sys/module" NETWORK_CONFIG="$TMP_DIR/network/config" \
    "$BASH" wrt_core/patches/991_custom_settings

grep -q "packet_steering=1" "$UCI_OUT" || { echo "FAIL: 非 NSS 机型应保留 packet_steering=1"; exit 1; }

echo "=== 测试 5: MT7621 (歌华链) 自动开启 PPE 硬件 NAT 流控加速 ==="
mkdir -p "$TMP_DIR/etc5" "$TMP_DIR/proc5" "$TMP_DIR/sysinfo5"
echo "MemTotal:         524288 kB" > "$TMP_DIR/proc5/meminfo"
echo "system type : MediaTek MT7621" > "$TMP_DIR/proc5/cpuinfo"
echo "gehua,ghl-r-001" > "$TMP_DIR/sysinfo5/board_name"
touch "$TMP_DIR/etc5/sysctl.conf" "$TMP_DIR/etc5/profile"
mkdir -p "$TMP_DIR/etc5/config"
touch "$TMP_DIR/etc5/config/firewall"
: > "$UCI_OUT"

MEMINFO_FILE="$TMP_DIR/proc5/meminfo" SYSCTL_CONF="$TMP_DIR/etc5/sysctl.conf" TARGET_ETC="$TMP_DIR/etc5" \
    FIREWALL_CONFIG="$TMP_DIR/etc5/config/firewall" BOARD_NAME_FILE="$TMP_DIR/sysinfo5/board_name" \
    CPUINFO_FILE="$TMP_DIR/proc5/cpuinfo" \
    "$BASH" wrt_core/patches/991_custom_settings

grep -q "flow_offloading=1" "$UCI_OUT" || { echo "FAIL: MT7621 应开启软件 flow_offloading"; exit 1; }
grep -q "flow_offloading_hw=1" "$UCI_OUT" || { echo "FAIL: MT7621 应开启硬件 flow_offloading_hw (PPE)"; exit 1; }

echo "PASS: test_custom_settings"
