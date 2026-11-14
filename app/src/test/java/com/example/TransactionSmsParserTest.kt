package com.example

import com.example.domain.sms.SmsParseOutcome
import com.example.domain.sms.TransactionSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionSmsParserTest {

    @Test
    fun `parses valid M-Pesa Lipa Namba confirmation accurately`() {
        val sms = "BK92MN8841 Confirmed. You have paid TZS 10,500.00 to Lipa Namba 5511223 BIGSTA on 14/11/26 at 10:30 AM. M-Pesa."
        val outcome = TransactionSmsParser.parse(sms)
        assertTrue(outcome is SmsParseOutcome.ValidTransaction)
        val tx = outcome as SmsParseOutcome.ValidTransaction
        assertEquals("BK92MN8841", tx.transactionRef)
        assertEquals(10500.0, tx.amount, 0.01)
        assertEquals("TZS", tx.currency)
        assertEquals("M-Pesa", tx.provider)
        assertEquals("LIPA_NAMBA", tx.transactionType)
        assertEquals("5511223", tx.senderAccount)
    }

    @Test
    fun `parses valid Tigo Pesa Swahili confirmation and normalizes TSH to TZS`() {
        val sms = "Imethibitishwa. Ref: TG7738192. Umelipa Tsh 5,000 kwa BIGSTA 0712345678 mnamo 14/11/2026 09:15. Mixx by Yas Tigo Pesa."
        val outcome = TransactionSmsParser.parse(sms)
        assertTrue(outcome is SmsParseOutcome.ValidTransaction)
        val tx = outcome as SmsParseOutcome.ValidTransaction
        assertEquals("TG7738192", tx.transactionRef)
        assertEquals(5000.0, tx.amount, 0.01)
        assertEquals("TZS", tx.currency)
        assertEquals("Tigo Pesa", tx.provider)
    }

    @Test
    fun `blocks login OTP message even if confirmation words appear`() {
        val otpSms = "Confirmed. Your BIGsta verification code / OTP is 482910 for TZS 0. Do not share this code."
        val outcome = TransactionSmsParser.parse(otpSms)
        assertTrue(outcome is SmsParseOutcome.Rejected)
        val rejected = outcome as SmsParseOutcome.Rejected
        assertTrue(rejected.isSensitiveAuthCode)
    }

    @Test
    fun `blocks banking PIN message strictly`() {
        val pinSms = "Confirmed. Your PIN is 9012 for NMB Mkononi Ref: NMB123456 TZS 1000."
        val outcome = TransactionSmsParser.parse(pinSms)
        assertTrue(outcome is SmsParseOutcome.Rejected)
        val rejected = outcome as SmsParseOutcome.Rejected
        assertTrue(rejected.isSensitiveAuthCode)
    }

    @Test
    fun `rejects disabled provider when filter is applied`() {
        val sms = "Confirmed. Ref: AM9928371. Paid TZS 3,000 via Airtel Money on 14/11/2026."
        val outcome = TransactionSmsParser.parse(sms, allowedProviders = setOf("M-Pesa"))
        assertTrue(outcome is SmsParseOutcome.Rejected)
        val rejected = outcome as SmsParseOutcome.Rejected
        assertFalse(rejected.isSensitiveAuthCode)
        assertTrue(rejected.reason.contains("disabled", ignoreCase = true))
    }

    @Test
    fun `rejects non-financial short or missing reference message`() {
        val shortSms = "Hello there"
        assertTrue(TransactionSmsParser.parse(shortSms) is SmsParseOutcome.Rejected)

        val noRefSms = "Confirmed. Paid TZS 2,000 via M-Pesa today."
        assertTrue(TransactionSmsParser.parse(noRefSms) is SmsParseOutcome.Rejected)
    }
}
