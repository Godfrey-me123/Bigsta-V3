package com.example.features.user

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.models.WeeklyOfferDto
import com.example.ui.components.ConnectivityStatusBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaAccentOrange
import com.example.ui.theme.BigstaSecondaryLight

data class StudioToolShortcut(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val tag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDashboardScreen(
    viewModel: UserDashboardViewModel,
    unreadNotifications: Int,
    onNavigate: (String) -> Unit,
    onSelectOffer: (WeeklyOfferDto) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val connectivity by viewModel.connectivity.collectAsStateWithLifecycle()

    var requestQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }

    val shortcuts = listOf(
        StudioToolShortcut("ID Card Studio", "Front & Back canvas + export", Icons.Default.Badge, "id_studio", "nav_id_studio"),
        StudioToolShortcut("Gov Forms", "NIDA, License, NHIF & NRC", Icons.Default.Description, "forms", "nav_forms"),
        StudioToolShortcut("PDF & Print", "Reader, Print & Downloads", Icons.Default.PictureAsPdf, "pdf_tools", "nav_pdf_tools"),
        StudioToolShortcut("Image Studio", "Crop, passport, watermark", Icons.Default.PhotoFilter, "image_studio", "nav_image_studio"),
        StudioToolShortcut("SMS Intake", "Parse & queue payment SMS", Icons.Default.Sms, "sms", "nav_sms"),
        StudioToolShortcut("Recharge", "Token packs & Lipa Namba", Icons.Default.AccountBalanceWallet, "billing", "nav_billing")
    )

    val filteredRequests = remember(state.userRequests, requestQuery, statusFilter) {
        state.userRequests.filter { req ->
            val matchesQuery = requestQuery.isBlank() ||
                req.applicantDisplayName.contains(requestQuery, ignoreCase = true) ||
                req.serviceType.contains(requestQuery, ignoreCase = true) ||
                req.id.contains(requestQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "ALL" || req.status.equals(statusFilter, ignoreCase = true)
            matchesQuery && matchesStatus
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BIGsta Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            state.profile?.displayName ?: session.email,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        modifier = Modifier.testTag("dashboard_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh dashboard")
                    }
                    IconButton(
                        onClick = { onNavigate("notifications") },
                        modifier = Modifier.testTag("dashboard_notifications_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge { Text(unreadNotifications.toString()) }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
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
            item {
                ConnectivityStatusBanner(
                    connectivity = connectivity,
                    onRetryProbe = { viewModel.probeConnection() }
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                item {
                    ErrorMessageBanner(
                        message = state.errorMessage,
                        onDismiss = { viewModel.clearFeedback() }
                    )
                }
            }

            // Token Balance & Account Summary Card (Solid Color, No Gradient)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Available Token Balance",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${state.profile?.effectiveTokens ?: 0} Tokens",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                if (session.isAdmin) "Verified Role: Administrator" else "Verified Role: Standard User",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                            )
                        }
                        FilledTonalButton(
                            onClick = { onNavigate("billing") },
                            modifier = Modifier.testTag("dashboard_recharge_cta")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Recharge")
                        }
                    }
                }
            }

            // Admin Console Entry Banner — shown ONLY if verified against user_roles
            if (session.isAdmin) {
                item {
                    SolidSectionCard(
                        onClick = { onNavigate("admin") },
                        modifier = Modifier.testTag("admin_entry_card")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BigstaSecondaryLight.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = BigstaSecondaryLight)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Administrator Console", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    "Approve recharges, manage NIDA requests, templates & SMS logs",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open Admin Console")
                        }
                    }
                }
            }

            // Weekly Offers Carousel (e.g. Special Creator Pass)
            if (state.weeklyOffers.isNotEmpty()) {
                item {
                    Text("Active Weekly Offers", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.weeklyOffers, key = { it.id }) { offer ->
                            Surface(
                                onClick = { onSelectOffer(offer) },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, BigstaAccentOrange.copy(alpha = 0.6f)),
                                modifier = Modifier.width(240.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        offer.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = BigstaAccentOrange
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${offer.tokens} Tokens • ${offer.price.toInt()} TZS",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp
                                    )
                                    if (!offer.description.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            offer.description,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Native Studios & Tools Grid
            item {
                Text("Studios & Tools", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    shortcuts.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { item ->
                                SolidSectionCard(
                                    onClick = { onNavigate(item.route) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag(item.tag)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        item.subtitle,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Recent User Requests + Search/Status Filter
            item {
                Text("Recent Requests", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = requestQuery,
                    onValueChange = { requestQuery = it },
                    placeholder = { Text("Search requests by name, ID, or service") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("ALL", "pending", "processing", "completed", "rejected")) { status ->
                        FilterChip(
                            selected = statusFilter == status,
                            onClick = { statusFilter = status },
                            label = { Text(status.uppercase(), fontSize = 11.sp) }
                        )
                    }
                }
            }

            if (state.isLoading && state.userRequests.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredRequests.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Inbox,
                        title = "No Requests Found",
                        subtitle = "Submit a NIDA, Driving License, NHIF, or NRC Zambia form to track it here."
                    )
                }
            } else {
                items(filteredRequests, key = { it.id }) { req ->
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.applicantDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${req.serviceType} • ${req.phone ?: "No phone"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!req.adminNotes.isNullOrBlank()) {
                                    Text(
                                        "Admin note: ${req.adminNotes}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                            StatusBadge(status = req.status)
                        }
                    }
                }
            }
        }
    }
}
