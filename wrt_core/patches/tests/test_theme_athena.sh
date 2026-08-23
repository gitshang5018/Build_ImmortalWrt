#!/usr/bin/env bash
set -e

THEME_DIR="package/themes/luci-theme-athena"

echo "=== 1. 检查主题包结构完整性 ==="
[ -f "$THEME_DIR/Makefile" ] || { echo "FAIL: Makefile 不存在"; exit 1; }
[ -f "$THEME_DIR/root/etc/uci-defaults/30_luci-theme-athena" ] || { echo "FAIL: uci-defaults 缺失"; exit 1; }
[ -f "$THEME_DIR/htdocs/luci-static/athena/css/cascade.css" ] || { echo "FAIL: cascade.css 缺失"; exit 1; }
[ -f "$THEME_DIR/htdocs/luci-static/athena/css/dark.css" ] || { echo "FAIL: dark.css 缺失"; exit 1; }
[ -f "$THEME_DIR/htdocs/luci-static/athena/js/theme.js" ] || { echo "FAIL: theme.js 缺失"; exit 1; }
[ -f "$THEME_DIR/ucode/template/themes/athena/header.ut" ] || { echo "FAIL: header.ut 缺失"; exit 1; }
[ -f "$THEME_DIR/ucode/template/themes/athena/sysauth.ut" ] || { echo "FAIL: sysauth.ut 缺失"; exit 1; }

echo "=== 2. 验证 CSS 核心设计变量 ==="
grep -q -- "--primary-blue" "$THEME_DIR/htdocs/luci-static/athena/css/cascade.css" || { echo "FAIL: 缺失 --primary-blue 变量"; exit 1; }
grep -q -- "--bg-card" "$THEME_DIR/htdocs/luci-static/athena/css/cascade.css" || { echo "FAIL: 缺失 --bg-card 变量"; exit 1; }

echo "=== 3. 验证 JS 核心交互接口 ==="
grep -q -- "getTempInfo" "$THEME_DIR/htdocs/luci-static/athena/js/theme.js" || { echo "FAIL: 缺失温控获取逻辑"; exit 1; }
grep -q -- "drop_caches" "$THEME_DIR/htdocs/luci-static/athena/js/theme.js" || { echo "FAIL: 缺失内存释放逻辑"; exit 1; }

echo "PASS: test_theme_athena (主题文件结构与核心特性验证全部通过)"
