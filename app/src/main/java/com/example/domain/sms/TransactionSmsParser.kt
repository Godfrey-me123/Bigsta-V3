package com.example.domain.sms

sealed class SmsParseOutcome {
    data class ValidTransaction(
        val transactionRef: String,
        val amount: Double,
        val currency: String,
        val provider: String,
        val transactionDateTime: String,
        val transactionType: String,
        val senderAccount: String?
    ) : SmsParseOutcome()

    data class Rejected(
        val reason: String,
        val isSensitiveAuthCode: Boolean = false
    ) : SmsParseOutcome()
}

object TransactionSmsParser {

    private val authSecretIndicators = listOf(
        Regex("""\b(otp|one[\s-]?time[\s-]?password|passcode|verification[\s-]?code|login[\s-]?code|auth[\s-]?code|2fa|mfa)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(your\s+pin|pin\s+is|secret\s+code|do\s+not\s+share\s+(this|your)\s+(code|pin|otp))\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(reset\s+your\s+password|confirm\s+your\s+login)\b""", RegexOption.IGNORE_CASE)
    )

    private val transactionRefPatterns = listOf(
        Regex("""\b(?:Ref(?:erence)?|Trans(?:action)?\s*ID|Txn\s*ID|M-?Pesa\s*Code|Receipt|Kumb(?:ukumbu)?|Confirmed\.?\s*Ref)[:#\s-]*([A-Z0-9][A-Z0-9.\-]{5,24})\b""", RegexOption.IGNORE_CASE),
        Regex("""^([A-Z]{2,4}[0-9]{2}[A-Z0-9]{4,14})\s+(?:Confirmed|Imethibitishwa)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b([A-Z0-9]{2,4}\d{4,}[A-Z0-9.\-]{2,14})\b""")
    )

    private val amountPattern = Regex(
        """\b(TZS|TSH|USD|ZMW|KES)\.?\s*([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val dateTimePattern = Regex(
        """\b(\d{1,4}[/\-]\d{1,2}[/\-]\d{1,4}(?:\s+(?:at\s+)?\d{1,2}:\d{2}(?::\d{2})?(?:\s*[AP]M)?)?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val accountOrPhonePattern = Regex(
        """\b(?:\+?255|0)[67]\d{8}\b|\bLipa\s*Namba[:\s#]*([0-9]{5,10})\b""",
        RegexOption.IGNORE_CASE
    )

    fun parse(rawMessage: String, allowedProviders: Set<String>? = null): SmsParseOutcome {
        val text = rawMessage.trim()
        if (text.length < 15) {
            return SmsParseOutcome.Rejected("Message is too short to be a valid payment confirmation.")
        }

        // Strict guard: never treat login OTPs, PINs, or banking auth codes as transactions
        if (authSecretIndicators.any { it.containsMatchIn(text) }) {
            return SmsParseOutcome.Rejected(
                reason = "Authentication OTP/PIN message blocked. Authentication codes are never forwarded.",
                isSensitiveAuthCode = true
            )
        }

        val hasConfirmationKeyword = Regex(
            """\b(confirmed|imethibitishwa|paid|umelipa|sent|umetuma|received|umepokea|transferred|lipa)\b""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)

        if (!hasConfirmationKeyword) {
            return SmsParseOutcome.Rejected("Message does not match a recognized financial confirmation format.")
        }

        val provider = detectProvider(text)
        if (allowedProviders != null && provider !in allowedProviders) {
            return SmsParseOutcome.Rejected("Provider '$provider' is currently disabled in SMS settings.")
        }

        val amountMatch = amountPattern.find(text)
            ?: return SmsParseOutcome.Rejected("Could not extract transaction amount and currency.")

        val rawCurrency = amountMatch.groupValues[1].uppercase().let {
            if (it == "TSH") "TZS" else it
        }
        val parsedAmount = amountMatch.groupValues[2].replace(",", "").toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            return SmsParseOutcome.Rejected("Extracted transaction amount is invalid.")
        }

        val txRef = extractReference(text)
            ?: return SmsParseOutcome.Rejected("Could not extract a valid transaction reference code.")

        val txType = detectTransactionType(text)
        val dateTime = dateTimePattern.find(text)?.groupValues?.getOrNull(1) ?: "Unspecified"
        val account = accountOrPhonePattern.find(text)?.let { match ->
            match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
        }

        return SmsParseOutcome.ValidTransaction(
            transactionRef = txRef,
            amount = parsedAmount,
            currency = rawCurrency,
            provider = provider,
            transactionDateTime = dateTime,
            transactionType = txType,
            senderAccount = account
        )
    }

    private fun extractReference(text: String): String? {
        for (pattern in transactionRefPatterns) {
            val match = pattern.find(text)
            val candidate = match?.groupValues?.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank() && candidate.length >= 6 && candidate.any { it.isDigit() }) {
                return candidate
            }
        }
        return null
    }

    fun detectProvider(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("m-pesa") || lower.contains("mpesa") || lower.contains("vodacom") -> "M-Pesa"
            lower.contains("tigo") || lower.contains("mixx") || lower.contains("yas") -> "Tigo Pesa"
            lower.contains("airtel") -> "Airtel Money"
            lower.contains("halopesa") || lower.contains("halotel") -> "HaloPesa"
            lower.contains("nmb") -> "NMB"
            lower.contains("crdb") -> "CRDB"
            else -> "Mobile Money"
        }
    }

    private fun detectTransactionType(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("lipa") || lower.contains("merchant") || lower.contains("till") -> "LIPA_NAMBA"
            lower.contains("paybill") || lower.contains("business") -> "PAYBILL"
            lower.contains("received") || lower.contains("umepokea") -> "RECEIVED"
            lower.contains("bank") || lower.contains("transfer") -> "BANK_TRANSFER"
            else -> "SEND_MONEY"
        }
    }
}
