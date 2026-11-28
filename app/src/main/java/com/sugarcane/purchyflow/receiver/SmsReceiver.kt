package com.sugarcane.purchyflow.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sugarcane.purchyflow.PurchyApplication
import com.sugarcane.purchyflow.parser.CaneMessageType
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.security.CaneMessageFilter
import com.sugarcane.purchyflow.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Direct SMS BroadcastReceiver with Strict Privacy & Zero Retention Shield.
 *
 * Intercepts cellular text messages (SMS) ONLY from verified Sugarcane Department
 * or Sugar Mill sources. All non-cane messages (OTPs, personal texts) are rejected
 * immediately without logging or persistence.
 */
class SmsReceiver : BroadcastReceiver() {

    private val TAG = "SmsReceiver"
    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Extract sender and build message
            var sender = ""
            val fullBodyBuilder = StringBuilder()

            for (sms in messages) {
                if (sender.isEmpty()) {
                    sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: ""
                }
                fullBodyBuilder.append(sms.displayMessageBody ?: sms.messageBody ?: "")
            }

            val fullMessage = fullBodyBuilder.toString()
            if (fullMessage.isBlank()) return

            // =========================================================================
            // ZERO-RETENTION GATEKEEPER: Whitelist & Content Signature Filter
            // Non-cane SMS (Bank OTPs, Personal SMS) are discarded IMMEDIATELY here.
            // NEVER written to logs, NEVER saved to database.
            // =========================================================================
            if (!CaneMessageFilter.shouldProcess(sender, fullMessage)) {
                // Instantly discard and exit
                return
            }

            SecureLog.i(TAG, "Sugarcane SMS verified from approved sender/pattern. Processing...")

            val messageType = TokenMessageParser.identifyMessageType(fullMessage)
            val application = context.applicationContext as? PurchyApplication ?: return
            val repository = application.repository

            receiverScope.launch {
                try {
                    when (messageType) {
                        CaneMessageType.TAUL_RECEIPT -> {
                            val parsedReceipt = TokenMessageParser.parseWeighmentReceipt(fullMessage)
                            if (parsedReceipt != null) {
                                val linked = repository.linkWeighmentReceipt(parsedReceipt)
                                SecureLog.i(TAG, "Taul weighment receipt correlated: $linked for Purchy #${CaneMessageFilter.maskId(parsedReceipt.purchyNo)}")
                                if (linked) {
                                    com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyWeighmentCompleted(context, parsedReceipt)
                                }
                            }
                        }
                        CaneMessageType.PURCHY_TOKEN -> {
                            val parsedToken = TokenMessageParser.parseTokenMessage(fullMessage)
                            if (parsedToken != null) {
                                val saved = repository.saveParsedToken(parsedToken)
                                SecureLog.i(TAG, "Purchy token saved: $saved for Purchy #${CaneMessageFilter.maskId(parsedToken.purchyNo)}")
                                if (saved) {
                                    val token = repository.createTokenFromParsed(parsedToken)
                                    com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyNewToken(context, token)
                                }
                            }
                        }
                        else -> {
                            // Fallback attempts
                            val parsedReceipt = TokenMessageParser.parseWeighmentReceipt(fullMessage)
                            if (parsedReceipt != null && repository.linkWeighmentReceipt(parsedReceipt)) {
                                com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyWeighmentCompleted(context, parsedReceipt)
                            } else {
                                val parsedToken = TokenMessageParser.parseTokenMessage(fullMessage)
                                if (parsedToken != null && repository.saveParsedToken(parsedToken)) {
                                    val token = repository.createTokenFromParsed(parsedToken)
                                    com.sugarcane.purchyflow.notification.PurchyNotificationHelper.notifyNewToken(context, token)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    SecureLog.e(TAG, "Error processing cane SMS: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Failed reading SMS: ${e.message}", e)
        }
    }
}
