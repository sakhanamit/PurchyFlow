package com.sugarcane.purchyflow.export

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.sugarcane.purchyflow.util.SecureLog as Log
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.data.repository.PurchyRepository
import com.sugarcane.purchyflow.parser.ScannedReceiptData
import com.sugarcane.purchyflow.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

data class ImportedPurchyRow(
    val growerName: String,
    val dateStr: String,
    val growerCode: String = "",
    val purchyNo: String = "",
    val receiptNo: String,
    val netWeight: Double? = null,
    val grossWeight: Double? = null,
    val tareWeight: Double? = null,
    val society: String = "Mill",
    val caneType: String = "GENERAL"
)

data class ImportSummary(
    val totalProcessed: Int,
    val successCount: Int,
    val failedCount: Int,
    val message: String
)

/**
 * Intelligent Excel (.xlsx) and CSV (.csv) Importer.
 *
 * Supports:
 * 1. Standard CSV files (including PurchyFlow's own exported CSVs, mill exports, e-Ganna CSVs).
 * 2. Modern Microsoft Excel (.xlsx) files via zero-dependency XML ZIP streaming.
 * 3. Smart header recognition in Hindi & English (Grower Name, Date, Code, Receipt No, Net Wt).
 */
object ExcelImporter {
    private const val TAG = "ExcelImporter"

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        repository: PurchyRepository
    ): ImportSummary = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var fileName = "import.csv"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex) ?: "import.csv"
            }
        }

        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                return@withContext importStream(stream, fileName, repository)
            } ?: ImportSummary(0, 0, 0, "Could not open selected file")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing file", e)
            ImportSummary(0, 0, 0, "Import error: ${e.message}")
        }
    }

    suspend fun importStream(
        stream: InputStream,
        fileName: String,
        repository: PurchyRepository
    ): ImportSummary = withContext(Dispatchers.IO) {
        val lowerName = fileName.lowercase(Locale.ENGLISH)
        val rows: List<List<String>> = try {
            if (lowerName.endsWith(".xlsx")) {
                readXlsxRows(stream)
            } else {
                readCsvRows(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading spreadsheet rows", e)
            return@withContext ImportSummary(0, 0, 0, "Failed reading spreadsheet: ${e.message}")
        }

        if (rows.isEmpty()) {
            return@withContext ImportSummary(0, 0, 0, "The spreadsheet appears to be empty.")
        }

        // Parse rows into ImportedPurchyRow models
        val parsedRows = parseRows(rows)
        if (parsedRows.isEmpty()) {
            return@withContext ImportSummary(
                totalProcessed = rows.size,
                successCount = 0,
                failedCount = rows.size,
                message = "Could not find valid sugarcane data in sheet. Ensure columns for Name, Date, Receipt, or Net Weight exist."
            )
        }

        var successCount = 0
        var failedCount = 0

        for (item in parsedRows) {
            try {
                val cleanReceipt = item.receiptNo
                    .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                    .trim()
                val cleanPurchy = item.purchyNo
                    .replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                    .trim()
                val cleanName = item.growerName
                    .replace(Regex("/+"), " / ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .trimEnd('/', ' ', '-')

                val scanned = ScannedReceiptData(
                    millPurchyNo = cleanReceipt,
                    societyPurchyNo = cleanPurchy.ifEmpty { cleanReceipt },
                    growerName = cleanName,
                    growerCode = item.growerCode,
                    netWeight = item.netWeight ?: 0.0,
                    grossWeight = item.grossWeight,
                    tareWeight = item.tareWeight,
                    dateStr = item.dateStr,
                    dateEpochMs = DateUtil.parseDate(item.dateStr)?.time ?: 0L,
                    millOrSociety = item.society.ifEmpty { "Mill Weighment" },
                    caneType = item.caneType.ifEmpty { "GENERAL" },
                    rawOcrText = "Excel Import: $cleanName, Receipt:$cleanReceipt, Wt:${item.netWeight}"
                )

                val saved = repository.saveScannedReceipt(scanned)
                if (saved) {
                    successCount++
                } else {
                    failedCount++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to import row: ${e.message}")
                failedCount++
            }
        }

        ImportSummary(
            totalProcessed = parsedRows.size,
            successCount = successCount,
            failedCount = failedCount,
            message = "Successfully imported $successCount receipts ($failedCount skipped/duplicate)."
        )
    }

    /**
     * Reads CSV rows handling commas, tabs, quotes, and UTF-8 BOM.
     */
    fun readCsvRows(stream: InputStream): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line?.trimStart('\uFEFF')?.trim() ?: continue
            if (trimmed.isEmpty()) continue
            val tokens = parseCsvLine(trimmed)
            if (tokens.isNotEmpty()) {
                rows.add(tokens)
            }
        }
        return rows
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val delimiter = if (line.contains('\t') && !line.contains(',')) '\t' else ','
        val sb = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == delimiter && !inQuotes) {
                result.add(sb.toString().trim().trim('"'))
                sb.clear()
            } else {
                sb.append(c)
            }
        }
        result.add(sb.toString().trim().trim('"'))
        return result
    }

    /**
     * High-speed, zero-dependency .xlsx parser using Java ZipInputStream
     * and XmlPullParser. Reads sharedStrings.xml then sheet1.xml.
     */
    fun readXlsxRows(stream: InputStream): List<List<String>> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()

        // Step 1: In-memory Zip buffer to read sharedStrings first, then sheet
        val zipBytes = stream.readBytes()

        // Pass 1: Read xl/sharedStrings.xml
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.equals("xl/sharedStrings.xml", ignoreCase = true)) {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType
                    var currentString: StringBuilder? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val name = parser.name
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (name.equals("si", ignoreCase = true)) {
                                    currentString = StringBuilder()
                                } else if (name.equals("t", ignoreCase = true) && currentString != null) {
                                    currentString.append(parser.nextText())
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (name.equals("si", ignoreCase = true) && currentString != null) {
                                    sharedStrings.add(currentString.toString())
                                    currentString = null
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }

        // Pass 2: Read sheet1.xml (or first worksheet)
        ZipInputStream(zipBytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("xl/worksheets/sheet", ignoreCase = true) && entry.name.endsWith(".xml")) {
                    val parser = Xml.newPullParser()
                    parser.setInput(zip, "UTF-8")
                    var eventType = parser.eventType

                    var currentRow = mutableListOf<String>()
                    var cellType = ""
                    var cellColIndex = -1
                    var inInlineString = false
                    var inlineTextBuilder: StringBuilder? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        val name = parser.name
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                if (name.equals("row", ignoreCase = true)) {
                                    currentRow = mutableListOf()
                                } else if (name.equals("c", ignoreCase = true)) {
                                    cellType = parser.getAttributeValue(null, "t").orEmpty()
                                    val ref = parser.getAttributeValue(null, "r")
                                    cellColIndex = colRefToIndex(ref)
                                    inlineTextBuilder = null
                                    inInlineString = false
                                } else if (name.equals("is", ignoreCase = true)) {
                                    inInlineString = true
                                    inlineTextBuilder = StringBuilder()
                                } else if (name.equals("t", ignoreCase = true) && inInlineString) {
                                    inlineTextBuilder?.append(parser.nextText())
                                } else if (name.equals("v", ignoreCase = true)) {
                                    val text = parser.nextText()
                                    val resolved = if (cellType == "s") {
                                        val idx = text.toIntOrNull()
                                        if (idx != null && idx in sharedStrings.indices) {
                                            sharedStrings[idx]
                                        } else text
                                    } else {
                                        text
                                    }
                                    putCellAt(currentRow, cellColIndex, resolved)
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                if (name.equals("is", ignoreCase = true)) {
                                    inInlineString = false
                                } else if (name.equals("c", ignoreCase = true)) {
                                    if (cellType == "inlineStr" && inlineTextBuilder != null) {
                                        putCellAt(currentRow, cellColIndex, inlineTextBuilder.toString())
                                    }
                                    inlineTextBuilder = null
                                } else if (name.equals("row", ignoreCase = true)) {
                                    if (currentRow.any { it.isNotBlank() }) {
                                        sheetRows.add(currentRow)
                                    }
                                }
                            }
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }

        return sheetRows
    }

    /**
     * Parses raw tabular rows into clean ImportedPurchyRow models by mapping headers.
     */
    fun parseRows(rawRows: List<List<String>>): List<ImportedPurchyRow> {
        if (rawRows.isEmpty()) return emptyList()

        // Find header row (usually row 0, 1, or 2)
        var headerIndex = -1
        var nameCol = -1
        var dateCol = -1
        var codeCol = -1
        var purchyCol = -1
        var receiptCol = -1
        var netWeightCol = -1
        var grossWeightCol = -1
        var tareWeightCol = -1
        var societyCol = -1
        var caneTypeCol = -1

        for (i in 0 until minOf(8, rawRows.size)) {
            val row = rawRows[i]
            // Skip known report title / metadata banner rows
            val firstLower = row.firstOrNull()?.lowercase(Locale.ENGLISH)?.trim().orEmpty()
            if (firstLower.startsWith("purchyflow") || firstLower.startsWith("generated:") || firstLower.startsWith("sugar mill")) {
                continue
            }

            var rowNameCol = -1
            var rowDateCol = -1
            var rowCodeCol = -1
            var rowPurchyCol = -1
            var rowReceiptCol = -1
            var rowNetWeightCol = -1
            var rowGrossWeightCol = -1
            var rowTareWeightCol = -1
            var rowSocietyCol = -1
            var rowCaneTypeCol = -1

            for (c in row.indices) {
                val header = row[c].lowercase(Locale.ENGLISH).trim()
                if (header.isEmpty() || header.length > 40) continue
                if (header.contains("name") || (header.contains("grower") && !header.contains("code")) || header.contains("कृषक") || header.contains("farmer")) {
                    if (rowNameCol < 0) rowNameCol = c
                } else if (header.contains("date") || header.contains("दिनांक") || header.contains("weighed") || header.contains("taul") || header.contains("tareekh")) {
                    if (rowDateCol < 0) rowDateCol = c
                } else if (header.contains("code") || header.contains("कोड") || header.contains("grower code")) {
                    if (rowCodeCol < 0) rowCodeCol = c
                } else if (header.contains("receipt") || header.contains("mill purchy") || header.contains("रसीद") || header.contains("ticket")) {
                    rowReceiptCol = c
                } else if (header.contains("purchy")) {
                    if (rowPurchyCol < 0) rowPurchyCol = c
                } else if (header.contains("net") || header.contains("शुद्ध भार") || header.contains("shuddh")) {
                    rowNetWeightCol = c
                } else if (header.contains("weight") && rowNetWeightCol < 0 && !header.contains("gross") && !header.contains("tare")) {
                    rowNetWeightCol = c
                } else if (header == "wt" && rowNetWeightCol < 0) {
                    rowNetWeightCol = c
                } else if (header.contains("gross") || header.contains("सकल") || header.contains("bhari")) {
                    if (rowGrossWeightCol < 0) rowGrossWeightCol = c
                } else if (header.contains("tare") || header.contains("खाली") || header.contains("khali")) {
                    if (rowTareWeightCol < 0) rowTareWeightCol = c
                } else if (header.contains("society") || header.contains("mill") || header.contains("समिति") || header.contains("centre")) {
                    if (rowSocietyCol < 0) rowSocietyCol = c
                } else if (header.contains("cane") || header.contains("type") || header.contains("variety") || header.contains("जाति")) {
                    if (rowCaneTypeCol < 0) rowCaneTypeCol = c
                }
            }

            val matchedCount = listOf(rowNameCol, rowDateCol, rowCodeCol, rowPurchyCol, rowReceiptCol, rowNetWeightCol).count { it >= 0 }
            // Require at least 2 distinct header columns so title/metadata rows are never mistaken for headers
            if (matchedCount >= 2) {
                headerIndex = i
                nameCol = rowNameCol
                dateCol = rowDateCol
                codeCol = rowCodeCol
                purchyCol = rowPurchyCol
                receiptCol = rowReceiptCol
                netWeightCol = rowNetWeightCol
                grossWeightCol = rowGrossWeightCol
                tareWeightCol = rowTareWeightCol
                societyCol = rowSocietyCol
                caneTypeCol = rowCaneTypeCol
                break
            }
        }

        val results = mutableListOf<ImportedPurchyRow>()
        val startRow = if (headerIndex >= 0) headerIndex + 1 else 0

        for (r in startRow until rawRows.size) {
            val row = rawRows[r]
            if (row.isEmpty()) continue

            // Skip title/total summary rows
            val firstCell = row.firstOrNull()?.trim() ?: ""
            if (firstCell.startsWith("TOTAL", ignoreCase = true) || firstCell.startsWith("Sugar Mill", ignoreCase = true)) {
                continue
            }

            var name = ""
            var date = ""
            var code = ""
            var purchyNo = ""
            var receipt = ""
            var netWeight: Double? = null
            var grossWeight: Double? = null
            var tareWeight: Double? = null
            var society = "Mill"
            var caneType = "GENERAL"

            if (headerIndex >= 0) {
                if (nameCol in row.indices) name = stripFormulaGuard(row[nameCol])
                if (dateCol in row.indices) date = stripFormulaGuard(row[dateCol])
                if (codeCol in row.indices) code = stripFormulaGuard(row[codeCol])
                if (purchyCol in row.indices) purchyNo = stripFormulaGuard(row[purchyCol])
                if (receiptCol in row.indices) receipt = stripFormulaGuard(row[receiptCol])
                if (receipt.isEmpty() && purchyNo.isNotEmpty()) receipt = purchyNo
                if (netWeightCol in row.indices) netWeight = cleanWeightNumber(row[netWeightCol])
                if (grossWeightCol in row.indices) grossWeight = cleanWeightNumber(row[grossWeightCol])
                if (tareWeightCol in row.indices) tareWeight = cleanWeightNumber(row[tareWeightCol])
                if (societyCol in row.indices) society = stripFormulaGuard(row[societyCol])
                if (caneTypeCol in row.indices) caneType = stripFormulaGuard(row[caneTypeCol])
            } else {
                // Heuristic column detection if no header row found
                for (cell in row) {
                    val trimmed = cell.trim()
                    if (trimmed.isEmpty()) continue
                    if (date.isEmpty() && Regex("\\d{1,4}[-/.]\\d{1,2}[-/.]\\d{1,4}").containsMatchIn(trimmed)) {
                        date = trimmed
                    } else if (code.isEmpty() && Regex("^\\d{2,5}/\\d{1,4}$").matches(trimmed)) {
                        code = trimmed
                    } else if (netWeight == null && Regex("^\\d{1,3}\\.\\d{1,3}$").matches(trimmed)) {
                        netWeight = trimmed.toDoubleOrNull()
                    } else if (receipt.isEmpty() && Regex("^\\d{5,14}$").matches(trimmed)) {
                        receipt = trimmed
                    } else if (name.isEmpty() && Regex("^[A-Za-z\\s/]{3,35}$").matches(trimmed)) {
                        name = trimmed
                    }
                }
            }

            // Defaults
            if (date.isEmpty()) {
                date = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
            }
            if (receipt.isEmpty()) {
                receipt = "MILL-${System.currentTimeMillis() % 1000000 + r}"
            }
            if (name.isEmpty()) {
                name = if (code.isNotEmpty()) "GROWER ($code)" else "GROWER #$receipt"
            }

            // Add valid row
            results.add(
                ImportedPurchyRow(
                    growerName = name,
                    dateStr = date,
                    growerCode = code,
                    purchyNo = purchyNo,
                    receiptNo = receipt,
                    netWeight = netWeight,
                    grossWeight = grossWeight,
                    tareWeight = tareWeight,
                    society = society,
                    caneType = caneType
                )
            )
        }

        return results
    }

    private fun stripFormulaGuard(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length > 1 && trimmed[0] == '\'' && trimmed[1] in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            trimmed.substring(1)
        } else {
            trimmed
        }
    }

    private fun colRefToIndex(ref: String?): Int {
        if (ref.isNullOrBlank()) return -1
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return if (col > 0) col - 1 else -1
    }

    private fun putCellAt(row: MutableList<String>, colIndex: Int, value: String) {
        if (colIndex < 0) {
            row.add(value)
            return
        }
        while (row.size <= colIndex) {
            row.add("")
        }
        row[colIndex] = value
    }

    private fun cleanWeightNumber(raw: String): Double? {
        val cleaned = raw.replace(Regex("[^0-9.]"), "").trim()
        return cleaned.toDoubleOrNull()
    }
}
