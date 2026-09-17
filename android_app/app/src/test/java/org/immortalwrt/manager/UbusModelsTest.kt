package org.immortalwrt.manager

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.immortalwrt.manager.data.api.SystemInfoResult
import org.immortalwrt.manager.data.api.UbusRequest
import org.immortalwrt.manager.data.api.UbusResponse
import org.immortalwrt.manager.domain.model.ConnectedClient
import org.immortalwrt.manager.domain.model.ConnectionType
import org.immortalwrt.manager.domain.model.RealtimeTraffic
import org.immortalwrt.manager.domain.model.RouterOverview
import org.immortalwrt.manager.data.repository.RouterRepository
import org.immortalwrt.manager.ui.screens.clients.ClientFilter
import org.immortalwrt.manager.ui.screens.clients.ClientsUiState
import org.junit.Assert.*
import org.junit.Test

class UbusModelsTest {

    private val gson = Gson()

    @Test
    fun testUbusRequestSerialization() {
        val request = UbusRequest.create(
            sessionToken = "0123456789abcdef",
            module = "session",
            func = "login",
            args = mapOf("username" to "root", "password" to "password")
        )

        val json = gson.toJson(request)
        assertTrue(json.contains("\"jsonrpc\":\"2.0\""))
        assertTrue(json.contains("\"method\":\"call\""))
        assertTrue(json.contains("\"0123456789abcdef\""))
        assertTrue(json.contains("\"session\""))
        assertTrue(json.contains("\"login\""))
    }

    @Test
    fun testSystemInfoDeserialization() {
        val jsonString = """
            {
                "localtime": 1724220000,
                "uptime": 123456,
                "load": [6553, 3276, 1638],
                "memory": {
                    "total": 1073741824,
                    "free": 536870912,
                    "shared": 0,
                    "buffered": 10485760,
                    "cached": 209715200
                },
                "swap": {
                    "total": 536870912,
                    "free": 536870912
                }
            }
        """.trimIndent()

        val sysInfo = gson.fromJson(jsonString, SystemInfoResult::class.java)
        assertEquals(123456L, sysInfo.uptime)
        assertEquals(1073741824L, sysInfo.memory.total)
        assertEquals(536870912L, sysInfo.memory.free)

        // 真实可用内存 = free(512MB) + buffered(10MB) + cached(200MB) = 757071872
        val expectedAvailable = 536870912L + 10485760L + 209715200L
        val expectedUsed = 1073741824L - expectedAvailable
        val expectedPercent = (expectedUsed.toFloat() / 1073741824L.toFloat()) * 100f

        assertEquals(expectedAvailable, sysInfo.memory.realAvailable)
        assertEquals(expectedUsed, sysInfo.memory.used)
        assertEquals(expectedPercent, sysInfo.memory.usedPercentage, 0.1f)
    }

    @Test
    fun testTrafficSpeedFormatting() {
        val traffic = RealtimeTraffic(
            downloadSpeedBps = 25 * 1024 * 1024L, // 25 MB/s
            uploadSpeedBps = 512 * 1024L,        // 512 KB/s
            totalRxBytes = 1000000000L,
            totalTxBytes = 500000000L
        )

        assertEquals("25.00 MB/s", traffic.formattedDownloadSpeed)
        assertEquals("512.0 KB/s", traffic.formattedUploadSpeed)
    }

    @Test
    fun testUptimeFormatting() {
        val overview = RouterOverview(
            host = "10.10.10.1",
            modelName = "JDCloud AX1800 Pro",
            firmwareVersion = "ImmortalWrt 24.10",
            uptimeSeconds = 90060L, // 1 day, 1 hour, 1 min
            wanIpv4 = "192.168.1.5",
            cpuLoadPercentage = 12.5f,
            memoryTotalMb = 512L,
            memoryUsedMb = 180L,
            onlineClientsCount = 8
        )

        assertEquals("1天 1小时 1分", overview.formattedUptime)
    }

    @Test
    fun testClientFilterLogic() {
        val wiredOnline = ConnectedClient(
            hostname = "Desktop-PC",
            ipAddress = "192.168.1.100",
            macAddress = "aa:bb:cc:dd:ee:01",
            connectionType = ConnectionType.WIRED_LAN,
            isOnline = true
        )
        val wifiOnline = ConnectedClient(
            hostname = "iPhone 15",
            ipAddress = "192.168.1.101",
            macAddress = "aa:bb:cc:dd:ee:02",
            connectionType = ConnectionType.WIFI_5G,
            isOnline = true
        )
        val offlineWired = ConnectedClient(
            hostname = "Old-Printer",
            ipAddress = "192.168.1.200",
            macAddress = "aa:bb:cc:dd:ee:03",
            connectionType = ConnectionType.WIRED_LAN,
            isOnline = false
        )
        val offlineWifi = ConnectedClient(
            hostname = "iPad-Air",
            ipAddress = "192.168.1.201",
            macAddress = "aa:bb:cc:dd:ee:04",
            connectionType = ConnectionType.WIFI_2G,
            isOnline = false
        )

        val list = listOf(wiredOnline, wifiOnline, offlineWired, offlineWifi)

        val allState = ClientsUiState(clients = list, filter = ClientFilter.ALL)
        assertEquals(4, allState.filteredClients.size)
        assertEquals(2, allState.onlineCount)
        assertEquals(2, allState.offlineCount)

        val onlineState = ClientsUiState(clients = list, filter = ClientFilter.ONLINE_ONLY)
        assertEquals(2, onlineState.filteredClients.size)
        assertTrue(onlineState.filteredClients.all { it.isOnline })

        val offlineState = ClientsUiState(clients = list, filter = ClientFilter.OFFLINE_ONLY)
        assertEquals(2, offlineState.filteredClients.size)
        assertTrue(offlineState.filteredClients.all { !it.isOnline })

        val wiredState = ClientsUiState(clients = list, filter = ClientFilter.WIRED_ONLY)
        // 关键验证：有线 LAN 筛选中绝不能包含离线设备！只包含真实在线的有线设备
        assertEquals(1, wiredState.filteredClients.size)
        assertEquals("Desktop-PC", wiredState.filteredClients[0].hostname)
        assertTrue(wiredState.filteredClients[0].isOnline)

        val wifiState = ClientsUiState(clients = list, filter = ClientFilter.WIFI_ONLY)
        // 关键验证：Wi-Fi 筛选中只包含真实在线的无线设备
        assertEquals(1, wifiState.filteredClients.size)
        assertEquals("iPhone 15", wifiState.filteredClients[0].hostname)
        assertTrue(wifiState.filteredClients[0].isOnline)
    }

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

        // 3. 验证 parseCpuUsage 不会把带有 WiFi/温度的字符串存入 fullCpuUsageText
        val parsed = RouterRepository.parseCpuUsage(validTempInfo)
        assertNull(parsed.fullCpuUsageText) // 应当被安全过滤

        // 验证合法的 CPU 使用率文本解析
        val validCpuUsage = "CPU: 12.5% HWE: 5% ECM: 120 active"
        val parsedCpu = RouterRepository.parseCpuUsage(validCpuUsage)
        assertEquals(12.5f, parsedCpu.realCpuLoadPct ?: 0f, 0.01f)
        assertEquals("5%", parsedCpu.hweUsage)
        assertEquals("120 active", parsedCpu.ecmStats)
        assertEquals(validCpuUsage, parsedCpu.fullCpuUsageText)
    }

    @Test
    fun testClientDeviceClassification() {
        // 验证移动终端识别为 true
        assertTrue(RouterRepository.isMobileDevice("iPhone-14", "Apple, Inc."))
        assertTrue(RouterRepository.isMobileDevice("Galaxy-S23", "Samsung Electronics"))
        assertTrue(RouterRepository.isMobileDevice("Xiaomi-13-Pro", "Xiaomi Communications"))
        assertTrue(RouterRepository.isMobileDevice("HUAWEI-Mate-60", "Huawei Device Co., Ltd."))
        assertTrue(RouterRepository.isMobileDevice("iPad-Air", "Apple, Inc."))

        // 验证固定有线终端（电脑、NAS、电视盒、打印机）识别为 false
        assertFalse(RouterRepository.isMobileDevice("Desktop-PC", "Giga-Byte Technology"))
        assertFalse(RouterRepository.isMobileDevice("Synology-NAS", "Synology Incorporated"))
        assertFalse(RouterRepository.isMobileDevice("Apple-TV", "Apple, Inc."))
        assertFalse(RouterRepository.isMobileDevice("HP-LaserJet", "HP Inc."))
    }

    @Test
    fun testSmartLanOnlineDetectionLogic() {
        val desktopPc = ConnectedClient(
            hostname = "Desktop-PC",
            ipAddress = "192.168.1.100",
            macAddress = "aa:bb:cc:dd:ee:01",
            connectionType = ConnectionType.WIRED_LAN,
            vendor = "Giga-Byte Technology",
            isOnline = false
        )
        val iphone = ConnectedClient(
            hostname = "iPhone-14",
            ipAddress = "192.168.1.102",
            macAddress = "aa:bb:cc:dd:ee:02",
            connectionType = ConnectionType.WIRED_LAN,
            vendor = "Apple, Inc.",
            isOnline = false
        )
        val huawei = ConnectedClient(
            hostname = "HUAWEI-Mate-60",
            ipAddress = "192.168.1.103",
            macAddress = "aa:bb:cc:dd:ee:03",
            connectionType = ConnectionType.WIFI_5G,
            vendor = "Huawei Device Co., Ltd.",
            isOnline = false
        )

        val activeLeaseMacs = setOf("aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02")
        val onlineWifiMap = mapOf("aa:bb:cc:dd:ee:03" to ConnectionType.WIFI_5G)

        // 1. 验证对于有线固定设备（非移动终端），若处于活跃租约中（如在 activeLeaseMacs 中），判定为在线有线设备 (isOnline = true, connectionType = WIRED_LAN)
        val (pcOnline, pcConn) = RouterRepository.resolveClientStatus(
            client = desktopPc,
            onlineWifiMap = onlineWifiMap,
            activeLeaseMacs = activeLeaseMacs
        )
        assertTrue(pcOnline)
        assertEquals(ConnectionType.WIRED_LAN, pcConn)

        // 2. 验证对于移动终端（手机），若未在 onlineWifiMap 中，即使有历史租约记录，也正确判定为离线无线 (isOnline = false, connectionType = WIFI_5G)，绝不误标为有线 LAN 在线
        val (phoneOnline, phoneConn) = RouterRepository.resolveClientStatus(
            client = iphone,
            onlineWifiMap = onlineWifiMap,
            activeLeaseMacs = activeLeaseMacs
        )
        assertFalse(phoneOnline)
        assertEquals(ConnectionType.WIFI_5G, phoneConn)

        // 3. 验证处于 onlineWifiMap 中的终端，判定为在线无线 (isOnline = true)
        val (huaweiOnline, huaweiConn) = RouterRepository.resolveClientStatus(
            client = huawei,
            onlineWifiMap = onlineWifiMap,
            activeLeaseMacs = activeLeaseMacs
        )
        assertTrue(huaweiOnline)
        assertEquals(ConnectionType.WIFI_5G, huaweiConn)
    }
}
