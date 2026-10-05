package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FareOption
import com.example.data.LegalDoc
import com.example.data.PaymentOption
import com.example.data.ReturnOffer
import com.example.data.VehicleCategory
import com.example.ui.BookingState
import com.example.ui.PlaceTarget
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.Format
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionTitle
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun QuoteScreen(viewModel: RiderViewModel) {
    val booking by viewModel.booking.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var termsAccepted by rememberSaveable { mutableStateOf(false) }
    var showTerms by rememberSaveable { mutableStateOf(false) }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var confirmOffer by rememberSaveable { mutableStateOf<String?>(null) }

    RiderShell(viewModel, title = "Choose a vehicle", onBack = viewModel::back) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RouteCard(booking, onEdit = { viewModel.navigate(Screen.PickPlace(it)) }, onSwap = viewModel::swapPlaces)
                CategoryToggle(booking.category, viewModel::setCategory)
                LoadInputs(booking, viewModel)

                if (booking.returnOffers.isNotEmpty()) {
                    SectionTitle("Return-trip deals")
                    booking.returnOffers.forEach { offer ->
                        ReturnOfferCard(offer) { confirmOffer = offer.offerId }
                    }
                }

                SectionTitle("Vehicles")
                FareList(booking, viewModel)

                PromoRow(booking, viewModel)
                PaymentOptions(booking, viewModel::setPaymentOption)
                ScheduleRow(booking.scheduledAt, onPick = { showSchedule = true }, onClear = { viewModel.setSchedule(null) })
                TermsRow(termsAccepted, { termsAccepted = it }, onOpen = { viewModel.loadTerms(); showTerms = true })
                Spacer(Modifier.height(8.dp))
            }
            val fare = booking.selectedFare
            Surface(color = MaterialTheme.colorScheme.background) {
                val label = when {
                    fare == null -> "Choose a vehicle"
                    booking.scheduledAt != null -> "Schedule ${fare.name} · ${Format.money(fare.finalFare)}"
                    else -> "Book ${fare.name} · ${Format.money(fare.finalFare)}"
                }
                PrimaryButton(
                    label,
                    onClick = { viewModel.book(termsAccepted) },
                    enabled = fare != null && fare.fits && !booking.faresLoading,
                    loading = busy != null,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showTerms) TermsDialog(viewModel, onAccept = { termsAccepted = true; showTerms = false }, onDismiss = { showTerms = false })
    if (showSchedule) ScheduleDialog(onPick = { viewModel.setSchedule(it); showSchedule = false }, onDismiss = { showSchedule = false })
    val offer = confirmOffer?.let { id -> booking.returnOffers.firstOrNull { it.offerId == id } }
    if (offer != null) {
        AlertDialog(
            onDismissRequest = { confirmOffer = null },
            title = { Text("Book this return trip?") },
            text = {
                Text(
                    "${offer.driverName} is heading your way in a ${offer.vehicleTypeName}. You pay ${Format.money(offer.discountedFare)} " +
                        "instead of ${Format.money(offer.normalFare)}. The trip starts now and payment is taken next." +
                        if (!termsAccepted) "\n\nBooking means you accept the Terms & Conditions." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmOffer = null; termsAccepted = true; viewModel.bookReturnOffer(offer, true) }) {
                    Text("Book for ${Format.money(offer.discountedFare)}", color = BrandAmber, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmOffer = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RouteCard(booking: BookingState, onEdit: (PlaceTarget) -> Unit, onSwap: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RoutePoint(SuccessGreen, booking.pickup?.address ?: "Pickup") { onEdit(PlaceTarget.PICKUP) }
                HorizontalDivider(Modifier.padding(vertical = 6.dp, horizontal = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                RoutePoint(DangerRed, booking.drop?.address ?: "Destination") { onEdit(PlaceTarget.DROP) }
            }
            IconButton(onClick = onSwap) { Icon(Icons.Default.SwapVert, contentDescription = "Swap pickup and destination") }
        }
        booking.distanceKm?.let {
            Text("About ${Format.km(it)} by road", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
        }
    }
}

@Composable
private fun RoutePoint(color: androidx.compose.ui.graphics.Color, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadInputs(booking: BookingState, viewModel: RiderViewModel) {
    if (booking.category == VehicleCategory.PASSENGER) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Passengers", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                IconButton(onClick = { viewModel.setPassengers(booking.passengers - 1) }, enabled = booking.passengers > 1) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer passengers")
                }
                Text("${booking.passengers}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.setPassengers(booking.passengers + 1) }, enabled = booking.passengers < 50) {
                    Icon(Icons.Default.Add, contentDescription = "More passengers")
                }
            }
        }
    } else {
        SectionCard {
            OutlinedTextField(
                value = booking.goodsWeightKg,
                onValueChange = viewModel::setGoodsWeight,
                label = { Text("Approx. weight") },
                suffix = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = booking.goodsDescription,
                onValueChange = viewModel::setGoodsDescription,
                label = { Text("What are you sending? (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FareList(booking: BookingState, viewModel: RiderViewModel) {
    when {
        booking.faresLoading && booking.fares.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = BrandAmber)
        }
        booking.faresError != null && booking.fares.isEmpty() -> SectionCard {
            Text(booking.faresError, color = DangerRed)
            TextButton(onClick = { viewModel.loadQuote() }) { Text("Try again") }
        }
        booking.fares.isEmpty() -> Text("No vehicles are available for this trip right now.", color = TextSecondary)
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            booking.fares.forEach { fare ->
                FareRow(fare, selected = fare.vehicleTypeId == booking.selectedTypeId, eta = booking.etaFor(fare.vehicleTypeId)) {
                    if (fare.fits) viewModel.selectVehicle(fare.vehicleTypeId)
                }
            }
        }
    }
}

@Composable
private fun FareRow(fare: FareOption, selected: Boolean, eta: Int?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) BrandAmber.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(2.dp, BrandAmber) else null,
        modifier = Modifier.fillMaxWidth().clickable(enabled = fare.fits, onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fare.icon, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fare.name, fontWeight = FontWeight.Bold, color = if (fare.fits) MaterialTheme.colorScheme.onSurface else TextSecondary)
                val sub = when {
                    !fare.fits -> "Too small for this load"
                    eta != null -> "$eta min away" + (fare.capacityLabel?.let { " · $it" } ?: "")
                    else -> fare.capacityLabel ?: "Drivers nearby will be notified"
                }
                Text(sub, color = if (fare.fits) TextSecondary else DangerRed, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.money(fare.finalFare), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                if (fare.discount > 0) {
                    Text(
                        Format.money(fare.finalFare + fare.discount),
                        color = TextSecondary,
                        textDecoration = TextDecoration.LineThrough,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReturnOfferCard(offer: ReturnOffer, onBook: () -> Unit) {
    SectionCard(onClick = onBook) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${offer.vehicleTypeName} · ${offer.discountPercent.toInt()}% off", fontWeight = FontWeight.Bold)
                Text("${offer.driverName} is already heading this way", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                offer.expiresAt?.let { Text("Deal ends ${Format.time(it)}", color = TextSecondary, style = MaterialTheme.typography.labelSmall) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.money(offer.discountedFare), fontWeight = FontWeight.Bold, color = SuccessGreen, style = MaterialTheme.typography.titleMedium)
                Text(Format.money(offer.normalFare), color = TextSecondary, textDecoration = TextDecoration.LineThrough, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PromoRow(booking: BookingState, viewModel: RiderViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    val applied = booking.promoCode
    if (applied != null) {
        InputChip(
            selected = true,
            onClick = viewModel::removePromo,
            label = { Text("$applied applied") },
            leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove promo", modifier = Modifier.size(16.dp)) },
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' || c == '_' }.take(20) },
                placeholder = { Text("Promo code") },
                leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { viewModel.applyPromo(code) }, enabled = code.length >= 3) { Text("Apply") }
        }
    }
}

@Composable
private fun PaymentOptions(booking: BookingState, onSelect: (PaymentOption) -> Unit) {
    val fare = booking.selectedFare
    Column {
        SectionTitle("Payment")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentOption.entries.forEach { option ->
                val amount = when (option) {
                    PaymentOption.FULL -> fare?.finalFare
                    PaymentOption.ADVANCE -> fare?.advanceAmount
                }
                FilterChip(
                    selected = booking.paymentOption == option,
                    onClick = { onSelect(option) },
                    label = { Text(option.label + (amount?.let { " · ${Format.money(it)}" } ?: "")) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BrandAmber.copy(alpha = 0.2f), selectedLabelColor = BrandAmber),
                )
            }
        }
        Text(
            if (booking.paymentOption == PaymentOption.ADVANCE) "Pay 20% online now and the rest online when the trip ends."
            else "Pay the full fare online (UPI, card, netbanking or wallet).",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ScheduleRow(at: Instant?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = at == null, onClick = onClear, label = { Text("Ride now") })
        Spacer(Modifier.width(8.dp))
        FilterChip(
            selected = at != null,
            onClick = onPick,
            label = { Text(at?.let { "Scheduled · ${Format.dateTime(it)}" } ?: "Schedule for later") },
            leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun TermsRow(accepted: Boolean, onChange: (Boolean) -> Unit, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = accepted, onCheckedChange = onChange, colors = CheckboxDefaults.colors(checkedColor = BrandAmber))
        Text("I accept the ", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Terms & Conditions",
            color = BrandAmber,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = onOpen),
        )
    }
}

@Composable
fun TermsDialog(viewModel: RiderViewModel, onAccept: (() -> Unit)?, onDismiss: () -> Unit) {
    val terms by viewModel.terms.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadTerms() }
    val doc: LegalDoc? = terms
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(doc?.title ?: "Terms & Conditions") },
        text = {
            if (doc == null) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandAmber) }
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(doc.content.ifBlank { "The terms are not available right now." }, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (onAccept != null) {
                TextButton(onClick = onAccept, enabled = doc != null) { Text("I accept", color = BrandAmber, fontWeight = FontWeight.Bold) }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = { if (onAccept != null) TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val IST: ZoneId = ZoneId.of("Asia/Kolkata")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(onPick: (Instant) -> Unit, onDismiss: () -> Unit) {
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now(IST)
    val lastDay = today.plusDays(7)
    val day = pickedDay
    if (day == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    return !d.isBefore(today) && !d.isAfter(lastDay)
                }
                override fun isSelectableYear(year: Int) = year == today.year || year == lastDay.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = { pickedDay = dateState.selectedDateMillis }, enabled = dateState.selectedDateMillis != null) { Text("Next") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) { DatePicker(dateState) }
    } else {
        val soon = LocalTime.now(IST).plusMinutes(30)
        val timeState = rememberTimePickerState(initialHour = soon.hour, initialMinute = soon.minute - soon.minute % 5, is24Hour = false)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Pickup time") },
            text = { TimePicker(timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(day).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.atTime(timeState.hour, timeState.minute).atZone(IST).toInstant())
                }) { Text("Set time", color = BrandAmber, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pickedDay = null }) { Text("Back") } },
        )
    }
}
