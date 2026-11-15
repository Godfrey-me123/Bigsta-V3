package com.example.features.user

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BigstaSuccessGreen
import java.io.File
import java.io.FileOutputStream

data class NativeVehicleClass(
    val code: String,
    val description: String,
    val enabled: Boolean = false,
    val issueDate: String = "",
    val expiryDate: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernmentFormsScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit,
    initialServiceType: String = "NIDA",
    onOpenStudio: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val formTypes = listOf("NIDA", "Driving Licence", "NHIF", "NRC Zambia")
    val normalizedInitialType = remember(initialServiceType) {
        when (initialServiceType.trim().lowercase().replace("%20", " ")) {
            "driving licence", "driving license" -> "Driving Licence"
            "nhif" -> "NHIF"
            "nrc zambia" -> "NRC Zambia"
            else -> "NIDA"
        }
    }
    var selectedType by remember { mutableStateOf(normalizedInitialType) }

    // Common fields
    var firstName by remember { mutableStateOf("") }
    var middleName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("MALE") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }

    // Service-specific fields
    var district by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    val defaultVehicleClasses = remember {
        listOf(
            NativeVehicleClass("A", "Motorcycles & Tricycles"),
            NativeVehicleClass("A1", "Light Motorcycles"),
            NativeVehicleClass("A2", "Heavy Motorcycles"),
            NativeVehicleClass("A3", "Tricycles / Bajaj"),
            NativeVehicleClass("B", "Private Passenger Vehicles", enabled = true, issueDate = "2022-01-15", expiryDate = "2027-01-15"),
            NativeVehicleClass("C", "Public Service Vehicles (PSV)"),
            NativeVehicleClass("C1", "Light Bus"),
            NativeVehicleClass("C2", "Medium Bus"),
            NativeVehicleClass("C3", "Heavy Bus"),
            NativeVehicleClass("D", "Heavy Goods Vehicles (Trucks)"),
            NativeVehicleClass("E", "Articulated / Combination Vehicles"),
            NativeVehicleClass("F", "Tractors & Agricultural"),
            NativeVehicleClass("G", "Special Purpose & Construction")
        )
    }
    var drivingClasses by remember { mutableStateOf(defaultVehicleClasses) }
    val selectedDrivingClasses by remember(drivingClasses) {
        derivedStateOf { drivingClasses.filter { it.enabled }.map { it.code }.toSet() }
    }
    var bloodGroup by remember { mutableStateOf("O+") }
    var issuingAuthority by remember { mutableStateOf("TRA / Police") }
    var nhifFacility by remember { mutableStateOf("") }
    var nhifStatus by remember { mutableStateOf("Active") }
    var nrcProvince by remember { mutableStateOf("Lusaka") }
    var nrcChief by remember { mutableStateOf("") }
    var nrcVillage by remember { mutableStateOf("") }
    var nrcParentsPlaceOfBirth by remember { mutableStateOf("") }
    var nrcRegistrationDate by remember { mutableStateOf("") }
    var nrcRegNumber by remember { mutableStateOf("") }
    var placeOfBirth by remember { mutableStateOf("") }
    var villageChief by remember { mutableStateOf("") }
    var specialMarks by remember { mutableStateOf("None") }

    // Biometrics: Photo & Native Signature Pad
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoLabel by remember { mutableStateOf<String?>(null) }
    var termsAccepted by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var cardPreviewSide by remember { mutableStateOf("front") }

    // Signature path strokes
    val signatureLines = remember { mutableStateListOf<List<Offset>>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    photoBytes = bytes
                    photoBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    photoLabel = "Biometric Photo Selected (${bytes.size / 1024} KB)"
                }
            }
        }
    }

    fun loadDemoData() {
        when (selectedType) {
            "NIDA" -> {
                firstName = "Amina"
                middleName = "Juma"
                lastName = "Hassan"
                gender = "FEMALE"
                dateOfBirth = "1995-06-12"
                nationalId = "19950612141020000122"
                district = "Ilala, Dar es Salaam"
                phone = "+255712345678"
            }
            "Driving Licence" -> {
                firstName = "Godfrey"
                middleName = "Elias"
                lastName = "Mrema"
                gender = "MALE"
                dateOfBirth = "1991-03-24"
                nationalId = "DL-TZA-2024-88910"
                issueDate = "2022-01-15"
                expiryDate = "2027-01-15"
                drivingClasses = drivingClasses.map {
                    if (it.code in setOf("A", "B", "C")) {
                        it.copy(enabled = true, issueDate = "2022-01-15", expiryDate = "2027-01-15")
                    } else {
                        it.copy(enabled = false)
                    }
                }
                bloodGroup = "A+"
                issuingAuthority = "TRA Mainland"
                phone = "+255754987654"
            }
            "NHIF" -> {
                firstName = "Neema"
                middleName = "David"
                lastName = "Mallya"
                gender = "FEMALE"
                dateOfBirth = "1988-11-04"
                nationalId = "102847593021"
                nhifStatus = "Active"
                nhifFacility = "Muhimbili National Hospital (MNH-01)"
                phone = "+255788112233"
            }
            "NRC Zambia" -> {
                firstName = "Chileshe"
                middleName = "Mwamba"
                lastName = "Banda"
                gender = "MALE"
                dateOfBirth = "1994-08-19"
                nationalId = "481920/11/1"
                nrcRegNumber = "LUS/2021/0491"
                placeOfBirth = "UTH, Lusaka"
                nrcParentsPlaceOfBirth = "Kasama, Northern Province"
                district = "Lusaka"
                nrcProvince = "Lusaka"
                nrcChief = "Senior Chief Mwamba"
                nrcVillage = "Kabwata"
                nrcRegistrationDate = "2010-09-18"
                issueDate = "2021-05-10"
                specialMarks = "None"
                phone = "+260971234567"
            }
        }
    }

    val fullName = listOf(firstName, middleName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    fun renderLiveCardBitmap(side: String): Bitmap {
        val width = 1012
        val height = 638
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(cardRect, 32f, 32f, bgPaint)

        // Banner
        val headerPaint = Paint().apply {
            color = when (selectedType) {
                "Driving Licence" -> Color.rgb(20, 83, 45) // Dark Green
                "NHIF" -> Color.rgb(2, 132, 199) // Sky Blue
                "NRC Zambia" -> Color.rgb(180, 83, 9) // Amber/Bronze
                else -> Color.rgb(30, 58, 138) // Deep Blue NIDA
            }
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 120f, headerPaint)

        val accentPaint = Paint().apply {
            color = Color.rgb(234, 179, 8) // Gold Accent Line
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRect(0f, 120f, width.toFloat(), 128f, accentPaint)

        val textPaint = Paint().apply { isAntiAlias = true }

        // Header Title
        textPaint.color = Color.WHITE
        textPaint.textSize = 28f
        textPaint.isFakeBoldText = true
        val headerTitle = when (selectedType) {
            "Driving Licence" -> "JAMHURI YA MUUNGANO WA TANZANIA • DRIVING LICENCE"
            "NHIF" -> "NATIONAL HEALTH INSURANCE FUND • KITAMBULISHO CHA BIMA"
            "NRC Zambia" -> "REPUBLIC OF ZAMBIA • NATIONAL REGISTRATION CARD"
            else -> "JAMHURI YA MUUNGANO WA TANZANIA • NIDA"
        }
        canvas.drawText(headerTitle, 36f, 68f, textPaint)

        val borderPaint = Paint().apply {
            color = Color.rgb(228, 228, 231)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, 32f, 32f, borderPaint)

        if (side == "front") {
            // Photo Box
            val photoRect = RectF(40f, 150f, 280f, 490f)
            val pBoxPaint = Paint().apply {
                color = Color.rgb(244, 244, 245)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(photoRect, 16f, 16f, pBoxPaint)

            if (photoBitmap != null) {
                val scaledPhoto = Bitmap.createScaledBitmap(photoBitmap!!, photoRect.width().toInt(), photoRect.height().toInt(), true)
                canvas.drawBitmap(scaledPhoto, photoRect.left, photoRect.top, null)
            } else {
                textPaint.color = Color.GRAY
                textPaint.textSize = 18f
                textPaint.isFakeBoldText = false
                canvas.drawText("PASSPORT PHOTO", photoRect.left + 24f, photoRect.centerY(), textPaint)
            }

            // Cardholder Details
            textPaint.color = Color.rgb(24, 24, 27)
            textPaint.textSize = 32f
            textPaint.isFakeBoldText = true
            canvas.drawText(fullName.ifBlank { "FULL NAME" }, 310f, 210f, textPaint)

            textPaint.color = Color.rgb(113, 113, 122)
            textPaint.textSize = 20f
            textPaint.isFakeBoldText = false
            canvas.drawText("DOCUMENT ID NUMBER", 310f, 255f, textPaint)

            textPaint.color = Color.rgb(30, 58, 138)
            textPaint.textSize = 30f
            textPaint.isFakeBoldText = true
            canvas.drawText(nationalId.ifBlank { "XXXX-XXXX-XXXX" }, 310f, 295f, textPaint)

            textPaint.color = Color.rgb(24, 24, 27)
            textPaint.textSize = 22f
            textPaint.isFakeBoldText = false
            canvas.drawText("DOB: ${dateOfBirth.ifBlank { "YYYY-MM-DD" }}   SEX: $gender", 310f, 350f, textPaint)

            when (selectedType) {
                "Driving Licence" -> {
                    canvas.drawText("CLASSES: ${selectedDrivingClasses.sorted().joinToString(", ")}   BLOOD: $bloodGroup", 310f, 400f, textPaint)
                    canvas.drawText("EXPIRY: ${expiryDate.ifBlank { "2027-01-15" }}   AUTH: $issuingAuthority", 310f, 450f, textPaint)
                }
                "NHIF" -> {
                    canvas.drawText("FACILITY: ${nhifFacility.ifBlank { "Designated Clinic" }}", 310f, 400f, textPaint)
                    canvas.drawText("STATUS: $nhifStatus", 310f, 450f, textPaint)
                }
                "NRC Zambia" -> {
                    canvas.drawText("PROVINCE: $nrcProvince   DISTRICT: ${district.ifBlank { "Lusaka" }}", 310f, 400f, textPaint)
                    canvas.drawText("CHIEF: ${nrcChief.ifBlank { "Mwamba" }}   VILLAGE: ${nrcVillage.ifBlank { "Kabwata" }}", 310f, 440f, textPaint)
                    canvas.drawText("REG DATE: ${nrcRegistrationDate.ifBlank { "2010-09-18" }}", 310f, 475f, textPaint)
                }
                else -> {
                    canvas.drawText("DISTRICT: ${district.ifBlank { "Dar es Salaam" }}", 310f, 400f, textPaint)
                }
            }

            // Draw Holder Signature Box
            val sigRect = RectF(310f, 505f, 620f, 600f)
            val sigBoxPaint = Paint().apply {
                color = Color.rgb(244, 244, 245)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(sigRect, 8f, 8f, sigBoxPaint)

            val sigStrokePaint = Paint().apply {
                color = Color.rgb(30, 58, 138)
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }

            if (signatureLines.isNotEmpty()) {
                val scaleX = sigRect.width() / 300f
                val scaleY = sigRect.height() / 120f
                signatureLines.forEach { line ->
                    for (i in 0 until line.size - 1) {
                        val p1 = line[i]
                        val p2 = line[i + 1]
                        canvas.drawLine(
                            sigRect.left + (p1.x * scaleX),
                            sigRect.top + (p1.y * scaleY),
                            sigRect.left + (p2.x * scaleX),
                            sigRect.top + (p2.y * scaleY),
                            sigStrokePaint
                        )
                    }
                }
            } else {
                textPaint.color = Color.GRAY
                textPaint.textSize = 14f
                textPaint.isFakeBoldText = false
                canvas.drawText("HOLDER SIGNATURE", sigRect.left + 16f, sigRect.centerY() + 5f, textPaint)
            }
        } else {
            // Back Side
            if (selectedType == "Driving Licence") {
                textPaint.color = Color.rgb(20, 83, 45)
                textPaint.textSize = 22f
                textPaint.isFakeBoldText = true
                canvas.drawText("AUTHORISED VEHICLE CATEGORIES (CLASSES TABLE)", 40f, 175f, textPaint)

                // Table Header Bar
                val tableHeaderPaint = Paint().apply {
                    color = Color.rgb(240, 243, 240)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRoundRect(RectF(40f, 195f, 972f, 235f), 6f, 6f, tableHeaderPaint)

                textPaint.color = Color.rgb(20, 83, 45)
                textPaint.textSize = 14f
                textPaint.isFakeBoldText = true
                canvas.drawText("CLASS", 55f, 222f, textPaint)
                canvas.drawText("VEHICLE CATEGORY DESCRIPTION", 145f, 222f, textPaint)
                canvas.drawText("VALID FROM", 620f, 222f, textPaint)
                canvas.drawText("VALID UNTIL", 790f, 222f, textPaint)

                val activeClasses = drivingClasses.filter { it.enabled }.take(6)
                var rowY = 265f
                val divPaint = Paint().apply {
                    color = Color.rgb(228, 228, 231)
                    strokeWidth = 1.5f
                    style = Paint.Style.STROKE
                }

                if (activeClasses.isEmpty()) {
                    textPaint.color = Color.GRAY
                    textPaint.isFakeBoldText = false
                    textPaint.textSize = 15f
                    canvas.drawText("No specific vehicle classes authorized yet.", 55f, 275f, textPaint)
                } else {
                    activeClasses.forEach { cls ->
                        textPaint.isFakeBoldText = true
                        textPaint.color = Color.rgb(20, 83, 45)
                        textPaint.textSize = 15f
                        canvas.drawText("Class ${cls.code}", 55f, rowY, textPaint)

                        textPaint.isFakeBoldText = false
                        textPaint.color = Color.rgb(39, 39, 42)
                        textPaint.textSize = 13.5f
                        canvas.drawText(cls.description.take(38), 145f, rowY, textPaint)
                        canvas.drawText(cls.issueDate.ifBlank { issueDate.ifBlank { "2022-01-15" } }, 620f, rowY, textPaint)
                        canvas.drawText(cls.expiryDate.ifBlank { expiryDate.ifBlank { "2027-01-15" } }, 790f, rowY, textPaint)

                        canvas.drawLine(40f, rowY + 12f, 972f, rowY + 12f, divPaint)
                        rowY += 36f
                    }
                }
            } else if (selectedType == "NRC Zambia") {
                textPaint.color = Color.rgb(180, 83, 9)
                textPaint.textSize = 22f
                textPaint.isFakeBoldText = true
                canvas.drawText("NATIONAL REGISTRATION RECORD (CAP 126)", 40f, 180f, textPaint)

                textPaint.textSize = 17f
                textPaint.isFakeBoldText = false
                textPaint.color = Color.rgb(39, 39, 42)
                canvas.drawText("PLACE OF BIRTH: ${placeOfBirth.ifBlank { "Lusaka, Zambia" }}", 40f, 230f, textPaint)
                canvas.drawText("PARENTS PLACE OF BIRTH: ${nrcParentsPlaceOfBirth.ifBlank { "Kasama, Northern Province" }}", 40f, 270f, textPaint)
                canvas.drawText("DISTRICT REGISTRAR OFFICE: ${district.ifBlank { "Lusaka District" }}", 40f, 310f, textPaint)
                canvas.drawText("RECORD ENTRY REF: ${nrcRegNumber.ifBlank { "LUS/2021/0491" }}", 40f, 350f, textPaint)

                textPaint.textSize = 20f
                textPaint.isFakeBoldText = true
                textPaint.color = Color.rgb(24, 24, 27)
                canvas.drawText("CONTACT / EMERGENCY: ${phone.ifBlank { "+260 97X XXX XXX" }}", 40f, 410f, textPaint)
            } else {
                textPaint.color = Color.rgb(24, 24, 27)
                textPaint.textSize = 24f
                textPaint.isFakeBoldText = true
                canvas.drawText("TERMS & CONDITIONS OF USE", 40f, 180f, textPaint)
            }

            textPaint.textSize = 18f
            textPaint.isFakeBoldText = false
            textPaint.color = Color.rgb(82, 82, 91)
            canvas.drawText("1. This document remains the property of the issuing Government authority.", 40f, 230f, textPaint)
            canvas.drawText("2. If lost or found, please return to the nearest administrative or police station.", 40f, 270f, textPaint)
            canvas.drawText("3. Alteration or unauthorized reproduction is a criminal offense.", 40f, 310f, textPaint)

            textPaint.textSize = 22f
            textPaint.isFakeBoldText = true
            textPaint.color = Color.rgb(24, 24, 27)
            canvas.drawText("CONTACT / EMERGENCY: ${phone.ifBlank { "+255 XXX XXX XXX" }}", 40f, 380f, textPaint)

            // Machine Readable Zone MRZ simulation
            textPaint.textSize = 24f
            textPaint.isFakeBoldText = true
            textPaint.color = Color.rgb(24, 24, 27)
            val cleanId = nationalId.filter { it.isLetterOrDigit() }.padEnd(12, '<')
            canvas.drawText("I<TZA$cleanId<<<<<<<<<<<<<<<<<<<", 40f, 520f, textPaint)
            canvas.drawText("8501018F2812312TZA<<<<<<<<<<<<<6", 40f, 570f, textPaint)
        }

        return bitmap
    }

    fun exportCardToDownloads(): File? {
        val bmp = renderLiveCardBitmap(cardPreviewSide)
        return runCatching {
            val file = File(context.cacheDir, "card_${selectedType.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            viewModel.recordLocalExport(
                id = "doc_${System.currentTimeMillis()}",
                fileName = file.name,
                mimeType = "image/png",
                localPath = file.absolutePath,
                sizeBytes = file.length(),
                pageCount = 1,
                sourceType = "GOVERNMENT_FORM"
            )
            file
        }.getOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Government ID & Forms", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("$selectedType Application", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { loadDemoData() }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Demo Fill", fontSize = 12.sp)
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
            // Feedback messages
            state.errorMessage?.let { err ->
                item {
                    ErrorMessageBanner(message = err)
                }
            }

            state.submitSuccessMessage?.let { msg ->
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
                            IconButton(onClick = { viewModel.clearFeedback() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            validationError?.let { err ->
                item {
                    ErrorMessageBanner(message = err)
                }
            }

            // Service Selector Chips
            item {
                SolidSectionCard {
                    Text("Select Government Service", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(formTypes) { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = {
                                    selectedType = type
                                    validationError = null
                                },
                                label = { Text(type, fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // Applicant Information
            item {
                SolidSectionCard {
                    Text("1. Applicant Identification", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it; validationError = null },
                            label = { Text("First Name *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = middleName,
                            onValueChange = { middleName = it },
                            label = { Text("Middle Name") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it; validationError = null },
                        label = { Text("Last Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Gender:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = gender == "MALE",
                            onClick = { gender = "MALE" },
                            label = { Text("Male") }
                        )
                        FilterChip(
                            selected = gender == "FEMALE",
                            onClick = { gender = "FEMALE" },
                            label = { Text("Female") }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it; validationError = null },
                        label = { Text("Date of Birth (YYYY-MM-DD) *") },
                        placeholder = { Text("e.g. 1995-06-12") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val idLabel = when (selectedType) {
                        "Driving Licence" -> "Licence Number / Application Ref *"
                        "NHIF" -> "NHIF Membership / Card Number (10-12 digits) *"
                        "NRC Zambia" -> "NRC Number (Format: XXXXXX/XX/X) *"
                        else -> "NIDA Number / NIN (20 digits or Ref) *"
                    }
                    OutlinedTextField(
                        value = nationalId,
                        onValueChange = { nationalId = it; validationError = null },
                        label = { Text(idLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; validationError = null },
                        label = { Text("Applicant Phone Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Service Specific Details
            item {
                SolidSectionCard {
                    Text("2. $selectedType Specifications", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedType) {
                        "NIDA" -> {
                            OutlinedTextField(
                                value = district,
                                onValueChange = { district = it },
                                label = { Text("District & Region of Residence") },
                                placeholder = { Text("e.g. Ilala, Dar es Salaam") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        "Driving Licence" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Vehicle Classes & Authorizations (${selectedDrivingClasses.size} Active):", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {
                                        drivingClasses = drivingClasses.map {
                                            if (it.enabled) it.copy(issueDate = issueDate, expiryDate = expiryDate) else it
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Copy Dates to All", fontSize = 11.sp)
                                }
                            }

                            // Quick Class Presets
                            Text("Quick Presets:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    AssistChip(
                                        onClick = {
                                            drivingClasses = drivingClasses.map {
                                                if (it.code in listOf("A", "A1", "A2", "A3")) it.copy(enabled = true, issueDate = issueDate, expiryDate = expiryDate) else it
                                            }
                                        },
                                        label = { Text("Motorcycles (A)", fontSize = 10.sp) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            drivingClasses = drivingClasses.map {
                                                if (it.code == "B") it.copy(enabled = true, issueDate = issueDate, expiryDate = expiryDate) else it
                                            }
                                        },
                                        label = { Text("Private Car (B)", fontSize = 10.sp) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            drivingClasses = drivingClasses.map {
                                                if (it.code in listOf("C", "C1", "C2")) it.copy(enabled = true, issueDate = issueDate, expiryDate = expiryDate) else it
                                            }
                                        },
                                        label = { Text("PSV/Bus (C)", fontSize = 10.sp) }
                                    )
                                }
                                item {
                                    AssistChip(
                                        onClick = {
                                            drivingClasses = drivingClasses.map {
                                                if (it.code in listOf("D", "E")) it.copy(enabled = true, issueDate = issueDate, expiryDate = expiryDate) else it
                                            }
                                        },
                                        label = { Text("Truck/Articulated (D,E)", fontSize = 10.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Interactive Classes Grid / List
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                drivingClasses.forEach { cls ->
                                    Surface(
                                        color = if (cls.enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (cls.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Checkbox(
                                                        checked = cls.enabled,
                                                        onCheckedChange = { checked ->
                                                            drivingClasses = drivingClasses.map {
                                                                if (it.code == cls.code) {
                                                                    it.copy(
                                                                        enabled = checked,
                                                                        issueDate = if (checked && it.issueDate.isBlank()) issueDate else it.issueDate,
                                                                        expiryDate = if (checked && it.expiryDate.isBlank()) expiryDate else it.expiryDate
                                                                    )
                                                                } else it
                                                            }
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text("Class ${cls.code}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(cls.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }

                                            if (cls.enabled) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    OutlinedTextField(
                                                        value = cls.issueDate,
                                                        onValueChange = { newDate ->
                                                            drivingClasses = drivingClasses.map {
                                                                if (it.code == cls.code) it.copy(issueDate = newDate) else it
                                                            }
                                                        },
                                                        label = { Text("Valid From", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    OutlinedTextField(
                                                        value = cls.expiryDate,
                                                        onValueChange = { newDate ->
                                                            drivingClasses = drivingClasses.map {
                                                                if (it.code == cls.code) it.copy(expiryDate = newDate) else it
                                                            }
                                                        },
                                                        label = { Text("Valid Until", fontSize = 11.sp) },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Blood Group:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            val bloodGroups = listOf("O+", "A+", "B+", "AB+", "O-", "A-", "B-", "AB-")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(bloodGroups) { bg ->
                                    FilterChip(
                                        selected = bloodGroup == bg,
                                        onClick = { bloodGroup = bg },
                                        label = { Text(bg) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = issueDate,
                                    onValueChange = { issueDate = it },
                                    label = { Text("Issue Date") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = expiryDate,
                                    onValueChange = { expiryDate = it },
                                    label = { Text("Expiry Date") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        "NHIF" -> {
                            OutlinedTextField(
                                value = nhifFacility,
                                onValueChange = { nhifFacility = it },
                                label = { Text("Designated Health Facility / Hospital *") },
                                placeholder = { Text("e.g. Muhimbili National Hospital") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Card Membership Status:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            val statuses = listOf("Active", "Dependent", "Renewal", "Expired")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                statuses.forEach { st ->
                                    FilterChip(
                                        selected = nhifStatus == st,
                                        onClick = { nhifStatus = st },
                                        label = { Text(st) }
                                    )
                                }
                            }
                        }

                        "NRC Zambia" -> {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = placeOfBirth,
                                    onValueChange = { placeOfBirth = it },
                                    label = { Text("Place of Birth") },
                                    placeholder = { Text("e.g. UTH, Lusaka") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = nrcParentsPlaceOfBirth,
                                    onValueChange = { nrcParentsPlaceOfBirth = it },
                                    label = { Text("Parents' Birthplace") },
                                    placeholder = { Text("e.g. Kasama") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val provinces = listOf("Lusaka", "Copperbelt", "Central", "Eastern", "Luapula", "Northern", "Southern", "Western", "North-Western", "Muchinga")
                            Text("Province of Registration:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(provinces) { prov ->
                                    FilterChip(
                                        selected = nrcProvince == prov,
                                        onClick = { nrcProvince = prov },
                                        label = { Text(prov) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val zambiaDistricts = listOf("Lusaka", "Ndola", "Kitwe", "Livingstone", "Kabwe", "Chingola", "Mufulira", "Kasama", "Chipata", "Solwezi", "Mongu", "Choma")
                            Text("District of Registration:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(zambiaDistricts) { dst ->
                                    FilterChip(
                                        selected = district == dst,
                                        onClick = { district = dst },
                                        label = { Text(dst) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = nrcChief,
                                    onValueChange = { nrcChief = it },
                                    label = { Text("Chief Authority") },
                                    placeholder = { Text("e.g. Senior Chief Mwamba") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = nrcVillage,
                                    onValueChange = { nrcVillage = it },
                                    label = { Text("Village / Town") },
                                    placeholder = { Text("e.g. Kabwata") },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = nrcRegistrationDate,
                                    onValueChange = { nrcRegistrationDate = it },
                                    label = { Text("Date of Registration") },
                                    placeholder = { Text("YYYY-MM-DD") },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = nrcRegNumber,
                                    onValueChange = { nrcRegNumber = it },
                                    label = { Text("Record Entry Ref") },
                                    placeholder = { Text("e.g. LUS/2021/0491") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Biometrics: Photo & Signature Pad
            item {
                SolidSectionCard {
                    Text("3. Biometrics & Digital Signature", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Passport Photo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Passport Photo (White Background)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(photoLabel ?: "No photo selected (optional for draft)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Native Touch Signature Pad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Applicant Signature Pad (Sign Below):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TextButton(
                            onClick = {
                                signatureLines.clear()
                                currentLine = emptyList()
                            }
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val strokeColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentLine = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentLine = currentLine + change.position
                                    },
                                    onDragEnd = {
                                        if (currentLine.isNotEmpty()) {
                                            signatureLines.add(currentLine)
                                            currentLine = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                            signatureLines.forEach { line ->
                                for (i in 0 until line.size - 1) {
                                    drawLine(
                                        color = strokeColor,
                                        start = line[i],
                                        end = line[i + 1],
                                        strokeWidth = 4f
                                    )
                                }
                            }
                            for (i in 0 until currentLine.size - 1) {
                                drawLine(
                                    color = strokeColor,
                                    start = currentLine[i],
                                    end = currentLine[i + 1],
                                    strokeWidth = 4f
                                )
                            }
                        }

                        if (signatureLines.isEmpty() && currentLine.isEmpty()) {
                            Text(
                                "Sign with finger or stylus here",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Terms Acceptance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = termsAccepted,
                            onCheckedChange = { termsAccepted = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "I confirm that all supplied information and biometrics are accurate and authorized.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Instant Card Preview (CR80)
            item {
                SolidSectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live CR80 Card Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        FilledTonalButton(
                            onClick = {
                                cardPreviewSide = if (cardPreviewSide == "front") "back" else "front"
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.FlipToBack, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (cardPreviewSide == "front") "View Back" else "View Front", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val cardBmp = remember(firstName, middleName, lastName, nationalId, dateOfBirth, gender, selectedType, photoBitmap, signatureLines.size, cardPreviewSide) {
                        renderLiveCardBitmap(cardPreviewSide)
                    }

                    Image(
                        bitmap = cardBmp.asImageBitmap(),
                        contentDescription = "Live Card Preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1012f / 638f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val file = exportCardToDownloads()
                                if (file != null) {
                                    validationError = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export PNG", fontSize = 12.sp)
                        }

                        if (onOpenStudio != null) {
                            OutlinedButton(
                                onClick = onOpenStudio,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit in Studio", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Submission Action
            item {
                Button(
                    onClick = {
                        if (firstName.isBlank() || lastName.isBlank()) {
                            validationError = "First name and last name are required."
                            return@Button
                        }
                        if (dateOfBirth.isBlank()) {
                            validationError = "Date of birth is required (YYYY-MM-DD)."
                            return@Button
                        }
                        if (nationalId.isBlank()) {
                            validationError = "National ID / Number is required."
                            return@Button
                        }
                        if (phone.isBlank()) {
                            validationError = "Contact phone number is required."
                            return@Button
                        }
                        if (!termsAccepted) {
                            validationError = "Please accept the terms and conditions checkbox."
                            return@Button
                        }

                        validationError = null
                        val rawData = buildMap {
                            put("service_type", selectedType)
                            put("first_name", firstName)
                            put("middle_name", middleName)
                            put("last_name", lastName)
                            put("full_name", fullName)
                            put("gender", gender)
                            put("dob", dateOfBirth)
                            put("national_id", nationalId)
                            put("phone", phone)
                            when (selectedType) {
                                "Driving Licence" -> {
                                    put("classes", selectedDrivingClasses.sorted().joinToString(","))
                                    val classesArray = JSONArray()
                                    drivingClasses.filter { it.enabled }.forEach { cls ->
                                        val cObj = JSONObject()
                                        cObj.put("class_code", cls.code)
                                        cObj.put("description", cls.description)
                                        cObj.put("issue_date", cls.issueDate.ifBlank { issueDate })
                                        cObj.put("expiry_date", cls.expiryDate.ifBlank { expiryDate })
                                        classesArray.put(cObj)
                                    }
                                    put("classes_table", classesArray)
                                    put("blood_group", bloodGroup)
                                    put("authority", issuingAuthority)
                                }
                                "NHIF" -> {
                                    put("facility", nhifFacility)
                                    put("status", nhifStatus)
                                }
                                "NRC Zambia" -> {
                                    put("province", nrcProvince)
                                    put("district", district)
                                    put("chief", nrcChief)
                                    put("village", nrcVillage)
                                    put("place_of_birth", placeOfBirth)
                                    put("parents_place_of_birth", nrcParentsPlaceOfBirth)
                                    put("registration_date", nrcRegistrationDate)
                                    put("entry_ref", nrcRegNumber)
                                }
                                else -> {
                                    put("district", district)
                                }
                            }
                        }

                        viewModel.submitGovernmentForm(
                            serviceType = selectedType,
                            formData = rawData,
                            photoBytes = photoBytes
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("gov_form_submit_btn"),
                    enabled = !state.isSubmitting
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submitting Application...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit $selectedType Application", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Application History Tracker
            item {
                SolidSectionCard {
                    Text("Your Service Applications (${state.userRequests.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.userRequests.isEmpty()) {
                        Text("No applications submitted yet. Completed forms appear here with real-time status updates.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.userRequests.take(5).forEach { req ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(req.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("${req.serviceType} • Ref: ${req.id.take(8)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        StatusBadge(status = req.status)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
