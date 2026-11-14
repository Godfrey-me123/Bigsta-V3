package com.example.features.user

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.SolidSectionCard
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageStudioScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var jpegQuality by remember { mutableFloatStateOf(85f) }
    var watermarkText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var lastSavedFile by remember { mutableStateOf<File?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    activeBitmap = BitmapFactory.decodeStream(stream)
                    statusText = activeBitmap?.let { "Loaded ${it.width}x${it.height} px" }
                    lastSavedFile = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image & Passport Studio") },
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
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share Edited Image"))
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share edited image")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("image_studio_pick_btn")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Photo (Android Photo Picker)")
            }

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
                        contentDescription = "Edited image preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "Choose an image to rotate, passport-crop, watermark, or compress.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            if (!statusText.isNullOrBlank()) {
                Text(statusText.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            SolidSectionCard {
                Text("Native Photo Operations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val bmp = activeBitmap ?: return@OutlinedButton
                            val matrix = Matrix().apply { postRotate(90f) }
                            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                            activeBitmap = rotated
                            statusText = "Rotated 90° (${rotated.width}x${rotated.height} px)"
                        },
                        enabled = activeBitmap != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rotate 90°")
                    }

                    OutlinedButton(
                        onClick = {
                            val bmp = activeBitmap ?: return@OutlinedButton
                            // Standard 35:45 passport portrait ratio crop
                            val targetRatio = 35f / 45f
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
                            statusText = "Passport 35x45mm crop (${cropped.width}x${cropped.height} px)"
                        },
                        enabled = activeBitmap != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Passport Crop")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Optional Watermark Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Compression Quality: ${jpegQuality.toInt()}%", fontSize = 13.sp)
                Slider(
                    value = jpegQuality,
                    onValueChange = { jpegQuality = it },
                    valueRange = 20f..100f
                )
            }

            Button(
                onClick = {
                    val source = activeBitmap ?: return@Button
                    val outputBmp = source.copy(Bitmap.Config.ARGB_8888, true)
                    if (watermarkText.isNotBlank()) {
                        val canvas = Canvas(outputBmp)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(170, 255, 255, 255)
                            textSize = (outputBmp.width * 0.055f).coerceAtLeast(24f)
                            isFakeBoldText = true
                        }
                        canvas.drawText(
                            watermarkText,
                            24f,
                            (outputBmp.height - 32).toFloat(),
                            paint
                        )
                    }
                    val baos = ByteArrayOutputStream()
                    outputBmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality.toInt(), baos)
                    val bytes = baos.toByteArray()
                    val dir = File(context.filesDir, "exports").apply { mkdirs() }
                    val file = File(dir, "bigsta_img_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { it.write(bytes) }
                    lastSavedFile = file
                    statusText = "Saved ${file.name} (${bytes.size / 1024} KB)"
                    viewModel.recordLocalExport(
                        id = file.name,
                        fileName = file.name,
                        mimeType = "image/jpeg",
                        localPath = file.absolutePath,
                        sizeBytes = file.length(),
                        pageCount = null,
                        sourceType = "IMAGE_EXPORT"
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
                Text("Save Compressed JPEG to Downloads")
            }
        }
    }
}
