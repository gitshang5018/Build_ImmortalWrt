package org.immortalwrt.manager.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.domain.model.*
import org.immortalwrt.manager.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "插件中枢与系统工具",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadPlugins()
                        viewModel.loadLogs()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("常用插件 (${state.plugins.size})") },
                    icon = { Icon(Icons.Default.Extension, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("系统日志") },
                    icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("网络诊断") },
                    icon = { Icon(Icons.Default.Speed, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> PluginsTabContent(state, viewModel)
                1 -> SystemLogsTabContent(state, viewModel)
                2 -> DiagnosticsTabContent(state, viewModel)
            }
        }

        // 插件配置弹窗
        state.activeConfigPlugin?.let { plugin ->
            when (plugin.id) {
                "passwall" -> {
                    state.passwallConfig?.let { cfg ->
                        PasswallConfigDialog(
                            config = cfg,
                            isOperating = state.isOperating,
                            onDismiss = { viewModel.closePluginConfig() },
                            onSave = { updated -> viewModel.savePasswallConfig(updated) }
                        )
                    }
                }
                "openclash" -> {
                    state.openclashConfig?.let { cfg ->
                        OpenClashConfigDialog(
                            config = cfg,
                            isOperating = state.isOperating,
                            onDismiss = { viewModel.closePluginConfig() },
                            onSave = { updated -> viewModel.saveOpenClashConfig(updated) },
                            onOpenWebUi = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://10.10.10.1:9090/ui/"))
                                context.startActivity(intent)
                            }
                        )
                    }
                }
                "mosdns" -> {
                    state.mosdnsConfig?.let { cfg ->
                        MosdnsConfigDialog(
                            config = cfg,
                            isOperating = state.isOperating,
                            onDismiss = { viewModel.closePluginConfig() },
                            onSave = { updated -> viewModel.saveMosdnsConfig(updated) }
                        )
                    }
                }
                else -> {
                    GenericPluginDialog(
                        plugin = plugin,
                        onDismiss = { viewModel.closePluginConfig() },
                        onOpenWeb = { port ->
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://10.10.10.1:$port/"))
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PluginsTabContent(state: ToolsUiState, viewModel: ToolsViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = state.selectedPluginCategory == null,
                    onClick = { viewModel.selectPluginCategory(null) },
                    label = { Text("全部插件") }
                )
            }
            items(PluginCategory.entries) { cat ->
                FilterChip(
                    selected = state.selectedPluginCategory == cat,
                    onClick = { viewModel.selectPluginCategory(cat) },
                    label = { Text(cat.title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.isLoadingPlugins) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filteredPlugins) { plugin ->
                    PluginCard(
                        plugin = plugin,
                        isOperating = state.isOperating,
                        onAction = { action -> viewModel.controlPlugin(plugin.serviceName, action) },
                        onConfigure = { viewModel.openPluginConfig(plugin) }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun PluginCard(
    plugin: PluginServiceInfo,
    isOperating: Boolean,
    onAction: (String) -> Unit,
    onConfigure: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (plugin.isRunning) SuccessGreen else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = plugin.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = plugin.category.title,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (plugin.isRunning) SuccessGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (plugin.isRunning) "运行中" else "已停止",
                        color = if (plugin.isRunning) SuccessGreen else Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onConfigure,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("设置参数", fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (plugin.isRunning) {
                        OutlinedButton(
                            onClick = { onAction("restart") },
                            enabled = !isOperating,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("重启", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onAction("stop") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            enabled = !isOperating,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("停止", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { onAction("start") },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            enabled = !isOperating,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("启动服务", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswallConfigDialog(
    config: PasswallConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (PasswallConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.isEnabled) }
    var mode by remember { mutableStateOf(config.proxyMode) }
    var dnsMode by remember { mutableStateOf(config.dnsMode) }
    var remoteDns by remember { mutableStateOf(config.remoteDns) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PassWall 代理配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("主服务总开关", fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text("TCP 代理运行模式：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "chnroute", onClick = { mode = "chnroute" }, label = { Text("中国列表外") })
                    FilterChip(selected = mode == "gfwlist", onClick = { mode = "gfwlist" }, label = { Text("GFW列表") })
                    FilterChip(selected = mode == "global", onClick = { mode = "global" }, label = { Text("全局") })
                }

                OutlinedTextField(
                    value = dnsMode,
                    onValueChange = { dnsMode = it },
                    label = { Text("DNS 过滤转发模式 (dns2socks/smartdns)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = remoteDns,
                    onValueChange = { remoteDns = it },
                    label = { Text("远端防污染 DNS (如 1.1.1.1, 8.8.8.8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(config.copy(isEnabled = enabled, proxyMode = mode, dnsMode = dnsMode, remoteDns = remoteDns)) },
                enabled = !isOperating
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保存并应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取 消") }
        }
    )
}

@Composable
fun OpenClashConfigDialog(
    config: OpenClashConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (OpenClashConfig) -> Unit,
    onOpenWebUi: () -> Unit
) {
    var enabled by remember { mutableStateOf(config.isEnabled) }
    var mode by remember { mutableStateOf(config.operationMode) }
    var coreType by remember { mutableStateOf(config.coreType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OpenClash (Meta) 代理配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用 OpenClash", fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text("运行模式：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "fake-ip", onClick = { mode = "fake-ip" }, label = { Text("Fake-IP (混合)") })
                    FilterChip(selected = mode == "redir-host", onClick = { mode = "redir-host" }, label = { Text("Redir-Host") })
                    FilterChip(selected = mode == "tun", onClick = { mode = "tun" }, label = { Text("TUN 模式") })
                }

                Text("内核类型：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = coreType == "Meta", onClick = { coreType = "Meta" }, label = { Text("Mihomo (Meta)") })
                    FilterChip(selected = coreType == "DEV", onClick = { coreType = "DEV" }, label = { Text("DEV 官方") })
                }

                Button(
                    onClick = onOpenWebUi,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开 Clash 控制台 (WebUI)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(config.copy(isEnabled = enabled, operationMode = mode, coreType = coreType)) },
                enabled = !isOperating
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保存并生效")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取 消") }
        }
    )
}

@Composable
fun MosdnsConfigDialog(
    config: MosdnsConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (MosdnsConfig) -> Unit
) {
    var enabled by remember { mutableStateOf(config.isEnabled) }
    var port by remember { mutableStateOf(config.listenPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MosDNS 分流配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("启用 MosDNS", fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("本地监听端口 (默认 5335)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "MosDNS 会将国内域名直连国内快速 DNS，国外域名转发加密防污染 DNS。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(config.copy(isEnabled = enabled, listenPort = port.toIntOrNull() ?: 5335)) },
                enabled = !isOperating
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取 消") }
        }
    )
}

@Composable
fun GenericPluginDialog(
    plugin: PluginServiceInfo,
    onDismiss: () -> Unit,
    onOpenWeb: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(plugin.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(plugin.description, style = MaterialTheme.typography.bodyMedium)

                if (plugin.webPort != null) {
                    Button(
                        onClick = { onOpenWeb(plugin.webPort) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("打开 Web 管理控制台 (端口 :${plugin.webPort})")
                    }
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "该插件由系统守护进程托管，可通过左下角按钮启动、停止或重启服务。",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("关 闭") }
        }
    )
}

@Composable
fun SystemLogsTabContent(state: ToolsUiState, viewModel: ToolsViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.logSearchQuery,
            onValueChange = { viewModel.onLogSearchChange(it) },
            placeholder = { Text("搜索日志关键字 (如 crash, auth, wan)") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.logFilterLevel == "ALL",
                onClick = { viewModel.onLogFilterLevelChange("ALL") },
                label = { Text("全部日志") }
            )
            FilterChip(
                selected = state.logFilterLevel == "WARN",
                onClick = { viewModel.onLogFilterLevelChange("WARN") },
                label = { Text("仅警告与错误") }
            )
            FilterChip(
                selected = state.logFilterLevel == "ERR",
                onClick = { viewModel.onLogFilterLevelChange("ERR") },
                label = { Text("仅错误") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isLoadingLogs) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.filteredLogs) { log ->
                        val logColor = when {
                            log.level.contains("err", ignoreCase = true) -> Color(0xFFFF5252)
                            log.level.contains("warn", ignoreCase = true) -> Color(0xFFFFD700)
                            else -> Color(0xFFD4D4D4)
                        }
                        Text(
                            text = "[${log.timestamp}] [${log.level.uppercase()}] ${log.message}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = logColor,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DiagnosticsTabContent(state: ToolsUiState, viewModel: ToolsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.diagMode == DiagnosticMode.PING,
                onClick = { viewModel.onDiagModeChange(DiagnosticMode.PING) },
                label = { Text("Ping 时延测试") }
            )
            FilterChip(
                selected = state.diagMode == DiagnosticMode.NSLOOKUP,
                onClick = { viewModel.onDiagModeChange(DiagnosticMode.NSLOOKUP) },
                label = { Text("DNS 解析探测") }
            )
            FilterChip(
                selected = state.diagMode == DiagnosticMode.TRACEROUTE,
                onClick = { viewModel.onDiagModeChange(DiagnosticMode.TRACEROUTE) },
                label = { Text("Traceroute 追踪") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.diagTarget,
                onValueChange = { viewModel.onDiagTargetChange(it) },
                label = { Text(if (state.diagMode == DiagnosticMode.NSLOOKUP) "输入待解析域名 (如 baidu.com)" else "输入目标 IP / 域名") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { viewModel.runDiagnostics() },
                enabled = !state.isDiagnosing,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(54.dp)
            ) {
                if (state.isDiagnosing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("执 行")
                }
            }
        }

        if (state.diagResult != null) {
            Text(
                text = "诊断输出回显：",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )

            Surface(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.diagResult!!,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF81C784),
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

