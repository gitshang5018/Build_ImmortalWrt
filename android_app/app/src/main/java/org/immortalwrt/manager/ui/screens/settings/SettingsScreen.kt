package org.immortalwrt.manager.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.domain.model.FirewallRedirectRule
import org.immortalwrt.manager.domain.model.RouterNode
import org.immortalwrt.manager.ui.theme.ErrorRed
import org.immortalwrt.manager.ui.theme.PrimaryBlue
import org.immortalwrt.manager.ui.theme.SecondaryCyan
import org.immortalwrt.manager.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) {
            onLogout()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置与高级网络",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 多路由器节点管理
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "多路由器节点管理",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { viewModel.openAddNodeDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加节点")
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 当前活跃节点
                    Surface(
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).background(SuccessGreen, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("当前连接路由", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Text(
                                    text = "${state.currentCredentials.host}:${state.currentCredentials.port} (${if (state.currentCredentials.useHttps) "HTTPS" else "HTTP"})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(color = PrimaryBlue, shape = RoundedCornerShape(6.dp)) {
                                Text("活跃", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // 保存的其他节点列表
                    state.savedNodes.filter { it.credentials.host != state.currentCredentials.host }.forEach { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(node.alias, fontWeight = FontWeight.SemiBold)
                                Text("${node.credentials.host}:${node.credentials.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                OutlinedButton(
                                    onClick = { viewModel.switchActiveNode(node) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("切 换", fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(onClick = { viewModel.deleteNode(node.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Divider()
                    }
                }
            }

            // 2. 防火墙端口转发管理
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "防火墙端口转发 (Port Forwarding)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { viewModel.openAddPortForwardDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增规则")
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.isLoadingRules) {
                        Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else if (state.portForwardRules.isEmpty()) {
                        Text("暂无配置的端口转发规则", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.portForwardRules.forEach { rule ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(rule.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "WAN [${rule.proto.uppercase()} :${rule.srcPort}] ➔ ${rule.destIp}:${rule.destPort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deletePortForwardRule(rule.id) },
                                    enabled = !state.isOperating
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除规则", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            Divider()
                        }
                    }
                }
            }

            // 3. 外观与主题偏好
            Text(
                text = "外观与系统偏好",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("深色主题模式", fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = state.themeMode == 0,
                                onClick = { viewModel.setThemeMode(0) },
                                label = { Text("跟随系统") }
                            )
                            FilterChip(
                                selected = state.themeMode == 1,
                                onClick = { viewModel.setThemeMode(1) },
                                label = { Text("浅色") }
                            )
                            FilterChip(
                                selected = state.themeMode == 2,
                                onClick = { viewModel.setThemeMode(2) },
                                label = { Text("深色") }
                            )
                        }
                    }

                    Divider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Material You 动态色彩", fontWeight = FontWeight.Medium)
                            Text("依据系统壁纸自适应配色 (Android 12+)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.dynamicColor,
                            onCheckedChange = { viewModel.setDynamicColor(it) }
                        )
                    }

                    Divider()

                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("退出登录当前路由器")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 添加节点对话框
        if (state.showAddNodeDialog) {
            AddNodeDialog(
                onDismiss = { viewModel.closeAddNodeDialog() },
                onSave = { alias, host, port, user, pwd, https ->
                    viewModel.saveNewNode(alias, host, port, user, pwd, https)
                }
            )
        }

        // 添加端口转发对话框
        if (state.showAddPortForwardDialog) {
            AddPortForwardDialog(
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeAddPortForwardDialog() },
                onSave = { name, proto, srcPort, destIp, destPort ->
                    viewModel.addPortForwardRule(name, proto, srcPort, destIp, destPort)
                }
            )
        }
    }
}

@Composable
fun AddNodeDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String, Boolean) -> Unit
) {
    var alias by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("10.10.10.1") }
    var port by remember { mutableStateOf("80") }
    var user by remember { mutableStateOf("root") }
    var pwd by remember { mutableStateOf("") }
    var https by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加路由器节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = alias, onValueChange = { alias = it }, label = { Text("节点备注 (如: 家中/公司)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("路由器 IP / 域名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = user, onValueChange = { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = pwd, onValueChange = { pwd = it }, label = { Text("登录密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("使用 HTTPS 传输")
                    Switch(checked = https, onCheckedChange = { https = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(alias.ifBlank { host }, host, port.toIntOrNull() ?: 80, user, pwd, https) },
                enabled = host.isNotBlank()
            ) {
                Text("保 存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取 消") }
        }
    )
}

@Composable
fun AddPortForwardDialog(
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var proto by remember { mutableStateOf("tcp") }
    var srcPort by remember { mutableStateOf("") }
    var destIp by remember { mutableStateOf("192.168.1.100") }
    var destPort by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增端口转发规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("规则名称 (如 WebServer)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = proto, onValueChange = { proto = it }, label = { Text("协议 (tcp/udp)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = srcPort, onValueChange = { srcPort = it }, label = { Text("外部端口") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = destIp, onValueChange = { destIp = it }, label = { Text("内网目标 IP") }, singleLine = true, modifier = Modifier.weight(1.3f))
                    OutlinedTextField(value = destPort, onValueChange = { destPort = it }, label = { Text("内部端口") }, singleLine = true, modifier = Modifier.weight(0.9f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.ifBlank { "Forward_$srcPort" }, proto, srcPort, destIp, destPort) },
                enabled = !isOperating && srcPort.isNotBlank() && destIp.isNotBlank()
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isOperating) { Text("取 消") }
        }
    )
}
