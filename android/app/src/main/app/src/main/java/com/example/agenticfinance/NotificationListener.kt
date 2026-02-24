package com.example.agenticfinance

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val TAG = "NotiListener"
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "✅ NotificationListener CONNECTED and running!")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "❌ NotificationListener DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val rawMessage = "$title $text".trim()

        Log.d(TAG, "📩 Notification from [$pkg]: ${rawMessage.take(100)}")

        if (rawMessage.isEmpty()) {
            Log.d(TAG, "Empty message, skipping")
            return
        }

        // Send ALL notifications to backend — backend decides what to keep
        sendToBackend(rawMessage)
    }

    private fun sendToBackend(raw: String) {
        ioScope.launch {
            try {
                Log.d(TAG, "📤 Sending to backend: ${raw.take(80)}")
                val body = ParseMessageRequestDto(raw_message = raw)
                val tx = ApiClient.api.parseMessage(body)
                Log.d(TAG, "✅ Parsed: ₹${tx.amount} [${tx.category}] merchant=${tx.merchant}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "❌ Backend error: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }
}
