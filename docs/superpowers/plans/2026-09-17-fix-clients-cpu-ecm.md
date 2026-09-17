# Android APP 客户端在线判定与 CPU 仪表盘优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复 Android APP 有线 LAN 终端离线误判、彻底剔除 CPU 卡片中的 Wi-Fi 温度残留、展示 ECM 硬件加速状态以及修复 CPU 温度解析精度。

**架构：** 在 `RouterRepository.kt` 中重构终端分类算法（结合无线关联列表、DHCP 活跃租期与移动设备指纹）并规范温度/CPU/ECM 解析边界；在 `DashboardScreen.kt` 中完善 CPU 卡片副标题分支（支持独立 ECM 展示）；在 `UbusModelsTest.kt` 中编写自动化单元测试。

**技术栈：** Kotlin, Jetpack Compose, Android Coroutines, JUnit 4, Gson

---

### 任务 1：修复 CPU 温度解析与 CPU/ECM 负载提取 (`RouterRepository.kt`)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt:107-205`
- 测试：`android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt`

- [ ] **步骤 1：编写单元测试验证温度提取与 CPU/ECM 解析**

在 `android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt` 中添加单元测试：

```kotlin
    @Test
    fun testCpuAndTemperatureParsingLogic() {
        // 1. 验证非温度字符串（如 board/system info 中的型号与版本）不被解析为 CPU 温度
        val fakeBoardInfo = """{"model":"JDCloud AX6600","release":{"description":"ImmortalWrt 24.10.0-rc1"}}"""
        val (cpu1, wifi1) = RouterRepository.extractTemperaturesFromText(fakeBoardInfo)
        assertNull(cpu1)
        assertTrue(wifi1.isEmpty())

        // 2. 验证标准温度字符串能正确提取 CPU 和 WiFi 温度
        val validTempInfo = """{"tempinfo":"CPU: 48.0°C, WiFi: 51.0°C 53.0°C 49.0°C"}"""
        val (cpu2, wifi2) = RouterRepository.extractTemperaturesFromText(validTempInfo)
        assertEquals(48, cpu2)
        assertEquals(listOf(51, 53, 49), wifi2)

        // 3. 验证 parseCpuUsageString 不会把带有 WiFi/温度的字符串存入 fullCpuUsageText
        val parsed = RouterRepository.parseCpuUsage(validTempInfo)
        assertNull(parsed.fullCpuUsageText) // 应当被安全过滤
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行测试命令：
```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testCpuAndTemperatureParsingLogic"
```
预期：FAIL，提示 `extractTemperaturesFromText` 仍匹配了 66 或 24，或者伴生函数尚未暴露为测试可见。

- [ ] **步骤 3：在 `RouterRepository.kt` 中实现严格的温度与 CPU/ECM 解析**

在 `RouterRepository.kt` 中：
1. 将 `extractTemperaturesFromText` 与 `parseCpuUsage` 抽为 companion object 工具函数便于测试与解耦。
2. `parseCpuUsage` 中增加安全判断：若包含 `WiFi:`、`°C`、`℃`、`tempinfo`，则直接忽略并不设置 `fullCpuUsageText`；提取 `ECM:\s*([^\r\n]+)` 与 `HWE:\s*([^\r\n]+)`。
3. `extractTemperaturesFromText` 增加严格校验：必须包含 `CPU:`、`Core`、`SoC` 等关键词或紧跟 `°C`/`℃`，且温度在 `25..105` 范围内；严禁盲目匹配 2 位数字。
4. 在 `getRouterOverview` 中，温度提取只针对 `getTempInfo`、`getCPUInfo` 和 `autocore`，禁止对 `getBoardInfo` 和 `getSystemInfo` 提取温度。

```kotlin
    companion object {
        data class CpuUsageResult(
            val realCpuLoadPct: Float? = null,
            val fullCpuUsageText: String? = null,
            val hweUsage: String? = null,
            val ecmStats: String? = null
        )

        fun parseCpuUsage(str: String): CpuUsageResult {
            val lower = str.lowercase()
            // 若包含明显的温度文本，绝不作为 CPU 使用率文本保存
            val isTempText = lower.contains("wifi") || lower.contains("°c") || lower.contains("℃") || lower.contains("tempinfo")
            val fullText = if (isTempText) null else str.trim().takeIf { it.isNotBlank() }

            var cpuLoad: Float? = null
            val cpuMatch = Regex("""CPU:\s*([0-9]+(?:\.[0-9]+)?)\s*%""", RegexOption.IGNORE_CASE).find(str)
            if (cpuMatch != null) {
                cpuLoad = cpuMatch.groupValues[1].toFloatOrNull()
            }

            var hweUsage: String? = null
            val hweMatch = Regex("""HWE:\s*([0-9]+(?:\.[0-9]+)?%?)""", RegexOption.IGNORE_CASE).find(str)
            if (hweMatch != null) {
                hweUsage = hweMatch.groupValues[1].let { if (it.endsWith("%")) it else "$it%" }
            }

            var ecmStats: String? = null
            val ecmMatch = Regex("""ECM:\s*([^\r\n]+)""", RegexOption.IGNORE_CASE).find(str)
            if (ecmMatch != null) {
                ecmStats = ecmMatch.groupValues[1].trim()
            }

            return CpuUsageResult(
                realCpuLoadPct = cpuLoad,
                fullCpuUsageText = fullText,
                hweUsage = hweUsage,
                ecmStats = ecmStats
            )
        }

        fun extractTemperaturesFromText(text: String): Pair<Int?, List<Int>> {
            var cpu: Int? = null
            val wifis = mutableListOf<Int>()

            val cpuMatch = Regex("""(?:CPU|SoC|Core(?:\s*[0-9]+)?)[：:\s]+([0-9]+(?:\.[0-9]+)?)\s*°?C?""", RegexOption.IGNORE_CASE).find(text)
            if (cpuMatch != null) {
                val v = cpuMatch.groupValues[1].toFloatOrNull()?.toInt()
                if (v != null && v in 25..105) {
                    cpu = v
                }
            }

            val wifiSection = text.substringAfter("WiFi:", "").ifBlank { text.substringAfter("wifi:", "") }
            if (wifiSection.isNotBlank()) {
                Regex("""([0-9]+(?:\.[0-9]+)?)\s*°?C?""").findAll(wifiSection).forEach { m ->
                    m.groupValues[1].toFloatOrNull()?.toInt()?.let {
                        if (it in 25..105) wifis.add(it)
                    }
                }
            }

            return Pair(cpu, wifis)
        }
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行测试命令：
```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testCpuAndTemperatureParsingLogic"
```
预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt; git commit -m "fix(app): refine cpu temp parsing and prevent wifi temp leakage into cpu card"
```

---

### 任务 2：实现智能有线 LAN 终端在线判定 (`RouterRepository.kt`)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt:431-730`
- 测试：`android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt`

- [ ] **步骤 1：编写有线/无线终端智能分类的单元测试**

在 `android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt` 中添加：

```kotlin
    @Test
    fun testClientDeviceClassification() {
        // 移动终端识别为 true
        assertTrue(RouterRepository.isMobileDevice("iPhone-14", "Apple, Inc."))
        assertTrue(RouterRepository.isMobileDevice("Galaxy-S23", "Samsung Electronics"))
        assertTrue(RouterRepository.isMobileDevice("Xiaomi-13-Pro", "Xiaomi Communications"))
        assertTrue(RouterRepository.isMobileDevice("HUAWEI-Mate-60", "Huawei Device Co., Ltd."))

        // 固定有线终端（电脑、NAS、电视盒、打印机）识别为 false
        assertFalse(RouterRepository.isMobileDevice("Desktop-PC", "Giga-Byte Technology"))
        assertFalse(RouterRepository.isMobileDevice("Synology-NAS", "Synology Incorporated"))
        assertFalse(RouterRepository.isMobileDevice("Apple-TV", "Apple, Inc."))
        assertFalse(RouterRepository.isMobileDevice("HP-LaserJet", "HP Inc."))
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行测试命令：
```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testClientDeviceClassification"
```
预期：FAIL，`isMobileDevice` 尚未定义。

- [ ] **步骤 3：在 `RouterRepository.kt` 中实现 `isMobileDevice` 与方案 A 在线判定策略**

在 `RouterRepository.kt` 的 companion object 中定义 `isMobileDevice`：
```kotlin
        fun isMobileDevice(hostname: String, vendor: String): Boolean {
            val name = hostname.lowercase()
            val ven = vendor.lowercase()
            val mobileKeywords = listOf(
                "iphone", "ipad", "ipod", "android", "galaxy", "xiaomi", "redmi",
                "huawei", "honor", "oppo", "vivo", "oneplus", "meizu", "pixel",
                "realme", "iqoo", "phone", "mobile", "pad", "tab"
            )
            // 排除 Apple TV / PC 等非移动设备
            if (name.contains("apple-tv") || name.contains("appletv")) return false
            return mobileKeywords.any { name.contains(it) || ven.contains(it) }
        }
```

在 `getConnectedClients()` 中采集活跃租约与静态绑定：
1. 记录 `activeLeaseMacs`（在 `getDHCPLeases` / `/tmp/dhcp.leases` 中 `expires > 0` 的设备）。
2. 记录 `staticLeaseMacs`（从 `getStaticDhcpLeases` 读取的设备）。
3. 判定最终终端在线与类型：
```kotlin
            val resultList = clientMap.values.map { client ->
                val mac = client.macAddress.lowercase()
                val isWifi = onlineWifiMap.containsKey(mac)
                val isMobile = isMobileDevice(client.hostname, client.vendor)
                val hasActiveLease = activeLeaseMacs.contains(mac) || staticLeaseMacs.contains(mac) || onlineLanMacs.contains(mac)

                val (isOnline, connType) = when {
                    isWifi -> Pair(true, onlineWifiMap[mac] ?: ConnectionType.WIFI_5G)
                    isMobile -> Pair(false, ConnectionType.WIFI_5G) // 移动终端未在 WiFi 关联列表中，标记为离线无线
                    hasActiveLease -> Pair(true, ConnectionType.WIRED_LAN) // 固定设备处于活跃租期内，判定为在线有线
                    else -> Pair(false, client.connectionType)
                }

                client.copy(
                    isOnline = isOnline,
                    connectionType = connType
                )
            }.sortedWith(
                compareByDescending<ConnectedClient> { it.isOnline }
                    .thenBy { it.displayName }
            )
```

- [ ] **步骤 4：运行测试验证通过**

运行测试命令：
```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testClientDeviceClassification"
```
预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt; git commit -m "fix(app): implement smart wired LAN client online detection"
```

---

### 任务 3：优化 CPU 卡片副标题并展示 ECM 硬件加速状态 (`DashboardScreen.kt`)

**文件：**
- 修改：`android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt:175-195`

- [ ] **步骤 1：修改 `DashboardScreen.kt` 中的 `cpuSubtitle` 逻辑**

在 `DashboardScreen.kt` 中：
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

- [ ] **步骤 2：全量运行所有单元测试**

运行测试命令：
```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest"
```
预期：BUILD SUCCESSFUL，所有单元测试 100% 通过。

- [ ] **步骤 3：Commit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt; git commit -m "fix(app): show ECM stats and clean CPU card subtitle in Dashboard"
```

---

### 任务 4：推送到 Git 远端并触发 CI 构建

**文件：**
- 远端同步

- [ ] **步骤 1：推送所有 commit 至 GitHub main 分支**

运行命令：
```powershell
git push origin main
```
预期：Everything up-to-date / Commit 成功推送。
