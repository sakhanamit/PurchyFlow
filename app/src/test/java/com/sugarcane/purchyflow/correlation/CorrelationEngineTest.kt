package com.sugarcane.purchyflow.correlation

import com.sugarcane.purchyflow.data.correlation.CorrelationEngine
import com.sugarcane.purchyflow.data.model.PurchyToken
import com.sugarcane.purchyflow.data.model.TokenStatus
import com.sugarcane.purchyflow.parser.ParsedWeighmentReceipt
import com.sugarcane.purchyflow.parser.TokenMessageParser
import com.sugarcane.purchyflow.util.DateUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CorrelationEngineTest {

    @Test
    fun testNormalizeGrowerCode_handlesLeadingZerosAndPunctuation() {
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160/134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160/0134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("01160/00134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160-134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160 - 134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160 / 134"))
        assertEquals("1160/134", CorrelationEngine.normalizeGrowerCode("1160_134"))
        assertEquals("5501/45", CorrelationEngine.normalizeGrowerCode("05501/0045"))
        assertEquals("5501/45", CorrelationEngine.normalizeGrowerCode("Code: 5501/045"))
    }

    @Test
    fun testMatchesGrowerCode_matchesAcrossFormats() {
        assertTrue(CorrelationEngine.matchesGrowerCode("1160/134", "1160/0134"))
        assertTrue(CorrelationEngine.matchesGrowerCode("01160/00134", "1160-134"))
        assertTrue(CorrelationEngine.matchesGrowerCode("5501/45", "05501/0045"))
        assertFalse(CorrelationEngine.matchesGrowerCode("1160/134", "1160/135"))
        assertFalse(CorrelationEngine.matchesGrowerCode("1160/134", "1161/134"))
    }

    @Test
    fun testMatchesUgc_exactAndSuffix() {
        assertTrue(CorrelationEngine.matchesUgc("11211017410041", "11211017410041"))
        assertTrue(CorrelationEngine.matchesUgc("11211017410041", "1017410041")) // Suffix match
        assertFalse(CorrelationEngine.matchesUgc("11211017410041", "99887766550022"))
    }

    @Test
    fun testDateAlignment_withinWindowAndOutsideWindow() {
        val issueDate = DateUtil.parseDate("19/11/2026")!!.time
        val validUntil = DateUtil.parseDate("21/11/2026")!!.time

        // Same day weighment
        assertTrue(CorrelationEngine.isDateAligned(issueDate, validUntil, issueDate))

        // Within 2-day validity period (21-Nov)
        val weighmentDate21 = DateUtil.parseDate("21/11/2026")!!.time
        assertTrue(CorrelationEngine.isDateAligned(issueDate, validUntil, weighmentDate21))

        // Mill queue delay: 2 days past validity (23-Nov)
        val weighmentDate23 = DateUtil.parseDate("23/11/2026")!!.time
        assertTrue(CorrelationEngine.isDateAligned(issueDate, validUntil, weighmentDate23))

        // 1 month later (19-Dec) -> Must be rejected!
        val lateDate = DateUtil.parseDate("19/12/2026")!!.time
        assertFalse(CorrelationEngine.isDateAligned(issueDate, validUntil, lateDate))
    }

    @Test
    fun testFuzzyNameScore_handlesMillPatronymicSlashes() {
        val score = CorrelationEngine.fuzzyNameScore("RAVI DEVI", "RAVI DEVI/ARVIND")
        assertTrue("Score should be high for patronymic name addition", score >= 0.90)

        val score2 = CorrelationEngine.fuzzyNameScore("SUNITA DEVI", "SUNITA DEVI/RAMESH")
        assertTrue(score2 >= 0.90)

        val score3 = CorrelationEngine.fuzzyNameScore("RAMESH KUMAR", "DINESH SHARMA")
        assertTrue(score3 < 0.3)
    }

    @Test
    fun testFuzzyCorrelation_linksMillWeighmentToActiveTokenWithDifferentPurchyNo() {
        // Scenario from user's device:
        // Cane Department Token SMS:
        val activeToken = PurchyToken(
            purchyNo = "103301122198",
            growerName = "RAVI DEVI",
            growerCode = "1160/134",
            ugc = "11211017410041",
            caneType = "E-RATOON",
            fc = "1/10",
            allottedWeight = "45ql",
            allottedWeightNumber = 45.0,
            issueDateStr = "19/11/2026",
            issueDateEpochMs = DateUtil.parseDate("19/11/2026")!!.time,
            validUntilStr = "21/11/2026",
            validUntilEpochMs = DateUtil.parseDate("21/11/2026")!!.time,
            society = "Deoband",
            centre = "100",
            status = TokenStatus.ACTIVE
        )

        // Incoming Mill Weighment SMS:
        // Notice: Mill Purchy No is 8003347 (weighbridge receipt no), Grower Code is 1160/134,
        // Name has patronymic "/ARVIND", and Date is 21-NOV-26.
        val millSms = "1160/134 RAVI DEVI/ARVIND TAUL DINAAK 21-NOV-26 MILL PURCHY NO 8003347 WT 44.45 QTL TRIVENI"
        val parsedReceipt = TokenMessageParser.parseWeighmentReceipt(millSms)
        assertNotNull("Mill weighment receipt must be parsed", parsedReceipt)

        val dispatchEpoch = DateUtil.parseDate(parsedReceipt!!.dispatchDateStr)!!.time

        // Run Correlation Engine
        val result = CorrelationEngine.findBestMatchingToken(
            receipt = parsedReceipt,
            candidateTokens = listOf(activeToken),
            dispatchEpochMs = dispatchEpoch
        )

        assertTrue("Correlation engine must successfully match record", result.isMatch)
        assertNotNull("Matched token must not be null", result.matchedToken)
        assertEquals("Must match Cane Token 103301122198", "103301122198", result.matchedToken!!.purchyNo)
        assertTrue("Confidence score must be >= 85%", result.confidenceScore >= 85.0)
        assertTrue("Match reason must mention Grower Code or UGC", result.matchReason.contains("Grower Code") || result.matchReason.contains("UGC"))
    }

    @Test
    fun testCorrelationEngine_rejectsUnmatchedGrowerCode() {
        val activeToken = PurchyToken(
            purchyNo = "103301122198",
            growerName = "RAVI DEVI",
            growerCode = "1160/134",
            ugc = "11211017410041",
            caneType = "E-RATOON",
            fc = "1/10",
            allottedWeight = "45ql",
            allottedWeightNumber = 45.0,
            issueDateStr = "19/11/2026",
            issueDateEpochMs = DateUtil.parseDate("19/11/2026")!!.time,
            validUntilStr = "21/11/2026",
            validUntilEpochMs = DateUtil.parseDate("21/11/2026")!!.time,
            society = "Deoband",
            centre = "100",
            status = TokenStatus.ACTIVE
        )

        // Weighment from different grower code 5501/99
        val differentGrowerSms = "5501/99 DINESH KUMAR TAUL DINAAK 21-NOV-26 MILL PURCHY NO 8003347 WT 44.45 QTL TRIVENI"
        val parsed = TokenMessageParser.parseWeighmentReceipt(differentGrowerSms)
        val dispatchEpoch = DateUtil.parseDate(parsed!!.dispatchDateStr)!!.time

        val result = CorrelationEngine.findBestMatchingToken(
            receipt = parsed,
            candidateTokens = listOf(activeToken),
            dispatchEpochMs = dispatchEpoch
        )

        assertFalse("Different grower code must not match", result.isMatch)
    }
}
