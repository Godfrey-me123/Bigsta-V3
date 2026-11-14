package com.example.features.user

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernmentFormsScreen(
    viewModel: UserDashboardViewModel,
    onBack: () -> Unit,
    initialServiceType: String = "NIDA"
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
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }

    // Service-specific fields
    var district by remember { mutableStateOf("") }
    var drivingClass by remember { mutableStateOf("Class B (Light Motor Vehicle)") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var nhifFacility by remember { mutableStateOf("") }
    var nrcProvince by remember { mutableStateOf("Lusaka") }

    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var photoLabel by remember { mutableStateOf<String?>(null) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val drivingClasses = listOf(
        "Class A (Motorcycle)",
        "Class B (Light Motor Vehicle)",
        "Class C (Commercial / Heavy Goods)",
        "Class D (Passenger Service / PSV)",
        "Class E (Articulated / Trailer)"
    )

    val bloodGroups = listOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-", "Unknown")

    val zambiaProvinces = listOf(
        "Central", "Copperbelt", "Eastern", "Luapula", "Lusaka",
        "Muchinga", "Northern", "North-Western", "Southern", "Western"
    )

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.let { bytes ->
                photoBytes = bytes
                photoLabel = "Attached Passport Photo (${bytes.size / 1024} KB)"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Government Forms") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            item {
                ScrollableTabRow(
                    selectedTabIndex = formTypes.indexOf(selectedType),
                    edgePadding = 0.dp
                ) {
                    formTypes.forEach { type ->
                        Tab(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                validationError = null
                                viewModel.clearFeedback()
                            },
                            text = { Text(type, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // Service guidance card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                when (selectedType) {
                                    "NIDA" -> "National ID Card (NIDA) Request"
                                    "Driving Licence" -> "Tanzania Driving Licence Renewal / Issue"
                                    "NHIF" -> "National Health Insurance Fund Registration"
                                    else -> "Zambia National Registration Card (NRC)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                when (selectedType) {
                                    "NIDA" -> "Ensure NIN or reference matches your official NIDA records exactly."
                                    "Driving Licence" -> "Select authorized vehicle class and blood group for endorsement."
                                    "NHIF" -> "Enter designated primary facility code and beneficiary membership."
                                    else -> "Requires NRC format XXXXXX/XX/X and registered district."
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!state.errorMessage.isNullOrBlank() || validationError != null) {
                item {
                    ErrorMessageBanner(
                        message = validationError ?: state.errorMessage.orEmpty(),
                        onDismiss = {
                            validationError = null
                            viewModel.clearFeedback()
                        }
                    )
                }
            }

            if (!state.submitSuccessMessage.isNullOrBlank()) {
                item {
                    Surface(
                        color = BigstaSuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BigstaSuccessGreen.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BigstaSuccessGreen)
                            Text(state.submitSuccessMessage.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                SolidSectionCard {
                    Text("$selectedType Application Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it; validationError = null },
                            label = { Text("First Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_first_name")
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it; validationError = null },
                            label = { Text("Last Name *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_last_name")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it; validationError = null },
                            label = { Text("Phone Number *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_phone")
                        )
                        OutlinedTextField(
                            value = dateOfBirth,
                            onValueChange = { dateOfBirth = it; validationError = null },
                            label = { Text("Date of Birth (YYYY-MM-DD) *") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("form_dob")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Service-specific primary ID input
                    OutlinedTextField(
                        value = nationalId,
                        onValueChange = { nationalId = it; validationError = null },
                        label = {
                            Text(
                                when (selectedType) {
                                    "NIDA" -> "National ID Number (NIN / 20 digits)"
                                    "Driving Licence" -> "Existing Licence Number (if renewing)"
                                    "NHIF" -> "NHIF Membership / Card Number"
                                    else -> "Zambia NRC Number (e.g. 123456/11/1)"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary service-specific inputs
                    when (selectedType) {
                        "NIDA" -> {
                            OutlinedTextField(
                                value = district,
                                onValueChange = { district = it },
                                label = { Text("Registration District / Region") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "Driving Licence" -> {
                            var drivingMenuExpanded by remember { mutableStateOf(false) }
                            var bloodMenuExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = drivingMenuExpanded,
                                onExpandedChange = { drivingMenuExpanded = !drivingMenuExpanded }
                            ) {
                                OutlinedTextField(
                                    value = drivingClass,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Licence Class") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = drivingMenuExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = drivingMenuExpanded,
                                    onDismissRequest = { drivingMenuExpanded = false }
                                ) {
                                    drivingClasses.forEach { cls ->
                                        DropdownMenuItem(
                                            text = { Text(cls, fontSize = 13.sp) },
                                            onClick = {
                                                drivingClass = cls
                                                drivingMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            ExposedDropdownMenuBox(
                                expanded = bloodMenuExpanded,
                                onExpandedChange = { bloodMenuExpanded = !bloodMenuExpanded }
                            ) {
                                OutlinedTextField(
                                    value = bloodGroup,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Blood Group") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodMenuExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = bloodMenuExpanded,
                                    onDismissRequest = { bloodMenuExpanded = false }
                                ) {
                                    bloodGroups.forEach { bg ->
                                        DropdownMenuItem(
                                            text = { Text(bg, fontSize = 13.sp) },
                                            onClick = {
                                                bloodGroup = bg
                                                bloodMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "NHIF" -> {
                            OutlinedTextField(
                                value = nhifFacility,
                                onValueChange = { nhifFacility = it },
                                label = { Text("Designated Health Facility / Hospital") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        "NRC Zambia" -> {
                            var provinceMenuExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = provinceMenuExpanded,
                                onExpandedChange = { provinceMenuExpanded = !provinceMenuExpanded }
                            ) {
                                OutlinedTextField(
                                    value = nrcProvince,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Province") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceMenuExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = provinceMenuExpanded,
                                    onDismissRequest = { provinceMenuExpanded = false }
                                ) {
                                    zambiaProvinces.forEach { prov ->
                                        DropdownMenuItem(
                                            text = { Text(prov, fontSize = 13.sp) },
                                            onClick = {
                                                nrcProvince = prov
                                                provinceMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
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
                        Text(photoLabel ?: "Attach Passport Photo (Recommended)")
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            // Validation checks
                            if (firstName.trim().isBlank() || lastName.trim().isBlank()) {
                                validationError = "First Name and Last Name are required."
                                return@Button
                            }
                            if (phone.trim().length < 9) {
                                validationError = "Please enter a valid telephone number."
                                return@Button
                            }
                            if (dateOfBirth.trim().isBlank()) {
                                validationError = "Date of Birth is required (YYYY-MM-DD)."
                                return@Button
                            }

                            val enrichedReference = when (selectedType) {
                                "NIDA" -> nationalId.ifBlank { district }
                                "Driving Licence" -> listOfNotNull(nationalId.takeIf { it.isNotBlank() }, drivingClass, bloodGroup).joinToString(" | ")
                                "NHIF" -> listOfNotNull(nationalId.takeIf { it.isNotBlank() }, nhifFacility.takeIf { it.isNotBlank() }).joinToString(" | ")
                                else -> listOfNotNull(nationalId.takeIf { it.isNotBlank() }, nrcProvince).joinToString(" | ")
                            }.takeIf { it.isNotBlank() }

                            viewModel.submitGovernmentForm(
                                serviceType = selectedType,
                                firstName = firstName.trim(),
                                lastName = lastName.trim(),
                                phone = phone.trim(),
                                dateOfBirth = dateOfBirth.trim(),
                                nationalId = enrichedReference,
                                photoBytes = photoBytes
                            )
                        },
                        enabled = !state.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("form_submit_btn")
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Submit $selectedType Request")
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Submitted Applications (${state.userRequests.size})", style = MaterialTheme.typography.titleMedium)
                    if (state.userRequests.isNotEmpty()) {
                        TextButton(onClick = { viewModel.refreshAll() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }
                }
            }

            items(state.userRequests, key = { it.id }) { req ->
                var expanded by remember { mutableStateOf(false) }

                SolidSectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(req.applicantDisplayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    "${req.serviceType} • DOB: ${req.dateOfBirth ?: "-"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(status = req.status)
                        }

                        if (expanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tracking ID: ${req.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            if (!req.nationalId.isNullOrBlank()) {
                                Text("Ref / Meta: ${req.nationalId}", fontSize = 12.sp)
                            }
                            if (!req.phone.isNullOrBlank()) {
                                Text("Phone: ${req.phone}", fontSize = 12.sp)
                            }
                            if (!req.adminNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Admin Feedback: ${req.adminNotes}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
