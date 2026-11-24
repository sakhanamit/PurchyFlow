package com.sugarcane.purchyflow.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.util.DateUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Report Exporter for Dispatched Sugarcane weighed at the Sugar Mill.
 * Generates both Microsoft Excel (CSV) and printable PDF documents containing:
 * Date, Grower Name, Receipt Number, and Net Weight.
 */
object ExcelReportGenerator {

    /**
     * Generates a fully formatted Excel-compliant spreadsheet (.csv)
     * specifically for mill-weighed dispatched data.
     */
    suspend fun generateExcelCsvReport(context: Context, dispatchedTokens: List<PurchyToken>): File = withContext(Dispatchers.IO) {
        val reportDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "PurchyFlow_Mill_Weighment_$timeStamp.csv"
        val reportFile = File(reportDir, fileName)

        val totalWeight = dispatchedTokens.sumOf { it.actualNettedWeightNumber ?: 0.0 }

        FileWriter(reportFile).use { writer ->
            // Excel UTF-8 BOM so Excel opens with proper accents/characters
            writer.write("\uFEFF")

            // Title and Metadata Header
            writer.append("Sugar Mill Sugarcane Weighment & Dispatch Report\n")
            writer.append("Generated On:,\"${DateUtil.getCurrentTimestampString()}\"\n")
            writer.append("Total Dispatched Receipts:,${dispatchedTokens.size}\n")
            writer.append("Total Mill Netted Weight:,\"${String.format(Locale.ENGLISH, "%.2f ql", totalWeight)}\"\n\n")

            // Primary required columns: Date, Name, Receipt Number, Weight
            val headers = listOf(
                "Date",
                "Name",
                "Receipt Number",
                "Weight",
                "Purchy No",
                "Gross Weight",
                "Tare Weight",
                "Cane Type",
                "Centre",
                "Society"
            )
            writer.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")

            // Rows
            for (token in dispatchedTokens) {
                val cleanReceipt = token.weightReceiptNo
                    ?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                    ?.trim() ?: "N/A"
                val row = listOf(
                    token.dispatchDateStr ?: token.issueDateStr,
                    token.growerName,
                    cleanReceipt,
                    token.actualNettedWeight ?: "${token.allottedWeight} (Allotted)",
                    token.purchyNo,
                    token.grossWeight ?: "-",
                    token.tareWeight ?: "-",
                    token.caneType,
                    token.centre,
                    token.society
                )
                writer.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
            }

            // Summary Total Row
            writer.append("\n")
            val summaryRow = listOf(
                "TOTAL",
                "${dispatchedTokens.size} Receipts",
                "-",
                String.format(Locale.ENGLISH, "%.2f ql", totalWeight),
                "-",
                "-",
                "-",
                "-",
                "-",
                "-"
            )
            writer.append(summaryRow.joinToString(",") { escapeCsv(it) }).append("\n")
        }

        reportFile
    }

    /**
     * Generates a printable, styled PDF Document containing the mill weighment records
     * with Date, Name, Receipt Number, and Weight in a clean table.
     */
    suspend fun generatePdfReport(context: Context, dispatchedTokens: List<PurchyToken>): File = withContext(Dispatchers.IO) {
        val reportDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "PurchyFlow_Mill_Weighment_$timeStamp.pdf"
        val pdfFile = File(reportDir, fileName)

        val totalWeight = dispatchedTokens.sumOf { it.actualNettedWeightNumber ?: 0.0 }

        val document = PdfDocument()

        // Page setup: A4 (595 x 842 points)
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rowsPerPage = 22
        val totalPages = maxOf(1, (dispatchedTokens.size + rowsPerPage - 1) / rowsPerPage)

        var currentTokenIndex = 0

        while (currentTokenIndex < dispatchedTokens.size || (dispatchedTokens.isEmpty() && pageNumber == 1)) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 35f

            // 1. Header Banner
            paint.color = Color.rgb(27, 94, 32) // Emerald Green #1B5E20
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

            // Header Title
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("PurchyFlow - Sugar Mill Weighment Report", 30f, 40f, paint)

            paint.textSize = 10f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Official Sugar Mill Cane Weighment & Dispatch Records", 30f, 58f, paint)

            // Header Subtext: Generated date and page number
            paint.textSize = 9f
            paint.color = Color.rgb(200, 230, 201)
            canvas.drawText("Generated: ${DateUtil.getCurrentTimestampString()}", 30f, 75f, paint)
            canvas.drawText("Page $pageNumber of $totalPages", (pageWidth - 95).toFloat(), 75f, paint)

            y = 115f

            // 2. Summary KPI Box (Only on page 1)
            if (pageNumber == 1) {
                paint.color = Color.rgb(241, 245, 249) // Light grey card
                canvas.drawRoundRect(30f, y, (pageWidth - 30).toFloat(), y + 42f, 8f, 8f, paint)

                paint.color = Color.rgb(71, 85, 105)
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TOTAL DISPATCHED RECEIPTS:", 45f, y + 25f, paint)
                paint.color = Color.rgb(27, 94, 32)
                canvas.drawText("${dispatchedTokens.size}", 215f, y + 25f, paint)

                paint.color = Color.rgb(71, 85, 105)
                canvas.drawText("TOTAL NETTED WEIGHT:", 310f, y + 25f, paint)
                paint.color = Color.rgb(27, 94, 32)
                canvas.drawText(String.format(Locale.ENGLISH, "%.2f ql", totalWeight), 445f, y + 25f, paint)

                y += 58f
            }

            // 3. Table Column Headers
            val colDate = 30f
            val colName = 115f
            val colReceipt = 285f
            val colWeight = 390f
            val colPurchy = 485f
            val tableRight = (pageWidth - 30).toFloat()

            // Header background
            paint.color = Color.rgb(46, 125, 50) // #2E7D32
            canvas.drawRect(30f, y, tableRight, y + 24f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

            canvas.drawText("DATE", colDate + 6f, y + 16f, paint)
            canvas.drawText("GROWER NAME", colName + 6f, y + 16f, paint)
            canvas.drawText("RECEIPT NO", colReceipt + 6f, y + 16f, paint)
            canvas.drawText("WEIGHT (NET)", colWeight + 6f, y + 16f, paint)
            canvas.drawText("PURCHY NO", colPurchy + 6f, y + 16f, paint)

            y += 24f

            // 4. Data Rows
            var rowsOnThisPage = 0
            while (currentTokenIndex < dispatchedTokens.size && rowsOnThisPage < rowsPerPage) {
                val token = dispatchedTokens[currentTokenIndex]
                val rowHeight = 22f

                // Alternating zebra striping
                if (currentTokenIndex % 2 == 1) {
                    paint.color = Color.rgb(248, 250, 252)
                    canvas.drawRect(30f, y, tableRight, y + rowHeight, paint)
                }

                // Row bottom border
                paint.color = Color.rgb(226, 232, 240)
                canvas.drawLine(30f, y + rowHeight, tableRight, y + rowHeight, paint)

                // Row text
                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 8.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                // Date
                val dateText = token.dispatchDateStr ?: token.issueDateStr
                canvas.drawText(dateText, colDate + 6f, y + 14f, paint)

                // Name (truncated if needed)
                val safeName = if (token.growerName.length > 28) token.growerName.take(26) + "…" else token.growerName
                canvas.drawText(safeName, colName + 6f, y + 14f, paint)

                // Receipt No (without '#MILL' or 'MILL-')
                val receiptText = token.weightReceiptNo
                    ?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")
                    ?.trim() ?: "N/A"
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(13, 148, 136) // Teal
                canvas.drawText(receiptText, colReceipt + 6f, y + 14f, paint)

                // Net Weight
                paint.color = Color.rgb(27, 94, 32)
                val weightText = token.actualNettedWeight ?: "${token.allottedWeight} (Alt)"
                canvas.drawText(weightText, colWeight + 6f, y + 14f, paint)

                // Purchy No (without '#')
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                paint.color = Color.rgb(100, 116, 139)
                canvas.drawText(token.purchyNo, colPurchy + 6f, y + 14f, paint)

                y += rowHeight
                rowsOnThisPage++
                currentTokenIndex++
            }

            // If empty
            if (dispatchedTokens.isEmpty()) {
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 10f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                canvas.drawText("No mill-weighed dispatched receipts found.", 190f, y + 30f, paint)
                break
            }

            // Summary row if last page
            if (currentTokenIndex >= dispatchedTokens.size) {
                y += 4f
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRect(30f, y, tableRight, y + 24f, paint)

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 9.5f
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("TOTAL DISPATCHED", colDate + 6f, y + 16f, paint)

                paint.color = Color.rgb(27, 94, 32)
                canvas.drawText(String.format(Locale.ENGLISH, "%.2f ql", totalWeight), colWeight + 6f, y + 16f, paint)
            }

            // Footer note
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 8f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Generated by PurchyFlow Sugarcane Management System", 30f, (pageHeight - 25).toFloat(), paint)

            document.finishPage(page)
            pageNumber++
        }

        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        pdfFile
    }

    /**
     * Generates a genuine Microsoft Excel formatted spreadsheet (.xlsx)
     * adhering to OpenXML spreadsheet standards (ECMA-376) containing all linked fields.
     */
    suspend fun generateExcelXlsxReport(context: Context, tokens: List<PurchyToken>): File = withContext(Dispatchers.IO) {
        val reportDir = File(context.cacheDir, "reports").apply { if (!exists()) mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "PurchyFlow_Export_$timeStamp.xlsx"
        val reportFile = File(reportDir, fileName)

        val totalWeight = tokens.sumOf { it.actualNettedWeightNumber ?: it.allottedWeightNumber ?: 0.0 }

        // Build XML content for sheet1.xml
        val sheetBuilder = StringBuilder()
        sheetBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sheetBuilder.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
        sheetBuilder.append("  <sheetData>\n")

        // Title row
        sheetBuilder.append("    <row r=\"1\">\n")
        sheetBuilder.append("      <c r=\"A1\" t=\"inlineStr\"><is><t>PurchyFlow - Sugarcane Mill Weighment &amp; Dispatch Report</t></is></c>\n")
        sheetBuilder.append("    </row>\n")

        // Metadata row
        sheetBuilder.append("    <row r=\"2\">\n")
        sheetBuilder.append("      <c r=\"A2\" t=\"inlineStr\"><is><t>Generated: ${DateUtil.getCurrentTimestampString()}</t></is></c>\n")
        sheetBuilder.append("      <c r=\"B2\" t=\"inlineStr\"><is><t>Total Records: ${tokens.size}</t></is></c>\n")
        sheetBuilder.append("      <c r=\"C2\" t=\"inlineStr\"><is><t>Total Weight: ${String.format(Locale.ENGLISH, "%.2f ql", totalWeight)}</t></is></c>\n")
        sheetBuilder.append("    </row>\n")

        // Blank row 3
        sheetBuilder.append("    <row r=\"3\"/>\n")

        // Header row 4
        val columns = listOf(
            "A" to "Date",
            "B" to "Grower Name",
            "C" to "Grower Code",
            "D" to "Purchy No",
            "E" to "Mill Receipt No",
            "F" to "Actual Net Weight (ql)",
            "G" to "Allotted Weight (ql)",
            "H" to "Gross Weight",
            "I" to "Tare Weight",
            "J" to "Cane Type",
            "K" to "Society",
            "L" to "Centre",
            "M" to "Status",
            "N" to "UGC"
        )
        sheetBuilder.append("    <row r=\"4\">\n")
        for ((col, header) in columns) {
            sheetBuilder.append("      <c r=\"${col}4\" t=\"inlineStr\"><is><t>${escapeXml(header)}</t></is></c>\n")
        }
        sheetBuilder.append("    </row>\n")

        // Data rows 5..N
        var rowNum = 5
        for (token in tokens) {
            val dateStr = token.dispatchDateStr ?: token.issueDateStr
            val cleanReceipt = token.weightReceiptNo?.replace(Regex("^(?:#?MILL[-\\s]*|#)", RegexOption.IGNORE_CASE), "")?.trim() ?: "-"
            val netWeight = token.actualNettedWeightNumber ?: 0.0
            val allottedWeight = token.allottedWeightNumber ?: 0.0

            sheetBuilder.append("    <row r=\"$rowNum\">\n")
            sheetBuilder.append("      <c r=\"A$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(dateStr)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"B$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.growerName)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"C$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.growerCode)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"D$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.purchyNo)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"E$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(cleanReceipt)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"F$rowNum\" t=\"n\"><v>$netWeight</v></c>\n")
            sheetBuilder.append("      <c r=\"G$rowNum\" t=\"n\"><v>$allottedWeight</v></c>\n")
            sheetBuilder.append("      <c r=\"H$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.grossWeight ?: "-")}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"I$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.tareWeight ?: "-")}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"J$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.caneType)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"K$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.society)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"L$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.centre)}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"M$rowNum\" t=\"inlineStr\"><is><t>${token.status.name}</t></is></c>\n")
            sheetBuilder.append("      <c r=\"N$rowNum\" t=\"inlineStr\"><is><t>${escapeXml(token.ugc)}</t></is></c>\n")
            sheetBuilder.append("    </row>\n")
            rowNum++
        }

        // Summary Total Row
        sheetBuilder.append("    <row r=\"$rowNum\">\n")
        sheetBuilder.append("      <c r=\"A$rowNum\" t=\"inlineStr\"><is><t>TOTAL</t></is></c>\n")
        sheetBuilder.append("      <c r=\"B$rowNum\" t=\"inlineStr\"><is><t>${tokens.size} Records</t></is></c>\n")
        sheetBuilder.append("      <c r=\"F$rowNum\" t=\"n\"><v>$totalWeight</v></c>\n")
        sheetBuilder.append("    </row>\n")

        sheetBuilder.append("  </sheetData>\n")
        sheetBuilder.append("</worksheet>")

        // Write OpenXML Zip entries
        java.util.zip.ZipOutputStream(FileOutputStream(reportFile)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(java.util.zip.ZipEntry("[Content_Types].xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(java.util.zip.ZipEntry("_rels/.rels"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. xl/workbook.xml
            zos.putNextEntry(java.util.zip.ZipEntry("xl/workbook.xml"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="PurchyFlow Report" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. xl/_rels/workbook.xml.rels
            zos.putNextEntry(java.util.zip.ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. xl/worksheets/sheet1.xml
            zos.putNextEntry(java.util.zip.ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheetBuilder.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        reportFile
    }

    private fun escapeXml(str: String): String {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Launches the system Android Share Sheet to share the Excel or PDF report.
     */
    fun shareReportFile(context: Context, file: File, mimeType: String = "text/comma-separated-values") {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, "Sugar Mill Weighment Report - ${file.name}")
            putExtra(Intent.EXTRA_TEXT, "Attached is the latest Sugar Mill Cane Weighment & Dispatch Report from PurchyFlow.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val title = if (mimeType == "application/pdf") "Share PDF Report via" else "Share Excel Report via"
        val chooser = Intent.createChooser(intent, title)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Writes generated report directly into a user-selected SAF Uri (Storage Access Framework).
     * Avoids broad storage permissions while allowing the user to save to any directory.
     */
    suspend fun writeReportToUri(context: Context, sourceFile: File, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(value: String): String {
        var str = value.trim()
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            str = str.replace("\"", "\"\"")
            return "\"$str\""
        }
        return "\"$str\""
    }
}
