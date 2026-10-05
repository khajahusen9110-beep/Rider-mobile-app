package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Ride
import com.example.data.RideStatus
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.EmptyState
import com.example.ui.components.Format
import com.example.ui.components.InfoRow
import com.example.ui.components.Intents
import com.example.ui.components.LoadingBox
import com.example.ui.components.SecondaryButton
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusChip
import com.example.ui.components.Tone
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.TextSecondary

val COMPLAINT_CATEGORIES = listOf("Driver behaviour", "Overcharged", "Payment or refund", "Safety", "Vehicle condition", "App problem", "Other")

fun statusTone(status: RideStatus) = when (status) {
    RideStatus.COMPLETED -> Tone.Good
    RideStatus.CANCELLED -> Tone.Bad
    RideStatus.PENDING_PAYMENT -> Tone.Warn
    else -> Tone.Info
}

@Composable
fun RidesScreen(viewModel: RiderViewModel) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    RiderShell(viewModel, title = "Your rides", tab = Screen.Rides) {
        val rides = history
        when {
            rides == null -> LoadingBox()
            rides.isEmpty() -> EmptyState(Icons.Default.DirectionsCar, "No rides yet", "Your trips and deliveries will show up here.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(rides, key = { it.id }) { ride -> RideRow(ride) { viewModel.openRide(ride.id) } }
            }
        }
    }
}

@Composable
private fun RideRow(ride: Ride, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.dateTime(ride.scheduledAt ?: ride.requestedAt), color = TextSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            StatusChip(ride.status.label, statusTone(ride.status))
        }
        Spacer(Modifier.height(6.dp))
        Text(ride.drop.address, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("From ${ride.pickup.address}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(Format.money(ride.fare), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RideDetailScreen(viewModel: RiderViewModel, rideId: String) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showComplaint by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(rideId) { viewModel.refreshWalletBalance() }

    RiderShell(viewModel, title = "Ride details", onBack = viewModel::back) {
        val ride = detail.ride
        if (ride == null || ride.id != rideId) {
            if (detail.loading) LoadingBox() else Text("This ride could not be found.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Format.dateTime(ride.requestedAt), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusChip(ride.status.label, statusTone(ride.status))
                }
                detail.payments.firstOrNull { it.isDue }?.let { due ->
                    PayCard(due, wallet.balance, onOnline = { viewModel.startOnlinePayment(due.id) }, onWallet = { viewModel.payWithWallet(due.id) })
                }
                detail.participants?.let { DriverCard(it, canCall = false) { phone -> Intents.dial(context, phone) } }
                if (ride.status == RideStatus.COMPLETED && !detail.rated && ride.driverId != null) {
                    RateDriverCard { stars, comment -> viewModel.rateDriver(ride.id, ride.driverId, stars, comment) }
                }
                if (ride.status == RideStatus.CANCELLED) CancelledCard(ride, detail.refunds)
                TripSummary(ride, detail.payments)
                detail.invoice?.takeIf { ride.status == RideStatus.COMPLETED }?.let { inv ->
                    SectionTitle("Receipt")
                    SectionCard {
                        inv.vehicleType?.let { InfoRow("Vehicle", it + (inv.vehiclePlate?.let { p -> " · $p" } ?: "")) }
                        inv.distanceKm?.let { InfoRow("Distance", Format.km(it)) }
                        if (inv.discount > 0) InfoRow("Discount", "- ${Format.money(inv.discount)}")
                        InfoRow("Total", Format.money(inv.fare))
                        inv.payments.forEach { line ->
                            InfoRow("${line.label} (${Format.title(line.method)})", "${Format.money(line.amount)} · ${Format.title(line.status)}")
                        }
                        InfoRow("Trip id", ride.id.take(8).uppercase())
                    }
                }
                if (!ride.status.isOpen) {
                    SecondaryButton("Report a problem with this ride", onClick = { showComplaint = true })
                }
            }
        }
    }

    if (showComplaint) {
        ComplaintDialog(
            onSubmit = { category, text -> viewModel.raiseComplaint(rideId, category, text) { showComplaint = false } },
            onDismiss = { showComplaint = false },
        )
    }
}

@Composable
fun ComplaintDialog(onSubmit: (String, String) -> Unit, onDismiss: () -> Unit) {
    var category by rememberSaveable { mutableStateOf(COMPLAINT_CATEGORIES.first()) }
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a problem") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                COMPLAINT_CATEGORIES.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = category == c, onClick = { category = c }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = category == c, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(c)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    placeholder = { Text("Tell us what happened") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(category, text) }, enabled = text.trim().length >= 10) {
                Text("Send", color = BrandAmber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
