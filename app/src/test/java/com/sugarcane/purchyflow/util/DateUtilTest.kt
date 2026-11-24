package com.sugarcane.purchyflow.util

import com.sugarcane.purchyflow.data.model.UrgencyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateUtilTest {

    @Test
    fun testCaneSeasonCalculation_November2026() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 12, 0, 0)
        }
        val season = DateUtil.getCurrentCaneSeason(cal.timeInMillis)

        assertEquals("2026-27", season.seasonName)
        assertEquals("01/11/2026", season.startDateStr)
        assertEquals("30/04/2027", season.endDateStr)
    }

    @Test
    fun testCaneSeasonCalculation_January2027() {
        val cal = Calendar.getInstance().apply {
            set(2027, Calendar.JANUARY, 15, 12, 0, 0)
        }
        val season = DateUtil.getCurrentCaneSeason(cal.timeInMillis)

        assertEquals("2026-27", season.seasonName)
        assertEquals("01/11/2026", season.startDateStr)
        assertEquals("30/04/2027", season.endDateStr)
    }

    @Test
    fun testCaneSeason_StrictRejectionOfNovember2025() {
        // Active Season is 2026-27
        val activeReferenceTime = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 12, 0, 0)
        }.timeInMillis

        // Date from November 2025 (e.g. 19-NOV-25)
        val pastSeasonDate = Calendar.getInstance().apply {
            set(2025, Calendar.NOVEMBER, 19, 12, 0, 0)
        }.timeInMillis

        val isAccepted = DateUtil.isWithinCurrentCaneSeason(pastSeasonDate, activeReferenceTime)
        assertFalse("November 2025 must be REJECTED during 2026-27 season", isAccepted)
    }

    @Test
    fun testCaneSeason_AcceptsCurrentSeasonDates() {
        val activeReferenceTime = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 12, 0, 0)
        }.timeInMillis

        val currentSeasonNov = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
        }.timeInMillis
        assertTrue("Nov 18, 2026 must be accepted", DateUtil.isWithinCurrentCaneSeason(currentSeasonNov, activeReferenceTime))

        val currentSeasonApr = Calendar.getInstance().apply {
            set(2027, Calendar.APRIL, 20, 10, 0, 0)
        }.timeInMillis
        assertTrue("Apr 20, 2027 must be accepted", DateUtil.isWithinCurrentCaneSeason(currentSeasonApr, activeReferenceTime))
    }

    @Test
    fun testParseDate_SupportedFormats() {
        assertNotNull("18/11/2026", DateUtil.parseDate("18/11/2026"))
        assertNotNull("18/11/26", DateUtil.parseDate("18/11/26"))
        assertNotNull("19-NOV-26", DateUtil.parseDate("19-NOV-26"))
        assertNotNull("19-Nov-2026", DateUtil.parseDate("19-Nov-2026"))
        assertNotNull("19-NOV-25", DateUtil.parseDate("19-NOV-25"))
        assertNotNull("10/11/2026", DateUtil.parseDate("10/11/2026"))
    }

    @Test
    fun testUrgencyLevel_ExpiredTokenStaysVisibleAsExpired() {
        // Current device time: Nov 19, 2026 20:00:00
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 20, 0, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Token issued on Nov 10, 2026 (valid until Nov 12, 2026 23:59:59)
        val issueDate = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 10, 0, 0)
        }.time
        val (_, validUntilMs) = DateUtil.calculateValidUntil(issueDate)

        val urgency = DateUtil.getUrgencyLevel(validUntilMs, nowMs)
        assertEquals(UrgencyLevel.EXPIRED, urgency)
        assertEquals("Expired", DateUtil.getUrgencyBadgeText(validUntilMs, nowMs))
    }

    @Test
    fun testUrgencyLevel_OneDayLeft() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 19, 10, 0, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Token issued Nov 18 -> Valid until Nov 20 23:59:59 (~38 hours left from Nov 19 10:00)
        val issueDate = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 18, 10, 0, 0)
        }.time
        val (_, validUntilMs) = DateUtil.calculateValidUntil(issueDate)

        val urgency = DateUtil.getUrgencyLevel(validUntilMs, nowMs)
        assertEquals(UrgencyLevel.ONE_DAY_LEFT, urgency)
        assertTrue(DateUtil.getUrgencyBadgeText(validUntilMs, nowMs).startsWith("1 Day Left"))
    }
}
