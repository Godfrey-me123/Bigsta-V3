package com.example.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.network.ApiResult
import com.example.core.security.UserSession
import com.example.data.models.NidaRequestDto
import com.example.data.models.PaymentRequestDto
import com.example.data.models.ProfileDto
import com.example.data.models.TemplateDto
import com.example.data.repositories.AdminRepository
import com.example.data.repositories.AuthRepository
import com.example.features.notifications.BigstaNotificationManager
import com.example.features.notifications.NotificationCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminUiState(
    val isLoading: Boolean = false,
    val paymentRequests: List<PaymentRequestDto> = emptyList(),
    val nidaRequests: List<NidaRequestDto> = emptyList(),
    val userProfiles: List<ProfileDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val smsMonitorRaw: String = "",
    val errorMessage: String? = null,
    val actionMessage: String? = null
)

class AdminViewModel(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val notificationManager: BigstaNotificationManager
) : ViewModel() {

    val session: StateFlow<UserSession> = authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSession())

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    fun loadAdminDashboard() {
        viewModelScope.launch {
            val verified = authRepository.refreshSessionRole()
            if (!verified.isAdmin) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Access denied: administrator role in user_roles is required."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val payments = adminRepository.fetchAllPaymentRequests()
            val requests = adminRepository.fetchAllNidaRequests()
            val profiles = adminRepository.fetchAllProfiles()
            val templates = adminRepository.fetchAllTemplates()
            val smsLogs = adminRepository.fetchSmsMonitorLogs()

            val payList = (payments as? ApiResult.Success)?.data ?: emptyList()
            val pendingPaymentsCount = payList.count { it.status.equals("pending", ignoreCase = true) }
            if (pendingPaymentsCount > 0) {
                notificationManager.notifyEvent(
                    eventKey = "admin_pending_payments_$pendingPaymentsCount",
                    title = "Admin: Pending Recharges",
                    body = "$pendingPaymentsCount payment request(s) await administrator review.",
                    category = NotificationCategory.ADMIN,
                    targetRoute = "admin"
                )
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                paymentRequests = payList,
                nidaRequests = (requests as? ApiResult.Success)?.data ?: emptyList(),
                userProfiles = (profiles as? ApiResult.Success)?.data ?: emptyList(),
                templates = (templates as? ApiResult.Success)?.data ?: emptyList(),
                smsMonitorRaw = (smsLogs as? ApiResult.Success)?.data ?: "",
                errorMessage = (payments as? ApiResult.Error)?.message
                    ?: (requests as? ApiResult.Error)?.message
            )
        }
    }

    fun updatePaymentStatus(requestId: String, status: String, note: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.updatePaymentRequestStatus(requestId, status, note)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.paymentRequests.map {
                        if (it.id == requestId) res.data else it
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        paymentRequests = updatedList,
                        actionMessage = "Payment $requestId updated to $status."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun updateNidaStatus(requestId: String, status: String, notes: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.updateNidaRequestStatus(requestId, status, notes)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.nidaRequests.map {
                        if (it.id == requestId) res.data else it
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        nidaRequests = updatedList,
                        actionMessage = "Request $requestId updated to $status."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun toggleTemplateActive(template: TemplateDto, active: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.toggleTemplateActiveSide(template, active)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.templates.map {
                        if (it.id == template.id) res.data else it
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        templates = updatedList,
                        actionMessage = "${template.resolvedCategory} (${template.side}) updated."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun deleteTemplate(templateId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.deleteTemplate(templateId)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.templates.filterNot { it.id == templateId }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        templates = updatedList,
                        actionMessage = "Template $templateId deleted."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun reassignTemplateCategory(templateId: String, newCategory: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.reassignTemplateCategory(templateId, newCategory)) {
                is ApiResult.Success -> {
                    val updatedList = _uiState.value.templates.map {
                        if (it.id == templateId) res.data else it
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        templates = updatedList,
                        actionMessage = "Template reassigned to $newCategory."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun saveOrUpdateTemplate(
        id: String? = null,
        name: String,
        category: String,
        side: String,
        backgroundUrl: String? = null,
        isActive: Boolean = true,
        isUniversal: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, actionMessage = null)
            when (val res = adminRepository.saveOrUpdateTemplate(id, name, category, side, backgroundUrl, isActive, isUniversal)) {
                is ApiResult.Success -> {
                    val existingIndex = _uiState.value.templates.indexOfFirst { it.id == res.data.id }
                    val updatedList = if (existingIndex >= 0) {
                        _uiState.value.templates.map { if (it.id == res.data.id) res.data else it }
                    } else {
                        listOf(res.data) + _uiState.value.templates
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        templates = updatedList,
                        actionMessage = "Template '${res.data.name}' saved to cloud."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, actionMessage = null)
    }

    class Factory(
        private val adminRepository: AdminRepository,
        private val authRepository: AuthRepository,
        private val notificationManager: BigstaNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(adminRepository, authRepository, notificationManager) as T
    }
}
