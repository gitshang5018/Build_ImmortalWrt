# ImmortalWrt 原生 Android 管理 App 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建一个基于 Kotlin + Jetpack Compose (Material 3) 的纯原生 Android App，直接对接 ImmortalWrt / OpenWrt 原生 `ubus` JSON-RPC API，提供仪表盘、客户端设备管理、无线 Wi-Fi 设置和快捷系统工具。

**架构：** 采用标准的 Android Clean Architecture + MVVM 架构。`data/` 层负责 OkHttp/Retrofit JSON-RPC 2.0 通信、Session Token 自动保持与 DataStore 凭据存储；`domain/` 层定义路由器业务实体；`ui/` 层采用 Compose Material 3 与 Navigation Compose 实现单 Activity 多屏幕。

**技术栈：** Kotlin 2.0+, Android Gradle Plugin 8.5+, Jetpack Compose, Material 3, Retrofit2, OkHttp3, Kotlinx Serialization / Gson, Jetpack DataStore, Coroutines & StateFlow.

---

### 任务 1：搭建 Android Gradle 工程脚手架与基础清单

**文件：**
- 创建：`android_app/build.gradle.kts`
- 创建：`android_app/settings.gradle.kts`
- 创建：`android_app/gradle.properties`
- 创建：`android_app/app/build.gradle.kts`
- 创建：`android_app/app/src/main/AndroidManifest.xml`
- 创建：`android_app/app/src/main/res/values/strings.xml`
- 创建：`android_app/app/src/main/res/values/colors.xml`
- 创建：`android_app/app/src/main/res/values/themes.xml`

- [ ] **步骤 1：创建根目录 Gradle 配置文件**

配置 `android_app/settings.gradle.kts`、`android_app/build.gradle.kts` 与 `android_app/gradle.properties`，引入 Android Application、Kotlin Android、Compose Compiler 插件与 Maven 仓库。

- [ ] **步骤 2：配置 `app/build.gradle.kts`**

配置依赖项：
- Jetpack Compose BOM (`androidx.compose:compose-bom:2024.06.00`)
- Material 3 (`androidx.compose.material3:material3`)
- Navigation Compose (`androidx.navigation:navigation-compose:2.7.7`)
- ViewModel Compose (`androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3`)
- Retrofit 2 & OkHttp 3 (`com.squareup.retrofit2:retrofit:2.11.0`, `converter-gson:2.11.0`, `logging-interceptor:4.12.0`)
- DataStore Preferences (`androidx.datastore:datastore-preferences:1.1.1`)
- Coroutines (`org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1`)

- [ ] **步骤 3：创建 `AndroidManifest.xml` 与资源文件**

配置网络权限 `<uses-permission android:name="android.permission.INTERNET" />`、`<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`，允许明文 HTTP 传输（`android:usesCleartextTraffic="true"` 以兼容本地 80 端口访问），配置应用名称与基础主题。

- [ ] **步骤 4：Commit**

```bash
git add android_app/
git commit -m "chore(android): setup android gradle project structure and manifest"
```

---

### 任务 2：实现 Ubus JSON-RPC 网络通信层与数据仓库

**文件：**
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/data/api/UbusModels.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/data/api/UbusApi.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/data/api/UbusClient.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/domain/model/Models.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/PreferencesRepository.kt`
- 测试：`android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt`

- [ ] **步骤 1：编写 Ubus JSON-RPC 请求与响应测试**

在 `android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt` 中编写 JSON-RPC 2.0 请求序列化、登录响应反序列化、系统状态反序列化的单元测试。

- [ ] **步骤 2：实现 Ubus 数据契约与 API 接口 (`UbusModels.kt`, `UbusApi.kt`)**

定义标准泛型 `UbusRequest<T>` 与 `UbusResponse<T>`，定义登录、system info、network.device status、uci get/set、system reboot 等 Retrofit 接口。

- [ ] **步骤 3：实现 `UbusClient.kt` 与 Token 自动维持管理**

实现动态 BaseUrl 配置、自签名 SSL 信任链构建、Session Token 自动保持与错误重试逻辑。

- [ ] **步骤 4：实现 `RouterRepository.kt` 与 `PreferencesRepository.kt`**

封装业务方法：`login()`, `getSystemInfo()`, `getRealtimeTraffic()`, `getConnectedClients()`, `getWifiConfigs()`, `updateWifiConfig()`, `rebootRouter()`, `dropCaches()`。

- [ ] **步骤 5：运行测试验证通过**

运行测试，验证数据契约序列化与业务转换逻辑全部通过。

- [ ] **步骤 6：Commit**

```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/data/ android_app/app/src/main/java/org/immortalwrt/manager/domain/ android_app/app/src/test/
git commit -m "feat(android): implement ubus json-rpc client, data models and repositories"
```

---

### 任务 3：实现 Jetpack Compose Material 3 基础与主题导航

**文件：**
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/theme/Color.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/theme/Theme.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/theme/Type.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/Screen.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/NavGraph.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/MainActivity.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ImmortalWrtApp.kt`

- [ ] **步骤 1：定义 Material 3 调色板与主题 (`Theme.kt`, `Color.kt`)**

配置科技蓝/深青主色系，支持自适应 Light / Dark Mode 与 Material You 动态色彩。

- [ ] **步骤 2：定义路由与导航骨架 (`Screen.kt`, `NavGraph.kt`)**

定义导航路由：`Login`、`MainContainer`（包含底部导航 Tabs：`Dashboard`、`Clients`、`Wireless`、`Tools`）。

- [ ] **步骤 3：实现 `MainActivity.kt` 与主窗口 Compose 容器**

- [ ] **步骤 4：Commit**

```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/theme/ android_app/app/src/main/java/org/immortalwrt/manager/ui/navigation/ android_app/app/src/main/java/org/immortalwrt/manager/MainActivity.kt android_app/app/src/main/java/org/immortalwrt/manager/ImmortalWrtApp.kt
git commit -m "feat(android): implement material 3 theme, navigation and main activity"
```

---

### 任务 4：实现全套核心业务屏幕与 ViewModels

**文件：**
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/login/LoginScreen.kt`, `LoginViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt`, `DashboardViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/clients/ClientsScreen.kt`, `ClientsViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/wireless/WirelessScreen.kt`, `WirelessViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/tools/ToolsScreen.kt`, `ToolsViewModel.kt`
- 创建：`android_app/app/src/main/java/org/immortalwrt/manager/ui/components/CommonComponents.kt`

- [ ] **步骤 1：实现 `LoginScreen.kt` 与 `LoginViewModel.kt`**

包含路由器 IP、端口、用户名、密码输入表单，HTTPS 开关，登录状态加载中指示与错误提示 Toast。

- [ ] **步骤 2：实现 `DashboardScreen.kt` 与 `DashboardViewModel.kt`**

包含实时网速波形指示器、CPU 与内存使用率环形进度条、在线设备数统计、系统运行时长指示与 1.5s 协程轮询。

- [ ] **步骤 3：实现 `ClientsScreen.kt` 与 `ClientsViewModel.kt`**

包含客户端列表展示（主机名、IP、MAC、LAN/2.4G/5G 标记、信号强度 dBm）、搜索栏与筛选器。

- [ ] **步骤 4：实现 `WirelessScreen.kt` 与 `WirelessViewModel.kt`**

包含 2.4G / 5G 无线状态卡片、SSID 与密码修改对话框、一键开关与密码复制。

- [ ] **步骤 5：实现 `ToolsScreen.kt` 与 `ToolsViewModel.kt`**

包含一键重启确认弹窗、内存释放、Ping 诊断工具。

- [ ] **步骤 6：Commit**

```bash
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/ android_app/app/src/main/java/org/immortalwrt/manager/ui/components/
git commit -m "feat(android): implement full dashboard, clients, wireless, tools and login screens"
```

---

### 任务 5：端到端构建与验证

- [ ] **步骤 1：运行 JVM 单元测试**
- [ ] **步骤 2：代码质量与文件结构核对**
- [ ] **步骤 3：编写 `android_app/README.md` 说明文档**
- [ ] **步骤 4：Commit 最终成果并同步**
