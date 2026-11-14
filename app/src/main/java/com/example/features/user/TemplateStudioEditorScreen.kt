package com.example.features.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.SolidSectionCard
import java.io.File
import java.io.FileOutputStream

enum class StudioLayerType(val label: String) {
    TEXT("Text Field"),
    PHOTO("Photo Box"),
    BARCODE("Barcode (Code128)"),
    QRCODE("QR Code 2D"),
    SIGNATURE("Signature Box"),
    BADGE("Security Seal")
}

data class StudioLayer(
    val id: String,
    val name: String,
    val type: StudioLayerType,
    val bindingToken: String = "",
    val textValue: String = "",
    val xPercent: Float = 10f,
    val yPercent: Float = 20f,
    val widthPercent: Float = 80f,
    val heightPercent: Float = 10f,
    val fontSize: Float = 24f,
    val isBold: Boolean = false,
    val colorHex: String = "#18181B",
    val alignment: String = "LEFT",
    val isVisible: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateStudioEditorScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val categories = listOf("NIDA", "Driving Licence", "NHIF", "NRC Zambia", "Student ID", "Employee Badge")
    val bindingTokens = listOf(
        "{{full_name}}",
        "{{first_name}}",
        "{{last_name}}",
        "{{nida_number}}",
        "{{id_number}}",
        "{{dob}}",
        "{{gender}}",
        "{{expiry_date}}",
        "{{blood_group}}",
        "{{licence_classes}}",
        "{{authority}}"
    )
    val colorPresets = listOf("#18181B", "#1E3A8A", "#14532D", "#7F1D1D", "#854D0E", "#0284C7")

    var templateName by remember { mutableStateOf("Tanzania National ID Template") }
    var selectedCategory by remember { mutableStateOf("NIDA") }
    var selectedSide by remember { mutableStateOf("front") }
    var orientation by remember { mutableStateOf("landscape") }
    var primaryColorHex by remember { mutableStateOf("#18181B") }
    var cardBgHex by remember { mutableStateOf("#FFFFFF") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var badgePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

    val defaultFrontLayers = remember {
        listOf(
            StudioLayer(
                id = "header_banner",
                name = "Agency Header Banner",
                type = StudioLayerType.TEXT,
                textValue = "JAMHURI YA MUUNGANO WA TANZANIA",
                xPercent = 5f,
                yPercent = 6f,
                fontSize = 24f,
                isBold = true,
                colorHex = "#FFFFFF"
            ),
            StudioLayer(
                id = "doc_title",
                name = "Document Title",
                type = StudioLayerType.TEXT,
                textValue = "NATIONAL IDENTIFICATION CARD",
                xPercent = 5f,
                yPercent = 12f,
                fontSize = 18f,
                isBold = false,
                colorHex = "#E4E4E7"
            ),
            StudioLayer(
                id = "photo_box",
                name = "Portrait Photo Placeholder",
                type = StudioLayerType.PHOTO,
                bindingToken = "{{photo}}",
                xPercent = 5f,
                yPercent = 24f,
                widthPercent = 25f,
                heightPercent = 56f
            ),
            StudioLayer(
                id = "f_name",
                name = "Full Name Field",
                type = StudioLayerType.TEXT,
                bindingToken = "{{full_name}}",
                textValue = "AMINA JUMA HASSAN",
                xPercent = 34f,
                yPercent = 26f,
                fontSize = 32f,
                isBold = true,
                colorHex = "#18181B"
            ),
            StudioLayer(
                id = "f_id",
                name = "National ID Number",
                type = StudioLayerType.TEXT,
                bindingToken = "{{id_number}}",
                textValue = "19950612-14102-00001-22",
                xPercent = 34f,
                yPercent = 42f,
                fontSize = 28f,
                isBold = true,
                colorHex = "#0284C7"
            ),
            StudioLayer(
                id = "f_dob",
                name = "Date of Birth",
                type = StudioLayerType.TEXT,
                bindingToken = "{{dob}}",
                textValue = "DOB: 12/06/1995 • GENDER: F",
                xPercent = 34f,
                yPercent = 58f,
                fontSize = 22f,
                isBold = false,
                colorHex = "#3F3F46"
            ),
            StudioLayer(
                id = "f_barcode",
                name = "Card Barcode (Code128)",
                type = StudioLayerType.BARCODE,
                bindingToken = "{{id_number}}",
                textValue = "19950612141020000122",
                xPercent = 34f,
                yPercent = 74f,
                widthPercent = 60f,
                heightPercent = 14f
            )
        )
    }

    val defaultBackLayers = remember {
        listOf(
            StudioLayer(
                id = "b_header",
                name = "Issuing Authority Header",
                type = StudioLayerType.TEXT,
                textValue = "NATIONAL IDENTIFICATION AUTHORITY - NIDA",
                xPercent = 5f,
                yPercent = 8f,
                fontSize = 22f,
                isBold = true,
                colorHex = "#18181B"
            ),
            StudioLayer(
                id = "b_notice",
                name = "Cardholder Notice",
                type = StudioLayerType.TEXT,
                textValue = "Kitambulisho hiki ni mali ya Serikali. Kikionekana kirudishwe kituo chochote cha polisi.",
                xPercent = 5f,
                yPercent = 22f,
                fontSize = 17f,
                isBold = false,
                colorHex = "#52525B"
            ),
            StudioLayer(
                id = "b_ref",
                name = "Document Reference",
                type = StudioLayerType.TEXT,
                bindingToken = "{{id_number}}",
                textValue = "REF: TZ-NIDA-CARD-2026",
                xPercent = 5f,
                yPercent = 45f,
                fontSize = 20f,
                isBold = true,
                colorHex = "#18181B"
            ),
            StudioLayer(
                id = "b_sig",
                name = "Holder Signature Box",
                type = StudioLayerType.SIGNATURE,
                bindingToken = "{{signature}}",
                textValue = "Authorized Signature",
                xPercent = 5f,
                yPercent = 60f,
                widthPercent = 40f,
                heightPercent = 24f
            ),
            StudioLayer(
                id = "b_qr",
                name = "2D Verification QR Code",
                type = StudioLayerType.QRCODE,
                bindingToken = "{{id_number}}",
                textValue = "https://nida.go.tz/verify/1995061214102",
                xPercent = 68f,
                yPercent = 48f,
                widthPercent = 26f,
                heightPercent = 38f
            )
        )
    }

    var frontLayers by remember { mutableStateOf(defaultFrontLayers) }
    var backLayers by remember { mutableStateOf(defaultBackLayers) }
    var selectedLayerId by remember { mutableStateOf<String?>("f_name") }

    val activeLayers = if (selectedSide == "front") frontLayers else backLayers
    val selectedLayer = activeLayers.firstOrNull { it.id == selectedLayerId } ?: activeLayers.firstOrNull()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) badgePhotoUri = uri
    }

    fun updateSelectedLayer(transform: (StudioLayer) -> StudioLayer) {
        val current = selectedLayer ?: return
        val updated = transform(current)
        if (selectedSide == "front") {
            frontLayers = frontLayers.map { if (it.id == updated.id) updated else it }
        } else {
            backLayers = backLayers.map { if (it.id == updated.id) updated else it }
        }
    }

    fun renderCardBitmap(): Bitmap {
        val width = 1012
        val height = 638
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        runCatching {
            canvas.drawColor(Color.parseColor(cardBgHex))
        }.getOrElse {
            canvas.drawColor(Color.WHITE)
        }

        val paint = Paint().apply { isAntiAlias = true }

        // Top authority strip
        val bannerHeight = 110f
        runCatching {
            paint.color = Color.parseColor(primaryColorHex)
        }.getOrElse {
            paint.color = Color.rgb(24, 24, 27)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), bannerHeight, paint)

        // Draw each layer
        for (layer in activeLayers) {
            if (!layer.isVisible) continue

            val x = (layer.xPercent / 100f) * width
            val y = (layer.yPercent / 100f) * height
            val w = (layer.widthPercent / 100f) * width
            val h = (layer.heightPercent / 100f) * height

            when (layer.type) {
                StudioLayerType.TEXT -> {
                    paint.style = Paint.Style.FILL
                    runCatching {
                        paint.color = Color.parseColor(layer.colorHex)
                    }.getOrElse {
                        paint.color = Color.BLACK
                    }
                    paint.textSize = layer.fontSize * 1.15f
                    paint.isFakeBoldText = layer.isBold
                    val display = layer.textValue.ifBlank { layer.bindingToken.ifBlank { layer.name } }
                    canvas.drawText(display, x, y + paint.textSize * 0.85f, paint)
                }

                StudioLayerType.PHOTO -> {
                    val rect = RectF(x, y, x + w, y + h)
                    paint.color = Color.rgb(228, 228, 231)
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(rect, 12f, 12f, paint)

                    if (badgePhotoUri != null) {
                        runCatching {
                            context.contentResolver.openInputStream(badgePhotoUri!!)?.use { stream ->
                                val decoded = android.graphics.BitmapFactory.decodeStream(stream)
                                if (decoded != null) {
                                    val scaled = Bitmap.createScaledBitmap(decoded, w.toInt().coerceAtLeast(10), h.toInt().coerceAtLeast(10), true)
                                    canvas.drawBitmap(scaled, x, y, paint)
                                }
                            }
                        }
                    } else {
                        paint.color = Color.rgb(113, 113, 122)
                        paint.textSize = 20f
                        paint.isFakeBoldText = false
                        canvas.drawText("PASSPORT PHOTO", x + 16f, y + h / 2f, paint)
                    }

                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(161, 161, 170)
                    paint.strokeWidth = 2.5f
                    canvas.drawRoundRect(rect, 12f, 12f, paint)
                }

                StudioLayerType.BARCODE -> {
                    val rect = RectF(x, y, x + w, y + h)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawRect(rect, paint)

                    paint.color = Color.BLACK
                    val barCount = 42
                    val step = w / barCount
                    for (i in 0 until barCount) {
                        val barW = if (i % 3 == 0 || i % 7 == 0) step * 0.8f else step * 0.4f
                        canvas.drawRect(x + i * step, y + 4f, x + i * step + barW, y + h - 16f, paint)
                    }
                    paint.textSize = 14f
                    paint.isFakeBoldText = true
                    canvas.drawText(layer.textValue.ifBlank { "1995061214102" }, x + 8f, y + h - 2f, paint)
                }

                StudioLayerType.QRCODE -> {
                    val rect = RectF(x, y, x + w, y + h)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawRoundRect(rect, 8f, 8f, paint)

                    paint.color = Color.BLACK
                    val gridSize = 6
                    val cellW = w / gridSize
                    val cellH = h / gridSize
                    for (r in 0 until gridSize) {
                        for (c in 0 until gridSize) {
                            if ((r + c) % 2 == 0 || (r == 0 && c == 0) || (r == gridSize - 1 && c == gridSize - 1)) {
                                canvas.drawRect(x + c * cellW + 2f, y + r * cellH + 2f, x + (c + 1) * cellW - 2f, y + (r + 1) * cellH - 2f, paint)
                            }
                        }
                    }
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(200, 200, 200)
                    paint.strokeWidth = 2f
                    canvas.drawRoundRect(rect, 8f, 8f, paint)
                }

                StudioLayerType.SIGNATURE -> {
                    val rect = RectF(x, y, x + w, y + h)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(250, 250, 250)
                    canvas.drawRoundRect(rect, 6f, 6f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(180, 180, 180)
                    paint.strokeWidth = 1.5f
                    canvas.drawRoundRect(rect, 6f, 6f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(30, 58, 138)
                    paint.strokeWidth = 2.5f
                    canvas.drawLine(x + 12f, y + h * 0.65f, x + w * 0.4f, y + h * 0.45f, paint)
                    canvas.drawLine(x + w * 0.4f, y + h * 0.45f, x + w * 0.7f, y + h * 0.75f, paint)
                    canvas.drawLine(x + w * 0.7f, y + h * 0.75f, x + w - 12f, y + h * 0.5f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = Color.GRAY
                    paint.textSize = 14f
                    paint.isFakeBoldText = false
                    canvas.drawText("HOLDER SIGNATURE", x + 10f, y + h - 6f, paint)
                }

                StudioLayerType.BADGE -> {
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(202, 138, 4)
                    paint.strokeWidth = 3f
                    canvas.drawCircle(x + w / 2f, y + h / 2f, w / 2f, paint)
                    paint.style = Paint.Style.FILL
                    paint.textSize = 14f
                    paint.color = Color.rgb(202, 138, 4)
                    canvas.drawText("SEAL", x + w / 3f, y + h / 2f + 5f, paint)
                }
            }
        }

        return bitmap
    }

    fun saveCurrentCard(): File? {
        val bmp = renderCardBitmap()
        return runCatching {
            val file = File(context.cacheDir, "studio_template_${selectedCategory.lowercase().replace(" ", "_")}_${selectedSide}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = "tpl_${System.currentTimeMillis()}",
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = 1,
                sourceType = "TEMPLATE_EDITOR"
            )
            lastExportedFile = file
            file
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Template Studio Editor", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$selectedCategory • ${selectedSide.uppercase()} SIDE", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val file = lastExportedFile ?: saveCurrentCard()
                            if (file != null) {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Studio Template"))
                            }
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share template")
                    }
                    IconButton(
                        onClick = {
                            val saved = saveCurrentCard()
                            statusMessage = if (saved != null) {
                                "Saved ${saved.name} to Downloads & local studio drafts!"
                            } else {
                                "Export failed. Please check storage permission."
                            }
                        },
                        modifier = Modifier.testTag("editor_save_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save template")
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (!statusMessage.isNullOrBlank()) {
                item {
                    SolidSectionCard {
                        Text(statusMessage.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // CR80 Preview Surface
            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CR80 Canvas Preview (${selectedSide.uppercase()} SIDE)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        AssistChip(
                            onClick = {
                                selectedSide = if (selectedSide == "front") "back" else "front"
                                selectedLayerId = if (selectedSide == "front") frontLayers.firstOrNull()?.id else backLayers.firstOrNull()?.id
                            },
                            label = { Text("Flip Card") },
                            leadingIcon = { Icon(Icons.Default.FlipCameraAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val previewBmp = remember(
                        activeLayers,
                        primaryColorHex,
                        cardBgHex,
                        badgePhotoUri,
                        selectedSide
                    ) {
                        renderCardBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Template Canvas Preview",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Category & Side Selector
            item {
                SolidSectionCard {
                    Text("Template Category & Side", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("front", "back").forEachIndexed { index, side ->
                            SegmentedButton(
                                selected = selectedSide == side,
                                onClick = {
                                    selectedSide = side
                                    selectedLayerId = if (side == "front") frontLayers.firstOrNull()?.id else backLayers.firstOrNull()?.id
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                modifier = Modifier.testTag("side_switch_$side")
                            ) {
                                Text("${side.replaceFirstChar { it.uppercase() }} Side")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Theme & Banner Color Palette
            item {
                SolidSectionCard {
                    Text("Card Branding & Primary Palette", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Select Header Bar Color", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        colorPresets.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (primaryColorHex == hex) 3.dp else 1.dp,
                                        color = if (primaryColorHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { primaryColorHex = hex }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (badgePhotoUri == null) "Select Sample Badge Photo" else "Change Badge Photo")
                    }
                }
            }

            // Layer Stack Management
            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Layers (${activeLayers.size})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                val newId = "text_${System.currentTimeMillis()}"
                                val newLayer = StudioLayer(
                                    id = newId,
                                    name = "New Text Layer",
                                    type = StudioLayerType.TEXT,
                                    textValue = "Sample Text",
                                    xPercent = 10f,
                                    yPercent = 50f
                                )
                                if (selectedSide == "front") frontLayers = frontLayers + newLayer
                                else backLayers = backLayers + newLayer
                                selectedLayerId = newId
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add text layer")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeLayers) { layer ->
                            FilterChip(
                                selected = selectedLayerId == layer.id,
                                onClick = { selectedLayerId = layer.id },
                                label = { Text(layer.name) },
                                leadingIcon = {
                                    Icon(
                                        when (layer.type) {
                                            StudioLayerType.TEXT -> Icons.Default.TextFields
                                            StudioLayerType.PHOTO -> Icons.Default.AccountBox
                                            StudioLayerType.BARCODE -> Icons.Default.QrCode
                                            StudioLayerType.QRCODE -> Icons.Default.QrCode2
                                            StudioLayerType.SIGNATURE -> Icons.Default.Draw
                                            StudioLayerType.BADGE -> Icons.Default.Shield
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Selected Layer Inspector
            if (selectedLayer != null) {
                item {
                    val layer = selectedLayer
                    SolidSectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Layer: ${layer.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            IconButton(
                                onClick = {
                                    if (selectedSide == "front") {
                                        frontLayers = frontLayers.filterNot { it.id == layer.id }
                                        selectedLayerId = frontLayers.firstOrNull()?.id
                                    } else {
                                        backLayers = backLayers.filterNot { it.id == layer.id }
                                        selectedLayerId = backLayers.firstOrNull()?.id
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete layer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = layer.name,
                            onValueChange = { newName -> updateSelectedLayer { it.copy(name = newName) } },
                            label = { Text("Layer Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (layer.type == StudioLayerType.TEXT || layer.type == StudioLayerType.BARCODE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = layer.textValue,
                                onValueChange = { newVal -> updateSelectedLayer { it.copy(textValue = newVal) } },
                                label = { Text("Static Text or Preview Value") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Dynamic Binding Token:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(bindingTokens) { token ->
                                    FilterChip(
                                        selected = layer.bindingToken == token,
                                        onClick = {
                                            updateSelectedLayer { it.copy(bindingToken = if (it.bindingToken == token) "" else token) }
                                        },
                                        label = { Text(token, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Size: ${layer.fontSize.toInt()}sp", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilledTonalButton(
                                    onClick = { updateSelectedLayer { it.copy(fontSize = (it.fontSize - 2f).coerceAtLeast(10f)) } }
                                ) {
                                    Text("-")
                                }
                                FilledTonalButton(
                                    onClick = { updateSelectedLayer { it.copy(fontSize = (it.fontSize + 2f).coerceAtMost(52f)) } }
                                ) {
                                    Text("+")
                                }
                                FilterChip(
                                    selected = layer.isBold,
                                    onClick = { updateSelectedLayer { it.copy(isBold = !it.isBold) } },
                                    label = { Text("Bold") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Position (X: ${layer.xPercent.toInt()}%, Y: ${layer.yPercent.toInt()}%)", fontSize = 12.sp)
                        Slider(
                            value = layer.xPercent,
                            onValueChange = { newX -> updateSelectedLayer { it.copy(xPercent = newX) } },
                            valueRange = 0f..90f
                        )
                        Slider(
                            value = layer.yPercent,
                            onValueChange = { newY -> updateSelectedLayer { it.copy(yPercent = newY) } },
                            valueRange = 0f..90f
                        )
                    }
                }
            }

            // Export Actions
            item {
                Button(
                    onClick = {
                        val saved = saveCurrentCard()
                        statusMessage = if (saved != null) {
                            "Exported ${saved.name} to Downloads (${saved.length() / 1024} KB)"
                        } else {
                            "Export failed."
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Render & Save Card PNG (${selectedSide.uppercase()})")
                }
            }
        }
    }
}
