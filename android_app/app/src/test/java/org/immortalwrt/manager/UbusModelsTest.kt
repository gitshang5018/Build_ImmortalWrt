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
        assertEquals(50f, sysInfo.memory.usedPercentage, 0.1f)
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
}
