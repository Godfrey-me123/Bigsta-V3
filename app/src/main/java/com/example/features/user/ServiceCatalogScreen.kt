package com.example.features.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.SolidSectionCard

private data class ServiceCatalogItem(val id: String, val name: String, val authority: String, val description: String, val category: String, val status: String, val destination: String?)

private val catalog = listOf(
    ServiceCatalogItem("nida", "NIDA Services", "National Identification Authority", "Instant auto-fill, verification & CR80 card generation for Front & Back National IDs.", "Identity", "Ready • 1 Token", "forms/NIDA"),
    ServiceCatalogItem("pdf_viewer", "PDF Reader & Print", "BIGsta Tools", "View, search, and print your PDF documents locally.", "Document", "Free", "pdf_tools"),
    ServiceCatalogItem("docx_to_pdf", "Open Doc File (Word to PDF)", "BIGsta Tools", "Open DOC, DOCX, TXT, RTF, or Markdown documents on-device and convert offline to PDF.", "Document", "Free", "docx_to_pdf"),
    ServiceCatalogItem("nida_request", "Request NIDA Info", "National Identification Authority", "Request NIDA picture or signature upload for manual processing.", "Identity", "Request", null),
    ServiceCatalogItem("driving_license", "Driving License Services", "Traffic & Vehicle Inspection", "Driver permit issuance, class endorsements & digital driver identification cards.", "Civil", "Ready • 1 Token", "forms/Driving%20Licence"),
    ServiceCatalogItem("nhif", "NHIF Membership Card", "National Health Insurance Fund", "Healthcare membership smart cards, dependent coverage validation & biometric health passes.", "Health", "Ready • 1 Token", "forms/NHIF"),
    ServiceCatalogItem("nrc_zambia", "NRC card-zambia", "National Registration Department", "Official Zambia National Registration Card (NRC) issuance, verification and CR80 card generation.", "Identity", "Ready • 1 Token", "forms/NRC%20Zambia"),
    ServiceCatalogItem("birth_certificate", "Birth Certificate Services", "Registration, Insolvency and Trusteeship Agency", "Official birth records authentication, extract certificates & civil registry document assistance.", "Civil", "Manual request", null),
    ServiceCatalogItem("passport", "Passport Services", "Tanzania Immigration Services Department", "International travel document applications, renewal assistance & biometric passport data IDs.", "Civil", "Manual request", null),
    ServiceCatalogItem("tin", "TIN Services", "Tanzania Revenue Authority", "Taxpayer Identification Number cards, tax compliance credentials & PIN certificates assistance.", "Finance", "Manual request", null),
    ServiceCatalogItem("business_license", "Business License Services", "BRELA & Municipal Authorities", "Commercial enterprise registration certificates, municipal trade permits & corporate IDs assistance.", "Finance", "Manual request", null),
    ServiceCatalogItem("heslb", "HESLB Student Loans Services", "Higher Education Students’ Loans Board", "Student beneficiary loan allocation cards, academic verification & repayment IDs assistance.", "Education", "Manual request", null),
    ServiceCatalogItem("ajira", "Ajira Portal Services", "Public Service Recruitment Secretariat", "Government job application portfolios, civil service recruitment IDs & applicant profiles assistance.", "Education", "Manual request", null)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCatalogScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Services") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            item { Text("Services and tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(catalog, key = { it.id }) { item ->
                SolidSectionCard(onClick = { item.destination?.let(onNavigate) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Text(item.authority, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp)); Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp)); Text("${item.category} • ${item.status}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                        if (item.destination != null) Icon(Icons.Default.ChevronRight, contentDescription = "Open ${item.name}") else Text("Not available in Android", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
