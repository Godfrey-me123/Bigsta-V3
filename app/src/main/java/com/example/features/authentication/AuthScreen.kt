package com.example.features.authentication

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ErrorMessageBanner
import com.example.ui.components.SolidSectionCard
import com.example.ui.theme.BigstaSuccessGreen
import com.example.ui.theme.BigstaWarningAmber

private enum class AuthTab { SIGN_IN, REGISTER, RESET }

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(AuthTab.SIGN_IN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = "BIGsta logo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("BIGsta Studio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "ID Card Designer, Document Suite & Token Platform",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!state.isSupabaseConfigured) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                color = BigstaWarningAmber.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BigstaWarningAmber.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Supabase Secrets Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BigstaWarningAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Configure SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY in the AI Studio Secrets panel to connect to your amber-dog backend.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        SolidSectionCard(modifier = Modifier.widthIn(max = 520.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                AuthTab.entries.forEachIndexed { idx, tab ->
                    SegmentedButton(
                        selected = activeTab == tab,
                        onClick = {
                            activeTab = tab
                            viewModel.clearMessages()
                        },
                        shape = SegmentedButtonDefaults.itemShape(idx, AuthTab.entries.size),
                        modifier = Modifier.testTag("auth_tab_${tab.name.lowercase()}")
                    ) {
                        Text(
                            text = when (tab) {
                                AuthTab.SIGN_IN -> "Sign In"
                                AuthTab.REGISTER -> "Register"
                                AuthTab.RESET -> "Reset"
                            },
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ErrorMessageBanner(
                message = state.errorMessage,
                onDismiss = { viewModel.clearMessages() }
            )

            if (!state.infoMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = BigstaSuccessGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BigstaSuccessGreen.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = state.infoMessage.orEmpty(),
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (activeTab == AuthTab.REGISTER) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("register_name_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            if (activeTab != AuthTab.RESET) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )
            }

            if (activeTab == AuthTab.REGISTER) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (optional)") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("Region (optional)") },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    when (activeTab) {
                        AuthTab.SIGN_IN -> viewModel.signIn(email, password)
                        AuthTab.REGISTER -> viewModel.signUp(email, password, fullName, phone, region)
                        AuthTab.RESET -> viewModel.requestPasswordReset(email)
                    }
                },
                enabled = !state.isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("auth_submit_button")
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        when (activeTab) {
                            AuthTab.SIGN_IN -> "Sign In to BIGsta"
                            AuthTab.REGISTER -> "Create Account"
                            AuthTab.RESET -> "Send Recovery Link"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
