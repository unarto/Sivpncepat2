package com.sivpn.cepat.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sivpn.cepat.repository.LogRepository
import com.sivpn.cepat.ui.components.*
import com.sivpn.cepat.ui.dialogs.*
import com.sivpn.cepat.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val logs by LogRepository().logs.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri != null) {
                val json = viewModel.exportConfigContent()
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "Konfigurasi berhasil diekspor!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal mengekspor konfigurasi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { isStream ->
                        String(isStream.readBytes(), Charsets.UTF_8)
                    }
                    if (content != null && viewModel.importConfigContent(content)) {
                        Toast.makeText(context, "Konfigurasi berhasil diimpor!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Gagal mengimpor: Berkas tidak valid", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Gagal membaca berkas", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    val vpnGradientBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color(0xFFFFFFFF),
            0.5f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFE0F2FE),
            1.0f to Color(0xFF87CEEB)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vpnGradientBrush)
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    uiState = uiState,
                    onRefreshIp = { viewModel.restartPublicIpMonitor() },
                    onSplitTunnelingClick = { viewModel.setShowSplitTunnelingDialog(true) },
                    onKillSwitchClick = { viewModel.setShowKillSwitchDialog(true) },
                    onTetherClick = { viewModel.setShowTetherDialog(true) },
                    onLogClick = { viewModel.setShowLogDialog(true) },
                    onMenuToggle = { show -> viewModel.setShowMenu(show) },
                    onAddProfileClick = { viewModel.setShowAddProfileDialog(true) },
                    onDeleteProfileClick = {
                        if (!viewModel.deleteCurrentProfile()) {
                            Toast.makeText(context, "Tidak dapat menghapus profile terakhir!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onThemeModeChange = { mode -> viewModel.updateThemeMode(mode) },
                    onSpeedometerToggle = { active -> viewModel.updateSpeedometerEnabled(active) },
                    onImportFileClick = { importLauncher.launch(arrayOf("*/*")) },
                    onImportClipboardClick = {
                        if (viewModel.importFromClipboard()) {
                            Toast.makeText(context, "Konfigurasi berhasil diimpor!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Gagal mengimpor dari clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportFileClick = { exportLauncher.launch("${uiState.currentProfile}.sivpn") },
                    onExportClipboardClick = {
                        if (viewModel.exportToClipboard()) {
                            Toast.makeText(context, "Konfigurasi disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    JniWarningCard(
                        isNativeSshLoadedState = uiState.isNativeSshLoadedState,
                        isHevLoadedState = uiState.isHevLoadedState,
                        onDownloadClick = { viewModel.setShowJniDownloader(true) }
                    )

                    SectionHeader("CONTROLLER")
                    ConnectionCard(
                        isVpnActive = uiState.isVpnActive,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StatusCard(
                        uiState = uiState,
                        onSetTimeLimitClick = { viewModel.setShowLimitDialog(true) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader("CONFIGURATION")

                    ProfileCard(
                        currentProfile = uiState.currentProfile,
                        onProfileClick = { viewModel.setShowProfileDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    PayloadCard(
                        payload = uiState.payload,
                        onPayloadClick = { viewModel.setShowPayloadDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProxyCard(
                        proxyFullInput = uiState.proxyFullInput,
                        onProxyClick = { viewModel.setShowTlsDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SshCard(
                        sshHost = uiState.sshHost,
                        sshPort = uiState.sshPort,
                        sshUsername = uiState.sshUsername,
                        onSshClick = { viewModel.setShowAddProfileDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DnsCard(
                        dns = uiState.dns,
                        onDnsClick = { viewModel.setShowDnsDropdown(true) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader("SETTINGS")

                    SplitTunnelingCard(
                        enabled = uiState.splitTunnelingEnabled,
                        bypassCount = uiState.bypassApps.size,
                        onClick = { viewModel.setShowSplitTunnelingDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    KillSwitchCard(
                        enabled = uiState.killSwitchEnabled,
                        onClick = { viewModel.setShowKillSwitchDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LogCard(
                        lastLog = logs.lastOrNull() ?: "",
                        onClick = { viewModel.setShowLogDialog(true) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SettingsCard(
                        onClick = { viewModel.setShowLimitDialog(true) }
                    )
                }
            }
        }

        // Active Dialogs rendering
        if (uiState.showPayloadDialog) {
            PayloadDialog(
                initialPayload = uiState.payload,
                onDismiss = { viewModel.setShowPayloadDialog(false) },
                onSave = { newPayload -> viewModel.updatePayload(newPayload) }
            )
        }
        if (uiState.showProfileDialog) {
            ProfileDialog(
                currentProfile = uiState.currentProfile,
                profileList = uiState.profileList,
                onDismiss = { viewModel.setShowProfileDialog(false) },
                onSelectProfile = { profile -> viewModel.selectProfile(profile) },
                onAddProfileClick = { viewModel.setShowAddProfileDialog(true) }
            )
        }
        if (uiState.showAddProfileDialog) {
            SshDialog(
                initialSshFullInput = uiState.sshFullInput,
                onDismiss = { viewModel.setShowAddProfileDialog(false) },
                onSave = { input -> viewModel.updateSshFullInput(input) }
            )
        }
        if (uiState.showTlsDialog) {
            ProxyDialog(
                initialProxyFullInput = uiState.proxyFullInput,
                onDismiss = { viewModel.setShowTlsDialog(false) },
                onSave = { input -> viewModel.updateProxyFullInput(input) }
            )
        }
        if (uiState.showLogDialog) {
            LogDialog(
                logs = logs,
                onDismiss = { viewModel.setShowLogDialog(false) }
            )
        }
        if (uiState.showSplitTunnelingDialog) {
            SplitTunnelDialog(
                initialEnabled = uiState.splitTunnelingEnabled,
                initialFilterMode = uiState.appsFilterMode,
                initialBypassApps = uiState.bypassApps,
                onDismiss = { viewModel.setShowSplitTunnelingDialog(false) },
                onSave = { enabled, mode, apps -> viewModel.updateSplitTunneling(enabled, mode, apps) }
            )
        }
        if (uiState.showKillSwitchDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.setShowKillSwitchDialog(false) },
                title = { Text("Kill Switch") },
                text = { Text("Aktifkan Kill Switch untuk memblokir seluruh koneksi internet ketika VPN terputus secara tidak terduga.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.updateKillSwitch(!uiState.killSwitchEnabled)
                        viewModel.setShowKillSwitchDialog(false)
                    }) {
                        Text(if (uiState.killSwitchEnabled) "Nonaktifkan" else "Aktifkan")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.setShowKillSwitchDialog(false) }) {
                        Text("Batal")
                    }
                }
            )
        }
        if (uiState.showLimitDialog) {
            SettingsDialog(
                uiState = uiState,
                onDismiss = { viewModel.setShowLimitDialog(false) },
                onKeepAliveChange = { sec -> },
                onAutoCleanLogsChange = { enabled, mins, maxLines -> }
            )
        }
        if (uiState.showTetherDialog) {
            TetherDialog(onDismiss = { viewModel.setShowTetherDialog(false) })
        }
        if (uiState.showJniDownloader) {
            JniDownloaderDialog(onDismiss = { viewModel.setShowJniDownloader(false) })
        }
    }
}
