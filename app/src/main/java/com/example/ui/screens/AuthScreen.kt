package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuthUiState
import com.example.ui.CustomerAppViewModel
import com.example.ui.theme.*

@Composable
fun AuthScreen(viewModel: CustomerAppViewModel, authState: AuthUiState) {
    var phoneInput by remember { mutableStateOf("9876543210") }
    var nameInput by remember { mutableStateOf("Rahul Sharma") }
    var otpInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate900, Slate800, Color(0xFF0B132B))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (authState) {
            is AuthUiState.Suspended -> {
                SuspendedAccountCard(
                    reason = authState.reason ?: "Account review in progress",
                    until = authState.until,
                    onSignOut = { viewModel.logout() }
                )
            }
            is AuthUiState.OtpSent -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_verify_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AmberSecondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "OTP Security",
                                tint = AmberSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Verify Mobile Number",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter the 6-digit code sent to ${authState.phone}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate100.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = otpInput,
                            onValueChange = { if (it.length <= 6) otpInput = it },
                            label = { Text("6-Digit OTP") },
                            placeholder = { Text("123456") },
                            leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("otp_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberSecondary,
                                focusedLabelColor = AmberSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.verifyOtp(authState.phone, otpInput, authState.fullName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("verify_otp_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberSecondary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Verify & Continue", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(
                            onClick = { viewModel.sendOtp(authState.phone, authState.fullName) }
                        ) {
                            Text("Resend Code", color = SkyAccent)
                        }
                    }
                }
            }
            else -> {
                // Login / Signup Form
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("login_signup_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate800)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Brand Logo
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🚗", fontSize = 32.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GoRide & Cargo",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "🚛", fontSize = 32.sp)
                        }

                        Text(
                            text = "Passenger Rides & Goods Transport",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate100.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name") },
                            placeholder = { Text("e.g. Rahul Sharma") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fullname_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberSecondary,
                                focusedLabelColor = AmberSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Mobile Number") },
                            prefix = { Text("+91 ", color = AmberSecondary, fontWeight = FontWeight.Bold) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberSecondary,
                                focusedLabelColor = AmberSecondary,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.sendOtp(phoneInput, nameInput) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("send_otp_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberSecondary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Slate900)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send OTP", color = Slate900, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        HorizontalDivider(color = Slate700)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Fast one-click demo login for reviewers
                        OutlinedButton(
                            onClick = { viewModel.loginWithDemo() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("demo_login_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(AmberSecondary, SkyAccent))),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = AmberSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Quick Demo Login (Tester Mode)", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Instant access with preloaded customer profile & test wallet balance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate100.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SuspendedAccountCard(reason: String, until: String?, onSignOut: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suspended_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Account Suspended",
                tint = RoseError,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Account Suspended",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your customer account has been temporarily restricted.",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate100,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color = Slate800,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Reason: $reason", color = RoseLight, fontWeight = FontWeight.SemiBold)
                    if (!until.isNullOrBlank()) {
                        Text(text = "Suspended Until: $until", color = Slate100.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
