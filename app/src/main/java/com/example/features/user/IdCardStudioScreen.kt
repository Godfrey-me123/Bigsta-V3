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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
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

    val categories = listOf("NIDA", "Driving Licence", "NHIF", "NRC Zambia", "Passport Badge")
    var selectedCategory by remember { mutableStateOf("NIDA") }
    var selectedSide by remember { mutableStateOf("front") } // Independent front/back side state

    var fullName by remember { mutableStateOf("Amina Juma") }
    var idNumber by remember { mutableStateOf("19950612-14102-00001-22") }
    var dateOfBirth by remember { mutableStateOf("1995-06-12") }
    var issuingAuthority by remember { mutableStateOf("National Identification Authority") }
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

    fun renderAndSaveCard(): File? {
        return runCatching {
            val width = 1012
            val height = 638
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Clean solid card surface
            canvas.drawColor(android.graphics.Color.WHITE)

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(24, 24, 27)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), 110f, headerPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 34f
                isFakeBoldText = true
            }
            canvas.drawText(
                "${selectedCategory.uppercase()} — ${selectedSide.uppercase()}",
                36f,
                68f,
                titlePaint
            )

            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(20, 20, 20)
                textSize = 32f
                isFakeBoldText = true
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(90, 90, 90)
                textSize = 24f
            }

            if (selectedSide == "front") {
                val photoRect = RectF(36f, 150f, 276f, 440f)
                val photoBg = Paint().apply { color = android.graphics.Color.rgb(230, 230, 235) }
                canvas.drawRoundRect(photoRect, 14f, 14f, photoBg)
                photoBitmap?.let { bmp ->
                    canvas.drawBitmap(bmp, null, photoRect, null)
                }
                canvas.drawText("FULL NAME", 316f, 185f, labelPaint)
                canvas.drawText(fullName, 316f, 228f, bodyPaint)
                canvas.drawText("DOCUMENT / ID NUMBER", 316f, 295f, labelPaint)
                canvas.drawText(idNumber, 316f, 338f, bodyPaint)
                canvas.drawText("DATE OF BIRTH", 316f, 405f, labelPaint)
                canvas.drawText(dateOfBirth, 316f, 448f, bodyPaint)
            } else {
                canvas.drawText("ISSUING AUTHORITY", 44f, 195f, labelPaint)
                canvas.drawText(issuingAuthority, 44f, 240f, bodyPaint)
                canvas.drawText("CARD REFERENCE", 44f, 315f, labelPaint)
                canvas.drawText(idNumber, 44f, 360f, bodyPaint)
                canvas.drawText("HOLDER: $fullName", 44f, 450f, labelPaint)
            }

            val dir = File(context.filesDir, "exports").apply { mkdirs() }
            val file = File(dir, "bigsta_${selectedCategory.lowercase().replace(" ", "_")}_${selectedSide}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = file.name,
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = null,
                sourceType = "ID_CARD"
            )
            file
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ID Card Designer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val file = lastExportedFile ?: renderAndSaveCard()
                            if (file != null) {
                                lastExportedFile = file
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ID Card"))
                            }
                        },
                        modifier = Modifier.testTag("id_card_share_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share exported card")
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

            // Independent Front / Back Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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

            // Live Solid Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(225.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                if (!activeTemplate?.backgroundUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = activeTemplate?.backgroundUrl,
                        contentDescription = "Template background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${selectedCategory.uppercase()} • ${selectedSide.uppercase()}",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (selectedSide == "front") {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp, 92.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                val bmp = photoBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Applicant photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Default.Person, contentDescription = "Photo placeholder")
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("NAME", fontSize = 10.sp, color = Color.Gray)
                                Text(fullName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                                Text("ID NUMBER", fontSize = 10.sp, color = Color.Gray)
                                Text(idNumber, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Black)
                                Text("DOB: $dateOfBirth", fontSize = 12.sp, color = Color.DarkGray)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("ISSUING AUTHORITY", fontSize = 10.sp, color = Color.Gray)
                                Text(issuingAuthority, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                            }
                            Column {
                                Text("HOLDER REFERENCE", fontSize = 10.sp, color = Color.Gray)
                                Text("$fullName • $idNumber", fontSize = 12.sp, color = Color.DarkGray)
                            }
                            Text(
                                activeTemplate?.name ?: "Standard Back Layout",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
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

            SolidSectionCard {
                Text("Card Layer Fields", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    label = { Text("ID / Reference Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedSide == "back") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = issuingAuthority,
                        onValueChange = { issuingAuthority = it },
                        label = { Text("Issuing Authority") },
                        singleLine = true,
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
                    Text("Choose Passport Photo (Photo Picker)")
                }
            }

            Button(
                onClick = {
                    val saved = renderAndSaveCard()
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
                Text("Render & Save PNG (${selectedSide.uppercase()})")
            }
        }
    }
}
