package org.immortalwrt.manager.domain.model

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
    val rxRateMbps: Float = 0f,
    val txRateMbps: Float = 0f
)

enum class ConnectionType {
    WIRED_LAN,
    WIFI_2G,
    WIFI_5G,
    WIFI_6G
}

data class WifiInterfaceConfig(
    val deviceRadio: String, // e.g. radio0, radio1
    val bandName: String,   // e.g. "2.4 GHz", "5 GHz"
    val ssid: String,
    val encryption: String,
    val key: String,
    val channel: String,
    val htmode: String,
    val isEnabled: Boolean,
    val txPower: String
)
