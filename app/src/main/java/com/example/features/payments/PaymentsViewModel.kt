package com.example.features.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.network.ApiResult
import com.example.data.models.PaymentMethodInfoDto
import com.example.data.models.PaymentRequestDto
import com.example.data.models.TokenPackageDto
import com.example.data.models.WeeklyOfferDto
import com.example.data.repositories.PaymentRepository
import com.example.features.notifications.BigstaNotificationManager
import com.example.features.notifications.NotificationCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PaymentsUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val packages: List<TokenPackageDto> = emptyList(),
    val weeklyOffers: List<WeeklyOfferDto> = emptyList(),
    val paymentMethods: List<PaymentMethodInfoDto> = emptyList(),
    val paymentHistory: List<PaymentRequestDto> = emptyList(),
    val selectedPackage: TokenPackageDto? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class PaymentsViewModel(
    private val paymentRepository: PaymentRepository,
    private val notificationManager: BigstaNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

    init {
        loadBillingData()
    }

    fun loadBillingData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val pkgs = paymentRepository.fetchTokenPackages()
            val offers = paymentRepository.fetchWeeklyOffers()
            val methods = paymentRepository.fetchPaymentMethodsConfig()
            val history = paymentRepository.fetchUserPaymentRequests()

            val loadedPkgs = (pkgs as? ApiResult.Success)?.data ?: emptyList()
            val loadedHistory = (history as? ApiResult.Success)?.data ?: emptyList()

            // Emit deduplicated notifications for any newly approved/rejected payment requests
            loadedHistory.forEach { req ->
                when (req.status.lowercase()) {
                    "approved" -> notificationManager.notifyEvent(
                        eventKey = "pay_approved_${req.id}",
                        title = "Recharge Approved",
                        body = "${req.tokens} tokens (${req.packageName ?: "Pack"}) have been verified.",
                        category = NotificationCategory.RECHARGE,
                        targetRoute = "billing"
                    )
                    "rejected" -> notificationManager.notifyEvent(
                        eventKey = "pay_rejected_${req.id}",
                        title = "Recharge Rejected",
                        body = req.adminNote ?: "Transaction ${req.transactionRef ?: req.id} was rejected.",
                        category = NotificationCategory.RECHARGE,
                        targetRoute = "billing"
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                packages = loadedPkgs,
                weeklyOffers = (offers as? ApiResult.Success)?.data ?: emptyList(),
                paymentMethods = (methods as? ApiResult.Success)?.data ?: emptyList(),
                paymentHistory = loadedHistory,
                selectedPackage = _uiState.value.selectedPackage ?: loadedPkgs.firstOrNull(),
                errorMessage = (pkgs as? ApiResult.Error)?.message ?: (history as? ApiResult.Error)?.message
            )
        }
    }

    fun selectPackage(pkg: TokenPackageDto) {
        _uiState.value = _uiState.value.copy(selectedPackage = pkg, errorMessage = null, successMessage = null)
    }

    fun selectOfferAsPackage(offer: WeeklyOfferDto) {
        val syntheticPkg = TokenPackageDto(
            id = offer.id,
            name = offer.title,
            usages = offer.tokens,
            price = offer.price,
            description = offer.description
        )
        _uiState.value = _uiState.value.copy(selectedPackage = syntheticPkg, errorMessage = null, successMessage = null)
    }

    fun submitRecharge(
        paymentMethod: String,
        transactionRef: String,
        senderPhone: String,
        smsText: String? = null
    ) {
        val pkg = _uiState.value.selectedPackage
        if (pkg == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Select a token package or weekly offer first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null, successMessage = null)
            // Strictly awaits Supabase payment_requests write BEFORE reporting success
            when (val res = paymentRepository.submitPaymentRequest(
                packageId = pkg.id.takeIf { it.isNotBlank() },
                packageName = pkg.name,
                amount = pkg.price,
                tokens = pkg.usages + pkg.bonus,
                paymentMethod = paymentMethod,
                transactionRef = transactionRef,
                senderPhone = senderPhone,
                smsText = smsText
            )) {
                is ApiResult.Success -> {
                    notificationManager.notifyEvent(
                        eventKey = "pay_pending_${res.data.id}",
                        title = "Payment Submitted (Pending Review)",
                        body = "Ref ${res.data.transactionRef ?: transactionRef} for ${pkg.name} is awaiting verification.",
                        category = NotificationCategory.PAYMENT,
                        targetRoute = "billing"
                    )
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        paymentHistory = listOf(res.data) + _uiState.value.paymentHistory,
                        successMessage = "Payment request saved (status: ${res.data.status}). Tokens will be credited once verified."
                    )
                }
                is ApiResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = "Submission failed (selection preserved): ${res.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    class Factory(
        private val paymentRepository: PaymentRepository,
        private val notificationManager: BigstaNotificationManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PaymentsViewModel(paymentRepository, notificationManager) as T
    }
}
