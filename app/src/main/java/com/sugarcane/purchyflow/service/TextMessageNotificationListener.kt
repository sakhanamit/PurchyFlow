package com.sugarcane.purchyflow.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.sugarcane.purchyflow.PurchyApplication
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.security.CaneMessageFilter
import com.sugarcane.purchyflow.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Background Android Notification Listener Service with Strict Privacy Shield.
 *
 * Intercepts notifications from messaging apps ONLY when they match verified
 * Sugarcane Department (CANEDEPTT) or Sugar Mill weighbridge receipts.
 *
 * All non-cane notifications (Bank OTPs, Personal WhatsApp/SMS, Financial alerts)
 * are immediately rejected and dropped from memory without logging or persistence.
 */
class TextMessageNotificationListener : NotificationListenerService() {

    private val TAG = "TextMessageListener"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":").toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
            return false
        }

        fun getNotificationSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        SecureLog.i(TAG, "Notification Listener connected.")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SecureLog.w(TAG, "Notification Listener disconnected.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification?.extras ?: return

        // Extract message texts (title, text, big text)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val combinedMessage = when {
            bigText.isNotEmpty() -> bigText
            text.isNotEmpty() -> text
            else -> title
        }

        if (combinedMessage.isBlank()) return

        // =========================================================================
        // ZERO-RETENTION GATEKEEPER: Whitelist & Content Signature Filter
        // Non-cane SMS / notifications (e.g. Bank OTPs, Personal SMS) are dropped
        // IMMEDIATELY here. NEVER written to logs, NEVER saved to database.
        // =========================================================================
        if (!CaneMessageFilter.shouldProcess(title, combinedMessage)) {
            // Discard immediately from memory
            return
        }

        SecureLog.i(TAG, "Cane notification verified from [${CaneMessageFilter.maskId(title)}]. Processing...")

        val repository = (applicationContext as? PurchyApplication)?.repository ?: return

        serviceScope.launch {
            try {
                // 1. Try parsing as a Weighment Receipt first
                if (TokenMessageParser.isWeighmentReceipt(combinedMessage)) {
                    val parsedReceipt = TokenMessageParser.parseWeighmentReceipt(combinedMessage)
                    if (parsedReceipt != null) {
                        val linked = repository.linkWeighmentReceipt(parsedReceipt)
                        SecureLog.i(TAG, "Receipt correlation result: $linked for Purchy #${CaneMessageFilter.maskId(parsedReceipt.purchyNo)}")
                        if (linked) {
                            com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyWeighmentCompleted(applicationContext, parsedReceipt)
                        }
                        return@launch
                    }
                }

                // 2. Otherwise parse as a Token (Purchy) Message
                val parsedToken = TokenMessageParser.parseTokenMessage(combinedMessage)
                if (parsedToken != null) {
                    val saved = repository.saveParsedToken(parsedToken)
                    SecureLog.i(TAG, "Token saved result: $saved for Purchy #${CaneMessageFilter.maskId(parsedToken.purchyNo)}")
                    if (saved) {
                        val token = repository.createTokenFromParsed(parsedToken)
                        com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyNewToken(applicationContext, token)
                    }
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Error handling notification in background: ${e.message}", e)
            }
        }
    }
}
