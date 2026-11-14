package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.features.notifications.BigstaNotificationManager
import com.example.ui.navigation.BigstaAppRoot
import com.example.ui.theme.BigstaTheme

class MainActivity : ComponentActivity() {

    private var pendingSharedSmsText by mutableStateOf<String?>(null)
    private var pendingNotificationRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val appContainer = (application as BigstaApplication).container

        setContent {
            BigstaTheme {
                BigstaAppRoot(
                    appContainer = appContainer,
                    sharedSmsText = pendingSharedSmsText,
                    onConsumedSharedSms = { pendingSharedSmsText = null },
                    notificationTargetRoute = pendingNotificationRoute,
                    onConsumedNotificationRoute = { pendingNotificationRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                pendingSharedSmsText = sharedText
            }
        }
        val targetRoute = intent.getStringExtra(BigstaNotificationManager.EXTRA_TARGET_ROUTE)
        if (!targetRoute.isNullOrBlank()) {
            pendingNotificationRoute = targetRoute
        }
    }
}
