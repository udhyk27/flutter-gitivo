package com.ydh.salvio.util

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormatTest {

    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-01T12:00:00Z")

    private fun ago(amount: Long, unit: ChronoUnit): String = now.minus(amount, unit).toString()

    // ---- formatAbsolute ----

    @Test
    fun formatAbsolute_formatsWithPatternAndZone() {
        assertEquals(
            "2026-01-15 09:30",
            formatAbsolute("2026-01-15T09:30:00Z", "yyyy-MM-dd HH:mm", utc)
        )
    }

    @Test
    fun formatAbsolute_fallsBackToFirst10CharsOnParseFailure() {
        assertEquals("not-a-date", formatAbsolute("not-a-date-xyz", "yyyy-MM-dd", utc))
    }

    // ---- relativeTime ----

    @Test
    fun relativeTime_minutes() {
        assertEquals("30분 전", relativeTime(ago(30, ChronoUnit.MINUTES), now, utc))
    }

    @Test
    fun relativeTime_hours() {
        assertEquals("3시간 전", relativeTime(ago(3, ChronoUnit.HOURS), now, utc))
    }

    @Test
    fun relativeTime_overADayShowsMonthDay() {
        assertEquals("05/30", relativeTime(ago(2, ChronoUnit.DAYS), now, utc))
    }

    @Test
    fun relativeTime_parseFailureFallback() {
        assertEquals("bad-string", relativeTime("bad-string!!", now, utc))
    }

    // ---- relativeDays ----

    @Test
    fun relativeDays_today() {
        assertEquals("오늘", relativeDays(ago(0, ChronoUnit.DAYS), now))
    }

    @Test
    fun relativeDays_yesterday() {
        assertEquals("어제", relativeDays(ago(1, ChronoUnit.DAYS), now))
    }

    @Test
    fun relativeDays_daysAgo() {
        assertEquals("3일 전", relativeDays(ago(3, ChronoUnit.DAYS), now))
    }

    @Test
    fun relativeDays_weeksAgo() {
        assertEquals("1주 전", relativeDays(ago(10, ChronoUnit.DAYS), now))
    }

    @Test
    fun relativeDays_monthsAgo() {
        assertEquals("2개월 전", relativeDays(ago(60, ChronoUnit.DAYS), now))
    }

    @Test
    fun relativeDays_parseFailureFallback() {
        assertEquals("2026-06-01", relativeDays("2026-06-01", now)) // 'Z' 없어 파싱 실패 → take(10)
    }

    // ---- isOlderThanDays ----

    @Test
    fun isOlderThanDays_trueWhenOlder() {
        assertTrue(isOlderThanDays(ago(40, ChronoUnit.DAYS), 30, now))
    }

    @Test
    fun isOlderThanDays_falseWhenNewer() {
        assertFalse(isOlderThanDays(ago(10, ChronoUnit.DAYS), 30, now))
    }

    @Test
    fun isOlderThanDays_falseOnParseFailure() {
        assertFalse(isOlderThanDays("nope", 30, now))
    }
}
