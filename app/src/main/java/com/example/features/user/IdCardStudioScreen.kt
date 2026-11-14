package com.example.features.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.SolidSectionCard
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdCardStudioScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cachedTemplates by viewModel.cachedTemplates.collectAsStateWithLifecycle()

    val categories = listOf("NIDA", "Driving Licence", "NHIF", "NRC Zambia", "Employee Badge")
    var selectedCategory by remember { mutableStateOf("NIDA") }
    var selectedSide by remember { mutableStateOf("front") }

    // Core ID card fields
    var fullName by remember { mutableStateOf("AMINA JUMA HASSAN") }
    var idNumber by remember { mutableStateOf("19950612-14102-00001-22") }
    var dateOfBirth by remember { mutableStateOf("1995-06-12") }
    var gender by remember { mutableStateOf("Female") }
    var nationality by remember { mutableStateOf("Tanzanian") }

    // Driving licence specific
    var bloodGroup by remember { mutableStateOf("O+") }
    var licenceClasses by remember { mutableStateOf("A, B, D") }
    var expiryDate by remember { mutableStateOf("2029-06-12") }

    // NHIF specific
    var facilityCode by remember { mutableStateOf("TZ-DAR-001 (Muhimbili)") }
    var memberType by remember { mutableStateOf("Principal Member") }

    // NRC Zambia specific
    var nrcProvince by remember { mutableStateOf("Lusaka Province") }

    // Back side fields
    var issuingAuthority by remember { mutableStateOf("National Identification Authority (NIDA)") }
    var cardNotice by remember { mutableStateOf("This card is property of the Government. If found, please return to the nearest police station.") }

    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

    val matchingTemplates = remember(cachedTemplates, selectedCategory, selectedSide) {
        cachedTemplates.filter {
            it.documentCategoryId.equals(selectedCategory, ignoreCase = true) &&
                it.side.equals(selectedSide, ignoreCase = true)
        }
    }
    val activeTemplate = matchingTemplates.firstOrNull { it.isActive } ?: matchingTemplates.firstOrNull()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    photoBitmap = BitmapFactory.decodeStream(stream)
                }
            }
        }
    }

    fun renderCard(side: String): Bitmap {
        val width = 1012
        val height = 638
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(android.graphics.Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (selectedCategory) {
                "Driving Licence" -> android.graphics.Color.rgb(20, 83, 45) // Deep Green
                "NHIF" -> android.graphics.Color.rgb(30, 58, 138) // Deep Blue
                "NRC Zambia" -> android.graphics.Color.rgb(180, 83, 9) // Amber/Copper
                "Employee Badge" -> android.graphics.Color.rgb(127, 29, 29) // Deep Maroon
                else -> android.graphics.Color.rgb(24, 24, 27) // Dark Zinc for NIDA
            }
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 110f, headerPaint)

        val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            isFakeBoldText = true
        }
        val headerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(228, 228, 231)
            textSize = 18f
        }

        val title = when (selectedCategory) {
            "Driving Licence" -> "LESENI YA UDEREVA • DRIVING LICENCE"
            "NHIF" -> "NATIONAL HEALTH INSURANCE FUND • NHIF"
            "NRC Zambia" -> "REPUBLIC OF ZAMBIA • NATIONAL REGISTRATION CARD"
            "Employee Badge" -> "CORPORATE STAFF IDENTITY BADGE"
            else -> "JAMHURI YA MUUNGANO WA TANZANIA • NIDA"
        }
        canvas.drawText(title, 36f, 56f, headerTitlePaint)
        canvas.drawText("CR80 COMPLIANT SPECIFICATION — SIDE: ${side.uppercase()}", 36f, 92f, headerSubPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(24, 24, 27)
            textSize = 28f
            isFakeBoldText = true
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(113, 113, 122)
            textSize = 20f
        }

        if (side == "front") {
            // Photo Frame Box
            val photoRect = RectF(36f, 140f, 286f, 480f)
            val photoBg = Paint().apply { color = android.graphics.Color.rgb(244, 244, 245) }
            canvas.drawRoundRect(photoRect, 14f, 14f, photoBg)
            val photoBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = android.graphics.Color.rgb(212, 212, 216)
            }
            canvas.drawRoundRect(photoRect, 14f, 14f, photoBorder)

            photoBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, photoRect, null)
            } ?: run {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.GRAY
                    textSize = 22f
                }
                canvas.drawText("PASSPORT PHOTO", 54f, 310f, placeholderPaint)
            }

            // Data Columns
            val leftCol = 320f
            var curY = 175f

            canvas.drawText("FULL NAME / JINA KAMILI", leftCol, curY, labelPaint)
            curY += 34f
            canvas.drawText(fullName.uppercase(), leftCol, curY, bodyPaint)

            curY += 50f
            canvas.drawText("CARD / NATIONAL ID NUMBER", leftCol, curY, labelPaint)
            curY += 34f
            canvas.drawText(idNumber, leftCol, curY, bodyPaint)

            curY += 50f
            canvas.drawText("DATE OF BIRTH • GENDER", leftCol, curY, labelPaint)
            curY += 34f
            canvas.drawText("$dateOfBirth • ${gender.uppercase()}", leftCol, curY, bodyPaint)

            curY += 50f
            when (selectedCategory) {
                "Driving Licence" -> {
                    canvas.drawText("CLASSES • BLOOD GROUP • EXPIRY", leftCol, curY, labelPaint)
                    curY += 34f
                    canvas.drawText("CLASS: $licenceClasses | BG: $bloodGroup | EXP: $expiryDate", leftCol, curY, bodyPaint)
                }
                "NHIF" -> {
                    canvas.drawText("FACILITY CODE • CATEGORY", leftCol, curY, labelPaint)
                    curY += 34f
                    canvas.drawText("$facilityCode • $memberType", leftCol, curY, bodyPaint)
                }
                "NRC Zambia" -> {
                    canvas.drawText("PROVINCE • NATIONALITY", leftCol, curY, labelPaint)
                    curY += 34f
                    canvas.drawText("$nrcProvince • Zambian Citizen", leftCol, curY, bodyPaint)
                }
                else -> {
                    canvas.drawText("NATIONALITY / URAIA", leftCol, curY, labelPaint)
                    curY += 34f
                    canvas.drawText(nationality.uppercase(), leftCol, curY, bodyPaint)
                }
            }

            // Bottom Barcode Stripe
            val barY = 530f
            val barH = 70f
            val barW = 600f
            val barPaint = Paint().apply { color = android.graphics.Color.BLACK }
            val barCount = 48
            val step = barW / barCount
            for (i in 0 until barCount) {
                val bw = if (i % 3 == 0 || i % 5 == 0) step * 0.75f else step * 0.35f
                canvas.drawRect(leftCol + i * step, barY, leftCol + i * step + bw, barY + barH - 18f, barPaint)
            }
            val codeNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.DKGRAY
                textSize = 16f
                isFakeBoldText = true
            }
            canvas.drawText(idNumber.replace("-", ""), leftCol + 10f, barY + barH, codeNumPaint)

        } else {
            // Back Side
            var curY = 175f

            canvas.drawText("ISSUING AUTHORITY / MAMLAKA ILIYOIDHINISHA", 44f, curY, labelPaint)
            curY += 36f
            canvas.drawText(issuingAuthority.uppercase(), 44f, curY, bodyPaint)

            curY += 60f
            canvas.drawText("TERMS & CONDITIONS OF USE", 44f, curY, labelPaint)
            curY += 32f
            val noticePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(82, 82, 91)
                textSize = 21f
            }
            canvas.drawText(cardNotice, 44f, curY, noticePaint)

            curY += 60f
            canvas.drawText("HOLDER IDENTIFIER", 44f, curY, labelPaint)
            curY += 34f
            canvas.drawText("$fullName • ID: $idNumber", 44f, curY, bodyPaint)

            // Signature Box
            val sigRect = RectF(44f, 440f, 440f, 570f)
            val sigBg = Paint().apply { color = android.graphics.Color.rgb(250, 250, 250) }
            canvas.drawRoundRect(sigRect, 8f, 8f, sigBg)
            val sigBorder = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = android.graphics.Color.rgb(212, 212, 216)
            }
            canvas.drawRoundRect(sigRect, 8f, 8f, sigBorder)

            // Signature line
            val sigLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(30, 58, 138)
                strokeWidth = 2.5f
            }
            canvas.drawLine(64f, 510f, 180f, 480f, sigLine)
            canvas.drawLine(180f, 480f, 260f, 520f, sigLine)
            canvas.drawLine(260f, 520f, 400f, 490f, sigLine)

            val sigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.GRAY
                textSize = 15f
            }
            canvas.drawText("AUTHORIZED SIGNATURE / SAHIHI YA MMILIKI", 60f, 555f, sigText)

            // QR Code Matrix Box on Right
            val qrRect = RectF(740f, 400f, 960f, 580f)
            canvas.drawRoundRect(qrRect, 8f, 8f, sigBg)
            canvas.drawRoundRect(qrRect, 8f, 8f, sigBorder)
            val qrPaint = Paint().apply { color = android.graphics.Color.BLACK }
            val qSize = 6
            val qw = (qrRect.width() - 16f) / qSize
            val qh = (qrRect.height() - 16f) / qSize
            for (r in 0 until qSize) {
                for (c in 0 until qSize) {
                    if ((r * c + r + c) % 2 == 0) {
                        canvas.drawRect(qrRect.left + 8f + c * qw + 1f, qrRect.top + 8f + r * qh + 1f, qrRect.left + 8f + (c + 1) * qw - 1f, qrRect.top + 8f + (r + 1) * qh - 1f, qrPaint)
                    }
                }
            }
        }

        return bitmap
    }

    fun renderAndSaveSingleCard(side: String): File? {
        return runCatching {
            val bitmap = renderCard(side)
            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, "bigsta_${selectedCategory.lowercase().replace(" ", "_")}_${side}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = file.name,
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = 1,
                sourceType = "ID_CARD"
            )
            file
        }.getOrNull()
    }

    fun renderAndSaveMergedSheet(): File? {
        return runCatching {
            val front = renderCard("front")
            val back = renderCard("back")

            // Merged double CR80 print sheet (vertical stack)
            val sheetWidth = 1012
            val sheetHeight = 638 * 2 + 60
            val mergedBmp = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mergedBmp)
            canvas.drawColor(android.graphics.Color.rgb(244, 244, 245))

            canvas.drawBitmap(front, 0f, 0f, null)
            canvas.drawBitmap(back, 0f, 638f + 60f, null)

            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, "bigsta_${selectedCategory.lowercase().replace(" ", "_")}_merged_sheet_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                mergedBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = file.name,
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = 2,
                sourceType = "ID_CARD"
            )
            file
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ID Card Studio", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("$selectedCategory • Merging & Verification", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            val file = lastExportedFile ?: renderAndSaveSingleCard(selectedSide)
                            if (file != null) {
                                lastExportedFile = file
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Exported ID Card"))
                            }
                        },
                        modifier = Modifier.testTag("id_card_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share card")
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
            // Category Selector
            Text("Document Category", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }

            // Independent Front / Back Selector with Flip action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    listOf("front", "back").forEachIndexed { index, side ->
                        SegmentedButton(
                            selected = selectedSide == side,
                            onClick = { selectedSide = side },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                            modifier = Modifier.testTag("card_side_$side")
                        ) {
                            Text("${side.replaceFirstChar { it.uppercase() }} Side")
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { selectedSide = if (selectedSide == "front") "back" else "front" }) {
                    Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Card")
                }
            }

            // Live CR80 Card Preview
            val previewBitmap = remember(
                selectedCategory,
                selectedSide,
                fullName,
                idNumber,
                dateOfBirth,
                gender,
                nationality,
                bloodGroup,
                licenceClasses,
                expiryDate,
                facilityCode,
                memberType,
                nrcProvince,
                photoBitmap,
                issuingAuthority,
                cardNotice
            ) {
                renderCard(selectedSide)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "CR80 Live Render",
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (!exportFeedback.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = exportFeedback.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // Card Layer Fields
            SolidSectionCard {
                Text("$selectedCategory Data Mapping", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name (Jina Kamili)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    label = {
                        Text(when (selectedCategory) {
                            "Driving Licence" -> "Licence Reference Number"
                            "NHIF" -> "NHIF Membership Number"
                            "NRC Zambia" -> "NRC Number (e.g. 123456/12/1)"
                            else -> "NIDA National ID Number"
                        })
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        label = { Text("Date of Birth") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = gender,
                        onValueChange = { gender = it },
                        label = { Text("Gender") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectedCategory == "Driving Licence") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = bloodGroup,
                            onValueChange = { bloodGroup = it },
                            label = { Text("Blood Group") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = licenceClasses,
                            onValueChange = { licenceClasses = it },
                            label = { Text("Classes (A,B,C,D,E)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { expiryDate = it },
                        label = { Text("Expiry Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedCategory == "NHIF") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = facilityCode,
                        onValueChange = { facilityCode = it },
                        label = { Text("Designated Health Facility") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memberType,
                        onValueChange = { memberType = it },
                        label = { Text("Membership Scheme") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedCategory == "NRC Zambia") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nrcProvince,
                        onValueChange = { nrcProvince = it },
                        label = { Text("Province / District of Issue") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (selectedSide == "back") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = issuingAuthority,
                        onValueChange = { issuingAuthority = it },
                        label = { Text("Issuing Authority") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cardNotice,
                        onValueChange = { cardNotice = it },
                        label = { Text("Back Notice / Terms") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoBitmap == null) "Select Passport Photo (Photo Picker)" else "Change Passport Photo")
                }
            }

            // Export Actions
            Button(
                onClick = {
                    val saved = renderAndSaveSingleCard(selectedSide)
                    if (saved != null) {
                        lastExportedFile = saved
                        exportFeedback = "Exported ${saved.name} (${saved.length() / 1024} KB) to Downloads."
                    } else {
                        exportFeedback = "Could not render card image."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("id_card_export_btn")
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Render & Save PNG (${selectedSide.uppercase()} SIDE)")
            }

            OutlinedButton(
                onClick = {
                    val saved = renderAndSaveMergedSheet()
                    if (saved != null) {
                        lastExportedFile = saved
                        exportFeedback = "Exported Dual Merged Sheet: ${saved.name} (${saved.length() / 1024} KB) to Downloads."
                    } else {
                        exportFeedback = "Could not render merged sheet."
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("id_card_merge_btn")
            ) {
                Icon(Icons.Default.ViewAgenda, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Merged Dual-Side Print Sheet (Front + Back)")
            }
        }
    }
}
