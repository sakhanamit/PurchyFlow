package com.sugarcane.purchyflow.parser

import com.sugarcane.purchyflow.util.DateUtil
import com.sugarcane.purchyflow.util.SecureLog as Log
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-precision Regular Expression & NLP Parsing Engine for Sugarcane cellular SMS and text messages:
 * 1. PURCHY_TOKEN: Issued by Cane Department (CANEDEPTT) before weighment (Token for dispatch).
 * 2. TAUL_RECEIPT: Issued by Sugar Mill after sugarcane has been weighed in the mill (Dispatched weight receipt).
 */
enum class CaneMessageType {
    PURCHY_TOKEN,   // Token SMS (ready for dispatch)
    TAUL_RECEIPT,   // Taul SMS (mill weighment receipt after weight)
    UNKNOWN         // Unrelated text message
}

object TokenMessageParser {

    private const val TAG = "TokenMessageParser"
    private const val MAX_INPUT_LENGTH = 1000

    // Pre-compiled regex patterns to avoid per-call compilation overhead and ReDoS
    private val TOKEN_NAME_PATTERN = Pattern.compile("^(.*?)(?:\\s+Code\\s*:)", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val TOKEN_CODE_PATTERN = Pattern.compile("Code\\s*:\\s*([0-9a-zA-Z/_-]+)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_UGC_PATTERN = Pattern.compile("UGC\\s*:\\s*([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_TYPE_PATTERN = Pattern.compile("Type\\s*:\\s*([0-9a-zA-Z_-]+)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_PURCHY_PATTERN = Pattern.compile("Purchy\\s*:\\s*([0-9]+)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_FC_PATTERN = Pattern.compile("FC\\s*:\\s*([0-9/]+)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_WT_PATTERN = Pattern.compile("Wt\\s*:\\s*([0-9.]+\\s*[a-zA-Z]*)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_DATE_PATTERN = Pattern.compile("(?:W\\s*Date|Date|DINAAK)\\s*:?\\s*([0-9]{1,2}[/\\-.\\s][a-zA-Z0-9]{1,4}[/\\-.\\s][0-9]{2,4})", Pattern.CASE_INSENSITIVE)
    private val TOKEN_SOC_PATTERN = Pattern.compile("Soc\\s*:\\s*([a-zA-Z0-9_ -]+?)(?=\\s+Cnt:|\\s+CANEDEPTT|$)", Pattern.CASE_INSENSITIVE)
    private val TOKEN_CNT_PATTERN = Pattern.compile("Cnt\\s*:\\s*([0-9a-zA-Z_-]+)", Pattern.CASE_INSENSITIVE)

    private val NLP_CODE_PATTERN = Pattern.compile("\\b([0-9]{2,6}/[0-9]{1,5})\\b")
    private val NLP_LONG_NUM_PATTERN = Pattern.compile("\\b([0-9]{10,16})\\b")
    private val NLP_UGC_LABEL_PATTERN = Pattern.compile("UGC\\s*:?\\s*([0-9a-zA-Z]+)", Pattern.CASE_INSENSITIVE)
    private val NLP_PURCHY_LABEL_PATTERN = Pattern.compile("(?:Purchy|Token|Pur|P\\.?No|P#)\\s*:?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE)
    private val NLP_WT_PATTERN = Pattern.compile("(?:\\b(?:Wt|Weight|Taul|Net|Alt)\\s*:?\\s*([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\b|\\b([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*(?:ql|qtl|quintal|kg|ton)\\b)", Pattern.CASE_INSENSITIVE)
    private val NLP_DATE_PATTERN = Pattern.compile("\\b([0-9]{1,2}[/\\-.][0-9]{1,2}[/\\-.][0-9]{2,4}|[0-9]{1,2}[/\\-.][a-zA-Z]{3}[/\\-.][0-9]{2,4})\\b")
    private val NLP_CANE_TYPE_PATTERN = Pattern.compile("\\b(E-RATOON|P-RATOON|PLANT|RATOON|AUTUMN|0238|CO-0238|EARLY|GENERAL)\\b", Pattern.CASE_INSENSITIVE)
    private val NLP_FC_PATTERN = Pattern.compile("\\b([0-9]/[0-9]{1,2})\\b")
    private val NLP_SOC_PATTERN = Pattern.compile("\\b(DEOBAND|TRIVENI|DHAMPUR|BAJAJ|SIMBHAOLI|UTTAM|SHAMLI|MANSURPUR|KHATOLI)\\b", Pattern.CASE_INSENSITIVE)

    private val REC_PURCHY_PATTERN = Pattern.compile("(?:MILL\\s+PURCHY\\s+NO|PURCHY(?:\\s*NO)?)\\s*:?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE)
    private val REC_CODE_PATTERN = Pattern.compile("(?:Code\\s*:\\s*|^\\s*)([0-9]{2,6}/[0-9]{1,5})", Pattern.CASE_INSENSITIVE)
    private val REC_NAME_PATTERN = Pattern.compile("^[0-9]+/[0-9]+\\s+(.+?)(?=\\s+TAUL|\\s+DINAAK|\\s+MILL|\\s+WT|$)", Pattern.CASE_INSENSITIVE)
    private val REC_NET_PATTERN = Pattern.compile("(?:NET(?:\\s*WT)?|WT)\\s*:?\\s*([0-9.]+\\s*(?:QTL|ql|kg|ton)?)", Pattern.CASE_INSENSITIVE)
    private val REC_GROSS_PATTERN = Pattern.compile("Gross\\s*:\\s*([0-9.]+\\s*[a-zA-Z]*)", Pattern.CASE_INSENSITIVE)
    private val REC_TARE_PATTERN = Pattern.compile("Tare\\s*:\\s*([0-9.]+\\s*[a-zA-Z]*)", Pattern.CASE_INSENSITIVE)
    private val REC_DATE_PATTERN = Pattern.compile("(?:DINAAK|DATE|W\\s*DATE)\\s*:?\\s*([0-9]{1,2}[/\\-.\\s][a-zA-Z0-9]{1,4}[/\\-.\\s][0-9]{2,4})", Pattern.CASE_INSENSITIVE)
    private val REC_MILL_PATTERN = Pattern.compile("\\b(TRIVENI|DEOBAND|DHAMPUR|BAJAJ|SIMBHAOLI|UTTAM|[A-Z]{4,})\\s*$", Pattern.CASE_INSENSITIVE)
    private val REC_RECEIPT_PATTERN = Pattern.compile("(?:Receipt|Ticket|Rcpt)\\s*:\\s*([0-9a-zA-Z_-]+)", Pattern.CASE_INSENSITIVE)
    private val NLP_REC_WT_PATTERN = Pattern.compile("(?:\\b(?:NET(?:\\s*WT)?|WT|TAUL)\\s*:?\\s*([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\b|\\b([0-9]{1,3}(?:\\.[0-9]{1,2})?)\\s*(?:ql|qtl|quintal|kg|ton)\\b)", Pattern.CASE_INSENSITIVE)
    private val NLP_REC_PURCHY_PATTERN = Pattern.compile("(?:MILL\\s+PURCHY\\s+NO|PURCHY(?:\\s*NO)?|TICKET|RECEIPT)\\s*:?\\s*([0-9]+)", Pattern.CASE_INSENSITIVE)
    private val NLP_REC_LONG_NUM_PATTERN = Pattern.compile("\\b([0-9]{6,14})\\b")
    private val NLP_REC_MILL_PATTERN = Pattern.compile("\\b(TRIVENI|DEOBAND|DHAMPUR|BAJAJ|SIMBHAOLI|UTTAM)\\b", Pattern.CASE_INSENSITIVE)

    private fun capInput(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length > MAX_INPUT_LENGTH) trimmed.substring(0, MAX_INPUT_LENGTH) else trimmed
    }

    /**
     * High-precision classifier that distinguishes between:
     * 1. PURCHY_TOKEN: Issued by Cane Department (CANEDEPTT) before weighment.
     *    Example: SUNITA DEVI Code:5501/45 UGC:99887766550022 Type:E-RATOON Purchy:700100200302 FC:1/8 Wt:45ql W Date:18/11/2026 Soc:GreenValley Cnt:100 CANEDEPTT
     * 2. TAUL_RECEIPT: Issued by Sugar Mill after weighment.
     *    Example: 5501/45 SUNITA DEVI/RAMESH TAUL DINAAK 19-NOV-26 MILL PURCHY NO 8800102 WT 54.9 QTL TRIVENI
     */
    fun identifyMessageType(rawMessage: String?): CaneMessageType {
        if (rawMessage.isNullOrBlank()) return CaneMessageType.UNKNOWN
        val text = capInput(rawMessage)
        val upper = text.uppercase()

        // 1. Definite Token markers from Cane Department
        val hasTokenSignals = upper.contains("CANEDEPTT") ||
                upper.contains("UGC:") ||
                upper.contains("UGC") ||
                upper.contains("FC:") ||
                upper.contains("W DATE") ||
                upper.contains("W.DATE") ||
                upper.contains("TYPE:E-") ||
                upper.contains("TYPE:P-") ||
                (upper.contains("PURCHY") && upper.contains("CODE"))

        // 2. Definite Weighment / Taul markers from Sugar Mill
        val hasTaulSignals = upper.contains("TAUL") ||
                upper.contains("MILL PURCHY") ||
                upper.contains("DINAAK") ||
                upper.contains("WEIGHMENT") ||
                upper.contains("GROSS") ||
                upper.contains("TARE") ||
                upper.contains("KHALI") ||
                upper.contains("BHARI") ||
                (upper.contains("WT") && upper.contains("QTL") && !upper.contains("CANEDEPTT"))

        val isTaulStructure = Regex("^[0-9]{2,6}/[0-9]{1,5}\\s+.+?\\s+(?:TAUL|DINAAK|MILL)", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val isTokenStructure = Regex("^.+?\\s+Code\\s*:\\s*[0-9]+/[0-9]+", RegexOption.IGNORE_CASE).containsMatchIn(text)

        if (hasTokenSignals && !hasTaulSignals) {
            return CaneMessageType.PURCHY_TOKEN
        }

        if (hasTaulSignals && !hasTokenSignals) {
            return CaneMessageType.TAUL_RECEIPT
        }

        if (isTaulStructure || upper.contains("TAUL") || upper.contains("MILL PURCHY") || upper.contains("WEIGHMENT") || (upper.contains("GROSS") && upper.contains("TARE"))) {
            return CaneMessageType.TAUL_RECEIPT
        }

        if (isTokenStructure || upper.contains("CANEDEPTT") || upper.contains("UGC:") || upper.contains("FC:")) {
            return CaneMessageType.PURCHY_TOKEN
        }

        // Check for non-cane URL tokens (e.g., pay?token=..., auth_token) or e-commerce tracking
        if ((upper.contains("HTTP://") || upper.contains("HTTPS://")) &&
            !upper.contains("CANEDEPTT") && !upper.contains("UPCANE") && !upper.contains("GANNASOCIETY")
        ) {
            return CaneMessageType.UNKNOWN
        }

        if (upper.contains("PURCHY") || 
            upper.contains("PARCHI") || 
            upper.contains("पर्ची") || 
            upper.contains("CANE TOKEN") || 
            Regex("\\bTOKEN\\s*[:#0-9]", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            return CaneMessageType.PURCHY_TOKEN
        }

        return CaneMessageType.UNKNOWN
    }

    /**
     * Checks if notification/SMS text contains sugarcane keywords.
     */
    fun isCaneMessage(text: String?): Boolean {
        return identifyMessageType(text) != CaneMessageType.UNKNOWN
    }

    /**
     * Checks if text is a mill weighment / Taul receipt message.
     */
    fun isWeighmentReceipt(text: String?): Boolean {
        return identifyMessageType(text) == CaneMessageType.TAUL_RECEIPT
    }

    /**
     * Parses Sugarcane Token (Purchy) Notification Message.
     * Uses structured regex first, then falls back to NLP Entity Extraction for scrubbed/unstructured text.
     */
    fun parseTokenMessage(rawMessage: String): ParsedToken? {
        try {
            val text = capInput(rawMessage)

            // 1. Grower Name: Everything before 'Code:'
            val nameMatcher = TOKEN_NAME_PATTERN.matcher(text)
            var growerName = if (nameMatcher.find()) {
                nameMatcher.group(1)?.replace("\n", " ")?.trim().orEmpty()
            } else ""

            if (growerName.contains(":")) {
                growerName = growerName.substringAfterLast(":").trim()
            }
            growerName = growerName.trimEnd('/', ' ', '-')

            // 2. Grower Code: Value after Code:
            val codeMatcher = TOKEN_CODE_PATTERN.matcher(text)
            val growerCode = if (codeMatcher.find()) codeMatcher.group(1)?.trim().orEmpty() else ""

            // 3. UGC: Value after UGC:
            val ugcMatcher = TOKEN_UGC_PATTERN.matcher(text)
            val ugc = if (ugcMatcher.find()) ugcMatcher.group(1)?.trim().orEmpty() else ""

            // 4. Cane Type: Value after Type:
            val typeMatcher = TOKEN_TYPE_PATTERN.matcher(text)
            val caneType = if (typeMatcher.find()) typeMatcher.group(1)?.trim().orEmpty() else "GENERAL"

            // 5. Purchy No.: Value after Purchy: (Primary Key)
            val purchyMatcher = TOKEN_PURCHY_PATTERN.matcher(text)
            val purchyNo = if (purchyMatcher.find()) purchyMatcher.group(1)?.trim().orEmpty() else ""

            // 6. Fortnight/Column (FC): Value after FC:
            val fcMatcher = TOKEN_FC_PATTERN.matcher(text)
            val fc = if (fcMatcher.find()) fcMatcher.group(1)?.trim().orEmpty() else ""

            // 7. Allotted Weight: Value after Wt:
            val wtMatcher = TOKEN_WT_PATTERN.matcher(text)
            val allottedWeight = if (wtMatcher.find()) wtMatcher.group(1)?.trim().orEmpty() else ""
            val numericWeight = extractNumericWeight(allottedWeight)

            // 8. Issue Date: Value after W Date: or Date:
            val dateMatcher = TOKEN_DATE_PATTERN.matcher(text)
            val issueDateStr = if (dateMatcher.find()) dateMatcher.group(1)?.trim().orEmpty() else ""

            val parsedDate = if (issueDateStr.isNotEmpty()) DateUtil.parseDate(issueDateStr) ?: Date() else Date()
            val (validUntilStr, validUntilEpochMs) = DateUtil.calculateValidUntil(parsedDate)

            // 9. Society: Value after Soc:
            val socMatcher = TOKEN_SOC_PATTERN.matcher(text)
            val society = if (socMatcher.find()) socMatcher.group(1)?.trim().orEmpty() else ""

            // 10. Centre: Value after Cnt:
            val cntMatcher = TOKEN_CNT_PATTERN.matcher(text)
            val centre = if (cntMatcher.find()) cntMatcher.group(1)?.trim().orEmpty() else ""

            if (purchyNo.isNotEmpty()) {
                return ParsedToken(
                    growerName = growerName.ifEmpty { "GROWER ($purchyNo)" },
                    growerCode = growerCode,
                    ugc = ugc,
                    caneType = caneType,
                    purchyNo = purchyNo,
                    fc = fc,
                    allottedWeight = allottedWeight.ifEmpty { "${numericWeight}ql" },
                    allottedWeightNumber = numericWeight,
                    issueDateStr = issueDateStr,
                    issueDateEpochMs = parsedDate.time,
                    validUntilStr = validUntilStr,
                    validUntilEpochMs = validUntilEpochMs,
                    society = society.ifEmpty { "Deoband" },
                    centre = centre.ifEmpty { "100" },
                    rawMessage = rawMessage
                )
            }

            // Fallback to NLP Entity Extraction for unstructured or scrubbed text
            return extractEntitiesWithNLP(rawMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Error in parseTokenMessage, falling back to NLP: ${e.message}")
            return extractEntitiesWithNLP(rawMessage)
        }
    }

    /**
     * Named Entity Recognition (NER) / Pattern-based Entity Extraction:
     * Dynamically identifies entity types, string lengths, and context windows
     * without relying on rigid label headers (e.g. processes scrubbed, Hindi, or conversational text).
     */
    fun extractEntitiesWithNLP(rawMessage: String): ParsedToken? {
        try {
            val text = capInput(rawMessage)
            if (text.isBlank()) return null

            // 1. Grower Code: match pattern e.g. 5501/12, 504/12, 5501/45
            val codeMatcher = NLP_CODE_PATTERN.matcher(text)
            val growerCode = if (codeMatcher.find()) codeMatcher.group(1).orEmpty() else ""

            // 2. Scan all long digit tokens for UGC (13-16 digits) vs Purchy (10-14 digits)
            val longNumbers = mutableListOf<String>()
            val numMatcher = NLP_LONG_NUM_PATTERN.matcher(text)
            while (numMatcher.find()) {
                numMatcher.group(1)?.let { longNumbers.add(it) }
            }

            var ugc = ""
            var purchyNo = ""

            // Look for explicit or relaxed prefixes
            val ugcLabelMatcher = NLP_UGC_LABEL_PATTERN.matcher(text)
            if (ugcLabelMatcher.find()) {
                ugc = ugcLabelMatcher.group(1).orEmpty()
            }

            val purchyLabelMatcher = NLP_PURCHY_LABEL_PATTERN.matcher(text)
            if (purchyLabelMatcher.find()) {
                purchyNo = purchyLabelMatcher.group(1).orEmpty()
            }

            for (num in longNumbers) {
                if (num == ugc || num == purchyNo) continue
                if (ugc.isEmpty() && (num.length >= 13 || num.startsWith("11") || num.startsWith("12"))) {
                    ugc = num
                } else if (purchyNo.isEmpty()) {
                    purchyNo = num
                }
            }

            if (purchyNo.isEmpty()) {
                if (longNumbers.isNotEmpty()) {
                    purchyNo = longNumbers.first()
                } else {
                    return null
                }
            }

            // 3. Extract Weight: Match explicit Wt label OR number with cane weight units (ql, qtl, quintal)
            val wtMatcher = NLP_WT_PATTERN.matcher(text)
            var allottedWeight = ""
            var numericWeight = 0.0
            while (wtMatcher.find()) {
                val numVal = wtMatcher.group(1)?.toDoubleOrNull() ?: wtMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                if (numVal in 10.0..250.0) {
                    val fullMatch = wtMatcher.group(0).orEmpty().trim()
                    allottedWeight = if (fullMatch.contains("ql", true) || fullMatch.contains("qtl", true)) fullMatch else "${numVal}ql"
                    numericWeight = numVal
                    break
                }
            }
            if (numericWeight <= 0.0) {
                allottedWeight = "45ql"
                numericWeight = 45.0
            }

            // 4. Extract Date
            val dateMatcher = NLP_DATE_PATTERN.matcher(text)
            val issueDateStr = if (dateMatcher.find()) dateMatcher.group(1).orEmpty() else DateUtil.getCurrentTimestampString()
            val parsedDate = if (issueDateStr.isNotEmpty()) DateUtil.parseDate(issueDateStr) ?: Date() else Date()
            val (validUntilStr, validUntilEpochMs) = DateUtil.calculateValidUntil(parsedDate)

            // 5. Extract Cane Type
            val caneTypeMatcher = NLP_CANE_TYPE_PATTERN.matcher(text)
            val caneType = if (caneTypeMatcher.find()) (caneTypeMatcher.group(1) ?: "").uppercase(Locale.ROOT) else "E-RATOON"

            // 6. Extract Fortnight/Column (FC)
            val fcMatcher = NLP_FC_PATTERN.matcher(text)
            val fc = if (fcMatcher.find()) fcMatcher.group(1).orEmpty() else "1/2"

            // 7. Extract Society / Mill
            val socMatcher = NLP_SOC_PATTERN.matcher(text)
            val society = if (socMatcher.find()) {
                val rawSoc = socMatcher.group(1) ?: "Deoband"
                rawSoc.lowercase(Locale.ROOT).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            } else "Deoband"

            // 8. Extract Grower Name via Context Boundary
            var growerName = ""
            if (growerCode.isNotEmpty() && text.contains(growerCode)) {
                val beforeCode = text.substringBefore(growerCode).trim()
                val cleaned = beforeCode.replace(Regex("(?i)\\b(Code|G\\.Code|Grower|Name|Cane)\\b:?"), "").trim()
                if (cleaned.length >= 3 && cleaned.any { it.isLetter() }) {
                    growerName = cleaned
                }
            }
            if (growerName.isEmpty() && growerCode.isNotEmpty()) {
                val afterCode = text.substringAfter(growerCode).trim()
                val words = afterCode.split("\\s+".toRegex()).take(4)
                    .filter { it.all { c -> c.isLetter() || c == '/' } && !it.equals("UGC", true) && !it.equals("TAUL", true) && !it.equals("PURCHY", true) }
                if (words.isNotEmpty()) {
                    growerName = words.joinToString(" ")
                }
            }
            if (growerName.isBlank()) {
                growerName = "GROWER ($purchyNo)"
            }
            growerName = growerName.trimEnd('/', ' ', '-')

            return ParsedToken(
                growerName = growerName,
                growerCode = growerCode,
                ugc = ugc,
                caneType = caneType,
                purchyNo = purchyNo,
                fc = fc,
                allottedWeight = allottedWeight,
                allottedWeightNumber = numericWeight,
                issueDateStr = issueDateStr,
                issueDateEpochMs = parsedDate.time,
                validUntilStr = validUntilStr,
                validUntilEpochMs = validUntilEpochMs,
                society = society,
                centre = "100",
                rawMessage = rawMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "NLP entity extraction failed: ${e.message}", e)
            return null
        }
    }

    /**
     * Parses subsequent Weighment / Taul Receipt SMS from sugar mill.
     * Supports both structured mill messages and unstructured text.
     */
    fun parseWeighmentReceipt(rawMessage: String): ParsedWeighmentReceipt? {
        try {
            val text = capInput(rawMessage)

            // 1. Purchy Number: "MILL PURCHY NO 8800102" or "Purchy:700100200301"
            val purchyMatcher = REC_PURCHY_PATTERN.matcher(text)
            var purchyNo = if (purchyMatcher.find()) purchyMatcher.group(1)?.trim() else null

            // 2. Grower Code: "5501/45" at start, or after "Code:"
            val codeMatcher = REC_CODE_PATTERN.matcher(text)
            val growerCode = if (codeMatcher.find()) codeMatcher.group(1)?.trim() else null

            // 3. Grower Name: "SUNITA DEVI/RAMESH" between code and TAUL/DINAAK
            val nameMatcher = REC_NAME_PATTERN.matcher(text)
            val growerName = if (nameMatcher.find()) nameMatcher.group(1)?.trim()?.trimEnd('/', ' ', '-') else null

            // 4. UGC: "UGC:99887766550011"
            val ugcMatcher = TOKEN_UGC_PATTERN.matcher(text)
            val ugc = if (ugcMatcher.find()) ugcMatcher.group(1)?.trim() else null

            // 5. Net Weight: "WT 54.9 QTL" or "Net:44.80ql" or "Net Wt:44.80ql"
            val netMatcher = REC_NET_PATTERN.matcher(text)
            var netWeightStr = if (netMatcher.find()) netMatcher.group(1)?.trim().orEmpty() else ""

            // 6. Gross Weight
            val grossMatcher = REC_GROSS_PATTERN.matcher(text)
            val grossWeight = if (grossMatcher.find()) grossMatcher.group(1)?.trim() else null

            // 7. Tare Weight
            val tareMatcher = REC_TARE_PATTERN.matcher(text)
            val tareWeight = if (tareMatcher.find()) tareMatcher.group(1)?.trim() else null

            // 8. Weighment Date: "DINAAK 19-NOV-26" or "Date:13/11/2026"
            val dateMatcher = REC_DATE_PATTERN.matcher(text)
            val dateStr = if (dateMatcher.find()) dateMatcher.group(1)?.trim() else DateUtil.getCurrentTimestampString()

            // 9. Receipt / Mill Name: "TRIVENI", "DEOBAND", etc.
            val millMatcher = REC_MILL_PATTERN.matcher(text)
            val millOrSociety = if (millMatcher.find()) millMatcher.group(1)?.trim() else null

            // 10. Receipt Number
            val receiptMatcher = REC_RECEIPT_PATTERN.matcher(text)
            val receiptNo = if (receiptMatcher.find()) {
                receiptMatcher.group(1)?.trim()?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
            } else if (purchyNo != null) {
                purchyNo.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
            } else {
                null
            }

            if (netWeightStr.isEmpty() && purchyNo == null && growerCode == null) {
                return extractWeighmentEntitiesWithNLP(rawMessage)
            }

            val numericNet = extractNumericWeight(netWeightStr)

            return ParsedWeighmentReceipt(
                purchyNo = purchyNo,
                growerCode = growerCode,
                ugc = ugc,
                growerName = growerName,
                societyOrMill = millOrSociety,
                actualNettedWeight = netWeightStr,
                actualNettedWeightNumber = numericNet,
                dispatchDateStr = dateStr,
                receiptNo = receiptNo,
                grossWeight = grossWeight,
                tareWeight = tareWeight,
                rawMessage = rawMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing weighment receipt: ${e.message}", e)
            return extractWeighmentEntitiesWithNLP(rawMessage)
        }
    }

    /**
     * Fallback NLP entity extraction for Weighment / Mill receipts
     */
    fun extractWeighmentEntitiesWithNLP(rawMessage: String): ParsedWeighmentReceipt? {
        try {
            val text = capInput(rawMessage)
            if (text.isBlank()) return null

            val wtMatcher = NLP_REC_WT_PATTERN.matcher(text)
            var netWeightStr = ""
            var netWeightNum = 0.0
            while (wtMatcher.find()) {
                val num = wtMatcher.group(1)?.toDoubleOrNull() ?: wtMatcher.group(2)?.toDoubleOrNull() ?: 0.0
                if (num in 15.0..250.0) {
                    netWeightNum = num
                    netWeightStr = "${num}ql"
                    break
                }
            }

            val codeMatcher = NLP_CODE_PATTERN.matcher(text)
            val growerCode = if (codeMatcher.find()) codeMatcher.group(1) else null

            val purchyMatcher = NLP_REC_PURCHY_PATTERN.matcher(text)
            var purchyNo = if (purchyMatcher.find()) purchyMatcher.group(1) else null

            if (purchyNo == null) {
                val longNum = NLP_REC_LONG_NUM_PATTERN.matcher(text)
                if (longNum.find()) purchyNo = longNum.group(1)
            }

            val millMatcher = NLP_REC_MILL_PATTERN.matcher(text)
            val mill = if (millMatcher.find()) millMatcher.group(1) else "MILL"

            val dateMatcher = NLP_DATE_PATTERN.matcher(text)
            val dateStr = if (dateMatcher.find()) dateMatcher.group(1) else DateUtil.getCurrentTimestampString()

            if (netWeightNum <= 0.0 && purchyNo == null && growerCode == null) return null

            return ParsedWeighmentReceipt(
                purchyNo = purchyNo,
                growerCode = growerCode,
                ugc = null,
                growerName = null,
                societyOrMill = mill,
                actualNettedWeight = netWeightStr.ifEmpty { "${netWeightNum}ql" },
                actualNettedWeightNumber = netWeightNum,
                dispatchDateStr = dateStr,
                receiptNo = purchyNo,
                grossWeight = null,
                tareWeight = null,
                rawMessage = rawMessage
            )
        } catch (e: Exception) {
            Log.e(TAG, "NLP weighment extraction failed: ${e.message}", e)
            return null
        }
    }

    private fun extractNumericWeight(weightStr: String): Double {
        if (weightStr.isBlank()) return 0.0
        val numericPart = weightStr.replace("[^0-9.]".toRegex(), "")
        return numericPart.toDoubleOrNull() ?: 0.0
    }
}
