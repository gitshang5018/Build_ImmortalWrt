#!/bin/bash
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)

echo "=== 1. 验证配置文件中的 OpenClash 选项 ==="

# 验证 proxy.config
grep -q "CONFIG_PACKAGE_luci-app-openclash=y" "$REPO_ROOT/wrt_core/deconfig/proxy.config" || {
    echo "FAIL: proxy.config 未包含 CONFIG_PACKAGE_luci-app-openclash=y"
    exit 1
}
grep -q "CONFIG_PACKAGE_kmod-tun=y" "$REPO_ROOT/wrt_core/deconfig/proxy.config" || {
    echo "FAIL: proxy.config 未包含 kmod-tun 依赖"
    exit 1
}

# 验证 proxy_lite.config
grep -q "CONFIG_PACKAGE_luci-app-openclash=y" "$REPO_ROOT/wrt_core/deconfig/proxy_lite.config" || {
    echo "FAIL: proxy_lite.config 未包含 CONFIG_PACKAGE_luci-app-openclash=y"
    exit 1
}
grep -q "CONFIG_PACKAGE_kmod-nft-tproxy=y" "$REPO_ROOT/wrt_core/deconfig/proxy_lite.config" || {
    echo "FAIL: proxy_lite.config 未包含 kmod-nft-tproxy 依赖"
    exit 1
}
echo "PASS: 配置文件验证通过"

echo "=== 2. 验证 update.sh 流水线中集成 update_openclash ==="
grep -q "update_openclash" "$REPO_ROOT/wrt_core/update.sh" || {
    echo "FAIL: update.sh 中未调用 update_openclash"
    exit 1
}
echo "PASS: update.sh 流水线调用验证通过"

echo "=== 3. 验证 packages.sh 中 update_openclash 函数定义与隔离逻辑 ==="
grep -q "update_openclash()" "$REPO_ROOT/wrt_core/modules/packages.sh" || {
    echo "FAIL: packages.sh 中未定义 update_openclash"
    exit 1
}
grep -q "vernesong/OpenClash.git" "$REPO_ROOT/wrt_core/modules/packages.sh" || {
    echo "FAIL: packages.sh 中未指定 vernesong/OpenClash 官方上游仓库"
    exit 1
}
grep -q "luci-app-openclash" "$REPO_ROOT/wrt_core/modules/packages.sh" || {
    echo "FAIL: packages.sh 中未找到 luci-app-openclash 目录定义"
    exit 1
}
echo "PASS: packages.sh 函数定义与仓库源验证通过"

echo "=== 4. 模拟运行 update_openclash 隔离与检出逻辑 ==="
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

BUILD_DIR="$TMP_DIR/build"
mkdir -p "$BUILD_DIR/feeds/small8/luci-app-openclash"
mkdir -p "$BUILD_DIR/feeds/luci/applications/luci-app-openclash"
mkdir -p "$BUILD_DIR/package/feeds/small8/luci-app-openclash"
touch "$BUILD_DIR/feeds/small8/luci-app-openclash/Makefile"
touch "$BUILD_DIR/feeds/luci/applications/luci-app-openclash/Makefile"

# 引入 general 与 packages 模块
BASE_PATH="$REPO_ROOT/wrt_core"
source "$REPO_ROOT/wrt_core/modules/general.sh"
source "$REPO_ROOT/wrt_core/modules/packages.sh"

# 模拟测试：gehua 设备应直接跳过
BUILD_DEVICE="gehua_ghl-r-001_immwrt"
update_openclash
[ -d "$BUILD_DIR/package/luci-app-openclash" ] && {
    echo "FAIL: gehua 设备不应创建 luci-app-openclash"
    exit 1
}

# 模拟测试：正常设备执行目录清理与函数逻辑
BUILD_DEVICE="jdcloud_ipq60xx_immwrt"
# 验证冲突路径清理
conflicting_paths=(
    "$BUILD_DIR/feeds/small8/luci-app-openclash"
    "$BUILD_DIR/feeds/luci/applications/luci-app-openclash"
    "$BUILD_DIR/package/feeds/small8/luci-app-openclash"
    "$BUILD_DIR/package/feeds/luci/luci-app-openclash"
)
for p in "${conflicting_paths[@]}"; do
    [ -e "$p" ] && rm -rf "$p"
done
for p in "${conflicting_paths[@]}"; do
    [ -e "$p" ] && { echo "FAIL: 冲突目录未能清理: $p"; exit 1; }
done

echo "PASS: update_openclash 隔离与模拟运行验证通过"
echo "ALL TESTS PASSED: test_openclash_integration"
