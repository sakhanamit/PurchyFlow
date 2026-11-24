package com.sugarcane.purchyflow.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sugarcane.purchyflow.MainActivity
import com.sugarcane.purchyflow.R
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.parser.ParsedWeighmentReceipt

/**
 * Helper to display high-priority system notifications immediately when
 * new Purchy Tokens or Weighment Receipts arrive via SMS, notification interception,
 * or manual testing.
 */
object PurchyNotificationHelper {

    private const val TAG = "PurchyNotification"
    const val CHANNEL_ID = "purchy_live_alerts_channel"
    private const val CHANNEL_NAME = "Sugarcane Purchy & Weighment Alerts"
    private const val CHANNEL_DESC = "Instant notifications when new Purchy tokens or weighment receipts arrive"

    /**
     * Initializes the Android Notification Channel with sound, vibration, and high importance.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableLights(true)
                    lightColor = 0xFF2E7D32.toInt()
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 250, 150, 250)
                }
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created: $CHANNEL_ID")
            }
        }
    }

    /**
     * Notifies the user immediately when a new sugarcane Purchy Token is received.
     */
    fun notifyNewToken(context: Context, token: PurchyToken) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot show notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PURCHY_NO", token.purchyNo)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            token.purchyNo.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val bigText = buildString {
            appendLine("🌾 Grower: ${token.growerName} (Code: ${token.growerCode})")
            appendLine("📋 Purchy No: #${token.purchyNo}")
            appendLine("⚖️ Allotted Weight: ${token.allottedWeight} | Type: ${token.caneType}")
            appendLine("⏳ Valid Until: ${token.validUntilStr} (2-Day Window)")
            append("📍 Centre: ${token.centre} • Society: ${token.society}")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🌾 New Sugarcane Purchy #${token.purchyNo}")
            .setContentText("${token.growerName} • ${token.allottedWeight} • Valid: ${token.validUntilStr}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(0xFF2E7D32.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(token.purchyNo.hashCode(), notification)
            Log.i(TAG, "Notification successfully posted for Purchy #${token.purchyNo}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting new token notification: ${e.message}", e)
        }
    }

    /**
     * Notifies the user immediately when a Weighment Receipt is correlated and recorded.
     */
    fun notifyWeighmentCompleted(context: Context, receipt: ParsedWeighmentReceipt) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Cannot show weighment notification: POST_NOTIFICATIONS permission not granted")
                return
            }
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_PURCHY_NO", receipt.purchyNo)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            receipt.purchyNo.hashCode() + 1,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val bigText = buildString {
            appendLine("⚖️ Weighment Recorded for Purchy #${receipt.purchyNo}")
            appendLine("✅ Net Dispatched: ${receipt.actualNettedWeight}")
            if (!receipt.receiptNo.isNullOrBlank()) {
                appendLine("🧾 Receipt No: ${receipt.receiptNo}")
            }
            if (!receipt.dispatchDateStr.isNullOrBlank()) {
                appendLine("📅 Date: ${receipt.dispatchDateStr}")
            }
            if (!receipt.grossWeight.isNullOrBlank() || !receipt.tareWeight.isNullOrBlank()) {
                append("Gross: ${receipt.grossWeight ?: "-"} | Tare: ${receipt.tareWeight ?: "-"}")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚖️ Weighment Done: Purchy #${receipt.purchyNo}")
            .setContentText("Net: ${receipt.actualNettedWeight} • Receipt: ${receipt.receiptNo ?: "N/A"}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setColor(0xFF047857.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(receipt.purchyNo.hashCode() + 1, notification)
            Log.i(TAG, "Notification successfully posted for Weighment #${receipt.purchyNo}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting weighment notification: ${e.message}", e)
        }
    }
}
