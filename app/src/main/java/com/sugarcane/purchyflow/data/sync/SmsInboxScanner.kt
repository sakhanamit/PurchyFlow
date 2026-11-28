package com.sugarcane.purchyflow.data.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import com.sugarcane.purchyflow.util.SecureLog as Log
import androidx.core.content.ContextCompat
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.data.repository.PurchyRepository
import com.sugarcane.purchyflow.parser.CaneMessageType
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsSyncResult(
    val scannedCount: Int = 0,
    val caneMessagesFound: Int = 0,
    val tokensImported: Int = 0,
    val receiptsLinked: Int = 0,
    val seasonName: String = "",
    val isSuccess: Boolean = false,
    val message: String = ""
)

/**
 * Data model for a discovered Sugarcane SMS from the device inbox.
 * Allows the user to preview message details and choose which messages to sync.
 */
data class DiscoveredSmsItem(
    val smsId: String,
    val sender: String,
    val dateMs: Long,
    val dateStr: String,
    val rawBody: String,
    val messageType: CaneMessageType,
    val purchyOrReceiptNo: String,
    val growerName: String,
    val growerCode: String,
    val weightStr: String,
    val societyOrMill: String,
    val isAlreadySynced: Boolean,     // Active, Dispatched, or Audit in DB
    val isPreviouslyDeleted: Boolean, // Archived by user
    var isSelected: Boolean = false   // Checked by user in UI
)

/**
 * Scans and discovers existing SMS inbox messages for the sugarcane season.
 * Pulls both Token SMS (before weight) and Taul SMS (after weighment).
 *
 * Provides selective sync: users can preview all messages and choose which ones to import.
 * Respects deletions: previously deleted messages are never silently re-imported.
 */
class SmsInboxScanner(
    private val context: Context,
    private val repository: PurchyRepository
) {
    private val TAG = "SmsInboxScanner"

    /**
     * Discovers all Sugarcane SMS messages in the device's inbox for the current season.
     * Evaluates database presence to tag items as New, Already Synced, or Previously Deleted.
     */
    suspend fun discoverSeasonMessages(): List<DiscoveredSmsItem> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot discover messages: READ_SMS permission is not granted.")
            return@withContext emptyList()
        }

        val discovered = mutableListOf<DiscoveredSmsItem>()
        val inboxUri: Uri = Telephony.Sms.Inbox.CONTENT_URI ?: Uri.parse("content://sms/inbox")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        try {
            val cursor = context.contentResolver.query(inboxUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val idIndex = c.getColumnIndex(Telephony.Sms._ID)
                val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)

                while (c.moveToNext()) {
                    val smsId = if (idIndex != -1) c.getString(idIndex) else System.currentTimeMillis().toString()
                    val body = if (bodyIndex != -1) c.getString(bodyIndex) else null
                    val sender = if (addressIndex != -1) c.getString(addressIndex) else ""
                    val smsDateMs = if (dateIndex != -1) c.getLong(dateIndex) else System.currentTimeMillis()

                    if (body.isNullOrBlank()) continue
                    if (!com.sugarcane.purchyflow.security.CaneMessageFilter.shouldProcess(sender, body)) continue

                    val messageType = TokenMessageParser.identifyMessageType(body)
                    if (messageType == CaneMessageType.UNKNOWN) continue

                    val formattedDate = DateUtil.formatDate(smsDateMs)

                    when (messageType) {
                        CaneMessageType.PURCHY_TOKEN -> {
                            val parsed = TokenMessageParser.parseTokenMessage(body)
                            if (parsed != null && DateUtil.isWithinCurrentCaneSeason(parsed.issueDateEpochMs)) {
                                val existing = repository.getByPurchyNo(parsed.purchyNo)
                                val isDeleted = existing?.status == TokenStatus.ARCHIVED
                                val isSynced = existing != null && !isDeleted

                                discovered.add(
                                    DiscoveredSmsItem(
                                        smsId = smsId,
                                        sender = sender,
                                        dateMs = parsed.issueDateEpochMs,
                                        dateStr = parsed.issueDateStr.ifEmpty { formattedDate },
                                        rawBody = body,
                                        messageType = CaneMessageType.PURCHY_TOKEN,
                                        purchyOrReceiptNo = parsed.purchyNo,
                                        growerName = parsed.growerName,
                                        growerCode = parsed.growerCode,
                                        weightStr = parsed.allottedWeight,
                                        societyOrMill = parsed.society,
                                        isAlreadySynced = isSynced,
                                        isPreviouslyDeleted = isDeleted,
                                        isSelected = !isSynced && !isDeleted // Checked by default ONLY if new
                                    )
                                )
                            }
                        }
                        CaneMessageType.TAUL_RECEIPT -> {
                            val parsed = TokenMessageParser.parseWeighmentReceipt(body)
                            if (parsed != null) {
                                val receiptEpochMs = DateUtil.parseDate(parsed.dispatchDateStr)?.time ?: smsDateMs
                                if (DateUtil.isWithinCurrentCaneSeason(receiptEpochMs)) {
                                    val cleanPurchy = parsed.purchyNo.orEmpty()
                                    val cleanReceipt = parsed.receiptNo.orEmpty()
                                    val existing = repository.findDuplicate(cleanPurchy, cleanReceipt)
                                        ?: (if (cleanPurchy.isNotEmpty()) repository.getByPurchyNo(cleanPurchy) else null)
                                        ?: (if (cleanReceipt.isNotEmpty()) repository.getByReceiptNo(cleanReceipt) else null)

                                    val isDeleted = existing?.status == TokenStatus.ARCHIVED
                                    val isSynced = existing != null && !isDeleted

                                    discovered.add(
                                        DiscoveredSmsItem(
                                            smsId = smsId,
                                            sender = sender,
                                            dateMs = receiptEpochMs,
                                            dateStr = parsed.dispatchDateStr ?: formattedDate,
                                            rawBody = body,
                                            messageType = CaneMessageType.TAUL_RECEIPT,
                                            purchyOrReceiptNo = cleanReceipt.ifEmpty { cleanPurchy },
                                            growerName = parsed.growerName ?: "GROWER",
                                            growerCode = parsed.growerCode.orEmpty(),
                                            weightStr = parsed.actualNettedWeight,
                                            societyOrMill = parsed.societyOrMill ?: "Mill",
                                            isAlreadySynced = isSynced,
                                            isPreviouslyDeleted = isDeleted,
                                            isSelected = !isSynced && !isDeleted
                                        )
                                    )
                                }
                            }
                        }
                        CaneMessageType.UNKNOWN -> {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering SMS messages: ${e.message}", e)
        }

        discovered
    }

    /**
     * Synchronizes exclusively the user-selected messages into the database.
     * Reactivates archived records if the user explicitly checked them.
     */
    suspend fun syncSelectedMessages(items: List<DiscoveredSmsItem>): SmsSyncResult = withContext(Dispatchers.IO) {
        val selected = items.filter { it.isSelected }
        if (selected.isEmpty()) {
            return@withContext SmsSyncResult(isSuccess = true, message = "No messages selected to sync.")
        }

        val season = DateUtil.getCurrentCaneSeason()
        var tokensImported = 0
        var receiptsLinked = 0

        // Process in chronological order (Tokens first, then weighments)
        val sortedSelected = selected.sortedWith(
            compareBy<DiscoveredSmsItem> { if (it.messageType == CaneMessageType.PURCHY_TOKEN) 0 else 1 }
                .thenBy { it.dateMs }
        )

        for (item in sortedSelected) {
            when (item.messageType) {
                CaneMessageType.PURCHY_TOKEN -> {
                    val parsed = TokenMessageParser.parseTokenMessage(item.rawBody)
                    if (parsed != null) {
                        val inserted = repository.saveParsedToken(parsed, forceReactivate = true)
                        if (inserted) tokensImported++
                    }
                }
                CaneMessageType.TAUL_RECEIPT -> {
                    val parsed = TokenMessageParser.parseWeighmentReceipt(item.rawBody)
                    if (parsed != null) {
                        val linked = repository.linkWeighmentReceipt(parsed, forceReactivate = true)
                        if (linked) receiptsLinked++
                    }
                }
                CaneMessageType.UNKNOWN -> {}
            }
        }

        val msg = when {
            tokensImported > 0 && receiptsLinked > 0 ->
                "Synced $tokensImported tokens and $receiptsLinked weighments"
            tokensImported > 0 ->
                "Synced $tokensImported cane tokens"
            receiptsLinked > 0 ->
                "Linked $receiptsLinked mill weighments"
            else ->
                "Selected records are already up to date"
        }

        SmsSyncResult(
            scannedCount = items.size,
            caneMessagesFound = selected.size,
            tokensImported = tokensImported,
            receiptsLinked = receiptsLinked,
            seasonName = season.seasonName,
            isSuccess = true,
            message = msg
        )
    }

    /**
     * Full season inbox scan (legacy / automated path).
     * Strictly preserves deletions: never silently reactivates archived records.
     */
    suspend fun scanCurrentSeasonInbox(): SmsSyncResult = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Cannot scan inbox: READ_SMS permission is not granted.")
            return@withContext SmsSyncResult(
                isSuccess = false,
                message = "READ_SMS permission is required to read existing messages."
            )
        }

        val season = DateUtil.getCurrentCaneSeason()
        var scannedCount = 0
        var caneMessagesFound = 0
        var tokensImported = 0
        var receiptsLinked = 0

        val inboxUri: Uri = Telephony.Sms.Inbox.CONTENT_URI ?: Uri.parse("content://sms/inbox")
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        try {
            val cursor = context.contentResolver.query(inboxUri, projection, null, null, sortOrder)
            cursor?.use { c ->
                val bodyIndex = c.getColumnIndex(Telephony.Sms.BODY)
                val addressIndex = c.getColumnIndex(Telephony.Sms.ADDRESS)
                val dateIndex = c.getColumnIndex(Telephony.Sms.DATE)

                while (c.moveToNext()) {
                    scannedCount++
                    val body = if (bodyIndex != -1) c.getString(bodyIndex) else null
                    val sender = if (addressIndex != -1) c.getString(addressIndex) else ""
                    val smsDateMs = if (dateIndex != -1) c.getLong(dateIndex) else System.currentTimeMillis()

                    if (body.isNullOrBlank()) continue
                    if (!com.sugarcane.purchyflow.security.CaneMessageFilter.shouldProcess(sender, body)) continue

                    val messageType = TokenMessageParser.identifyMessageType(body)
                    if (messageType == CaneMessageType.UNKNOWN) continue

                    when (messageType) {
                        CaneMessageType.PURCHY_TOKEN -> {
                            val parsed = TokenMessageParser.parseTokenMessage(body)
                            if (parsed != null && DateUtil.isWithinCurrentCaneSeason(parsed.issueDateEpochMs)) {
                                caneMessagesFound++
                                // forceReactivate = false: deleted messages remain deleted
                                val inserted = repository.saveParsedToken(parsed, forceReactivate = false)
                                if (inserted) tokensImported++
                            }
                        }
                        CaneMessageType.TAUL_RECEIPT -> {
                            val parsed = TokenMessageParser.parseWeighmentReceipt(body)
                            if (parsed != null) {
                                val receiptEpochMs = DateUtil.parseDate(parsed.dispatchDateStr)?.time ?: smsDateMs
                                if (DateUtil.isWithinCurrentCaneSeason(receiptEpochMs)) {
                                    caneMessagesFound++
                                    // forceReactivate = false: deleted messages remain deleted
                                    val linked = repository.linkWeighmentReceipt(parsed, forceReactivate = false)
                                    if (linked) receiptsLinked++
                                }
                            }
                        }
                        CaneMessageType.UNKNOWN -> {}
                    }
                }
            }

            val syncMsg = when {
                tokensImported > 0 && receiptsLinked > 0 ->
                    "Imported $tokensImported tokens & $receiptsLinked weighments for Season ${season.seasonName}"
                tokensImported > 0 ->
                    "Imported $tokensImported cane tokens for Season ${season.seasonName}"
                receiptsLinked > 0 ->
                    "Linked $receiptsLinked mill weighments for Season ${season.seasonName}"
                caneMessagesFound > 0 ->
                    "All $caneMessagesFound cane messages are up to date (${season.seasonName})"
                else ->
                    "No sugarcane messages found in inbox for Season ${season.seasonName}"
            }

            SmsSyncResult(
                scannedCount = scannedCount,
                caneMessagesFound = caneMessagesFound,
                tokensImported = tokensImported,
                receiptsLinked = receiptsLinked,
                seasonName = season.seasonName,
                isSuccess = true,
                message = syncMsg
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS inbox: ${e.message}", e)
            SmsSyncResult(
                scannedCount = scannedCount,
                isSuccess = false,
                message = "Failed to scan SMS inbox: ${e.localizedMessage}"
            )
        }
    }
}
