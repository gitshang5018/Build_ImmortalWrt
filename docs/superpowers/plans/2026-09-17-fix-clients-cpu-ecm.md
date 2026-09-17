# Android APP 瀹㈡埛绔湪绾垮垽瀹氫笌 CPU 浠〃鐩樹紭鍖栧疄鐜拌鍒?
> **闈㈠悜 AI 浠ｇ悊鐨勫伐浣滆€咃細** 蹇呴渶瀛愭妧鑳斤細浣跨敤 superpowers:subagent-driven-development锛堟帹鑽愶級鎴?superpowers:executing-plans 閫愪换鍔″疄鐜版璁″垝銆傛楠や娇鐢ㄥ閫夋锛坄- [x]`锛夎娉曟潵璺熻釜杩涘害銆?
**鐩爣锛?* 淇 Android APP 鏈夌嚎 LAN 缁堢绂荤嚎璇垽銆佸交搴曞墧闄?CPU 鍗＄墖涓殑 Wi-Fi 娓╁害娈嬬暀銆佸睍绀?ECM 纭欢鍔犻€熺姸鎬佷互鍙婁慨澶?CPU 娓╁害瑙ｆ瀽绮惧害銆?
**鏋舵瀯锛?* 鍦?`RouterRepository.kt` 涓噸鏋勭粓绔垎绫荤畻娉曪紙缁撳悎鏃犵嚎鍏宠仈鍒楄〃銆丏HCP 娲昏穬绉熸湡涓庣Щ鍔ㄨ澶囨寚绾癸級骞惰鑼冩俯搴?CPU/ECM 瑙ｆ瀽杈圭晫锛涘湪 `DashboardScreen.kt` 涓畬鍠?CPU 鍗＄墖鍓爣棰樺垎鏀紙鏀寔鐙珛 ECM 灞曠ず锛夛紱鍦?`UbusModelsTest.kt` 涓紪鍐欒嚜鍔ㄥ寲鍗曞厓娴嬭瘯銆?
**鎶€鏈爤锛?* Kotlin, Jetpack Compose, Android Coroutines, JUnit 4, Gson

---

### 浠诲姟 1锛氫慨澶?CPU 娓╁害瑙ｆ瀽涓?CPU/ECM 璐熻浇鎻愬彇 (`RouterRepository.kt`)

**鏂囦欢锛?*
- 淇敼锛歚android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt:107-205`
- 娴嬭瘯锛歚android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt`

- [x] **姝ラ 1锛氱紪鍐欏崟鍏冩祴璇曢獙璇佹俯搴︽彁鍙栦笌 CPU/ECM 瑙ｆ瀽**

鍦?`android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt` 涓坊鍔犲崟鍏冩祴璇曪細

```kotlin
    @Test
    fun testCpuAndTemperatureParsingLogic() {
        // 1. 楠岃瘉闈炴俯搴﹀瓧绗︿覆锛堝 board/system info 涓殑鍨嬪彿涓庣増鏈級涓嶈瑙ｆ瀽涓?CPU 娓╁害
        val fakeBoardInfo = """{"model":"JDCloud AX6600","release":{"description":"ImmortalWrt 24.10.0-rc1"}}"""
        val (cpu1, wifi1) = RouterRepository.extractTemperaturesFromText(fakeBoardInfo)
        assertNull(cpu1)
        assertTrue(wifi1.isEmpty())

        // 2. 楠岃瘉鏍囧噯娓╁害瀛楃涓茶兘姝ｇ‘鎻愬彇 CPU 鍜?WiFi 娓╁害
        val validTempInfo = """{"tempinfo":"CPU: 48.0掳C, WiFi: 51.0掳C 53.0掳C 49.0掳C"}"""
        val (cpu2, wifi2) = RouterRepository.extractTemperaturesFromText(validTempInfo)
        assertEquals(48, cpu2)
        assertEquals(listOf(51, 53, 49), wifi2)

        // 3. 楠岃瘉 parseCpuUsageString 涓嶄細鎶婂甫鏈?WiFi/娓╁害鐨勫瓧绗︿覆瀛樺叆 fullCpuUsageText
        val parsed = RouterRepository.parseCpuUsage(validTempInfo)
        assertNull(parsed.fullCpuUsageText) // 搴斿綋琚畨鍏ㄨ繃婊?    }
```

- [x] **姝ラ 2锛氳繍琛屾祴璇曢獙璇佸け璐?*

杩愯娴嬭瘯鍛戒护锛?```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testCpuAndTemperatureParsingLogic"
```
棰勬湡锛欶AIL锛屾彁绀?`extractTemperaturesFromText` 浠嶅尮閰嶄簡 66 鎴?24锛屾垨鑰呬即鐢熷嚱鏁板皻鏈毚闇蹭负娴嬭瘯鍙銆?
- [x] **姝ラ 3锛氬湪 `RouterRepository.kt` 涓疄鐜颁弗鏍肩殑娓╁害涓?CPU/ECM 瑙ｆ瀽**

鍦?`RouterRepository.kt` 涓細
1. 灏?`extractTemperaturesFromText` 涓?`parseCpuUsage` 鎶戒负 companion object 宸ュ叿鍑芥暟渚夸簬娴嬭瘯涓庤В鑰︺€?2. `parseCpuUsage` 涓鍔犲畨鍏ㄥ垽鏂細鑻ュ寘鍚?`WiFi:`銆乣掳C`銆乣鈩僠銆乣tempinfo`锛屽垯鐩存帴蹇界暐骞朵笉璁剧疆 `fullCpuUsageText`锛涙彁鍙?`ECM:\s*([^\r\n]+)` 涓?`HWE:\s*([^\r\n]+)`銆?3. `extractTemperaturesFromText` 澧炲姞涓ユ牸鏍￠獙锛氬繀椤诲寘鍚?`CPU:`銆乣Core`銆乣SoC` 绛夊叧閿瘝鎴栫揣璺?`掳C`/`鈩僠锛屼笖娓╁害鍦?`25..105` 鑼冨洿鍐咃紱涓ョ鐩茬洰鍖归厤 2 浣嶆暟瀛椼€?4. 鍦?`getRouterOverview` 涓紝娓╁害鎻愬彇鍙拡瀵?`getTempInfo`銆乣getCPUInfo` 鍜?`autocore`锛岀姝㈠ `getBoardInfo` 鍜?`getSystemInfo` 鎻愬彇娓╁害銆?
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
            // 鑻ュ寘鍚槑鏄剧殑娓╁害鏂囨湰锛岀粷涓嶄綔涓?CPU 浣跨敤鐜囨枃鏈繚瀛?            val isTempText = lower.contains("wifi") || lower.contains("掳c") || lower.contains("鈩?) || lower.contains("tempinfo")
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

            val cpuMatch = Regex("""(?:CPU|SoC|Core(?:\s*[0-9]+)?)[锛?\s]+([0-9]+(?:\.[0-9]+)?)\s*掳?C?""", RegexOption.IGNORE_CASE).find(text)
            if (cpuMatch != null) {
                val v = cpuMatch.groupValues[1].toFloatOrNull()?.toInt()
                if (v != null && v in 25..105) {
                    cpu = v
                }
            }

            val wifiSection = text.substringAfter("WiFi:", "").ifBlank { text.substringAfter("wifi:", "") }
            if (wifiSection.isNotBlank()) {
                Regex("""([0-9]+(?:\.[0-9]+)?)\s*掳?C?""").findAll(wifiSection).forEach { m ->
                    m.groupValues[1].toFloatOrNull()?.toInt()?.let {
                        if (it in 25..105) wifis.add(it)
                    }
                }
            }

            return Pair(cpu, wifis)
        }
    }
```

- [x] **姝ラ 4锛氳繍琛屾祴璇曢獙璇侀€氳繃**

杩愯娴嬭瘯鍛戒护锛?```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testCpuAndTemperatureParsingLogic"
```
棰勬湡锛歅ASS銆?
- [x] **姝ラ 5锛欳ommit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt; git commit -m "fix(app): refine cpu temp parsing and prevent wifi temp leakage into cpu card"
```

---

### 浠诲姟 2锛氬疄鐜版櫤鑳芥湁绾?LAN 缁堢鍦ㄧ嚎鍒ゅ畾 (`RouterRepository.kt`)

**鏂囦欢锛?*
- 淇敼锛歚android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt:431-730`
- 娴嬭瘯锛歚android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt`

- [x] **姝ラ 1锛氱紪鍐欐湁绾?鏃犵嚎缁堢鏅鸿兘鍒嗙被鐨勫崟鍏冩祴璇?*

鍦?`android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt` 涓坊鍔狅細

```kotlin
    @Test
    fun testClientDeviceClassification() {
        // 绉诲姩缁堢璇嗗埆涓?true
        assertTrue(RouterRepository.isMobileDevice("iPhone-14", "Apple, Inc."))
        assertTrue(RouterRepository.isMobileDevice("Galaxy-S23", "Samsung Electronics"))
        assertTrue(RouterRepository.isMobileDevice("Xiaomi-13-Pro", "Xiaomi Communications"))
        assertTrue(RouterRepository.isMobileDevice("HUAWEI-Mate-60", "Huawei Device Co., Ltd."))

        // 鍥哄畾鏈夌嚎缁堢锛堢數鑴戙€丯AS銆佺數瑙嗙洅銆佹墦鍗版満锛夎瘑鍒负 false
        assertFalse(RouterRepository.isMobileDevice("Desktop-PC", "Giga-Byte Technology"))
        assertFalse(RouterRepository.isMobileDevice("Synology-NAS", "Synology Incorporated"))
        assertFalse(RouterRepository.isMobileDevice("Apple-TV", "Apple, Inc."))
        assertFalse(RouterRepository.isMobileDevice("HP-LaserJet", "HP Inc."))
    }
```

- [x] **姝ラ 2锛氳繍琛屾祴璇曢獙璇佸け璐?*

杩愯娴嬭瘯鍛戒护锛?```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testClientDeviceClassification"
```
棰勬湡锛欶AIL锛宍isMobileDevice` 灏氭湭瀹氫箟銆?
- [x] **姝ラ 3锛氬湪 `RouterRepository.kt` 涓疄鐜?`isMobileDevice` 涓庢柟妗?A 鍦ㄧ嚎鍒ゅ畾绛栫暐**

鍦?`RouterRepository.kt` 鐨?companion object 涓畾涔?`isMobileDevice`锛?```kotlin
        fun isMobileDevice(hostname: String, vendor: String): Boolean {
            val name = hostname.lowercase()
            val ven = vendor.lowercase()
            val mobileKeywords = listOf(
                "iphone", "ipad", "ipod", "android", "galaxy", "xiaomi", "redmi",
                "huawei", "honor", "oppo", "vivo", "oneplus", "meizu", "pixel",
                "realme", "iqoo", "phone", "mobile", "pad", "tab"
            )
            // 鎺掗櫎 Apple TV / PC 绛夐潪绉诲姩璁惧
            if (name.contains("apple-tv") || name.contains("appletv")) return false
            return mobileKeywords.any { name.contains(it) || ven.contains(it) }
        }
```

鍦?`getConnectedClients()` 涓噰闆嗘椿璺冪绾︿笌闈欐€佺粦瀹氾細
1. 璁板綍 `activeLeaseMacs`锛堝湪 `getDHCPLeases` / `/tmp/dhcp.leases` 涓?`expires > 0` 鐨勮澶囷級銆?2. 璁板綍 `staticLeaseMacs`锛堜粠 `getStaticDhcpLeases` 璇诲彇鐨勮澶囷級銆?3. 鍒ゅ畾鏈€缁堢粓绔湪绾夸笌绫诲瀷锛?```kotlin
            val resultList = clientMap.values.map { client ->
                val mac = client.macAddress.lowercase()
                val isWifi = onlineWifiMap.containsKey(mac)
                val isMobile = isMobileDevice(client.hostname, client.vendor)
                val hasActiveLease = activeLeaseMacs.contains(mac) || staticLeaseMacs.contains(mac) || onlineLanMacs.contains(mac)

                val (isOnline, connType) = when {
                    isWifi -> Pair(true, onlineWifiMap[mac] ?: ConnectionType.WIFI_5G)
                    isMobile -> Pair(false, ConnectionType.WIFI_5G) // 绉诲姩缁堢鏈湪 WiFi 鍏宠仈鍒楄〃涓紝鏍囪涓虹绾挎棤绾?                    hasActiveLease -> Pair(true, ConnectionType.WIRED_LAN) // 鍥哄畾璁惧澶勪簬娲昏穬绉熸湡鍐咃紝鍒ゅ畾涓哄湪绾挎湁绾?                    else -> Pair(false, client.connectionType)
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

- [x] **姝ラ 4锛氳繍琛屾祴璇曢獙璇侀€氳繃**

杩愯娴嬭瘯鍛戒护锛?```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest --tests org.immortalwrt.manager.UbusModelsTest.testClientDeviceClassification"
```
棰勬湡锛歅ASS銆?
- [x] **姝ラ 5锛欳ommit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/data/repository/RouterRepository.kt android_app/app/src/test/java/org/immortalwrt/manager/UbusModelsTest.kt; git commit -m "fix(app): implement smart wired LAN client online detection"
```

---

### 浠诲姟 3锛氫紭鍖?CPU 鍗＄墖鍓爣棰樺苟灞曠ず ECM 纭欢鍔犻€熺姸鎬?(`DashboardScreen.kt`)

**鏂囦欢锛?*
- 淇敼锛歚android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt:175-195`

- [x] **姝ラ 1锛氫慨鏀?`DashboardScreen.kt` 涓殑 `cpuSubtitle` 閫昏緫**

鍦?`DashboardScreen.kt` 涓細
```kotlin
                    val hwe = state.overview?.hweUsage
                    val ecm = state.overview?.ecmStats
                    val loadAvg = state.overview?.cpuLoadAverage

                    val cpuSubtitle = when {
                        hwe != null && ecm != null -> "HWE: $hwe 路 ECM: $ecm"
                        ecm != null -> "ECM: $ecm 路 璐熻浇: ${loadAvg ?: "--"}"
                        hwe != null -> "HWE: $hwe 路 璐熻浇: ${loadAvg ?: "--"}"
                        else -> "骞冲潎璐熻浇: ${loadAvg ?: "--"}"
                    }
```

- [x] **姝ラ 2锛氬叏閲忚繍琛屾墍鏈夊崟鍏冩祴璇?*

杩愯娴嬭瘯鍛戒护锛?```powershell
cmd /c "cd android_app && gradlew testDebugUnitTest"
```
棰勬湡锛欱UILD SUCCESSFUL锛屾墍鏈夊崟鍏冩祴璇?100% 閫氳繃銆?
- [x] **姝ラ 3锛欳ommit**

```powershell
git add android_app/app/src/main/java/org/immortalwrt/manager/ui/screens/dashboard/DashboardScreen.kt; git commit -m "fix(app): show ECM stats and clean CPU card subtitle in Dashboard"
```

---

### 浠诲姟 4锛氭帹閫佸埌 Git 杩滅骞惰Е鍙?CI 鏋勫缓

**鏂囦欢锛?*
- 杩滅鍚屾

- [x] **姝ラ 1锛氭帹閫佹墍鏈?commit 鑷?GitHub main 鍒嗘敮**

杩愯鍛戒护锛?```powershell
git push origin main
```
棰勬湡锛欵verything up-to-date / Commit 鎴愬姛鎺ㄩ€併€?
