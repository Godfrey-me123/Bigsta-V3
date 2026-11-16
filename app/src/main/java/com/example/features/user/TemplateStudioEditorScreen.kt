package com.example.features.user

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.Rect
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


data class TokenDefinition(
    val token: String,
    val label: String,
    val category: String
)

val allStudioTokens = listOf(
    // Common / Personal
    TokenDefinition("{{full_name}}", "Full Name", "Common"),
    TokenDefinition("{{first_name}}", "First Name", "Common"),
    TokenDefinition("{{middle_name}}", "Middle Name", "Common"),
    TokenDefinition("{{last_name}}", "Last Name", "Common"),
    TokenDefinition("{{id_number}}", "Document ID Number", "Common"),
    TokenDefinition("{{dob}}", "Date of Birth", "Common"),
    TokenDefinition("{{gender}}", "Sex / Gender", "Common"),
    TokenDefinition("{{phone}}", "Phone Number", "Common"),
    TokenDefinition("{{place_of_birth}}", "Place of Birth", "Common"),

    // NIDA (National ID)
    TokenDefinition("{{nida_number}}", "NIDA NIN Number", "NIDA"),
    TokenDefinition("{{district}}", "District of Residence", "NIDA"),
    TokenDefinition("{{mrz}}", "Machine Readable Zone (MRZ)", "NIDA"),

    // Driving Licence
    TokenDefinition("{{licence_number}}", "Driving Licence No.", "Driving Licence"),
    TokenDefinition("{{licence_classes}}", "Vehicle Classes (A-G)", "Driving Licence"),
    TokenDefinition("{{issue_date}}", "Issue Date", "Driving Licence"),
    TokenDefinition("{{expiry_date}}", "Expiry Date", "Driving Licence"),
    TokenDefinition("{{blood_group}}", "Blood Group (O+, A+)", "Driving Licence"),
    TokenDefinition("{{authority}}", "Licensing Authority", "Driving Licence"),

    // NHIF (Health Insurance)
    TokenDefinition("{{nhif_number}}", "NHIF Membership Card No.", "NHIF"),
    TokenDefinition("{{facility}}", "Designated Clinic / Hospital", "NHIF"),
    TokenDefinition("{{nhif_status}}", "Membership Scheme / Status", "NHIF"),
    TokenDefinition("{{employer}}", "Employer Sponsor", "NHIF"),

    // NRC Zambia
    TokenDefinition("{{nrc_number}}", "NRC Card Number (123456/78/1)", "NRC Zambia"),
    TokenDefinition("{{province}}", "Province of Origin", "NRC Zambia"),
    TokenDefinition("{{chief}}", "Chief / Village Authority", "NRC Zambia"),
    TokenDefinition("{{village}}", "Village / Town", "NRC Zambia"),
    TokenDefinition("{{parents_birthplace}}", "Parents' Birthplace", "NRC Zambia"),
    TokenDefinition("{{registration_date}}", "Date of Registration", "NRC Zambia"),
    TokenDefinition("{{special_marks}}", "Distinguishing Marks", "NRC Zambia"),

    // Biometrics & Visuals
    TokenDefinition("{{photo}}", "Biometric Photo Box", "Biometrics"),
    TokenDefinition("{{signature}}", "Holder Signature Box", "Biometrics"),
    TokenDefinition("{{barcode}}", "Code128 Card Barcode", "Biometrics"),
    TokenDefinition("{{qr_code}}", "2D Verification QR Code", "Biometrics"),
    TokenDefinition("{{security_seal}}", "Government Security Seal", "Biometrics")
)

fun getServicePresetDefaultName(category: String): String = when (category) {
    "Driving Licence" -> "Tanzania Driving Licence Template"
    "NHIF" -> "NHIF Membership Smart Card Template"
    "NRC Zambia" -> "Zambia National Registration Card Template"
    "Student ID" -> "Student Academic Identity Card"
    "Employee Badge" -> "Corporate Employee Badge Template"
    else -> "Tanzania National ID Template"
}

fun getServicePresetPrimaryColor(category: String): String = when (category) {
    "Driving Licence" -> "#14532D" // Dark Green
    "NHIF" -> "#0284C7" // Sky Blue
    "NRC Zambia" -> "#854D0E" // Bronze / Amber
    "Student ID" -> "#4B5563"
    "Employee Badge" -> "#18181B"
    else -> "#1E3A8A" // Deep Blue NIDA
}

fun getServicePresetFrontLayers(category: String): List<StudioLayer> = when (category) {
    "Driving Licence" -> listOf(
        StudioLayer("header_banner", "Agency Header Banner", StudioLayerType.TEXT, textValue = "JAMHURI YA MUUNGANO WA TANZANIA", xPercent = 5f, yPercent = 6f, fontSize = 24f, isBold = true, colorHex = "#FFFFFF"),
        StudioLayer("doc_title", "Document Title", StudioLayerType.TEXT, textValue = "DRIVING LICENCE • LESENI YA UDEREVA", xPercent = 5f, yPercent = 12f, fontSize = 18f, isBold = false, colorHex = "#E4E4E7"),
        StudioLayer("photo_box", "Portrait Photo Placeholder", StudioLayerType.PHOTO, bindingToken = "{{photo}}", xPercent = 5f, yPercent = 24f, widthPercent = 25f, heightPercent = 56f),
        StudioLayer("f_name", "Driver Full Name", StudioLayerType.TEXT, bindingToken = "{{full_name}}", textValue = "GODFREY ELIAS MREMA", xPercent = 34f, yPercent = 26f, fontSize = 32f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_licence_no", "Licence Number", StudioLayerType.TEXT, bindingToken = "{{licence_number}}", textValue = "DL-TZA-2024-88910", xPercent = 34f, yPercent = 40f, fontSize = 28f, isBold = true, colorHex = "#15803D"),
        StudioLayer("f_classes", "Authorized Classes", StudioLayerType.TEXT, bindingToken = "{{licence_classes}}", textValue = "CLASSES: A, B, C, D", xPercent = 34f, yPercent = 54f, fontSize = 22f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_blood_exp", "Blood Group & Expiry", StudioLayerType.TEXT, bindingToken = "{{blood_group}}", textValue = "BLOOD: O+   EXP: 2027-01-15", xPercent = 34f, yPercent = 66f, fontSize = 20f, colorHex = "#3F3F46"),
        StudioLayer("f_auth", "Issuing Authority", StudioLayerType.TEXT, bindingToken = "{{authority}}", textValue = "AUTH: TRA MAINLAND / POLISI", xPercent = 34f, yPercent = 76f, fontSize = 18f, colorHex = "#52525B"),
        StudioLayer("f_barcode", "Card Barcode (Code128)", StudioLayerType.BARCODE, bindingToken = "{{licence_number}}", textValue = "DLTZA202488910", xPercent = 34f, yPercent = 84f, widthPercent = 42f, heightPercent = 12f),
        StudioLayer("f_qr", "Verification QR Code", StudioLayerType.QRCODE, bindingToken = "{{licence_number}}", textValue = "TRA-DL-VERIFIED-88910", xPercent = 80f, yPercent = 66f, widthPercent = 16f, heightPercent = 22f),
        StudioLayer("f_seal", "Traffic Seal Badge", StudioLayerType.BADGE, textValue = "TRAFFIC POLICE", xPercent = 82f, yPercent = 24f, widthPercent = 13f, heightPercent = 18f)
    )
    "NHIF" -> listOf(
        StudioLayer("header_banner", "Agency Header Banner", StudioLayerType.TEXT, textValue = "NATIONAL HEALTH INSURANCE FUND", xPercent = 5f, yPercent = 6f, fontSize = 24f, isBold = true, colorHex = "#FFFFFF"),
        StudioLayer("doc_title", "Document Title", StudioLayerType.TEXT, textValue = "KITAMBULISHO CHA BIMA YA AFYA (NHIF)", xPercent = 5f, yPercent = 12f, fontSize = 18f, isBold = false, colorHex = "#E4E4E7"),
        StudioLayer("photo_box", "Portrait Photo Placeholder", StudioLayerType.PHOTO, bindingToken = "{{photo}}", xPercent = 5f, yPercent = 24f, widthPercent = 25f, heightPercent = 56f),
        StudioLayer("f_name", "Member Full Name", StudioLayerType.TEXT, bindingToken = "{{full_name}}", textValue = "NEEMA DAVID MALLYA", xPercent = 34f, yPercent = 26f, fontSize = 32f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_nhif_no", "Membership Card No", StudioLayerType.TEXT, bindingToken = "{{nhif_number}}", textValue = "NHIF-102847593021", xPercent = 34f, yPercent = 42f, fontSize = 28f, isBold = true, colorHex = "#0284C7"),
        StudioLayer("f_facility", "Designated Clinic/Hospital", StudioLayerType.TEXT, bindingToken = "{{facility}}", textValue = "HOSPITAL: MUHIMBILI (MNH-01)", xPercent = 34f, yPercent = 56f, fontSize = 22f, colorHex = "#18181B"),
        StudioLayer("f_status", "Membership Status", StudioLayerType.TEXT, bindingToken = "{{nhif_status}}", textValue = "STATUS: ACTIVE • BIMA YA JAMII", xPercent = 34f, yPercent = 68f, fontSize = 20f, isBold = true, colorHex = "#15803D"),
        StudioLayer("f_barcode", "Card Barcode (Code128)", StudioLayerType.BARCODE, bindingToken = "{{nhif_number}}", textValue = "102847593021", xPercent = 34f, yPercent = 80f, widthPercent = 42f, heightPercent = 14f),
        StudioLayer("f_qr", "Verification QR Code", StudioLayerType.QRCODE, bindingToken = "{{nhif_number}}", textValue = "NHIF-VALID-102847593021", xPercent = 80f, yPercent = 66f, widthPercent = 16f, heightPercent = 22f),
        StudioLayer("f_seal", "Health Security Seal", StudioLayerType.BADGE, textValue = "NHIF SEAL", xPercent = 82f, yPercent = 24f, widthPercent = 13f, heightPercent = 18f)
    )
    "NRC Zambia" -> listOf(
        StudioLayer("header_banner", "Agency Header Banner", StudioLayerType.TEXT, textValue = "REPUBLIC OF ZAMBIA", xPercent = 5f, yPercent = 6f, fontSize = 24f, isBold = true, colorHex = "#FFFFFF"),
        StudioLayer("doc_title", "Document Title", StudioLayerType.TEXT, textValue = "NATIONAL REGISTRATION CARD", xPercent = 5f, yPercent = 12f, fontSize = 18f, isBold = false, colorHex = "#E4E4E7"),
        StudioLayer("photo_box", "Portrait Photo Placeholder", StudioLayerType.PHOTO, bindingToken = "{{photo}}", xPercent = 5f, yPercent = 24f, widthPercent = 25f, heightPercent = 56f),
        StudioLayer("f_name", "Full Name Field", StudioLayerType.TEXT, bindingToken = "{{full_name}}", textValue = "CHILUFYA BWALYA MWANSA", xPercent = 34f, yPercent = 26f, fontSize = 32f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_nrc_no", "NRC Number Field", StudioLayerType.TEXT, bindingToken = "{{nrc_number}}", textValue = "123456/78/1", xPercent = 34f, yPercent = 40f, fontSize = 30f, isBold = true, colorHex = "#B45309"),
        StudioLayer("f_dob_sex", "Date of Birth & Sex", StudioLayerType.TEXT, bindingToken = "{{dob}}", textValue = "DOB: 14/08/1993 • SEX: M", xPercent = 34f, yPercent = 54f, fontSize = 22f, colorHex = "#3F3F46"),
        StudioLayer("f_pob", "Place of Birth", StudioLayerType.TEXT, bindingToken = "{{place_of_birth}}", textValue = "POB: LUSAKA GENERAL HOSPITAL", xPercent = 34f, yPercent = 66f, fontSize = 20f, colorHex = "#3F3F46"),
        StudioLayer("f_dist_prov", "District & Province", StudioLayerType.TEXT, bindingToken = "{{district}}", textValue = "DISTRICT: LUSAKA • PROV: LUSAKA", xPercent = 34f, yPercent = 78f, fontSize = 19f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_sign", "Thumbprint/Signature Box", StudioLayerType.SIGNATURE, bindingToken = "{{signature}}", xPercent = 76f, yPercent = 66f, widthPercent = 20f, heightPercent = 24f),
        StudioLayer("f_seal", "Coat of Arms Badge", StudioLayerType.BADGE, textValue = "COAT OF ARMS", xPercent = 82f, yPercent = 24f, widthPercent = 13f, heightPercent = 18f)
    )
    else -> listOf(
        StudioLayer("header_banner", "Agency Header Banner", StudioLayerType.TEXT, textValue = "JAMHURI YA MUUNGANO WA TANZANIA", xPercent = 5f, yPercent = 6f, fontSize = 24f, isBold = true, colorHex = "#FFFFFF"),
        StudioLayer("doc_title", "Document Title", StudioLayerType.TEXT, textValue = "NATIONAL IDENTIFICATION CARD", xPercent = 5f, yPercent = 12f, fontSize = 18f, isBold = false, colorHex = "#E4E4E7"),
        StudioLayer("photo_box", "Portrait Photo Placeholder", StudioLayerType.PHOTO, bindingToken = "{{photo}}", xPercent = 5f, yPercent = 24f, widthPercent = 25f, heightPercent = 56f),
        StudioLayer("f_name", "Full Name Field", StudioLayerType.TEXT, bindingToken = "{{full_name}}", textValue = "AMINA JUMA HASSAN", xPercent = 34f, yPercent = 26f, fontSize = 32f, isBold = true, colorHex = "#18181B"),
        StudioLayer("f_id", "National ID Number", StudioLayerType.TEXT, bindingToken = "{{nida_number}}", textValue = "19950612-14102-00001-22", xPercent = 34f, yPercent = 42f, fontSize = 28f, isBold = true, colorHex = "#0284C7"),
        StudioLayer("f_dob", "Date of Birth & Gender", StudioLayerType.TEXT, bindingToken = "{{dob}}", textValue = "DOB: 12/06/1995 • GENDER: F", xPercent = 34f, yPercent = 58f, fontSize = 22f, isBold = false, colorHex = "#3F3F46"),
        StudioLayer("f_district", "District of Residence", StudioLayerType.TEXT, bindingToken = "{{district}}", textValue = "DISTRICT: ILALA, DAR ES SALAAM", xPercent = 34f, yPercent = 68f, fontSize = 20f, colorHex = "#52525B"),
        StudioLayer("f_barcode", "Card Barcode (Code128)", StudioLayerType.BARCODE, bindingToken = "{{id_number}}", textValue = "19950612141020000122", xPercent = 34f, yPercent = 78f, widthPercent = 40f, heightPercent = 14f),
        StudioLayer("f_qr", "Verification QR Code", StudioLayerType.QRCODE, bindingToken = "{{nida_number}}", textValue = "BIGSTA-VERIFIED-19950612", xPercent = 78f, yPercent = 68f, widthPercent = 17f, heightPercent = 22f),
        StudioLayer("f_seal", "Official Seal", StudioLayerType.BADGE, textValue = "COAT OF ARMS", xPercent = 82f, yPercent = 24f, widthPercent = 13f, heightPercent = 18f)
    )
}

fun getServicePresetBackLayers(category: String): List<StudioLayer> = when (category) {
    "Driving Licence" -> listOf(
        StudioLayer("b_header", "Back Header Terms", StudioLayerType.TEXT, textValue = "VEHICLE CLASSIFICATION ENDORSEMENTS", xPercent = 5f, yPercent = 8f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_terms", "Categories Legend", StudioLayerType.TEXT, textValue = "A: Motorcycles | B: Passenger Cars | C: Commercial PSV | D: Heavy Goods", xPercent = 5f, yPercent = 18f, fontSize = 16f, isBold = false, colorHex = "#52525B"),
        StudioLayer("b_notice", "Legal Notice", StudioLayerType.TEXT, textValue = "Holder must carry licence while operating vehicle. Subject to Traffic Act.", xPercent = 5f, yPercent = 28f, fontSize = 14f, colorHex = "#71717A"),
        StudioLayer("b_sign", "Signature of Driver", StudioLayerType.SIGNATURE, bindingToken = "{{signature}}", xPercent = 5f, yPercent = 42f, widthPercent = 40f, heightPercent = 22f),
        StudioLayer("b_authority", "Issuing Authority", StudioLayerType.TEXT, bindingToken = "{{authority}}", textValue = "COMMISSIONER FOR TRAFFIC POLICE", xPercent = 52f, yPercent = 48f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_barcode", "Back Verification Barcode", StudioLayerType.BARCODE, bindingToken = "{{licence_number}}", textValue = "DLTZA202488910", xPercent = 52f, yPercent = 68f, widthPercent = 42f, heightPercent = 18f)
    )
    "NHIF" -> listOf(
        StudioLayer("b_header", "Emergency Notice", StudioLayerType.TEXT, textValue = "HUDUMA YA DHARURA / HOTLINE: 0800 110 074", xPercent = 5f, yPercent = 8f, fontSize = 18f, isBold = true, colorHex = "#DC2626"),
        StudioLayer("b_terms", "Card Use Terms", StudioLayerType.TEXT, textValue = "Kadi hii inatambulika katika hospitali na vituo vyote vilivyoidhinishwa na NHIF.", xPercent = 5f, yPercent = 20f, fontSize = 16f, isBold = false, colorHex = "#52525B"),
        StudioLayer("b_sign", "Signature of Member", StudioLayerType.SIGNATURE, bindingToken = "{{signature}}", xPercent = 5f, yPercent = 40f, widthPercent = 40f, heightPercent = 22f),
        StudioLayer("b_authority", "Scheme Authority", StudioLayerType.TEXT, textValue = "DIRECTOR GENERAL • NHIF TANZANIA", xPercent = 52f, yPercent = 46f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_qr", "Verification QR Code", StudioLayerType.QRCODE, bindingToken = "{{nhif_number}}", textValue = "NHIF-BENEFICIARY-102847593021", xPercent = 55f, yPercent = 64f, widthPercent = 20f, heightPercent = 26f)
    )
    "NRC Zambia" -> listOf(
        StudioLayer("b_header", "Jurisdiction Header", StudioLayerType.TEXT, textValue = "RECORD OF JURISDICTION • NRC ZAMBIA", xPercent = 5f, yPercent = 8f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_chief_village", "Chief & Village", StudioLayerType.TEXT, bindingToken = "{{chief}}", textValue = "CHIEF: MWAMBA • VILLAGE/TOWN: KABWATA", xPercent = 5f, yPercent = 22f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_parents_pob", "Parents' Place of Birth", StudioLayerType.TEXT, bindingToken = "{{parents_birthplace}}", textValue = "PARENTS' BIRTHPLACE: KASAMA, NORTHERN", xPercent = 5f, yPercent = 36f, fontSize = 18f, colorHex = "#3F3F46"),
        StudioLayer("b_reg_date", "Date of Registration", StudioLayerType.TEXT, bindingToken = "{{registration_date}}", textValue = "DATE OF REGISTRATION: 18/09/2010", xPercent = 5f, yPercent = 50f, fontSize = 18f, colorHex = "#3F3F46"),
        StudioLayer("b_marks", "Special Marks", StudioLayerType.TEXT, bindingToken = "{{special_marks}}", textValue = "SPECIAL MARKS: NONE", xPercent = 5f, yPercent = 62f, fontSize = 17f, colorHex = "#52525B"),
        StudioLayer("b_authority", "Registrar Authority", StudioLayerType.TEXT, textValue = "CHIEF REGISTRAR OF PERSONS • ZAMBIA", xPercent = 5f, yPercent = 76f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_barcode", "Back Barcode", StudioLayerType.BARCODE, bindingToken = "{{nrc_number}}", textValue = "123456781", xPercent = 55f, yPercent = 72f, widthPercent = 40f, heightPercent = 16f)
    )
    else -> listOf(
        StudioLayer("b_header", "Back Header Terms", StudioLayerType.TEXT, textValue = "MALI YA SERIKALI YA JAMHURI YA MUUNGANO WA TANZANIA", xPercent = 5f, yPercent = 8f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_terms", "Legal Notice Text", StudioLayerType.TEXT, textValue = "Kadi hii inapaswa kurudishwa ofisi ya NIDA iliyo karibu ikipotea au kupatikana.", xPercent = 5f, yPercent = 20f, fontSize = 16f, isBold = false, colorHex = "#52525B"),
        StudioLayer("b_sign", "Signature of Holder", StudioLayerType.SIGNATURE, bindingToken = "{{signature}}", xPercent = 5f, yPercent = 40f, widthPercent = 40f, heightPercent = 22f),
        StudioLayer("b_authority", "Issuing Authority", StudioLayerType.TEXT, textValue = "DIRECTOR GENERAL • NIDA TANZANIA", xPercent = 55f, yPercent = 46f, fontSize = 18f, isBold = true, colorHex = "#18181B"),
        StudioLayer("b_district", "Issue District", StudioLayerType.TEXT, bindingToken = "{{district}}", textValue = "ISSUED AT: ILALA DISTRICT", xPercent = 55f, yPercent = 56f, fontSize = 16f, colorHex = "#52525B"),
        StudioLayer("b_mrz", "Machine Readable Zone (MRZ)", StudioLayerType.TEXT, bindingToken = "{{mrz}}", textValue = "I<TZA199506124<<<<<<<<<<<<<<<<<<\n8501018F2812312TZA<<<<<<<<<<<<6", xPercent = 5f, yPercent = 74f, fontSize = 20f, isBold = true, colorHex = "#18181B")
    )
}

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
    val bindingTokens = allStudioTokens.map { it.token }
    val tokenCategoryFilters = listOf("All", "Common", "NIDA", "Driving Licence", "NHIF", "NRC Zambia", "Biometrics")
    var selectedTokenFilter by remember { mutableStateOf("All") }
    val colorPresets = listOf("#18181B", "#1E3A8A", "#14532D", "#7F1D1D", "#854D0E", "#0284C7", "#D97706", "#4B5563")

    var templateId by remember { mutableStateOf<String?>(null) }
    var templateName by remember { mutableStateOf("Tanzania National ID Template") }
    var selectedCategory by remember { mutableStateOf("NIDA") }
    var selectedSide by remember { mutableStateOf("front") }
    var isUniversal by remember { mutableStateOf(false) }
    var primaryColorHex by remember { mutableStateOf("#18181B") }
    var cardBgHex by remember { mutableStateOf("#FFFFFF") }
    var frontBgUri by remember { mutableStateOf<Uri?>(null) }
    var backBgUri by remember { mutableStateOf<Uri?>(null) }
    var frontBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBgBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val bgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
            if (bmp != null) {
                if (selectedSide == "front") {
                    frontBgUri = uri
                    frontBgBitmap = bmp
                    statusMessage = "Front background artwork applied!"
                } else {
                    backBgUri = uri
                    backBgBitmap = bmp
                    statusMessage = "Back background artwork applied!"
                }
            } else {
                statusMessage = "Could not decode chosen background image."
            }
        }
    }
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

        val cardRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val currentBgBmp = if (sideToRender == "front") frontBgBitmap else backBgBitmap

        if (currentBgBmp != null) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(cardRect, 32f, 32f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val src = Rect(0, 0, currentBgBmp.width, currentBgBmp.height)
            val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
            canvas.drawBitmap(currentBgBmp, src, cardRect, bgPaint)
            canvas.restore()
        } else {
            val bgPaint = Paint().apply {
                color = runCatching { Color.parseColor(cardBgHex) }.getOrDefault(Color.WHITE)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(cardRect, 32f, 32f, bgPaint)
        }

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

    if (!session.isAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Custom Card Studio") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                SolidSectionCard {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Administrator Access Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Custom Template Studio is strictly restricted to system administrators for creating and modifying master card layouts. Non-admin users cannot access or edit card templates.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(onClick = onBack) {
                            Text("Return to Dashboard")
                        }
                    }
                }
            }
        }
        return
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

                    // Publish Master Template to Cloud (Administrator)
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

                    val previewBitmap = remember(frontLayers, backLayers, selectedSide, primaryColorHex, cardBgHex, frontBgBitmap, backBgBitmap) {
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

                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            frontLayers = getServicePresetFrontLayers(selectedCategory)
                            backLayers = getServicePresetBackLayers(selectedCategory)
                            primaryColorHex = getServicePresetPrimaryColor(selectedCategory)
                            templateName = getServicePresetDefaultName(selectedCategory)
                            selectedLayerId = (if (selectedSide == "front") frontLayers else backLayers).firstOrNull()?.id ?: ""
                            statusMessage = "Loaded official $selectedCategory layout preset with all service elements!"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Official $selectedCategory Preset Layers", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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

                    // Custom Background Artwork Picker (Front & Back)
                    Text("CR80 Background Artwork (${selectedSide.uppercase()}):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                bgLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if ((if (selectedSide == "front") frontBgBitmap else backBgBitmap) != null) "Change Artwork" else "Upload Artwork", fontSize = 12.sp)
                        }

                        if ((if (selectedSide == "front") frontBgBitmap else backBgBitmap) != null) {
                            OutlinedButton(
                                onClick = {
                                    if (selectedSide == "front") {
                                        frontBgBitmap = null
                                        frontBgUri = null
                                    } else {
                                        backBgBitmap = null
                                        backBgUri = null
                                    }
                                    statusMessage = "${selectedSide.uppercase()} background cleared to default."
                                },
                                modifier = Modifier.weight(0.7f)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
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

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Quick Add Elements:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_txt_${System.currentTimeMillis()}", "Text Field", StudioLayerType.TEXT, textValue = "SAMPLE TEXT", xPercent = 10f, yPercent = 50f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Text", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_photo_${System.currentTimeMillis()}", "Photo Placeholder", StudioLayerType.PHOTO, bindingToken = "{{photo}}", xPercent = 5f, yPercent = 25f, widthPercent = 25f, heightPercent = 50f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Photo Box", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_sign_${System.currentTimeMillis()}", "Signature Box", StudioLayerType.SIGNATURE, bindingToken = "{{signature}}", xPercent = 10f, yPercent = 65f, widthPercent = 35f, heightPercent = 20f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Signature", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_bar_${System.currentTimeMillis()}", "Barcode (Code128)", StudioLayerType.BARCODE, bindingToken = "{{id_number}}", textValue = "1234567890", xPercent = 10f, yPercent = 75f, widthPercent = 40f, heightPercent = 14f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Barcode", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_qr_${System.currentTimeMillis()}", "Verification QR Code", StudioLayerType.QRCODE, bindingToken = "{{id_number}}", textValue = "VERIFIED-ID", xPercent = 75f, yPercent = 65f, widthPercent = 18f, heightPercent = 22f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ QR Code", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_seal_${System.currentTimeMillis()}", "Security Seal", StudioLayerType.BADGE, textValue = "SEAL", xPercent = 80f, yPercent = 20f, widthPercent = 14f, heightPercent = 18f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Seal", fontSize = 11.sp) }
                            )
                        }
                        item {
                            AssistChip(
                                onClick = {
                                    val newLayer = StudioLayer("f_shape_${System.currentTimeMillis()}", "Accent Shape", StudioLayerType.SHAPE, colorHex = primaryColorHex, xPercent = 5f, yPercent = 90f, widthPercent = 90f, heightPercent = 4f)
                                    updateCurrentLayers(currentLayers + newLayer)
                                    selectedLayerId = newLayer.id
                                },
                                label = { Text("+ Shape", fontSize = 11.sp) }
                            )
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

                        Text("Filter Tokens by Service:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tokenCategoryFilters) { tCat ->
                                FilterChip(
                                    selected = selectedTokenFilter.equals(tCat, ignoreCase = true),
                                    onClick = { selectedTokenFilter = tCat },
                                    label = { Text(tCat, fontSize = 11.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Bind Dynamic Field Token:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        val displayedTokens = remember(selectedTokenFilter) {
                            if (selectedTokenFilter == "All") allStudioTokens
                            else allStudioTokens.filter { it.category.equals(selectedTokenFilter, ignoreCase = true) }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(displayedTokens) { tokenDef ->
                                val isBound = layer.bindingToken == tokenDef.token
                                FilterChip(
                                    selected = isBound,
                                    onClick = {
                                        val nextToken = if (isBound) "" else tokenDef.token
                                        updateSelectedLayer { it.copy(bindingToken = nextToken) }
                                    },
                                    label = { Text("${tokenDef.label} (${tokenDef.token})", fontSize = 11.sp) }
                                )
                            }
                        }
                        if (layer.bindingToken.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = { updateSelectedLayer { it.copy(bindingToken = "") } }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Token Binding (${layer.bindingToken})", fontSize = 11.sp)
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
