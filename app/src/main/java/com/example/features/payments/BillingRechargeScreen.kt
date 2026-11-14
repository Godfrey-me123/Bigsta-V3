package com.example.features.payments

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaAccentOrange
import com.example.ui.theme.BigstaSuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingRechargeScreen(
    viewModel: PaymentsViewModel,
    currentBalance: Int,
    onOpenSmsIntake: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val defaultMethods = listOf("M-Pesa", "Tigo Pesa", "Airtel Money", "HaloPesa")
    var selectedMethod by remember { mutableStateOf("M-Pesa") }
    var transactionRef by remember { mutableStateOf("") }
    var senderPhone by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tokens & Recharge") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadBillingData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh packages")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            // Token Balance Header (Solid Color, No Gradient)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Current Wallet Balance",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            )
                            Text(
                                "$currentBalance Tokens",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenSmsIntake,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parse SMS")
                        }
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    ErrorMessageBanner(
                        message = state.errorMessage,
                        onDismiss = { viewModel.clearMessages() }
                    )
                }
            }

            if (!state.successMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = BigstaSuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BigstaSuccessGreen.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BigstaSuccessGreen)
                            Text(state.successMessage.orEmpty(), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Weekly Offers
            if (state.weeklyOffers.isNotEmpty()) {
                item {
                    Text("Weekly Offers", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.weeklyOffers, key = { it.id }) { offer ->
                            val isSelected = state.selectedPackage?.id == offer.id
                            Surface(
                                onClick = { viewModel.selectOfferAsPackage(offer) },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) BigstaAccentOrange.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) BigstaAccentOrange else MaterialTheme.colorScheme.outline
                                ),
                                modifier = Modifier.width(220.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(offer.title, fontWeight = FontWeight.Bold, color = BigstaAccentOrange, fontSize = 14.sp)
                                    Text("${offer.tokens} Tokens • ${offer.price.toInt()} TZS", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Token Packages from Supabase
            item {
                Text("Token Packages", style = MaterialTheme.typography.titleMedium)
            }

            if (state.isLoading && state.packages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (state.packages.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = "No Packages Loaded",
                        subtitle = "Connect to Supabase to load active token_packages from the database."
                    )
                }
            } else {
                items(state.packages, key = { it.id }) { pkg ->
                    val selected = state.selectedPackage?.id == pkg.id
                    Surface(
                        onClick = { viewModel.selectPackage(pkg) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("package_card_${pkg.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "${pkg.usages} Tokens" + if (pkg.bonus > 0) " + ${pkg.bonus} Bonus" else "",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!pkg.description.isNullOrBlank()) {
                                    Text(pkg.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                "${pkg.price.toInt()} TZS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Payment Instructions (from system_settings if configured) + Submission Form
            item {
                SolidSectionCard {
                    Text("Submit Payment Confirmation", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.selectedPackage?.let {
                            "Selected: ${it.name} (${it.usages + it.bonus} Tokens — ${it.price.toInt()} TZS)"
                        } ?: "Select a package above before submitting.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(defaultMethods) { method ->
                            FilterChip(
                                selected = selectedMethod == method,
                                onClick = { selectedMethod = method },
                                label = { Text(method, fontSize = 12.sp) }
                            )
                        }
                    }

                    // Show configured Lipa Namba / USSD for selected provider if present in system_settings
                    val methodConfig = state.paymentMethods.firstOrNull {
                        it.provider.equals(selectedMethod, ignoreCase = true)
                    }
                    if (methodConfig != null && methodConfig.lipaNamba.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Lipa Namba: ${methodConfig.lipaNamba}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (methodConfig.accountName.isNotBlank()) {
                                    Text("Account: ${methodConfig.accountName}", fontSize = 12.sp)
                                }
                                if (methodConfig.ussdCode.isNotBlank()) {
                                    Text("USSD: ${methodConfig.ussdCode}", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = transactionRef,
                        onValueChange = { transactionRef = it },
                        label = { Text("Transaction Reference Code") },
                        placeholder = { Text("e.g. BKL9X82M1P") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_tx_ref_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = senderPhone,
                        onValueChange = { senderPhone = it },
                        label = { Text("Sender Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_phone_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.submitRecharge(
                                paymentMethod = selectedMethod,
                                transactionRef = transactionRef,
                                senderPhone = senderPhone
                            )
                        },
                        enabled = !state.isSubmitting && state.selectedPackage != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("payment_submit_btn")
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit Payment Request")
                        }
                    }
                }
            }

            // Transaction History
            item {
                Text("Transaction History (${state.paymentHistory.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (state.paymentHistory.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.ReceiptLong,
                        title = "No Payment Submissions Yet",
                        subtitle = "Submitted recharge requests will appear here with verification status."
                    )
                }
            } else {
                items(state.paymentHistory, key = { it.id }) { tx ->
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.packageName ?: "Token Recharge", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${tx.paymentMethod ?: "-"} • Ref: ${tx.transactionRef ?: "-"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "${tx.tokens} Tokens • ${tx.amount.toInt()} TZS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (!tx.adminNote.isNullOrBlank()) {
                                    Text(
                                        "Note: ${tx.adminNote}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            StatusBadge(status = tx.status)
                        }
                    }
                }
            }
        }
    }
}
