package com.example.features.user

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SolidSectionCard
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(viewModel: UserDashboardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val downloads by viewModel.localDownloads.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(icon = Icons.Default.Download, title = "No Downloads Yet", subtitle = "Files you export or save in BIGsta appear here.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                items(downloads, key = { it.id }) { doc ->
                    SolidSectionCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(doc.fileName, fontWeight = FontWeight.SemiBold)
                                Text("${doc.sourceType} • ${doc.fileSizeBytes / 1024} KB${doc.pageCount?.let { " • $it pages" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                val file = File(doc.localFilePath)
                                if (file.exists()) runCatching {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_SEND).apply { type = doc.mimeType; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                    context.startActivity(Intent.createChooser(intent, "Share file"))
                                }
                            }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                            IconButton(onClick = { File(doc.localFilePath).delete(); viewModel.deleteLocalDownload(doc.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                        }
                    }
                }
            }
        }
    }
}
