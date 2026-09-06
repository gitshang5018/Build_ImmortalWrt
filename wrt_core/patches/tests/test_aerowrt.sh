#!/usr/bin/env bash
set -e

PKG_DIR="package/services/aerowrt"

echo "=== 1. 检查 aerowrt 包结构完整性 ==="
[ -f "$PKG_DIR/Makefile" ] || { echo "FAIL: Makefile 不存在"; exit 1; }
[ -f "$PKG_DIR/files/aerowrt.init" ] || { echo "FAIL: aerowrt.init 缺失"; exit 1; }
[ -f "$PKG_DIR/files/aerowrt.config" ] || { echo "FAIL: aerowrt.config 缺失"; exit 1; }
[ -f "$PKG_DIR/src/main.go" ] || { echo "FAIL: main.go 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/index.html" ] || { echo "FAIL: WebUI index.html 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/app.css" ] || { echo "FAIL: WebUI app.css 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/app.js" ] || { echo "FAIL: WebUI app.js 缺失"; exit 1; }
[ -f "$PKG_DIR/files/luci/menu.d/luci-app-aerowrt.json" ] || { echo "FAIL: LuCI menu.d 缺失"; exit 1; }
[ -f "$PKG_DIR/files/luci/acl.d/luci-app-aerowrt.json" ] || { echo "FAIL: LuCI acl.d 缺失"; exit 1; }
[ -f "$PKG_DIR/files/luci/view/overview.js" ] || { echo "FAIL: LuCI view 缺失"; exit 1; }
[ -f "$PKG_DIR/files/luci/controller/aerowrt.lua" ] || { echo "FAIL: LuCI controller 缺失"; exit 1; }
[ -f "$PKG_DIR/files/luci/cbi/aerowrt.lua" ] || { echo "FAIL: LuCI cbi 缺失"; exit 1; }

echo "=== 2. 运行 Go 自动化单元测试 ==="
pushd "$PKG_DIR/src" >/dev/null
go test ./... -v
popd >/dev/null

echo "=== 3. 验证跨平台交叉编译能力 ==="
pushd "$PKG_DIR/src" >/dev/null
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /tmp/aerowrt-amd64 main.go
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o /tmp/aerowrt-arm64 main.go
rm -f /tmp/aerowrt-amd64 /tmp/aerowrt-arm64
popd >/dev/null

echo "PASS: test_aerowrt (结构完整性、Go 单元测试与交叉编译全链路通过)"
