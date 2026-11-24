package com.sugarcane.purchyflow.data.repository

import android.util.Log
import com.sugarcane.purchyflow.data.local.PurchyDao
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.parser.ParsedToken
import com.sugarcane.purchyflow.parser.ParsedWeighmentReceipt
import com.sugarcane.purchyflow.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date

class PurchyRepository(private val dao: PurchyDao) {

    private val TAG = "PurchyRepository"

    fun getActiveTokens(query: String = ""): Flow<List<PurchyToken>> {
        return if (query.isBlank()) {
            dao.getActiveTokensFifo()
        } else {
            dao.searchActiveTokens(query.trim())
        }
    }

    /**
     * Retrieves all Cane Tokens (both active valid and expired/completed) synced from SMS.
     */
    fun getAllCaneTokens(query: String = ""): Flow<List<PurchyToken>> {
        return if (query.isBlank()) {
            dao.getAllCaneTokens()
        } else {
            dao.searchAllCaneTokens(query.trim())
        }
    }

    fun getDispatchedReceipts(query: String = ""): Flow<List<PurchyToken>> {
        return if (query.isBlank()) {
            dao.getDispatchedReceipts()
        } else {
            dao.searchDispatchedReceipts(query.trim())
        }
    }

    fun getActiveCount(): Flow<Int> = dao.getActiveCount()
    fun getDispatchedCount(): Flow<Int> = dao.getDispatchedCount()

    /**
     * Live compiled total actual netted weight from mill-weighed dispatched loads.
     */
    fun getTotalDispatchedWeight(): Flow<Double> = dao.getTotalDispatchedWeight().map { it ?: 0.0 }

    /**
     * Fetches only dispatched tokens received & weighed at sugar mill for export.
     */
    suspend fun getDispatchedTokensForExport(): List<PurchyToken> = withContext(Dispatchers.IO) {
        dao.getDispatchedTokensForExport()
    }

    fun createTokenFromParsed(parsed: ParsedToken): PurchyToken {
        return PurchyToken(
            purchyNo = parsed.purchyNo,
            growerName = parsed.growerName,
            growerCode = parsed.growerCode,
            ugc = parsed.ugc,
            caneType = parsed.caneType,
            fc = parsed.fc,
            allottedWeight = parsed.allottedWeight,
            allottedWeightNumber = parsed.allottedWeightNumber,
            issueDateStr = parsed.issueDateStr,
            issueDateEpochMs = parsed.issueDateEpochMs,
            validUntilStr = parsed.validUntilStr,
            validUntilEpochMs = parsed.validUntilEpochMs,
            society = parsed.society,
            centre = parsed.centre,
            status = TokenStatus.ACTIVE,
            rawTokenMessage = parsed.rawMessage
        )
    }

    /**
     * Saves parsed token with deduplication and cane season validation.
     * Returns true if newly inserted/updated, false if rejected.
     */
    suspend fun saveParsedToken(parsed: ParsedToken, forceReactivate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // Strict Cane Season Check: Only accept messages from the current season (Nov to Apr next year)
        if (!DateUtil.isWithinCurrentCaneSeason(parsed.issueDateEpochMs)) {
            val season = DateUtil.getCurrentCaneSeason()
            Log.w(TAG, "Rejected Token #${parsed.purchyNo}: Date ${parsed.issueDateStr} is outside current cane season ${season.seasonName} (${season.startDateStr} to ${season.endDateStr})")
            return@withContext false
        }

        // Deduplication Logic: Any incoming message containing a Purchy Number already present in the database
        // must be automatically rejected to ensure zero double entries.
        val existing = dao.getByPurchyNo(parsed.purchyNo)
        if (existing != null) {
            if (existing.status == TokenStatus.ARCHIVED) {
                if (forceReactivate) {
                    val token = createTokenFromParsed(parsed).copy(status = TokenStatus.ACTIVE, updatedAt = System.currentTimeMillis())
                    dao.updateToken(token)
                    Log.i(TAG, "Reactivated previously archived Token #${parsed.purchyNo} on user request")
                    return@withContext true
                } else {
                    Log.i(TAG, "Preserving deletion: skipped archived Token #${parsed.purchyNo}")
                    return@withContext false
                }
            }
            Log.w(TAG, "Deduplication: Automatically rejected incoming Token #${parsed.purchyNo} - already exists in database (zero double entries)")
            return@withContext false
        }

        // Reverse Correlation: Check if a standalone weighment receipt was already recorded
        // for this grower and date, waiting for the Cane Token SMS to arrive.
        val standaloneReceipts = dao.getStandaloneDispatchedReceipts()
        if (standaloneReceipts.isNotEmpty()) {
            val reverseCorrelation = com.sugarcane.purchyflow.data.correlation.CorrelationEngine.findMatchingStandaloneReceipt(
                token = parsed,
                standaloneReceipts = standaloneReceipts
            )
            if (reverseCorrelation.isMatch && reverseCorrelation.matchedToken != null) {
                val standalone = reverseCorrelation.matchedToken
                val mergedToken = createTokenFromParsed(parsed).copy(
                    status = TokenStatus.DISPATCHED,
                    actualNettedWeight = standalone.actualNettedWeight,
                    actualNettedWeightNumber = standalone.actualNettedWeightNumber,
                    dispatchDateStr = standalone.dispatchDateStr,
                    dispatchDateEpochMs = standalone.dispatchDateEpochMs,
                    weightReceiptNo = standalone.weightReceiptNo ?: standalone.purchyNo,
                    grossWeight = standalone.grossWeight,
                    tareWeight = standalone.tareWeight,
                    rawReceiptMessage = standalone.rawReceiptMessage,
                    updatedAt = System.currentTimeMillis()
                )
                dao.deleteByPurchyNo(standalone.purchyNo)
                dao.insertToken(mergedToken)
                Log.i(TAG, "CorrelationEngine reverse-correlated Token #${parsed.purchyNo} with standalone receipt #${standalone.purchyNo} (Score: ${reverseCorrelation.confidenceScore})")
                return@withContext true
            }
        }

        val token = createTokenFromParsed(parsed)
        val rowId = dao.insertToken(token)
        Log.i(TAG, "Inserted new Purchy token: ${token.purchyNo} for ${token.growerName}, rowId: $rowId")
        return@withContext rowId > 0
    }

    /**
     * Manually saves or updates a Purchy token with duplicate avoidance.
     */
    suspend fun saveManualToken(token: PurchyToken): Boolean = withContext(Dispatchers.IO) {
        val cleanPurchy = token.purchyNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
        val existing = dao.findDuplicateByNumbers(cleanPurchy, token.weightReceiptNo ?: "")
            ?: if (token.growerCode.isNotBlank()) {
                dao.findDuplicateByGrowerDateWeight(
                    growerCode = token.growerCode,
                    dateStr = token.issueDateStr ?: "",
                    weight = token.allottedWeightNumber ?: 0.0,
                    receiptNo = cleanPurchy
                )
            } else null

        if (existing != null) {
            val updated = token.copy(
                purchyNo = existing.purchyNo,
                actualNettedWeight = existing.actualNettedWeight ?: token.actualNettedWeight,
                actualNettedWeightNumber = existing.actualNettedWeightNumber ?: token.actualNettedWeightNumber,
                dispatchDateStr = existing.dispatchDateStr ?: token.dispatchDateStr,
                dispatchDateEpochMs = existing.dispatchDateEpochMs?.takeIf { it > 0L } ?: token.dispatchDateEpochMs,
                weightReceiptNo = existing.weightReceiptNo ?: token.weightReceiptNo,
                status = if (existing.status == TokenStatus.ARCHIVED) TokenStatus.ACTIVE else existing.status,
                updatedAt = System.currentTimeMillis()
            )
            dao.updateToken(updated)
            Log.i(TAG, "Duplicate avoided / reactivated: updated existing Purchy token #${existing.purchyNo}")
            true
        } else {
            val rowId = dao.insertToken(token.copy(purchyNo = cleanPurchy))
            Log.i(TAG, "Inserted new manual Purchy token #$cleanPurchy, rowId=$rowId")
            rowId > 0
        }
    }

    /**
     * Purges records that fall outside the active cane season window (Nov - Apr),
     * deletes any corrupted/placeholder records, and sanitizes legacy receipt numbers.
     */
    suspend fun purgeOutdatedSeasonRecords(): Int = withContext(Dispatchers.IO) {
        val season = DateUtil.getCurrentCaneSeason()
        val purged = dao.deleteOutsideSeason(season.startEpochMs, season.endEpochMs)
        if (purged > 0) {
            Log.i(TAG, "Purged $purged outdated records outside Cane Season ${season.seasonName}")
        }
        val sanitized = dao.sanitizeReceiptNumbers()
        if (sanitized > 0) {
            Log.i(TAG, "Sanitized $sanitized receipt numbers, removed '#MILL' prefix")
        }
        val deletedPlaceholders = dao.deleteCorruptedOrPlaceholderRecords()
        if (deletedPlaceholders > 0) {
            Log.i(TAG, "Deleted $deletedPlaceholders corrupted or placeholder 'GROWER' records")
        }
        purged + deletedPlaceholders
    }

    /**
     * Correlates an incoming Weighment Receipt with an existing Active Token.
     * Strategy:
     * 1. Validate that the weighment falls strictly within the current cane season (Nov to Apr next year).
     * 2. Try matching by unique Purchy No.
     * 3. Fallback: match by Grower Code or UGC against currently Active tokens.
     * 4. On match, updates status to DISPATCHED and attaches actual netted weight.
     * 5. If no prior active token existed, inserts a new DISPATCHED record so data is never lost.
     */
    suspend fun linkWeighmentReceipt(receipt: ParsedWeighmentReceipt, forceReactivate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val parsedDateEpoch = DateUtil.parseDate(receipt.dispatchDateStr)?.time ?: 0L
        val dispatchDateEpoch = if (parsedDateEpoch > 0) parsedDateEpoch else now
        val dispatchDateStr = receipt.dispatchDateStr ?: DateUtil.getCurrentTimestampString()

        // Strict Cane Season Check: Reject weighments outside current season
        if (!DateUtil.isWithinCurrentCaneSeason(dispatchDateEpoch)) {
            val season = DateUtil.getCurrentCaneSeason()
            Log.w(TAG, "Rejected Taul weighment: Date ${receipt.dispatchDateStr} is outside current cane season ${season.seasonName} (${season.startDateStr} to ${season.endDateStr})")
            return@withContext false
        }

        val cleanReceiptNo = receipt.receiptNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()
        val cleanPurchyNo = receipt.purchyNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()

        // State Transition & Cross-Matching Logic:
        // 1. Check direct Purchy/Receipt numbers
        var existingToken = dao.findDuplicateByNumbers(cleanPurchyNo, cleanReceiptNo)

        // 2. Correlation Engine with Fuzzy Matching:
        // Overcomes format variations by matching shared Grower Code or UGC and aligning dates.
        if (existingToken == null) {
            val unmatchedTokens = dao.getAllUnmatchedTokens()
            val correlation = com.sugarcane.purchyflow.data.correlation.CorrelationEngine.findBestMatchingToken(
                receipt = receipt,
                candidateTokens = unmatchedTokens,
                dispatchEpochMs = dispatchDateEpoch
            )
            if (correlation.isMatch && correlation.matchedToken != null) {
                existingToken = correlation.matchedToken
                Log.i(TAG, "CorrelationEngine cross-matched Weighment to Token #${existingToken.purchyNo} (Score: ${correlation.confidenceScore}): ${correlation.matchReason}")
            }
        }

        // 3. Fallback duplicate check by grower code, date, and weight
        if (existingToken == null && !receipt.growerCode.isNullOrBlank() && (receipt.actualNettedWeightNumber ?: 0.0) > 0.0) {
            existingToken = dao.findDuplicateByGrowerDateWeight(
                growerCode = receipt.growerCode,
                dateStr = dispatchDateStr,
                weight = receipt.actualNettedWeightNumber ?: 0.0,
                receiptNo = cleanReceiptNo
            )
        }

        val targetPurchyNo = existingToken?.purchyNo ?: cleanPurchyNo.ifEmpty { cleanReceiptNo }

        if (existingToken != null) {
            if (existingToken.status == TokenStatus.ARCHIVED && !forceReactivate) {
                Log.i(TAG, "Preserving deletion: skipped linking to archived Token #${existingToken.purchyNo}")
                return@withContext false
            }

            val rowsUpdated = dao.linkWeighmentReceipt(
                purchyNo = existingToken.purchyNo,
                actualWeight = receipt.actualNettedWeight,
                actualWeightNum = receipt.actualNettedWeightNumber,
                dispatchDateStr = dispatchDateStr,
                dispatchEpochMs = dispatchDateEpoch,
                receiptNo = cleanReceiptNo.ifEmpty { existingToken.weightReceiptNo },
                grossWeight = receipt.grossWeight ?: existingToken.grossWeight,
                tareWeight = receipt.tareWeight ?: existingToken.tareWeight,
                rawReceipt = receipt.rawMessage,
                updatedAt = now
            )
            if (existingToken.status == TokenStatus.ARCHIVED && forceReactivate) {
                dao.updateToken(existingToken.copy(status = TokenStatus.DISPATCHED, updatedAt = now))
            }
            Log.i(TAG, "Duplicate avoided / Linked weighment receipt to existing Purchy ${existingToken.purchyNo}: rows=$rowsUpdated")
            return@withContext true
        } else {
            // Direct standalone weighment receipt without prior active token in DB
            val effectivePurchyNo = targetPurchyNo.ifEmpty { "TAUL-${System.currentTimeMillis() % 1000000}" }
            val standaloneToken = com.sugarcane.purchyflow.data.model.PurchyToken(
                purchyNo = effectivePurchyNo,
                growerName = receipt.growerName ?: "GROWER (${receipt.growerCode ?: effectivePurchyNo})",
                growerCode = receipt.growerCode.orEmpty(),
                ugc = receipt.ugc.orEmpty(),
                caneType = "GENERAL",
                fc = "1/1",
                allottedWeight = receipt.actualNettedWeight,
                allottedWeightNumber = receipt.actualNettedWeightNumber,
                actualNettedWeight = receipt.actualNettedWeight,
                actualNettedWeightNumber = receipt.actualNettedWeightNumber,
                issueDateStr = dispatchDateStr,
                issueDateEpochMs = dispatchDateEpoch,
                validUntilStr = dispatchDateStr,
                validUntilEpochMs = dispatchDateEpoch,
                dispatchDateStr = dispatchDateStr,
                dispatchDateEpochMs = dispatchDateEpoch,
                weightReceiptNo = cleanReceiptNo.ifEmpty { effectivePurchyNo },
                grossWeight = receipt.grossWeight,
                tareWeight = receipt.tareWeight,
                society = receipt.societyOrMill ?: "MILL",
                centre = "100",
                status = TokenStatus.DISPATCHED,
                rawReceiptMessage = receipt.rawMessage,
                updatedAt = now
            )
            val rowId = dao.insertToken(standaloneToken)
            Log.i(TAG, "Created standalone dispatched token: $effectivePurchyNo, rowId=$rowId")
            return@withContext rowId > 0
        }
    }

    /**
     * Soft-deletes a token (moves to ARCHIVED) to protect against accidental deletion.
     */
    suspend fun deleteToken(purchyNo: String): Boolean = withContext(Dispatchers.IO) {
        val rows = dao.softDeleteByPurchyNo(purchyNo)
        Log.i(TAG, "Soft-deleted (archived) token with Purchy #$purchyNo: rows=$rows")
        rows > 0
    }

    suspend fun permanentDeleteToken(purchyNo: String): Boolean = withContext(Dispatchers.IO) {
        dao.deleteByPurchyNo(purchyNo) > 0
    }

    suspend fun restoreToken(purchyNo: String): Boolean = withContext(Dispatchers.IO) {
        dao.restoreByPurchyNo(purchyNo) > 0
    }

    suspend fun getByPurchyNo(purchyNo: String): PurchyToken? = withContext(Dispatchers.IO) {
        dao.getByPurchyNo(purchyNo.trim())
    }

    suspend fun getByReceiptNo(receiptNo: String): PurchyToken? = withContext(Dispatchers.IO) {
        val clean = receiptNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
        dao.getByReceiptNo(clean) ?: dao.getByReceiptNo(receiptNo.trim())
    }

    suspend fun findDuplicate(purchyNo: String, receiptNo: String): PurchyToken? = withContext(Dispatchers.IO) {
        val cleanP = purchyNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
        val cleanR = receiptNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "").trim()
        dao.findDuplicateByNumbers(cleanP, cleanR)
    }

    /**
     * Runs a retroactive correlation pass over all unmatched active tokens and standalone weighment receipts.
     * Transitions newly matched records to DISPATCHED.
     * Returns the number of newly correlated records.
     */
    suspend fun correlateAllUnmatched(): Int = withContext(Dispatchers.IO) {
        val unmatchedTokens = dao.getAllUnmatchedTokens()
        val standaloneReceipts = dao.getStandaloneDispatchedReceipts()
        if (unmatchedTokens.isEmpty() || standaloneReceipts.isEmpty()) return@withContext 0

        var correlatedCount = 0
        for (standalone in standaloneReceipts) {
            val dummyReceipt = com.sugarcane.purchyflow.parser.ParsedWeighmentReceipt(
                purchyNo = standalone.weightReceiptNo ?: standalone.purchyNo,
                growerCode = standalone.growerCode,
                ugc = standalone.ugc,
                growerName = standalone.growerName,
                societyOrMill = standalone.society,
                actualNettedWeight = standalone.actualNettedWeight ?: "${standalone.actualNettedWeightNumber ?: 0.0}ql",
                actualNettedWeightNumber = standalone.actualNettedWeightNumber ?: 0.0,
                dispatchDateStr = standalone.dispatchDateStr,
                receiptNo = standalone.weightReceiptNo ?: standalone.purchyNo,
                grossWeight = standalone.grossWeight,
                tareWeight = standalone.tareWeight,
                rawMessage = standalone.rawReceiptMessage.orEmpty()
            )

            val correlation = com.sugarcane.purchyflow.data.correlation.CorrelationEngine.findBestMatchingToken(
                receipt = dummyReceipt,
                candidateTokens = unmatchedTokens,
                dispatchEpochMs = standalone.dispatchDateEpochMs ?: standalone.createdAt
            )

            if (correlation.isMatch && correlation.matchedToken != null) {
                val matched = correlation.matchedToken
                val now = System.currentTimeMillis()
                dao.linkWeighmentReceipt(
                    purchyNo = matched.purchyNo,
                    actualWeight = dummyReceipt.actualNettedWeight,
                    actualWeightNum = dummyReceipt.actualNettedWeightNumber,
                    dispatchDateStr = dummyReceipt.dispatchDateStr ?: matched.validUntilStr,
                    dispatchEpochMs = standalone.dispatchDateEpochMs ?: matched.validUntilEpochMs,
                    receiptNo = dummyReceipt.receiptNo,
                    grossWeight = dummyReceipt.grossWeight,
                    tareWeight = dummyReceipt.tareWeight,
                    rawReceipt = dummyReceipt.rawMessage,
                    updatedAt = now
                )
                // Remove the redundant standalone record if it had a different purchyNo
                if (standalone.purchyNo != matched.purchyNo) {
                    dao.deleteByPurchyNo(standalone.purchyNo)
                }
                correlatedCount++
                Log.i(TAG, "Retroactively correlated Token #${matched.purchyNo} with standalone receipt #${standalone.purchyNo}")
            }
        }
        correlatedCount
    }

    /**
     * Unlinks a correlated Weighment Receipt from a Token if the system matched the wrong SMS.
     * Restores the Cane Token back to ACTIVE (or AUDIT_PENDING if expired),
     * and preserves the Mill Weighment as a standalone DISPATCHED record.
     */
    suspend fun unlinkToken(purchyNo: String): Boolean = withContext(Dispatchers.IO) {
        val token = dao.getByPurchyNo(purchyNo.trim()) ?: return@withContext false
        if (token.status != TokenStatus.DISPATCHED || token.actualNettedWeight == null) {
            return@withContext false
        }

        val now = System.currentTimeMillis()
        val cleanReceipt = token.weightReceiptNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()
        val standalonePurchyNo = if (cleanReceipt.isNotEmpty() && cleanReceipt != token.purchyNo) {
            cleanReceipt
        } else {
            "TAUL-${cleanReceipt.ifEmpty { (System.currentTimeMillis() % 1000000).toString() }}"
        }

        // 1. Create/Isolate Standalone Mill Weighment Record so no weighment data is lost
        val standaloneWeighment = PurchyToken(
            purchyNo = standalonePurchyNo,
            growerName = token.growerName,
            growerCode = token.growerCode,
            ugc = token.ugc,
            caneType = token.caneType,
            fc = token.fc,
            allottedWeight = token.actualNettedWeight ?: "0ql",
            allottedWeightNumber = token.actualNettedWeightNumber ?: 0.0,
            actualNettedWeight = token.actualNettedWeight,
            actualNettedWeightNumber = token.actualNettedWeightNumber,
            issueDateStr = token.dispatchDateStr ?: token.issueDateStr,
            issueDateEpochMs = token.dispatchDateEpochMs ?: token.issueDateEpochMs,
            validUntilStr = token.dispatchDateStr ?: token.validUntilStr,
            validUntilEpochMs = token.dispatchDateEpochMs ?: token.validUntilEpochMs,
            dispatchDateStr = token.dispatchDateStr,
            dispatchDateEpochMs = token.dispatchDateEpochMs,
            weightReceiptNo = cleanReceipt.ifEmpty { standalonePurchyNo },
            grossWeight = token.grossWeight,
            tareWeight = token.tareWeight,
            society = token.society,
            centre = token.centre,
            status = TokenStatus.DISPATCHED,
            rawTokenMessage = null,
            rawReceiptMessage = token.rawReceiptMessage,
            createdAt = now,
            updatedAt = now
        )

        val existingWeighment = dao.getByPurchyNo(standalonePurchyNo)
        if (existingWeighment != null) {
            dao.updateToken(standaloneWeighment)
        } else {
            dao.insertToken(standaloneWeighment)
        }

        // 2. Revert the original Cane Token back to ACTIVE (or EXPIRED if validity expired)
        val isExpired = token.validUntilEpochMs in 1 until now
        val restoredStatus = if (isExpired) TokenStatus.EXPIRED else TokenStatus.ACTIVE

        val restoredToken = token.copy(
            actualNettedWeight = null,
            actualNettedWeightNumber = null,
            dispatchDateStr = null,
            dispatchDateEpochMs = null,
            weightReceiptNo = null,
            grossWeight = null,
            tareWeight = null,
            rawReceiptMessage = null,
            status = restoredStatus,
            updatedAt = now
        )
        dao.updateToken(restoredToken)
        Log.i(TAG, "Unlinked Token #${token.purchyNo}. Cane token reverted to $restoredStatus; weighment isolated as #$standalonePurchyNo")
        true
    }

    suspend fun getAllTokensForExport(): List<PurchyToken> = withContext(Dispatchers.IO) {
        dao.getAllTokensForExport()
    }

    /**
     * Saves or correlates weighment receipt data with robust duplicate avoidance.
     */
    suspend fun saveScannedReceipt(scanned: com.sugarcane.purchyflow.parser.ScannedReceiptData): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val parsedDateEpoch = DateUtil.parseDate(scanned.dateStr)?.time ?: 0L
        val dispatchDateEpoch = if (scanned.dateEpochMs > 0) scanned.dateEpochMs else if (parsedDateEpoch > 0) parsedDateEpoch else now
        val dispatchDateStr = scanned.dateStr.ifEmpty { DateUtil.getCurrentTimestampString() }

        // Sanitize mill receipt number (no '#MILL' or '#')
        val cleanMillReceipt = scanned.millPurchyNo
            .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
            .trim()
        val cleanSocietyPurchy = scanned.societyPurchyNo
            .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
            .trim()

        // 1. Check duplicate by numbers (Purchy No or Mill Receipt No)
        var existingToken = dao.findDuplicateByNumbers(cleanSocietyPurchy, cleanMillReceipt)

        // 2. Check duplicate by grower code, date, and net weight
        if (existingToken == null && scanned.growerCode.isNotBlank() && (scanned.netWeight ?: 0.0) > 0.0) {
            existingToken = dao.findDuplicateByGrowerDateWeight(
                growerCode = scanned.growerCode,
                dateStr = dispatchDateStr,
                weight = scanned.netWeight ?: 0.0,
                receiptNo = cleanMillReceipt
            )
        }

        // 3. Fallback: match by UGC or Grower Code on active tokens
        if (existingToken == null && (scanned.ugc.isNotBlank() || scanned.growerCode.isNotBlank())) {
            val matchedActive = dao.findActiveByUgcOrCode(
                ugc = scanned.ugc,
                code = scanned.growerCode
            )
            if (matchedActive != null) {
                existingToken = matchedActive
            }
        }

        val netWeightStr = scanned.netWeight?.let { String.format(java.util.Locale.ENGLISH, "%.2f QTL", it) } ?: "0 QTL"

        if (existingToken != null) {
            val rows = dao.linkWeighmentReceipt(
                purchyNo = existingToken.purchyNo,
                actualWeight = netWeightStr,
                actualWeightNum = scanned.netWeight ?: 0.0,
                dispatchDateStr = dispatchDateStr,
                dispatchEpochMs = dispatchDateEpoch,
                receiptNo = cleanMillReceipt.ifEmpty { existingToken.weightReceiptNo ?: existingToken.purchyNo },
                grossWeight = scanned.grossWeight?.let { "$it" } ?: existingToken.grossWeight,
                tareWeight = scanned.tareWeight?.let { "$it" } ?: existingToken.tareWeight,
                rawReceipt = scanned.rawOcrText.ifEmpty { existingToken.rawReceiptMessage },
                updatedAt = now
            )
            Log.i(TAG, "Duplicate avoided / Linked OCR receipt to existing Purchy ${existingToken.purchyNo}: rows=$rows")
            return@withContext true
        } else {
            // New standalone weighment record
            val finalPurchyNo = cleanSocietyPurchy.ifEmpty {
                cleanMillReceipt.ifEmpty { "REC-${scanned.growerCode.replace('/', '-')}-${System.currentTimeMillis() % 100000}" }
            }
            val newToken = PurchyToken(
                purchyNo = finalPurchyNo,
                growerName = scanned.growerName.ifEmpty { "GROWER (${scanned.growerCode})" },
                growerCode = scanned.growerCode,
                ugc = scanned.ugc,
                caneType = scanned.caneType.ifEmpty { "GENERAL" },
                fc = "1/1",
                allottedWeight = netWeightStr,
                allottedWeightNumber = scanned.netWeight ?: 0.0,
                issueDateStr = dispatchDateStr,
                issueDateEpochMs = dispatchDateEpoch,
                validUntilStr = dispatchDateStr,
                validUntilEpochMs = dispatchDateEpoch,
                society = scanned.millOrSociety,
                centre = "Mill",
                actualNettedWeight = netWeightStr,
                actualNettedWeightNumber = scanned.netWeight ?: 0.0,
                dispatchDateStr = dispatchDateStr,
                dispatchDateEpochMs = dispatchDateEpoch,
                weightReceiptNo = cleanMillReceipt.ifEmpty { finalPurchyNo },
                grossWeight = scanned.grossWeight?.let { "$it" },
                tareWeight = scanned.tareWeight?.let { "$it" },
                status = TokenStatus.DISPATCHED,
                rawReceiptMessage = scanned.rawOcrText
            )
            val rowId = dao.insertToken(newToken)
            Log.i(TAG, "Saved OCR receipt as new Dispatched token $finalPurchyNo, rowId=$rowId")
            return@withContext rowId > 0
        }
    }
}
