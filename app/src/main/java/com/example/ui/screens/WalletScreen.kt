package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.RiderViewModel
import com.example.ui.Screen
import com.example.ui.components.EmptyState
import com.example.ui.components.Format
import com.example.ui.components.LoadingBox
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionTitle
import com.example.ui.components.StatusChip
import com.example.ui.components.Tone
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextSecondary

@Composable
fun WalletScreen(viewModel: RiderViewModel) {
    val wallet by viewModel.wallet.collectAsStateWithLifecycle()
    RiderShell(viewModel, title = "Wallet", tab = Screen.Wallet) {
        if (wallet.loading && wallet.transactions.isEmpty()) {
            LoadingBox()
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    SectionCard {
                        Text("GoRide wallet balance", color = TextSecondary)
                        Text(Format.money(wallet.balance), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Refunds and referral rewards land here. Use it to pay for any ride.",
                            color = TextSecondary, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (wallet.refunds.isNotEmpty()) {
                    item { SectionTitle("Refunds") }
                    items(wallet.refunds, key = { "r" + it.id }) { r ->
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(Format.money(r.amount), fontWeight = FontWeight.Bold)
                                    Text(Format.dateTime(r.requestedAt), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    r.reason?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
                                }
                                StatusChip(r.statusLabel, if (r.status == "rejected") Tone.Bad else if (r.status == "pending") Tone.Warn else Tone.Good)
                            }
                        }
                    }
                }
                item { SectionTitle("Transactions") }
                if (wallet.transactions.isEmpty()) {
                    item { EmptyState(Icons.Default.AccountBalanceWallet, "No transactions yet", "Credits and payments from your wallet show up here.") }
                }
                items(wallet.transactions, key = { it.id }) { t ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.reason?.let(Format::title) ?: if (t.isCredit) "Credit" else "Payment", fontWeight = FontWeight.Medium)
                                Text(Format.dateTime(t.createdAt), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                (if (t.isCredit) "+ " else "- ") + Format.money(t.amount),
                                color = if (t.isCredit) SuccessGreen else DangerRed,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
