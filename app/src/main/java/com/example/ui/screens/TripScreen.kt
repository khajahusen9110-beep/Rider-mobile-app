package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Payment
import com.example.data.PaymentStatus
import com.example.data.Participants
import com.example.data.Refund
import com.example.data.Ride
import com.example.data.RideStatus
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.SosEvent
import com.example.ui.TripState
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.Format
import com.example.ui.components.InfoRow
import com.example.ui.components.Intents
import com.example.ui.components.LoadingBox
import com.example.ui.components.MapMarker
import com.example.ui.components.MarkerKind
import com.example.ui.components.OsmMap
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionCard
import com.example.ui.components.StatusChip
import com.example.ui.components.Tone
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SosRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextSecondary
import java.time.Instant

private val CANCEL_REASONS = listOf(
    "Driver is taking too long",
    "Driver asked me to cancel",
    "Changed my plans",
    "Booked by mistake",
    "Found another ride",
    "Other",
)

@Composable
fun TripScreen(viewModel: RiderViewModel, rideId: String) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showCancel by rememberSaveable { mutableStateOf(false) }
    var confirmSos by rememberSaveable { mutableStateOf(false) }
    var sosEvent by remember { mutableStateOf<SosEvent?>(null) }

    LaunchedEffect(rideId) { viewModel.refreshWalletBalance() }
    LaunchedEffect(Unit) { viewModel.sosEvents.collect { sosEvent = it } }

    RiderShell(viewModel, title = "Your trip", onBack = viewModel::back) {
        val ride = trip.ride
        if (ride == null || ride.id != rideId) {
            if (trip.loading) LoadingBox() else Text("This trip could not be found.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        } else {
            Column(Modifier.fillMaxSize()) {
                TripMap(ride, trip, Modifier.fillMaxWidth().weight(0.4f))
                Column(
                    Modifier.weight(0.6f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusHeader(ride)
                    trip.duePayment?.let { due ->
                        PayCard(due, wallet.balance, onOnline = { viewModel.startOnlinePayment(due.id) }, onWallet = { viewModel.payWithWallet(due.id) })
                    }
                    if (ride.status == RideStatus.ACCEPTED || ride.status == RideStatus.ARRIVED) StartCodeCard(trip.startCode)
                    trip.participants?.takeIf { ride.status.hasDriver || ride.status == RideStatus.COMPLETED }?.let { p ->
                        DriverCard(p, canCall = ride.status.hasDriver) { phone -> Intents.dial(context, phone) }
                    }
                    if (ride.status == RideStatus.COMPLETED && !trip.rated && ride.driverId != null) {
                        RateDriverCard { stars, comment -> viewModel.rateDriver(ride.id, ride.driverId, stars, comment) }
                    }
                    if (ride.status == RideStatus.CANCELLED) CancelledCard(ride, trip.refunds)
                    TripSummary(ride, trip.payments)

                    if (ride.status.hasDriver) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Share trip", onClick = { viewModel.shareTripText()?.let { Intents.share(context, it) } }, modifier = Modifier.weight(1f))
                            Button(
                                onClick = { confirmSos = true },
                                colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("SOS", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (ride.status.canCancel) {
                        TextButton(onClick = { showCancel = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (ride.status == RideStatus.PENDING_PAYMENT) "Cancel booking" else "Cancel ride", color = DangerRed)
                        }
                    }
                    if (!ride.status.isOpen) {
                        PrimaryButton("Book another ride", onClick = { viewModel.navigate(Screen.Home) })
                    }
                }
            }
        }
    }

    if (showCancel) {
        trip.ride?.let { ride -> CancelDialog(ride, onCancel = { viewModel.cancelRide(it); showCancel = false }, onDismiss = { showCancel = false }) }
    }
    if (confirmSos) {
        ConfirmDialog(
            title = "Send SOS?",
            message = "We'll alert the GoRide safety team with your live location and trip details. You can then call 112 or text your emergency contacts.",
            confirmText = "Send SOS",
            destructive = true,
            onConfirm = viewModel::sos,
            onDismiss = { confirmSos = false },
        )
    }
    sosEvent?.let { event -> SosDialog(event, onDismiss = { sosEvent = null }) }
}

@Composable
private fun TripMap(ride: Ride, trip: TripState, modifier: Modifier) {
    val driver = trip.driverLocation
    val markers = buildList {
        if (ride.status != RideStatus.ONGOING) add(MapMarker(ride.pickup.lat, ride.pickup.lng, MarkerKind.PICKUP))
        add(MapMarker(ride.drop.lat, ride.drop.lng, MarkerKind.DROP))
        driver?.let { add(MapMarker(it.lat, it.lng, MarkerKind.DRIVER)) }
    }
    // Re-frame when the status changes or the driver first appears, not on every position update.
    OsmMap(markers, modifier, fitKey = ride.status to (driver != null))
}

@Composable
private fun StatusHeader(ride: Ride) {
    val futureScheduled = ride.scheduledAt?.isAfter(Instant.now().plusSeconds(15 * 60)) == true
    val (title, subtitle) = when (ride.status) {
        RideStatus.PENDING_PAYMENT -> "Complete payment to confirm" to "Your booking is held while you pay."
        RideStatus.REQUESTED -> if (futureScheduled) {
            "Scheduled for ${Format.dateTime(ride.scheduledAt)}" to "We'll start finding a driver shortly before pickup."
        } else "Finding a driver near you…" to "Nearby drivers have been notified."
        RideStatus.ACCEPTED -> "Driver is on the way" to "Meet them at your pickup point."
        RideStatus.ARRIVED -> "Your driver has arrived" to "Share your start code to begin the trip."
        RideStatus.ONGOING -> "On the way to your destination" to "Started ${Format.time(ride.startedAt)}"
        RideStatus.COMPLETED -> "You've arrived" to "Completed ${Format.dateTime(ride.completedAt)}"
        RideStatus.CANCELLED -> "Booking cancelled" to (ride.cancelReason ?: "")
    }
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, color = TextSecondary)
        if (ride.status == RideStatus.REQUESTED && !futureScheduled) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandAmber)
        }
    }
}

@Composable
fun PayCard(due: Payment, walletBalance: Double, onOnline: () -> Unit, onWallet: () -> Unit) {
    SectionCard {
        Text("${due.typeLabel} due", color = TextSecondary)
        Text(Format.money(due.amount), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Pay ${Format.money(due.amount)} online", onClick = onOnline)
        if (walletBalance >= due.amount) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Pay from wallet (${Format.money(walletBalance)} available)", onClick = onWallet)
        }
        Spacer(Modifier.height(6.dp))
        Text("UPI, cards, netbanking and wallets via Razorpay.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StartCodeCard(code: String?) {
    SectionCard {
        Text("Start code", color = TextSecondary)
        Text(
            code?.toCharArray()?.joinToString("  ") ?: "• • • •",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = BrandAmber,
        )
        Text("Tell this code to your driver only when you're in the vehicle.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun DriverCard(p: Participants, canCall: Boolean, onCall: (String) -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.driverName ?: "Your driver", fontWeight = FontWeight.Bold)
                    p.driverRating?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Star, contentDescription = null, tint = BrandAmber, modifier = Modifier.size(16.dp))
                        Text(String.format(java.util.Locale.US, "%.1f", it), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(p.vehicleLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                p.vehiclePlate?.let {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text(it, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
            val phone = p.driverPhone
            if (canCall && phone != null) {
                FilledIconButton(onClick = { onCall(phone) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = SuccessGreen)) {
                    Icon(Icons.Default.Call, contentDescription = "Call driver", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun RateDriverCard(onSubmit: (Int, String) -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    SectionCard {
        Text("How was your driver?", fontWeight = FontWeight.Bold)
        Row {
            (1..5).forEach { i ->
                IconButton(onClick = { stars = i }) {
                    Icon(
                        if (i <= stars) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "$i star${if (i > 1) "s" else ""}",
                        tint = BrandAmber,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
        if (stars > 0) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(500) },
                placeholder = { Text("Anything to add? (optional)") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Submit rating", onClick = { onSubmit(stars, comment.trim()) })
        }
    }
}

@Composable
fun CancelledCard(ride: Ride, refunds: List<Refund>) {
    SectionCard {
        InfoRow("Cancelled", Format.dateTime(ride.cancelledAt))
        ride.cancelledBy?.let { InfoRow("By", if (it == ride.customerId) "You" else "Driver / GoRide") }
        if (refunds.isEmpty()) {
            Text("No refund is due for this booking.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        refunds.forEach { r ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Refund ${Format.money(r.amount)}", fontWeight = FontWeight.SemiBold)
                    r.reason?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                }
                StatusChip(r.statusLabel, if (r.status == "rejected") Tone.Bad else if (r.status == "pending") Tone.Warn else Tone.Good)
            }
        }
    }
}

@Composable
fun TripSummary(ride: Ride, payments: List<Payment>) {
    SectionCard {
        InfoRow("From", ride.pickup.address)
        InfoRow("To", ride.drop.address)
        ride.distanceKm?.let { InfoRow("Distance", Format.km(it)) }
        ride.passengerCount?.let { InfoRow("Passengers", "$it") }
        ride.goodsWeightKg?.let { InfoRow("Goods", "${it.toInt()} kg" + (ride.goodsDescription?.let { d -> " · $d" } ?: "")) }
        if (ride.discount > 0) InfoRow("Discount", "- ${Format.money(ride.discount)}" + (ride.promoCode?.let { " ($it)" } ?: ""))
        InfoRow(if (ride.fareFinal != null) "Fare" else "Estimated fare", Format.money(ride.fare))
        payments.forEach { p ->
            InfoRow(
                p.typeLabel,
                "${Format.money(p.amount)} · " + when (p.status) {
                    PaymentStatus.COMPLETED -> "Paid"
                    PaymentStatus.REFUNDED -> "Refunded"
                    PaymentStatus.PROCESSING -> "Due"
                    PaymentStatus.PENDING -> "Later"
                },
            )
        }
    }
}

@Composable
private fun CancelDialog(ride: Ride, onCancel: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf(CANCEL_REASONS.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this booking?") },
        text = {
            Column {
                if (ride.status.hasDriver) {
                    Text(
                        "Cancelling is free within 2 minutes of a driver accepting. After that, a cancellation fee may be kept from your payment.",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                CANCEL_REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == r, onClick = { reason = r }, role = Role.RadioButton).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCancel(reason) }) { Text("Cancel booking", color = DangerRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
    )
}

@Composable
private fun SosDialog(event: SosEvent, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = SosRed) },
        title = { Text("Help is being alerted") },
        text = {
            Column {
                Text("Our safety team has your location. For immediate help, call 112.", textAlign = TextAlign.Start)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { Intents.dial(context, "112") },
                    colors = ButtonDefaults.buttonColors(containerColor = SosRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Call 112")
                }
                if (event.contacts.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Add emergency contacts in your profile to alert them in one tap.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                event.contacts.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Intents.sms(context, c.phone, event.shareText) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = BrandAmber)
                        Spacer(Modifier.width(12.dp))
                        Text("Text ${c.name}", modifier = Modifier.weight(1f))
                        Text(c.phone, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { Intents.share(context, event.shareText) }) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share my location")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
