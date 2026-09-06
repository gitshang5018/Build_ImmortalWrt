#!/usr/bin/env bash
set -e

PKG_DIR="package/services/v2rayn-wrt"

echo "=== 1. 检查 v2rayn-wrt 包结构完整性 ==="
[ -f "$PKG_DIR/Makefile" ] || { echo "FAIL: Makefile 不存在"; exit 1; }
[ -f "$PKG_DIR/files/v2rayn-wrt.init" ] || { echo "FAIL: v2rayn-wrt.init 缺失"; exit 1; }
[ -f "$PKG_DIR/files/v2rayn-wrt.config" ] || { echo "FAIL: v2rayn-wrt.config 缺失"; exit 1; }
[ -f "$PKG_DIR/src/main.go" ] || { echo "FAIL: main.go 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/index.html" ] || { echo "FAIL: WebUI index.html 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/app.css" ] || { echo "FAIL: WebUI app.css 缺失"; exit 1; }
[ -f "$PKG_DIR/src/web/dist/app.js" ] || { echo "FAIL: WebUI app.js 缺失"; exit 1; }

echo "=== 2. 运行 Go 自动化单元测试 ==="
pushd "$PKG_DIR/src" >/dev/null
go test ./... -v
popd >/dev/null

echo "=== 3. 验证跨平台交叉编译能力 ==="
pushd "$PKG_DIR/src" >/dev/null
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o /tmp/v2rayn-wrt-amd64 main.go
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o /tmp/v2rayn-wrt-arm64 main.go
rm -f /tmp/v2rayn-wrt-amd64 /tmp/v2rayn-wrt-arm64
popd >/dev/null

echo "PASS: test_v2rayn_wrt (结构完整性、Go 单元测试与交叉编译全链路通过)"
