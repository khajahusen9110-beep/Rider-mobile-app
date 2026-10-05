package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.RideInvoice
import com.example.ui.theme.*

@Composable
fun InvoiceDialog(
    invoice: RideInvoice,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("invoice_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TAX INVOICE / RECEIPT",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            color = Slate900
                        )
                        Text(
                            text = "GoRide & Cargo Mobility Ltd.",
                            fontSize = 11.sp,
                            color = Slate600
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate700)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Slate600.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Invoice metadata
                InvoiceRow(label = "Booking ID", value = "#${invoice.rideId.takeLast(8).uppercase()}", isMonospace = true)
                InvoiceRow(label = "Date & Time", value = invoice.date.ifBlank { "Recently Completed" })
                InvoiceRow(label = "Driver Partner", value = invoice.driverName ?: "Verified Partner")
                InvoiceRow(label = "Vehicle / Plate", value = invoice.vehicleInfo ?: "Commercial Unit")

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Slate600.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Route
                Text(text = "TRIP ROUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Pickup: ${invoice.pickupAddress}", fontSize = 12.sp, color = Slate900)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Drop: ${invoice.dropAddress}", fontSize = 12.sp, color = Slate900)

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Slate600.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                // Fare Breakdown
                Text(text = "FARE BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(modifier = Modifier.height(6.dp))
                InvoiceRow(label = "Base Fare", value = "₹${invoice.baseFare.toInt()}")
                InvoiceRow(label = "Distance Fare", value = "₹${invoice.distanceFare.toInt()}")
                if (invoice.tollCharge > 0) {
                    InvoiceRow(label = "Toll / Highway Charges", value = "₹${invoice.tollCharge.toInt()}")
                }
                if (invoice.discountAmount > 0) {
                    InvoiceRow(label = "Promo Discount", value = "-₹${invoice.discountAmount.toInt()}", valueColor = EmeraldSuccess)
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Slate900, thickness = 1.5.dp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total Charged", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Slate900)
                    Text(text = "₹${invoice.finalFare.toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AmberPrimary)
                }

                Spacer(modifier = Modifier.height(6.dp))
                InvoiceRow(label = "Advance Paid", value = "₹${invoice.advancePaid.toInt()}")
                InvoiceRow(label = "Balance Paid", value = "₹${invoice.balancePaid.toInt()}")

                Spacer(modifier = Modifier.height(16.dp))

                // Footer
                Surface(
                    color = Slate100,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Thank you for riding with GoRide! All fares include GST & regulatory road taxes.",
                        fontSize = 11.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Receipt", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun InvoiceRow(
    label: String,
    value: String,
    valueColor: Color = Slate900,
    isMonospace: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = Slate600)
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default
        )
    }
}
