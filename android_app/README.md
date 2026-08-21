# ImmortalWrt 路由管家 (原生 Android App)

这是一个为 **ImmortalWrt / OpenWrt** 路由器打造的纯原生 Android 移动端管理应用（非 Web 封装）。基于 **Kotlin + Jetpack Compose (Material 3)** 打造，具备毫秒级响应速度、流畅的原生手势动画与极低的资源消耗。

---

## 🌟 核心特性

- **纯原生 UI (Material 3)**：基于 Jetpack Compose 响应式架构构建，原生支持浅色/暗黑模式自适应。
- **免装服务端插件**：直接对接 OpenWrt 官方原生的 **`ubus` JSON-RPC** API (`/ubus`)，开箱即用。
- **极速实时监控**：
  - 📊 **仪表盘 (Dashboard)**：实时上下行网速（支持平滑波形图）、CPU 负载、内存使用率、WAN IP 与运行时长。
  - 📱 **在线终端 (Clients)**：已连接设备列表、实时 IP/MAC、信号强度（RSSI dBm）与有线/无线设备识别。
  - 📶 **无线 Wi-Fi 管理 (Wireless)**：2.4G & 5G 双频段 SSID 与密码一键查看/修改、一键复制密码。
  - 🛠️ **快捷工具箱 (Tools)**：一键平稳重启路由器（带防误触确认）、一键释放内核缓存、网络 Ping 时延诊断。

---

## 🏗️ 架构与技术栈

- **架构设计**：MVVM + Clean Architecture + Single Activity Multi-Screen (Navigation Compose)
- **开发语言**：Kotlin 2.0+
- **网络通信**：Retrofit2 + OkHttp3 + Gson (Ubus JSON-RPC 2.0 客户端，内置 Session Token 自动刷新与无感重试)
- **数据持久化**：Jetpack DataStore Preferences
- **异步处理**：Kotlin Coroutines + StateFlow

---

## 🚀 如何构建与运行

### 方式一：使用 Android Studio 打开
1. 打开 **Android Studio (Ladybug / Koala 或更高版本)**。
2. 选择 **Open Project**，打开本仓库下的 `android_app/` 目录。
3. 等待 Gradle 同步完成，连接 Android 手机或模拟器（要求 Android 8.0 / API 26+）。
4. 点击 **Run 'app'** 即可在真机上运行。

### 方式二：使用 Gradle 命令行编译 APK
在 `android_app/` 目录下执行：

```bash
# 生成 Debug 调试版 APK
./gradlew assembleDebug
```

编译产物位于：
`android_app/app/build/outputs/apk/debug/app-debug.apk`

---

## 🔐 登录配置说明

- **路由器地址**：默认为 `10.10.10.1`（支持局域网 IP 或公网域名）。
- **端口**：HTTP 默认为 `80`，HTTPS 默认为 `443`。
- **账户**：默认为 `root`。
- **密码**：路由器登录密码。
