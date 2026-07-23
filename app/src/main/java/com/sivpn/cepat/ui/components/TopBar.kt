package com.sivpn.cepat.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sivpn.cepat.model.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    uiState: MainUiState,
    onRefreshIp: () -> Unit,
    onSplitTunnelingClick: () -> Unit,
    onKillSwitchClick: () -> Unit,
    onTetherClick: () -> Unit,
    onLogClick: () -> Unit,
    onMenuToggle: (Boolean) -> Unit,
    onAddProfileClick: () -> Unit,
    onDeleteProfileClick: () -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onSpeedometerToggle: (Boolean) -> Unit,
    onImportFileClick: () -> Unit,
    onImportClipboardClick: () -> Unit,
    onExportFileClick: () -> Unit,
    onExportClipboardClick: () -> Unit
) {
    val context = LocalContext.current

    TopAppBar(
        title = {
            Column {
                Text(
                    text = "SIVPN Cepat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
                val ipLabel = if (uiState.currentPublicIp.isNotEmpty()) uiState.currentPublicIp else uiState.sshHost
                Row(
                    modifier = Modifier.clickable {
                        onRefreshIp()
                        Toast.makeText(context, "Memperbarui IP Publik...", Toast.LENGTH_SHORT).show()
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isVpnActive) {
                            if (uiState.connectionState == "CONNECTED") "Connected (IP: $ipLabel)" else "Connecting... (IP: $ipLabel)"
                        } else {
                            ipLabel
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh IP Publik",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSplitTunnelingClick) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = "Bypass Aplikasi",
                    tint = if (uiState.splitTunnelingEnabled) Color(0xFFF59E0B) else Color(0xFF334155)
                )
            }
            IconButton(onClick = onKillSwitchClick) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Kill Switch",
                    tint = if (uiState.killSwitchEnabled) Color(0xFFEF4444) else Color(0xFF334155)
                )
            }
            IconButton(onClick = onTetherClick) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = "Hotshare / Tethering",
                    tint = Color(0xFF334155)
                )
            }
            IconButton(onClick = onLogClick) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Terminal Logs",
                    tint = Color(0xFF334155)
                )
            }
            IconButton(onClick = { onMenuToggle(true) }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = Color(0xFF334155)
                )
            }
            DropdownMenu(
                expanded = uiState.showMenu,
                onDismissRequest = { onMenuToggle(false) }
            ) {
                DropdownMenuItem(
                    text = { Text("Add Profile") },
                    onClick = {
                        onMenuToggle(false)
                        onAddProfileClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete Current Profile") },
                    onClick = {
                        onMenuToggle(false)
                        onDeleteProfileClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hotspot Root") },
                    onClick = {
                        onMenuToggle(false)
                        onTetherClick()
                    }
                )
                Divider(color = Color(0x3394A3B8), thickness = 1.dp)
                DropdownMenuItem(
                    text = {
                        val themeText = when (uiState.themeMode) {
                            1 -> "Mode Tema: Terang"
                            2 -> "Mode Tema: Gelap"
                            else -> "Mode Tema: Sistem"
                        }
                        Text(themeText)
                    },
                    onClick = {
                        val nextMode = (uiState.themeMode + 1) % 3
                        onThemeModeChange(nextMode)
                    }
                )
                Divider(color = Color(0x3394A3B8), thickness = 1.dp)

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Speedometer", fontSize = 14.sp)
                            Switch(
                                checked = uiState.speedometerEnabled,
                                onCheckedChange = { active ->
                                    onMenuToggle(false)
                                    onSpeedometerToggle(active)
                                }
                            )
                        }
                    },
                    onClick = {
                        val nextState = !uiState.speedometerEnabled
                        onSpeedometerToggle(nextState)
                        onMenuToggle(false)
                    }
                )
                Divider(color = Color(0x3394A3B8), thickness = 1.dp)
                DropdownMenuItem(
                    text = { Text("Import Config (.sivpn)") },
                    onClick = {
                        onMenuToggle(false)
                        onImportFileClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Import dari Clipboard") },
                    onClick = {
                        onMenuToggle(false)
                        onImportClipboardClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export Config (.sivpn)") },
                    onClick = {
                        onMenuToggle(false)
                        onExportFileClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Export ke Clipboard") },
                    onClick = {
                        onMenuToggle(false)
                        onExportClipboardClick()
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color(0xFF1E293B)
        )
    )
}
