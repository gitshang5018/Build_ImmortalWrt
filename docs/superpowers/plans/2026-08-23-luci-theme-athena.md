# luci-theme-athena 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建并集成 `luci-theme-athena` 现代卡片化 LuCI 主题，将 Android App 的设计语言（清爽蓝/暗黑双模、圆角悬浮卡片、三频温控、实时波形图、一键内存释放）完整赋能至 OpenWrt Web 网页端。

**架构：** 基于 LuCI 现代 ucode/JS 模板体系构建独立的主题软件包 `luci-theme-athena`。通过 CSS 变量系统提供明亮/暗黑双模适配，通过 JS 实现实时流量贝塞尔曲线图、雅典娜三频温控和内存一键清理，通过 Makefile 与 UCI 默认配置注册进构建系统。

**技术栈：** OpenWrt LuCI (ucode/JS), CSS3 (CSS Variables, Flexbox/Grid, Backdrop Filter), SVG 矢量图形, Shell (Makefile / uci-defaults).

---

## 涉及文件结构

- `package/themes/luci-theme-athena/Makefile`: 主题 OpenWrt 编译描述文件
- `package/themes/luci-theme-athena/root/etc/uci-defaults/30_luci-theme-athena`: 主题注册脚本
- `package/themes/luci-theme-athena/root/usr/share/luci/menu.d/luci-theme-athena.json`: 主题菜单项定义
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/css/cascade.css`: 主题核心 CSS 与明亮模式变量
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/css/dark.css`: 暗黑模式配色表
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/css/mobile.css`: 移动端自适应布局
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/js/theme.js`: 核心交互（侧边栏、明暗模式、波形图、三频温控、内存释放）
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/img/logo.svg`: 雅典娜科技矢量 Logo
- `package/themes/luci-theme-athena/htdocs/luci-static/athena/img/bg-login.svg`: 登录页背景图形
- `package/themes/luci-theme-athena/ucode/template/themes/athena/header.ut`: 头部与侧边栏 ucode 模板
- `package/themes/luci-theme-athena/ucode/template/themes/athena/footer.ut`: 底部 ucode 模板
- `package/themes/luci-theme-athena/ucode/template/themes/athena/sysauth.ut`: 登录认证 ucode 模板
- `wrt_core/patches/tests/test_theme_athena.sh`: 自动化测试脚本
- `wrt_core/deconfig/jdcloud_ipq60xx_immwrt.config`: 编译配置集成

---

### 任务 1：创建自动化验证测试脚本 (`test_theme_athena.sh`)

**文件：**
- 创建：`wrt_core/patches/tests/test_theme_athena.sh`

- [ ] **步骤 1：编写失败的验证测试脚本**

```bash
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
```

- [ ] **步骤 2：运行测试验证失败**

运行：`bash wrt_core/patches/tests/test_theme_athena.sh`
预期：FAIL，报错 "Makefile 不存在"

- [ ] **步骤 3：提交测试脚本**

```bash
git add wrt_core/patches/tests/test_theme_athena.sh
git commit -m "test(theme): add test_theme_athena test suite"
```

---

### 任务 2：构建主题 Makefile 与 UCI 默认配置

**文件：**
- 创建：`package/themes/luci-theme-athena/Makefile`
- 创建：`package/themes/luci-theme-athena/root/etc/uci-defaults/30_luci-theme-athena`
- 创建：`package/themes/luci-theme-athena/root/usr/share/luci/menu.d/luci-theme-athena.json`

- [ ] **步骤 1：编写 Makefile**

```makefile
#
# Copyright (C) 2026 OpenWrt.org
#
# This is free software, licensed under the Apache License, Version 2.0 .
#

include $(TOPDIR)/rules.mk

LUCI_TITLE:=Athena Modern App-Style Theme
LUCI_DEPENDS:=+luci-base
PKG_VERSION:=1.0.0
PKG_RELEASE:=1
PKG_LICENSE:=Apache-2.0

include $(TOPDIR)/feeds/luci/luci.mk

# call BuildPackage - OpenWrt buildroot signature
```

- [ ] **步骤 2：编写 UCI 注册与菜单配置**

`root/etc/uci-defaults/30_luci-theme-athena`:
```sh
#!/bin/sh

uci -q batch <<-EOF >/dev/null
	set luci.themes.Athena=/luci-static/athena
	commit luci
EOF

exit 0
```

`root/usr/share/luci/menu.d/luci-theme-athena.json`:
```json
{
	"admin/system/themes/athena": {
		"title": "Athena",
		"order": 30,
		"action": {
			"type": "theme",
			"name": "athena"
		}
	}
}
```

- [ ] **步骤 3：提交构建配置**

```bash
git add package/themes/luci-theme-athena/
git commit -m "feat(theme): add Makefile and uci-defaults for luci-theme-athena"
```

---

### 任务 3：构建主题样式表 (`cascade.css`, `dark.css`, `mobile.css`)

**文件：**
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/css/cascade.css`
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/css/dark.css`
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/css/mobile.css`

- [ ] **步骤 1：编写 `cascade.css`（核心设计系统与明亮模式）**
- [ ] **步骤 2：编写 `dark.css`（暗黑模式配色覆盖）**
- [ ] **步骤 3：编写 `mobile.css`（移动端自适应）**
- [ ] **步骤 4：提交样式表代码**

```bash
git add package/themes/luci-theme-athena/htdocs/luci-static/athena/css/
git commit -m "feat(theme): add Material 3 card-based CSS styles with dark mode"
```

---

### 任务 4：实现核心交互 JS 与实时看板 (`theme.js`, `logo.svg`, `bg-login.svg`)

**文件：**
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/js/theme.js`
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/img/logo.svg`
- 创建：`package/themes/luci-theme-athena/htdocs/luci-static/athena/img/bg-login.svg`

- [ ] **步骤 1：编写 `theme.js` 核心逻辑**
  - 明暗模式切换（localStorage 存储 + 系统偏好监听）。
  - 侧边栏折叠/展开动画。
  - 实时流量波形图渲染（监听 ubus `network.interface` 流量并平滑绘制贝塞尔曲线）。
  - 雅典娜三频温控卡片注入（读取 `/ubus` 的 `luci.getTempInfo`，拆解 CPU、2.4G、5.8G、5.2G 电竞三频）。
  - 一键释放内存（调用 ubus `file.exec` 执行 `sync && echo 3 > /proc/sys/vm/drop_caches` 并刷新界面）。
- [ ] **步骤 2：生成 `logo.svg` 与 `bg-login.svg` 矢量素材**
- [ ] **步骤 3：提交核心脚本与图像素材**

```bash
git add package/themes/luci-theme-athena/htdocs/luci-static/athena/
git commit -m "feat(theme): add theme.js with traffic waveform, tri-band temp and drop-caches"
```

---

### 任务 5：编写 LuCI 模板 (`header.ut`, `footer.ut`, `sysauth.ut`)

**文件：**
- 创建：`package/themes/luci-theme-athena/ucode/template/themes/athena/header.ut`
- 创建：`package/themes/luci-theme-athena/ucode/template/themes/athena/footer.ut`
- 创建：`package/themes/luci-theme-athena/ucode/template/themes/athena/sysauth.ut`

- [ ] **步骤 1：编写现代卡片化登录页模板 `sysauth.ut`**
- [ ] **步骤 2：编写悬浮侧边栏与头部导航模板 `header.ut`**
- [ ] **步骤 3：编写底部模板 `footer.ut`**
- [ ] **步骤 4：提交模板文件**

```bash
git add package/themes/luci-theme-athena/ucode/
git commit -m "feat(theme): add ucode templates for header, footer and sysauth"
```

---

### 任务 6：集成到固件构建系统并执行全量测试

**文件：**
- 修改：`wrt_core/deconfig/jdcloud_ipq60xx_immwrt.config`

- [ ] **步骤 1：在配置中加入 `CONFIG_PACKAGE_luci-theme-athena=y`**
- [ ] **步骤 2：运行测试脚本 `test_theme_athena.sh` 与现有所有测试**

运行：`bash wrt_core/patches/tests/test_theme_athena.sh`
预期：PASS: test_theme_athena

- [ ] **步骤 3：提交配置修改**

```bash
git add wrt_core/deconfig/jdcloud_ipq60xx_immwrt.config
git commit -m "chore(config): enable luci-theme-athena for jdcloud_ipq60xx"
```
