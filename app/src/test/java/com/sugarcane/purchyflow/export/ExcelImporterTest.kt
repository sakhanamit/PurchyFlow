package com.sugarcane.purchyflow.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream

class ExcelImporterTest {

    @Test
    fun testParseCsvRows() {
        val csvContent = """
            Date,Name,Receipt Number,Weight,Purchy No,Gross Weight,Tare Weight
            18-NOV-2026,DEEPAK VERMA,8800101,47.77,700100200304,82.95,34.70
            19-NOV-2026,SUNITA DEVI,8800102,54.90,700100200302,88.20,33.30
            14-NOV-2026,RAMESH KUMAR,8800103,50.49,700100200305,85.10,34.61
        """.trimIndent()

        val rows = ExcelImporter.readCsvRows(ByteArrayInputStream(csvContent.toByteArray()))
        assertEquals(4, rows.size)

        val parsed = ExcelImporter.parseRows(rows)
        assertEquals(3, parsed.size)

        val first = parsed[0]
        assertEquals("DEEPAK VERMA", first.growerName)
        assertEquals("18-NOV-2026", first.dateStr)
        assertEquals("8800101", first.receiptNo)
        assertEquals(47.77, first.netWeight ?: 0.0, 0.001)

        val second = parsed[1]
        assertEquals("SUNITA DEVI", second.growerName)
        assertEquals("54.90", String.format(java.util.Locale.ENGLISH, "%.2f", second.netWeight))

        val third = parsed[2]
        assertEquals("RAMESH KUMAR", third.growerName)
        assertEquals("8800103", third.receiptNo)
    }

    @Test
    fun testParseHindiHeaders() {
        val csvHindi = """
            दिनांक,कृषक का नाम,रसीद नं,शुद्ध भार
            18-11-2026,दिनेश कुमार,8800104,48.50
        """.trimIndent()

        val rows = ExcelImporter.readCsvRows(ByteArrayInputStream(csvHindi.toByteArray()))
        val parsed = ExcelImporter.parseRows(rows)
        assertEquals(1, parsed.size)
        assertEquals("दिनेश कुमार", parsed[0].growerName)
        assertEquals("8800104", parsed[0].receiptNo)
        assertEquals(48.50, parsed[0].netWeight ?: 0.0, 0.01)
    }

    @Test
    fun testParseAppExportedCsvRoundTrip_preservesNameAndReceipt() {
        val exportedCsv = """
            Date,Grower Name,Grower Code,Purchy No,Receipt No,Net Weight (ql)
            21-11-2026,RAVI DEVI,1160/134,103301122198,8003347,44.45
            19-11-2026,GYANO DEVI,1160/045,102691127631,8002589,54.90
            TOTAL,2 Receipts,,,,99.35
        """.trimIndent()

        val rows = ExcelImporter.readCsvRows(ByteArrayInputStream(exportedCsv.toByteArray()))
        val parsed = ExcelImporter.parseRows(rows)
        assertEquals(2, parsed.size)

        assertEquals("RAVI DEVI", parsed[0].growerName)
        assertEquals("1160/134", parsed[0].growerCode)
        assertEquals("103301122198", parsed[0].purchyNo)
        assertEquals("8003347", parsed[0].receiptNo)
        assertEquals(44.45, parsed[0].netWeight ?: 0.0, 0.001)

        assertEquals("GYANO DEVI", parsed[1].growerName)
        assertEquals("8002589", parsed[1].receiptNo)
        assertEquals(54.90, parsed[1].netWeight ?: 0.0, 0.001)
    }
}
