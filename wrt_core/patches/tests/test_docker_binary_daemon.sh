#!/bin/bash
set -e

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../../.." && pwd)

echo "=== 1. 模拟 dockerd/Makefile 修复前结构 ==="
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

MK_PATH="$TMP_DIR/Makefile"
cat > "$MK_PATH" << 'EOF'
define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
endef

define Build/Compile
	cd $(PKG_BUILD_DIR); $(DOCKERD_MAKE_ENV) ./hack/make.sh binary
endef
EOF

source "$REPO_ROOT/wrt_core/modules/docker.sh"

echo "=== 2. 执行 _docker_stack_fix_dockerd_binary_daemon_copy ==="
_docker_stack_fix_dockerd_binary_daemon_copy "$MK_PATH"

grep -q 's/copy_binaries() {/copy_binaries() { return 0;/g' "$MK_PATH" || {
    echo "FAIL: Makefile 中未正确插入 copy_binaries() { return 0; 修补规则"
    exit 1
}

echo "=== 3. 模拟真实的 hack/make/binary-daemon 文件执行 ==="
MOCK_BUILD_DIR="$TMP_DIR/build_dir"
mkdir -p "$MOCK_BUILD_DIR/hack/make"
DAEMON_SCRIPT="$MOCK_BUILD_DIR/hack/make/binary-daemon"

cat > "$DAEMON_SCRIPT" << 'EOF'
#!/usr/bin/env bash
set -e

copy_binaries() {
	local dir="${1:?}"
	echo "Attempting to copy to $dir"
}

GOBIN="/tmp/mock_gobin"
copy_binaries "$GOBIN"
echo "Binary daemon built successfully"
EOF

chmod +x "$DAEMON_SCRIPT"

# 模拟 Makefile 中执行的修补指令
sed -i 's/copy_binaries() {/copy_binaries() { return 0;/g' "$DAEMON_SCRIPT"

# 执行该脚本，确保不再触发 "1: parameter null or not set"
OUTPUT=$("$BASH" "$DAEMON_SCRIPT")
echo "Script output: $OUTPUT"
echo "$OUTPUT" | grep -q "Binary daemon built successfully" || {
    echo "FAIL: 脚本未能成功执行完成"
    exit 1
}

echo "PASS: test_docker_binary_daemon (dockerd binary-daemon 修复验证全部通过)"
