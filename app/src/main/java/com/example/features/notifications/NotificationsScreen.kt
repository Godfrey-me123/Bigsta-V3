package com.example.features.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.AppNotificationDao
import com.example.core.utilities.AppPreferences
import com.example.core.utilities.AppUserSettings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    notificationDao: AppNotificationDao,
    appPreferences: AppPreferences,
    onNavigateRoute: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifications by notificationDao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by appPreferences.settingsFlow.collectAsStateWithLifecycle(initialValue = AppUserSettings())

    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications & Alerts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { notificationDao.markAllRead() } }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item {
                    SolidSectionCard {
                        Text("Enable Android System Notifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Allow notifications to receive alerts for payment confirmations, recharge approvals, and low token balances.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Grant Notification Permission")
                        }
                    }
                }
            }

            item {
                SolidSectionCard {
                    Text("Notification Channels", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    NotificationToggleRow("Payment Submissions", settings.notifyPayments) {
                        scope.launch { appPreferences.updateNotificationSetting(payments = it) }
                    }
                    NotificationToggleRow("Recharge Approvals & Rejections", settings.notifyRecharges) {
                        scope.launch { appPreferences.updateNotificationSetting(recharges = it) }
                    }
                    NotificationToggleRow("Token Balance Alerts", settings.notifyBalanceAlerts) {
                        scope.launch { appPreferences.updateNotificationSetting(balance = it) }
                    }
                    NotificationToggleRow("Administrator Alerts", settings.notifyAdminAlerts) {
                        scope.launch { appPreferences.updateNotificationSetting(admin = it) }
                    }
                }
            }

            item {
                Text("Recent Alerts (${notifications.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (notifications.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Notifications,
                        title = "No Notifications Yet",
                        subtitle = "Payment, recharge, and account status alerts will appear here."
                    )
                }
            } else {
                items(notifications, key = { it.eventKey }) { item ->
                    SolidSectionCard(
                        onClick = {
                            scope.launch { notificationDao.markRead(item.eventKey) }
                            onNavigateRoute(item.targetRoute)
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    item.body,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!item.isRead) {
                                Badge { Text("NEW", fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
