package com.example.features.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.CachedTemplateEntity
import com.example.core.database.LocalDownloadDao
import com.example.core.database.LocalDownloadEntity
import com.example.core.network.ApiResult
import com.example.core.network.ConnectivityMonitor
import com.example.core.network.ConnectivityState
import com.example.core.security.UserSession
import com.example.data.models.NidaRequestDto
import com.example.data.models.ProfileDto
import com.example.data.models.WeeklyOfferDto
import com.example.data.repositories.AuthRepository
import com.example.data.repositories.PaymentRepository
import com.example.data.repositories.ServiceRequestRepository
import com.example.data.repositories.TemplateRepository
import com.example.features.notifications.BigstaNotificationManager
import com.example.features.notifications.NotificationCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UserDashboardUiState(
    val isLoading: Boolean = false,
    val profile: ProfileDto? = null,
    val userRequests: List<NidaRequestDto> = emptyList(),
    val weeklyOffers: List<WeeklyOfferDto> = emptyList(),
    val errorMessage: String? = null,
    val submitSuccessMessage: String? = null
)

class UserDashboardViewModel(
    private val authRepository: AuthRepository,
    private val serviceRequestRepository: ServiceRequestRepository,
    private val paymentRepository: PaymentRepository,
    private val templateRepository: TemplateRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val localDownloadDao: LocalDownloadDao,
    private val notificationManager: BigstaNotificationManager
) : ViewModel() {

    val session: StateFlow<UserSession> = authRepository.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSession())

    val connectivity: StateFlow<ConnectivityState> = connectivityMonitor.state

    val cachedTemplates: StateFlow<List<CachedTemplateEntity>> = templateRepository.cachedTemplatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localDownloads: StateFlow<List<LocalDownloadEntity>> = session
        .flatMapLatest { s ->
            if (s.userId.isBlank()) flowOf(emptyList()) else localDownloadDao.observeByUser(s.userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(UserDashboardUiState())
    val uiState: StateFlow<UserDashboardUiState> = _uiState.asStateFlow()

    init {
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            connectivityMonitor.probeBackend()
            authRepository.refreshSessionRole()

            val profileRes = authRepository.fetchCurrentProfile()
            val requestsRes = serviceRequestRepository.fetchUserRequests()
            val offersRes = paymentRepository.fetchWeeklyOffers()
            templateRepository.syncTemplates()

            val profile = (profileRes as? ApiResult.Success)?.data
            val requests = (requestsRes as? ApiResult.Success)?.data ?: _uiState.value.userRequests
            val offers = (offersRes as? ApiResult.Success)?.data ?: _uiState.value.weeklyOffers

            if (profile != null && profile.effectiveTokens <= 2) {
                notificationManager.notifyEvent(
                    eventKey = "low_balance_${profile.id}_${profile.effectiveTokens}",
                    title = "Low Token Balance",
                    body = "You have ${profile.effectiveTokens} tokens remaining. Recharge to continue exporting.",
                    category = NotificationCategory.BALANCE,
                    targetRoute = "billing"
                )
            }

            val err = (profileRes as? ApiResult.Error)?.message
                ?: (requestsRes as? ApiResult.Error)?.message

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile ?: _uiState.value.profile,
                userRequests = requests,
                weeklyOffers = offers,
                errorMessage = err
            )
        }
    }

    fun probeConnection() {
        viewModelScope.launch {
            connectivityMonitor.probeBackend()
        }
    }

    fun submitGovernmentForm(
        serviceType: String,
        firstName: String,
        lastName: String,
        phone: String,
        dateOfBirth: String,
        nationalId: String?,
        photoBytes: ByteArray?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, submitSuccessMessage = null)
            when (val res = serviceRequestRepository.submitServiceRequest(
                serviceType = serviceType,
                firstName = firstName,
                lastName = lastName,
                phone = phone,
                dateOfBirth = dateOfBirth,
                nationalId = nationalId,
                photoBytes = photoBytes
            )) {
                is ApiResult.Success -> {
                    notificationManager.notifyEvent(
                        eventKey = "nida_submitted_${res.data.id}",
                        title = "$serviceType Request Submitted",
                        body = "Application for ${res.data.applicantDisplayName} is queued (${res.data.status}).",
                        category = NotificationCategory.ALERT,
                        targetRoute = "forms"
                    )
                    val refreshed = (serviceRequestRepository.fetchUserRequests() as? ApiResult.Success)?.data
                        ?: (listOf(res.data) + _uiState.value.userRequests)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userRequests = refreshed,
                        submitSuccessMessage = "$serviceType application submitted (ID: ${res.data.id.take(8)})."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = res.message)
                }
            }
        }
    }

    fun recordLocalExport(
        id: String,
        fileName: String,
        mimeType: String,
        localPath: String,
        sizeBytes: Long,
        pageCount: Int?,
        sourceType: String
    ) {
        viewModelScope.launch {
            val uid = session.value.userId.ifBlank { "local" }
            localDownloadDao.upsert(
                LocalDownloadEntity(
                    id = id,
                    fileName = fileName,
                    mimeType = mimeType,
                    localFilePath = localPath,
                    fileSizeBytes = sizeBytes,
                    pageCount = pageCount,
                    userId = uid,
                    sourceType = sourceType
                )
            )
        }
    }

    fun deleteLocalDownload(id: String) {
        viewModelScope.launch {
            val uid = session.value.userId.ifBlank { "local" }
            localDownloadDao.deleteOwned(id, uid)
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(errorMessage = null, submitSuccessMessage = null)
    }

    class Factory(
        private val authRepository: AuthRepository,
        private val serviceRequestRepository: ServiceRequestRepository,
        private val paymentRepository: PaymentRepository,
        private val templateRepository: TemplateRepository,
        private val connectivityMonitor: ConnectivityMonitor,
        private val localDownloadDao: LocalDownloadDao,
        private val notificationManager: BigstaNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            UserDashboardViewModel(
                authRepository,
                serviceRequestRepository,
                paymentRepository,
                templateRepository,
                connectivityMonitor,
                localDownloadDao,
                notificationManager
            ) as T
    }
}
