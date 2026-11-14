package com.example.features.user

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateStudioEditorScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var templateName by remember { mutableStateOf("Custom National ID Template") }
    var categoryId by remember { mutableStateOf("NIDA") }
    var side by remember { mutableStateOf("front") }
    var selectedFont by remember { mutableStateOf("Sans-Serif") }
    var primaryColorHex by remember { mutableStateOf("#18181B") }
    var applicantNameText by remember { mutableStateOf("JOHN DOE") }
    var idNumberText by remember { mutableStateOf("19900101-12345-00001-12") }
    var dobText by remember { mutableStateOf("01/01/1990") }
    var customBadgeText by remember { mutableStateOf("JAMHURI YA MUUNGANO WA TANZANIA") }

    var badgePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var savedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) badgePhotoUri = uri
    }

    fun renderCardBitmap(): Bitmap {
        val width = 1012
        val height = 638
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F4F4F5"))

        val paint = Paint().apply { isAntiAlias = true }

        // Header Banner
        paint.color = Color.parseColor(primaryColorHex)
        canvas.drawRect(0f, 0f, width.toFloat(), 110f, paint)

        paint.color = Color.WHITE
        paint.textSize = 28f
        paint.isFakeBoldText = true
        canvas.drawText(customBadgeText.uppercase(), 40f, 65f, paint)

        paint.textSize = 20f
        paint.color = Color.parseColor("#E4E4E7")
        canvas.drawText("TEMPLATE STUDIO EDITOR • SIDE: ${side.uppercase()}", 40f, 95f, paint)

        // Photo Box Placeholder / Image
        val photoLeft = 40f
        val photoTop = 150f
        val photoRight = 280f
        val photoBottom = 500f
        paint.color = Color.parseColor("#E4E4E7")
        canvas.drawRect(photoLeft, photoTop, photoRight, photoBottom, paint)
        paint.color = Color.GRAY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawRect(photoLeft, photoTop, photoRight, photoBottom, paint)
        paint.style = Paint.Style.FILL

        if (badgePhotoUri != null) {
            runCatching {
                context.contentResolver.openInputStream(badgePhotoUri!!)?.use { stream ->
                    val decoded = android.graphics.BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        val scaled = Bitmap.createScaledBitmap(decoded, (photoRight - photoLeft).toInt(), (photoBottom - photoTop).toInt(), true)
                        canvas.drawBitmap(scaled, photoLeft, photoTop, paint)
                    }
                }
            }
        } else {
            paint.color = Color.DKGRAY
            paint.textSize = 22f
            canvas.drawText("No Photo", photoLeft + 70f, photoTop + 180f, paint)
        }

        // Details Text
        val textLeft = 320f
        var currentY = 190f

        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("FULL NAME / JINA LA KWANZA:", textLeft, currentY, paint)
        currentY += 32f
        paint.textSize = 34f
        paint.isFakeBoldText = true
        paint.color = Color.parseColor(primaryColorHex)
        canvas.drawText(applicantNameText.ifBlank { "FULL NAME" }, textLeft, currentY, paint)

        currentY += 60f
        paint.color = Color.DKGRAY
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("NATIONAL ID / NAMBA YA KITAMBULISHO:", textLeft, currentY, paint)
        currentY += 32f
        paint.textSize = 30f
        paint.isFakeBoldText = true
        paint.color = Color.BLACK
        canvas.drawText(idNumberText.ifBlank { "ID NUMBER" }, textLeft, currentY, paint)

        currentY += 60f
        paint.color = Color.DKGRAY
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("DATE OF BIRTH / TAREHE YA KUZALIWA:", textLeft, currentY, paint)
        currentY += 32f
        paint.textSize = 26f
        paint.isFakeBoldText = true
        canvas.drawText(dobText, textLeft, currentY, paint)

        return bitmap
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Template Studio Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val bmp = renderCardBitmap()
                            savedBitmap = bmp
                            runCatching {
                                val file = File(context.cacheDir, "custom_template_${System.currentTimeMillis()}.png")
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
                                statusMessage = "Template rendered and saved to local downloads!"
                            }.onFailure {
                                statusMessage = "Export failed: ${it.localizedMessage}"
                            }
                        },
                        modifier = Modifier.testTag("editor_save_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save & Export Template")
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
                        Text(statusMessage.orEmpty(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Live Preview Card
            item {
                SolidSectionCard {
                    Text("Live Template Preview (${side.uppercase()})", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    val previewBmp = remember(applicantNameText, idNumberText, dobText, primaryColorHex, side, badgePhotoUri, customBadgeText) {
                        renderCardBitmap()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2100.dp / 5f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = previewBmp.asImageBitmap(),
                            contentDescription = "Template Preview",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Side Selector & Category
            item {
                SolidSectionCard {
                    Text("Card Properties & Side", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("front", "back").forEach { s ->
                            FilterChip(
                                selected = side == s,
                                onClick = { side = s },
                                label = { Text("Side: ${s.uppercase()}") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Template Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Text & Badge Layers
            item {
                SolidSectionCard {
                    Text("Text & Element Layers", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customBadgeText,
                        onValueChange = { customBadgeText = it },
                        label = { Text("Header Badge Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = applicantNameText,
                        onValueChange = { applicantNameText = it },
                        label = { Text("Applicant Full Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idNumberText,
                        onValueChange = { idNumberText = it },
                        label = { Text("National ID Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dobText,
                        onValueChange = { dobText = it },
                        label = { Text("Date of Birth") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Photo Layer
            item {
                SolidSectionCard {
                    Text("Photo Layer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (badgePhotoUri == null) "Select Badge Photo" else "Change Badge Photo")
                    }
                }
            }
        }
    }
}
