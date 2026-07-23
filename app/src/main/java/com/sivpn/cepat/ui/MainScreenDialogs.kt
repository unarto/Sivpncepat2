package com.sivpn.cepat.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import com.sivpn.cepat.model.DialogUiState
import com.sivpn.cepat.model.MainUiState
import com.sivpn.cepat.ui.dialogs.*
import com.sivpn.cepat.viewmodel.DialogViewModel
import com.sivpn.cepat.viewmodel.MainViewModel

@Composable
fun MainScreenDialogs(
    uiState: MainUiState,
    dialogState: DialogUiState,
    viewModel: MainViewModel,
    dialogViewModel: DialogViewModel,
    logs: List<String>
) {
    if (dialogState.showPayloadDialog) {
        PayloadDialog(
            initialPayload = uiState.payload,
            onDismiss = { dialogViewModel.setShowPayloadDialog(false) },
            onSave = { newPayload -> viewModel.updatePayload(newPayload) }
        )
    }

    if (dialogState.showProfileDialog) {
        ProfileDialog(
            currentProfile = uiState.currentProfile,
            profileList = uiState.profileList,
            onDismiss = { dialogViewModel.setShowProfileDialog(false) },
            onSelectProfile = { profile -> viewModel.selectProfile(profile) },
            onAddProfileClick = { dialogViewModel.setShowAddProfileDialog(true) }
        )
    }

    if (dialogState.showAddProfileDialog) {
        SshDialog(
            initialSshFullInput = uiState.sshFullInput,
            onDismiss = { dialogViewModel.setShowAddProfileDialog(false) },
            onSave = { input -> viewModel.updateSshFullInput(input) }
        )
    }

    if (dialogState.showTlsDialog) {
        ProxyDialog(
            initialProxyFullInput = uiState.proxyFullInput,
            onDismiss = { dialogViewModel.setShowTlsDialog(false) },
            onSave = { input -> viewModel.updateProxyFullInput(input) }
        )
    }

    if (dialogState.showLogDialog) {
        LogDialog(
            logs = logs,
            onDismiss = { dialogViewModel.setShowLogDialog(false) }
        )
    }

    if (dialogState.showSplitTunnelingDialog) {
        SplitTunnelDialog(
            initialEnabled = uiState.splitTunnelingEnabled,
            initialFilterMode = uiState.appsFilterMode,
            initialBypassApps = uiState.bypassApps,
            onDismiss = { dialogViewModel.setShowSplitTunnelingDialog(false) },
            onSave = { enabled, mode, apps -> viewModel.updateSplitTunneling(enabled, mode, apps) }
        )
    }

    if (dialogState.showKillSwitchDialog) {
        AlertDialog(
            onDismissRequest = { dialogViewModel.setShowKillSwitchDialog(false) },
            title = { Text("Kill Switch") },
            text = { Text("Aktifkan Kill Switch untuk memblokir seluruh koneksi internet ketika VPN terputus secara tidak terduga.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateKillSwitch(!uiState.killSwitchEnabled)
                    dialogViewModel.setShowKillSwitchDialog(false)
                }) {
                    Text(if (uiState.killSwitchEnabled) "Nonaktifkan" else "Aktifkan")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogViewModel.setShowKillSwitchDialog(false) }) {
                    Text("Batal")
                }
            }
        )
    }

    if (dialogState.showLimitDialog) {
        SettingsDialog(
            uiState = uiState,
            onDismiss = { dialogViewModel.setShowLimitDialog(false) },
            onKeepAliveChange = { sec -> },
            onAutoCleanLogsChange = { enabled, mins, maxLines -> }
        )
    }

    if (dialogState.showTetherDialog) {
        TetherDialog(onDismiss = { dialogViewModel.setShowTetherDialog(false) })
    }

    if (dialogState.showJniDownloader) {
        JniDownloaderDialog(onDismiss = { dialogViewModel.setShowJniDownloader(false) })
    }
}
