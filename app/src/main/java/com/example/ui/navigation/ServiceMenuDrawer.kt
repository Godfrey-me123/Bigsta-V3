package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.core.security.UserSession
import com.example.ui.components.StatusBadge

data class DrawerMenuItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val tag: String,
    val adminOnly: Boolean = false
)

@Composable
fun ServiceMenuDrawer(
    session: UserSession,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val menuItems = listOf(
        DrawerMenuItem(BigstaRoutes.DASHBOARD, "Home Dashboard", Icons.Default.Home, "drawer_home"),
        DrawerMenuItem(BigstaRoutes.ID_STUDIO, "ID Card Studio", Icons.Default.Badge, "drawer_id_studio"),
        DrawerMenuItem("template_editor", "Template Studio Editor", Icons.Default.EditNote, "drawer_template_editor"),
        DrawerMenuItem(BigstaRoutes.FORMS, "Government Forms", Icons.Default.Description, "drawer_forms"),
        DrawerMenuItem(BigstaRoutes.PDF_TOOLS, "PDF & Document Tools", Icons.Default.PictureAsPdf, "drawer_pdf_tools"),
        DrawerMenuItem(BigstaRoutes.IMAGE_STUDIO, "Image Editing Studio", Icons.Default.PhotoFilter, "drawer_image_studio"),
        DrawerMenuItem(BigstaRoutes.BILLING, "Tokens & Billing", Icons.Default.AccountBalanceWallet, "drawer_billing"),
        DrawerMenuItem(BigstaRoutes.SMS, "Transaction SMS Intake", Icons.Default.Sms, "drawer_sms"),
        DrawerMenuItem(BigstaRoutes.NOTIFICATIONS, "Notifications", Icons.Default.Notifications, "drawer_notifications"),
        DrawerMenuItem(BigstaRoutes.SETTINGS, "Account Settings", Icons.Default.Settings, "drawer_settings"),
        DrawerMenuItem(BigstaRoutes.ADMIN, "Administrator Console", Icons.Default.AdminPanelSettings, "drawer_admin", adminOnly = true)
    )

    ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drawer Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("BIGsta Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        session.email.ifBlank { "User Account" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = if (session.isAdmin) "ADMIN" else "USER")
                if (session.isAdmin) {
                    Text("Full Control Enabled", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Navigation Items
            menuItems.forEach { item ->
                if (item.adminOnly && !session.isAdmin) return@forEach

                val selected = currentRoute == item.route
                NavigationDrawerItem(
                    label = { Text(item.title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    selected = selected,
                    onClick = {
                        onNavigate(item.route)
                        onCloseDrawer()
                    },
                    icon = { Icon(item.icon, contentDescription = item.title) },
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .testTag(item.tag)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Log Out Button
            OutlinedButton(
                onClick = {
                    onCloseDrawer()
                    onSignOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_logout_btn"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
        }
    }
}
