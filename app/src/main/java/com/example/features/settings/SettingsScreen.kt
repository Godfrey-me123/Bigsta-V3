package com.example.features.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.network.SupabaseConfig
import com.example.features.authentication.AuthViewModel
import com.example.features.user.UserDashboardViewModel
import com.example.ui.components.ConnectivityStatusBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaErrorRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    dashboardViewModel: UserDashboardViewModel,
    onNavigate: (String) -> Unit
) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()
    val connectivity by dashboardViewModel.connectivity.collectAsStateWithLifecycle()
    val env = remember { SupabaseConfig.load() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Account & Settings") })
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
            item {
                ConnectivityStatusBanner(
                    connectivity = connectivity,
                    onRetryProbe = { dashboardViewModel.probeConnection() }
                )
            }

            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                dashboardState.profile?.displayName ?: "BIGsta Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                session.email.ifBlank { dashboardState.profile?.email.orEmpty() },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "User ID: ${session.userId.take(12)}...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(status = if (session.isAdmin) "ADMIN" else "USER")
                    }
                }
            }

            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backend Environment", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                if (env.isConfigured) "Supabase Project: ${env.projectId}" else "Secrets not configured",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                connectivity.databaseStatus,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { dashboardViewModel.probeConnection() },
                            modifier = Modifier.testTag("settings_probe_btn")
                        ) {
                            Text("Probe")
                        }
                    }
                }
            }

            item {
                Text("BIGsta Tools", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            item {
                SolidSectionCard(onClick = { onNavigate("templates") }, modifier = Modifier.testTag("settings_templates_row")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Collections, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("Saved Templates", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Browse templates synced from the account", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                SolidSectionCard(onClick = { onNavigate("downloads") }, modifier = Modifier.testTag("settings_downloads_row")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("Downloads & Storage", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Manage exported files saved on this device", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                SolidSectionCard(onClick = { onNavigate("passport_studio") }, modifier = Modifier.testTag("settings_passport_row")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Column(Modifier.weight(1f)) {
                            Text("Passport Photo Studio", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Photo size specifications and printable sheets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item {
                SolidSectionCard(
                    onClick = { onNavigate("notifications") },
                    modifier = Modifier.testTag("settings_notifications_row")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notifications & Channels", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Manage alerts for payments, recharges, and low balance", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open notifications")
                    }
                }
            }

            item {
                SolidSectionCard(
                    onClick = { onNavigate("sms") },
                    modifier = Modifier.testTag("settings_sms_row")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Transaction SMS Intake & Consent", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Configure providers and offline WorkManager retry queue", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Open SMS intake")
                    }
                }
            }

            if (session.isAdmin) {
                item {
                    SolidSectionCard(
                        onClick = { onNavigate("admin") },
                        modifier = Modifier.testTag("settings_admin_row")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Administrator Console", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Verified role in user_roles", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open Admin Console")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { authViewModel.signOut() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_signout_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = BigstaErrorRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", color = BigstaErrorRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
