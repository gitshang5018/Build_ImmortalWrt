package org.immortalwrt.manager

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.immortalwrt.manager.data.api.SystemInfoResult
import org.immortalwrt.manager.data.api.UbusRequest
import org.immortalwrt.manager.data.api.UbusResponse
import org.immortalwrt.manager.domain.model.RealtimeTraffic
import org.immortalwrt.manager.domain.model.RouterOverview
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
            uptimeSeconds = 90060, // 1 day, 1 hour, 1 min
            wanIp = "192.168.1.5",
            cpuLoadPercentage = 12.5f,
            memoryTotalMb = 512,
            memoryUsedMb = 180,
            onlineClientsCount = 8
        )

        assertEquals("1天 1小时 1分", overview.formattedUptime)
    }
}
