package com.sugarcane.purchyflow.security

import com.sugarcane.purchyflow.parser.TokenMessageParser
import java.util.Locale

/**
 * Strict SMS & Notification Whitelist Filter (Privacy & Zero-Retention Shield).
 *
 * Ensures only legitimate Sugarcane Cane Department and Sugar Mill notifications/SMS
 * are processed. Any non-cane SMS (e.g., Bank OTPs, Personal Texts, Promo messages)
 * is immediately rejected and discarded in memory without logging or persistence.
 */
object CaneMessageFilter {

    // Whitelist of verified Sugarcane Department and Sugar Mill sender headers
    private val WHITELISTED_SENDER_PATTERNS = listOf(
        "CANEDEPTT",
        "UPCANE",
        "TRIVENI",
        "DHAMPUR",
        "BAJAJ",
        "SIMBHAOLI",
        "UTTAM",
        "SHAMLI",
        "MANSURPUR",
        "KHATOLI",
        "CANE",
        "SUGAR",
        "MILL",
        "KISAAN",
        "KISHAN",
        "GANNASOCIETY",
        "GANNA"
    )

    // Strong domain signals indicating a genuine Cane Token or Mill Weighment Receipt
    private val CANE_KEYWORDS = listOf(
        "PURCHY",
        "UGC:",
        "UGC",
        "TAUL",
        "DINAAK",
        "MILL PURCHY",
        "WEIGHMENT",
        "CANEDEPTT",
        "E-RATOON",
        "P-RATOON",
        "KHALI",
        "BHARI"
    )

    // Non-cane payment or e-commerce URL patterns that might contain token= query params
    private val EXCLUDED_URL_TOKENS = listOf(
        "EZETAP",
        "RAZORPAY",
        "PAYTM",
        "AMAZON",
        "FLIPKART",
        "ORDER",
        "TRACKING"
    )

    /**
     * Checks if the sender address or notification title matches approved Cane/Mill entities.
     */
    fun isWhitelistedSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        val upper = sender.uppercase(Locale.ROOT)
        return WHITELISTED_SENDER_PATTERNS.any { upper.contains(it) }
    }

    /**
     * Checks if the message body contains clear sugarcane mill domain signatures.
     */
    fun isSugarcaneContent(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val upper = text.uppercase(Locale.ROOT)

        // Check if multiple cane keywords or parser patterns match
        var matchCount = 0
        for (kw in CANE_KEYWORDS) {
            if (upper.contains(kw)) {
                matchCount++
            }
        }

        // If it matches 2 or more cane keywords, or passes parser validation
        if (matchCount >= 2) return true

        return TokenMessageParser.isCaneMessage(text)
    }

    /**
     * Primary Gatekeeper: Decides whether an incoming SMS or Notification should be processed.
     *
     * If this returns false, the caller MUST immediately discard the message from memory.
     * Do NOT write to Room database. Do NOT write the message body to Logcat.
     */
    fun shouldProcess(sender: String?, messageBody: String?): Boolean {
        if (messageBody.isNullOrBlank()) return false
        val upper = messageBody.uppercase(Locale.ROOT)

        // Immediately reject e-commerce / payment links with token= query params
        if (EXCLUDED_URL_TOKENS.any { upper.contains(it) } &&
            !upper.contains("CANEDEPTT") && !upper.contains("UPCANE")
        ) {
            return false
        }

        // Check sender whitelist first
        val isSenderApproved = isWhitelistedSender(sender)

        // Check content signatures
        val isContentApproved = isSugarcaneContent(messageBody)

        // Accept if sender is whitelisted AND content has cane signals, OR content has strong cane structure
        if (isSenderApproved && (isContentApproved || TokenMessageParser.isCaneMessage(messageBody))) {
            return true
        }

        // If sender is unknown/generic phone number, REQUIRE strong content verification
        return isContentApproved
    }

    /**
     * Sanitizes a Purchy number or Grower code for safe debugging without exposing full IDs.
     */
    fun maskId(id: String?): String {
        if (id.isNullOrBlank()) return "N/A"
        return if (id.length > 4) {
            "***" + id.takeLast(4)
        } else {
            "***"
        }
    }
}
