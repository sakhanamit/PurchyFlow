package com.sugarcane.purchyflow.parser

import com.sugarcane.purchyflow.security.CaneMessageFilter
import com.sugarcane.purchyflow.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TokenMessageParserTest {

    @Test
    fun testParseTokenMessage_standardTokenFormat() {
        val sampleToken = "SUNITA DEVI Code:5501/45 UGC:99887766550022 Type:E-RATOON Purchy:700100200302 FC:1/8 Wt:45ql W Date:18/11/2026 Soc:GreenValley Cnt:100 CANEDEPTT"

        assertEquals(CaneMessageType.PURCHY_TOKEN, TokenMessageParser.identifyMessageType(sampleToken))
        assertTrue(TokenMessageParser.isCaneMessage(sampleToken))

        val parsed = TokenMessageParser.parseTokenMessage(sampleToken)
        assertNotNull("Parsed token should not be null", parsed)

        assertEquals("SUNITA DEVI", parsed!!.growerName)
        assertEquals("5501/45", parsed.growerCode)
        assertEquals("99887766550022", parsed.ugc)
        assertEquals("E-RATOON", parsed.caneType)
        assertEquals("700100200302", parsed.purchyNo)
        assertEquals("1/8", parsed.fc)
        assertEquals("45ql", parsed.allottedWeight)
        assertEquals(45.0, parsed.allottedWeightNumber, 0.001)
        assertEquals("18/11/2026", parsed.issueDateStr)
        assertEquals("20/11/2026", parsed.validUntilStr)
        assertEquals("GreenValley", parsed.society)
        assertEquals("100", parsed.centre)
    }

    @Test
    fun testParseWeighmentReceipt_standardMillTaul() {
        val sampleTaul = "5501/45 SUNITA DEVI/RAMESH TAUL DINAAK 19-NOV-26 MILL PURCHY NO 8800102 WT 54.9 QTL TRIVENI"

        assertEquals(CaneMessageType.TAUL_RECEIPT, TokenMessageParser.identifyMessageType(sampleTaul))
        assertTrue(TokenMessageParser.isWeighmentReceipt(sampleTaul))

        val parsed = TokenMessageParser.parseWeighmentReceipt(sampleTaul)
        assertNotNull("Parsed receipt should not be null", parsed)

        assertEquals("8800102", parsed!!.purchyNo)
        assertEquals("5501/45", parsed.growerCode)
        assertEquals("SUNITA DEVI/RAMESH", parsed.growerName)
        assertEquals("54.9 QTL", parsed.actualNettedWeight)
        assertEquals(54.9, parsed.actualNettedWeightNumber, 0.001)
        assertEquals("19-NOV-26", parsed.dispatchDateStr)
        assertEquals("TRIVENI", parsed.societyOrMill)
        assertEquals("8800102", parsed.receiptNo)
    }

    @Test
    fun testParseTokenMessage_exactPromptFormat() {
        val sampleMessage = "RAMESH KUMAR Code:5501/12 UGC:99887766550011 Type:E-RATOON Purchy:700100200301 FC:1/2 Wt:45ql W Date:12/11/2026 Soc:GreenValley Cnt:100 CANEDEPTT."

        val parsed = TokenMessageParser.parseTokenMessage(sampleMessage)
        assertNotNull("Parsed token should not be null", parsed)

        assertEquals("RAMESH KUMAR", parsed!!.growerName)
        assertEquals("5501/12", parsed.growerCode)
        assertEquals("99887766550011", parsed.ugc)
        assertEquals("E-RATOON", parsed.caneType)
        assertEquals("700100200301", parsed.purchyNo)
        assertEquals("1/2", parsed.fc)
        assertEquals("45ql", parsed.allottedWeight)
        assertEquals(45.0, parsed.allottedWeightNumber, 0.001)
        assertEquals("12/11/2026", parsed.issueDateStr)
        assertEquals("14/11/2026", parsed.validUntilStr)
        assertEquals("GreenValley", parsed.society)
        assertEquals("100", parsed.centre)
    }

    @Test
    fun testParseWeighmentReceipt_legacyFormat() {
        val sampleReceipt = "WEIGHMENT RECEIPT Purchy:700100200301 Gross:62.50ql Tare:17.70ql Net:44.80ql Date:13/11/2026 Receipt:WR-55101 CANEDEPTT."

        assertTrue(TokenMessageParser.isWeighmentReceipt(sampleReceipt))

        val parsed = TokenMessageParser.parseWeighmentReceipt(sampleReceipt)
        assertNotNull("Parsed receipt should not be null", parsed)

        assertEquals("700100200301", parsed!!.purchyNo)
        assertEquals("44.80ql", parsed.actualNettedWeight)
        assertEquals(44.80, parsed.actualNettedWeightNumber, 0.001)
        assertEquals("WR-55101", parsed.receiptNo)
        assertEquals("13/11/2026", parsed.dispatchDateStr)
    }

    // =========================================================================
    // SECURITY & ZERO RETENTION UNIT TESTS (Pillar 1 Verification)
    // =========================================================================

    @Test
    fun testZeroRetention_rejectsBankOtpAndPersonalSms() {
        val bankOtp = "Your One Time Password (OTP) for SBI credit card txn of INR 4,500.00 is 849201. Do not share this OTP with anyone."
        val bankSender = "VM-SBIBNK"
        assertFalse(
            "Bank OTP must be strictly rejected by CaneMessageFilter",
            CaneMessageFilter.shouldProcess(bankSender, bankOtp)
        )

        val personalText = "Hey brother, please pick up the kids from school at 3pm today."
        val personalSender = "+919876543210"
        assertFalse(
            "Personal text message must be strictly rejected by CaneMessageFilter",
            CaneMessageFilter.shouldProcess(personalSender, personalText)
        )

        val promoText = "Special 50% discount on shoes! Click here https://bit.ly/offer to buy now. T&C apply."
        val promoSender = "AD-MYNTRA"
        assertFalse(
            "Promotional SMS must be strictly rejected by CaneMessageFilter",
            CaneMessageFilter.shouldProcess(promoSender, promoText)
        )

        val amazonPaymentSms = "To pay INR 3149.00 for your Amazon Order with Tracking # 220212205585 please click https://www.ezetap.com/cnp/remotepay/pay?token=YS3cywA4gjflpVpxELae"
        val amazonSender = "TX-AMAZON"
        assertFalse(
            "E-commerce payment link with token= query param must be rejected",
            CaneMessageFilter.shouldProcess(amazonSender, amazonPaymentSms)
        )
        assertEquals(
            "E-commerce payment link must not be classified as a Cane message",
            CaneMessageType.UNKNOWN,
            TokenMessageParser.identifyMessageType(amazonPaymentSms)
        )
    }

    @Test
    fun testWhitelistedSenders_acceptsCaneAndMillMessages() {
        val caneSms = "RAMESH KUMAR Code:5501/12 UGC:99887766550011 Type:E-RATOON Purchy:700100200301 FC:1/2 Wt:45ql W Date:12/11/2026 Soc:GreenValley Cnt:100 CANEDEPTT."
        val caneSender = "CANEDEPTT"
        assertTrue(
            "Cane department SMS must be approved",
            CaneMessageFilter.shouldProcess(caneSender, caneSms)
        )

        val taulSms = "5501/45 SUNITA DEVI TAUL DINAAK 19-NOV-26 MILL PURCHY NO 8800102 WT 54.9 QTL TRIVENI"
        val taulSender = "TRIVENI"
        assertTrue(
            "Sugar mill weighment SMS must be approved",
            CaneMessageFilter.shouldProcess(taulSender, taulSms)
        )
    }

    @Test
    fun testParseTokenMessage_unstructuredScrubbedNlp() {
        // Scrubbed text with missing colons or informal formatting
        val unstructuredMessage = "SURESH KUMAR 5501/88 UGC 99887766550033 Purchy 700100200303 45ql 15/11/2026 GREENVALLEY E-RATOON 1/4"

        val parsed = TokenMessageParser.parseTokenMessage(unstructuredMessage)
        assertNotNull("NLP parser should successfully extract entities from unstructured text", parsed)

        assertEquals("700100200303", parsed!!.purchyNo)
        assertEquals("5501/88", parsed.growerCode)
        assertEquals("99887766550033", parsed.ugc)
        assertEquals(45.0, parsed.allottedWeightNumber, 0.001)
        assertEquals("15/11/2026", parsed.issueDateStr)
        assertEquals("17/11/2026", parsed.validUntilStr)
        assertEquals("Deoband", parsed.society)
    }

    @Test
    fun testTwoDayValidityPeriod_strictCalculation() {
        val parsedDate = DateUtil.parseDate("12/11/2026")
        assertNotNull(parsedDate)

        val (validUntilStr, validUntilEpochMs) = DateUtil.calculateValidUntil(parsedDate!!)
        assertEquals("14/11/2026", validUntilStr)

        val cal = Calendar.getInstance().apply { timeInMillis = validUntilEpochMs }
        assertEquals(14, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
        assertEquals(59, cal.get(Calendar.SECOND))
    }

    @Test
    fun testMaskId_sanitizesSensitiveIds() {
        assertEquals("***0302", CaneMessageFilter.maskId("700100200302"))
        assertEquals("***/045", CaneMessageFilter.maskId("5501/045"))
        assertEquals("N/A", CaneMessageFilter.maskId(null))
    }

    @Test
    fun testReDoSProtection_longMalformedInputCompletesQuickly() {
        // Warm up regex/date/filter classes (including NLP fallback) before timing
        val warmupStr = "warmup Code:0/0 CANEDEPTT"
        TokenMessageParser.identifyMessageType(warmupStr)
        TokenMessageParser.parseTokenMessage(warmupStr)
        TokenMessageParser.parseWeighmentReceipt(warmupStr)
        CaneMessageFilter.shouldProcess("WARMUP", warmupStr)

        val malformed = "A".repeat(10_000) + " Code:" + "0/".repeat(2000) + " CANEDEPTT"
        val startNs = System.nanoTime()
        TokenMessageParser.identifyMessageType(malformed)
        TokenMessageParser.parseTokenMessage(malformed)
        TokenMessageParser.parseWeighmentReceipt(malformed)
        CaneMessageFilter.shouldProcess("CANEDEPTT", malformed)
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        assertTrue("Parser took ${elapsedMs}ms on long malformed input (limit 50ms)", elapsedMs < 50)
    }
}
