package com.example.core.utilities

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prefsDataStore by preferencesDataStore(name = "bigsta_preferences")

data class AppUserSettings(
    val smsMonitoringEnabled: Boolean = false,
    val smsConsentAccepted: Boolean = false,
    val enabledSmsProviders: Set<String> = setOf("M-Pesa", "Tigo Pesa", "Airtel Money", "HaloPesa"),
    val notifyPayments: Boolean = true,
    val notifyRecharges: Boolean = true,
    val notifyBalanceAlerts: Boolean = true,
    val notifyAdminAlerts: Boolean = true,
    val disclaimerAccepted: Boolean = false
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val SMS_MONITORING = booleanPreferencesKey("sms_monitoring_enabled")
        val SMS_CONSENT = booleanPreferencesKey("sms_consent_accepted")
        val SMS_PROVIDERS = stringSetPreferencesKey("sms_providers")
        val NOTIFY_PAYMENTS = booleanPreferencesKey("notify_payments")
        val NOTIFY_RECHARGES = booleanPreferencesKey("notify_recharges")
        val NOTIFY_BALANCE = booleanPreferencesKey("notify_balance")
        val NOTIFY_ADMIN = booleanPreferencesKey("notify_admin")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
        val LAST_ROUTE = stringPreferencesKey("last_route")
    }

    val settingsFlow: Flow<AppUserSettings> = context.prefsDataStore.data.map { p ->
        AppUserSettings(
            smsMonitoringEnabled = p[Keys.SMS_MONITORING] ?: false,
            smsConsentAccepted = p[Keys.SMS_CONSENT] ?: false,
            enabledSmsProviders = p[Keys.SMS_PROVIDERS]
                ?: setOf("M-Pesa", "Tigo Pesa", "Airtel Money", "HaloPesa"),
            notifyPayments = p[Keys.NOTIFY_PAYMENTS] ?: true,
            notifyRecharges = p[Keys.NOTIFY_RECHARGES] ?: true,
            notifyBalanceAlerts = p[Keys.NOTIFY_BALANCE] ?: true,
            notifyAdminAlerts = p[Keys.NOTIFY_ADMIN] ?: true,
            disclaimerAccepted = p[Keys.DISCLAIMER_ACCEPTED] ?: false
        )
    }

    suspend fun current(): AppUserSettings = settingsFlow.first()

    suspend fun setSmsConsentAndEnabled(consentAccepted: Boolean, enabled: Boolean) {
        context.prefsDataStore.edit {
            it[Keys.SMS_CONSENT] = consentAccepted
            it[Keys.SMS_MONITORING] = consentAccepted && enabled
        }
    }

    suspend fun toggleSmsProvider(provider: String, enabled: Boolean) {
        context.prefsDataStore.edit { prefs ->
            val current = prefs[Keys.SMS_PROVIDERS]
                ?: setOf("M-Pesa", "Tigo Pesa", "Airtel Money", "HaloPesa")
            prefs[Keys.SMS_PROVIDERS] = if (enabled) current + provider else current - provider
        }
    }

    suspend fun updateNotificationSetting(
        payments: Boolean? = null,
        recharges: Boolean? = null,
        balance: Boolean? = null,
        admin: Boolean? = null
    ) {
        context.prefsDataStore.edit { prefs ->
            payments?.let { prefs[Keys.NOTIFY_PAYMENTS] = it }
            recharges?.let { prefs[Keys.NOTIFY_RECHARGES] = it }
            balance?.let { prefs[Keys.NOTIFY_BALANCE] = it }
            admin?.let { prefs[Keys.NOTIFY_ADMIN] = it }
        }
    }

    suspend fun acceptDisclaimer() {
        context.prefsDataStore.edit { it[Keys.DISCLAIMER_ACCEPTED] = true }
    }
}
