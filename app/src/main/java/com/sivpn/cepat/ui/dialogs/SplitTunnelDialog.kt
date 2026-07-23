package com.sivpn.cepat.ui.dialogs

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(
    val name: String,
    val packageName: String,
    val iconDrawable: android.graphics.drawable.Drawable?
)

@Composable
fun SplitTunnelDialog(
    initialEnabled: Boolean,
    initialFilterMode: String,
    initialBypassApps: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Boolean, String, Set<String>) -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(initialEnabled) }
    var filterMode by remember { mutableStateOf(initialFilterMode) }
    var selectedApps by remember { mutableStateOf(initialBypassApps) }
    var appList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = packages.mapNotNull { appInfo ->
                if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                    AppItem(
                        name = appInfo.loadLabel(pm).toString(),
                        packageName = appInfo.packageName,
                        iconDrawable = appInfo.loadIcon(pm)
                    )
                } else null
            }.sortedBy { it.name.lowercase() }
            appList = items
            isLoading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Split Tunneling (Bypass Apps)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Aktifkan Split Tunneling", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }

                if (enabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterMode == "bypass",
                            onClick = { filterMode = "bypass" },
                            label = { Text("Bypass Terpilih", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = filterMode == "filter",
                            onClick = { filterMode = "filter" },
                            label = { Text("Hanya Lewatkan Terpilih", fontSize = 11.sp) }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(appList) { app ->
                                val isChecked = selectedApps.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedApps = if (isChecked) {
                                                selectedApps - app.packageName
                                            } else {
                                                selectedApps + app.packageName
                                            }
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    app.iconDrawable?.let { drawable ->
                                        Image(
                                            bitmap = drawable.toBitmap(48, 48).asImageBitmap(),
                                            contentDescription = app.name,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(app.packageName, fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedApps = if (checked) {
                                                selectedApps + app.packageName
                                            } else {
                                                selectedApps - app.packageName
                                            }
                                        }
                                    )
                                }
                                Divider(color = Color(0xFFF1F5F9))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color(0xFF64748B))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(enabled, filterMode, selectedApps)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
