package com.example.features.user

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxToPdfScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloads by viewModel.localDownloads.collectAsStateWithLifecycle()

    var sourceName by remember { mutableStateOf("") }
    var documentText by remember { mutableStateOf("") }
    var conversionStatus by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var outputPdfFile by remember { mutableStateOf<File?>(null) }
    var generatedPageCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isConverting = true
            outputPdfFile = null
            errorMessage = null
            conversionStatus = "Reading document file..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error("Could not read file from storage")
                        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
                        val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "document.$ext"
                        val parsedText = readSupportedDocument(bytes, ext)
                        filename to parsedText
                    }
                }
                isConverting = false
                result.onSuccess { (name, text) ->
                    sourceName = name
                    documentText = text
                    conversionStatus = "Ready to convert ($name • ${text.length} characters)"
                }.onFailure {
                    errorMessage = it.localizedMessage ?: "This document could not be opened."
                    conversionStatus = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word / Document to PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SolidSectionCard {
                Text("Offline Document to PDF Converter", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Open DOCX, TXT, RTF, or Markdown files directly from your phone. Converted to a clean, multi-page A4 PDF completely offline without uploading to any server.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        docPicker.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "text/plain",
                                "text/markdown",
                                "application/rtf",
                                "text/rtf",
                                "application/msword"
                            )
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("docx_to_pdf_open_btn")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (sourceName.isNotBlank()) "Choose Different Document" else "Open Document File")
                }
            }

            // Error message if any
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Document Details Card
            if (sourceName.isNotBlank() && documentText.isNotBlank()) {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(sourceName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${documentText.length} characters • ${documentText.lines().size} lines",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Document Preview (First paragraphs):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            documentText.take(1500) + if (documentText.length > 1500) "..." else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (!conversionStatus.isNullOrBlank()) {
                    Text(conversionStatus.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }

                if (isConverting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Convert Button
                Button(
                    enabled = documentText.isNotBlank() && !isConverting,
                    onClick = {
                        isConverting = true
                        conversionStatus = "Formatting A4 pages & typography..."
                        scope.launch {
                            val made = withContext(Dispatchers.IO) {
                                runCatching {
                                    createFormattedA4Pdf(context.filesDir, sourceName, documentText)
                                }
                            }
                            isConverting = false
                            made.onSuccess { (file, pageCount) ->
                                outputPdfFile = file
                                generatedPageCount = pageCount
                                conversionStatus = "Successfully converted to ${file.name} ($pageCount pages)"
                                viewModel.recordLocalExport(
                                    id = file.name,
                                    fileName = file.name,
                                    mimeType = "application/pdf",
                                    localPath = file.absolutePath,
                                    sizeBytes = file.length(),
                                    pageCount = pageCount,
                                    sourceType = "DOCX_TO_PDF"
                                )
                            }.onFailure {
                                errorMessage = it.localizedMessage ?: "PDF creation failed."
                                conversionStatus = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("docx_to_pdf_convert_btn")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Convert to A4 PDF", fontWeight = FontWeight.Bold)
                }
            }

            // Output Actions (Print, Share, Open)
            outputPdfFile?.let { file ->
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conversion Complete!", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${file.name} • ${file.length() / 1024} KB • $generatedPageCount pages", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Print
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { printPdfFile(context, file) }
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print", fontSize = 12.sp)
                        }

                        // Open in viewer
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching {
                                    context.startActivity(intent)
                                }.onFailure {
                                    errorMessage = "No external PDF viewer installed. You can print or share the document."
                                }
                            }
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open", fontSize = 12.sp)
                        }

                        // Share
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share PDF Document"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Recent Converted Documents
            val recentConversions = downloads.filter { it.sourceType == "DOCX_TO_PDF" }
            if (recentConversions.isNotEmpty()) {
                Text("Recent Converted Documents (${recentConversions.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                recentConversions.take(5).forEach { rec ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.fileName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text("${rec.fileSizeBytes / 1024} KB" + (rec.pageCount?.let { " • $it pages" } ?: ""), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = {
                                    val f = File(rec.localFilePath)
                                    if (f.exists()) printPdfFile(context, f)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    val f = File(rec.localFilePath)
                                    if (f.exists()) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share PDF"))
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun readSupportedDocument(bytes: ByteArray, extension: String): String {
    val text = when (extension) {
        "docx" -> {
            val temp = File.createTempFile("bigsta_doc_", ".docx")
            try {
                temp.writeBytes(bytes)
                ZipFile(temp).use { zip ->
                    val entry = zip.getEntry("word/document.xml")
                        ?: error("DOCX document.xml entry missing inside archive")
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(zip.getInputStream(entry), "UTF-8")
                    val out = StringBuilder()
                    var event = parser.eventType
                    var inParagraph = false

                    while (event != XmlPullParser.END_DOCUMENT) {
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                if (parser.name == "p") {
                                    inParagraph = true
                                } else if (parser.name == "t") {
                                    out.append(parser.nextText())
                                } else if (parser.name == "tab") {
                                    out.append("    ")
                                } else if (parser.name == "br") {
                                    out.append("\n")
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (parser.name == "p") {
                                    out.append("\n\n")
                                    inParagraph = false
                                }
                            }
                        }
                        event = parser.next()
                    }
                    out.toString()
                }
            } finally {
                temp.delete()
            }
        }
        "txt", "md", "markdown" -> bytes.toString(Charsets.UTF_8)
        "rtf" -> {
            bytes.toString(Charsets.UTF_8)
                .replace(Regex("\\\\par[d]? ?"), "\n")
                .replace(Regex("\\\\tab ?"), "    ")
                .replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ")
                .replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "")
                .replace(Regex("[{}]"), "")
        }
        "doc" -> {
            // Binary doc fallback: extract printable character runs
            val raw = bytes.toString(Charsets.ISO_8859_1)
            val clean = Regex("[a-zA-Z0-9.,;:!?'\"()\\-–—\\n\\r/\\\\@#\$%&*+=\\[\\]{}<> ]{6,}").findAll(raw)
                .map { it.value.trim() }
                .filter { it.length > 8 }
                .joinToString("\n\n")
            if (clean.length > 50) clean
            else error("Legacy .doc binary format cannot be parsed offline. Please save the file as .docx or .rtf and reopen it.")
        }
        else -> error("Unsupported file format: .$extension. Choose a DOCX, TXT, RTF, or Markdown document.")
    }.trim()

    if (text.isBlank()) error("The document contains no readable text.")
    return text
}

private fun createFormattedA4Pdf(dir: File, title: String, text: String): Pair<File, Int> {
    val pdf = PdfDocument()
    // Standard A4: 595 x 842 points
    val pageW = 595
    val pageH = 842
    val left = 50f
    val right = 50f
    val top = 56f
    val bottom = 50f
    val maxTextWidth = pageW - left - right
    val usableHeight = pageH - top - bottom

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10.5f
        color = android.graphics.Color.rgb(30, 41, 59)
    }
    val h1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 15f
        color = android.graphics.Color.rgb(15, 23, 42)
        isFakeBoldText = true
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 17f
        color = android.graphics.Color.rgb(30, 58, 138)
        isFakeBoldText = true
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f
        color = android.graphics.Color.GRAY
    }

    // Split paragraphs and wrap lines
    data class FormattedLine(val text: String, val isHeading: Boolean, val isBlank: Boolean)
    val formattedLines = mutableListOf<FormattedLine>()

    text.lines().forEach { rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.isBlank()) {
            formattedLines.add(FormattedLine("", isHeading = false, isBlank = true))
            return@forEach
        }

        val isHeading = trimmed.startsWith("#") || (trimmed.length < 50 && trimmed == trimmed.uppercase(Locale.getDefault()) && trimmed.any { it.isLetter() })
        val cleanText = if (trimmed.startsWith("#")) trimmed.trimStart('#').trim() else trimmed
        val paint = if (isHeading) h1Paint else bodyPaint

        var currentLine = ""
        cleanText.split(Regex("\\s+")).forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) > maxTextWidth && currentLine.isNotEmpty()) {
                formattedLines.add(FormattedLine(currentLine, isHeading, false))
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            formattedLines.add(FormattedLine(currentLine, isHeading, false))
        }
    }

    val lineHeight = 14f
    val linesPerPage = (usableHeight / lineHeight).toInt()
    val totalPages = maxOf(1, (formattedLines.size + linesPerPage - 1) / linesPerPage)
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())

    repeat(totalPages) { pageIdx ->
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIdx + 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        // First page title header
        var startY = top
        if (pageIdx == 0) {
            canvas.drawText(title, left, top + 16f, titlePaint)
            // Accent bar
            val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(37, 99, 235) }
            canvas.drawRect(left, top + 24f, left + 40f, top + 27f, barPaint)
            startY = top + 42f
        }

        // Draw body lines
        val pageLines = formattedLines.drop(pageIdx * linesPerPage).take(linesPerPage)
        pageLines.forEachIndexed { lineIdx, fl ->
            if (!fl.isBlank) {
                val paint = if (fl.isHeading) h1Paint else bodyPaint
                canvas.drawText(fl.text, left, startY + (lineIdx * lineHeight), paint)
            }
        }

        // Footer
        canvas.drawText(
            "BIGsta Document Studio • $dateFormat",
            left,
            pageH - 24f,
            footerPaint
        )
        canvas.drawText(
            "Page ${pageIdx + 1} of $totalPages",
            pageW - right - 70f,
            pageH - 24f,
            footerPaint
        )

        pdf.finishPage(page)
    }

    val cleanName = title.substringBeforeLast('.', title).replace(Regex("[^A-Za-z0-9_-]"), "_")
    val outDir = File(dir, "exports").apply { mkdirs() }
    val outFile = File(outDir, "${cleanName}_${System.currentTimeMillis()}.pdf")
    FileOutputStream(outFile).use { pdf.writeTo(it) }
    pdf.close()
    return outFile to totalPages
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
