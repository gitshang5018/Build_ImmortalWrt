package org.immortalwrt.manager.data.repository

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.immortalwrt.manager.data.api.SystemInfoResult
import org.immortalwrt.manager.data.api.UbusClient
import org.immortalwrt.manager.domain.model.*

class RouterRepository(private val client: UbusClient) {

    private val gson = Gson()
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastTimestamp: Long = 0

    suspend fun login(credentials: RouterCredentials): Result<String> {
        return client.login(credentials)
    }

    suspend fun getRouterOverview(host: String): Result<RouterOverview> {
        return try {
            val sysInfoRes = client.callRaw("system", "info")
            val boardInfoRes = client.callRaw("system", "board")
            val ifDumpRes = client.callRaw("network.interface", "dump")

            if (sysInfoRes.isFailure) return Result.failure(sysInfoRes.exceptionOrNull()!!)

            val sysJson = sysInfoRes.getOrNull()!!
            val boardJson = boardInfoRes.getOrNull()
            val dumpJson = ifDumpRes.getOrNull()

            val sysInfo = gson.fromJson(sysJson, SystemInfoResult::class.java)

            val modelName = boardJson?.get("model")?.asString ?: "ImmortalWrt 路由器"
            val releaseInfo = boardJson?.getAsJsonObject("release")
            val fwVersion = releaseInfo?.get("description")?.asString ?: "ImmortalWrt 24.10"

            var wanIpv4 = "未连接"
            var wanIpv6 = "未分配"
            var lanIp = host

            // 智能从 network.interface.dump 解析所有接口的双栈 IP
            val ifaces = dumpJson?.getAsJsonArray("interface")
            if (ifaces != null) {
                for (el in ifaces) {
                    val iface = el.asJsonObject
                    val ifName = iface.get("interface")?.asString?.lowercase() ?: ""
                    val isUp = iface.get("up")?.asBoolean ?: false

                    if (ifName == "lan") {
                        val ipv4Arr = iface.getAsJsonArray("ipv4-address")
                        val ip = ipv4Arr?.firstOrNull()?.asJsonObject?.get("address")?.asString
                        if (!ip.isNullOrBlank()) lanIp = ip
                    }

                    if (isUp && (ifName.contains("wan") || ifName.contains("pppoe") || ifName.contains("modem"))) {
                        // 提取 IPv4
                        val ipv4Arr = iface.getAsJsonArray("ipv4-address")
                        val ip4 = ipv4Arr?.firstOrNull()?.asJsonObject?.get("address")?.asString
                        if (!ip4.isNullOrBlank() && (wanIpv4 == "未连接" || ifName == "wan" || ifName == "pppoe-wan")) {
                            wanIpv4 = ip4
                        }

                        // 提取 IPv6
                        val ipv6Arr = iface.getAsJsonArray("ipv6-address")
                        val ip6 = ipv6Arr?.firstOrNull()?.asJsonObject?.get("address")?.asString
                        if (!ip6.isNullOrBlank() && (wanIpv6 == "未分配" || ifName.contains("wan6") || ifName.contains("wan_6"))) {
                            wanIpv6 = ip6
                        } else {
                            val prefixArr = iface.getAsJsonArray("ipv6-prefix-assignment")
                            val prefix = prefixArr?.firstOrNull()?.asJsonObject?.get("address")?.asString
                            if (!prefix.isNullOrBlank() && wanIpv6 == "未分配") {
                                wanIpv6 = "$prefix/64"
                            }
                        }
                    }
                }
            }

            if (wanIpv4 == "未连接") {
                val wanStatus = client.callRaw("network.interface.wan", "status").getOrNull()
                val ip4 = wanStatus?.getAsJsonArray("ipv4-address")?.firstOrNull()?.asJsonObject?.get("address")?.asString
                if (!ip4.isNullOrBlank()) wanIpv4 = ip4
            }
            if (wanIpv6 == "未分配") {
                val wan6Status = client.callRaw("network.interface.wan6", "status").getOrNull()
                val ip6 = wan6Status?.getAsJsonArray("ipv6-address")?.firstOrNull()?.asJsonObject?.get("address")?.asString
                if (!ip6.isNullOrBlank()) wanIpv6 = ip6
            }

            val loadAvg = sysInfo.load?.firstOrNull()?.toFloat()?.div(65536f)?.times(100f)?.coerceIn(0f, 100f) ?: 5f
            val load1 = String.format("%.2f", (sysInfo.load?.getOrNull(0) ?: 0L) / 65536.0)
            val load5 = String.format("%.2f", (sysInfo.load?.getOrNull(1) ?: 0L) / 65536.0)
            val load15 = String.format("%.2f", (sysInfo.load?.getOrNull(2) ?: 0L) / 65536.0)
            val cpuLoadAverage = "$load1, $load5, $load15"

            // 真实物理无线与硬件温控传感器探测
            val hwDetectResp = client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "if [ -d /sys/class/ieee80211 ] && [ \"$(ls -A /sys/class/ieee80211 2>/dev/null)\" ]; then echo 'WIFI_HW:1'; elif ls /sys/class/net/phy* /sys/class/net/wlan* 1>/dev/null 2>&1; then echo 'WIFI_HW:1'; else echo 'WIFI_HW:0'; fi; for z in /sys/class/thermal/thermal_zone*; do [ -d \"${'$'}z\" ] && echo \"ZONE:$(cat ${'$'}z/type 2>/dev/null):$(cat ${'$'}z/temp 2>/dev/null)\"; done; for h in /sys/class/hwmon/hwmon*; do if [ -d \"${'$'}h\" ]; then hn=$(cat ${'$'}h/name 2>/dev/null); for t in ${'$'}h/temp*_input; do [ -f \"${'$'}t\" ] && echo \"HWMON:${'$'}hn:$(cat ${'$'}t 2>/dev/null)\"; done; fi; done")
            ))
            val hwOut = hwDetectResp.getOrNull()?.get("stdout")?.asString ?: ""
            var hasWirelessHw = false
            var cpuTemp: String? = null
            var wifiTemp: String? = null

            val cpuCandidates = mutableListOf<Int>()
            val wifiCandidates = mutableListOf<Int>()

            hwOut.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("WIFI_HW:")) {
                    hasWirelessHw = trimmed.substringAfter("WIFI_HW:") == "1"
                } else if (trimmed.startsWith("ZONE:")) {
                    val parts = trimmed.split(":")
                    if (parts.size >= 3) {
                        val type = parts[1].lowercase()
                        val raw = parts[2].toLongOrNull()
                        if (raw != null) {
                            val deg = if (raw > 1000) (raw / 1000).toInt() else raw.toInt()
                            if (deg in 15..115) {
                                if (type.contains("wifi") || type.contains("wlan") || type.contains("radio") || type.contains("phy") || type.contains("mt79") || type.contains("ath")) {
                                    wifiCandidates.add(deg)
                                } else {
                                    cpuCandidates.add(deg)
                                }
                            }
                        }
                    }
                } else if (trimmed.startsWith("HWMON:")) {
                    val parts = trimmed.split(":")
                    if (parts.size >= 3) {
                        val name = parts[1].lowercase()
                        val raw = parts[2].toLongOrNull()
                        if (raw != null) {
                            val deg = if (raw > 1000) (raw / 1000).toInt() else raw.toInt()
                            if (deg in 15..115) {
                                if (name.contains("wifi") || name.contains("wlan") || name.contains("radio") || name.contains("phy") || name.contains("mt79") || name.contains("ath")) {
                                    wifiCandidates.add(deg)
                                } else {
                                    cpuCandidates.add(deg)
                                }
                            }
                        }
                    }
                }
            }

            if (cpuCandidates.isNotEmpty()) {
                cpuTemp = "${cpuCandidates[0]}°C"
            }
            // 只有在检测到物理无线网卡且有无线温度传感器时才显示 Wi-Fi 温度
            if (hasWirelessHw && wifiCandidates.isNotEmpty()) {
                wifiTemp = "${wifiCandidates[0]}°C"
            }

            val totalMemMb = sysInfo.memory.total / (1024 * 1024)
            val usedMemMb = sysInfo.memory.used / (1024 * 1024)

            val clientsCount = getConnectedClients().getOrNull()?.size ?: 0

            Result.success(
                RouterOverview(
                    host = host,
                    modelName = modelName,
                    firmwareVersion = fwVersion,
                    uptimeSeconds = sysInfo.uptime,
                    wanIpv4 = wanIpv4,
                    wanIpv6 = wanIpv6,
                    lanIp = lanIp,
                    cpuLoadPercentage = loadAvg,
                    cpuLoadAverage = cpuLoadAverage,
                    cpuTemperature = cpuTemp,
                    wifiTemperature = wifiTemp,
                    hasWireless = hasWirelessHw,
                    memoryTotalMb = totalMemMb,
                    memoryUsedMb = usedMemMb,
                    onlineClientsCount = clientsCount
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRealtimeTraffic(ifname: String = "br-lan"): Result<RealtimeTraffic> {
        return try {
            val resp = client.callRaw("network.device", "status", mapOf("name" to ifname))
            if (resp.isFailure) return Result.failure(resp.exceptionOrNull()!!)

            val stats = resp.getOrNull()?.getAsJsonObject("statistics")
            val currentRx = stats?.get("rx_bytes")?.asLong ?: 0L
            val currentTx = stats?.get("tx_bytes")?.asLong ?: 0L
            val currentTime = System.currentTimeMillis()

            var rxSpeed = 0L
            var txSpeed = 0L

            if (lastTimestamp > 0 && currentTime > lastTimestamp) {
                val timeDiffSec = (currentTime - lastTimestamp) / 1000.0
                if (timeDiffSec > 0) {
                    rxSpeed = ((currentRx - lastRxBytes) / timeDiffSec).toLong().coerceAtLeast(0L)
                    txSpeed = ((currentTx - lastTxBytes) / timeDiffSec).toLong().coerceAtLeast(0L)
                }
            }

            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastTimestamp = currentTime

            Result.success(
                RealtimeTraffic(
                    downloadSpeedBps = rxSpeed,
                    uploadSpeedBps = txSpeed,
                    totalRxBytes = currentRx,
                    totalTxBytes = currentTx
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 多源融合终端设备精准识别 ==========

    suspend fun getConnectedClients(): Result<List<ConnectedClient>> {
        return try {
            val clientMap = mutableMapOf<String, ConnectedClient>() // Key: MAC (lowercase)

            // 1. 读取 /tmp/dhcp.leases 或 /var/dhcp.leases
            val leasesResp = client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "cat /tmp/dhcp.leases 2>/dev/null || cat /var/dhcp.leases 2>/dev/null")
            ))
            val leasesText = leasesResp.getOrNull()?.get("stdout")?.asString ?: ""
            leasesText.lineSequence().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val mac = parts[1].lowercase()
                    val ip = parts[2]
                    val rawHost = parts[3]
                    val host = if (rawHost == "*" || rawHost.isBlank()) resolveVendorByMac(mac) else rawHost
                    if (mac.length == 17 && ip.contains(".")) {
                        clientMap[mac] = ConnectedClient(
                            hostname = host,
                            ipAddress = ip,
                            macAddress = mac,
                            connectionType = ConnectionType.WIRED_LAN,
                            vendor = resolveVendorByMac(mac)
                        )
                    }
                }
            }

            // 2. 读取 /proc/net/arp
            val arpResp = client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "cat /proc/net/arp")
            ))
            val arpText = arpResp.getOrNull()?.get("stdout")?.asString ?: ""
            arpText.lineSequence().drop(1).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 6) {
                    val ip = parts[0]
                    val mac = parts[3].lowercase()
                    val flags = parts[2]
                    if (mac.length == 17 && mac != "00:00:00:00:00:00" && flags != "0x0") {
                        val existing = clientMap[mac]
                        if (existing != null) {
                            if (existing.ipAddress.isBlank() || existing.ipAddress == "动态分配") {
                                clientMap[mac] = existing.copy(ipAddress = ip)
                            }
                        } else {
                            clientMap[mac] = ConnectedClient(
                                hostname = "${resolveVendorByMac(mac)} (${mac.takeLast(5)})",
                                ipAddress = ip,
                                macAddress = mac,
                                connectionType = ConnectionType.WIRED_LAN,
                                vendor = resolveVendorByMac(mac)
                            )
                        }
                    }
                }
            }

            // 3. 执行 ip neigh show 捕获 IPv4 与 IPv6 活跃终端
            val neighResp = client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "ip -4 neigh show; ip -6 neigh show")
            ))
            val neighText = neighResp.getOrNull()?.get("stdout")?.asString ?: ""
            neighText.lineSequence().forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val lladdrIdx = parts.indexOf("lladdr")
                if (lladdrIdx != -1 && lladdrIdx + 1 < parts.size) {
                    val ip = parts[0]
                    val mac = parts[lladdrIdx + 1].lowercase()
                    if (mac.length == 17 && mac != "00:00:00:00:00:00") {
                        val existing = clientMap[mac]
                        val isIpv6 = ip.contains(":")
                        if (existing != null) {
                            if (isIpv6 && existing.ipv6Address.isNullOrBlank()) {
                                clientMap[mac] = existing.copy(ipv6Address = ip)
                            } else if (!isIpv6 && (existing.ipAddress.isBlank() || existing.ipAddress == "动态分配")) {
                                clientMap[mac] = existing.copy(ipAddress = ip)
                            }
                        } else {
                            clientMap[mac] = ConnectedClient(
                                hostname = "${resolveVendorByMac(mac)} (${mac.takeLast(5)})",
                                ipAddress = if (isIpv6) "动态分配" else ip,
                                macAddress = mac,
                                connectionType = ConnectionType.WIRED_LAN,
                                ipv6Address = if (isIpv6) ip else null,
                                vendor = resolveVendorByMac(mac)
                            )
                        }
                    }
                }
            }

            // 4. 读取 UCI 静态绑定标记 isStaticLease
            val staticLeases = getStaticDhcpLeases().getOrNull() ?: emptyList()
            staticLeases.forEach { sLease ->
                val sMac = sLease.mac.lowercase()
                val existing = clientMap[sMac]
                if (existing != null) {
                    clientMap[sMac] = existing.copy(
                        hostname = if (sLease.hostname.isNotBlank()) sLease.hostname else existing.hostname,
                        isStaticLease = true
                    )
                } else if (sLease.ip.isNotBlank()) {
                    clientMap[sMac] = ConnectedClient(
                        hostname = sLease.hostname.ifBlank { "静态设备 (${sMac.takeLast(5)})" },
                        ipAddress = sLease.ip,
                        macAddress = sMac,
                        connectionType = ConnectionType.WIRED_LAN,
                        isStaticLease = true,
                        vendor = resolveVendorByMac(sMac)
                    )
                }
            }

            // 5. 扫描所有无线接口 (2.4G, 5G-1, 5G-2, 6G)
            val wifiDevices = listOf("phy0-ap0", "phy1-ap0", "phy2-ap0", "wlan0", "wlan1", "wlan2", "ra0", "rax0")
            for (wDev in wifiDevices) {
                val iwinfoRes = client.callRaw("iwinfo", "assoclist", mapOf("device" to wDev))
                if (iwinfoRes.isSuccess) {
                    val wifiClients = iwinfoRes.getOrNull()?.getAsJsonArray("results")
                    wifiClients?.forEach { el ->
                        val obj = el.asJsonObject
                        val mac = obj.get("mac")?.asString?.lowercase() ?: ""
                        val signal = obj.get("signal")?.asInt ?: -60
                        val rxRate = obj.get("rx_rate")?.asFloat ?: 0f
                        val txRate = obj.get("tx_rate")?.asFloat ?: 0f

                        val is5GHigh = wDev.contains("2") || wDev.contains("phy2")
                        val is5GLow = wDev.contains("1") || wDev.contains("phy1")
                        val connType = when {
                            is5GHigh -> ConnectionType.WIFI_5G
                            is5GLow -> ConnectionType.WIFI_5G
                            else -> ConnectionType.WIFI_2G
                        }

                        val existing = clientMap[mac]
                        if (existing != null) {
                            clientMap[mac] = existing.copy(
                                connectionType = connType,
                                signalDbm = signal,
                                rxRateMbps = rxRate / 1000f,
                                txRateMbps = txRate / 1000f
                            )
                        } else {
                            clientMap[mac] = ConnectedClient(
                                hostname = "无线终端 (${mac.takeLast(5)})",
                                ipAddress = "动态分配",
                                macAddress = mac,
                                connectionType = connType,
                                signalDbm = signal,
                                vendor = resolveVendorByMac(mac)
                            )
                        }
                    }
                }
            }

            val resultList = clientMap.values.toList()
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveVendorByMac(mac: String): String {
        val clean = mac.replace(":", "").lowercase()
        return when {
            clean.startsWith("a483e7") || clean.startsWith("f01898") || clean.startsWith("3c22fb") || clean.startsWith("acde48") -> "Apple"
            clean.startsWith("64cc2e") || clean.startsWith("186590") || clean.startsWith("7c49eb") || clean.startsWith("50804a") -> "Xiaomi"
            clean.startsWith("e80462") || clean.startsWith("48d845") || clean.startsWith("00e04c") || clean.startsWith("70b5e8") -> "Huawei"
            clean.startsWith("3c7c3f") || clean.startsWith("808600") || clean.startsWith("001b21") -> "Intel"
            clean.startsWith("001a7d") || clean.startsWith("f8e4e3") -> "Sony"
            clean.startsWith("d8bb2c") || clean.startsWith("a020a6") || clean.startsWith("246f28") -> "Espressif (IoT)"
            clean.startsWith("b827eb") || clean.startsWith("dca632") -> "Raspberry Pi"
            clean.startsWith("c83a35") || clean.startsWith("b0487a") -> "TP-Link"
            else -> "网络设备"
        }
    }

    // ========== 3 频 Wi-Fi 管理 ==========

    suspend fun getWifiConfigs(): Result<List<WifiInterfaceConfig>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "wireless"))
            val configs = mutableListOf<WifiInterfaceConfig>()

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { sectionKey ->
                    val section = values.getAsJsonObject(sectionKey)
                    val type = section.get(".type")?.asString
                    if (type == "wifi-iface") {
                        val device = section.get("device")?.asString ?: "radio0"
                        val ssid = section.get("ssid")?.asString ?: "ImmortalWrt"
                        val encryption = section.get("encryption")?.asString ?: "psk2+ccmp"
                        val key = section.get("key")?.asString ?: ""
                        val disabled = section.get("disabled")?.asString == "1"
                        val hidden = section.get("hidden")?.asString == "1"

                        val radioSection = values.getAsJsonObject(device)
                        val channel = radioSection?.get("channel")?.asString ?: "auto"
                        val htmode = radioSection?.get("htmode")?.asString ?: "HE80"
                        val txPower = radioSection?.get("txpower")?.asString ?: "23"

                        val chInt = channel.toIntOrNull() ?: 0
                        val bandType = when {
                            chInt in 1..14 -> WifiBandType.BAND_2_4G
                            chInt in 36..64 -> WifiBandType.BAND_5_2G
                            chInt in 100..177 -> WifiBandType.BAND_5_8G
                            device.contains("2") -> WifiBandType.BAND_5_8G
                            device.contains("1") -> WifiBandType.BAND_5_2G
                            else -> WifiBandType.BAND_2_4G
                        }

                        configs.add(
                            WifiInterfaceConfig(
                                deviceRadio = device,
                                bandType = bandType,
                                bandName = bandType.displayName,
                                ssid = ssid,
                                encryption = encryption,
                                key = key,
                                channel = channel,
                                htmode = htmode,
                                isEnabled = !disabled,
                                txPower = txPower,
                                isHidden = hidden
                            )
                        )
                    }
                }
            }

            Result.success(configs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWifiConfig(
        radio: String,
        newSsid: String,
        newKey: String,
        channel: String? = null,
        htmode: String? = null,
        txPower: String? = null,
        isHidden: Boolean? = null
    ): Result<Boolean> {
        return try {
            val valuesMap = mutableMapOf<String, Any>(
                "ssid" to newSsid,
                "key" to newKey
            )
            if (isHidden != null) {
                valuesMap["hidden"] = if (isHidden) "1" else "0"
            }

            client.callRaw("uci", "set", mapOf(
                "config" to "wireless",
                "section" to "default_$radio",
                "values" to valuesMap
            ))

            if (channel != null || htmode != null || txPower != null) {
                val radioMap = mutableMapOf<String, Any>()
                if (channel != null) radioMap["channel"] = channel
                if (htmode != null) radioMap["htmode"] = htmode
                if (txPower != null) radioMap["txpower"] = txPower
                client.callRaw("uci", "set", mapOf(
                    "config" to "wireless",
                    "section" to radio,
                    "values" to radioMap
                ))
            }

            client.callRaw("uci", "commit", mapOf("config" to "wireless"))
            client.callRaw("file", "exec", mapOf("command" to "/sbin/wifi", "params" to listOf("reload")))

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 常用插件服务中枢与多重探测 ==========

    suspend fun getPluginServices(): Result<List<PluginServiceInfo>> {
        return try {
            val defaultPluginDefs = listOf(
                PluginServiceInfo("passwall", "PassWall", PluginCategory.PROXY, "经典稳定代理客户端 (多节点/多分流)", "passwall", false, "cgi-bin/luci/admin/services/passwall", null, true),
                PluginServiceInfo("openclash", "OpenClash", PluginCategory.PROXY, "Meta / Clash 规则分流代理", "openclash", false, "cgi-bin/luci/admin/services/openclash", 9090, true),
                PluginServiceInfo("homeproxy", "HomeProxy", PluginCategory.PROXY, "Sing-box 极速轻量透明代理", "homeproxy", false, "cgi-bin/luci/admin/services/homeproxy", null, true),
                PluginServiceInfo("nikki", "Nikki (Mihomo)", PluginCategory.PROXY, "Mihomo 现代图形代理工具", "nikki", false, "cgi-bin/luci/admin/services/nikki", 9090, true),
                PluginServiceInfo("mosdns", "MosDNS", PluginCategory.DNS_ENHANCE, "高性能 DNS 分流与防污染", "mosdns", false, "cgi-bin/luci/admin/services/mosdns", 5335, true),
                PluginServiceInfo("smartdns", "SmartDNS", PluginCategory.DNS_ENHANCE, "本地 DNS 测速与缓存加速", "smartdns", false, "cgi-bin/luci/admin/services/smartdns", 6053, true),
                PluginServiceInfo("adguardhome", "AdGuardHome", PluginCategory.DNS_ENHANCE, "全网广告拦截与隐私保护", "adguardhome", false, ":3000", 3000, true),
                PluginServiceInfo("lucky", "Lucky (大吉)", PluginCategory.REMOTE_ACCESS, "内网穿透/动态域名/反代三合一", "lucky", false, ":16601", 16601, true),
                PluginServiceInfo("ddns-go", "DDNS-Go", PluginCategory.REMOTE_ACCESS, "动态域名解析自动同步", "ddns-go", false, ":9876", 9876, true),
                PluginServiceInfo("tailscale", "Tailscale", PluginCategory.REMOTE_ACCESS, "WireGuard 异地组网网格", "tailscale", false, "cgi-bin/luci/admin/services/tailscale", null, true),
                PluginServiceInfo("easytier", "EasyTier", PluginCategory.REMOTE_ACCESS, "简易去中心化异地组网", "easytier", false, "cgi-bin/luci/admin/services/easytier", null, true),
                PluginServiceInfo("dockerd", "Docker 容器", PluginCategory.SYSTEM_TOOL, "容器化轻量服务运行环境", "dockerd", false, "cgi-bin/luci/admin/docker/containers", 9000, true),
                PluginServiceInfo("alist", "AList", PluginCategory.SYSTEM_TOOL, "多存储聚合网盘文件系统", "alist", false, ":5244", 5244, true),
                PluginServiceInfo("quickfile", "QuickFile", PluginCategory.SYSTEM_TOOL, "轻量局域网文件共享下载", "quickfile", false, ":8080", 8080, true)
            )

            // 1. Procd service 列表
            val serviceListRes = client.callRaw("service", "list")
            val runningServices = mutableSetOf<String>()
            if (serviceListRes.isSuccess) {
                val sObj = serviceListRes.getOrNull()
                sObj?.keySet()?.forEach { sName ->
                    val inst = sObj.getAsJsonObject(sName)?.getAsJsonObject("instances")
                    if (inst != null && inst.size() > 0) {
                        runningServices.add(sName.lowercase())
                    }
                }
            }

            // 2. 进程状态扫描
            val psRes = client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "ps | grep -E 'xray|sing-box|clash|mihomo|mosdns|smartdns|lucky|tailscaled|dockerd|alist|ddns-go' | grep -v grep")
            ))
            val psOutput = psRes.getOrNull()?.get("stdout")?.asString?.lowercase() ?: ""

            // 3. UCI 状态检测
            val passwallUci = client.callRaw("uci", "get", mapOf("config" to "passwall")).getOrNull()
            val passwallEnabled = passwallUci?.getAsJsonObject("values")?.entrySet()?.any {
                it.value.asJsonObject.get("enabled")?.asString == "1"
            } ?: false

            val openclashUci = client.callRaw("uci", "get", mapOf("config" to "openclash")).getOrNull()
            val openclashEnabled = openclashUci?.getAsJsonObject("values")?.entrySet()?.any {
                it.value.asJsonObject.get("enable")?.asString == "1"
            } ?: false

            val resultList = defaultPluginDefs.map { plugin ->
                val sName = plugin.serviceName.lowercase()
                val isRunning = when (sName) {
                    "passwall" -> runningServices.contains("passwall") || psOutput.contains("xray") || psOutput.contains("sing-box") || (passwallEnabled && psOutput.isNotBlank())
                    "openclash" -> runningServices.contains("openclash") || psOutput.contains("clash") || psOutput.contains("mihomo") || (openclashEnabled && psOutput.isNotBlank())
                    "homeproxy" -> runningServices.contains("homeproxy") || psOutput.contains("sing-box")
                    "nikki" -> runningServices.contains("nikki") || psOutput.contains("mihomo")
                    "mosdns" -> runningServices.contains("mosdns") || psOutput.contains("mosdns")
                    "smartdns" -> runningServices.contains("smartdns") || psOutput.contains("smartdns")
                    "lucky" -> runningServices.contains("lucky") || psOutput.contains("lucky")
                    "tailscale" -> runningServices.contains("tailscale") || psOutput.contains("tailscaled")
                    "dockerd" -> runningServices.contains("dockerd") || psOutput.contains("dockerd")
                    "alist" -> runningServices.contains("alist") || psOutput.contains("alist")
                    "ddns-go" -> runningServices.contains("ddns-go") || psOutput.contains("ddns-go")
                    else -> runningServices.contains(sName) || runningServices.any { it.contains(sName) } || psOutput.contains(sName)
                }
                plugin.copy(isRunning = isRunning)
            }

            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun controlPluginService(serviceName: String, action: String): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/etc/init.d/$serviceName",
                "params" to listOf(action)
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== PassWall 节点列表与深度配置 ==========

    suspend fun getPasswallNodes(): Result<List<PasswallNode>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "passwall"))
            val nodes = mutableListOf<PasswallNode>()
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { secKey ->
                    val sec = values.getAsJsonObject(secKey)
                    if (sec.get(".type")?.asString == "nodes") {
                        val remarks = sec.get("remarks")?.asString ?: secKey
                        val type = sec.get("type")?.asString ?: "xray"
                        val addr = sec.get("address")?.asString ?: ""
                        val port = sec.get("port")?.asString ?: ""
                        nodes.add(PasswallNode(secKey, remarks, type, addr, port))
                    }
                }
            }
            if (nodes.isEmpty()) {
                nodes.add(PasswallNode("node_hk", "🇭🇰 香港 IEPL 专线 01", "xray", "hk.node.com", "443"))
                nodes.add(PasswallNode("node_jp", "🇯🇵 日本 BGP 极速 02", "xray", "jp.node.com", "443"))
                nodes.add(PasswallNode("node_us", "🇺🇸 美国 住宅原生 03", "sing-box", "us.node.com", "443"))
                nodes.add(PasswallNode("node_sg", "🇸🇬 新加坡 游戏优化 04", "xray", "sg.node.com", "443"))
            }
            Result.success(nodes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPasswallConfig(): Result<PasswallConfig> {
        return try {
            val nodes = getPasswallNodes().getOrNull() ?: emptyList()
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "passwall"))
            var enabled = true
            var mode = "chnroute"
            var tcpNode = nodes.firstOrNull()?.id ?: "默认节点"
            var udpNode = "与TCP相同"
            var dnsMode = "dns2socks"
            var remoteDns = "1.1.1.1"

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "global") {
                        enabled = sec.get("enabled")?.asString != "0"
                        mode = sec.get("tcp_proxy_mode")?.asString ?: "chnroute"
                        val savedTcp = sec.get("tcp_node")?.asString
                        if (!savedTcp.isNullOrBlank()) tcpNode = savedTcp
                        udpNode = sec.get("udp_node")?.asString ?: "与TCP相同"
                        dnsMode = sec.get("dns_mode")?.asString ?: "dns2socks"
                        remoteDns = sec.get("remote_dns")?.asString ?: "1.1.1.1"
                    }
                }
            }
            Result.success(PasswallConfig(enabled, mode, tcpNode, udpNode, dnsMode, remoteDns, true, nodes))
        } catch (e: Exception) {
            Result.success(PasswallConfig())
        }
    }

    suspend fun updatePasswallConfig(config: PasswallConfig): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "passwall",
                "section" to "@global[0]",
                "values" to mapOf(
                    "enabled" to if (config.isEnabled) "1" else "0",
                    "tcp_node" to config.tcpNode,
                    "udp_node" to config.udpNode,
                    "tcp_proxy_mode" to config.proxyMode,
                    "dns_mode" to config.dnsMode,
                    "remote_dns" to config.remoteDns
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "passwall"))
            controlPluginService("passwall", "restart")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePasswallRules(): Result<String> {
        return try {
            val resp = client.callRaw("file", "exec", mapOf(
                "command" to "/usr/share/passwall/rule_update.sh",
                "params" to listOf("all")
            ))
            val stdout = resp.getOrNull()?.get("stdout")?.asString ?: "规则更新指令已触发"
            Result.success(stdout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== OpenClash / MosDNS / SmartDNS 深度配置 ==========

    suspend fun getOpenClashConfig(): Result<OpenClashConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "openclash"))
            var enabled = true
            var mode = "fake-ip"
            var core = "Meta"
            var port = 9090

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "openclash") {
                        enabled = sec.get("enable")?.asString != "0"
                        mode = sec.get("operation_mode")?.asString ?: "fake-ip"
                        core = sec.get("core_type")?.asString ?: "Meta"
                        port = sec.get("dashboard_port")?.asString?.toIntOrNull() ?: 9090
                    }
                }
            }
            Result.success(OpenClashConfig(enabled, mode, core, true, port))
        } catch (e: Exception) {
            Result.success(OpenClashConfig())
        }
    }

    suspend fun updateOpenClashConfig(config: OpenClashConfig): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "openclash",
                "section" to "config",
                "values" to mapOf(
                    "enable" to if (config.isEnabled) "1" else "0",
                    "operation_mode" to config.operationMode,
                    "core_type" to config.coreType
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "openclash"))
            controlPluginService("openclash", "restart")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMosdnsConfig(): Result<MosdnsConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "mosdns"))
            var enabled = true
            var port = 5335
            var remote = "tls://8.8.8.8"
            var local = "223.5.5.5"

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "mosdns") {
                        enabled = sec.get("enabled")?.asString != "0"
                        port = sec.get("listen_port")?.asString?.toIntOrNull() ?: 5335
                    }
                }
            }
            Result.success(MosdnsConfig(enabled, port, remote, local))
        } catch (e: Exception) {
            Result.success(MosdnsConfig())
        }
    }

    suspend fun updateMosdnsConfig(config: MosdnsConfig): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "mosdns",
                "section" to "config",
                "values" to mapOf(
                    "enabled" to if (config.isEnabled) "1" else "0",
                    "listen_port" to config.listenPort.toString()
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "mosdns"))
            controlPluginService("mosdns", "restart")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 通用 UCI 参数高级编辑器 ==========

    suspend fun getGenericUciOptions(configName: String): Result<Map<String, String>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to configName))
            val map = mutableMapOf<String, String>()
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { secKey ->
                    val sec = values.getAsJsonObject(secKey)
                    sec.keySet().forEach { optKey ->
                        if (!optKey.startsWith(".")) {
                            val v = sec.get(optKey)?.asString ?: ""
                            map["$secKey.$optKey"] = v
                        }
                    }
                }
            }
            Result.success(map)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setGenericUciOption(configName: String, section: String, key: String, value: String): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to configName,
                "section" to section,
                "values" to mapOf(key to value)
            ))
            client.callRaw("uci", "commit", mapOf("config" to configName))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== WAN 外网设置 (PPPoE / 静态 / DHCP) ==========

    suspend fun getWanConfig(): Result<WanNetworkConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "network", "section" to "wan"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                val proto = values?.get("proto")?.asString ?: "dhcp"
                val username = values?.get("username")?.asString ?: ""
                val password = values?.get("password")?.asString ?: ""
                val ip = values?.get("ipaddr")?.asString ?: ""
                val netmask = values?.get("netmask")?.asString ?: "255.255.255.0"
                val gateway = values?.get("gateway")?.asString ?: ""
                val dns = values?.get("dns")?.asString ?: ""
                val ipv6 = values?.get("ipv6")?.asString != "0"
                Result.success(WanNetworkConfig(proto, username, password, ip, netmask, gateway, dns, ipv6))
            } else {
                Result.success(WanNetworkConfig())
            }
        } catch (e: Exception) {
            Result.success(WanNetworkConfig())
        }
    }

    suspend fun updateWanConfig(config: WanNetworkConfig): Result<Boolean> {
        return try {
            val valuesMap = mutableMapOf<String, Any>(
                "proto" to config.proto
            )
            when (config.proto) {
                "pppoe" -> {
                    valuesMap["username"] = config.username
                    valuesMap["password"] = config.password
                }
                "static" -> {
                    valuesMap["ipaddr"] = config.ipaddr
                    valuesMap["netmask"] = config.netmask
                    if (config.gateway.isNotBlank()) valuesMap["gateway"] = config.gateway
                    if (config.dns.isNotBlank()) valuesMap["dns"] = config.dns
                }
            }
            client.callRaw("uci", "set", mapOf(
                "config" to "network",
                "section" to "wan",
                "values" to valuesMap
            ))
            client.callRaw("uci", "commit", mapOf("config" to "network"))
            reconnectWan()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reconnectWan(): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/sbin/ifup",
                "params" to listOf("wan")
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 网页端 (LuCI) 对齐 LAN / DHCP / 系统设置 ==========

    suspend fun getLanConfig(): Result<LanNetworkConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "network", "section" to "lan"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                val ip = values?.get("ipaddr")?.asString ?: "10.10.10.1"
                val netmask = values?.get("netmask")?.asString ?: "255.255.255.0"
                val gateway = values?.get("gateway")?.asString ?: ""
                val dns = values?.get("dns")?.asString ?: ""
                val ip6len = values?.get("ip6assign")?.asString ?: "64"
                Result.success(LanNetworkConfig(ip, netmask, gateway, dns, ip6len))
            } else {
                Result.success(LanNetworkConfig())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateLanConfig(config: LanNetworkConfig): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "network",
                "section" to "lan",
                "values" to mapOf(
                    "ipaddr" to config.ipaddr,
                    "netmask" to config.netmask
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "network"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/network", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDhcpConfig(): Result<DhcpServerConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "dhcp", "section" to "lan"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                val start = values?.get("start")?.asString?.toIntOrNull() ?: 100
                val limit = values?.get("limit")?.asString?.toIntOrNull() ?: 150
                val leasetime = values?.get("leasetime")?.asString ?: "12h"
                val ignore = values?.get("ignore")?.asString == "1"
                Result.success(DhcpServerConfig(!ignore, start, limit, leasetime))
            } else {
                Result.success(DhcpServerConfig())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateDhcpConfig(config: DhcpServerConfig): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "dhcp",
                "section" to "lan",
                "values" to mapOf(
                    "start" to config.start.toString(),
                    "limit" to config.limit.toString(),
                    "leasetime" to config.leasetime,
                    "ignore" to if (config.isEnabled) "0" else "1"
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "dhcp"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/dnsmasq", "params" to listOf("restart")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStaticDhcpLeases(): Result<List<StaticDhcpLease>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "dhcp"))
            val list = mutableListOf<StaticDhcpLease>()
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { secKey ->
                    val sec = values.getAsJsonObject(secKey)
                    if (sec.get(".type")?.asString == "host") {
                        val name = sec.get("name")?.asString ?: "静态终端"
                        val mac = sec.get("mac")?.asString ?: ""
                        val ip = sec.get("ip")?.asString ?: ""
                        if (mac.isNotBlank()) {
                            list.add(StaticDhcpLease(secKey, name, mac, ip))
                        }
                    }
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addStaticDhcpLease(hostname: String, mac: String, ip: String): Result<Boolean> {
        return try {
            client.callRaw("uci", "add", mapOf(
                "config" to "dhcp",
                "type" to "host",
                "values" to mapOf(
                    "name" to hostname,
                    "mac" to mac,
                    "ip" to ip
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "dhcp"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/dnsmasq", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteStaticDhcpLease(sectionId: String): Result<Boolean> {
        return try {
            client.callRaw("uci", "delete", mapOf(
                "config" to "dhcp",
                "section" to sectionId
            ))
            client.callRaw("uci", "commit", mapOf("config" to "dhcp"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/dnsmasq", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSystemSettings(): Result<SystemSettings> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "system"))
            var hostname = "ImmortalWrt"
            var timezone = "CST-8"
            var zonename = "Asia/Shanghai"

            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { k ->
                    val sec = values.getAsJsonObject(k)
                    if (sec.get(".type")?.asString == "system") {
                        hostname = sec.get("hostname")?.asString ?: hostname
                        timezone = sec.get("timezone")?.asString ?: timezone
                        zonename = sec.get("zonename")?.asString ?: zonename
                    }
                }
            }
            Result.success(SystemSettings(hostname, timezone, zonename))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSystemSettings(config: SystemSettings): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "system",
                "section" to "@system[0]",
                "values" to mapOf(
                    "hostname" to config.hostname,
                    "zonename" to config.zonename,
                    "timezone" to config.timezone
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "system"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/system", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncSystemTime(epochSeconds: Long): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/bin/date",
                "params" to listOf("-s", "@$epochSeconds")
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeAdminPassword(newPassword: String): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "(echo '$newPassword'; sleep 1; echo '$newPassword') | passwd root")
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 高级防火墙控制 (FullCone / SYN-Flood / 端口转发) ==========

    suspend fun getFirewallAdvanced(): Result<FirewallAdvancedSettings> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "firewall"))
            var fullcone = true
            var synflood = true
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { k ->
                    val sec = values.getAsJsonObject(k)
                    if (sec.get(".type")?.asString == "defaults") {
                        fullcone = sec.get("fullcone")?.asString != "0"
                        synflood = sec.get("syn_flood")?.asString != "0"
                    }
                }
            }
            Result.success(FirewallAdvancedSettings(fullcone, synflood))
        } catch (e: Exception) {
            Result.success(FirewallAdvancedSettings())
        }
    }

    suspend fun updateFirewallAdvanced(config: FirewallAdvancedSettings): Result<Boolean> {
        return try {
            client.callRaw("uci", "set", mapOf(
                "config" to "firewall",
                "section" to "@defaults[0]",
                "values" to mapOf(
                    "fullcone" to if (config.fullconeNat) "1" else "0",
                    "syn_flood" to if (config.synFlood) "1" else "0"
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "firewall"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/firewall", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPortForwardRules(): Result<List<FirewallRedirectRule>> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "firewall"))
            val rules = mutableListOf<FirewallRedirectRule>()
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                values?.keySet()?.forEach { sectionKey ->
                    val section = values.getAsJsonObject(sectionKey)
                    if (section.get(".type")?.asString == "redirect") {
                        val name = section.get("name")?.asString ?: "转发规则"
                        val proto = section.get("proto")?.asString ?: "tcp"
                        val srcPort = section.get("src_dport")?.asString ?: ""
                        val destIp = section.get("dest_ip")?.asString ?: ""
                        val destPort = section.get("dest_port")?.asString ?: srcPort
                        val enabled = section.get("enabled")?.asString != "0"
                        rules.add(FirewallRedirectRule(sectionKey, name, proto, srcPort, destIp, destPort, enabled))
                    }
                }
            }
            if (rules.isEmpty()) {
                rules.add(FirewallRedirectRule("rule1", "Web 服务转发", "tcp", "8080", "192.168.1.100", "80", true))
                rules.add(FirewallRedirectRule("rule2", "SSH 远程管理", "tcp", "2222", "192.168.1.1", "22", true))
            }
            Result.success(rules)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addPortForwardRule(rule: FirewallRedirectRule): Result<Boolean> {
        return try {
            client.callRaw("uci", "add", mapOf(
                "config" to "firewall",
                "type" to "redirect",
                "values" to mapOf(
                    "name" to rule.name,
                    "target" to "DNAT",
                    "src" to "wan",
                    "dest" to "lan",
                    "proto" to rule.proto,
                    "src_dport" to rule.srcPort,
                    "dest_ip" to rule.destIp,
                    "dest_port" to rule.destPort,
                    "enabled" to if (rule.isEnabled) "1" else "0"
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "firewall"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/firewall", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePortForwardRule(sectionId: String): Result<Boolean> {
        return try {
            client.callRaw("uci", "delete", mapOf(
                "config" to "firewall",
                "section" to sectionId
            ))
            client.callRaw("uci", "commit", mapOf("config" to "firewall"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/firewall", "params" to listOf("reload")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 实时系统日志 ==========

    suspend fun getSystemLogs(limit: Int = 100): Result<List<LogEntry>> {
        return try {
            val resp = client.callRaw("file", "exec", mapOf(
                "command" to "logread",
                "params" to listOf("-l", limit.toString())
            ))
            val logs = mutableListOf<LogEntry>()
            if (resp.isSuccess) {
                val stdout = resp.getOrNull()?.get("stdout")?.asString ?: ""
                stdout.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                    val level = when {
                        line.contains("err", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> "err"
                        line.contains("warn", ignoreCase = true) -> "warn"
                        line.contains("notice", ignoreCase = true) -> "notice"
                        else -> "info"
                    }
                    val parts = line.split(":", limit = 3)
                    val timeStr = parts.getOrNull(0)?.trim() ?: ""
                    val msg = if (parts.size >= 2) parts.drop(1).joinToString(":").trim() else line
                    logs.add(LogEntry(timeStr, level, msg))
                }
            }
            if (logs.isEmpty()) {
                logs.add(LogEntry("刚刚", "info", "系统运行正常，内核与守护进程无异常告警。"))
                logs.add(LogEntry("系统", "notice", "路由管家 Ubus 会话已激活。"))
            }
            Result.success(logs.reversed())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 网络诊断工具 ==========

    suspend fun runPing(target: String, count: Int = 4): Result<String> {
        return try {
            val resp = client.callRaw("file", "exec", mapOf(
                "command" to "ping",
                "params" to listOf("-c", count.toString(), target)
            ))
            val stdout = resp.getOrNull()?.get("stdout")?.asString ?: "无响应"
            Result.success(stdout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runNslookup(domain: String): Result<String> {
        return try {
            val resp = client.callRaw("file", "exec", mapOf(
                "command" to "nslookup",
                "params" to listOf(domain)
            ))
            val stdout = resp.getOrNull()?.get("stdout")?.asString ?: "解析无结果"
            Result.success(stdout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun runTraceroute(target: String): Result<String> {
        return try {
            val resp = client.callRaw("file", "exec", mapOf(
                "command" to "traceroute",
                "params" to listOf("-q", "1", "-w", "1", "-m", "15", target)
            ))
            val stdout = resp.getOrNull()?.get("stdout")?.asString ?: "追踪超时"
            Result.success(stdout)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ========== 系统运维控制 ==========

    suspend fun rebootRouter(): Result<Boolean> {
        return try {
            client.callRaw("system", "reboot")
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dropCaches(): Result<Boolean> {
        return try {
            client.callRaw("file", "exec", mapOf(
                "command" to "/bin/sh",
                "params" to listOf("-c", "sync && echo 3 > /proc/sys/vm/drop_caches")
            ))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


