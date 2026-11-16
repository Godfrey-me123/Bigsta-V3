package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.example.core.security.UserSession
import com.example.ui.components.StatusBadge

private data class DrawerEntry(val route: String, val title: String, val icon: ImageVector, val tag: String, val adminOnly: Boolean = false)
private data class DrawerGroup(val title: String, val icon: ImageVector, val entries: List<DrawerEntry>)

@Composable
fun ServiceMenuDrawer(
    session: UserSession,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    var expandedGroup by remember { mutableStateOf("Services") }
    val groups = listOf(
        DrawerGroup("Dashboard", Icons.Default.Dashboard, listOf(
            DrawerEntry(BigstaRoutes.DASHBOARD, "Home", Icons.Default.Home, "drawer_home"),
            DrawerEntry(BigstaRoutes.NOTIFICATIONS, "Notifications", Icons.Default.Notifications, "drawer_notifications"),
            DrawerEntry(BigstaRoutes.BILLING, "Tokens & Billing", Icons.Default.AccountBalanceWallet, "drawer_billing")
        )),
        DrawerGroup("Services", Icons.Default.GridView, listOf(
            DrawerEntry(BigstaRoutes.SERVICE_CATALOG, "All Services", Icons.Default.GridView, "drawer_services"),
            DrawerEntry(BigstaRoutes.FORMS, "Government Forms", Icons.Default.Description, "drawer_forms"),
            DrawerEntry(BigstaRoutes.ID_STUDIO, "ID Card Studio", Icons.Default.Badge, "drawer_id_studio"),
            DrawerEntry(BigstaRoutes.IMAGE_STUDIO, "Image Editing Studio", Icons.Default.PhotoFilter, "drawer_image_studio"),
            DrawerEntry(BigstaRoutes.PASSPORT_STUDIO, "Passport Size Photo Studio", Icons.Default.CameraAlt, "drawer_passport_studio")
        )),
        DrawerGroup("Templates", Icons.Default.Layers, listOf(
            DrawerEntry(BigstaRoutes.TEMPLATES, "Saved Templates Library", Icons.Default.Collections, "drawer_templates", adminOnly = true),
            DrawerEntry(BigstaRoutes.TEMPLATE_EDITOR, "Custom Card Studio", Icons.Default.EditNote, "drawer_template_editor", adminOnly = true)
        )),
        DrawerGroup("Print & Documents", Icons.Default.PictureAsPdf, listOf(
            DrawerEntry(BigstaRoutes.PDF_TOOLS, "PDF Reader & Print", Icons.Default.PictureAsPdf, "drawer_pdf_tools"),
            DrawerEntry(BigstaRoutes.PDF_LIBRARY, "PDF Library", Icons.Default.FolderOpen, "drawer_pdf_library"),
            DrawerEntry(BigstaRoutes.DOCX_TO_PDF, "Open DOC/DOCX to PDF", Icons.Default.Description, "drawer_docx_to_pdf"),
            DrawerEntry(BigstaRoutes.DOWNLOADS, "Downloads", Icons.Default.Download, "drawer_downloads")
        )),
        DrawerGroup("Account", Icons.Default.Settings, listOf(
            DrawerEntry(BigstaRoutes.SETTINGS, "Settings", Icons.Default.Settings, "drawer_settings"),
            DrawerEntry(BigstaRoutes.SMS, "Transaction SMS Intake", Icons.Default.Sms, "drawer_sms"),
            DrawerEntry(BigstaRoutes.ADMIN, "Administrator Console", Icons.Default.AdminPanelSettings, "drawer_admin", adminOnly = true)
        ))
    )

    ModalDrawerSheet(modifier = Modifier.width(320.dp)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("BIGsta Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(session.email.ifBlank { "User Account" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(status = if (session.isAdmin) "ADMIN" else "USER")
                if (session.isAdmin) Text("Full Control Enabled", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            groups.forEach { group ->
                val visibleEntries = group.entries.filter { !it.adminOnly || session.isAdmin }
                if (visibleEntries.isEmpty()) return@forEach
                val isExpanded = expandedGroup == group.title
                Surface(shape = RoundedCornerShape(12.dp), color = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f) else MaterialTheme.colorScheme.surface) {
                    Column {
                        NavigationDrawerItem(
                            label = { Text(group.title, fontWeight = FontWeight.SemiBold) },
                            selected = false,
                            onClick = { expandedGroup = if (isExpanded) "" else group.title },
                            icon = { Icon(group.icon, contentDescription = null) },
                            badge = { Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null) },
                            modifier = Modifier.testTag("drawer_group_${group.title.lowercase().replace(' ', '_')}")
                        )
                        if (isExpanded) {
                            visibleEntries.forEach { entry ->
                                NavigationDrawerItem(
                                    label = { Text(entry.title, fontSize = 13.sp, fontWeight = if (currentRoute == entry.route) FontWeight.Bold else FontWeight.Normal) },
                                    selected = currentRoute == entry.route,
                                    onClick = { onNavigate(entry.route); onCloseDrawer() },
                                    icon = { Icon(entry.icon, contentDescription = entry.title, modifier = Modifier.size(20.dp)) },
                                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 2.dp).testTag(entry.tag)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { onCloseDrawer(); onSignOut() }, modifier = Modifier.fillMaxWidth().testTag("drawer_logout_btn"), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp)); Text("Log Out", fontWeight = FontWeight.Bold)
            }
        }
    }
}
