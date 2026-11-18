package fit.sigap.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class IncidentRulesTest {
    private val now: Instant = Instant.parse("2026-11-05T08:00:00Z")

    private fun incident(
        status: IncidentStatus = IncidentStatus.NOTIFICATION_SENT,
        createdSecondsAgo: Long = 60,
        acknowledgedSecondsAgo: Long? = null,
        byMe: Boolean = false,
        arrived: Boolean = false,
    ) = Incident(
        id = "i",
        number = "CB-1",
        status = status,
        location = IncidentLocation("ICU Bed 01"),
        patient = IncidentPatient("p", "SIM Pasien A", "SIM-PATIENT-001"),
        triggerSummary = "",
        createdAt = now.minusSeconds(createdSecondsAgo),
        acknowledgedAt = acknowledgedSecondsAgo?.let(now::minusSeconds),
        acknowledgedByMe = byMe,
        arrivedAt = if (arrived) now else null,
    )

    @Test
    fun `location line skips missing parts`() {
        assertEquals(
            "Lantai 3 · Bed 01",
            IncidentLocation("ICU", floor = "3", bed = "01").detailLine,
        )
    }

    @Test
    fun `active list keeps unacknowledged first then oldest`() {
        val acknowledged = incident(createdSecondsAgo = 500, acknowledgedSecondsAgo = 10, status = IncidentStatus.ACKNOWLEDGED)
        val newer = incident(createdSecondsAgo = 10)
        val older = incident(createdSecondsAgo = 100)
        assertEquals(listOf(older, newer, acknowledged), listOf(acknowledged, newer, older).activeSorted())
    }

    @Test
    fun `handled incidents leave the active list`() {
        val waiting = incident(status = IncidentStatus.DOCUMENTATION_PENDING)
        assertTrue(waiting.awaitsDocumentation)
        assertTrue(listOf(waiting, incident(status = IncidentStatus.COMPLETED)).activeSorted().isEmpty())
    }

    @Test
    fun `only the acknowledger is overdue, and only past the limit`() {
        val limit = Duration.ofMinutes(2)
        val mine = incident(IncidentStatus.ACKNOWLEDGED, acknowledgedSecondsAgo = 121, byMe = true)
        assertTrue(mine.isArrivalOverdue(now, limit))
        assertFalse(mine.copy(acknowledgedByMe = false).isArrivalOverdue(now, limit))
        assertFalse(incident(IncidentStatus.ACKNOWLEDGED, acknowledgedSecondsAgo = 60, byMe = true).isArrivalOverdue(now, limit))
        assertFalse(incident(IncidentStatus.ACKNOWLEDGED, acknowledgedSecondsAgo = 121, byMe = true, arrived = true).isArrivalOverdue(now, limit))
        assertFalse(mine.isArrivalOverdue(now, null))
    }

    @Test
    fun `team status line follows the furthest recorded step`() {
        assertEquals("Belum ada yang menuju lokasi", incident().teamStatusLine)
        assertEquals(
            "Sudah ada yang menuju lokasi, belum tiba",
            incident(acknowledgedSecondsAgo = 5).teamStatusLine,
        )
        assertEquals(
            "Tim sudah tiba di lokasi",
            incident(acknowledgedSecondsAgo = 5, arrived = true).teamStatusLine,
        )
    }

    @Test
    fun `kata Gedung tidak berulang pada baris lokasi`() {
        assertEquals("Gedung A · Lantai 1", IncidentLocation("x", building = "Gedung A", floor = "1").detailLine)
        assertEquals("Gedung B", IncidentLocation("x", building = "B").detailLine)
    }
}
