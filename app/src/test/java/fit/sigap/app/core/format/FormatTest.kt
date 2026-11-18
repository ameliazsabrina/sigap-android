package fit.sigap.app.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FormatTest {
    private val now: Instant = Instant.parse("2026-11-05T08:00:00Z")

    private fun relative(secondsAgo: Long) = formatRelative(now.minusSeconds(secondsAgo), now, ZoneOffset.UTC)

    @Test
    fun `relative time matches the web wording`() {
        assertEquals("baru saja", relative(9))
        assertEquals("45 detik lalu", relative(45))
        assertEquals("4 menit lalu", relative(240))
        assertEquals("2 jam lalu", relative(7200))
        assertEquals("04 Nov, 08.00", relative(86_400))
    }

    @Test
    fun `missing duration reads as not recorded, never zero`() {
        assertEquals("tidak tercatat", formatDuration(null, now))
        assertEquals("tidak tercatat", formatDuration(now, null))
        assertEquals("02:30", formatDuration(now.minusSeconds(150), now))
        assertEquals("1 j 5 m", formatDuration(now.minusSeconds(3900), now))
    }

    @Test
    fun `vital values match the web formatVital without rounding`() {
        assertEquals("97", formatVital(97.0))
        assertEquals("97.5", formatVital(97.5))
        assertEquals("36.25", formatVital(36.25))
        assertEquals("100", formatVital(100.0))
    }

    @Test
    fun `clock uses the Indonesian separator`() {
        assertEquals("08.00.00", formatTime(now, ZoneOffset.UTC))
        assertEquals("08.00", formatClock(now, ZoneOffset.UTC))
    }
}
