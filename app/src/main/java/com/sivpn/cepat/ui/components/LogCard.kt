package com.sivpn.cepat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LogCard(
    lastLog: String,
    onClick: () -> Unit
) {
    VpnItemCard(
        icon = Icons.Default.Terminal,
        iconColor = Color(0xFF6366F1),
        title = "Terminal Logs",
        subtitle = if (lastLog.isEmpty()) "Lihat Log Real-time" else lastLog,
        onClick = onClick
    )
}
