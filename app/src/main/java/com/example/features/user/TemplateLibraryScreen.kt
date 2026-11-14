package com.example.features.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SolidSectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateLibraryScreen(viewModel: UserDashboardViewModel, onBack: () -> Unit, onOpenStudio: () -> Unit) {
    val templates by viewModel.cachedTemplates.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Saved Templates") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        }, actions = {
            TextButton(onClick = onOpenStudio) { Text("Open Studio") }
        })
    }) { padding ->
        if (templates.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(icon = Icons.Default.Collections, title = "No Saved Templates", subtitle = "Templates synced from your account appear here. Create or manage templates in the studio.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                item { Text("${templates.size} templates synced from the account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(templates, key = { it.id }) { template ->
                    SolidSectionCard(onClick = onOpenStudio) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(template.name, fontWeight = FontWeight.SemiBold)
                                Text("${template.documentCategoryId} • ${template.side}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (template.isActive) "Active" else "Inactive", style = MaterialTheme.typography.labelSmall, color = if (template.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
