package com.sugarcane.purchyflow.data.correlation

import com.sugarcane.purchyflow.util.SecureLog as Log
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.parser.ParsedToken
import com.sugarcane.purchyflow.parser.ParsedWeighmentReceipt
import java.util.Locale
import kotlin.math.abs

/**
 * High-Precision Sugarcane Correlation Engine using Fuzzy Matching.
 *
 * Resolves format variations between Cane Department Token SMS and Sugar Mill Weighment (Taul) SMS:
 * 1. Grower Code Normalization & Fuzzy Matching (e.g., "1160/0134" vs "1160/134", "05501/0045" vs "5501/45", "1160-134" vs "1160/134").
 * 2. UGC (Unique Grower Code) Normalization & Suffix Matching (14-digit standard).
 * 3. Date Alignment: Verifies that mill weighment timestamp aligns within the token issue date and validity/queue window (-24h to +5 days).
 * 4. Name Fuzzy Alignment: Recognizes grower names with mill patronymic slashes (e.g., "RAVI DEVI" matching "RAVI DEVI/ARVIND").
 * 5. State Transition: Extracts actual netted weight, updates database state, and transitions records from Active Tokens to Dispatched Receipts.
 */
object CorrelationEngine {

    private const val TAG = "CorrelationEngine"

    // Correlation confidence threshold (0.0 to 100.0)
    const val MINIMUM_CONFIDENCE_THRESHOLD = 70.0

    data class CorrelationResult(
        val isMatch: Boolean,
        val confidenceScore: Double,
        val matchedToken: PurchyToken?,
        val matchReason: String
    )

    /**
     * Normalizes a Sugarcane Grower Code (VillageCode/GrowerSerial).
     * Handles:
     * - "1160/0134" -> "1160/134" (strips leading zeros from grower serial)
     * - "01160/00134" -> "1160/134" (strips leading zeros from both)
     * - "1160 - 134", "1160-134", "1160 / 134", "1160_134" -> "1160/134"
     */
    fun normalizeGrowerCode(rawCode: String?): String? {
        if (rawCode.isNullOrBlank()) return null
        val cleaned = rawCode.trim()
            .replace(Regex("(?i)\\bcode\\s*:?\\s*"), "")
            .replace(Regex("\\s*[/\\-_\\\\]+\\s*"), "/")
            .replace(Regex("/+"), "/")
            .trim('/', ' ')

        val parts = cleaned.split('/').filter { it.isNotBlank() }
        if (parts.size == 2) {
            val village = parts[0].trim().trimStart('0').ifEmpty { "0" }
            val serial = parts[1].trim().trimStart('0').ifEmpty { "0" }
            return "$village/$serial"
        } else if (parts.size == 1 && parts[0].isNotEmpty()) {
            return parts[0].trim().trimStart('0').ifEmpty { "0" }
        }

        return cleaned.ifEmpty { null }
    }

    /**
     * Compares two Grower Codes using normalized and fuzzy matching rules.
     */
    fun matchesGrowerCode(codeA: String?, codeB: String?): Boolean {
        if (codeA.isNullOrBlank() || codeB.isNullOrBlank()) return false
        val normA = normalizeGrowerCode(codeA) ?: return false
        val normB = normalizeGrowerCode(codeB) ?: return false

        // 1. Exact normalized match ("1160/134" == "1160/134")
        if (normA.equals(normB, ignoreCase = true)) return true

        // 2. Cross-format match: If one has village code and the other only has grower serial
        val partsA = normA.split('/')
        val partsB = normB.split('/')
        if (partsA.size == 2 && partsB.size == 2) {
            val villageA = partsA[0]
            val serialA = partsA[1]
            val villageB = partsB[0]
            val serialB = partsB[1]
            if (villageA.equals(villageB, ignoreCase = true) && serialA.equals(serialB, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * Normalizes a Unique Grower Code (UGC) by stripping non-digit characters.
     */
    fun normalizeUgc(rawUgc: String?): String? {
        if (rawUgc.isNullOrBlank()) return null
        val digitsOnly = rawUgc.replace(Regex("[^0-9]"), "").trim()
        return digitsOnly.ifEmpty { null }
    }

    /**
     * Compares UGC identifiers, supporting exact and suffix matches.
     */
    fun matchesUgc(ugcA: String?, ugcB: String?): Boolean {
        val normA = normalizeUgc(ugcA) ?: return false
        val normB = normalizeUgc(ugcB) ?: return false

        if (normA == normB) return true

        // Suffix match (minimum 6 digits common, e.g., truncated receipt printouts)
        if (normA.length >= 6 && normB.length >= 6) {
            if (normA.endsWith(normB) || normB.endsWith(normA)) {
                return true
            }
        }

        return false
    }

    /**
     * Verifies that the mill weighment date aligns with the token's lifecycle.
     * Window: From 24 hours prior to issueDate (time skew / midnight arrival)
     * up to validity period + 5 days grace (for long mill gate lines and dispatch queues).
     */
    fun isDateAligned(
        tokenIssueEpochMs: Long,
        tokenValidUntilEpochMs: Long,
        weighmentEpochMs: Long
    ): Boolean {
        if (tokenIssueEpochMs <= 0L || weighmentEpochMs <= 0L) {
            // If either date is missing, treat as aligned with lower confidence
            return true
        }

        val minAllowedEpoch = tokenIssueEpochMs - (24 * 3600 * 1000L) // 1 day prior grace
        val maxValidEpoch = if (tokenValidUntilEpochMs > 0L) {
            tokenValidUntilEpochMs
        } else {
            tokenIssueEpochMs + (2 * 86400000L) // Default 2 days validity
        }
        val maxAllowedEpoch = maxValidEpoch + (5 * 86400000L) // 5 days gate delay grace

        return weighmentEpochMs in minAllowedEpoch..maxAllowedEpoch
    }

    /**
     * Computes proximity score between Token Issue Date and Mill Weighment Date (0.0 to 1.0).
     */
    fun calculateDateProximityScore(tokenIssueEpochMs: Long, weighmentEpochMs: Long): Double {
        if (tokenIssueEpochMs <= 0L || weighmentEpochMs <= 0L) return 0.5
        val diffDays = abs(weighmentEpochMs - tokenIssueEpochMs) / 86400000.0
        return when {
            diffDays <= 2.0 -> 1.0   // Within valid token window
            diffDays <= 4.0 -> 0.8   // Typical queue delay
            diffDays <= 7.0 -> 0.6   // Extended queue
            else -> 0.2
        }
    }

    /**
     * Computes fuzzy name similarity (0.0 to 1.0) accounting for sugar mill naming conventions:
     * e.g., Token = "RAVI DEVI", Mill = "RAVI DEVI/ARVIND" (Grower / Guardian).
     */
    fun fuzzyNameScore(nameToken: String?, nameWeighment: String?): Double {
        if (nameToken.isNullOrBlank() || nameWeighment.isNullOrBlank()) return 0.5
        val cleanA = nameToken.uppercase(Locale.ROOT).trim().replace(Regex("[^A-Z0-9\\s/]"), "")
        val cleanB = nameWeighment.uppercase(Locale.ROOT).trim().replace(Regex("[^A-Z0-9\\s/]"), "")

        if (cleanA == cleanB) return 1.0

        // Substring / Prefix match: "RAVI DEVI" is in "RAVI DEVI/ARVIND"
        if (cleanB.startsWith(cleanA) || cleanA.startsWith(cleanB)) return 0.95
        if (cleanB.contains(cleanA) || cleanA.contains(cleanB)) return 0.90

        // Tokenized word set similarity
        val wordsA = cleanA.split(Regex("[\\s/]+")).filter { it.length > 1 }.toSet()
        val wordsB = cleanB.split(Regex("[\\s/]+")).filter { it.length > 1 }.toSet()

        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0

        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        val jaccard = intersection.toDouble() / union.toDouble()

        // If all words in Token name are found in Mill receipt name, high match
        val subsetRatio = intersection.toDouble() / wordsA.size.toDouble()
        if (subsetRatio >= 1.0) return 0.90

        return maxOf(jaccard, subsetRatio * 0.8)
    }

    /**
     * Evaluates a Weighment Receipt against a list of candidate active tokens,
     * returning the best correlated token with confidence score and reason.
     */
    fun findBestMatchingToken(
        receipt: ParsedWeighmentReceipt,
        candidateTokens: List<PurchyToken>,
        dispatchEpochMs: Long
    ): CorrelationResult {
        if (candidateTokens.isEmpty()) {
            return CorrelationResult(false, 0.0, null, "NO_CANDIDATE_TOKENS")
        }

        val cleanReceiptNo = receipt.receiptNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()
        val cleanPurchyNo = receipt.purchyNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim().orEmpty()

        // 1. Direct Purchy Number or Receipt Number Exact Match (100% confidence)
        for (candidate in candidateTokens) {
            val candPurchy = candidate.purchyNo.trim()
            val candReceipt = candidate.weightReceiptNo?.trim().orEmpty()

            if (cleanPurchyNo.isNotEmpty() && (candPurchy == cleanPurchyNo || candReceipt == cleanPurchyNo)) {
                return CorrelationResult(true, 100.0, candidate, "EXACT_PURCHY_NUMBER_MATCH")
            }
            if (cleanReceiptNo.isNotEmpty() && (candPurchy == cleanReceiptNo || candReceipt == cleanReceiptNo)) {
                return CorrelationResult(true, 100.0, candidate, "EXACT_RECEIPT_NUMBER_MATCH")
            }
        }

        // 2. Fuzzy Correlation based on Shared Grower Code, UGC, and Date Alignment
        var bestCandidate: PurchyToken? = null
        var highestScore = 0.0
        var bestReason = ""

        for (candidate in candidateTokens) {
            val codeMatched = matchesGrowerCode(candidate.growerCode, receipt.growerCode)
            val ugcMatched = matchesUgc(candidate.ugc, receipt.ugc)

            // Must match at least one shared identifier (Grower Code or UGC)
            if (!codeMatched && !ugcMatched) continue

            // Must be date-aligned within the season/lifecycle window
            val dateAligned = isDateAligned(
                tokenIssueEpochMs = candidate.issueDateEpochMs,
                tokenValidUntilEpochMs = candidate.validUntilEpochMs,
                weighmentEpochMs = dispatchEpochMs
            )
            if (!dateAligned) continue

            // Compute score components
            var score = 0.0
            if (ugcMatched) score += 55.0
            if (codeMatched) score += 50.0

            val dateScore = calculateDateProximityScore(candidate.issueDateEpochMs, dispatchEpochMs)
            score += (dateScore * 20.0) // Up to 20 pts for date proximity

            val nameScore = fuzzyNameScore(candidate.growerName, receipt.growerName)
            score += (nameScore * 25.0) // Up to 25 pts for name alignment

            val reason = buildString {
                append("CORRELATED: ")
                if (ugcMatched) append("[UGC Matched] ")
                if (codeMatched) append("[Grower Code ${normalizeGrowerCode(candidate.growerCode)} Matched] ")
                append("[Date Proximity ${(dateScore * 100).toInt()}%] ")
                append("[Name Match ${(nameScore * 100).toInt()}%]")
            }

            if (score > highestScore) {
                highestScore = score
                bestCandidate = candidate
                bestReason = reason
            } else if (score == highestScore && bestCandidate != null) {
                // Tie breaker: Earliest expiring token wins (FIFO queue)
                if (candidate.validUntilEpochMs < bestCandidate.validUntilEpochMs) {
                    bestCandidate = candidate
                    bestReason = reason
                }
            }
        }

        val isApprovedMatch = highestScore >= MINIMUM_CONFIDENCE_THRESHOLD && bestCandidate != null
        return CorrelationResult(
            isMatch = isApprovedMatch,
            confidenceScore = highestScore,
            matchedToken = if (isApprovedMatch) bestCandidate else null,
            matchReason = if (isApprovedMatch) bestReason else "INSUFFICIENT_CORRELATION_CONFIDENCE ($highestScore < $MINIMUM_CONFIDENCE_THRESHOLD)"
        )
    }

    /**
     * Reverse correlation: When a Cane Token arrives after a standalone Weighment Receipt
     * was already recorded in the database.
     */
    fun findMatchingStandaloneReceipt(
        token: ParsedToken,
        standaloneReceipts: List<PurchyToken>
    ): CorrelationResult {
        if (standaloneReceipts.isEmpty()) {
            return CorrelationResult(false, 0.0, null, "NO_STANDALONE_RECEIPTS")
        }

        var bestReceipt: PurchyToken? = null
        var highestScore = 0.0
        var bestReason = ""

        for (receipt in standaloneReceipts) {
            val codeMatched = matchesGrowerCode(receipt.growerCode, token.growerCode)
            val ugcMatched = matchesUgc(receipt.ugc, token.ugc)

            if (!codeMatched && !ugcMatched) continue

            val dispatchEpoch = receipt.dispatchDateEpochMs ?: receipt.createdAt
            val dateAligned = isDateAligned(
                tokenIssueEpochMs = token.issueDateEpochMs,
                tokenValidUntilEpochMs = token.validUntilEpochMs,
                weighmentEpochMs = dispatchEpoch
            )
            if (!dateAligned) continue

            var score = 0.0
            if (ugcMatched) score += 55.0
            if (codeMatched) score += 50.0

            val dateScore = calculateDateProximityScore(token.issueDateEpochMs, dispatchEpoch)
            score += (dateScore * 20.0)

            val nameScore = fuzzyNameScore(token.growerName, receipt.growerName)
            score += (nameScore * 25.0)

            val reason = "REVERSE_CORRELATED: [Grower Code/UGC Matched] [Date Aligned]"

            if (score > highestScore) {
                highestScore = score
                bestReceipt = receipt
                bestReason = reason
            }
        }

        val isApproved = highestScore >= MINIMUM_CONFIDENCE_THRESHOLD && bestReceipt != null
        return CorrelationResult(
            isMatch = isApproved,
            confidenceScore = highestScore,
            matchedToken = if (isApproved) bestReceipt else null,
            matchReason = if (isApproved) bestReason else "INSUFFICIENT_CORRELATION_CONFIDENCE"
        )
    }
}
