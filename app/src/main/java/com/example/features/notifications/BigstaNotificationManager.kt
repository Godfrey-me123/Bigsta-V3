package com.example.features.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.database.AppNotificationDao
import com.example.core.database.AppNotificationEntity
import com.example.core.utilities.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class NotificationCategory(val channelId: String, val channelName: String, val importance: Int) {
    PAYMENT("bigsta_payments", "Payments & Submissions", NotificationManager.IMPORTANCE_HIGH),
    RECHARGE("bigsta_recharges", "Recharge Approvals", NotificationManager.IMPORTANCE_HIGH),
    BALANCE("bigsta_balance", "Token Balance Updates", NotificationManager.IMPORTANCE_DEFAULT),
    ALERT("bigsta_alerts", "Account Alerts", NotificationManager.IMPORTANCE_HIGH),
    ADMIN("bigsta_admin", "Administrator Alerts", NotificationManager.IMPORTANCE_HIGH),
    FAILURE("bigsta_failures", "Processing Failures", NotificationManager.IMPORTANCE_DEFAULT)
}

class BigstaNotificationManager(
    private val context: Context,
    private val notificationDao: AppNotificationDao,
    private val appPreferences: AppPreferences
) {
    companion object {
        const val EXTRA_TARGET_ROUTE = "extra_target_route"
    }

    fun ensureChannelsCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            NotificationCategory.entries.forEach { cat ->
                val channel = NotificationChannel(cat.channelId, cat.channelName, cat.importance)
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun notifyEvent(
        eventKey: String,
        title: String,
        body: String,
        category: NotificationCategory,
        targetRoute: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            // Deduplicate: insertIfAbsent returns -1 if eventKey already exists
            val insertedRowId = notificationDao.insertIfAbsent(
                AppNotificationEntity(
                    eventKey = eventKey,
                    title = title,
                    body = body,
                    category = category.name,
                    targetRoute = targetRoute
                )
            )
            if (insertedRowId == -1L) return@launch

            val prefs = appPreferences.current()
            val allowedByUser = when (category) {
                NotificationCategory.PAYMENT -> prefs.notifyPayments
                NotificationCategory.RECHARGE -> prefs.notifyRecharges
                NotificationCategory.BALANCE -> prefs.notifyBalanceAlerts
                NotificationCategory.ADMIN -> prefs.notifyAdminAlerts
                NotificationCategory.ALERT, NotificationCategory.FAILURE -> true
            }
            if (!allowedByUser) return@launch

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return@launch
            }

            ensureChannelsCreated()

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_ROUTE, targetRoute)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                eventKey.hashCode(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, category.channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(eventKey.hashCode(), notification)
            } catch (_: SecurityException) {
            }
        }
    }
}
