#!/bin/bash
set -e

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

echo "=== 测试 1: 小内存设备 (< 300MB, 如歌华链 128MB) ==="
mkdir -p "$TMP_DIR/proc1" "$TMP_DIR/etc1"
echo "MemTotal:         128000 kB" > "$TMP_DIR/proc1/meminfo"
touch "$TMP_DIR/etc1/sysctl.conf"
touch "$TMP_DIR/etc1/profile"

MEMINFO_FILE="$TMP_DIR/proc1/meminfo" SYSCTL_CONF="$TMP_DIR/etc1/sysctl.conf" TARGET_ETC="$TMP_DIR/etc1" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 32768" "$TMP_DIR/etc1/sysctl.conf" || { echo "FAIL: 小内存 conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=48MiB" "$TMP_DIR/etc1/environment" || { echo "FAIL: 小内存 GOMEMLIMIT 不正确"; exit 1; }
grep -q "net.core.netdev_budget = 600" "$TMP_DIR/etc1/sysctl.conf" || { echo "FAIL: 小内存 netdev_budget 不正确"; exit 1; }

echo "=== 测试 2: 中内存设备 (300MB~1.5GB, 如京东云 1024MB) ==="
mkdir -p "$TMP_DIR/proc2" "$TMP_DIR/etc2"
echo "MemTotal:        1048576 kB" > "$TMP_DIR/proc2/meminfo"
touch "$TMP_DIR/etc2/sysctl.conf"
touch "$TMP_DIR/etc2/profile"

MEMINFO_FILE="$TMP_DIR/proc2/meminfo" SYSCTL_CONF="$TMP_DIR/etc2/sysctl.conf" TARGET_ETC="$TMP_DIR/etc2" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 65535" "$TMP_DIR/etc2/sysctl.conf" || { echo "FAIL: 中内存 conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=128MiB" "$TMP_DIR/etc2/environment" || { echo "FAIL: 中内存 GOMEMLIMIT 不正确"; exit 1; }

echo "=== 测试 3: 大内存设备 (> 1.5GB, 如 X86 4096MB) ==="
mkdir -p "$TMP_DIR/proc3" "$TMP_DIR/etc3"
echo "MemTotal:        4194304 kB" > "$TMP_DIR/proc3/meminfo"
touch "$TMP_DIR/etc3/sysctl.conf"
touch "$TMP_DIR/etc3/profile"

MEMINFO_FILE="$TMP_DIR/proc3/meminfo" SYSCTL_CONF="$TMP_DIR/etc3/sysctl.conf" TARGET_ETC="$TMP_DIR/etc3" bash wrt_core/patches/991_custom_settings

grep -q "net.netfilter.nf_conntrack_max = 262144" "$TMP_DIR/etc3/sysctl.conf" || { echo "FAIL: 大内存 conntrack_max 不正确"; exit 1; }
grep -q "GOMEMLIMIT=512MiB" "$TMP_DIR/etc3/environment" || { echo "FAIL: 大内存 GOMEMLIMIT 不正确"; exit 1; }

echo "PASS: test_custom_settings"
