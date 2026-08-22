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

data class WifiBandTemperature(
    val bandName: String,
    val radioDevice: String,
    val temperature: String
)

data class RouterOverview(
    val host: String,
    val modelName: String,
    val firmwareVersion: String,
    val uptimeSeconds: Long,
    val wanIpv4: String = "未连接",
    val wanIpv6: String = "未分配",
    val lanIp: String = "10.10.10.1",
    val cpuLoadPercentage: Float,
    val cpuLoadAverage: String = "0.00, 0.00, 0.00",
    val cpuUsageText: String? = null,
    val hweUsage: String? = null,
    val ecmStats: String? = null,
    val cpuTemperature: String? = null,
    val wifiTemperature: String? = null,
    val wifiBandTemperatures: List<WifiBandTemperature> = emptyList(),
    val hasWireless: Boolean = false,
    val memoryTotalMb: Long,
    val memoryUsedMb: Long,
    val memoryAvailableMb: Long = 0L,
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
    val txRateMbps: Float = 0f,
    val ipv6Address: String? = null,
    val vendor: String? = null,
    val isStaticLease: Boolean = false
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
    BAND_2_4G("2.4 GHz 基础频段", "2.4G"),
    BAND_5_2G("5.2 GHz 电竞频道 (160M 游戏特快)", "5.2G 电竞"),
    BAND_5_8G("5.8 GHz 扩展频段 (80M)", "5.8G"),
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
    val webRelativePath: String? = null,
    val webPort: Int? = null,
    val hasDetailConfig: Boolean = true
)

// 节点与插件详细配置
data class PasswallNode(
    val id: String,
    val remarks: String,
    val type: String = "xray",
    val address: String = "",
    val port: String = ""
)

data class PasswallConfig(
    val isEnabled: Boolean = true,
    val proxyMode: String = "chnroute", // chnroute, gfwlist, global, returnhome
    val tcpNode: String = "默认节点",
    val udpNode: String = "与TCP相同",
    val dnsMode: String = "dns2socks", // dns2socks, pdnsd, smartdns, xray_doh
    val remoteDns: String = "1.1.1.1",
    val chinadnsNg: Boolean = true,
    val nodes: List<PasswallNode> = emptyList()
)

data class OpenClashConfig(
    val isEnabled: Boolean = true,
    val operationMode: String = "fake-ip", // fake-ip, redir-host, tun
    val coreType: String = "Meta", // Meta, DEV, Premium
    val autoUpdateGeo: Boolean = true,
    val dashboardPort: Int = 9090,
    val secret: String = ""
)

data class MosdnsConfig(
    val isEnabled: Boolean = true,
    val listenPort: Int = 5335,
    val remoteDns: String = "tls://8.8.8.8",
    val localDns: String = "223.5.5.5",
    val concurrent: Boolean = true
)

data class SmartdnsConfig(
    val isEnabled: Boolean = true,
    val port: Int = 6053,
    val tcpServer: Boolean = true,
    val ipv6Server: Boolean = false,
    val autoSetDnsmasq: Boolean = true
)

data class GenericUciConfig(
    val configName: String,
    val sectionName: String,
    val options: Map<String, String> = emptyMap()
)

// 网页端 (LuCI) 对齐的 WAN/LAN/DHCP/系统设置模型
data class WanNetworkConfig(
    val proto: String = "dhcp", // pppoe, dhcp, static
    val username: String = "",
    val password: String = "",
    val ipaddr: String = "",
    val netmask: String = "255.255.255.0",
    val gateway: String = "",
    val dns: String = "",
    val ipv6: Boolean = true
)

data class LanNetworkConfig(
    val ipaddr: String = "10.10.10.1",
    val netmask: String = "255.255.255.0",
    val gateway: String = "",
    val dns: String = "",
    val ipv6AssignmentLength: String = "64"
)

data class DhcpServerConfig(
    val isEnabled: Boolean = true,
    val start: Int = 100,
    val limit: Int = 150,
    val leasetime: String = "12h",
    val dnsServers: String = "" // 自定义宣告 DNS 如 6,10.10.10.1
)

data class SystemSettings(
    val hostname: String = "ImmortalWrt",
    val timezone: String = "CST-8",
    val zonename: String = "Asia/Shanghai"
)

data class FirewallAdvancedSettings(
    val fullconeNat: Boolean = true,
    val synFlood: Boolean = true
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


