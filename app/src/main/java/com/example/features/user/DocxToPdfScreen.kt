package com.example.features.user

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocxToPdfScreen(viewModel: UserDashboardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceName by remember { mutableStateOf("") }
    var documentText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf<File?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true; output = null
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { readSupportedDocument(context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read file"), uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()) } }
                result.onSuccess { (name, text) -> sourceName = name; documentText = text; status = "Ready to create a local A4 PDF. The document stays on this device." }
                    .onFailure { status = it.localizedMessage ?: "This document could not be opened." }
                busy = false
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Word / Document to PDF") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SolidSectionCard {
                Text("Offline document conversion", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Open DOCX, TXT, RTF, or Markdown from this device and save as an A4 PDF. No document is uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown", "application/rtf", "text/rtf", "application/msword")) }, modifier = Modifier.fillMaxWidth().testTag("docx_to_pdf_open_btn")) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Open Document")
                }
            }
            if (sourceName.isNotBlank()) {
                SolidSectionCard {
                    Text(sourceName, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp)); Text("${documentText.length} characters", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp)); Text(documentText.take(3000), style = MaterialTheme.typography.bodySmall)
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Button(enabled = documentText.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val made = withContext(Dispatchers.IO) { runCatching { createA4Pdf(context.filesDir, sourceName, documentText) } }
                    made.onSuccess { file -> output = file; status = "Created ${file.name}"; viewModel.recordLocalExport(file.name, file.name, "application/pdf", file.absolutePath, file.length(), null, "DOCX_TO_PDF") }
                        .onFailure { status = it.localizedMessage ?: "PDF creation failed." }
                    busy = false
                }
            }, modifier = Modifier.fillMaxWidth().testTag("docx_to_pdf_convert_btn")) {
                Icon(Icons.Default.Description, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Convert to PDF")
            }
            output?.let { file ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        context.startActivity(Intent.createChooser(intent, "Share PDF"))
                    }) { Icon(Icons.Default.Download, null); Text("Share") }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), "application/pdf"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        runCatching { context.startActivity(intent) }.onFailure { status = "No PDF viewer is installed." }
                    }) { Icon(Icons.Default.Print, null); Text("Open / Print") }
                }
            }
        }
    }
}

private fun readSupportedDocument(bytes: ByteArray, extension: String): Pair<String, String> {
    val text = when (extension) {
        "docx" -> {
            val temp = File.createTempFile("bigsta_doc_", ".docx")
            try {
                temp.writeBytes(bytes)
                ZipFile(temp).use { zip ->
                    val entry = zip.getEntry("word/document.xml") ?: error("DOCX document content is missing")
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(zip.getInputStream(entry), "UTF-8")
                    val out = StringBuilder(); var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        if (event == XmlPullParser.START_TAG && parser.name == "t") out.append(parser.nextText())
                        else if (event == XmlPullParser.END_TAG && parser.name == "p") out.append("\n")
                        event = parser.next()
                    }
                    out.toString()
                }
            } finally { temp.delete() }
        }
        "txt", "md", "markdown" -> bytes.toString(Charsets.UTF_8)
        "rtf" -> bytes.toString(Charsets.UTF_8).replace(Regex("\\\\par[d]? ?"), "\n").replace(Regex("\\\\'[0-9a-fA-F]{2}"), " ").replace(Regex("\\\\[a-zA-Z]+-?\\d* ?"), "").replace(Regex("[{}]"), "")
        "doc" -> error("Legacy .doc files are not supported by the offline Android converter. Save as .docx, RTF, or TXT and reopen it.")
        else -> error("Choose a DOCX, TXT, RTF, or Markdown file.")
    }.trim()
    if (text.isBlank()) error("The selected document has no readable text.")
    return "document.$extension" to text
}

private fun createA4Pdf(dir: File, title: String, text: String): File {
    val pdf = PdfDocument()
    val pageW = 595; val pageH = 842; val left = 48f; val right = 48f; val top = 58f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = android.graphics.Color.BLACK }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; color = android.graphics.Color.BLACK; isFakeBoldText = true }
    val maxWidth = pageW - left - right
    val lines = mutableListOf<String>()
    text.lines().forEach { para ->
        if (para.isBlank()) { lines.add(""); return@forEach }
        var current = ""
        para.split(Regex("\\s+")).forEach { word ->
            val next = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(next) > maxWidth && current.isNotEmpty()) { lines.add(current); current = word } else current = next
        }
        if (current.isNotEmpty()) lines.add(current)
    }
    val linesPerPage = 54
    val totalPages = maxOf(1, (lines.size + linesPerPage - 1) / linesPerPage)
    repeat(totalPages) { index ->
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create())
        val canvas = page.canvas
        if (index == 0) canvas.drawText(title, left, top, titlePaint)
        val startY = if (index == 0) top + 28f else top
        lines.drop(index * linesPerPage).take(linesPerPage).forEachIndexed { lineIndex, line -> canvas.drawText(line, left, startY + lineIndex * 13.5f, paint) }
        canvas.drawText("${index + 1} / $totalPages", pageW - 80f, pageH - 26f, paint)
        pdf.finishPage(page)
    }
    val outDir = File(dir, "exports").apply { mkdirs() }
    val file = File(outDir, "${title.substringBeforeLast('.', title).replace(Regex("[^A-Za-z0-9_-]"), "_")}_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { pdf.writeTo(it) }
    pdf.close()
    return file
}
