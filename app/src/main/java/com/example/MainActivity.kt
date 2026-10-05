package com.example

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.CheckoutOrder
import com.example.push.PushMessagingService
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.needsNotificationPermission
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlockedScreen
import com.example.ui.screens.CompleteProfileScreen
import com.example.ui.screens.EmergencyContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PickPlaceScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.QuoteScreen
import com.example.ui.screens.RideDetailScreen
import com.example.ui.screens.RidesScreen
import com.example.ui.screens.SavedPlacesScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SupportScreen
import com.example.ui.screens.TripScreen
import com.example.ui.screens.WalletScreen
import com.example.ui.theme.GoRideTheme
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    private val viewModel: RiderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Checkout.preload(applicationContext)
        handleIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.checkoutEvents.collect(::openCheckout)
            }
        }
        setContent {
            GoRideTheme {
                RiderAppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    private fun handleIntent(intent: Intent?) {
        val rideId = intent?.getStringExtra(PushMessagingService.EXTRA_RIDE_ID) ?: return
        intent.removeExtra(PushMessagingService.EXTRA_RIDE_ID)
        viewModel.handleRideIntent(rideId)
    }

    private fun openCheckout(order: CheckoutOrder) {
        val checkout = Checkout().apply { setKeyID(order.keyId) }
        val options = JSONObject()
            .put("name", order.name)
            .put("description", order.description)
            .put("order_id", order.orderId)
            .put("amount", order.amountPaise)
            .put("currency", order.currency)
            .put("theme", JSONObject().put("color", "#F59E0B"))
            .put("prefill", JSONObject().put("name", order.prefillName).put("contact", order.prefillContact))
            .put("retry", JSONObject().put("enabled", true).put("max_count", 3))
        try {
            checkout.open(this, options)
        } catch (e: Exception) {
            Log.e("Checkout", "Couldn't open Razorpay checkout", e)
            viewModel.onCheckoutError(-1, e.message)
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, data: PaymentData?) {
        viewModel.onCheckoutSuccess(data?.orderId, razorpayPaymentId ?: data?.paymentId, data?.signature)
    }

    override fun onPaymentError(code: Int, response: String?, data: PaymentData?) {
        val description = response?.let { runCatching { JSONObject(it).optJSONObject("error")?.optString("description") }.getOrNull() ?: it }
        viewModel.onCheckoutError(if (code == Checkout.PAYMENT_CANCELED) 0 else code, description)
    }
}

@Composable
fun RiderAppRoot(viewModel: RiderViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbar.showSnackbar(it) }
    }

    val signedIn = screen !is Screen.Auth && screen !is Screen.Splash && screen !is Screen.Blocked && screen != Screen.CompleteProfile
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.refreshMyLocation()
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    // Trip updates arrive as notifications, so ask once the first booking screen is reached.
    LaunchedEffect(screen is Screen.Trip) {
        if (screen is Screen.Trip && needsNotificationPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = signedIn && screen != Screen.Home) { viewModel.back() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = screen) {
            Screen.Splash -> SplashScreen(viewModel)
            Screen.Auth -> AuthScreen(viewModel)
            Screen.CompleteProfile -> CompleteProfileScreen(viewModel)
            is Screen.Blocked -> BlockedScreen(viewModel, s.message)
            Screen.Home -> HomeScreen(viewModel)
            is Screen.PickPlace -> PickPlaceScreen(viewModel, s.target)
            Screen.Quote -> QuoteScreen(viewModel)
            is Screen.Trip -> TripScreen(viewModel, s.rideId)
            Screen.Rides -> RidesScreen(viewModel)
            is Screen.RideDetail -> RideDetailScreen(viewModel, s.rideId)
            Screen.Wallet -> WalletScreen(viewModel)
            Screen.Profile -> ProfileScreen(viewModel)
            Screen.SavedPlaces -> SavedPlacesScreen(viewModel)
            Screen.EmergencyContacts -> EmergencyContactsScreen(viewModel)
            Screen.Support -> SupportScreen(viewModel)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (screen in tabScreens) 88.dp else 16.dp),
        )
    }
}

private val tabScreens = setOf<Screen>(Screen.Home, Screen.Rides, Screen.Wallet, Screen.Profile)
