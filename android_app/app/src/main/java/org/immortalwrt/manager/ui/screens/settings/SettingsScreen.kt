package org.immortalwrt.manager.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.immortalwrt.manager.domain.model.*
import org.immortalwrt.manager.ui.theme.ErrorRed
import org.immortalwrt.manager.ui.theme.PrimaryBlue
import org.immortalwrt.manager.ui.theme.SuccessGreen
import org.immortalwrt.manager.ui.theme.WarningOrange

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
                        text = "设置与网络配置",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.loadWebSettings() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新配置")
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. WAN 外网连接与拨号 (与网页端同步)
            Text(
                text = "WAN 外网接入与拨号",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val protoLabel = when (state.wanConfig.proto) {
                                "pppoe" -> "PPPoE 宽带拨号"
                                "static" -> "静态 IP (Static)"
                                else -> "DHCP 动态获取 (客户端)"
                            }
                            Text("协议类型：$protoLabel", fontWeight = FontWeight.SemiBold)
                            if (state.wanConfig.proto == "pppoe") {
                                Text("拨号账号: ${state.wanConfig.username.ifBlank { "（未设置）" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else if (state.wanConfig.proto == "static") {
                                Text("静态 IP: ${state.wanConfig.ipaddr} · 网关: ${state.wanConfig.gateway}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.reconnectWan() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("重拨", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { viewModel.openEditWanDialog() },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("修改", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // 2. 局域网 (LAN) 与 IPv6 设置 (与网页端同步)
            Text(
                text = "局域网 (LAN) 与 IPv6 设置",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LAN IP 与掩码", fontWeight = FontWeight.SemiBold)
                            Text("${state.lanConfig.ipaddr} · ${state.lanConfig.netmask}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openEditLanDialog() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("修改", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. DHCP 服务与静态租约绑定 (与网页端同步)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DHCP 服务与静态租约绑定",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = { viewModel.openAddStaticLeaseDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("新增绑定")
                }
            }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DHCP 地址池与租期", fontWeight = FontWeight.SemiBold)
                            Text("起始: .${state.dhcpConfig.start} · 数量: ${state.dhcpConfig.limit} · 租期: ${state.dhcpConfig.leasetime}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openEditDhcpDialog() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("修改", fontSize = 12.sp)
                        }
                    }

                    if (state.staticLeases.isNotEmpty()) {
                        HorizontalDivider()
                        Text("静态 IP 绑定列表 (${state.staticLeases.size})：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        state.staticLeases.forEach { lease ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(lease.hostname, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    Text("${lease.ip} · ${lease.mac}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { viewModel.deleteStaticDhcpLease(lease.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除绑定", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 4. 防火墙、FullCone NAT 与端口转发
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "防火墙与端口转发",
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("FullCone NAT (全锥形 NAT)", fontWeight = FontWeight.SemiBold)
                            Text("游戏联机 / P2P 极速加速必备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.firewallAdvanced.fullconeNat,
                            onCheckedChange = {
                                viewModel.updateFirewallAdvanced(state.firewallAdvanced.copy(fullconeNat = it))
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SYN-flood 攻击防御", fontWeight = FontWeight.SemiBold)
                            Text("防范海量握手包洪水攻击", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.firewallAdvanced.synFlood,
                            onCheckedChange = {
                                viewModel.updateFirewallAdvanced(state.firewallAdvanced.copy(synFlood = it))
                            }
                        )
                    }

                    if (state.portForwardRules.isNotEmpty()) {
                        HorizontalDivider()
                        Text("端口转发规则列表：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        state.portForwardRules.forEach { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(rule.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        text = "WAN [${rule.proto.uppercase()} :${rule.srcPort}] ➔ ${rule.destIp}:${rule.destPort}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.deletePortForwardRule(rule.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "删除规则", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // 5. 系统管理、时间同步与 root 密码 (与网页端同步)
            Text(
                text = "系统主机名与管理维护",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("系统主机名与时区", fontWeight = FontWeight.SemiBold)
                            Text("${state.systemSettings.hostname} · ${state.systemSettings.zonename} (${state.systemSettings.timezone})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.openEditSystemDialog() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("修改", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("系统时间对齐", fontWeight = FontWeight.SemiBold)
                            Text("将路由器时钟与手机当前时间一键同步", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(
                            onClick = { viewModel.syncSystemTime() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("同步时间", fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("修改管理员 root 密码", fontWeight = FontWeight.SemiBold)
                            Text("在线安全修改 LuCI 与 SSH 密码", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { viewModel.openChangePwdDialog() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("更改密码", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 6. 多路由器节点管理
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
                                Text("活跃", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

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
                        HorizontalDivider()
                    }
                }
            }

            // 7. 外观与主题偏好
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
                            FilterChip(selected = state.themeMode == 0, onClick = { viewModel.setThemeMode(0) }, label = { Text("系统") })
                            FilterChip(selected = state.themeMode == 1, onClick = { viewModel.setThemeMode(1) }, label = { Text("浅色") })
                            FilterChip(selected = state.themeMode == 2, onClick = { viewModel.setThemeMode(2) }, label = { Text("深色") })
                        }
                    }

                    HorizontalDivider()

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
                }
            }

            // 8. 系统危险控制操作区
            Text(
                text = "高级系统控制与维护",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ErrorRed)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("重启路由器系统", fontWeight = FontWeight.SemiBold)
                            Text("平稳软重启整个系统并重新加载全部服务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { viewModel.openRebootDialog() },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("重启")
                        }
                    }

                    HorizontalDivider()

                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("退出登录当前路由器")
                    }
                }
            }

            // 9. 关于与软件版本
            Text(
                text = "关于软件",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("应用名称", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("路由管家 (ImmortalWrt Manager)", fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("客户端版本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v1.2.2 (Build 122)", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("适配平台", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("ImmortalWrt / OpenWrt 全系列", fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 对话框列表
        if (state.showEditWanDialog) {
            EditWanDialog(
                current = state.wanConfig,
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeEditWanDialog() },
                onSave = { updated -> viewModel.updateWanConfig(updated) }
            )
        }

        if (state.showAddNodeDialog) {
            AddNodeDialog(
                onDismiss = { viewModel.closeAddNodeDialog() },
                onSave = { alias, host, port, user, pwd, https ->
                    viewModel.saveNewNode(alias, host, port, user, pwd, https)
                }
            )
        }

        if (state.showAddStaticLeaseDialog) {
            AddStaticLeaseDialog(
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeAddStaticLeaseDialog() },
                onSave = { host, mac, ip ->
                    viewModel.addStaticDhcpLease(host, mac, ip)
                }
            )
        }

        if (state.showAddPortForwardDialog) {
            AddPortForwardDialog(
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeAddPortForwardDialog() },
                onSave = { name, proto, srcPort, destIp, destPort ->
                    viewModel.addPortForwardRule(name, proto, srcPort, destIp, destPort)
                }
            )
        }

        if (state.showEditLanDialog) {
            EditLanDialog(
                current = state.lanConfig,
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeEditLanDialog() },
                onSave = { ip, mask -> viewModel.updateLanConfig(ip, mask) }
            )
        }

        if (state.showEditDhcpDialog) {
            EditDhcpDialog(
                current = state.dhcpConfig,
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeEditDhcpDialog() },
                onSave = { start, limit, lease -> viewModel.updateDhcpConfig(start, limit, lease) }
            )
        }

        if (state.showEditSystemDialog) {
            EditSystemDialog(
                current = state.systemSettings,
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeEditSystemDialog() },
                onSave = { host, zone, tz -> viewModel.updateSystemSettings(host, zone, tz) }
            )
        }

        if (state.showChangePwdDialog) {
            ChangePasswordDialog(
                isOperating = state.isOperating,
                onDismiss = { viewModel.closeChangePwdDialog() },
                onSave = { newPwd -> viewModel.changeAdminPassword(newPwd) }
            )
        }

        if (state.showRebootDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.closeRebootDialog() },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange) },
                title = { Text("确认重启路由器？") },
                text = { Text("重启过程大约需要 1~2 分钟，期间所有连接将短暂中断。") },
                confirmButton = {
                    Button(
                        onClick = { viewModel.rebootRouter() },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("立即重启")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.closeRebootDialog() }) {
                        Text("取 消")
                    }
                }
            )
        }
    }
}

@Composable
fun EditWanDialog(
    current: WanNetworkConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (WanNetworkConfig) -> Unit
) {
    var proto by remember { mutableStateOf(current.proto) }
    var username by remember { mutableStateOf(current.username) }
    var password by remember { mutableStateOf(current.password) }
    var ipaddr by remember { mutableStateOf(current.ipaddr) }
    var netmask by remember { mutableStateOf(current.netmask) }
    var gateway by remember { mutableStateOf(current.gateway) }
    var dns by remember { mutableStateOf(current.dns) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 WAN 外网连接") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("接入协议类型：", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = proto == "dhcp", onClick = { proto = "dhcp" }, label = { Text("DHCP 动态") })
                    FilterChip(selected = proto == "pppoe", onClick = { proto = "pppoe" }, label = { Text("PPPoE 拨号") })
                    FilterChip(selected = proto == "static", onClick = { proto = "static" }, label = { Text("静态 IP") })
                }

                if (proto == "pppoe") {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("宽带账号 (PAP/CHAP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("宽带密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else if (proto == "static") {
                    OutlinedTextField(value = ipaddr, onValueChange = { ipaddr = it }, label = { Text("静态 IPv4 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = netmask, onValueChange = { netmask = it }, label = { Text("子网掩码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gateway, onValueChange = { gateway = it }, label = { Text("默认网关") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = dns, onValueChange = { dns = it }, label = { Text("自定义 DNS (空格分隔)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                } else {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp)) {
                        Text("DHCP 模式将由上级光猫或上级路由自动分配 IP 地址与 DNS 服务器。", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(current.copy(proto = proto, username = username, password = password, ipaddr = ipaddr, netmask = netmask, gateway = gateway, dns = dns)) },
                enabled = !isOperating
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保存并连接")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
}

@Composable
fun AddStaticLeaseDialog(
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var host by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("10.10.10.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增静态 DHCP 绑定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("主机名/备注名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mac, onValueChange = { mac = it }, label = { Text("物理 MAC 地址 (AA:BB:CC:DD:EE:FF)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("固定分配 IP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(host.ifBlank { "StaticHost" }, mac.trim(), ip.trim()) },
                enabled = !isOperating && mac.isNotBlank() && ip.isNotBlank()
            ) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
}

@Composable
fun EditLanDialog(
    current: LanNetworkConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var ip by remember { mutableStateOf(current.ipaddr) }
    var mask by remember { mutableStateOf(current.netmask) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 LAN 局域网接口") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = ip, onValueChange = { ip = it }, label = { Text("IPv4 地址 (如 192.168.1.1)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mask, onValueChange = { mask = it }, label = { Text("IPv4 子网掩码 (如 255.255.255.0)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("⚠️ 注意：修改 LAN IP 后，需要使用新 IP 重新登录应用。", color = WarningOrange, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(ip, mask) }, enabled = !isOperating && ip.isNotBlank()) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
}

@Composable
fun EditDhcpDialog(
    current: DhcpServerConfig,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String) -> Unit
) {
    var start by remember { mutableStateOf(current.start.toString()) }
    var limit by remember { mutableStateOf(current.limit.toString()) }
    var lease by remember { mutableStateOf(current.leasetime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 DHCP 服务配置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("起始分配 IP (如 100)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = limit, onValueChange = { limit = it }, label = { Text("最大分配数量 (如 150)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lease, onValueChange = { lease = it }, label = { Text("租期时长 (如 12h)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(start.toIntOrNull() ?: 100, limit.toIntOrNull() ?: 150, lease) }, enabled = !isOperating) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
}

@Composable
fun EditSystemDialog(
    current: SystemSettings,
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var host by remember { mutableStateOf(current.hostname) }
    var zone by remember { mutableStateOf(current.zonename) }
    var tz by remember { mutableStateOf(current.timezone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改系统属性") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("系统主机名 (Hostname)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = zone, onValueChange = { zone = it }, label = { Text("所在时区地区 (如 Asia/Shanghai)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tz, onValueChange = { tz = it }, label = { Text("时区代码 (如 CST-8)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { onSave(host, zone, tz) }, enabled = !isOperating && host.isNotBlank()) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
}

@Composable
fun ChangePasswordDialog(
    isOperating: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pwd1 by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改 root 管理员密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = pwd1, onValueChange = { pwd1 = it }, label = { Text("新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = pwd2, onValueChange = { pwd2 = it }, label = { Text("确认新密码") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (pwd1.isNotEmpty() && pwd2.isNotEmpty() && pwd1 != pwd2) {
                    Text("两次输入的密码不一致", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(pwd1) }, enabled = !isOperating && pwd1.isNotBlank() && pwd1 == pwd2) {
                if (isOperating) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("保 存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取 消") } }
    )
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

