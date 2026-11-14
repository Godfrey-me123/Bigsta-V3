package com.example.features.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.PendingUploadEntity
import com.example.core.database.ProcessedSmsEntity
import com.example.core.utilities.AppPreferences
import com.example.core.utilities.AppUserSettings
import com.example.data.repositories.AuthRepository
import com.example.data.repositories.SmsSubmissionResult
import com.example.data.repositories.SmsTransactionRepository
import com.example.domain.sms.SmsParseOutcome
import com.example.domain.sms.TransactionSmsParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SmsUiState(
    val inputSmsText: String = "",
    val previewOutcome: SmsParseOutcome? = null,
    val isSubmitting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class SmsViewModel(
    private val smsRepository: SmsTransactionRepository,
    private val appPreferences: AppPreferences,
    authRepository: AuthRepository
) : ViewModel() {

    val settings: StateFlow<AppUserSettings> = appPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppUserSettings())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<ProcessedSmsEntity>> = authRepository.sessionFlow
        .flatMapLatest { session -> smsRepository.observeTransactions(session.userId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingQueue: StateFlow<List<PendingUploadEntity>> = smsRepository.observePendingQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(SmsUiState())
    val uiState: StateFlow<SmsUiState> = _uiState.asStateFlow()

    fun setSmsMonitoringEnabled(consentAccepted: Boolean, enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setSmsConsentAndEnabled(consentAccepted, enabled)
        }
    }

    fun toggleProvider(provider: String, enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.toggleSmsProvider(provider, enabled)
        }
    }

    fun onIncomingSharedSms(sharedText: String) {
        updateInputText(sharedText)
    }

    fun updateInputText(text: String) {
        val allowed = settings.value.enabledSmsProviders
        val parsed = if (text.isBlank()) null else TransactionSmsParser.parse(text, allowed)
        _uiState.value = _uiState.value.copy(
            inputSmsText = text,
            previewOutcome = parsed,
            errorMessage = null,
            statusMessage = null
        )
    }

    fun submitCurrentSms(packageName: String = "SMS Transaction Intake", tokens: Int = 0) {
        val raw = _uiState.value.inputSmsText
        if (raw.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Paste or share a payment confirmation SMS first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null, statusMessage = null)
            when (val res = smsRepository.processIncomingTransactionText(raw, packageName, tokens)) {
                is SmsSubmissionResult.SubmittedImmediately -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        inputSmsText = "",
                        previewOutcome = null,
                        statusMessage = "Uploaded ${res.provider} ref ${res.transactionRef} (${res.amount}) for server approval."
                    )
                }
                is SmsSubmissionResult.QueuedOffline -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        inputSmsText = "",
                        previewOutcome = null,
                        statusMessage = "Queued offline (Ref ${res.transactionRef}). WorkManager will retry automatically: ${res.reason}"
                    )
                }
                is SmsSubmissionResult.DuplicateBlocked -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Duplicate blocked: transaction reference ${res.transactionRef} was already processed."
                    )
                }
                is SmsSubmissionResult.Rejected -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = res.reason
                    )
                }
            }
        }
    }

    fun retryQueuedUploads() {
        smsRepository.enqueueSyncWorker()
        _uiState.value = _uiState.value.copy(statusMessage = "WorkManager background sync scheduled for pending queue.")
    }

    class Factory(
        private val smsRepository: SmsTransactionRepository,
        private val appPreferences: AppPreferences,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SmsViewModel(smsRepository, appPreferences, authRepository) as T
    }
}
