#!/bin/bash
# dockerd hack/make/binary-daemon 修复（_docker_stack_fix_dockerd_binary_daemon_copy）的行为测试
#
# 覆盖三层：
#  1) 产物断言：注入进 Makefile 的 sed 包含完整正则（\1、反斜杠未被 awk -v 吃掉）
#  2) 行为断言：注入后的 Makefile 配方在被 shell 执行时，会真改写 hack/make/binary-daemon
#  3) 端到端断言：修补后的 moby 风格 binary-daemon 从 "cp 空串必炸" 变为 "整段跑完"
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)
cd "$REPO_ROOT"

. wrt_core/modules/docker.sh

fail() { echo "FAIL: $*" >&2; exit 1; }

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# ============================================================
# 用例 A：典型 openwrt feed dockerd Makefile
# ============================================================
MK_PATH="$TMP_DIR/Makefile.dockerd"
cat > "$MK_PATH" << 'MKEOF'
PKG_NAME:=dockerd
PKG_VERSION:=29.3.1

define Build/Prepare
	$(Build/Prepare/Default)
endef

define Build/Compile
	( cd $(PKG_BUILD_DIR); \
		DOCKER_BUILDTAGS='none' \
		VERSION=$(PKG_VERSION) ./hack/make.sh binary )
endef
MKEOF

echo "== 1. 单次注入 =="
_docker_stack_fix_dockerd_binary_daemon_copy "$MK_PATH" || fail "注入返回非 0"

grep -Fq 'wrt-fix' "$MK_PATH" || fail "未注入 [wrt-fix] 标记"
grep -Fq 'cd $(PKG_BUILD_DIR); if [ -f "hack/make/binary-daemon" ]' "$MK_PATH" \
    || fail "注入未锚定在 cd 语句边界之后"
grep -Fq './hack/make.sh binary' "$MK_PATH" || fail "原始的 ./hack/make.sh binary 调用丢失"

echo "== 2. 注入内容保真（awk 传参吞反斜杠的回归点） =="
# 注入的 sed 正则里分组括与后引用必须原样存在（这两个点在历史上都被 awk -v 吞过）
grep -Fq 'copy_binaries[[:space:]]*\(\)[[:space:]]*\{' "$MK_PATH" || fail "sed 正则中的 \( \) 括转义丢失"
grep -Fq '\1 return 0;' "$MK_PATH" || fail "sed 替换侧的 \1 后引用丢失"


echo "== 3. 幂等性 =="
cp "$MK_PATH" "$TMP_DIR/first.mk"
_docker_stack_fix_dockerd_binary_daemon_copy "$MK_PATH" || fail "第二次注入失败"
cmp -s "$TMP_DIR/first.mk" "$MK_PATH" || fail "第二次运行后 Makefile 与第一次不同（非幂等）"

echo "== 4. 行为验证（执行注入的 sed） =="
# 与 CI 中 make 的行为一致：取 Makefile 里 we 注入的完整 sed 命令，直接送入源码目录执行
# （不重新模拟 make 配方展开，聚焦"注入的命令本身能否修好 binary-daemon"）
INJECTED_SED=$(grep -oE "sed -i -E -e '[^']*' -e '[^']*' \"hack/make/binary-daemon\"" "$MK_PATH")
[ -n "$INJECTED_SED" ] || fail "从 Makefile 中未提取到注入的 sed 命令"

# 伪造一个已解压的 dockerd 源码树，其中 binary-daemon 具有 moby 29.x 的真实 bug
BUILD_SRC="$TMP_DIR/pkg_source"
mkdir -p "$BUILD_SRC/hack/make" "$BUILD_SRC/bundles/binary-daemon"

cat > "$BUILD_SRC/hack/make/binary-daemon" << 'DAEMONEOF'
#!/usr/bin/env bash
set -e
mkdir -p bundles/binary-daemon

copy_binaries() {
    local dest="$1"
    shift
    echo "Copying nested executables into $dest"
    local f
    for f in "$@"; do
        cp "$f" "$dest/"
    done
}

# 无问题时正常调用
cp /bin/true "bundles/binary-daemon/true" 2>/dev/null || true
# moby 29.x：同构架构下 NESTED_BINS 被清空，第二个参数成为空串
copy_binaries "bundles/binary-daemon" ""
DAEMONEOF

# 先跑未修补版本：set -e + cp "" 必须炸，证明 mock 有意义
if bash -e "$BUILD_SRC/hack/make/binary-daemon" > /dev/null 2>&1; then
    fail "mock 的 binary-daemon 未触发 bug，测试无对照价值"
fi

# 在 mock 源码目录（模拟 cd 后的 cwd）执行注入的 sed
( cd "$BUILD_SRC" && eval "$INJECTED_SED" ) || fail "注入的 sed 执行失败"

# 注入的 sed 必须真的改写了 daemon 脚本
grep -Fq 'copy_binaries() { return 0;' "$BUILD_SRC/hack/make/binary-daemon" \
    || fail "注入的 sed 未改写 hack/make/binary-daemon"

# 改写后的 daemon 应能跑完（copy 空串已被屏蔽）
bash -e "$BUILD_SRC/hack/make/binary-daemon" > /dev/null 2>&1 \
    || fail "修补后 binary-daemon 仍中止"

echo "== 5. 历史遗留注入清理 =="
LEGACY="$TMP_DIR/legacy.mk"
cat > "$LEGACY" << 'LEGACYEOF'
define Build/Compile
	( cd $(PKG_BUILD_DIR); [ ! -f ./hack/make/binary-daemon ] || sed -i "s/copy_binaries() {/copy_binaries() { return 0;/g" ./hack/make/binary-daemon; ./hack/make.sh binary )
endef
LEGACYEOF
_docker_stack_fix_dockerd_binary_daemon_copy "$LEGACY" || fail "legacy 注入失败"
grep -Fq 'wrt-fix' "$LEGACY" || fail "legacy 中缺少新注入"
COUNT=$(grep -c 'sed.*copy_binaries' "$LEGACY")
[ "$COUNT" -eq 1 ] || fail "legacy 清理后仍含 $COUNT 处 sed 修补（应为 1）"

echo
echo "PASS: test_docker_binary_daemon (dockerd 29.3.1 copy_binaries 修复全流程验证通过)"
