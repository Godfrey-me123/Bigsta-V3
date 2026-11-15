package com.example.features.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.database.CachedTemplateEntity
import com.example.ui.components.SolidSectionCard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class StudioLayerType(val label: String) {
    TEXT("Text Field"),
    PHOTO("Photo Box"),
    BARCODE("Barcode (Code128)"),
    QRCODE("QR Code 2D"),
    SIGNATURE("Signature Box"),
    BADGE("Security Seal"),
    SHAPE("Accent Shape")
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
    val isVisible: Boolean = true,
    val isLocked: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateStudioEditorScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val cachedTemplates by viewModel.cachedTemplates.collectAsStateWithLifecycle()

    val categories = listOf("NIDA", "Driving Licence", "NHIF", "NRC Zambia", "Student ID", "Employee Badge", "Custom")
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
        "{{authority}}",
        "{{district}}",
        "{{facility}}",
        "{{signature}}",
        "{{photo}}"
    )
    val colorPresets = listOf("#18181B", "#1E3A8A", "#14532D", "#7F1D1D", "#854D0E", "#0284C7", "#D97706", "#4B5563")

    var templateId by remember { mutableStateOf<String?>(null) }
    var templateName by remember { mutableStateOf("Tanzania National ID Template") }
    var selectedCategory by remember { mutableStateOf("NIDA") }
    var selectedSide by remember { mutableStateOf("front") }
    var isUniversal by remember { mutableStateOf(false) }
    var primaryColorHex by remember { mutableStateOf("#18181B") }
    var cardBgHex by remember { mutableStateOf("#FFFFFF") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

    // Dialog & Library Sheet States
    var showLibraryModal by remember { mutableStateOf(false) }
    var showJsonModal by remember { mutableStateOf(false) }
    var jsonModalMode by remember { mutableStateOf("EXPORT") } // EXPORT or IMPORT
    var jsonTextState by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
                widthPercent = 40f,
                heightPercent = 14f
            ),
            StudioLayer(
                id = "f_qr",
                name = "Verification QR Code",
                type = StudioLayerType.QRCODE,
                bindingToken = "{{nida_number}}",
                textValue = "BIGSTA-VERIFIED-19950612",
                xPercent = 78f,
                yPercent = 68f,
                widthPercent = 17f,
                heightPercent = 22f
            ),
            StudioLayer(
                id = "f_seal",
                name = "Official Seal",
                type = StudioLayerType.BADGE,
                textValue = "COAT OF ARMS",
                xPercent = 82f,
                yPercent = 24f,
                widthPercent = 13f,
                heightPercent = 18f
            )
        )
    }

    val defaultBackLayers = remember {
        listOf(
            StudioLayer(
                id = "b_header",
                name = "Back Header Terms",
                type = StudioLayerType.TEXT,
                textValue = "MALI YA SERIKALI YA JAMHURI YA MUUNGANO WA TANZANIA",
                xPercent = 5f,
                yPercent = 8f,
                fontSize = 18f,
                isBold = true,
                colorHex = "#18181B"
            ),
            StudioLayer(
                id = "b_terms",
                name = "Legal Notice Text",
                type = StudioLayerType.TEXT,
                textValue = "Kadi hii inapaswa kurudishwa ofisi ya NIDA iliyo karibu ikipotea au kupatikana.",
                xPercent = 5f,
                yPercent = 20f,
                fontSize = 16f,
                isBold = false,
                colorHex = "#52525B"
            ),
            StudioLayer(
                id = "b_sign",
                name = "Signature of Holder",
                type = StudioLayerType.SIGNATURE,
                bindingToken = "{{signature}}",
                xPercent = 5f,
                yPercent = 42f,
                widthPercent = 40f,
                heightPercent = 22f
            ),
            StudioLayer(
                id = "b_authority",
                name = "Issuing Authority",
                type = StudioLayerType.TEXT,
                textValue = "DIRECTOR GENERAL • NIDA",
                xPercent = 55f,
                yPercent = 48f,
                fontSize = 18f,
                isBold = true,
                colorHex = "#18181B"
            ),
            StudioLayer(
                id = "b_mrz",
                name = "Machine Readable Zone (MRZ)",
                type = StudioLayerType.TEXT,
                textValue = "I<TZA199506124<<<<<<<<<<<<<<<<<<<\n8501018F2812312TZA<<<<<<<<<<<<<6",
                xPercent = 5f,
                yPercent = 76f,
                fontSize = 20f,
                isBold = true,
                colorHex = "#18181B"
            )
        )
    }

    var frontLayers by remember { mutableStateOf(defaultFrontLayers) }
    var backLayers by remember { mutableStateOf(defaultBackLayers) }
    var selectedLayerId by remember { mutableStateOf(defaultFrontLayers.first().id) }

    val currentLayers = if (selectedSide == "front") frontLayers else backLayers
    val selectedLayer = currentLayers.find { it.id == selectedLayerId } ?: currentLayers.firstOrNull()

    fun updateCurrentLayers(newList: List<StudioLayer>) {
        if (selectedSide == "front") {
            frontLayers = newList
        } else {
            backLayers = newList
        }
    }

    fun updateSelectedLayer(transformer: (StudioLayer) -> StudioLayer) {
        val target = selectedLayer ?: return
        val updated = currentLayers.map { if (it.id == target.id) transformer(it) else it }
        updateCurrentLayers(updated)
    }

    fun moveLayerUp(id: String) {
        val index = currentLayers.indexOfFirst { it.id == id }
        if (index > 0) {
            val list = currentLayers.toMutableList()
            val item = list.removeAt(index)
            list.add(index - 1, item)
            updateCurrentLayers(list)
        }
    }

    fun moveLayerDown(id: String) {
        val index = currentLayers.indexOfFirst { it.id == id }
        if (index in 0 until currentLayers.size - 1) {
            val list = currentLayers.toMutableList()
            val item = list.removeAt(index)
            list.add(index + 1, item)
            updateCurrentLayers(list)
        }
    }

    fun bringLayerToFront(id: String) {
        val index = currentLayers.indexOfFirst { it.id == id }
        if (index in 0 until currentLayers.size - 1) {
            val list = currentLayers.toMutableList()
            val item = list.removeAt(index)
            list.add(item)
            updateCurrentLayers(list)
        }
    }

    fun sendLayerToBack(id: String) {
        val index = currentLayers.indexOfFirst { it.id == id }
        if (index > 0) {
            val list = currentLayers.toMutableList()
            val item = list.removeAt(index)
            list.add(0, item)
            updateCurrentLayers(list)
        }
    }

    fun duplicateLayer(id: String) {
        val original = currentLayers.find { it.id == id } ?: return
        val copy = original.copy(
            id = "layer_${System.currentTimeMillis()}",
            name = "${original.name} (Copy)",
            xPercent = (original.xPercent + 4f).coerceAtMost(80f),
            yPercent = (original.yPercent + 4f).coerceAtMost(80f)
        )
        val list = currentLayers.toMutableList().apply { add(copy) }
        updateCurrentLayers(list)
        selectedLayerId = copy.id
    }

    fun deleteLayer(id: String) {
        if (currentLayers.size <= 1) return
        val list = currentLayers.filterNot { it.id == id }
        updateCurrentLayers(list)
        selectedLayerId = list.firstOrNull()?.id ?: ""
    }

    fun serializeLayersJson(): String {
        return try {
            val root = JSONObject()
            root.put("template_name", templateName)
            root.put("category", selectedCategory)
            root.put("is_universal", isUniversal)

            val frontArr = JSONArray()
            frontLayers.forEach { l ->
                val o = JSONObject()
                o.put("id", l.id)
                o.put("name", l.name)
                o.put("type", l.type.name)
                o.put("bindingToken", l.bindingToken)
                o.put("textValue", l.textValue)
                o.put("xPercent", l.xPercent.toDouble())
                o.put("yPercent", l.yPercent.toDouble())
                o.put("widthPercent", l.widthPercent.toDouble())
                o.put("heightPercent", l.heightPercent.toDouble())
                o.put("fontSize", l.fontSize.toDouble())
                o.put("isBold", l.isBold)
                o.put("colorHex", l.colorHex)
                o.put("alignment", l.alignment)
                frontArr.put(o)
            }
            root.put("front_layers", frontArr)

            val backArr = JSONArray()
            backLayers.forEach { l ->
                val o = JSONObject()
                o.put("id", l.id)
                o.put("name", l.name)
                o.put("type", l.type.name)
                o.put("bindingToken", l.bindingToken)
                o.put("textValue", l.textValue)
                o.put("xPercent", l.xPercent.toDouble())
                o.put("yPercent", l.yPercent.toDouble())
                o.put("widthPercent", l.widthPercent.toDouble())
                o.put("heightPercent", l.heightPercent.toDouble())
                o.put("fontSize", l.fontSize.toDouble())
                o.put("isBold", l.isBold)
                o.put("colorHex", l.colorHex)
                o.put("alignment", l.alignment)
                backArr.put(o)
            }
            root.put("back_layers", backArr)
            root.toString(2)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun importLayersJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)
            if (root.has("template_name")) templateName = root.getString("template_name")
            if (root.has("category")) selectedCategory = root.getString("category")
            if (root.has("is_universal")) isUniversal = root.optBoolean("is_universal", false)

            if (root.has("front_layers")) {
                val fArr = root.getJSONArray("front_layers")
                val fList = mutableListOf<StudioLayer>()
                for (i in 0 until fArr.length()) {
                    val o = fArr.getJSONObject(i)
                    fList.add(
                        StudioLayer(
                            id = o.optString("id", "f_$i"),
                            name = o.optString("name", "Layer $i"),
                            type = runCatching { StudioLayerType.valueOf(o.getString("type")) }.getOrDefault(StudioLayerType.TEXT),
                            bindingToken = o.optString("bindingToken", ""),
                            textValue = o.optString("textValue", ""),
                            xPercent = o.optDouble("xPercent", 10.0).toFloat(),
                            yPercent = o.optDouble("yPercent", 20.0).toFloat(),
                            widthPercent = o.optDouble("widthPercent", 80.0).toFloat(),
                            heightPercent = o.optDouble("heightPercent", 10.0).toFloat(),
                            fontSize = o.optDouble("fontSize", 24.0).toFloat(),
                            isBold = o.optBoolean("isBold", false),
                            colorHex = o.optString("colorHex", "#18181B"),
                            alignment = o.optString("alignment", "LEFT")
                        )
                    )
                }
                if (fList.isNotEmpty()) frontLayers = fList
            }

            if (root.has("back_layers")) {
                val bArr = root.getJSONArray("back_layers")
                val bList = mutableListOf<StudioLayer>()
                for (i in 0 until bArr.length()) {
                    val o = bArr.getJSONObject(i)
                    bList.add(
                        StudioLayer(
                            id = o.optString("id", "b_$i"),
                            name = o.optString("name", "Layer $i"),
                            type = runCatching { StudioLayerType.valueOf(o.getString("type")) }.getOrDefault(StudioLayerType.TEXT),
                            bindingToken = o.optString("bindingToken", ""),
                            textValue = o.optString("textValue", ""),
                            xPercent = o.optDouble("xPercent", 10.0).toFloat(),
                            yPercent = o.optDouble("yPercent", 20.0).toFloat(),
                            widthPercent = o.optDouble("widthPercent", 80.0).toFloat(),
                            heightPercent = o.optDouble("heightPercent", 10.0).toFloat(),
                            fontSize = o.optDouble("fontSize", 24.0).toFloat(),
                            isBold = o.optBoolean("isBold", false),
                            colorHex = o.optString("colorHex", "#18181B"),
                            alignment = o.optString("alignment", "LEFT")
                        )
                    )
                }
                if (bList.isNotEmpty()) backLayers = bList
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun renderCardBitmap(sideToRender: String = selectedSide): Bitmap {
        val width = 1012
        val height = 638
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = runCatching { Color.parseColor(cardBgHex) }.getOrDefault(Color.WHITE)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(cardRect, 32f, 32f, bgPaint)

        val headerPaint = Paint().apply {
            color = runCatching { Color.parseColor(primaryColorHex) }.getOrDefault(Color.rgb(24, 24, 27))
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 120f, headerPaint)

        val flagPaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        flagPaint.color = Color.rgb(30, 64, 175)
        canvas.drawRect(0f, 120f, width.toFloat(), 128f, flagPaint)

        val borderPaint = Paint().apply {
            color = Color.rgb(228, 228, 231)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, 32f, 32f, borderPaint)

        val layers = if (sideToRender == "front") frontLayers else backLayers
        val paint = Paint().apply { isAntiAlias = true }

        layers.filter { it.isVisible }.forEach { layer ->
            val x = (layer.xPercent / 100f) * width
            val y = (layer.yPercent / 100f) * height
            val w = (layer.widthPercent / 100f) * width
            val h = (layer.heightPercent / 100f) * height

            when (layer.type) {
                StudioLayerType.TEXT -> {
                    paint.style = Paint.Style.FILL
                    paint.color = runCatching { Color.parseColor(layer.colorHex) }.getOrDefault(Color.BLACK)
                    paint.textSize = layer.fontSize * 1.35f
                    paint.isFakeBoldText = layer.isBold
                    val display = layer.textValue.ifBlank { layer.bindingToken.ifBlank { layer.name } }
                    canvas.drawText(display, x, y + (layer.fontSize * 1.1f), paint)
                }

                StudioLayerType.PHOTO -> {
                    val pRect = RectF(x, y, x + w, y + h)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(244, 244, 245)
                    canvas.drawRoundRect(pRect, 16f, 16f, paint)

                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(212, 212, 216)
                    paint.strokeWidth = 3f
                    canvas.drawRoundRect(pRect, 16f, 16f, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = Color.rgb(161, 161, 170)
                    paint.textSize = 20f
                    paint.isFakeBoldText = true
                    canvas.drawText("BIOMETRIC PHOTO", x + 16f, y + h / 2f, paint)
                }

                StudioLayerType.BARCODE -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawRect(x, y, x + w, y + h, paint)

                    paint.color = Color.BLACK
                    var currentX = x + 10f
                    val endX = x + w - 10f
                    var toggle = true
                    while (currentX < endX) {
                        val barWidth = if (toggle) 6f else 3f
                        canvas.drawRect(currentX, y + 4f, currentX + barWidth, y + h - 18f, paint)
                        currentX += barWidth + (if (toggle) 4f else 2f)
                        toggle = !toggle
                    }
                    paint.textSize = 14f
                    paint.isFakeBoldText = true
                    canvas.drawText(layer.textValue.take(18), x + 12f, y + h - 4f, paint)
                }

                StudioLayerType.QRCODE -> {
                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawRect(x, y, x + w, y + h, paint)

                    paint.color = Color.BLACK
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 4f
                    canvas.drawRect(x + 4f, y + 4f, x + w - 4f, y + h - 4f, paint)

                    paint.style = Paint.Style.FILL
                    canvas.drawRect(x + 10f, y + 10f, x + 34f, y + 34f, paint)
                    canvas.drawRect(x + w - 34f, y + 10f, x + w - 10f, y + 34f, paint)
                    canvas.drawRect(x + 10f, y + h - 34f, x + 34f, y + h - 10f, paint)
                }

                StudioLayerType.SIGNATURE -> {
                    paint.style = Paint.Style.STROKE
                    paint.color = Color.rgb(228, 228, 231)
                    paint.strokeWidth = 2f
                    canvas.drawRect(x, y, x + w, y + h, paint)

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

                StudioLayerType.SHAPE -> {
                    paint.style = Paint.Style.FILL
                    paint.color = runCatching { Color.parseColor(layer.colorHex) }.getOrDefault(Color.LTGRAY)
                    canvas.drawRoundRect(RectF(x, y, x + w, y + h), 8f, 8f, paint)
                }
            }
        }

        return bitmap
    }

    fun saveSingleSideCard(): File? {
        val bmp = renderCardBitmap(selectedSide)
        return runCatching {
            val file = File(context.cacheDir, "studio_${selectedCategory.lowercase().replace(" ", "_")}_${selectedSide}_${System.currentTimeMillis()}.png")
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

    fun savePrintableMergedSheet(): File? {
        val frontBmp = renderCardBitmap("front")
        val backBmp = renderCardBitmap("back")
        val sheetWidth = 1012
        val sheetHeight = 638 * 2 + 60
        val sheetBmp = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheetBmp)
        canvas.drawColor(Color.WHITE)

        canvas.drawBitmap(frontBmp, 0f, 0f, null)
        canvas.drawBitmap(backBmp, 0f, 638f + 60f, null)

        return runCatching {
            val file = File(context.cacheDir, "sheet_${selectedCategory.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                sheetBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = "sheet_${System.currentTimeMillis()}",
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = 2,
                sourceType = "TEMPLATE_STUDIO_SHEET"
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
                    // Open template library modal
                    IconButton(onClick = { showLibraryModal = true }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Template Library")
                    }

                    // JSON Import / Export modal
                    IconButton(onClick = {
                        jsonTextState = serializeLayersJson()
                        jsonModalMode = "EXPORT"
                        showJsonModal = true
                    }) {
                        Icon(Icons.Default.Code, contentDescription = "JSON Tools")
                    }

                    // Cloud Database Publish / Sync (Admin & User)
                    IconButton(
                        onClick = {
                            viewModel.saveStudioTemplate(
                                id = templateId,
                                name = templateName,
                                category = selectedCategory,
                                side = selectedSide,
                                isUniversal = isUniversal,
                                layersJson = serializeLayersJson()
                            ) { success, msg ->
                                statusMessage = if (success) "Saved & synced to database: $msg" else "Save failed: $msg"
                            }
                        }
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Save to Cloud")
                    }

                    // Share PNG
                    IconButton(
                        onClick = {
                            val file = lastExportedFile ?: saveSingleSideCard()
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

                    // Save single side PNG
                    IconButton(
                        onClick = {
                            val saved = saveSingleSideCard()
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            statusMessage?.let { msg ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // CR80 Canvas Live Card Preview
            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live CR80 Card Preview (300 DPI)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    selectedSide = if (selectedSide == "front") "back" else "front"
                                    selectedLayerId = (if (selectedSide == "front") frontLayers else backLayers).firstOrNull()?.id ?: ""
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedSide == "front") "Flip to Back" else "Flip to Front", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val previewBitmap = remember(frontLayers, backLayers, selectedSide, primaryColorHex, cardBgHex) {
                        renderCardBitmap()
                    }

                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "CR80 Canvas Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1012f / 638f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val file = saveSingleSideCard()
                                statusMessage = if (file != null) "Exported ${file.name} to Downloads!" else "Export failed."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Side PNG", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val file = savePrintableMergedSheet()
                                statusMessage = if (file != null) "Created merged print sheet ${file.name}!" else "Sheet creation failed."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Printable Sheet", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Template Metadata & Category Isolation
            item {
                SolidSectionCard {
                    Text("Template Metadata & Target Service", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Target Service Category:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory.equals(cat, ignoreCase = true),
                                onClick = { selectedCategory = cat },
                                label = { Text(cat, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Universal Template (Fallback for all)", fontSize = 13.sp)
                        Switch(checked = isUniversal, onCheckedChange = { isUniversal = it })
                    }
                }
            }

            // Card Style & Colors
            item {
                SolidSectionCard {
                    Text("Card Styling & Banner Colors", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Header Banner Color Preset:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(colorPresets) { hex ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(runCatching { androidx.compose.ui.graphics.Color(Color.parseColor(hex)) }.getOrDefault(androidx.compose.ui.graphics.Color.Black))
                                    .border(
                                        width = if (primaryColorHex.equals(hex, ignoreCase = true)) 3.dp else 1.dp,
                                        color = if (primaryColorHex.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { primaryColorHex = hex }
                            )
                        }
                    }
                }
            }

            // Layer Stack Management with Reorder & Actions
            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedSide.uppercase()} Layer Stack (${currentLayers.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Add Layer button
                            IconButton(
                                onClick = {
                                    val newLayer = StudioLayer(
                                        id = "layer_${System.currentTimeMillis()}",
                                        name = "New Text Field",
                                        type = StudioLayerType.TEXT,
                                        textValue = "SAMPLE TEXT",
                                        xPercent = 10f,
                                        yPercent = 50f
                                    )
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                }
                            ) {
                                Icon(Icons.Default.AddBox, contentDescription = "Add Layer")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    currentLayers.forEachIndexed { index, layer ->
                        val isSelected = layer.id == selectedLayerId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedLayerId = layer.id },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(layer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(layer.type.label, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                        }
                                    }
                                    Text(
                                        "X: ${layer.xPercent.toInt()}% • Y: ${layer.yPercent.toInt()}%" +
                                            if (layer.bindingToken.isNotBlank()) " • ${layer.bindingToken}" else "",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { moveLayerUp(layer.id) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { moveLayerDown(layer.id) },
                                        enabled = index < currentLayers.size - 1,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { duplicateLayer(layer.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { deleteLayer(layer.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Layer Properties Editor
            selectedLayer?.let { layer ->
                item {
                    SolidSectionCard {
                        Text("Edit Layer: ${layer.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = layer.name,
                            onValueChange = { newName -> updateSelectedLayer { it.copy(name = newName) } },
                            label = { Text("Layer Label") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (layer.type == StudioLayerType.TEXT || layer.type == StudioLayerType.BARCODE || layer.type == StudioLayerType.QRCODE) {
                            OutlinedTextField(
                                value = layer.textValue,
                                onValueChange = { newVal -> updateSelectedLayer { it.copy(textValue = newVal) } },
                                label = { Text("Display / Default Text") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text("Bind Dynamic Token:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(bindingTokens) { token ->
                                FilterChip(
                                    selected = layer.bindingToken == token,
                                    onClick = {
                                        val nextToken = if (layer.bindingToken == token) "" else token
                                        updateSelectedLayer { it.copy(bindingToken = nextToken) }
                                    },
                                    label = { Text(token, fontSize = 10.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Nudge and Alignment Controls
                        Text("Positioning & Alignment Tools:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val centeredX = ((100f - layer.widthPercent) / 2f).coerceIn(0f, 100f)
                                    updateSelectedLayer { it.copy(xPercent = centeredX) }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.FormatAlignCenter, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Center Horiz", fontSize = 11.sp)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { updateSelectedLayer { it.copy(xPercent = (it.xPercent - 2f).coerceAtLeast(0f)) } }
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Left")
                                }
                                IconButton(
                                    onClick = { updateSelectedLayer { it.copy(xPercent = (it.xPercent + 2f).coerceAtMost(100f)) } }
                                ) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Right")
                                }
                                IconButton(
                                    onClick = { updateSelectedLayer { it.copy(yPercent = (it.yPercent - 2f).coerceAtLeast(0f)) } }
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up")
                                }
                                IconButton(
                                    onClick = { updateSelectedLayer { it.copy(yPercent = (it.yPercent + 2f).coerceAtMost(100f)) } }
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Down")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("X Position: ${layer.xPercent.toInt()}%", fontSize = 12.sp)
                        Slider(
                            value = layer.xPercent,
                            onValueChange = { newVal -> updateSelectedLayer { it.copy(xPercent = newVal) } },
                            valueRange = 0f..100f
                        )

                        Text("Y Position: ${layer.yPercent.toInt()}%", fontSize = 12.sp)
                        Slider(
                            value = layer.yPercent,
                            onValueChange = { newVal -> updateSelectedLayer { it.copy(yPercent = newVal) } },
                            valueRange = 0f..100f
                        )

                        if (layer.type == StudioLayerType.TEXT) {
                            Text("Font Size: ${layer.fontSize.toInt()}sp", fontSize = 12.sp)
                            Slider(
                                value = layer.fontSize,
                                onValueChange = { newVal -> updateSelectedLayer { it.copy(fontSize = newVal) } },
                                valueRange = 12f..48f
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bold Weight", fontSize = 13.sp)
                                Switch(
                                    checked = layer.isBold,
                                    onCheckedChange = { isBold -> updateSelectedLayer { it.copy(isBold = isBold) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Template Library Picker Modal
    if (showLibraryModal) {
        AlertDialog(
            onDismissRequest = { showLibraryModal = false },
            title = { Text("Template Library & Presets") },
            text = {
                Column(modifier = Modifier.heightIn(max = 350.dp)) {
                    Text("Select a cloud template to load into studio:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (cachedTemplates.isEmpty()) {
                        Text("No cached templates found in database.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(cachedTemplates) { tpl ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            templateId = tpl.id
                                            templateName = tpl.name
                                            selectedCategory = tpl.documentCategoryId
                                            selectedSide = tpl.side.lowercase()
                                            if (tpl.layersJson.isNotBlank()) {
                                                importLayersJson(tpl.layersJson)
                                            }
                                            showLibraryModal = false
                                            statusMessage = "Loaded template '${tpl.name}'"
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(tpl.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${tpl.documentCategoryId} • Side: ${tpl.side.uppercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLibraryModal = false }) {
                    Text("Close")
                }
            }
        )
    }

    // JSON Import / Export Modal
    if (showJsonModal) {
        AlertDialog(
            onDismissRequest = { showJsonModal = false },
            title = { Text(if (jsonModalMode == "EXPORT") "Export Template JSON" else "Import Template JSON") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = jsonModalMode == "EXPORT",
                            onClick = {
                                jsonModalMode = "EXPORT"
                                jsonTextState = serializeLayersJson()
                            },
                            label = { Text("Export") }
                        )
                        FilterChip(
                            selected = jsonModalMode == "IMPORT",
                            onClick = {
                                jsonModalMode = "IMPORT"
                                jsonTextState = ""
                            },
                            label = { Text("Import") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = jsonTextState,
                        onValueChange = { jsonTextState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        readOnly = jsonModalMode == "EXPORT",
                        label = { Text(if (jsonModalMode == "EXPORT") "Template Definition JSON" else "Paste JSON Definition") }
                    )
                }
            },
            confirmButton = {
                if (jsonModalMode == "EXPORT") {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Template JSON", jsonTextState))
                            statusMessage = "Copied template JSON to clipboard!"
                            showJsonModal = false
                        }
                    ) {
                        Text("Copy JSON")
                    }
                } else {
                    Button(
                        onClick = {
                            val success = importLayersJson(jsonTextState)
                            statusMessage = if (success) "Successfully imported template!" else "Failed to parse JSON definition."
                            showJsonModal = false
                        }
                    ) {
                        Text("Apply JSON")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
