package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.RiderViewModel
import com.example.ui.components.Field
import com.example.ui.components.PrimaryButton
import com.example.ui.components.SecondaryButton
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.DangerRed
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(viewModel: RiderViewModel) {
    val error by viewModel.startupError.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Logo()
        Spacer(Modifier.height(32.dp))
        val message = error
        if (message == null) {
            CircularProgressIndicator(color = BrandAmber)
        } else {
            Text(message, color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Try again", onClick = viewModel::bootstrap)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = viewModel::logout) { Text("Log out") }
        }
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier.size(84.dp).background(BrandAmber, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.LocalTaxi, contentDescription = null, tint = Color.Black, modifier = Modifier.size(44.dp)) }
    Spacer(Modifier.height(16.dp))
    Text("GoRide", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
    Text("Rides and deliveries, on time.", color = TextSecondary)
}

@Composable
fun AuthScreen(viewModel: RiderViewModel) {
    val auth by viewModel.auth.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var phone by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Logo()
        Spacer(Modifier.height(40.dp))

        val sentTo = auth.otpSentTo
        if (sentTo == null) {
            Text("Log in or sign up", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("We'll send a 6-digit code to your mobile number.", color = TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() }.take(10) },
                label = { Text("Mobile number") },
                prefix = { Text("+91 ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Field(name, { name = it.take(60) }, "Your name (new users)", placeholder = "So drivers know who to pick up")
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                "Send code",
                onClick = { viewModel.sendOtp(phone, name) },
                enabled = phone.length == 10,
                loading = busy != null,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "By continuing you agree to the GoRide Terms & Conditions and privacy policy.",
                color = TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
        } else {
            Text("Enter the code", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Sent to $sentTo", color = TextSecondary)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { v ->
                    code = v.filter { it.isDigit() }.take(6)
                    if (code.length == 6 && busy == null) viewModel.verifyOtp(code)
                },
                label = { Text("6-digit code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton("Verify", onClick = { viewModel.verifyOtp(code) }, enabled = code.length == 6, loading = busy != null)
            Spacer(Modifier.height(8.dp))
            ResendRow(auth.resendAvailableAt, onResend = viewModel::resendOtp)
            SecondaryButton("Change number", onClick = { code = ""; viewModel.changeNumber() })
        }
    }
}

@Composable
private fun ResendRow(availableAt: Long, onResend: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(availableAt) {
        while (System.currentTimeMillis() < availableAt) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val secondsLeft = ((availableAt - now) / 1000).coerceAtLeast(0)
    TextButton(onClick = onResend, enabled = secondsLeft == 0L) {
        Text(if (secondsLeft > 0) "Resend code in ${secondsLeft}s" else "Resend code")
    }
}

@Composable
fun CompleteProfileScreen(viewModel: RiderViewModel) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Logo()
        Spacer(Modifier.height(40.dp))
        Text("What should we call you?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Your driver sees this name when they pick you up.", color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(60) },
            label = { Text("Full name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue", onClick = { viewModel.saveName(name) }, enabled = name.trim().length >= 2, loading = busy != null)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = viewModel::logout) { Text("Use a different number") }
    }
}

@Composable
fun BlockedScreen(viewModel: RiderViewModel, message: String) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Block, contentDescription = null, tint = DangerRed, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Can't continue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Log out", onClick = viewModel::logout)
    }
}
