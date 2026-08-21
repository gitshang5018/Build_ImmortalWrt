# ImmortalWrt 路由管家 (Android 原生 App) 全方位完善实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 全面完善 Android 端 ImmortalWrt 路由管家原生应用，实现 5 标签页现代架构、3 频 Wi-Fi 调优与二维码连接、常用插件中枢 (OpenClash/PassWall/MosDNS/Lucky/Docker 等)、实时贝塞尔波形流量图、端口转发与 DHCP 静态绑定、系统日志与多路由器节点管理。

**架构：** Kotlin + Jetpack Compose (Material 3) 响应式架构，MVVM + Clean Architecture，直接对接 OpenWrt 原生 Ubus JSON-RPC 2.0 API，无缝 Token 鉴权与自动重试，StateFlow 驱动 UI。

**技术栈：** Kotlin 2.0, Jetpack Compose, Material 3, Retrofit2, OkHttp3, Gson, DataStore Preferences, ZXing (QR Code).

---

## 文件结构计划

### 1. 构建配置与依赖
- `android_app/local.properties`: 配置 Android SDK 路径 `D:/Program Files/Android/SDK`
- `android_app/app/build.gradle.kts`: 添加 ZXing 二维码核心库依赖 `com.google.zxing:core:3.5.3`

### 2. 领域模型与数据访问层
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/domain/model/Models.kt` (定义 3频无线模型、插件服务状态、端口转发规则、静态 DHCP 绑定、系统日志条目、多路由节点)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/data/repository/PreferencesRepository.kt` (支持多路由器节点列表存储、暗黑模式切换)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt` (实现 3 频 Wi-Fi 解析、插件状态探测与控制、系统日志获取、端口转发与静态绑定 UCI 管理、网络诊断)

### 3. UI 基础组件
- 创建: `android_app/app/src/main/java/org/immortalwrt/manager/ui/components/TrafficWaveformChart.kt` (Canvas 贝塞尔波形图组件)
- 创建: `android_app/app/src/main/java/org/immortalwrt/manager/ui/components/QrCodeDialog.kt` (Wi-Fi 二维码弹窗)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/components/CommonComponents.kt` (增强卡片、状态指示灯)

### 4. 页面层 (Screens & ViewModels)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt` & `DashboardViewModel.kt`
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/clients/ClientsScreen.kt` & `ClientsViewModel.kt`
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/wireless/WirelessScreen.kt` & `WirelessViewModel.kt`
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/tools/ToolsScreen.kt` & `ToolsViewModel.kt`
- 创建: `android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/settings/SettingsScreen.kt` & `SettingsViewModel.kt`

### 5. 导航与入口
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/Screen.kt` (添加 Settings 路由)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/NavGraph.kt` (5 标签页底部导航栏)
- 修改: `android_app/app/src/main/java/org/immortalwrt/manager/MainActivity.kt` (主题与动态取色支持)

---

## 任务列表

### 任务 1：配置 SDK 路径与二维码依赖

**文件：**
- 修改：`android_app/local.properties`
- 修改：`android_app/app/build.gradle.kts`

- [ ] **步骤 1：写入 local.properties SDK 路径**
- [ ] **步骤 2：在 app/build.gradle.kts 中添加 zxing 依赖**
- [ ] **步骤 3：运行 Gradle 同步验证配置正常**
- [ ] **步骤 4：Commit**
```bash
git add android_app/local.properties android_app/app/build.gradle.kts
git commit -m "chore(android): configure sdk path and add zxing qr dependency"
```

---

### 任务 2：扩展领域模型 (Models.kt)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/domain/model/Models.kt`

- [ ] **步骤 1：添加 3 频 Wi-Fi、插件服务、端口转发、静态 DHCP、系统日志、多节点数据模型**
- [ ] **步骤 2：编写单元测试验证模型默认值与格式化辅助方法**
- [ ] **步骤 3：运行测试验证通过**
- [ ] **步骤 4：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/domain/model/Models.kt
git commit -m "feat(domain): define models for tri-band wifi, plugins, firewall, and multi-node"
```

---

### 任务 3：扩展数据仓库 (PreferencesRepository & RouterRepository)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/PreferencesRepository.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt`

- [ ] **步骤 1：在 PreferencesRepository 中添加多节点和主题持久化接口**
- [ ] **步骤 2：在 RouterRepository 中实现 3 频无线、插件服务状态查询与控制、端口转发、静态绑定、系统日志和网络诊断 API 对接**
- [ ] **步骤 3：编写单元测试测试数据仓库**
- [ ] **步骤 4：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/data/repository/
git commit -m "feat(data): implement tri-band wifi, plugins, firewall uci, and diagnostics repository"
```

---

### 任务 4：实现通用 UI 组件 (贝塞尔波形图与二维码弹窗)

**文件：**
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/components/TrafficWaveformChart.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/components/QrCodeDialog.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/components/CommonComponents.kt`

- [ ] **步骤 1：编写 TrafficWaveformChart 组件（Canvas 绘制 30s 动态贝塞尔波形曲线）**
- [ ] **步骤 2：编写 QrCodeDialog 组件（基于 ZXing 生成 Bitmap 并渲染二维码）**
- [ ] **步骤 3：扩展 CommonComponents 中的状态标签与卡片**
- [ ] **步骤 4：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/components/
git commit -m "feat(ui): add bezier traffic waveform chart and qr code dialog components"
```

---

### 任务 5：重构与增强仪表盘 (Dashboard)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardViewModel.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt`

- [ ] **步骤 1：在 DashboardViewModel 中维护 30 点历史网速环形缓冲区与定时轮询**
- [ ] **步骤 2：在 DashboardScreen 中集成实时波形图、动态 CPU 预警颜色、WAN/LAN 网关详情**
- [ ] **步骤 3：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/
git commit -m "feat(dashboard): integrate realtime bezier waveform chart and enhanced metrics"
```

---

### 任务 6：增强终端管理 (Clients)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/clients/ClientsViewModel.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/clients/ClientsScreen.kt`

- [ ] **步骤 1：在 ViewModel 中添加设备重命名别名与静态 DHCP IP 绑定逻辑**
- [ ] **步骤 2：在 Screen 中增加厂商 OUI / 设备类型图标智能识别与设备操作抽屉**
- [ ] **步骤 3：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/clients/
git commit -m "feat(clients): add vendor device recognition and static dhcp binding drawer"
```

---

### 任务 7：全面升级 3 频无线管理 (Wireless)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/wireless/WirelessViewModel.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/wireless/WirelessScreen.kt`

- [ ] **步骤 1：ViewModel 支持 2.4G/5.2G/5.8G/6G 多频段参数解析与批量更新**
- [ ] **步骤 2：Screen 呈现多频段专属卡片、信道/频宽/功率精细调节与一键二维码直连分享**
- [ ] **步骤 3：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/wireless/
git commit -m "feat(wireless): implement tri-band wifi management and qr code sharing"
```

---

### 任务 8：打造实用工具箱与插件服务中枢 (Tools & Plugins)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/tools/ToolsViewModel.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/tools/ToolsScreen.kt`

- [ ] **步骤 1：在 ViewModel 中接入常用插件服务状态探测、启停重启、系统日志与 DNS/Traceroute 诊断**
- [ ] **步骤 2：在 Screen 中构建插件看板网格（OpenClash/PassWall/MosDNS/Lucky/Docker等）、系统实时日志查看器、网络诊断面板**
- [ ] **步骤 3：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/tools/
git commit -m "feat(tools): add plugins service hub, real-time logread viewer, and network diagnostics"
```

---

### 任务 9：实现设置与高级网络模块 (Settings) 并升级 5 标签导航

**文件：**
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/settings/SettingsViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/settings/SettingsScreen.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/Screen.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/NavGraph.kt`
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/MainActivity.kt`

- [ ] **步骤 1：编写 SettingsViewModel 与 SettingsScreen（多路由器节点管理、防火墙端口转发规则、暗黑模式切换）**
- [ ] **步骤 2：更新 Screen.kt 与 NavGraph.kt 为 5 标签页底部导航栏**
- [ ] **步骤 3：更新 MainActivity.kt 主题与应用配置**
- [ ] **步骤 4：Commit**
```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/
git commit -m "feat(settings): add settings screen with multi-node management, port forwarding, and 5-tab nav"
```

---

### 任务 10：全量编译构建与自动化验证

**文件：**
- 全量 Android 项目代码

- [ ] **步骤 1：执行 `./gradlew testDebugUnitTest` 运行全量单元测试**
- [ ] **步骤 2：执行 `./gradlew assembleDebug` 编译生成完整 Debug APK**
- [ ] **步骤 3：验证 APK 生成无误 (`android_app/app/build/outputs/apk/debug/app-debug.apk`)**
- [ ] **步骤 4：Commit & Walkthrough**
```bash
git add .
git commit -m "chore: complete app enhancement build and verification"
```
