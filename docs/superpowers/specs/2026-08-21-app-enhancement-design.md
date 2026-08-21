# ImmortalWrt 路由管家 (Android 原生 App) 全方位完善设计规格说明书

## 1. 概述与设计目标

本设计规格旨在将 **ImmortalWrt 路由管家** 打造为一个成熟、专业、高质感、开箱即用的原生 Android 移动端路由器管理工具。应用基于 **Kotlin + Jetpack Compose (Material 3)** 响应式架构，直接对接 OpenWrt / ImmortalWrt 官方原生的 **Ubus JSON-RPC** API，无需在路由器端安装任何额外插件即可稳定运行。

本次全方位完善重点涵盖：
1. **5 标签页专业导航架构**：概览 (Dashboard)、终端 (Clients)、无线 (Wireless)、工具箱与插件 (Tools & Plugins)、设置 (Settings)。
2. **3 频 Wi-Fi 深度支持**：自动适配 2.4GHz、5.2GHz (5G-1)、5.8GHz (5G-2 电竞频段) 与 6GHz 频段独立参数调优及 Wi-Fi 二维码分享。
3. **常用插件服务中枢 (Plugins Hub)**：覆盖 OpenClash、PassWall、MosDNS、SmartDNS、Lucky、DDNS-Go、AdGuardHome、Tailscale、Docker 等常用插件的服务状态监控、启停重启与核心配置管理。
4. **实时网速平滑贝塞尔波形图**：高频低耗采样，动态呈现最近 30 秒流量速率波动。
5. **高级网络与运维工具**：端口转发规则、DHCP 静态绑定、系统实时日志 (Logread)、网络诊断三剑客 (Ping/DNS/Trace)。
6. **多路由器节点管理**：支持保存多个路由器节点配置并进行秒级平滑切换。

---

## 2. 系统整体架构设计

```
                               ┌─────────────────────────────────────────┐
                               │       Jetpack Compose UI (M3)           │
                               │  (Theme, Navigation, StateFlow Bindings) │
                               └────────────────────┬────────────────────┘
                                                    │
                 ┌──────────────────────────────────┴──────────────────────────────────┐
                 │                                                                     │
        ┌────────┴────────┐                                                   ┌────────┴────────┐
        │   ViewModels    │ (Dashboard, Clients, Wireless, Tools, Settings)    │ Local DataStore │
        └────────┬────────┘                                                   └────────┬────────┘
                 │                                                                     │
                 └──────────────────────────────────┬──────────────────────────────────┘
                                                    │
                               ┌────────────────────┴────────────────────┐
                               │           RouterRepository              │
                               │   (Domain Models & Business Logic)      │
                               └────────────────────┬────────────────────┘
                                                    │
                               ┌────────────────────┴────────────────────┐
                               │              UbusClient                 │
                               │   (Token Auth, Auto-Retry, SSL Config)  │
                               └────────────────────┬────────────────────┘
                                                    │  JSON-RPC 2.0 (HTTP/HTTPS)
                                                    ▼
                               ┌─────────────────────────────────────────┐
                               │      OpenWrt / ImmortalWrt /ubus        │
                               └─────────────────────────────────────────┘
```

---

## 3. 功能模块详细规格

### 3.1 📊 仪表盘模块 (Dashboard)
- **实时流量波形图**：
  - 前台采样周期 1000ms，维护 30 点环形缓冲区。
  - 使用 Jetpack Compose `Canvas` 绘制贝塞尔曲线，双色区分下载（Primary Blue）与上传（Secondary Cyan），并带半透明渐变区域填充。
- **系统硬件资源仪表**：
  - CPU 负载百分比卡片，依据数值动态呈现颜色：< 60% 正常绿，60%~85% 提示橙，> 85% 报警红。
  - 内存占用卡片（已用 MB / 总量 MB 及百分比条）。
  - 在线终端计数与系统开机运行时长。
- **网关信息卡片**：
  - 路由器型号名称、固件/内核版本（如 ImmortalWrt 24.10 / Kernel 6.6.x）、WAN IPv4/IPv6 地址、局域网网关 IP。
- **快捷控制**：
  - 一键释放内核 PageCache 与 Slab 缓存 (`sync && echo 3 > /proc/sys/vm/drop_caches`)。
  - 平稳软重启（带防误触确认对话框）。

### 3.2 📱 终端管理模块 (Clients)
- **多源数据融合**：
  - 结合 `luci-rpc getDHCPLeases`（IP、MAC、主机名）与各频段 `iwinfo assoclist`（MAC、信号强度 dBm、协商速率），精准匹配终端。
- **设备识别与展示**：
  - 根据 MAC 地址 OUI 数据库及 Hostname 关键字识别厂商与设备类型图标（苹果、安卓、Windows、Linux、IoT 设备、路由器等）。
  - 标签展示：有线 LAN、2.4G Wi-Fi、5.2G Wi-Fi、5.8G Wi-Fi。
- **搜索与过滤**：
  - 实时关键字过滤（设备名 / IP / MAC），分类筛选 Chips（全部 / 无线设备 / 有线设备）。
- **设备管理操作抽屉**：
  - 自定义设备别名备注重命名（本地持久化或写入 `/etc/config/dhcp`）。
  - 静态 DHCP IP 绑定（一键将动态分配的 IP 固化为静态绑定并下发 commit）。
  - 访问控制/拉黑开关。

### 3.3 📶 3 频无线管理模块 (Wireless)
- **多频段自适应解析**：
  - 扫描 `wireless` 中定义的所有 radio（`radio0`, `radio1`, `radio2` ...）。
  - 根据信道和频段特征智能归类：
    - **2.4 GHz 频段**：信道 1~13，频宽 HE20 / HE40。
    - **5.2 GHz 低频段 (5G-1)**：信道 36~64，频宽 HE80 / HE160。
    - **5.8 GHz 高频段 / 电竞专属频段 (5G-2)**：信道 149~165，频宽 HE80 / HE160。
    - **6 GHz 频段**（Wi-Fi 6E/7 支持）。
- **参数调优与修改**：
  - 独立修改 SSID、无线密码（明文/掩码切换与复制）、加密方式 (WPA2-PSK / WPA3-SAE / 混合模式)、信道、频宽、发射功率。
  - 隐藏 SSID 开关、频段开启/关闭开关。
- **Wi-Fi 扫码即连二维码**：
  - 针对每个频段生成标准的 Wi-Fi 连接 QR 码格式字符串（`WIFI:S:<SSID>;T:<TYPE>;P:<PASSWORD>;H:<HIDDEN>;;`），在 Compose 界面上直接渲染二维码供手机扫码连接。

### 3.4 🧩 实用工具与常用插件中枢 (Tools & Plugins)
- **常用插件服务状态看板与管控**：
  - 实时检测常用插件状态（运行中 🟢 / 已停止 ⚪）：
    - 科学上网与代理：`OpenClash` (`openclash`), `PassWall` (`passwall`), `HomeProxy` (`homeproxy`), `SSR-Plus` (`shadowsocksr`), `Nikki/Mihomo` (`nikki`/`mihomo`)
    - DNS 增强：`MosDNS` (`mosdns`), `SmartDNS` (`smartdns`), `AdGuardHome` (`adguardhome`)
    - 穿透与运维：`Lucky` (`lucky`), `DDNS-Go` (`ddns-go`), `Tailscale` (`tailscale`), `EasyTier` (`easytier`)
    - 容器与存储：`Docker` (`dockerd`), `AList` (`alist`), `QuickFile` (`quickfile`)
  - 操作支持：一键启动、停止、重启各服务，一键重载配置。
  - WebUI 快捷直达：点击一键在手机浏览器或内置 WebView 中打开该插件的管理后台。
- **实时系统日志查看器 (Logread)**：
  - 获取系统实时内核与服务运行日志。
  - 支持日志级别过滤（All, Info, Notice, Warn, Error）与文本搜索。
- **网络诊断工具箱**：
  - Ping 延迟测试（指定目标 host 与发包次数，实时输出响应时延）。
  - DNS 解析探测（测试各 DNS 服务器解析返回记录与耗时）。
  - Traceroute 路由追踪测试。
- **系统备份**：
  - 提供配置备份触发与说明。

### 3.5 ⚙️ 设置与高级网络模块 (Settings)
- **多路由器节点管理**：
  - 本地持久化保存多个路由器节点（名称、Host、端口、HTTPS、用户名、密码）。
  - 支持快捷切换活跃节点、新增节点、编辑与删除。
- **高级防火墙端口转发**：
  - 查询当前 `firewall` 中的端口映射规则（源端口、目标内网 IP、目标内网端口、协议 TCP/UDP）。
  - 支持新增端口转发规则与一键删除规则。
- **外观与系统偏好**：
  - 深色模式切换（跟随系统 / 强制深色 / 强制浅色）。
  - Material You 动态取色开关。
  - 安全登出当前路由器。

---

## 4. Ubus JSON-RPC 接口与数据交互设计

| 业务场景 | Ubus Module | Ubus Function | 参数 / 关键字段 | 作用说明 |
|---|---|---|---|---|
| 用户鉴权 | `session` | `login` | `username`, `password` | 验证身份并获取 `ubus_rpc_session` |
| 系统概览 | `system` | `info` | 无 | 获取 uptime, memory (total/free/cached), load |
| 硬件信息 | `system` | `board` | 无 | 获取 model, release (description, version) |
| 实时速率 | `network.device` | `status` | `name: "br-lan"` / `"eth0"` | 获取 rx_bytes, tx_bytes 统计 |
| DHCP 租约 | `luci-rpc` | `getDHCPLeases` | 无 | 获取 IP、MAC、Hostname 列表 |
| 无线关联 | `iwinfo` | `assoclist` | `device: "phy0-ap0"` 等 | 获取已连接无线客户端的 MAC、信号 dBm |
| 3频无线配置 | `uci` | `get` | `config: "wireless"` | 读取所有 radio 与 wifi-iface 配置 |
| 无线配置更新 | `uci` | `set`, `commit` | `config: "wireless"` | 批量更新 SSID, key, channel 等 |
| 端口转发规则 | `uci` | `get` / `set` | `config: "firewall"` | 读取与修改 `@redirect` 规则 |
| 插件服务状态 | `service` | `list` | 无 | 查询各服务运行状态与 PID |
| 插件启停/重启 | `file` | `exec` | `command: "/etc/init.d/xxx"`, `params: ["restart"]` | 控制插件守护进程 |
| 实时系统日志 | `file` | `exec` / `log` | `command: "logread"`, `params: ["-l", "100"]` | 读取最新系统日志 |
| 网络 Ping 诊断 | `file` | `exec` | `command: "ping"`, `params: ["-c", "4", host]` | 执行诊断测速 |

---

## 5. 错误处理与容错机制

1. **Token 自动续期与重试**：
   - 当 `UbusClient.callRaw` 捕获响应结果码为 `-1` (Permission Denied / Token Expired) 时，自动利用内存中保存的 `RouterCredentials` 静默重新调用 `session.login`。
   - 登录成功后自动更新 Session Token 并立即重试原请求，向业务 ViewModel 屏蔽鉴权过期细节。
2. **HTTPS 自签名证书支持**：
   - 内置 `UnsafeTrustManager` 配合 OkHttpClient SSLSocketFactory，消除 Android 对局域网自签名 SSL 证书的安全阻断。
3. **分级生命周期轮询**：
   - 仪表盘 1 秒高频轮询绑定 `LifecycleOwner`，页面不可见或退入后台时自动挂起协程 Job，返回前台时自动恢复。
4. **统一 UI 错误与空状态反馈**：
   - 各 Screen 统一处理 `isLoading`、`error` 与 `empty` 状态，提供重试按钮与 Material 3 Snackbar 提示。

---

## 6. 测试与验证计划

1. **单元测试与编译构建验证**：
   - 运行 `./gradlew assembleDebug` 确保全量 Kotlin/Compose 代码编译通过无警告。
2. **数据解析与模型验证**：
   - 针对 2.4G/5.2G/5.8G 多 radio JSON 进行反序列化与解析测试。
   - 针对各种插件服务状态列表和 DHCP leases 解析测试。
3. **UI 交互与主题渲染验证**：
   - 浅色/深色主题下 5 标签页、贝塞尔波形图、二维码组件、各种管理弹窗渲染正常。
