package com.example.features.user

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class MergeFileItem(
    val id: String,
    val file: File,
    val name: String,
    val sizeBytes: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentToolsScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by viewModel.localDownloads.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Continuous Viewer & Print, 1 = Merge PDFs, 2 = Remove Pages
    var openedPdfFile by remember { mutableStateOf<File?>(null) }
    var renderedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var zoomPercent by remember { mutableIntStateOf(100) }
    var isRendering by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Merge state
    var mergeFiles by remember { mutableStateOf<List<MergeFileItem>>(emptyList()) }
    var isMerging by remember { mutableStateOf(false) }

    // Page removal state
    var pagesSelectedForRemoval by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pagePendingDeleteConfirmation by remember { mutableStateOf<Int?>(null) }
    var isSavingCleanPdf by remember { mutableStateOf(false) }

    // Single PDF Picker
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
                pagesSelectedForRemoval = emptySet()
                errorMessage = null
                statusMessage = "Opened ${dest.name}"
            }.onFailure {
                errorMessage = "Could not open selected PDF: ${it.localizedMessage}"
            }
        }
    }

    // Multiple PDFs Picker for Merge
    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val dir = File(context.filesDir, "merge_inputs").apply { mkdirs() }
            val newItems = uris.mapIndexedNotNull { index, uri ->
                runCatching {
                    val dest = File(dir, "merge_src_${System.currentTimeMillis()}_$index.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    val name = uri.lastPathSegment?.substringAfterLast('/') ?: dest.name
                    MergeFileItem(
                        id = "${dest.name}_$index",
                        file = dest,
                        name = name,
                        sizeBytes = dest.length()
                    )
                }.getOrNull()
            }
            mergeFiles = mergeFiles + newItems
            statusMessage = "Added ${newItems.size} file(s) for merging"
        }
    }

    // Render PDF pages continuously
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
                val limit = renderer.pageCount.coerceAtMost(50)
                for (i in 0 until limit) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * scale * 1.3f).toInt().coerceIn(300, 1800)
                        val h = (page.height * scale * 1.3f).toInt().coerceIn(300, 2600)
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
                sourceType = "PDF_READER"
            )
        }.onFailure {
            errorMessage = "Failed to render PDF: ${it.localizedMessage}"
        }
    }

    // In-App Native Confirmation Dialog for Page Deletion (No browser URL popup!)
    if (pagePendingDeleteConfirmation != null) {
        val pageNum = pagePendingDeleteConfirmation ?: 1
        val totalPages = renderedPages.size
        AlertDialog(
            onDismissRequest = { pagePendingDeleteConfirmation = null },
            title = {
                Text(
                    "BIGsta Document Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    "BIGsta requires permission to delete Page $pageNum of $totalPages from this document. This cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pagePendingDeleteConfirmation ?: 1
                        pagesSelectedForRemoval = pagesSelectedForRemoval + target
                        pagePendingDeleteConfirmation = null
                        statusMessage = "Marked Page $target for deletion"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Page", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pagePendingDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF & Document Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (openedPdfFile != null && activeTab == 0) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Selector
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("PDF Reader", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Merge PDFs", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Page Manager", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.ViewCarousel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                // Status / Error Banners
                if (!errorMessage.isNullOrBlank()) {
                    item {
                        ErrorMessageBanner(
                            message = errorMessage,
                            onDismiss = { errorMessage = null }
                        )
                    }
                }
                if (!statusMessage.isNullOrBlank()) {
                    item {
                        Text(statusMessage.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                }

                // TAB 0: CONTINUOUS PDF READER & PRINT
                if (activeTab == 0) {
                    item {
                        Button(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("pdf_pick_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (openedPdfFile != null) "Open Different PDF" else "Open PDF Document")
                        }
                    }

                    if (openedPdfFile != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(openedPdfFile?.name.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${renderedPages.size} pages rendered • Zoom $zoomPercent%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    TextButton(onClick = { openedPdfFile?.let { printPdfFile(context, it) } }) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Print")
                                    }
                                    TextButton(onClick = {
                                        openedPdfFile = null
                                        renderedPages = emptyList()
                                    }) {
                                        Text("Close")
                                    }
                                }
                            }
                        }

                        if (isRendering) {
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
                        } else {
                            itemsIndexed(renderedPages) { idx, pageBitmap ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    Text("Page ${idx + 1} of ${renderedPages.size}", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                                    Image(
                                        bitmap = pageBitmap.asImageBitmap(),
                                        contentDescription = "PDF page ${idx + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 1: MERGE PDFS
                if (activeTab == 1) {
                    item {
                        SolidSectionCard {
                            Text("Combine Multiple PDFs into One Document", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Select two or more PDF files from your device. You can rearrange their order before merging.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add PDF Files to Merge")
                            }
                        }
                    }

                    if (mergeFiles.isNotEmpty()) {
                        item {
                            Text("Selected PDFs to Merge (${mergeFiles.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        itemsIndexed(mergeFiles) { index, item ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("${index + 1}.", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                        Text("${item.sizeBytes / 1024} KB", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val list = mergeFiles.toMutableList()
                                                val temp = list[index]
                                                list[index] = list[index - 1]
                                                list[index - 1] = temp
                                                mergeFiles = list
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < mergeFiles.size - 1) {
                                                val list = mergeFiles.toMutableList()
                                                val temp = list[index]
                                                list[index] = list[index + 1]
                                                list[index + 1] = temp
                                                mergeFiles = list
                                            }
                                        },
                                        enabled = index < mergeFiles.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                                    }

                                    // Delete from list
                                    IconButton(
                                        onClick = {
                                            mergeFiles = mergeFiles.filterIndexed { i, _ -> i != index }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    if (mergeFiles.size < 2) {
                                        errorMessage = "Please select at least 2 PDF files to merge."
                                        return@Button
                                    }
                                    isMerging = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                mergePdfFiles(context, mergeFiles.map { it.file })
                                            }
                                        }
                                        isMerging = false
                                        result.onSuccess { mergedFile ->
                                            openedPdfFile = mergedFile
                                            statusMessage = "Merged ${mergeFiles.size} PDFs successfully into ${mergedFile.name}!"
                                            viewModel.recordLocalExport(
                                                id = mergedFile.name,
                                                fileName = mergedFile.name,
                                                mimeType = "application/pdf",
                                                localPath = mergedFile.absolutePath,
                                                sizeBytes = mergedFile.length(),
                                                pageCount = null,
                                                sourceType = "PDF_MERGE"
                                            )
                                            activeTab = 0
                                        }.onFailure {
                                            errorMessage = "Merge failed: ${it.localizedMessage}"
                                        }
                                    }
                                },
                                enabled = mergeFiles.size >= 2 && !isMerging,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (isMerging) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Merging PDFs...")
                                } else {
                                    Icon(Icons.Default.MergeType, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Merge ${mergeFiles.size} PDFs Now", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // TAB 2: PAGE MANAGER & REMOVE PAGES
                if (activeTab == 2) {
                    item {
                        SolidSectionCard {
                            Text("Remove or Rearrange Pages", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Select pages you wish to remove from the document, then tap 'Save Clean PDF'.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (openedPdfFile != null) "Choose Another PDF" else "Open PDF to Manage Pages")
                            }
                        }
                    }

                    if (openedPdfFile != null && renderedPages.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Document: ${openedPdfFile?.name} (${renderedPages.size} pages)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (pagesSelectedForRemoval.isNotEmpty()) {
                                    TextButton(onClick = { pagesSelectedForRemoval = emptySet() }) {
                                        Text("Reset")
                                    }
                                }
                            }
                        }

                        // Page thumbnails grid
                        itemsIndexed(renderedPages) { index, pageBmp ->
                            val pageNum = index + 1
                            val isMarkedForDeletion = pagesSelectedForRemoval.contains(pageNum)

                            Surface(
                                color = if (isMarkedForDeletion) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(
                                        if (isMarkedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                                    )
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp, 95.dp)
                                            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                            .background(Color.White)
                                    ) {
                                        Image(
                                            bitmap = pageBmp.asImageBitmap(),
                                            contentDescription = "Page $pageNum",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Page $pageNum",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            textDecoration = if (isMarkedForDeletion) TextDecoration.LineThrough else null,
                                            color = if (isMarkedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            if (isMarkedForDeletion) "Marked to be deleted from document" else "Included in final document",
                                            fontSize = 11.sp,
                                            color = if (isMarkedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isMarkedForDeletion) {
                                        OutlinedButton(
                                            onClick = { pagesSelectedForRemoval = pagesSelectedForRemoval - pageNum },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Text("Keep", fontSize = 11.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                pagePendingDeleteConfirmation = pageNum
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Remove", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    val src = openedPdfFile ?: return@Button
                                    if (pagesSelectedForRemoval.size >= renderedPages.size) {
                                        errorMessage = "Cannot delete all pages from the document."
                                        return@Button
                                    }
                                    isSavingCleanPdf = true
                                    errorMessage = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching {
                                                removePagesFromPdf(context, src, pagesSelectedForRemoval)
                                            }
                                        }
                                        isSavingCleanPdf = false
                                        result.onSuccess { cleanFile ->
                                            openedPdfFile = cleanFile
                                            pagesSelectedForRemoval = emptySet()
                                            statusMessage = "Created clean document: ${cleanFile.name}"
                                            viewModel.recordLocalExport(
                                                id = cleanFile.name,
                                                fileName = cleanFile.name,
                                                mimeType = "application/pdf",
                                                localPath = cleanFile.absolutePath,
                                                sizeBytes = cleanFile.length(),
                                                pageCount = renderedPages.size - pagesSelectedForRemoval.size,
                                                sourceType = "PDF_EDIT"
                                            )
                                            activeTab = 0
                                        }.onFailure {
                                            errorMessage = "Failed to export clean PDF: ${it.localizedMessage}"
                                        }
                                    }
                                },
                                enabled = pagesSelectedForRemoval.isNotEmpty() && !isSavingCleanPdf,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                if (isSavingCleanPdf) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saving Clean PDF...")
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Clean PDF (Remove ${pagesSelectedForRemoval.size} Pages)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // DOWNLOADS & SAVED FILES SECTION
                item {
                    Spacer(modifier = Modifier.height(8.dp))
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
                                    Text(doc.fileName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        "${doc.sourceType} • ${doc.fileSizeBytes / 1024} KB" +
                                            (doc.pageCount?.let { " • $it pages" } ?: ""),
                                        fontSize = 11.sp,
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
}

private fun mergePdfFiles(context: Context, files: List<File>): File {
    val outPdf = PdfDocument()
    var targetPageIndex = 1

    for (file in files) {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { page ->
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, targetPageIndex++).create()
                val newPage = outPdf.startPage(pageInfo)
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                newPage.canvas.drawBitmap(bmp, 0f, 0f, null)
                bmp.recycle()
                outPdf.finishPage(newPage)
            }
        }
        renderer.close()
        pfd.close()
    }

    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    val outFile = File(dir, "BIGsta_merged_${System.currentTimeMillis()}.pdf")
    FileOutputStream(outFile).use { outPdf.writeTo(it) }
    outPdf.close()
    return outFile
}

private fun removePagesFromPdf(context: Context, srcFile: File, pagesToRemove: Set<Int>): File {
    val outPdf = PdfDocument()
    val pfd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(pfd)
    var targetPageIndex = 1

    for (i in 0 until renderer.pageCount) {
        val pageNumber = i + 1
        if (pagesToRemove.contains(pageNumber)) {
            continue
        }
        renderer.openPage(i).use { page ->
            val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, targetPageIndex++).create()
            val newPage = outPdf.startPage(pageInfo)
            val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            newPage.canvas.drawBitmap(bmp, 0f, 0f, null)
            bmp.recycle()
            outPdf.finishPage(newPage)
        }
    }
    renderer.close()
    pfd.close()

    val dir = File(context.filesDir, "exports").apply { mkdirs() }
    val outFile = File(dir, "${srcFile.nameWithoutExtension}_edited_${System.currentTimeMillis()}.pdf")
    FileOutputStream(outFile).use { outPdf.writeTo(it) }
    outPdf.close()
    return outFile
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
