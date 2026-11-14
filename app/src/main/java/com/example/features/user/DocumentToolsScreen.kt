package com.example.features.user

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentToolsScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val downloads by viewModel.localDownloads.collectAsStateWithLifecycle()

    var openedPdfFile by remember { mutableStateOf<File?>(null) }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var isRendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
                val dest = File(dir, "imported_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                openedPdfFile = dest
                errorMessage = null
            }.onFailure {
                errorMessage = "Could not open selected PDF: ${it.localizedMessage}"
            }
        }
    }

    LaunchedEffect(openedPdfFile, zoomPercent) {
        val file = openedPdfFile ?: return@LaunchedEffect
        isRendering = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val scale = (zoomPercent / 100f).coerceIn(0.75f, 2.0f)
                val pages = mutableListOf<Bitmap>()
                val limit = renderer.pageCount.coerceAtMost(25)
                for (i in 0 until limit) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * scale * 1.4f).toInt().coerceIn(300, 1800)
                        val h = (page.height * scale * 1.4f).toInt().coerceIn(300, 2600)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pages.add(bmp)
                    }
                }
                val count = renderer.pageCount
                renderer.close()
                pfd.close()
                pages to count
            }
        }
        isRendering = false
        result.onSuccess { (pages, pageCount) ->
            renderedPages = pages
            viewModel.recordLocalExport(
                id = file.name,
                fileName = file.name,
                mimeType = "application/pdf",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = pageCount,
                sourceType = "PDF_IMPORT"
            )
        }.onFailure {
            errorMessage = "Failed to render PDF: ${it.localizedMessage}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Reader & Downloads") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (openedPdfFile != null) {
                        IconButton(
                            onClick = { zoomPercent = (zoomPercent - 25).coerceAtLeast(75) },
                            modifier = Modifier.testTag("pdf_zoom_out")
                        ) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
                        }
                        IconButton(
                            onClick = { zoomPercent = (zoomPercent + 25).coerceAtMost(200) },
                            modifier = Modifier.testTag("pdf_zoom_in")
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
                        }
                        IconButton(
                            onClick = { openedPdfFile?.let { printPdfFile(context, it) } },
                            modifier = Modifier.testTag("pdf_print_btn")
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "Print PDF")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item {
                Button(
                    onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pdf_pick_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open PDF Document (Continuous Reader)")
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                item {
                    ErrorMessageBanner(
                        message = errorMessage,
                        onDismiss = { errorMessage = null }
                    )
                }
            }

            if (openedPdfFile != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${openedPdfFile?.name} (${zoomPercent}%)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        TextButton(onClick = {
                            openedPdfFile = null
                            renderedPages = emptyList()
                        }) {
                            Text("Close Reader")
                        }
                    }
                }

                if (isRendering) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    items(renderedPages) { pageBitmap ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        ) {
                            Image(
                                bitmap = pageBitmap.asImageBitmap(),
                                contentDescription = "PDF page",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Text("Downloads & Saved Files (${downloads.size})", style = MaterialTheme.typography.titleMedium)
            }

            if (downloads.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.PictureAsPdf,
                        title = "No Local Exports Yet",
                        subtitle = "Exported ID cards, edited images, and opened PDFs appear here for sharing and printing."
                    )
                }
            } else {
                items(downloads, key = { it.id }) { doc ->
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.fileName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "${doc.sourceType} • ${doc.fileSizeBytes / 1024} KB" +
                                        (doc.pageCount?.let { " • $it pages" } ?: ""),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    val file = File(doc.localFilePath)
                                    if (file.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = doc.mimeType
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share File"))
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share file")
                            }
                            IconButton(
                                onClick = {
                                    File(doc.localFilePath).delete()
                                    viewModel.deleteLocalDownload(doc.id)
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete file")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun printPdfFile(context: Context, file: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.localizedMessage)
            }
        }
    }
    printManager.print(file.name, adapter, null)
}
