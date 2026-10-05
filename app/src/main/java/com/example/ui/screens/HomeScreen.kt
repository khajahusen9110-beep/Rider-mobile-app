package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VehicleFare
import com.example.ui.CustomerAppViewModel
import com.example.ui.VehicleCategory
import com.example.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: CustomerAppViewModel,
    modifier: Modifier = Modifier
) {
    val category by viewModel.selectedCategory.collectAsState()
    val pickupAddress by viewModel.pickupAddress.collectAsState()
    val dropAddress by viewModel.dropAddress.collectAsState()
    val roadDistanceKm by viewModel.roadDistanceKm.collectAsState()
    val roadDurationMin by viewModel.roadDurationMin.collectAsState()
    val fares by viewModel.vehicleFares.collectAsState()
    val selectedFare by viewModel.selectedFare.collectAsState()
    val termsAccepted by viewModel.termsAccepted.collectAsState()
    val paymentOption by viewModel.paymentOption.collectAsState()
    val legalDoc by viewModel.legalDoc.collectAsState()
    val promoCode by viewModel.promoCode.collectAsState()
    val goodsWeight by viewModel.goodsWeightKg.collectAsState()
    val goodsDesc by viewModel.goodsDescription.collectAsState()
    val passengerCount by viewModel.passengerCount.collectAsState()
    val scheduledAt by viewModel.scheduledAtIso.collectAsState()
    val returnTrips by viewModel.returnTrips.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showTermsModal by remember { mutableStateOf(false) }
    var showSchedulePicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section 2: Two Big Entry Points
        item {
            Text(
                text = "What would you like to book?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Passenger Entry Card
                CategorySelectionCard(
                    title = "Passenger",
                    subtitle = "Cars, Autos & Cabs",
                    iconEmoji = "🚗",
                    badge = "Ola/Uber style",
                    isSelected = category == VehicleCategory.PASSENGER,
                    activeColor = AmberPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("passenger_category_button"),
                    onClick = { viewModel.setCategory(VehicleCategory.PASSENGER) }
                )

                // Goods Entry Card
                CategorySelectionCard(
                    title = "Goods",
                    subtitle = "Trucks, Pickups & Tempo",
                    iconEmoji = "🚛",
                    badge = "Logistics",
                    isSelected = category == VehicleCategory.GOODS,
                    activeColor = SkyAccent,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("goods_category_button"),
                    onClick = { viewModel.setCategory(VehicleCategory.GOODS) }
                )
            }
        }

        // Section 3: Pickup & Drop Search
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pickup_drop_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Route & Destination",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        // Use GPS Current Location Button
                        OutlinedButton(
                            onClick = {
                                viewModel.useCurrentLocation(
                                    lat = 19.0760,
                                    lng = 72.8777,
                                    address = "Current Location (Mumbai, MH)"
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("use_gps_button")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = SkyAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Use My GPS", fontSize = 12.sp, color = SkyAccent)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Pickup Field
                    OutlinedTextField(
                        value = pickupAddress,
                        onValueChange = { viewModel.pickupAddress.value = it },
                        label = { Text("Pickup Location") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSuccess)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.swapLocations() }) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Swap Locations", tint = AmberPrimary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pickup_address_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Drop Field
                    OutlinedTextField(
                        value = dropAddress,
                        onValueChange = { viewModel.dropAddress.value = it },
                        label = { Text("Drop Destination") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(RoseError)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drop_address_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick City Presets
                    Text(
                        text = "Quick Presets:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SuggestionChip(
                                onClick = {
                                    viewModel.setLocationPreset(false, "Mumbai International Airport (BOM)", 19.0896, 72.8656)
                                },
                                label = { Text("✈️ Airport", fontSize = 12.sp) }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    viewModel.setLocationPreset(false, "Bandra Kurla Complex (BKC)", 19.0607, 72.8687)
                                },
                                label = { Text("🏢 BKC Hub", fontSize = 12.sp) }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    viewModel.setLocationPreset(false, "Navi Mumbai Logistics Park", 19.0330, 73.0297)
                                },
                                label = { Text("🏭 Logistics Park", fontSize = 12.sp) }
                            )
                        }
                        item {
                            SuggestionChip(
                                onClick = {
                                    viewModel.setLocationPreset(false, "Pune IT Park (Outstation)", 18.5204, 73.8567)
                                },
                                label = { Text("🛣️ Pune (Outstation)", fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Road Distance & Duration pill
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Real Road: ${roadDistanceKm} km",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "~${roadDurationMin.toInt()} mins est.",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // For Goods: Weight & Cargo Description
        if (category == VehicleCategory.GOODS) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Goods & Cargo Specifications",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = goodsWeight,
                                onValueChange = {
                                    viewModel.goodsWeightKg.value = it
                                    viewModel.loadFares()
                                },
                                label = { Text("Weight (kg)") },
                                placeholder = { Text("e.g. 250") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = goodsDesc,
                                onValueChange = { viewModel.goodsDescription.value = it },
                                label = { Text("Item Description") },
                                placeholder = { Text("Electronics, Furniture, etc.") },
                                singleLine = true,
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                    }
                }
            }
        } else {
            // For Passenger: Passenger Count
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Passengers",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { if (passengerCount > 1) viewModel.passengerCount.value = passengerCount - 1 },
                            enabled = passengerCount > 1
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                        }
                        Text(
                            text = "$passengerCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { if (passengerCount < 6) viewModel.passengerCount.value = passengerCount + 1 }
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                        }
                    }
                }
            }
        }

        // Promo Code Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.LocalOffer, contentDescription = null, tint = AmberPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = promoCode,
                    onValueChange = { viewModel.promoCode.value = it.uppercase() },
                    placeholder = { Text("Enter Promo (try FIRST50)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { viewModel.loadFares() },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Section 12: Bonus Return Ride Section
        if (returnTrips.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldLight),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("return_trip_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔄", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Return Ride — Save Money!",
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Driver heading back on your route. Grab huge discounts!",
                            fontSize = 12.sp,
                            color = Slate800
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        returnTrips.forEach { offer ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = offer.driverName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = offer.vehicleName, fontSize = 11.sp, color = Slate600)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${offer.discountedFare.toInt()}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = EmeraldSuccess,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Save ₹${offer.savings.toInt()}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AmberPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Selectable List of Vehicle Fares ("Choose Your Ride")
        item {
            Text(
                text = "Choose your vehicle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (fares.isEmpty() && isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmberPrimary)
                }
            }
        } else {
            items(fares) { fare ->
                val isSelected = selectedFare?.vehicleTypeId == fare.vehicleTypeId
                VehicleFareCard(
                    fare = fare,
                    isSelected = isSelected,
                    onClick = { viewModel.selectedFare.value = fare }
                )
            }
        }

        // Section 4: Booking Details (T&C, Payment Option, Schedule)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_action_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Payment Choice & Cancellation Policy",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val finalFare = selectedFare?.finalFare ?: 0.0
                    val advanceAmount = selectedFare?.advanceAmount ?: (finalFare * 0.20)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Full payment option (100%)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (paymentOption == "full") AmberLight else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (paymentOption == "full") BorderStroke(2.dp, AmberPrimary) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.paymentOption.value = "full" }
                                .testTag("payment_option_full")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Full (100%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (paymentOption == "full") {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹${finalFare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AmberPrimary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("80% refund on cancellation", fontSize = 10.sp, color = Slate600)
                            }
                        }

                        // Advance payment option (20%)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (paymentOption == "advance") SkyLight else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (paymentOption == "advance") BorderStroke(2.dp, SkyAccent) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.paymentOption.value = "advance" }
                                .testTag("payment_option_advance")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Advance (20%)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (paymentOption == "advance") {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("₹${advanceAmount.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = SkyAccent)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Non-refundable if cancelled", fontSize = 10.sp, color = RoseError)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Optional: Schedule for later
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = SkyAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (scheduledAt == null) "Ride Now (Immediate)" else "Scheduled: $scheduledAt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = {
                                if (scheduledAt == null) {
                                    viewModel.scheduledAtIso.value = "2026-09-28T10:00:00Z"
                                } else {
                                    viewModel.scheduledAtIso.value = null
                                }
                            }
                        ) {
                            Text(if (scheduledAt == null) "Schedule Later" else "Cancel Schedule", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // T&C Checkbox
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = termsAccepted,
                            onCheckedChange = { viewModel.termsAccepted.value = it },
                            colors = CheckboxDefaults.colors(checkedColor = AmberPrimary),
                            modifier = Modifier.testTag("terms_checkbox")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "I accept the ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Terms & Conditions",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SkyAccent,
                            modifier = Modifier.clickable { showTermsModal = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Main Booking CTA
                    Button(
                        onClick = { viewModel.requestRide() },
                        enabled = termsAccepted && selectedFare != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("book_ride_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AmberPrimary,
                            disabledContainerColor = Slate600
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = if (scheduledAt == null) "Book Ride • ₹${(if (paymentOption == "advance") advanceAmount else finalFare).toInt()}" else "Schedule Ride",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Terms & Conditions Dialog
    if (showTermsModal) {
        AlertDialog(
            onDismissRequest = { showTermsModal = false },
            title = {
                Text(
                    text = legalDoc?.title ?: "Terms and Conditions",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text(
                        text = legalDoc?.content ?: "Loading terms...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.termsAccepted.value = true
                        showTermsModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Accept & Close", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTermsModal = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun CategorySelectionCard(
    title: String,
    subtitle: String,
    iconEmoji: String,
    badge: String,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) activeColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) activeColor else Slate700.copy(alpha = 0.2f)
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = iconEmoji, fontSize = 28.sp)
                Surface(
                    color = if (isSelected) activeColor else Slate700.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun VehicleFareCard(
    fare: VehicleFare,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AmberLight.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, AmberPrimary) else BorderStroke(1.dp, Slate700.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("vehicle_card_${fare.vehicleTypeId}")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle emoji icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AmberPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = fare.iconEmoji, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = fare.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (fare.routePriced) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = SkyAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Route Fixed",
                                fontSize = 9.sp,
                                color = SkyAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = fare.capacityLabel,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (fare.discountAmount > 0) {
                    Text(
                        text = "Discount applied: -₹${fare.discountAmount.toInt()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldSuccess
                    )
                }
            }

            // Price column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹${fare.finalFare.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = AmberPrimary
                )
                Text(
                    text = "Adv: ₹${fare.advanceAmount.toInt()}",
                    fontSize = 11.sp,
                    color = Slate600
                )
            }
        }
    }
}
