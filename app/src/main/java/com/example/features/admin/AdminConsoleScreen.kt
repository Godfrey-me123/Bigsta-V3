package com.example.features.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.NidaRequestDto
import com.example.data.models.PaymentRequestDto
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaErrorRed
import com.example.ui.theme.BigstaSuccessGreen

enum class AdminTab(val label: String) {
    PAYMENTS("Payments"),
    REQUESTS("Requests"),
    USERS("Users"),
    TEMPLATES("Templates"),
    SMS_LOGS("SMS Logs"),
    FEEDBACK("Feedback"),
    SETTINGS("Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(AdminTab.PAYMENTS) }
    var paymentNoteTarget by remember { mutableStateOf<Pair<PaymentRequestDto, String>?>(null) }
    var nidaNoteTarget by remember { mutableStateOf<Pair<NidaRequestDto, String>?>(null) }
    var noteInput by remember { mutableStateOf("") }

    LaunchedEffect(session.isAdmin) {
        if (session.isAdmin) {
            viewModel.loadAdminDashboard()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                        Text("Full Admin Console")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session.isAdmin) {
                        IconButton(
                            onClick = { viewModel.loadAdminDashboard() },
                            modifier = Modifier.testTag("admin_refresh_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh admin data")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (!session.isAdmin) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.Block,
                    title = "Administrator Access Required",
                    subtitle = "Access is strictly verified against protected user_roles records in Supabase. Standard user accounts cannot open admin tools."
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp
            ) {
                AdminTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, fontSize = 13.sp) },
                        modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
                    )
                }
            }

            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                if (!state.errorMessage.isNullOrBlank()) {
                    item {
                        ErrorMessageBanner(
                            message = state.errorMessage,
                            onDismiss = { viewModel.clearMessages() }
                        )
                    }
                }

                if (!state.actionMessage.isNullOrBlank()) {
                    item {
                        SolidSectionCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    state.actionMessage.orEmpty(),
                                    color = BigstaSuccessGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.clearMessages() }) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                when (selectedTab) {
                    AdminTab.PAYMENTS -> {
                        if (state.paymentRequests.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "No Payment Requests",
                                    subtitle = "Submitted token recharge requests from payment_requests will appear here."
                                )
                            }
                        } else {
                            items(state.paymentRequests, key = { it.id }) { req ->
                                SolidSectionCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${req.packageName ?: "Pack"} (${req.tokens} tokens)",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "${req.amount.toInt()} TZS • ${req.paymentMethod ?: "Mobile Money"}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                "Ref: ${req.transactionRef ?: "N/A"} • Phone: ${req.senderPhone ?: "N/A"}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(status = req.status)
                                    }
                                    if (!req.smsText.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            req.smsText,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!req.adminNote.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Note: ${req.adminNote}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                    if (req.status.equals("pending", ignoreCase = true)) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    noteInput = ""
                                                    paymentNoteTarget = req to "approved"
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("approve_pay_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Approve")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    noteInput = ""
                                                    paymentNoteTarget = req to "rejected"
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("reject_pay_${req.id}")
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = null, tint = BigstaErrorRed, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Reject", color = BigstaErrorRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.REQUESTS -> {
                        if (state.nidaRequests.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "No Service Requests",
                                    subtitle = "Submitted NIDA, Driving Licence, NHIF, and NRC Zambia applications appear here."
                                )
                            }
                        } else {
                            items(state.nidaRequests, key = { it.id }) { req ->
                                SolidSectionCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.applicantDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "${req.serviceType} • Phone: ${req.phone ?: "N/A"} • DOB: ${req.dateOfBirth ?: "N/A"}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!req.nationalId.isNullOrBlank()) {
                                                Text("ID: ${req.nationalId}", fontSize = 12.sp)
                                            }
                                        }
                                        StatusBadge(status = req.status)
                                    }
                                    if (!req.adminNotes.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Notes: ${req.adminNotes}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("processing", "completed", "rejected").forEach { nextStatus ->
                                            OutlinedButton(
                                                onClick = {
                                                    noteInput = req.adminNotes.orEmpty()
                                                    nidaNoteTarget = req to nextStatus
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(nextStatus.uppercase(), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.USERS -> {
                        if (state.userProfiles.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "No Profiles Loaded",
                                    subtitle = "User profiles from Supabase profiles table will appear here."
                                )
                            }
                        } else {
                            items(state.userProfiles, key = { it.id }) { profile ->
                                SolidSectionCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(profile.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                listOfNotNull(profile.email, profile.phone, profile.region)
                                                    .filter { it.isNotBlank() }
                                                    .joinToString(" • ")
                                                    .ifBlank { profile.id },
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusBadge(status = "${profile.effectiveTokens} TOKENS")
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.TEMPLATES -> {
                        if (state.templates.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.AdminPanelSettings,
                                    title = "No Templates Found",
                                    subtitle = "Templates grouped by document_category_id, template_card_id, and side appear here."
                                )
                            }
                        } else {
                            items(state.templates, key = { it.id }) { tpl ->
                                SolidSectionCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(tpl.name.ifBlank { tpl.resolvedCategory }, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text(
                                                "Category: ${tpl.resolvedCategory} • Card: ${tpl.templateCardId ?: "default"} • Side: ${tpl.side.uppercase()}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = tpl.isActive,
                                            onCheckedChange = { active -> viewModel.toggleTemplateActive(tpl, active) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AdminTab.SMS_LOGS -> {
                        item {
                            SolidSectionCard {
                                Text("SMS Gateway & Payment Logs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (state.smsMonitorRaw.isBlank()) {
                                    Text(
                                        "No SMS gateway records returned from backend.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        state.smsMonitorRaw,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    AdminTab.FEEDBACK -> {
                        item {
                            SolidSectionCard {
                                Text("User Feedback & Support Tickets", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "User feedback and support issues submitted through the app are monitored and answered here under full administrator control.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    AdminTab.SETTINGS -> {
                        item {
                            SolidSectionCard {
                                Text("System Settings & Webhook Control", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Global payment methods, Lipa Namba configurations, SMS webhooks, and maintenance toggles are managed through Supabase system_settings.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    paymentNoteTarget?.let { (req, targetStatus) ->
        AlertDialog(
            onDismissRequest = { paymentNoteTarget = null },
            title = { Text("${targetStatus.replaceFirstChar { it.uppercase() }} Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ref: ${req.transactionRef ?: req.id} • ${req.tokens} tokens")
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Admin Note (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updatePaymentStatus(req.id, targetStatus, noteInput.takeIf { it.isNotBlank() })
                        paymentNoteTarget = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentNoteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    nidaNoteTarget?.let { (req, targetStatus) ->
        AlertDialog(
            onDismissRequest = { nidaNoteTarget = null },
            title = { Text("Set Status: ${targetStatus.uppercase()}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${req.applicantDisplayName} (${req.serviceType})")
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        label = { Text("Admin Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNidaStatus(req.id, targetStatus, noteInput.takeIf { it.isNotBlank() })
                        nidaNoteTarget = null
                    }
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { nidaNoteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
