# Android APP 客户端在线判定与 CPU 仪表盘优化设计规格

## 1. 概述与背景
在 ImmortalWrt Manager Android APP 的使用场景中，用户反馈了以下 4 项体验与显示问题：
1. **LAN 连接的设备显示为离线**：由于 OpenWrt 默认 rpcd ACL 策略拦截了普通 ubus 用户调用 `file read /proc/net/arp` 与 `file exec`，导致有线活跃 MAC 集合为空，所有非 Wi-Fi 终端被判定为离线。
2. **CPU 卡片显示了 Wi-Fi 温度**：`parseCpuUsageString` 误读取了 `getTempInfo` 的完整文本并作为 `cpuUsageText` 存储，而在 `DashboardScreen` 中被渲染到 CPU 卡片副标题中。
3. **CPU 温度显示不准确**：`extractTemperaturesFromText` 盲目扫描了 `getBoardInfo` 与 `getSystemInfo` 中的任意 2 位数字，误将固件版本号 `24` 或型号 `66`（AX6600）提取为 CPU 核心温度。
4. **ECM 硬件加速负载未显示在 CPU 卡片中**：`DashboardScreen` 的副标题分支判断仅处理了 `hwe != null && ecm != null`，缺少高通平台常见的 `ecm != null && hwe == null` 单独 ECM 分支。

---

## 2. 详细设计与技术规格

### 2.1 有线 LAN 终端在线判定（方案 A）
- **数据源采集**：
  - `iwinfo assoclist`：采集真实在线的无线终端 `onlineWifiMap`。
  - `getDHCPLeases` / `/tmp/dhcp.leases`：采集当前租约列表，提取 `expires > 0` 的活跃租约 MAC 集合 `activeLeaseMacs`。
  - `getStaticDhcpLeases`：提取固定分配的静态租约 MAC 集合 `staticLeaseMacs`。
  - `getHostHints` / `/proc/net/arp`：提取已知主机名与网络提示。
- **设备类型与在线决策机制**：
  - 定义 `isMobileDevice(hostname: String, vendor: String): Boolean`，根据特征关键词识别手机与平板终端（如 `iphone`, `ipad`, `android`, `xiaomi`, `huawei`, `honor`, `oppo`, `vivo`, `meizu`, `oneplus`, `galaxy`, `pixel`, `redmi` 等）。
  - **判定逻辑**：
    1. 若 MAC 存在于 `onlineWifiMap` 中 -> `isOnline = true`，连接类型为对应频段（2.4G / 5G / 5.2G），记录协商速率与信号强度。
    2. 若 MAC 不在 `onlineWifiMap` 中：
       - 若 `isMobileDevice` 为真 -> `isOnline = false`，连接类型标识为无线（WIFI），绝不误标为有线 LAN。
       - 若 `isMobileDevice` 为假，且 MAC 在 `activeLeaseMacs` 或 `staticLeaseMacs` 中 -> `isOnline = true`，连接类型为 `ConnectionType.WIRED_LAN`。
       - 其他无活跃记录或租约已过期的设备 -> `isOnline = false`。

### 2.2 CPU 仪表盘卡片与 ECM 状态优化
- **CPU 使用率解析过滤**：
  - 在 `RouterRepository.kt` 的 `parseCpuUsageString` 中，增加前置安全校验：若文本包含 `WiFi:`、`°C`、`℃`、`tempinfo`，则视为温度字符串，直接跳过，严禁赋值给 `fullCpuUsageText`。
  - 增强 `ecmStats` 提取正则：支持匹配 `ECM:\s*([^\r\n]+)` 及高通 NSS 连接统计。
- **UI 副标题展示分支优化 (`DashboardScreen.kt`)**：
  ```kotlin
  val hwe = state.overview?.hweUsage
  val ecm = state.overview?.ecmStats
  val loadAvg = state.overview?.cpuLoadAverage

  val cpuSubtitle = when {
      hwe != null && ecm != null -> "HWE: $hwe · ECM: $ecm"
      ecm != null -> "ECM: $ecm · 负载: ${loadAvg ?: "--"}"
      hwe != null -> "HWE: $hwe · 负载: ${loadAvg ?: "--"}"
      else -> "平均负载: ${loadAvg ?: "--"}"
  }
  ```
  彻底剔除从 `cpuUsageText` 泄露的 Wi-Fi 温度，并确保高通平台 ECM 负载正常展示。

### 2.3 CPU 温度精确提取与防御设计
- **调用源隔离**：
  - 仅对专用温度接口（`getTempInfo`、`getCPUInfo`、`/sys/class/thermal/thermal_zone*`）执行温度解析。
  - 严禁对 `getBoardInfo` 和 `getSystemInfo` 调用 `extractTemperaturesFromText`。
- **解析正则增强**：
  - 提取 CPU 温度必须具备明确的语义前缀（如 `CPU:\s*([0-9]+(?:\.[0-9]+)?)\s*°?C?`、`SoC:\s*([0-9]+)` 或 `Core\s*[0-9]+:\s*([0-9]+)`）。
  - 温度数值范围必须处于 `25..105` 摄氏度之间。
  - 禁止在无温度上下文时无差别抓取 2 位数字，避免将系统版本号（`24`）或型号数字（`66`）误识别为温度。

---

## 3. 测试与验证计划
1. **单元测试 (`UbusModelsTest.kt`)**：
   - 验证 `extractTemperaturesFromText` 对包含系统版本、型号字符串的非温度数据不会提取出虚假温度。
   - 验证 `parseCpuUsageString` 面对 `getTempInfo` 格式时不会污染 `fullCpuUsageText`。
   - 验证有线固定设备（PC、NAS、TV）在活跃租期内正确判定为 `isOnline = true`，移动终端在非 Wi-Fi 状态下判定为 `isOnline = false`。
2. **构建验证**：
   - 运行单元测试验证通过。
