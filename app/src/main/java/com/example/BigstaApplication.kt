package com.example

import android.app.Application
import android.content.Context
import com.example.core.database.BigstaDatabase
import com.example.core.network.ConnectivityMonitor
import com.example.core.network.SupabaseRestClient
import com.example.core.security.SessionManager
import com.example.core.utilities.AppPreferences
import com.example.data.repositories.AdminRepository
import com.example.data.repositories.AuthRepository
import com.example.data.repositories.PaymentRepository
import com.example.data.repositories.ServiceRequestRepository
import com.example.data.repositories.SmsTransactionRepository
import com.example.data.repositories.TemplateRepository
import com.example.features.notifications.BigstaNotificationManager

class AppContainer(context: Context) {
    val restClient = SupabaseRestClient()
    val database = BigstaDatabase.getInstance(context)
    val sessionManager = SessionManager(context)
    val appPreferences = AppPreferences(context)
    val connectivityMonitor = ConnectivityMonitor(context, restClient)
    val notificationManager = BigstaNotificationManager(
        context = context,
        notificationDao = database.appNotificationDao(),
        appPreferences = appPreferences
    )

    val authRepository = AuthRepository(restClient, sessionManager)
    val paymentRepository = PaymentRepository(restClient, sessionManager)
    val serviceRequestRepository = ServiceRequestRepository(restClient, sessionManager)
    val templateRepository = TemplateRepository(restClient, sessionManager, database.cachedTemplateDao())
    val adminRepository = AdminRepository(restClient, sessionManager)
    val smsTransactionRepository = SmsTransactionRepository(
        context = context,
        smsDao = database.smsTransactionDao(),
        pendingUploadDao = database.pendingUploadDao(),
        restClient = restClient,
        sessionManager = sessionManager,
        appPreferences = appPreferences,
        notificationManager = notificationManager
    )
}

class BigstaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationManager.ensureChannelsCreated()
    }
}
