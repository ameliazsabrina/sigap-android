package fit.sigap.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServerClockTest {
    @Test
    fun `selisih diukur dari titik tengah perjalanan permintaan`() {
        var phone = 10_000L
        val clock = ServerClock { phone }
        assertFalse(clock.synced)

        clock.observe(serverDateMillis = 70_000, sentAtMillis = 9_800, receivedAtMillis = 10_000)

        assertTrue(clock.synced)
        assertEquals(60_600L, clock.offsetMs)
        phone = 11_000
        assertEquals(Instant.ofEpochMilli(71_600), clock.now())
    }
}
