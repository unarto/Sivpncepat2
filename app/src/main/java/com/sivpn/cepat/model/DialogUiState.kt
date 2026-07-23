package com.sivpn.cepat.model

data class DialogUiState(
    val showJniDownloader: Boolean = false,
    val showProfileDialog: Boolean = false,
    val showPayloadDialog: Boolean = false,
    val showTlsDialog: Boolean = false,
    val showAddProfileDialog: Boolean = false,
    val showLogDialog: Boolean = false,
    val showLimitDialog: Boolean = false,
    val showSplitTunnelingDialog: Boolean = false,
    val showKillSwitchDialog: Boolean = false,
    val showTetherDialog: Boolean = false,
    val showMenu: Boolean = false,
    val showDnsDropdown: Boolean = false
)
