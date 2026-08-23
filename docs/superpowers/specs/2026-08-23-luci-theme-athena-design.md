# luci-theme-athena 设计规格说明书

## 1. 项目概述

`luci-theme-athena` 是一套专为 OpenWrt / ImmortalWrt 设计的现代卡片化 LuCI 主题，其设计语言与交互逻辑完整对齐 Native Android 管理 App。该主题针对京东云雅典娜（AX6600 / RE-CS-02）及亚瑟（AX1800 Pro / RE-SS-01）进行了深度优化，同时通用兼容所有 OpenWrt 架构。

---

## 2. 架构与目录结构

主题作为独立的可选软件包集成至构建系统：

```
wrt_core/
└── themes/
    └── luci-theme-athena/
        ├── Makefile
        ├── htdocs/
        │   └── luci-static/
        │       └── athena/
        │           ├── css/
        │           │   ├── cascade.css       # 核心样式与明亮模式变量
        │           │   ├── dark.css          # 暗黑模式主题变量与配色覆盖
        │           │   └── mobile.css        # 移动端自适应响应式布局
        │           ├── js/
        │           │   ├── theme.js          # 主题核心（明暗切换、侧边栏收折、实时波形）
        │           │   └── chart.min.js      # 轻量贝塞尔流量波形渲染库
        │           ├── img/
        │           │   ├── logo.svg          # 雅典娜高质感矢量 Logo
        │           │   ├── favicon.ico
        │           │   └── bg-login.svg      # 登录页科技渐变背景
        │           └── font/                 # 核心矢量图标与字体
        ├── luasrc/                           # LuCI Lua 模板兼容层 (若需)
        └── ucode/                            # LuCI 24.10 / ucode 现代模板层
            └── template/
                └── themes/
                    └── athena/
                        ├── header.ut         # 头部与侧边栏导航模板
                        ├── footer.ut         # 底部模板
                        ├── sysauth.ut        # 登录认证模板
                        └── view/
                            └── status/
                                └── include/
                                    └── 10_system.js # 增强版状态概览（波形图/温控/内存清理）
```

---

## 3. 色彩规范与设计系统 (Material 3 / Card UI)

### 3.1 调色板定义

```css
:root {
  /* 品牌与状态色 (与 Android App 100% 对齐) */
  --primary-blue: #1E88E5;
  --primary-blue-dark: #1565C0;
  --secondary-cyan: #00BCD4;
  --success-green: #34C759;
  --warning-orange: #FF9500;
  --error-red: #FF3B30;

  /* 明亮模式 (Light Mode) */
  --bg-main: #F5F7FA;
  --bg-card: #FFFFFF;
  --bg-sidebar: #FFFFFF;
  --border-color: #E2E8F0;
  --text-primary: #1E293B;
  --text-secondary: #64748B;
  --card-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  --card-radius: 16px;
  --input-radius: 12px;
}

[data-theme="dark"] {
  /* 暗黑模式 (Dark Mode) */
  --bg-main: #0F172A;
  --bg-card: #1E293B;
  --bg-sidebar: #1E293B;
  --border-color: #334155;
  --text-primary: #F8FAFC;
  --text-secondary: #94A3B8;
  --card-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
}
```

---

## 4. 核心功能与页面设计

### 4.1 登录页 (`sysauth.ut`)
- **视觉**：采用科技蓝紫渐变背景，中央呈现带有毛玻璃质感的自适应登录卡片。
- **交互**：输入框聚焦发光动画，支持回车快捷登录、显示/隐藏密码切换，移动端键盘弹起不遮挡。

### 4.2 侧边栏与头部导航 (`header.ut` + `theme.js`)
- **侧边栏**：悬浮式卡片菜单，各级路由菜单项带有现代线条图标，支持一键收折为极简图标栏。
- **头部快捷栏**：
  - 当前路由器型号与固件版本徽章。
  - **明暗模式切换开关**：支持「跟随系统自动切换」与「手动明/暗锁定」，状态保存在 `localStorage`。
  - 快速注销与快捷导航。

### 4.3 状态概览页增强 (`view/status/include/10_system.js`)
- **实时流量贝塞尔波形图**：
  - 监听 `/ubus` 实时上下行网速，使用平滑贝塞尔曲线实时绘制近 60 秒流量波形（下行蓝色，上行青色）。
- **硬件资源监控仪表盘**：
  - CPU 核心负载环形进度条与平均负载。
  - Linux 物理内存、实时可用内存与缓存占比条。
- **雅典娜专用三频温控专区**：
  - 读取 `luci.getTempInfo` / `/sbin/tempinfo`，独立展示 **CPU 温度**、**2.4GHz 频段温度**、**5.8GHz 频段温度**、**5.2GHz 电竞频段温度**。
  - 温度随数值显示绿（<65°C）、橙（65-80°C）、红（>80°C）状态色彩。
- **一键内核缓存释放**：
  - 提供「释放缓存」交互按钮，点击通过 ubus 触发系统 `drop_caches`，清理不必要的 PageCache 并刷新内存状态。

### 4.4 全局表单与插件兼容
- 重绘所有标准 LuCI 组件：表格（Table）、输入框（Input）、下拉框（Select）、Tab 切换栏、开关（Switch）、提示气泡（Alerts）。
- 无缝兼容 PassWall、MosDNS、Lucky、SQM、OpenClash、Athena-LED 等所有 LuCI 应用，避免样式错位。

---

## 5. 构建与集成规范

1. **软件包定义**：`package/themes/luci-theme-athena/Makefile`
   - `PKG_NAME:=luci-theme-athena`
   - `LUCI_TITLE:=Athena App-Style Modern Theme`
   - `LUCI_DEPENDS:=+luci-base`
2. **配置文件集成**：
   - 在 `wrt_core/deconfig/jdcloud_ipq60xx_immwrt.config` 中加入 `CONFIG_PACKAGE_luci-theme-athena=y`。
   - 保留原主题选项，用户可在 LuCI「系统 - 语言/界面」自由切换。
