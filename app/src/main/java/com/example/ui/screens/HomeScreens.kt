package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Place
import com.example.data.Ride
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.ui.PlaceTarget
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.Format
import com.example.ui.components.MapMarker
import com.example.ui.components.MarkerKind
import com.example.ui.components.OsmMap
import com.example.ui.components.PrimaryButton
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(viewModel: RiderViewModel) {
    val booking by viewModel.booking.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    val openRides by viewModel.openRides.collectAsStateWithLifecycle()
    val saved by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    // Keep the nearby cars fresh while the home map is showing.
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            viewModel.refreshCars()
        }
    }

    val firstName = profile?.fullName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    RiderShell(viewModel, title = firstName?.let { "Hi, $it" } ?: "GoRide", tab = Screen.Home) {
        Box(Modifier.fillMaxSize()) {
            val pickup = booking.pickup
            val markers = buildList {
                booking.cars.forEach { add(MapMarker(it.lat, it.lng, MarkerKind.CAR)) }
                myLocation?.let { if (pickup == null || it.distanceTo(pickup) > 0.05) add(MapMarker(it.lat, it.lng, MarkerKind.ME)) }
                pickup?.let { add(MapMarker(it.lat, it.lng, MarkerKind.PICKUP)) }
            }
            OsmMap(markers, Modifier.fillMaxSize(), fitKey = pickup?.let { it.lat to it.lng })

            SmallFloatingActionButton(
                onClick = { viewModel.useCurrentLocationFor(PlaceTarget.PICKUP) },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) { Icon(Icons.Default.MyLocation, contentDescription = "Use my location as pickup") }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                openRides.take(2).forEach { ride ->
                    OpenRideBanner(ride) { viewModel.openRide(ride.id) }
                    Spacer(Modifier.height(8.dp))
                }
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        CategoryToggle(booking.category, viewModel::setCategory)
                        Spacer(Modifier.height(12.dp))
                        PlaceRow(SuccessGreen, "Pickup", pickup?.address ?: "Set pickup location") {
                            viewModel.navigate(Screen.PickPlace(PlaceTarget.PICKUP))
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        PlaceRow(DangerRed, null, if (booking.category == VehicleCategory.GOODS) "Deliver to?" else "Where to?", bold = true) {
                            viewModel.navigate(Screen.PickPlace(PlaceTarget.DROP))
                        }
                        if (saved.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(saved, key = { it.id }) { sp ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { viewModel.choosePlace(PlaceTarget.DROP, sp.place) },
                                        label = { Text(sp.label) },
                                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryToggle(selected: VehicleCategory, onSelect: (VehicleCategory) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleCategory.entries.forEach { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(if (c == VehicleCategory.GOODS) "Send goods" else "Book a ride") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrandAmber.copy(alpha = 0.2f),
                    selectedLabelColor = BrandAmber,
                ),
            )
        }
    }
}

@Composable
private fun PlaceRow(dot: Color, label: String?, text: String, bold: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            label?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.labelSmall) }
            Text(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun OpenRideBanner(ride: Ride, onClick: () -> Unit) {
    Surface(
        color = BrandAmber,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val title = if (ride.status == RideStatus.REQUESTED && ride.scheduledAt != null) {
                    "Scheduled for ${Format.dateTime(ride.scheduledAt)}"
                } else ride.status.label
                Text(title, color = Color.Black, fontWeight = FontWeight.Bold)
                Text("To ${ride.drop.address}", color = Color.Black.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Color.Black)
        }
    }
}

@Composable
fun PickPlaceScreen(viewModel: RiderViewModel, target: PlaceTarget) {
    val booking by viewModel.booking.collectAsStateWithLifecycle()
    val myLocation by viewModel.myLocation.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var onMap by rememberSaveable { mutableStateOf(false) }
    val title = when (target) {
        PlaceTarget.PICKUP -> "Pickup location"
        PlaceTarget.DROP -> if (booking.category == VehicleCategory.GOODS) "Deliver to" else "Where to?"
        PlaceTarget.SAVED -> "Save a place"
    }

    RiderShell(viewModel, title = title, onBack = { if (onMap) onMap = false else viewModel.back() }) {
        if (onMap) {
            val start = (if (target == PlaceTarget.PICKUP) booking.pickup else booking.drop) ?: booking.pickup ?: myLocation
            PickOnMap(viewModel, start) { place -> viewModel.choosePlace(target, place) }
        } else {
            PlaceSearch(viewModel, target, query, { query = it }, { onMap = true })
        }
    }
}

@Composable
private fun PlaceSearch(viewModel: RiderViewModel, target: PlaceTarget, query: String, onQuery: (String) -> Unit, onChooseOnMap: () -> Unit) {
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val saved by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQuery(it); viewModel.search(it) },
            placeholder = { Text("Search area, street or landmark") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery(""); viewModel.search("") }) { Icon(Icons.Default.Close, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                PlaceOption(Icons.Default.MyLocation, "Use current location", null, BrandAmber) { viewModel.useCurrentLocationFor(target) }
                PlaceOption(Icons.Default.Map, "Choose on map", null, BrandAmber, onChooseOnMap)
            }
            if (query.trim().length >= 3 && results.isEmpty() && !searching) {
                item { Text("No places found. Try another name, or choose on the map.", color = TextSecondary, modifier = Modifier.padding(16.dp)) }
            }
            items(results) { place ->
                PlaceOption(Icons.Default.Place, place.address.substringBefore(','), place.address) { viewModel.choosePlace(target, place) }
            }
            if (query.isBlank() && target != PlaceTarget.SAVED) {
                if (saved.isNotEmpty()) item { ListHeader("Saved places") }
                items(saved, key = { "s" + it.id }) { sp ->
                    PlaceOption(Icons.Default.Bookmark, sp.label, sp.place.address) { viewModel.choosePlace(target, sp.place) }
                }
                val recent = viewModel.recentPlaces
                if (recent.isNotEmpty()) item { ListHeader("Recent") }
                items(recent) { place ->
                    PlaceOption(Icons.Default.History, place.address.substringBefore(','), place.address) { viewModel.choosePlace(target, place) }
                }
            }
        }
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(text, color = TextSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun PlaceOption(icon: ImageVector, title: String, subtitle: String?, tint: Color = TextSecondary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun PickOnMap(viewModel: RiderViewModel, start: Place?, onConfirm: (Place) -> Unit) {
    var lat by remember { mutableDoubleStateOf(start?.lat ?: 0.0) }
    var lng by remember { mutableDoubleStateOf(start?.lng ?: 0.0) }
    var address by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lat, lng) {
        address = null
        if (lat == 0.0 && lng == 0.0) return@LaunchedEffect
        delay(300)
        address = viewModel.addressFor(lat, lng)
    }
    Box(Modifier.fillMaxSize()) {
        OsmMap(
            markers = emptyList(),
            modifier = Modifier.fillMaxSize(),
            fitKey = Unit,
            focus = start?.let { it.lat to it.lng },
            focusZoom = 17.0,
            onCenterChanged = { la, ln -> lat = la; lng = ln },
        )
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier.align(Alignment.Center).padding(bottom = 36.dp).size(44.dp),
        )
        Card(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Move the map to place the pin", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(address ?: "Finding address…", fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "Confirm location",
                    onClick = { onConfirm(Place(lat, lng, address ?: String.format(java.util.Locale.US, "%.5f, %.5f", lat, lng))) },
                    enabled = !(lat == 0.0 && lng == 0.0),
                )
            }
        }
    }
}
