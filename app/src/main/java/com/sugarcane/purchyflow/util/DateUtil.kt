package com.sugarcane.purchyflow.util

import com.sugarcane.purchyflow.data.model.UrgencyLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CaneSeason(
    val startEpochMs: Long,
    val endEpochMs: Long,
    val seasonName: String,
    val startDateStr: String,
    val endDateStr: String
)

object DateUtil {

    private const val DATE_PATTERN = "dd/MM/yyyy"
    private const val DISPLAY_PATTERN = "dd MMM yyyy"
    private const val TIMESTAMP_PATTERN = "dd/MM/yyyy HH:mm"

    private val SUPPORTED_PATTERNS = listOf(
        "dd/MM/yyyy",
        "dd/MM/yy",
        "dd-MM-yyyy",
        "dd-MM-yy",
        "dd.MM.yyyy",
        "dd.MM.yy",
        "dd-MMM-yy",
        "dd-MMM-yyyy",
        "dd/MMM/yy",
        "dd/MMM/yyyy",
        "dd MMM yyyy",
        "dd MMM yy",
        "d/M/yyyy",
        "d/M/yy",
        "d-M-yyyy",
        "d-M-yy"
    )

    private fun getFormatter(pattern: String): SimpleDateFormat {
        return SimpleDateFormat(pattern, Locale.ENGLISH).apply {
            isLenient = false
        }
    }

    /**
     * Cane Season Window:
     * Sugarcane crushing year runs from November 1st to April 30th of the following year (~6 months).
     * Calculates the exact start and end boundaries for the current active cane season.
     */
    fun getCurrentCaneSeason(referenceTimeMs: Long = System.currentTimeMillis()): CaneSeason {
        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) // Calendar.NOVEMBER = 10, Calendar.APRIL = 3

        val startYear = if (month >= Calendar.NOVEMBER) year else year - 1
        val endYear = startYear + 1

        val startCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, startYear)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, endYear)
            set(Calendar.MONTH, Calendar.APRIL)
            set(Calendar.DAY_OF_MONTH, 30)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val seasonName = "$startYear-${(endYear % 100).toString().padStart(2, '0')}"
        val formatter = getFormatter(DATE_PATTERN)
        return CaneSeason(
            startEpochMs = startCal.timeInMillis,
            endEpochMs = endCal.timeInMillis,
            seasonName = seasonName,
            startDateStr = formatter.format(startCal.time),
            endDateStr = formatter.format(endCal.time)
        )
    }

    /**
     * Checks if a timestamp falls within the current cane season.
     */
    fun isWithinCurrentCaneSeason(timestampMs: Long, referenceTimeMs: Long = System.currentTimeMillis()): Boolean {
        val season = getCurrentCaneSeason(referenceTimeMs)
        return timestampMs in season.startEpochMs..season.endEpochMs
    }

    /**
     * Parses standard sugarcane department date formats (e.g., "12/11/2026", "18/11/26", "19-NOV-26", "19-NOV-2026")
     */
    fun parseDate(dateStr: String?): Date? {
        if (dateStr.isNullOrBlank()) return null
        val cleaned = dateStr.trim().uppercase(Locale.ENGLISH)
        for (pattern in SUPPORTED_PATTERNS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }
                val parsed = sdf.parse(cleaned)
                if (parsed != null) return parsed
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Strict 2-Day Validity Calculation:
     * Valid Until = W Date + 2 days, set to end-of-day (23:59:59.999).
     * Example: W Date 12/11/2026 -> Valid until 14/11/2026 23:59:59
     */
    fun calculateValidUntil(issueDate: Date): Pair<String, Long> {
        val cal = Calendar.getInstance().apply {
            time = issueDate
            add(Calendar.DAY_OF_MONTH, 2)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val validDate = cal.time
        val formattedStr = getFormatter(DATE_PATTERN).format(validDate)
        return Pair(formattedStr, validDate.time)
    }

    /**
     * Determines urgency category for color-coding cards:
     * - EXPIRING_TODAY (Red): < 24 hours left or today is the expiry day
     * - ONE_DAY_LEFT (Amber): Between 24 and 48 hours left
     * - TWO_DAYS_LEFT (Green): More than 48 hours left
     * - EXPIRED (Grey): Past validUntil timestamp
     */
    fun getUrgencyLevel(validUntilEpochMs: Long, currentTimeMs: Long = System.currentTimeMillis()): UrgencyLevel {
        val diffMs = validUntilEpochMs - currentTimeMs
        if (diffMs <= 0) {
            return UrgencyLevel.EXPIRED
        }

        val hoursRemaining = TimeUnit.MILLISECONDS.toHours(diffMs)
        return when {
            hoursRemaining < 24 -> UrgencyLevel.EXPIRING_TODAY
            hoursRemaining in 24..48 -> UrgencyLevel.ONE_DAY_LEFT
            else -> UrgencyLevel.TWO_DAYS_LEFT
        }
    }

    /**
     * Human-friendly countdown text (e.g., "Expires Today (6h left)", "1 Day Left", "2 Days Left", "Expired")
     */
    fun getUrgencyBadgeText(validUntilEpochMs: Long, currentTimeMs: Long = System.currentTimeMillis()): String {
        val diffMs = validUntilEpochMs - currentTimeMs
        if (diffMs <= 0) {
            return "Expired"
        }

        val totalHours = TimeUnit.MILLISECONDS.toHours(diffMs)
        val days = totalHours / 24
        val hours = totalHours % 24

        return when {
            days == 0L -> "Expiring Today (${hours}h left)"
            days == 1L -> "1 Day Left (${hours}h)"
            else -> "$days Days Left"
        }
    }

    fun formatDate(epochMs: Long): String {
        return getFormatter(DATE_PATTERN).format(Date(epochMs))
    }

    fun formatDateForDisplay(dateStr: String?): String {
        val date = parseDate(dateStr) ?: return dateStr.orEmpty()
        return try {
            getFormatter(DISPLAY_PATTERN).format(date)
        } catch (e: Exception) {
            dateStr.orEmpty()
        }
    }

    fun getCurrentTimestampString(): String {
        return getFormatter(TIMESTAMP_PATTERN).format(Date())
    }

    /**
     * Checks if the given epoch timestamp is within today's calendar day.
     */
    fun isToday(epochMs: Long): Boolean {
        if (epochMs <= 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = epochMs }
        val cal2 = Calendar.getInstance()
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
