package org.immortalwrt.manager.domain.model

import java.util.UUID

data class RouterCredentials(
    val host: String = "10.10.10.1",
    val port: Int = 80,
    val username: String = "root",
    val password: String = "",
    val useHttps: Boolean = false
) {
    val baseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            return "$scheme://$host:$port/"
        }
}

data class RouterNode(
    val id: String = UUID.randomUUID().toString(),
    val alias: String = "默认路由器",
    val credentials: RouterCredentials = RouterCredentials()
)

data class RouterOverview(
    val host: String,
    val modelName: String,
    val firmwareVersion: String,
    val uptimeSeconds: Long,
    val wanIp: String,
    val cpuLoadPercentage: Float,
    val memoryTotalMb: Long,
    val memoryUsedMb: Long,
    val onlineClientsCount: Int
) {
    val formattedUptime: String
        get() {
            val days = uptimeSeconds / 86400
            val hours = (uptimeSeconds % 86400) / 3600
            val minutes = (uptimeSeconds % 3600) / 60
            return if (days > 0) {
                "${days}天 ${hours}小时 ${minutes}分"
            } else {
                "${hours}小时 ${minutes}分"
            }
        }
}

data class RealtimeTraffic(
    val downloadSpeedBps: Long,
    val uploadSpeedBps: Long,
    val totalRxBytes: Long,
    val totalTxBytes: Long
) {
    fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 * 1024 -> String.format("%.2f GB/s", bytesPerSec / (1024.0 * 1024.0 * 1024.0))
            bytesPerSec >= 1024 * 1024 -> String.format("%.2f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    val formattedDownloadSpeed: String get() = formatSpeed(downloadSpeedBps)
    val formattedUploadSpeed: String get() = formatSpeed(uploadSpeedBps)
}

data class ConnectedClient(
    val hostname: String,
    val ipAddress: String,
    val macAddress: String,
    val connectionType: ConnectionType,
    val signalDbm: Int = 0,
    val customAlias: String? = null,
    val isBlocked: Boolean = false,
    val rxRateMbps: Float = 0f,
    val txRateMbps: Float = 0f
) {
    val displayName: String get() = customAlias?.takeIf { it.isNotBlank() } ?: hostname
}

enum class ConnectionType {
    WIRED_LAN,
    WIFI_2G,
    WIFI_5G,
    WIFI_6G
}

enum class WifiBandType(val displayName: String, val badgeText: String) {
    BAND_2_4G("2.4 GHz", "2.4G"),
    BAND_5_2G("5.2 GHz (5G-1)", "5G-1"),
    BAND_5_8G("5.8 GHz (5G-2 电竞)", "5G-2"),
    BAND_6G("6 GHz (Wi-Fi 6E/7)", "6G")
}

data class WifiInterfaceConfig(
    val deviceRadio: String, // radio0, radio1, radio2
    val bandType: WifiBandType = WifiBandType.BAND_5_2G,
    val bandName: String,
    val ssid: String,
    val encryption: String,
    val key: String,
    val channel: String,
    val htmode: String,
    val isEnabled: Boolean,
    val txPower: String,
    val isHidden: Boolean = false
) {
    val qrCodeString: String
        get() {
            val encType = when {
                encryption.contains("sae", ignoreCase = true) -> "WPA"
                encryption.contains("psk", ignoreCase = true) -> "WPA"
                encryption.contains("none", ignoreCase = true) -> "nopass"
                else -> "WPA"
            }
            return "WIFI:S:$ssid;T:$encType;P:$key;H:${if (isHidden) "true" else "false"};;"
        }
}

enum class PluginCategory(val title: String) {
    PROXY("代理与加速"),
    DNS_ENHANCE("DNS 优选与分流"),
    REMOTE_ACCESS("穿透与异地组网"),
    SYSTEM_TOOL("实用工具与容器")
}

data class PluginServiceInfo(
    val id: String,
    val name: String,
    val category: PluginCategory,
    val description: String,
    val serviceName: String,
    val isRunning: Boolean,
    val webRelativePath: String? = null
)

data class FirewallRedirectRule(
    val id: String,
    val name: String,
    val proto: String,
    val srcPort: String,
    val destIp: String,
    val destPort: String,
    val isEnabled: Boolean = true
)

data class StaticDhcpLease(
    val id: String,
    val hostname: String,
    val mac: String,
    val ip: String
)

data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String
)
