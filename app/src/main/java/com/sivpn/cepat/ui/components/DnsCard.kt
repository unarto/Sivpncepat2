package com.sivpn.cepat.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun DnsCard(
    dns: String,
    onDnsClick: () -> Unit
) {
    VpnItemCard(
        icon = Icons.Default.Public,
        iconColor = Color(0xFF06B6D4),
        title = "DNS Custom Resolver",
        subtitle = if (dns.isEmpty()) "Standard (Google DNS: 8.8.8.8)" else dns,
        onClick = onDnsClick
    )
}
