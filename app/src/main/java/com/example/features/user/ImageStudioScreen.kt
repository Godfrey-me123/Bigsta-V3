package com.example.features.user

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.SolidSectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PassportDocSpec(
    val country: String,
    val document: String,
    val widthMm: Int,
    val heightMm: Int,
    val minWidth: Int,
    val minHeight: Int,
    val maxKb: Int,
    val defaultBgName: String = "White"
)

data class PaperSizeSpec(
    val name: String,
    val widthMm: Float,
    val heightMm: Float,
    val defaultCols: Int,
    val maxCopies: Int
)

data class ColorOption(
    val name: String,
    val colorInt: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Single Photo & Retouch, 1 = Multi-Photo Print Sheet
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var jpegQuality by remember { mutableFloatStateOf(90f) }
    var watermarkText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }

    val passportSpecs = remember {
        listOf(
            PassportDocSpec("Tanzania", "Passport & Visa", 35, 45, 413, 531, 200, "White"),
            PassportDocSpec("United Kingdom", "Passport", 35, 45, 413, 531, 1024, "Light Grey"),
            PassportDocSpec("United States", "Visa / Passport", 51, 51, 600, 600, 240, "White"),
            PassportDocSpec("Schengen Area", "Visa", 35, 45, 413, 531, 500, "White"),
            PassportDocSpec("Kenya / EA", "East Africa Passport", 35, 45, 413, 531, 300, "White"),
            PassportDocSpec("India", "Passport / OCI", 35, 45, 413, 531, 300, "White"),
            PassportDocSpec("Canada", "Passport / Visa", 50, 70, 590, 826, 400, "White"),
            PassportDocSpec("Custom Size", "Specified Dimensions", 35, 45, 400, 500, 500, "White")
        )
    }
    var selectedSpecIndex by remember { mutableIntStateOf(0) }

    val paperSizes = remember {
        listOf(
            PaperSizeSpec("3R (3.5x5 in)", 89f, 127f, 3, 12),
            PaperSizeSpec("4R (4x6 in)", 102f, 152f, 4, 16),
            PaperSizeSpec("5R (5x7 in)", 127f, 178f, 4, 20),
            PaperSizeSpec("6R (6x8 in)", 152f, 203f, 5, 30),
            PaperSizeSpec("8R (8x10 in)", 203f, 254f, 5, 35),
            PaperSizeSpec("A4 Paper (8.27x11.69 in)", 210f, 297f, 6, 42),
            PaperSizeSpec("S8R (8x12 in)", 203f, 305f, 6, 48),
            PaperSizeSpec("10R (10x12 in)", 254f, 305f, 6, 48)
        )
    }
    var selectedPaperIndex by remember { mutableIntStateOf(5) } // Default A4
    var requestedCopies by remember { mutableIntStateOf(12) }
    var isLandscape by remember { mutableStateOf(false) }
    var spacingMm by remember { mutableFloatStateOf(2f) }
    var showCuttingGuides by remember { mutableStateOf(true) }

    val backgroundColors = remember {
        listOf(
            ColorOption("White", android.graphics.Color.WHITE),
            ColorOption("Light Grey", android.graphics.Color.rgb(241, 245, 249)),
            ColorOption("Sky Blue", android.graphics.Color.rgb(56, 189, 248)),
            ColorOption("Royal Blue", android.graphics.Color.rgb(37, 99, 235)),
            ColorOption("Navy Blue", android.graphics.Color.rgb(30, 58, 138)),
            ColorOption("Crimson Red", android.graphics.Color.rgb(220, 38, 38)),
            ColorOption("Emerald Green", android.graphics.Color.rgb(5, 150, 105)),
            ColorOption("Soft Pink", android.graphics.Color.rgb(219, 39, 119))
        )
    }
    var selectedBgColor by remember { mutableStateOf(backgroundColors[0].colorInt) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    activeBitmap = BitmapFactory.decodeStream(stream)
                    statusText = activeBitmap?.let { "Loaded ${it.width}×${it.height} px" }
                    lastSavedFile = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Passport Size Photo Studio") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lastSavedFile != null) {
                        IconButton(
                            onClick = {
                                val file = lastSavedFile ?: return@IconButton
                                val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
                                val mime = if (isPdf) "application/pdf" else "image/jpeg"
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share Exported File"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share file")
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
                    text = { Text("Passport Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.PhotoFilter, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Print Sheet (${paperSizes[selectedPaperIndex].name.substringBefore(" ")})", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Photo Picker Button
                Button(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("image_studio_pick_btn")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (activeBitmap != null) "Choose Different Photo" else "Select Photo from Gallery")
                }

                // Photo Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = activeBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Passport photo preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(Icons.Default.PhotoFilter, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Select a portrait photo to crop, adjust background color, and layout on printable sheets.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!statusText.isNullOrBlank()) {
                    Text(statusText.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                }

                // TAB 0: PASSPORT PHOTO & RETOUCH
                if (activeTab == 0) {
                    SolidSectionCard {
                        Text("1. Document Specification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val currentSpec = passportSpecs[selectedSpecIndex]
                        passportSpecs.chunked(2).forEach { rowSpecs ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowSpecs.forEach { spec ->
                                    val idx = passportSpecs.indexOf(spec)
                                    FilterChip(
                                        selected = selectedSpecIndex == idx,
                                        onClick = { selectedSpecIndex = idx },
                                        label = { Text("${spec.country} (${spec.widthMm}×${spec.heightMm}mm)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${currentSpec.document} • 300 DPI • minimum ${currentSpec.minWidth}×${currentSpec.minHeight}px • max ${currentSpec.maxKb} KB",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SolidSectionCard {
                        Text("2. Background Color", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            backgroundColors.forEach { bg ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(bg.colorInt))
                                        .border(
                                            width = if (selectedBgColor == bg.colorInt) 2.5.dp else 1.dp,
                                            color = if (selectedBgColor == bg.colorInt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedBgColor = bg.colorInt },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedBgColor == bg.colorInt) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (bg.colorInt == android.graphics.Color.WHITE || bg.colorInt == android.graphics.Color.rgb(241, 245, 249)) Color.Black else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SolidSectionCard {
                        Text("3. Crop & Retouch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val bmp = activeBitmap ?: return@OutlinedButton
                                    val matrix = Matrix().apply { postRotate(90f) }
                                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                    activeBitmap = rotated
                                    statusText = "Rotated 90° (${rotated.width}×${rotated.height} px)"
                                },
                                enabled = activeBitmap != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rotate 90°", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val bmp = activeBitmap ?: return@OutlinedButton
                                    val matrix = Matrix().apply { preScale(-1f, 1f) }
                                    val flipped = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                                    activeBitmap = flipped
                                    statusText = "Flipped horizontally"
                                },
                                enabled = activeBitmap != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Flip", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val bmp = activeBitmap ?: return@Button
                                    val spec = passportSpecs[selectedSpecIndex]
                                    val targetRatio = spec.widthMm.toFloat() / spec.heightMm.toFloat()
                                    val currentRatio = bmp.width.toFloat() / bmp.height.toFloat()
                                    val cropW: Int
                                    val cropH: Int
                                    if (currentRatio > targetRatio) {
                                        cropH = bmp.height
                                        cropW = (bmp.height * targetRatio).toInt()
                                    } else {
                                        cropW = bmp.width
                                        cropH = (bmp.width / targetRatio).toInt()
                                    }
                                    val x = ((bmp.width - cropW) / 2).coerceAtLeast(0)
                                    val y = ((bmp.height - cropH) / 2).coerceAtLeast(0)
                                    val cropped = Bitmap.createBitmap(bmp, x, y, cropW.coerceAtLeast(1), cropH.coerceAtLeast(1))
                                    activeBitmap = cropped
                                    statusText = "Cropped to ${spec.country} ${spec.widthMm}×${spec.heightMm}mm ratio"
                                },
                                enabled = activeBitmap != null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Crop", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            label = { Text("Optional Watermark / Name Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Export Quality: ${jpegQuality.toInt()}%", fontSize = 12.sp)
                        Slider(
                            value = jpegQuality,
                            onValueChange = { jpegQuality = it },
                            valueRange = 30f..100f
                        )
                    }

                    // Export Single Photo Button
                    Button(
                        onClick = {
                            val source = activeBitmap ?: return@Button
                            val spec = passportSpecs[selectedSpecIndex]
                            val outputBmp = source.copy(Bitmap.Config.ARGB_8888, true)

                            // Apply Background color if aspect ratio left margin
                            val canvas = Canvas(outputBmp)
                            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = selectedBgColor }
                            val chosenRatio = spec.widthMm.toFloat() / spec.heightMm.toFloat()
                            val targetW = outputBmp.width.toFloat()
                            val targetH = targetW / chosenRatio
                            if (targetH < outputBmp.height) {
                                val y = (outputBmp.height - targetH) / 2f
                                canvas.drawRect(0f, 0f, outputBmp.width.toFloat(), y, bgPaint)
                                canvas.drawRect(0f, y + targetH, outputBmp.width.toFloat(), outputBmp.height.toFloat(), bgPaint)
                            }

                            if (watermarkText.isNotBlank()) {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.argb(180, 255, 255, 255)
                                    textSize = (outputBmp.width * 0.055f).coerceAtLeast(20f)
                                    isFakeBoldText = true
                                }
                                canvas.drawText(watermarkText, 20f, (outputBmp.height - 24).toFloat(), paint)
                            }

                            val baos = ByteArrayOutputStream()
                            outputBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.toInt(), baos)
                            val bytes = baos.toByteArray()
                            val dir = File(context.filesDir, "exports").apply { mkdirs() }
                            val file = File(dir, "passport_${spec.country.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.jpg")
                            FileOutputStream(file).use { it.write(bytes) }
                            lastSavedFile = file
                            statusText = "Saved ${file.name} (${bytes.size / 1024} KB)"

                            viewModel.recordLocalExport(
                                id = file.name,
                                fileName = file.name,
                                mimeType = "image/jpeg",
                                localPath = file.absolutePath,
                                sizeBytes = file.length(),
                                pageCount = 1,
                                sourceType = "PASSPORT_PHOTO"
                            )
                        },
                        enabled = activeBitmap != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("image_studio_save_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Passport Photo to Downloads", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { activeTab = 1 },
                        enabled = activeBitmap != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Print Sheet Grid →")
                    }
                }

                // TAB 1: PRINT SHEET & PAPER GRID
                if (activeTab == 1) {
                    val currentPaper = paperSizes[selectedPaperIndex]
                    val currentSpec = passportSpecs[selectedSpecIndex]

                    SolidSectionCard {
                        Text("1. Print Paper Size", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        paperSizes.chunked(2).forEach { rowPapers ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPapers.forEach { p ->
                                    val idx = paperSizes.indexOf(p)
                                    FilterChip(
                                        selected = selectedPaperIndex == idx,
                                        onClick = {
                                            selectedPaperIndex = idx
                                            requestedCopies = p.maxCopies.coerceAtMost(requestedCopies)
                                        },
                                        label = { Text(p.name, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    SolidSectionCard {
                        Text("2. Copies & Grid Layout", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Copies on Sheet:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { requestedCopies = (requestedCopies - 2).coerceAtLeast(2) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease copies")
                                }
                                Text("$requestedCopies", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                IconButton(onClick = { requestedCopies = (requestedCopies + 2).coerceAtMost(currentPaper.maxCopies) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase copies")
                                }
                            }
                        }

                        // Quick Copies Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(4, 6, 8, 12, 16, 24, 32, 48).filter { it <= currentPaper.maxCopies }.forEach { num ->
                                FilterChip(
                                    selected = requestedCopies == num,
                                    onClick = { requestedCopies = num },
                                    label = { Text("$num", fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = isLandscape,
                                onClick = { isLandscape = !isLandscape },
                                label = { Text(if (isLandscape) "Orientation: Landscape" else "Orientation: Portrait") },
                                modifier = Modifier.weight(1f)
                            )

                            FilterChip(
                                selected = showCuttingGuides,
                                onClick = { showCuttingGuides = !showCuttingGuides },
                                label = { Text(if (showCuttingGuides) "Cutting Lines: ON" else "Cutting Lines: OFF") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Spacing between photos: ${spacingMm.toInt()} mm", fontSize = 12.sp)
                        Slider(
                            value = spacingMm,
                            onValueChange = { spacingMm = it },
                            valueRange = 0f..8f,
                            steps = 3
                        )
                    }

                    // Direct Print Sheet Button
                    Button(
                        onClick = {
                            val source = activeBitmap ?: return@Button
                            scope.launch {
                                val pdfFile = withContext(Dispatchers.IO) {
                                    createPrintSheetPdf(
                                        context = context,
                                        photoBitmap = source,
                                        spec = currentSpec,
                                        paper = currentPaper,
                                        copies = requestedCopies,
                                        isLandscape = isLandscape,
                                        spacingMm = spacingMm,
                                        showGuides = showCuttingGuides,
                                        bgColor = selectedBgColor
                                    )
                                }
                                lastSavedFile = pdfFile
                                statusText = "Ready to print: ${pdfFile.name}"
                                viewModel.recordLocalExport(
                                    id = pdfFile.name,
                                    fileName = pdfFile.name,
                                    mimeType = "application/pdf",
                                    localPath = pdfFile.absolutePath,
                                    sizeBytes = pdfFile.length(),
                                    pageCount = 1,
                                    sourceType = "PASSPORT_PRINT_SHEET"
                                )
                                printPdfFile(context, pdfFile)
                            }
                        },
                        enabled = activeBitmap != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("passport_print_sheet_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print Sheet (${requestedCopies} Photos)", fontWeight = FontWeight.Bold)
                    }

                    // Save Sheet to Storage PDF
                    OutlinedButton(
                        onClick = {
                            val source = activeBitmap ?: return@OutlinedButton
                            scope.launch {
                                val pdfFile = withContext(Dispatchers.IO) {
                                    createPrintSheetPdf(
                                        context = context,
                                        photoBitmap = source,
                                        spec = currentSpec,
                                        paper = currentPaper,
                                        copies = requestedCopies,
                                        isLandscape = isLandscape,
                                        spacingMm = spacingMm,
                                        showGuides = showCuttingGuides,
                                        bgColor = selectedBgColor
                                    )
                                }
                                lastSavedFile = pdfFile
                                statusText = "Saved printable sheet to Downloads: ${pdfFile.name}"
                                viewModel.recordLocalExport(
                                    id = pdfFile.name,
                                    fileName = pdfFile.name,
                                    mimeType = "application/pdf",
                                    localPath = pdfFile.absolutePath,
                                    sizeBytes = pdfFile.length(),
                                    pageCount = 1,
                                    sourceType = "PASSPORT_PRINT_SHEET"
                                )
                            }
                        },
                        enabled = activeBitmap != null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export High-Res Printable PDF")
                    }
                }
            }
        }
    }
}

private fun createPrintSheetPdf(
    context: Context,
    photoBitmap: Bitmap,
    spec: PassportDocSpec,
    paper: PaperSizeSpec,
    copies: Int,
    isLandscape: Boolean,
    spacingMm: Float,
    showGuides: Boolean,
    bgColor: Int
): File {
    val pdf = android.graphics.pdf.PdfDocument()

    // 72 points per inch; 1 mm = 2.83465 pt
    val mmToPt = 2.83465f
    val rawW = paper.widthMm * mmToPt
    val rawH = paper.heightMm * mmToPt
    val pageW = (if (isLandscape) maxOf(rawW, rawH) else minOf(rawW, rawH)).toInt()
    val pageH = (if (isLandscape) minOf(rawW, rawH) else maxOf(rawW, rawH)).toInt()

    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
    val page = pdf.startPage(pageInfo)
    val canvas = page.canvas

    // Clean white page background
    canvas.drawColor(android.graphics.Color.WHITE)

    val photoW = spec.widthMm * mmToPt
    val photoH = spec.heightMm * mmToPt
    val gap = spacingMm * mmToPt

    // Calculate grid columns and rows that fit on page
    val usableW = pageW - (20f * mmToPt)
    val usableH = pageH - (20f * mmToPt)
    val maxCols = maxOf(1, ((usableW + gap) / (photoW + gap)).toInt())
    val maxRows = maxOf(1, ((usableH + gap) / (photoH + gap)).toInt())

    val totalToDraw = copies.coerceAtMost(maxCols * maxRows)
    val actualCols = minOf(maxCols, totalToDraw)
    val actualRows = (totalToDraw + actualCols - 1) / actualCols

    val gridW = actualCols * photoW + (actualCols - 1) * gap
    val gridH = actualRows * photoH + (actualRows - 1) * gap
    val startX = (pageW - gridW) / 2f
    val startY = (pageH - gridH) / 2f

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
    val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 0.75f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
    }

    var drawn = 0
    for (r in 0 until actualRows) {
        for (c in 0 until actualCols) {
            if (drawn >= totalToDraw) break
            val left = startX + c * (photoW + gap)
            val top = startY + r * (photoH + gap)
            val rect = RectF(left, top, left + photoW, top + photoH)

            // Draw photo background
            canvas.drawRect(rect, bgPaint)
            // Draw photo
            canvas.drawBitmap(photoBitmap, null, rect, null)

            // Draw cutting lines around photos
            if (showGuides) {
                canvas.drawRect(rect, guidePaint)
            }
            drawn++
        }
    }

    // Bottom caption
    val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textSize = 9f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText(
        "BIGsta Passport Studio • ${spec.country} (${spec.widthMm}×${spec.heightMm}mm) • ${paper.name} • 300 DPI High-Resolution",
        pageW / 2f,
        pageH - 12f,
        captionPaint
    )

    pdf.finishPage(page)
    val outDir = File(context.filesDir, "exports").apply { mkdirs() }
    val file = File(outDir, "passport_sheet_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { pdf.writeTo(it) }
    pdf.close()
    return file
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
