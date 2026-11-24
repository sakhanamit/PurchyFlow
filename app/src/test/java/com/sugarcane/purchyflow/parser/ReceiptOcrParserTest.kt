package com.sugarcane.purchyflow.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReceiptOcrParserTest {

    @Test
    fun testParseTriveniReceipt() {
        val ocrSample = """
            Triveni
            त्रिवेणी इंजीनियरिंग एण्ड इन्डस्ट्रीज लि०
            शुगर यूनिट-देवबंद, जिला-आदर्श नगर
            कृषक प्रति
            सत्र 2026-2027 पर्ची कोड विशि 18-11-2026
            मिल पर्ची नं. 8800101
            समिति पर्ची नं. 700100200304
            ग्रास स्लिप नं. 4400101
            टोकन नं. 2200101
            गन्ना जाति Early
            कृषक कोड 5501 /34
            UGC 99887766550044
            ग्राम RAMPUR
            कृषक श्री DEEPAK VERMA
            पुत्र श्री RAMESH CHANDRA
            विवरण भार समय शिफ्ट ओप.
            वजन भरी गाड़ी 82.95 810
            वजन खाली गाड़ी 34.70 820
            गन्ना मय जूना 48.25
            जूना 0.48
            शुद्ध भार गन्ना 47.77
            AMOUNT (Rs.) 0.00
            928
            तौल लिपिक
        """.trimIndent()

        val data = ReceiptOcrParser.parse(ocrSample)

        assertEquals("8800101", data.millPurchyNo)
        assertEquals("700100200304", data.societyPurchyNo)
        assertEquals("5501/34", data.growerCode)
        assertEquals("DEEPAK VERMA", data.growerName)
        assertEquals("99887766550044", data.ugc)
        assertEquals("Early", data.caneType)
        assertEquals(82.95, data.grossWeight!!, 0.01)
        assertEquals(34.70, data.tareWeight!!, 0.01)
        assertEquals(47.77, data.netWeight!!, 0.01)
        assertEquals("18-11-2026", data.dateStr)
        assertEquals("Triveni Deoband", data.millOrSociety)
    }
}
