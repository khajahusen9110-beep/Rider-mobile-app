package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.Place
import com.example.ui.PlaceTarget
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.ConfirmDialog
import com.example.ui.components.EmptyState
import com.example.ui.components.Format
import com.example.ui.components.Intents
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusChip
import com.example.ui.components.Tone
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.TextSecondary

@Composable
fun ProfileScreen(viewModel: RiderViewModel) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editName by rememberSaveable { mutableStateOf(false) }
    var referral by rememberSaveable { mutableStateOf(false) }
    var terms by rememberSaveable { mutableStateOf(false) }
    var deleteAccount by rememberSaveable { mutableStateOf(false) }
    var logout by rememberSaveable { mutableStateOf(false) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadAvatar(uri)
    }

    RiderShell(viewModel, title = "Profile", tab = Screen.Profile) {
        val p = profile
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = busy == null) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (p?.avatarUrl != null) {
                            AsyncImage(model = p.avatarUrl, contentDescription = "Profile photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Text(p?.fullName?.firstOrNull()?.uppercase() ?: "?", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.align(Alignment.BottomEnd).size(22.dp).background(BrandAmber, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = Color.Black, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p?.fullName ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p?.phone ?: "", color = TextSecondary)
                    }
                    IconButton(onClick = { editName = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit name") }
                }
            }

            p?.referralCode?.let { code ->
                SectionCard {
                    Text("Invite friends", fontWeight = FontWeight.Bold)
                    Text("Share your code. You both get a reward after their first ride.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code, fontWeight = FontWeight.ExtraBold, color = BrandAmber, fontSize = 22.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { Intents.share(context, "Ride with GoRide! Use my code $code when you sign up.") }) {
                            Icon(Icons.Default.Share, contentDescription = "Share referral code")
                        }
                    }
                }
            }

            SectionCard {
                MenuRow(Icons.Default.Bookmark, "Saved places") { viewModel.navigate(Screen.SavedPlaces) }
                MenuRow(Icons.Default.ContactPhone, "Emergency contacts") { viewModel.navigate(Screen.EmergencyContacts) }
                MenuRow(Icons.Default.Redeem, "Have a referral code?") { referral = true }
                MenuRow(Icons.Default.SupportAgent, "Help & support") { viewModel.navigate(Screen.Support) }
                MenuRow(Icons.Default.Description, "Terms & Conditions") { terms = true }
            }
            SectionCard {
                MenuRow(Icons.AutoMirrored.Filled.Logout, "Log out") { logout = true }
                MenuRow(Icons.Default.DeleteForever, "Delete account", tint = DangerRed) { deleteAccount = true }
            }
            Text("GoRide ${com.example.BuildConfig.VERSION_NAME}", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (editName) {
        TextInputDialog("Your name", profile?.fullName ?: "", "Save", KeyboardCapitalization.Words, onDone = { viewModel.updateName(it); editName = false }, onDismiss = { editName = false })
    }
    if (referral) {
        TextInputDialog("Enter referral code", "", "Apply", KeyboardCapitalization.Characters, onDone = { viewModel.applyReferral(it); referral = false }, onDismiss = { referral = false })
    }
    if (terms) TermsDialog(viewModel, onAccept = null, onDismiss = { terms = false })
    if (logout) ConfirmDialog("Log out?", "You'll need your phone number and a code to log back in.", "Log out", onConfirm = viewModel::logout, onDismiss = { logout = false })
    if (deleteAccount) {
        TextInputDialog(
            "Delete your account?",
            "",
            "Request deletion",
            KeyboardCapitalization.Sentences,
            message = "Our team will delete your account and personal data within 7 days. Tell us why you're leaving (optional).",
            allowEmpty = true,
            destructive = true,
            onDone = { viewModel.requestAccountDeletion(it.ifBlank { "No reason given" }); deleteAccount = false },
            onDismiss = { deleteAccount = false },
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = if (tint == MaterialTheme.colorScheme.onSurface) TextSecondary else tint)
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    capitalization: KeyboardCapitalization,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    allowEmpty: Boolean = false,
    destructive: Boolean = false,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                message?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(200) },
                    singleLine = !allowEmpty,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(value.trim()) }, enabled = allowEmpty || value.isNotBlank()) {
                Text(confirm, color = if (destructive) DangerRed else BrandAmber, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SavedPlacesScreen(viewModel: RiderViewModel) {
    val places by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val pending by viewModel.pendingSavedPlace.collectAsStateWithLifecycle()
    RiderShell(viewModel, title = "Saved places", onBack = viewModel::back) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (places.isEmpty()) {
                item { EmptyState(Icons.Default.Bookmark, "No saved places", "Save home, work and other places you visit often to book faster.") }
            }
            items(places, key = { it.id }) { sp ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sp.label, fontWeight = FontWeight.Bold)
                            Text(sp.place.address, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { viewModel.removeSavedPlace(sp.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${sp.label}", tint = TextSecondary) }
                    }
                }
            }
            if (places.size < 10) {
                item {
                    PrimaryButton("Add a place", onClick = { viewModel.navigate(Screen.PickPlace(PlaceTarget.SAVED)) })
                }
            }
        }
    }
    pending?.let { place -> SavePlaceDialog(place, onSave = { viewModel.addSavedPlace(it, place) }, onDismiss = viewModel::clearPendingSavedPlace) }
}

@Composable
private fun SavePlaceDialog(place: Place, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var label by rememberSaveable { mutableStateOf("Home") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this place") },
        text = {
            Column {
                Text(place.address, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Work").forEach { option ->
                        FilterChip(selected = label == option, onClick = { label = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(30) },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label.trim()) }, enabled = label.isNotBlank()) { Text("Save", color = BrandAmber, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmergencyContactsScreen(viewModel: RiderViewModel) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    RiderShell(viewModel, title = "Emergency contacts", onBack = viewModel::back) {
        LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(
                    "When you press SOS during a trip, you can text these people your live location in one tap.",
                    color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
            items(contacts, key = { it.id }) { c ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text(c.phone, color = TextSecondary)
                        }
                        IconButton(onClick = { viewModel.removeContact(c.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${c.name}", tint = TextSecondary) }
                    }
                }
            }
            if (contacts.size < 5) {
                item {
                    SectionCard {
                        SectionTitle("Add a contact")
                        OutlinedTextField(
                            value = name, onValueChange = { name = it.take(60) }, label = { Text("Name") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16) },
                            label = { Text("Phone number") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            "Add contact",
                            onClick = { viewModel.addContact(name, phone); name = ""; phone = "" },
                            enabled = name.isNotBlank() && phone.count { it.isDigit() } >= 10,
                            loading = busy != null,
                        )
                    }
                }
            } else {
                item { Text("You can save up to 5 contacts.", color = TextSecondary) }
            }
        }
    }
}

@Composable
fun SupportScreen(viewModel: RiderViewModel) {
    val complaints by viewModel.complaints.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var raise by rememberSaveable { mutableStateOf(false) }
    RiderShell(viewModel, title = "Help & support", onBack = viewModel::back) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard {
                    Text("Need help?", fontWeight = FontWeight.Bold)
                    Text(
                        "For a problem with a specific trip, open it from Your rides and tap \"Report a problem\". For anything else, write to us below.",
                        color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Contact support", onClick = { raise = true })
                    TextButton(onClick = { Intents.dial(context, "112") }) { Text("In an emergency, call 112", color = DangerRed) }
                }
            }
            if (complaints.isNotEmpty()) item { SectionTitle("Your requests") }
            items(complaints, key = { it.id }) { c ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusChip(Format.title(c.status), when (c.status) { "resolved", "closed" -> Tone.Good; "open" -> Tone.Warn; else -> Tone.Info })
                    }
                    Text(Format.dateTime(c.createdAt), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(c.description, style = MaterialTheme.typography.bodyMedium)
                    c.adminResponse?.let {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        Text("GoRide support", color = BrandAmber, style = MaterialTheme.typography.labelMedium)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (raise) {
        ComplaintDialog(
            onSubmit = { category, text -> viewModel.raiseComplaint(null, category, text) { raise = false } },
            onDismiss = { raise = false },
        )
    }
}

