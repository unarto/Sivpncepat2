package com.sivpn.cepat.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sivpn.cepat.model.MainUiState

@Composable
fun SettingsDialog(
    uiState: MainUiState,
    onDismiss: () -> Unit,
    onKeepAliveChange: (Int) -> Unit,
    onAutoCleanLogsChange: (Boolean, Int, Int) -> Unit
) {
    var keepAliveSec by remember { mutableStateOf(uiState.keepAliveInterval) }
    var autoCleanEnabled by remember { mutableStateOf(uiState.autoCleanLogsEnabled) }
    var autoCleanMins by remember { mutableStateOf(uiState.autoCleanInterval) }
    var maxLogLines by remember { mutableStateOf(uiState.maxLogLines) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Pengaturan Lanjutan",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // KeepAlive Interval
                Text(
                    text = "Interval Keep-Alive (Ping Ping):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(10, 30, 60, 120, 300).forEach { interval ->
                        val selected = keepAliveSec == interval
                        FilterChip(
                            selected = selected,
                            onClick = {
                                keepAliveSec = interval
                                onKeepAliveChange(interval)
                            },
                            label = { Text("${interval}s", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(16.dp))

                // Auto Clean Logs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Clean Logs Berkala", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Switch(
                        checked = autoCleanEnabled,
                        onCheckedChange = {
                            autoCleanEnabled = it
                            onAutoCleanLogsChange(it, autoCleanMins, maxLogLines)
                        }
                    )
                }

                if (autoCleanEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = autoCleanMins.toString(),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 60
                            autoCleanMins = v
                            onAutoCleanLogsChange(autoCleanEnabled, v, maxLogLines)
                        },
                        label = { Text("Interval Bersihkan Log (Menit)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxLogLines.toString(),
                        onValueChange = {
                            val v = it.toIntOrNull() ?: 500
                            maxLogLines = v
                            onAutoCleanLogsChange(autoCleanEnabled, autoCleanMins, v)
                        },
                        label = { Text("Maksimal Baris Log Terminal") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text("Selesai")
                    }
                }
            }
        }
    }
}
