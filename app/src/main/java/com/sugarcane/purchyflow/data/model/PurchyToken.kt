package com.sugarcane.purchyflow.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Unified Room Entity representing a Sugarcane Purchy (Token)
 * and its subsequently linked Weighment/Dispatch Receipt.
 */
@Entity(
    tableName = "purchy_tokens",
    indices = [
        Index(value = ["purchyNo"], unique = true),
        Index(value = ["status", "validUntilEpochMs"]),
        Index(value = ["growerName"]),
        Index(value = ["growerCode"]),
        Index(value = ["society"])
    ]
)
data class PurchyToken(
    @PrimaryKey
    val purchyNo: String,                      // e.g., "700100200301" (Unique Identifier / PK)
    val growerName: String,                    // e.g., "RAMESH KUMAR"
    val growerCode: String,                    // e.g., "5501/12"
    val ugc: String,                           // e.g., "99887766550011"
    val caneType: String,                      // e.g., "E-RATOON"
    val fc: String,                            // e.g., "1/2" (Fortnight/Column)
    val allottedWeight: String,                // e.g., "45ql"
    val allottedWeightNumber: Double = 0.0,    // Parsed numerical weight (in quintals)
    val issueDateStr: String,                  // e.g., "12/11/2026"
    val issueDateEpochMs: Long,                // Parsed timestamp
    val validUntilStr: String,                 // e.g., "14/11/2026" (Auto-calculated: Issue Date + 2 days)
    val validUntilEpochMs: Long,               // Auto-calculated expiration timestamp
    val society: String,                       // e.g., "GreenValley"
    val centre: String,                        // e.g., "100"
    val status: TokenStatus = TokenStatus.ACTIVE,

    // Correlated Weighment / Dispatch Receipt Fields
    val actualNettedWeight: String? = null,    // e.g., "44.80ql" or "4480kg"
    val actualNettedWeightNumber: Double? = null,
    val dispatchDateStr: String? = null,       // Date weighment was recorded
    val dispatchDateEpochMs: Long? = null,
    val weightReceiptNo: String? = null,       // Weighbridge ticket / receipt number
    val grossWeight: String? = null,           // Gross weighment if provided
    val tareWeight: String? = null,            // Tare/Empty trolley weighment
    val rawTokenMessage: String? = null,       // Original SMS / text message snippet
    val rawReceiptMessage: String? = null,     // Original SMS receipt message snippet
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
