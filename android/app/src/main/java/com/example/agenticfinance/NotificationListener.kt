package com.example.agenticfinance

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val rawMessage = "$title $text".trim()

        if (rawMessage.isEmpty()) return

        sendToBackend(rawMessage)
    }

    private fun sendToBackend(raw: String) {
        ioScope.launch {
            try {
                val body = ParseMessageRequestDto(raw_message = raw)
                val tx = ApiClient.api.parseMessage(body)
                Log.d("NotificationListener", "Sent transaction: $tx")
            } catch (e: Exception) {
                Log.e("NotificationListener", "Error sending to backend", e)
            }
        }
    }
}
