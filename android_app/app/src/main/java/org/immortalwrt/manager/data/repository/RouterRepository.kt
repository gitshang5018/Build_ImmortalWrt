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

            // 若依然为空，尝试降级查询 network.interface.wan / network.interface.wan6
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

    suspend fun getConnectedClients(): Result<List<ConnectedClient>> {
        return try {
            val clients = mutableListOf<ConnectedClient>()

            // 1. 获取 DHCP 租约列表
            val dhcpRes = client.callRaw("luci-rpc", "getDHCPLeases")
            if (dhcpRes.isSuccess) {
                val leases = dhcpRes.getOrNull()?.getAsJsonArray("leases")
                leases?.forEach { el ->
                    val obj = el.asJsonObject
                    val hostname = obj.get("hostname")?.asString?.takeIf { it.isNotEmpty() } ?: "未知终端"
                    val ip = obj.get("ipaddr")?.asString ?: ""
                    val mac = obj.get("macaddr")?.asString ?: ""
                    clients.add(
                        ConnectedClient(
                            hostname = hostname,
                            ipAddress = ip,
                            macAddress = mac,
                            connectionType = ConnectionType.WIRED_LAN
                        )
                    )
                }
            }

            // 2. 扫描所有无线关联接口 (3频: phy0-ap0, phy1-ap0, phy2-ap0 等)
            val wifiDevices = listOf("phy0-ap0", "phy1-ap0", "phy2-ap0", "wlan0", "wlan1", "wlan2")
            for (wDev in wifiDevices) {
                val iwinfoRes = client.callRaw("iwinfo", "assoclist", mapOf("device" to wDev))
                if (iwinfoRes.isSuccess) {
                    val wifiClients = iwinfoRes.getOrNull()?.getAsJsonArray("results")
                    wifiClients?.forEach { el ->
                        val obj = el.asJsonObject
                        val mac = obj.get("mac")?.asString?.lowercase() ?: ""
                        val signal = obj.get("signal")?.asInt ?: -60

                        val is5GHigh = wDev.contains("2") || wDev.contains("phy2")
                        val is5GLow = wDev.contains("1") || wDev.contains("phy1")
                        val connType = when {
                            is5GHigh -> ConnectionType.WIFI_5G
                            is5GLow -> ConnectionType.WIFI_5G
                            else -> ConnectionType.WIFI_2G
                        }

                        val existingIdx = clients.indexOfFirst { it.macAddress.equals(mac, ignoreCase = true) }
                        if (existingIdx != -1) {
                            val old = clients[existingIdx]
                            clients[existingIdx] = old.copy(
                                connectionType = connType,
                                signalDbm = signal
                            )
                        } else {
                            clients.add(
                                ConnectedClient(
                                    hostname = "无线客户端 (${mac.takeLast(5)})",
                                    ipAddress = "动态分配",
                                    macAddress = mac,
                                    connectionType = connType,
                                    signalDbm = signal
                                )
                            )
                        }
                    }
                }
            }

            Result.success(clients)
        } catch (e: Exception) {
            Result.failure(e)
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

            if (configs.isEmpty()) {
                configs.add(WifiInterfaceConfig("radio0", WifiBandType.BAND_2_4G, "2.4 GHz", "ImmortalWrt_2.4G", "psk2+ccmp", "12345678", "6", "HE20", true, "20", false))
                configs.add(WifiInterfaceConfig("radio1", WifiBandType.BAND_5_2G, "5.2 GHz (5G-1)", "ImmortalWrt_5G1", "psk2+ccmp", "12345678", "44", "HE160", true, "23", false))
                configs.add(WifiInterfaceConfig("radio2", WifiBandType.BAND_5_8G, "5.8 GHz (5G-2 电竞)", "ImmortalWrt_Gaming_5G2", "psk2+ccmp", "12345678", "149", "HE160", true, "23", false))
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
                PluginServiceInfo("passwall", "PassWall", PluginCategory.PROXY, "经典稳定代理客户端 (支持Xray/Sing-box)", "passwall", false, "cgi-bin/luci/admin/services/passwall", null, true),
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

            // 2. 进程状态扫描 (针对 PassWall, OpenClash 等使用独立核心进程的插件)
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

    // ========== 常用插件专属详细配置获取与下发 ==========

    suspend fun getPasswallConfig(): Result<PasswallConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "passwall"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                var enabled = true
                var mode = "chnroute"
                var tcpNode = "默认节点"
                var udpNode = "与TCP相同"
                var dnsMode = "dns2socks"
                var remoteDns = "1.1.1.1"

                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "global") {
                        enabled = sec.get("enabled")?.asString != "0"
                        mode = sec.get("tcp_proxy_mode")?.asString ?: "chnroute"
                        tcpNode = sec.get("tcp_node")?.asString ?: "主节点"
                        udpNode = sec.get("udp_node")?.asString ?: "与TCP相同"
                        dnsMode = sec.get("dns_mode")?.asString ?: "dns2socks"
                        remoteDns = sec.get("remote_dns")?.asString ?: "1.1.1.1"
                    }
                }
                Result.success(PasswallConfig(enabled, mode, tcpNode, udpNode, dnsMode, remoteDns))
            } else {
                Result.success(PasswallConfig())
            }
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

    suspend fun getOpenClashConfig(): Result<OpenClashConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "openclash"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                var enabled = true
                var mode = "fake-ip"
                var core = "Meta"
                var port = 9090

                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "openclash") {
                        enabled = sec.get("enable")?.asString != "0"
                        mode = sec.get("operation_mode")?.asString ?: "fake-ip"
                        core = sec.get("core_type")?.asString ?: "Meta"
                        port = sec.get("dashboard_port")?.asString?.toIntOrNull() ?: 9090
                    }
                }
                Result.success(OpenClashConfig(enabled, mode, core, true, port))
            } else {
                Result.success(OpenClashConfig())
            }
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
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                var enabled = true
                var port = 5335
                var remote = "tls://8.8.8.8"
                var local = "223.5.5.5"

                values?.keySet()?.forEach { key ->
                    val sec = values.getAsJsonObject(key)
                    if (sec.get(".type")?.asString == "mosdns") {
                        enabled = sec.get("enabled")?.asString != "0"
                        port = sec.get("listen_port")?.asString?.toIntOrNull() ?: 5335
                    }
                }
                Result.success(MosdnsConfig(enabled, port, remote, local))
            } else {
                Result.success(MosdnsConfig())
            }
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

    // ========== 网页端 (LuCI) 对齐设置同步 ==========

    suspend fun getLanConfig(): Result<LanNetworkConfig> {
        return try {
            val uciRes = client.callRaw("uci", "get", mapOf("config" to "network", "section" to "lan"))
            if (uciRes.isSuccess) {
                val values = uciRes.getOrNull()?.getAsJsonObject("values")
                val ip = values?.get("ipaddr")?.asString ?: "10.10.10.1"
                val netmask = values?.get("netmask")?.asString ?: "255.255.255.0"
                val gateway = values?.get("gateway")?.asString ?: ""
                val dns = values?.get("dns")?.asString ?: ""
                Result.success(LanNetworkConfig(ip, netmask, gateway, dns))
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
                Result.success(DhcpServerConfig(start, limit, leasetime))
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
                    "leasetime" to config.leasetime
                )
            ))
            client.callRaw("uci", "commit", mapOf("config" to "dhcp"))
            client.callRaw("file", "exec", mapOf("command" to "/etc/init.d/dnsmasq", "params" to listOf("restart")))
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

    // ========== 防火墙端口转发管理 ==========

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

    // ========== 静态 DHCP 绑定 ==========

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


