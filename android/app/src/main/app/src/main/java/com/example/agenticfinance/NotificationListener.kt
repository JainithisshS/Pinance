package com.example.agenticfinance

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    // Apps we should NEVER process (social, chat, entertainment, system)
    private val ignoredPackages = setOf(
        "com.whatsapp", "com.instagram.android", "com.facebook.orca",
        "com.snapchat.android", "com.twitter.android", "org.telegram.messenger",
        "com.spotify.music", "com.google.android.youtube",
        "com.google.android.gm",  // Gmail
        "com.android.systemui", "com.android.vending",
        "com.google.android.apps.photos",
    )

    // Keywords that indicate a genuine banking debit/credit SMS
    private val bankingKeywords = listOf(
        "debited", "credited", "debit", "credit",
        "spent", "received", "withdrawn", "transferred",
        "a/c", "account", "acct",
        "upi", "neft", "imps", "rtgs", "atm",
        "bal", "avl bal", "balance",
        "xxxx", "ending",
    )

    // Amount pattern: Rs/INR/₹ followed by a number, or number followed by "rupees"
    private val amountRegex = Regex(
        """(?:inr|rs\.?|₹)\s*[0-9,]+\.?[0-9]*|[0-9,]+\.?[0-9]*\s*rupees""",
        RegexOption.IGNORE_CASE
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // ── Skip notifications from known non-financial apps ──
        if (sbn.packageName in ignoredPackages) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val rawMessage = "$title $text".trim()

        if (rawMessage.isEmpty()) return

        Log.d("NotificationListener", "Checking [${sbn.packageName}]: ${rawMessage.take(80)}")

        // ── Only process if it looks like a banking debit/credit SMS ──
        if (!isFinancialMessage(rawMessage)) {
            Log.d("NotificationListener", "Skipped non-financial: ${rawMessage.take(60)}")
            return
        }

        Log.d("NotificationListener", "Sending financial SMS to backend")
        sendToBackend(rawMessage)
    }

    /**
     * Returns true only if the message contains BOTH a money amount
     * AND at least one banking keyword (debited, credited, a/c, upi, etc.).
     */
    private fun isFinancialMessage(message: String): Boolean {
        val lower = message.lowercase()

        val hasBankingKeyword = bankingKeywords.any { it in lower }
        if (!hasBankingKeyword) return false

        val hasAmount = amountRegex.containsMatchIn(message)
        return hasAmount
    }

    private fun sendToBackend(raw: String) {
        ioScope.launch {
            try {
                val body = ParseMessageRequestDto(raw_message = raw)
                val tx = ApiClient.api.parseMessage(body)
                Log.d("NotificationListener", "Parsed transaction: ₹${tx.amount} [${tx.category}]")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("NotificationListener", "Error sending to backend", e)
            }
        }
    }
}
