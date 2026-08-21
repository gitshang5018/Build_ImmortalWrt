# ImmortalWrt 原生 Android 管理 App 设计规范 (Spec)

## 1. 概述与目标

本项目旨在为 ImmortalWrt / OpenWrt 路由器打造一款**纯原生 Android 移动端管理应用（非 Web 封装）**。
该 App 采用 **Kotlin + Jetpack Compose (Material 3)** 现代化架构，直接通过 HTTP/HTTPS 对接路由器底层的 **`ubus` JSON-RPC** API (`/ubus`)，无需在路由器上安装任何额外插件即可开箱即用。

### 核心特性目标：
1. **纯原生极致流畅**：基于 Jetpack Compose 响应式 UI 与 Kotlin 协程，零 Web 容器开销，毫秒级启动与流畅手势动画。
2. **免服务端插件**：标准对接 OpenWrt 官方 `rpcd` / `uhttpd-mod-ubus` / `nginx-mod-ubus` 接口。
3. **安全与易用**：基于 Jetpack DataStore 安全保存连接凭据，支持内网自签名证书兼容，支持自动心跳与 Token 无感刷新。
4. **四大核心功能模块**：
   - 📊 **仪表盘 (Dashboard)**：实时上下行网速波形、CPU/内存负载、WAN 状态与在线终端统计。
   - 📱 **终端设备管理 (Clients)**：在线客户端列表、IP/MAC、信号强度（RSSI）、有线/无线识别。
   - 📶 **无线管理 (Wireless)**：2.4G/5G 状态展示、SSID/密码一键查看与修改、二维码分享。
   - 🛠️ **快捷工具箱 (Tools)**：一键软重启（防误触确认）、内存 Cache 释放、Ping 网络时延诊断。

---

## 2. 工程目录结构 (`android_app/`)

在项目根目录下创建标准的 Android 独立子工程：

```
android_app/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── res/
            │   ├── values/
            │   │   ├── strings.xml
            │   │   └── colors.xml
            │   └── mipmap-anydpi-v26/
            └── java/org/immortalwrt/manager/
                ├── MainActivity.kt
                ├── ImmortalWrtApp.kt
                ├── data/
                │   ├── api/
                │   │   ├── UbusApi.kt
                │   │   ├── UbusModels.kt
                │   │   └── UbusClient.kt
                │   └── repository/
                │       ├── RouterRepository.kt
                │       └── PreferencesRepository.kt
                ├── domain/
                │   └── model/
                │       ├── RouterInfo.kt
                │       ├── TrafficStats.kt
                │       ├── ClientDevice.kt
                │       └── WifiConfig.kt
                └── ui/
                    ├── theme/
                    │   ├── Color.kt
                    │   ├── Theme.kt
                    │   └── Type.kt
                    ├── navigation/
                    │   ├── NavGraph.kt
                    │   └── Screen.kt
                    ├── components/
                    │   ├── SpeedGaugeCard.kt
                    │   └── ResourceUsageCard.kt
                    └── screens/
                        ├── login/
                        │   ├── LoginScreen.kt
                        │   └── LoginViewModel.kt
                        ├── dashboard/
                        │   ├── DashboardScreen.kt
                        │   └── DashboardViewModel.kt
                        ├── clients/
                        │   ├── ClientsScreen.kt
                        │   └── ClientsViewModel.kt
                        ├── wireless/
                        │   ├── WirelessScreen.kt
                        │   └── WirelessViewModel.kt
                        └── tools/
                            ├── ToolsScreen.kt
                            └── ToolsViewModel.kt
```

---

## 3. 通信与 API 协议详细规范

### 3.1 Ubus JSON-RPC 2.0 请求格式
所有请求均发送至 `POST http(s)://<router_ip>:<port>/ubus`，请求体结构：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "call",
  "params": [
    "00000000000000000000000000000000",  // 鉴权 Token (未登录时全0)
    "session",                             // 模块名称
    "login",                               // 方法名称
    { "username": "root", "password": "..." } // 参数对象
  ]
}
```

### 3.2 核心 RPC 方法映射表

| 功能点 | Ubus 模块 | 方法 | 关键请求参数 | 关键返回字段 |
| :--- | :--- | :--- | :--- | :--- |
| **登录认证** | `session` | `login` | `username`, `password` | `ubus_rpc_session` (Token) |
| **系统信息** | `system` | `info` | `{}` | `uptime`, `memory.total/free`, `load` |
| **网络设备状态** | `network.device` | `status` | `name: "eth0/br-lan"` | `statistics.rx_bytes`, `statistics.tx_bytes` |
| **WAN 状态** | `network.interface` | `dump` | `{}` | 查找 `interface.interface=="wan"` 的 IPv4 地址与网关 |
| **DHCP 租约** | `luci-rpc` 或 `uci` | `get` | `{ "config": "dhcp" }` | 获取客户端主机名与分配 IP 映射 |
| **无线客户端列表** | `iwinfo` / `hostapd.*` | `assoclist` | `device: "wlan0/wlan1"` | `mac`, `signal`, `rx_rate`, `tx_rate` |
| **无线配置查询** | `uci` | `get` | `{ "config": "wireless" }` | 射频频段、SSID、加密方式、密码 |
| **无线配置下发** | `uci` | `set` + `commit` | `config`, `section`, `values` | 生效状态码 |
| **重载网络** | `file` / `luci` | `exec` | `/sbin/wifi reload` 或 `/etc/init.d/network restart` | 结果码 |
| **系统重启** | `system` | `reboot` | `{}` | 确认重启状态 |

### 3.3 Token 维持与重连机制
- `UbusClient` 内部维护单例 `SessionHolder`。
- 配置 OkHttp `Authenticator` 或 Response 拦截器：当 HTTP 响应返回错误码或 Ubus 结果为 `-1` (Permission Denied) 时，自动加锁重走 `login` 请求并重放原请求，对 UI 层完全透明。

---

## 4. UI 界面与交互设计规范

### 4.1 登录页 (`LoginScreen`)
- 输入项：路由器地址（默认 `10.10.10.1`）、端口（默认 `80`）、用户名（默认 `root`）、密码。
- 开关：使用 HTTPS（开启后支持自定义端口 443 并自动配置信任自签名证书）。
- 历史记录：自动记录最近登录成功的路由器，支持下拉一键切换。

### 4.2 仪表盘 (`DashboardScreen`)
- **顶部状态栏**：路由器硬件型号、固件版本标签、系统运行时长。
- **实时流量速率表**：
  - 每 1.5 秒轮询一次 `network.device status`，计算差值得到当前实时下载/上传速率（单位自动转换为 KB/s 或 MB/s）。
  - 使用 Canvas 绘制轻量级实时速率波形折线。
- **硬件资源指示卡**：
  - CPU 负载百分比环形图。
  - 物理内存使用百分比环形图（已用 / 总量）。
  - 在线连接终端总数统计徽章。

### 4.3 客户端管理 (`ClientsScreen`)
- 设备卡片列表：
  - 包含设备图标（电脑/手机/无线设备识别）、主机名、分配 IP、MAC 地址、连接频段（LAN / 2.4G Wi-Fi / 5G Wi-Fi）。
  - 信号强度指示条（Wi-Fi 设备显示 RSSI dBm 与格数）。
- 搜索与排序：支持按 IP/名称搜索，支持按信号强度或连接时长排序。

### 4.4 无线管理 (`WirelessScreen`)
- 2.4GHz / 5GHz 分频段独立展示卡片。
- 每卡片包含：SSID 名称、加密类型、当前信道、频宽（20/40/80/160MHz）、当前连接设备数。
- 操作按钮：一键切换启用/禁用、查看/修改 Wi-Fi 密码、一键复制 SSID 与密码。

### 4.5 工具箱 (`ToolsScreen`)
- **系统控制**：
  - 一键重启路由器（弹出 Material 3 确认对话框，带倒计时进度条）。
  - 一键释放内存（执行 drop_caches 命令）。
- **网络诊断**：
  - 内置 Ping 测试工具（输入 IP 或域名，返回往返时延 RTT 与丢包率）。

---

## 5. 验证与构建计划

1. **Gradle 工程构建配置完整性**：
   - 包含完整的 `build.gradle.kts`、`settings.gradle.kts`、`gradle-wrapper` 配置，符合 Google 官方 Android 标准项目规范。
2. **Ubus 通信层单元测试**：
   - 编写针对 JSON-RPC 序列化、反序列化以及 Token 拦截器的单元测试用例。
3. **Compose UI 预览与组件可维护性**：
   - 所有 UI 组件保持高内聚低耦合，支持 `@Preview` 独立预览与暗黑模式适配。
