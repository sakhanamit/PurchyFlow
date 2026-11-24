package com.sugarcane.purchyflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchyDao {

    /**
     * Active/Pending Tokens sorted on a strict First-In, First-Out (FIFO) basis by expiration date.
     * Tokens expiring earliest appear at the very top of the queue. Excludes archived/soft-deleted tokens.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status != 'DISPATCHED' AND status != 'ARCHIVED' ORDER BY validUntilEpochMs ASC, issueDateEpochMs ASC, createdAt ASC")
    fun getActiveTokensFifo(): Flow<List<PurchyToken>>

    /**
     * All Cane Tokens (active, expired, and correlated) synced from Cane Department SMS.
     * Excludes archived/soft-deleted records and standalone mill weighments with no token message.
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status != 'ARCHIVED' 
          AND (
             (rawTokenMessage IS NOT NULL AND rawTokenMessage != '')
             OR (allottedWeight IS NOT NULL AND allottedWeight != '' AND purchyNo NOT LIKE 'TAUL-%' AND purchyNo NOT LIKE 'REC-%')
          )
        ORDER BY validUntilEpochMs ASC, issueDateEpochMs DESC, createdAt DESC
    """)
    fun getAllCaneTokens(): Flow<List<PurchyToken>>

    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status != 'ARCHIVED'
          AND (
             (rawTokenMessage IS NOT NULL AND rawTokenMessage != '')
             OR (allottedWeight IS NOT NULL AND allottedWeight != '' AND purchyNo NOT LIKE 'TAUL-%' AND purchyNo NOT LIKE 'REC-%')
          )
          AND (growerName LIKE '%' || :query || '%' 
               OR purchyNo LIKE '%' || :query || '%' 
               OR society LIKE '%' || :query || '%'
               OR growerCode LIKE '%' || :query || '%')
        ORDER BY validUntilEpochMs ASC, issueDateEpochMs DESC, createdAt DESC
    """)
    fun searchAllCaneTokens(query: String): Flow<List<PurchyToken>>

    /**
     * Dispatched receipts list sorted by dispatch weighment date latest on the top.
     * Strictly filters for dispatched/weighed receipts only. Excludes archived.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status = 'DISPATCHED' ORDER BY COALESCE(dispatchDateEpochMs, issueDateEpochMs, updatedAt) DESC, updatedAt DESC")
    fun getDispatchedReceipts(): Flow<List<PurchyToken>>

    /**
     * Search Active/Pending Tokens by grower name, purchy number, or society.
     * Sorted by message date latest on the top. Excludes archived.
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status != 'DISPATCHED' AND status != 'ARCHIVED'
          AND (growerName LIKE '%' || :query || '%' 
               OR purchyNo LIKE '%' || :query || '%' 
               OR society LIKE '%' || :query || '%'
               OR growerCode LIKE '%' || :query || '%')
        ORDER BY issueDateEpochMs DESC, validUntilEpochMs DESC, createdAt DESC
    """)
    fun searchActiveTokens(query: String): Flow<List<PurchyToken>>

    /**
     * Search Dispatched Receipts.
     * Sorted by dispatch weighment date latest on the top.
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status = 'DISPATCHED' 
          AND (growerName LIKE '%' || :query || '%' 
               OR purchyNo LIKE '%' || :query || '%' 
               OR society LIKE '%' || :query || '%'
               OR growerCode LIKE '%' || :query || '%'
               OR weightReceiptNo LIKE '%' || :query || '%')
        ORDER BY COALESCE(dispatchDateEpochMs, issueDateEpochMs, updatedAt) DESC, updatedAt DESC
    """)
    fun searchDispatchedReceipts(query: String): Flow<List<PurchyToken>>

    /**
     * Retrieves all tokens for Excel/CSV export.
     */
    @Query("SELECT * FROM purchy_tokens ORDER BY status ASC, validUntilEpochMs ASC")
    suspend fun getAllTokensForExport(): List<PurchyToken>

    @Query("SELECT * FROM purchy_tokens WHERE purchyNo = :purchyNo LIMIT 1")
    suspend fun getByPurchyNo(purchyNo: String): PurchyToken?

    @Query("SELECT * FROM purchy_tokens WHERE weightReceiptNo = :receiptNo LIMIT 1")
    suspend fun getByReceiptNo(receiptNo: String): PurchyToken?

    /**
     * Finds an existing token or receipt matching either purchyNo or weightReceiptNo.
     * Enforces duplicate entry avoidance across receipts and tokens.
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE purchyNo = :purchyNo 
           OR weightReceiptNo = :purchyNo
           OR (:receiptNo != '' AND (weightReceiptNo = :receiptNo OR purchyNo = :receiptNo))
        LIMIT 1
    """)
    suspend fun findDuplicateByNumbers(purchyNo: String, receiptNo: String = ""): PurchyToken?

    /**
     * Advanced duplicate check: finds matching record by grower code, date, and weight.
     * Prevents duplicate receipts when purchy/receipt numbers vary slightly in formatting.
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE growerCode = :growerCode 
          AND (dispatchDateStr = :dateStr OR issueDateStr = :dateStr)
          AND (
              (:weight > 0.0 AND ABS(COALESCE(actualNettedWeightNumber, allottedWeightNumber, 0.0) - :weight) < 0.05)
              OR (:receiptNo != '' AND (weightReceiptNo = :receiptNo OR purchyNo = :receiptNo))
          )
        LIMIT 1
    """)
    suspend fun findDuplicateByGrowerDateWeight(
        growerCode: String,
        dateStr: String,
        weight: Double,
        receiptNo: String = ""
    ): PurchyToken?

    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status != 'DISPATCHED' AND status != 'ARCHIVED'
          AND (ugc = :ugc OR growerCode = :code) 
        ORDER BY validUntilEpochMs ASC 
        LIMIT 1
    """)
    suspend fun findActiveByUgcOrCode(ugc: String, code: String): PurchyToken?

    /**
     * Correlates an incoming Weighment/Dispatch message against Active Tokens
     * using the shared Grower Code and aligning dates within the validity window (+ 24h grace).
     */
    @Query("""
        SELECT * FROM purchy_tokens 
        WHERE status != 'DISPATCHED' AND status != 'ARCHIVED'
          AND growerCode = :growerCode 
          AND (
             :dateEpochMs = 0 
             OR (issueDateEpochMs <= :dateEpochMs + 86400000 AND validUntilEpochMs >= :dateEpochMs - 86400000)
          )
        ORDER BY validUntilEpochMs ASC 
        LIMIT 1
    """)
    suspend fun findActiveTokenForWeighment(growerCode: String, dateEpochMs: Long = 0L): PurchyToken?

    /**
     * Retrieves all unmatched tokens (both active valid and expired) eligible for correlation.
     * Excludes already DISPATCHED and ARCHIVED records.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status != 'DISPATCHED' AND status != 'ARCHIVED'")
    suspend fun getAllUnmatchedTokens(): List<PurchyToken>

    /**
     * Retrieves standalone dispatched weighment records that do not have associated cane token details
     * (e.g. no raw token message, or purchyNo matches mill receipt number).
     */
    @Query("SELECT * FROM purchy_tokens WHERE status = 'DISPATCHED' AND (rawTokenMessage IS NULL OR rawTokenMessage = '' OR purchyNo = weightReceiptNo OR purchyNo LIKE 'TAUL-%' OR purchyNo LIKE 'REC-%')")
    suspend fun getStandaloneDispatchedReceipts(): List<PurchyToken>

    /**
     * Segregates unmatched tokens whose 2-day validity period has expired
     * for the Pending Weighment Audit tab.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status != 'DISPATCHED' AND status != 'ARCHIVED' AND validUntilEpochMs < :nowEpochMs ORDER BY validUntilEpochMs DESC")
    fun getPendingAuditTokens(nowEpochMs: Long): Flow<List<PurchyToken>>

    @Query("SELECT COUNT(*) FROM purchy_tokens WHERE status != 'DISPATCHED' AND status != 'ARCHIVED' AND validUntilEpochMs < :nowEpochMs")
    fun getPendingAuditCount(nowEpochMs: Long): Flow<Int>

    /**
     * Insert with IGNORE conflict strategy to enforce deduplication using Purchy No as Primary Key.
     * Returns -1 if the token already exists.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertToken(token: PurchyToken): Long

    @Update
    suspend fun updateToken(token: PurchyToken)

    /**
     * Correlates and transitions an Active Token to Dispatched Receipts with actual netted weighment details.
     */
    @Query("""
        UPDATE purchy_tokens 
        SET status = 'DISPATCHED',
            actualNettedWeight = :actualWeight,
            actualNettedWeightNumber = :actualWeightNum,
            dispatchDateStr = :dispatchDateStr,
            dispatchDateEpochMs = :dispatchEpochMs,
            weightReceiptNo = :receiptNo,
            grossWeight = :grossWeight,
            tareWeight = :tareWeight,
            rawReceiptMessage = :rawReceipt,
            updatedAt = :updatedAt
        WHERE purchyNo = :purchyNo
    """)
    suspend fun linkWeighmentReceipt(
        purchyNo: String,
        actualWeight: String,
        actualWeightNum: Double,
        dispatchDateStr: String,
        dispatchEpochMs: Long,
        receiptNo: String?,
        grossWeight: String?,
        tareWeight: String?,
        rawReceipt: String?,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("SELECT COUNT(*) FROM purchy_tokens WHERE status != 'DISPATCHED' AND status != 'ARCHIVED'")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM purchy_tokens WHERE status = 'DISPATCHED'")
    fun getDispatchedCount(): Flow<Int>

    /**
     * Soft-deletes a record by moving it to ARCHIVED to safeguard against accidental financial loss.
     */
    @Query("UPDATE purchy_tokens SET status = 'ARCHIVED', updatedAt = :updatedAt WHERE purchyNo = :purchyNo")
    suspend fun softDeleteByPurchyNo(purchyNo: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Restores an archived record back to ACTIVE.
     */
    @Query("UPDATE purchy_tokens SET status = 'ACTIVE', updatedAt = :updatedAt WHERE purchyNo = :purchyNo")
    suspend fun restoreByPurchyNo(purchyNo: String, updatedAt: Long = System.currentTimeMillis()): Int

    /**
     * Retrieves all archived/soft-deleted tokens for audit recovery.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status = 'ARCHIVED' ORDER BY updatedAt DESC")
    fun getArchivedTokens(): Flow<List<PurchyToken>>

    /**
     * Retrieves all Mill-weighed Dispatched tokens specifically for export.
     * Contains Date, Grower Name, Receipt No, Actual Netted Weight, etc.
     */
    @Query("SELECT * FROM purchy_tokens WHERE status = 'DISPATCHED' ORDER BY dispatchDateEpochMs DESC, updatedAt DESC")
    suspend fun getDispatchedTokensForExport(): List<PurchyToken>

    /**
     * Compiles total actual netted weight from mill-weighed dispatched data.
     */
    @Query("SELECT SUM(actualNettedWeightNumber) FROM purchy_tokens WHERE status = 'DISPATCHED'")
    fun getTotalDispatchedWeight(): Flow<Double?>

    /**
     * Permanently deletes a specific token or dispatched receipt by Purchy No.
     */
    @Query("DELETE FROM purchy_tokens WHERE purchyNo = :purchyNo")
    suspend fun deleteByPurchyNo(purchyNo: String): Int

    /**
     * Purges corrupted, placeholder, or empty dummy records.
     */
    @Query("DELETE FROM purchy_tokens WHERE growerName = 'GROWER' OR growerName = '' OR purchyNo = ''")
    suspend fun deleteCorruptedOrPlaceholderRecords(): Int

    /**
     * Purges messages whose dates fall outside the active cane season window (Nov - Apr).
     */
    @Query("""
        DELETE FROM purchy_tokens 
        WHERE (issueDateEpochMs < :seasonStart OR issueDateEpochMs > :seasonEnd)
          AND (dispatchDateEpochMs IS NULL OR dispatchDateEpochMs < :seasonStart OR dispatchDateEpochMs > :seasonEnd)
    """)
    suspend fun deleteOutsideSeason(seasonStart: Long, seasonEnd: Long): Int

    /**
     * Cleans up any legacy '#MILL-' or 'MILL-' prefixes from receipt numbers in database.
     */
    @Query("""
        UPDATE purchy_tokens 
        SET weightReceiptNo = REPLACE(REPLACE(REPLACE(weightReceiptNo, '#MILL-', ''), 'MILL-', ''), '#', '')
        WHERE weightReceiptNo LIKE '%MILL%' OR weightReceiptNo LIKE '#%'
    """)
    suspend fun sanitizeReceiptNumbers(): Int
}
