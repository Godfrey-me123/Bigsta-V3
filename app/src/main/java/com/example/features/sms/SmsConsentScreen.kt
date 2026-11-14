package com.example.features.sms

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.sms.SmsParseOutcome
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaSuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsConsentScreen(
    viewModel: SmsViewModel,
    onBack: (() -> Unit)? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val pendingQueue by viewModel.pendingQueue.collectAsStateWithLifecycle()

    val supportedProviders = listOf("M-Pesa", "Tigo Pesa", "Airtel Money", "HaloPesa", "NMB", "CRDB")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction SMS Intake") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.retryQueuedUploads() },
                        modifier = Modifier.testTag("sms_sync_btn")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync pending SMS queue")
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
            // Policy & Privacy Disclosure Card
            item {
                SolidSectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text("User-Consented Transaction Processing", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "BIGsta does NOT read your SMS inbox or personal messages. You can share or paste a payment confirmation SMS (M-Pesa, Tigo Pesa, Airtel Money, HaloPesa, NMB, CRDB). Only transaction reference, amount, provider, date/time, and type are extracted. Login OTPs, PINs, and auth codes are automatically blocked.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (settings.smsMonitoringEnabled) "Transaction Intake: ACTIVE" else "Transaction Intake: PAUSED",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                "I consent to parsing payment confirmation SMS I submit.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.smsMonitoringEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setSmsMonitoringEnabled(consentAccepted = enabled, enabled = enabled)
                            },
                            modifier = Modifier.testTag("sms_consent_switch")
                        )
                    }
                }
            }

            // Supported Providers Config
            item {
                Text("Enabled Payment Providers", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(supportedProviders) { provider ->
                        val enabled = provider in settings.enabledSmsProviders
                        FilterChip(
                            selected = enabled,
                            onClick = { viewModel.toggleProvider(provider, !enabled) },
                            label = { Text(provider, fontSize = 12.sp) }
                        )
                    }
                }
            }

            if (!uiState.errorMessage.isNullOrBlank()) {
                item { ErrorMessageBanner(message = uiState.errorMessage) }
            }

            if (!uiState.statusMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = BigstaSuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BigstaSuccessGreen.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BigstaSuccessGreen)
                            Text(uiState.statusMessage.orEmpty(), fontSize = 13.sp)
                        }
                    }
                }
            }

            // Share / Paste Transaction SMS Intake Form
            item {
                SolidSectionCard {
                    Text("Submit or Share Payment Confirmation SMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tip: From your SMS app, tap Share -> BIGsta, or paste the confirmation message below.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.inputSmsText,
                        onValueChange = { viewModel.updateInputText(it) },
                        label = { Text("Payment Confirmation SMS") },
                        placeholder = { Text("e.g. BKL9X82M1P Confirmed. You paid TZS 6,000.00 to BIGSTA on 14/11/26 at 10:45 AM via M-Pesa.") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_input_field")
                    )

                    when (val preview = uiState.previewOutcome) {
                        is SmsParseOutcome.ValidTransaction -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("Extracted Approved Fields", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Ref: ${preview.transactionRef}", fontSize = 12.sp)
                                    Text("Amount: ${preview.currency} ${preview.amount}", fontSize = 12.sp)
                                    Text("Provider: ${preview.provider} (${preview.transactionType})", fontSize = 12.sp)
                                    Text("Date/Time: ${preview.transactionDateTime}", fontSize = 12.sp)
                                }
                            }
                        }
                        is SmsParseOutcome.Rejected -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            ErrorMessageBanner(message = preview.reason)
                        }
                        null -> Unit
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.submitCurrentSms() },
                        enabled = !uiState.isSubmitting && settings.smsMonitoringEnabled,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sms_submit_btn")
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify & Forward Transaction")
                        }
                    }
                }
            }

            // Pending Offline Queue
            if (pendingQueue.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Offline Retry Queue (${pendingQueue.size})", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { viewModel.retryQueuedUploads() }) {
                            Text("Retry Now")
                        }
                    }
                }
                items(pendingQueue, key = { it.idempotencyKey }) { item ->
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.idempotencyKey, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "Retries: ${item.retryCount} • ${item.lastError ?: "Waiting for network"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = item.status)
                        }
                    }
                }
            }

            // Processed SMS History
            item {
                Text("Processed SMS Records (${transactions.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (transactions.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Sms,
                        title = "No Transaction SMS Processed",
                        subtitle = "Enable intake above and paste or share a supported payment confirmation SMS."
                    )
                }
            } else {
                items(transactions, key = { it.transactionRef }) { tx ->
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${tx.provider} • ${tx.transactionRef}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${tx.currency} ${tx.amount} • ${tx.transactionDateTime}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = tx.status)
                        }
                    }
                }
            }
        }
    }
}
