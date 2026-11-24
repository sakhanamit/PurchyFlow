package com.sugarcane.purchyflow.parser

import com.sugarcane.purchyflow.util.DateUtil
import java.util.regex.Pattern

/**
 * Data class representing extracted data from a physical mill weighment receipt.
 */
data class ScannedReceiptData(
    val millPurchyNo: String = "",          // e.g. "8800101"
    val societyPurchyNo: String = "",       // e.g. "700100200304" (Token No in DB)
    val growerCode: String = "",            // e.g. "5501/34"
    val growerName: String = "",            // e.g. "DEEPAK VERMA"
    val fatherName: String = "",            // e.g. "RAMESH CHANDRA"
    val village: String = "",               // e.g. "RAMPUR"
    val ugc: String = "",                   // e.g. "99887766550044"
    val caneType: String = "",              // e.g. "Early"
    val grossWeight: Double? = null,        // e.g. 82.95
    val tareWeight: Double? = null,         // e.g. 34.70
    val netWeight: Double? = null,          // e.g. 47.77
    val dateStr: String = "",               // e.g. "18-11-2026"
    val dateEpochMs: Long = 0L,
    val millOrSociety: String = "Triveni Deoband",
    val rawOcrText: String = ""
)

/**
 * OCR Parser for Indian Sugar Mill Weighment Receipts (e.g. Triveni, Deoband, Bajaj, Dhampur, etc.).
 */
object ReceiptOcrParser {

    fun parse(ocrText: String): ScannedReceiptData {
        val cleanText = ocrText.replace("\r", "")

        var millPurchy = ""
        var societyPurchy = ""
        var growerCode = ""
        var growerName = ""
        var fatherName = ""
        var village = ""
        var ugc = ""
        var caneType = "GENERAL"
        var grossWeight: Double? = null
        var tareWeight: Double? = null
        var netWeight: Double? = null
        var dateStr = ""
        var millName = "Triveni"
        var centre = "Deoband"

        // 1. Identify Mill and Centre
        if (cleanText.contains("Triveni", ignoreCase = true) || cleanText.contains("त्रिवेणी", ignoreCase = true)) {
            millName = "Triveni"
        } else if (cleanText.contains("Dhampur", ignoreCase = true) || cleanText.contains("धामपुर", ignoreCase = true)) {
            millName = "Dhampur"
        } else if (cleanText.contains("Bajaj", ignoreCase = true) || cleanText.contains("बजाज", ignoreCase = true)) {
            millName = "Bajaj"
        } else if (cleanText.contains("Uttam", ignoreCase = true) || cleanText.contains("उत्तम", ignoreCase = true)) {
            millName = "Uttam"
        }

        if (cleanText.contains("Deoband", ignoreCase = true) || cleanText.contains("देवबंद", ignoreCase = true)) {
            centre = "Deoband"
        }

        // 2. Extract UGC (13-16 digits)
        val ugcPattern = Pattern.compile("(?:UGC|यूजीसी)[\\s:-]*([0-9]{12,16})", Pattern.CASE_INSENSITIVE)
        val ugcMatcher = ugcPattern.matcher(cleanText)
        if (ugcMatcher.find()) {
            ugc = ugcMatcher.group(1)?.trim().orEmpty()
        } else {
            // Fallback: look for 14-digit number
            val standalone14 = Pattern.compile("\\b([0-9]{14})\\b")
            val m = standalone14.matcher(cleanText)
            if (m.find()) {
                ugc = m.group(1)?.trim().orEmpty()
            }
        }

        // 3. Extract Mill Purchy No (usually 6-8 digits, e.g., 8001986)
        val millPurchyPattern = Pattern.compile("(?:मिल\\s*पर्ची\\s*नं|MILL\\s*PURCHY\\s*(?:NO)?)[.\\s:-]*([0-9]{5,9})", Pattern.CASE_INSENSITIVE)
        val millPurchyMatcher = millPurchyPattern.matcher(cleanText)
        if (millPurchyMatcher.find()) {
            millPurchy = millPurchyMatcher.group(1)?.trim().orEmpty()
        }

        // 4. Extract Society Purchy No (usually 10-12 digits, e.g. 102461123125)
        val socPurchyPattern = Pattern.compile("(?:समिति\\s*पर्ची\\s*नं|SOC(?:IETY)?\\s*PURCHY\\s*(?:NO)?)[.\\s:-]*([0-9]{10,13})", Pattern.CASE_INSENSITIVE)
        val socPurchyMatcher = socPurchyPattern.matcher(cleanText)
        if (socPurchyMatcher.find()) {
            societyPurchy = socPurchyMatcher.group(1)?.trim().orEmpty()
        } else {
            // Look for any 12-digit number starting with 102
            val p12 = Pattern.compile("\\b(102[0-9]{9})\\b")
            val m = p12.matcher(cleanText)
            if (m.find()) {
                societyPurchy = m.group(1)?.trim().orEmpty()
            }
        }

        // 5. Extract Grower Code (e.g. 5501 /12 or 5501/45)
        val codePattern = Pattern.compile("(?:कृषक\\s*कोड|Code|GROWER\\s*CODE)[.\\s:-]*([0-9]{2,6}\\s*/\\s*[0-9]{1,5})", Pattern.CASE_INSENSITIVE)
        val codeMatcher = codePattern.matcher(cleanText)
        if (codeMatcher.find()) {
            growerCode = codeMatcher.group(1)?.replace(" ", "")?.trim().orEmpty()
        } else {
            // Fallback: look for pattern like "5501 /12" or "5501/12"
            val rawCode = Pattern.compile("\\b([0-9]{2,5}\\s*/\\s*[0-9]{1,4})\\b")
            val m = rawCode.matcher(cleanText)
            if (m.find()) {
                growerCode = m.group(1)?.replace(" ", "")?.trim().orEmpty()
            }
        }

        // 6. Extract Grower Name & Father Name
        // Pattern: "कृषक श्री DEEPAK VERMA" or "पुत्र श्री RAMESH CHANDRA"
        val namePattern = Pattern.compile("(?:कृषक\\s*श्री|GROWER(?:\\s*NAME)?)[.\\s:-]*([a-zA-Z\\s]{3,35})(?=\\n|पुत्र|श्री|$)", Pattern.CASE_INSENSITIVE)
        val nameMatcher = namePattern.matcher(cleanText)
        if (nameMatcher.find()) {
            growerName = nameMatcher.group(1)?.trim()?.trimEnd('/', ' ', '-').orEmpty()
        }

        val fatherPattern = Pattern.compile("(?:पुत्र\\s*श्री|FATHER(?:\\s*NAME)?|S/O)[.\\s:-]*([a-zA-Z\\s]{3,35})(?=\\n|विवरण|$)", Pattern.CASE_INSENSITIVE)
        val fatherMatcher = fatherPattern.matcher(cleanText)
        if (fatherMatcher.find()) {
            fatherName = fatherMatcher.group(1)?.trim()?.trimEnd('/', ' ', '-').orEmpty()
        }

        val villagePattern = Pattern.compile("(?:ग्राम|VILLAGE)[.\\s:-]*([a-zA-Z\\s]{3,30})(?=\\n|कृषक|$)", Pattern.CASE_INSENSITIVE)
        val villageMatcher = villagePattern.matcher(cleanText)
        if (villageMatcher.find()) {
            village = villageMatcher.group(1)?.trim().orEmpty()
        }

        // 7. Extract Cane Variety / Type (e.g. Early, General, Ratoon)
        val typePattern = Pattern.compile("(?:गन्ना\\s*जाति|CANE\\s*TYPE|TYPE)[.\\s:-]*([a-zA-Z0-9_-]+)", Pattern.CASE_INSENSITIVE)
        val typeMatcher = typePattern.matcher(cleanText)
        if (typeMatcher.find()) {
            caneType = typeMatcher.group(1)?.trim().orEmpty()
        } else if (cleanText.contains("Early", ignoreCase = true)) {
            caneType = "Early"
        }

        // 8. Extract Weights (Gross, Tare, Net)
        // Look for:
        // वजन भरी गाड़ी / Gross: 82.95
        // वजन खाली गाड़ी / Tare: 34.70
        // शुद्ध भार गन्ना / Net: 47.77
        val grossPattern = Pattern.compile("(?:वजन\\s*भरी\\s*गाड़ी|GROSS)[.\\s:-]*([0-9]{1,3}\\.[0-9]{1,3})", Pattern.CASE_INSENSITIVE)
        val grossMatcher = grossPattern.matcher(cleanText)
        if (grossMatcher.find()) {
            grossWeight = grossMatcher.group(1)?.toDoubleOrNull()
        }

        val tarePattern = Pattern.compile("(?:वजन\\s*खाली\\s*गाड़ी|TARE)[.\\s:-]*([0-9]{1,3}\\.[0-9]{1,3})", Pattern.CASE_INSENSITIVE)
        val tareMatcher = tarePattern.matcher(cleanText)
        if (tareMatcher.find()) {
            tareWeight = tareMatcher.group(1)?.toDoubleOrNull()
        }

        val netPattern = Pattern.compile("(?:शुद्ध\\s*भार\\s*गन्ना|शुद्ध\\s*भार|NET(?:\\s*WT)?)[.\\s:-]*([0-9]{1,3}\\.[0-9]{1,3})", Pattern.CASE_INSENSITIVE)
        val netMatcher = netPattern.matcher(cleanText)
        if (netMatcher.find()) {
            netWeight = netMatcher.group(1)?.toDoubleOrNull()
        } else {
            // Fallback: look for 2 decimals before AMOUNT or near footer
            if (grossWeight != null && tareWeight != null) {
                netWeight = String.format(java.util.Locale.ENGLISH, "%.2f", grossWeight - tareWeight).toDoubleOrNull()
            }
        }

        // 9. Extract Date: e.g. 18-11-2026 or 18/11/2026
        val datePattern = Pattern.compile("\\b([0-9]{1,2}[/\\-.][0-9]{1,2}[/\\-.][0-9]{4})\\b")
        val dateMatcher = datePattern.matcher(cleanText)
        if (dateMatcher.find()) {
            dateStr = dateMatcher.group(1)?.trim().orEmpty()
        }

        val dateEpochMs = if (dateStr.isNotEmpty()) {
            DateUtil.parseDate(dateStr)?.time ?: System.currentTimeMillis()
        } else {
            System.currentTimeMillis()
        }

        // If society purchy is empty but mill purchy is found, use mill purchy as key
        val finalSocietyPurchy = societyPurchy.ifEmpty { millPurchy }

        return ScannedReceiptData(
            millPurchyNo = millPurchy,
            societyPurchyNo = finalSocietyPurchy,
            growerCode = growerCode,
            growerName = growerName,
            fatherName = fatherName,
            village = village,
            ugc = ugc,
            caneType = caneType,
            grossWeight = grossWeight,
            tareWeight = tareWeight,
            netWeight = netWeight,
            dateStr = dateStr.ifEmpty { DateUtil.getCurrentTimestampString() },
            dateEpochMs = dateEpochMs,
            millOrSociety = "$millName $centre".trim(),
            rawOcrText = cleanText
        )
    }
}
