package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.*
import com.example.ui.screens.*
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoRideTheme {
                CustomerAppRoot()
            }
        }
    }
}

@Composable
fun CustomerAppRoot(viewModel: CustomerAppViewModel = viewModel()) {
    val authState by viewModel.authUiState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val notification by viewModel.notification.collectAsState()
    val activeRide by viewModel.activeRide.collectAsState()
    val selectedInvoice by viewModel.selectedInvoice.collectAsState()

    var showActiveRideDetails by remember { mutableStateOf(false) }

    LaunchedEffect(activeRide?.id, activeRide?.status) {
        if (activeRide != null && activeRide?.status != "completed" && activeRide?.status != "cancelled") {
            showActiveRideDetails = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (authState is AuthUiState.Authenticated) {
                CustomerBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = {
                        viewModel.setTab(it)
                        showActiveRideDetails = false
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = authState) {
                is AuthUiState.Unauthenticated, is AuthUiState.OtpSent, is AuthUiState.Suspended -> {
                    AuthScreen(viewModel = viewModel, authState = state)
                }
                is AuthUiState.Authenticated -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top App Bar
                        CustomerTopBar(
                            tab = currentTab,
                            hasActiveRide = activeRide != null && activeRide?.status != "completed" && activeRide?.status != "cancelled",
                            isShowingActiveRide = showActiveRideDetails,
                            onToggleActiveRide = { showActiveRideDetails = !showActiveRideDetails }
                        )

                        // Main Content depending on currentTab
                        Box(modifier = Modifier.weight(1f)) {
                            when (currentTab) {
                                AppTab.HOME -> {
                                    if (activeRide != null && showActiveRideDetails) {
                                        ActiveRideScreen(
                                            viewModel = viewModel,
                                            ride = activeRide!!
                                        )
                                    } else {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            // Active ride banner if collapsed
                                            if (activeRide != null && (activeRide?.status != "completed" && activeRide?.status != "cancelled")) {
                                                Surface(
                                                    color = AmberLight,
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                                        .testTag("active_ride_banner")
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .padding(12.dp)
                                                            .fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("🚕", fontSize = 18.sp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column {
                                                                Text("Active Booking in Progress", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Slate900)
                                                                Text("Status: ${activeRide?.status}", fontSize = 11.sp, color = AmberPrimary)
                                                            }
                                                        }
                                                        Button(
                                                            onClick = { showActiveRideDetails = true },
                                                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp)
                                                        ) {
                                                            Text("Track", fontSize = 12.sp, color = Color.White)
                                                        }
                                                    }
                                                }
                                            }

                                            HomeScreen(
                                                viewModel = viewModel,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                AppTab.BOOKINGS -> {
                                    BookingsScreen(viewModel = viewModel)
                                }
                                AppTab.WALLET -> {
                                    WalletScreen(viewModel = viewModel)
                                }
                                AppTab.PROFILE -> {
                                    ProfileScreen(viewModel = viewModel, profile = state.profile)
                                }
                            }
                        }
                    }
                }
            }

            // Notification Banner at top
            notification?.let { notif ->
                LaunchedEffect(notif.timestamp) {
                    kotlinx.coroutines.delay(4000)
                    viewModel.dismissNotification()
                }

                Surface(
                    color = if (notif.isError) RoseError else Slate900,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth()
                        .testTag("notification_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = notif.message,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissNotification() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Printable Invoice Modal (Section 8)
            selectedInvoice?.let { invoice ->
                InvoiceDialog(
                    invoice = invoice,
                    onDismiss = { viewModel.closeInvoice() }
                )
            }
        }
    }

    BackHandler(enabled = showActiveRideDetails) {
        showActiveRideDetails = false
    }
}

@Composable
fun CustomerTopBar(
    tab: AppTab,
    hasActiveRide: Boolean,
    isShowingActiveRide: Boolean,
    onToggleActiveRide: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AmberPrimary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⚡", fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "GoRide & Cargo",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (tab) {
                            AppTab.HOME -> if (isShowingActiveRide) "Live Ride Tracking" else "Passenger & Goods"
                            AppTab.BOOKINGS -> "Booking History"
                            AppTab.WALLET -> "Wallet & Balance"
                            AppTab.PROFILE -> "Customer Profile"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tab == AppTab.HOME && hasActiveRide) {
                OutlinedButton(
                    onClick = onToggleActiveRide,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isShowingActiveRide) SkyLight else AmberLight
                    )
                ) {
                    Text(
                        text = if (isShowingActiveRide) "➕ New Ride" else "📍 Tracking",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShowingActiveRide) SkyAccent else AmberPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerBottomNavigation(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        windowInsets = NavigationBarDefaults.windowInsets
    ) {
        NavigationBarItem(
            selected = currentTab == AppTab.HOME,
            onClick = { onTabSelected(AppTab.HOME) },
            icon = {
                Icon(
                    if (currentTab == AppTab.HOME) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home", fontSize = 12.sp, fontWeight = if (currentTab == AppTab.HOME) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Slate900,
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberSecondary
            ),
            modifier = Modifier.testTag("nav_home")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.BOOKINGS,
            onClick = { onTabSelected(AppTab.BOOKINGS) },
            icon = {
                Icon(
                    if (currentTab == AppTab.BOOKINGS) Icons.Filled.ConfirmationNumber else Icons.Outlined.ConfirmationNumber,
                    contentDescription = "Bookings"
                )
            },
            label = { Text("Bookings", fontSize = 12.sp, fontWeight = if (currentTab == AppTab.BOOKINGS) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Slate900,
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberSecondary
            ),
            modifier = Modifier.testTag("nav_bookings")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.WALLET,
            onClick = { onTabSelected(AppTab.WALLET) },
            icon = {
                Icon(
                    if (currentTab == AppTab.WALLET) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                    contentDescription = "Wallet"
                )
            },
            label = { Text("Wallet", fontSize = 12.sp, fontWeight = if (currentTab == AppTab.WALLET) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Slate900,
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberSecondary
            ),
            modifier = Modifier.testTag("nav_wallet")
        )

        NavigationBarItem(
            selected = currentTab == AppTab.PROFILE,
            onClick = { onTabSelected(AppTab.PROFILE) },
            icon = {
                Icon(
                    if (currentTab == AppTab.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                    contentDescription = "Profile"
                )
            },
            label = { Text("Profile", fontSize = 12.sp, fontWeight = if (currentTab == AppTab.PROFILE) FontWeight.Bold else FontWeight.Normal) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Slate900,
                selectedTextColor = AmberPrimary,
                indicatorColor = AmberSecondary
            ),
            modifier = Modifier.testTag("nav_profile")
        )
    }
}
