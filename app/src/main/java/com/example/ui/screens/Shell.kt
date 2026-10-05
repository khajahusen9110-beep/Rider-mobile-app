package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.NotificationsDialog
import com.example.ui.components.RiderBottomNav
import com.example.ui.components.RiderTopBar

/** Top bar with the notifications bell, plus the bottom tabs when [tab] is set. */
@Composable
fun RiderShell(
    viewModel: RiderViewModel,
    title: String,
    tab: Screen? = null,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        topBar = {
            if (showTopBar) {
                RiderTopBar(
                    title = title,
                    onBack = onBack,
                    unreadNotifications = notifications.count { !it.isRead },
                    onNotifications = { showNotifications = true; viewModel.loadNotifications() },
                    actions = actions,
                )
            }
        },
        bottomBar = { if (tab != null) RiderBottomNav(tab, viewModel::navigate) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
    if (showNotifications) {
        NotificationsDialog(
            notifications,
            onDismiss = { showNotifications = false; viewModel.markNotificationsRead() },
            onOpenRide = viewModel::openRide,
        )
    }
}
