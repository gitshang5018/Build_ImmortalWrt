#!/bin/bash
set -e

# 显式指定 /tmp: Git Bash 的默认临时目录是 Windows 风格路径
TMP_DIR=$(mktemp -d /tmp/wb-golang-test.XXXXXX 2>/dev/null) || TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
MODULE="$SCRIPT_DIR/../../modules/packages.sh"

fail() { echo "FAIL: $*" >&2; exit 1; }

# 用空实现替换 general.sh 的日志函数, 让测试输出保持干净
log_info() { :; }
log_warn() { :; }
log_success() { :; }
log_error() { :; }

# 路径必须在 source 之前注入, 模块用 ${VAR:-default} 在加载时求值
export GOLANG_MK_FILE="$TMP_DIR/feeds/packages/lang/golang/golang/Makefile"
export FEEDS_SCAN_DIR="$TMP_DIR/feeds"

source "$MODULE"

mkdir -p "$(dirname "$GOLANG_MK_FILE")"
mkdir -p "$FEEDS_SCAN_DIR/small8/xray-core"

write_go_version() {
	local major="$1" patch="$2"
	{
		echo "GO_VERSION_MAJOR_MINOR:=$major"
		echo "GO_VERSION_PATCH:=$patch"
	} >"$GOLANG_MK_FILE"
}

write_go_mod() {
	local path="$1"
	shift
	mkdir -p "$(dirname "$path")"
	printf '%s\n' "$@" >"$path"
}

expect_pass() {
	local name="$1"
	if (verify_golang_version) >/dev/null 2>&1; then
		echo "  ok: $name"
	else
		fail "$name 应通过但被拒绝"
	fi
}

expect_fail() {
	local name="$1"
	if (verify_golang_version) >/dev/null 2>&1; then
		fail "$name 应被拒绝但通过了"
	else
		echo "  ok: $name"
	fi
}

write_go_version 1.27 3

echo "== 场景 1: 包要求与工具链一致 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.27"
expect_pass "go 1.27 vs 工具链 1.27.3"

echo "== 场景 2: 包要求高于工具链 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.28"
expect_fail "go 1.28 vs 工具链 1.27.3"

echo "== 场景 3: go 指令写全三段版本 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.27.0"
expect_pass "go 1.27.0 与 1.27 等价"

echo "== 场景 4: toolchain 指令要求更高 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.27" "toolchain go1.28.0"
expect_fail "toolchain go1.28.0 vs 工具链 1.27.3"

echo "== 场景 5: toolchain 指令可被满足 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.27" "toolchain go1.27.1"
expect_pass "toolchain go1.27.1 vs 工具链 1.27.3"

echo "== 场景 6: vendor 目录内的 go.mod 被忽略 =="
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/go.mod" \
	"module github.com/xtls/xray-core" "" "go 1.27"
write_go_mod "$FEEDS_SCAN_DIR/small8/xray-core/vendor/example.com/dep/go.mod" \
	"module example.com/dep" "" "go 1.99"
expect_pass "vendor 内 go 1.99 不影响校验"

echo "== 场景 7: feeds 下无 go.mod =="
rm -rf "$FEEDS_SCAN_DIR/small8/xray-core"
expect_pass "无 go.mod 时安全通过"

echo "== 场景 8: 缺 golang Makefile 时跳过 =="
rm -f "$GOLANG_MK_FILE"
expect_pass "缺少 Makefile 时跳过而非失败"

echo "ALL PASSED"
