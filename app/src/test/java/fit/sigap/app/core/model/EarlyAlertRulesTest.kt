package fit.sigap.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EarlyAlertRulesTest {
    private val base: Instant = Instant.parse("2026-11-05T08:00:00Z")

    private fun alert(
        id: String = "a",
        status: EarlyAlertStatus = EarlyAlertStatus.SENT,
        checkedAt: Instant? = null,
        createdAt: Instant = base,
    ) = EarlyAlert(
        id = id,
        status = status,
        patient = AlertPatient("p", "SIM Pasien A", "ICU Bed 01"),
        source = AlertSource.DETECTION,
        createdAt = createdAt,
        checkedAt = checkedAt,
    )

    @Test
    fun `unchecked means no check time and not finished`() {
        assertTrue(alert().isUnchecked)
        assertTrue(alert(status = EarlyAlertStatus.FAILED).isUnchecked)
        assertFalse(alert(status = EarlyAlertStatus.CHECKED, checkedAt = base).isUnchecked)
        assertFalse(alert(status = EarlyAlertStatus.CLOSED).isUnchecked)
        assertFalse(alert(status = EarlyAlertStatus.ESCALATED).isUnchecked)
    }

    @Test
    fun `open list puts unchecked first then oldest`() {
        val checked = alert("checked", EarlyAlertStatus.CHECKED, base, base.minusSeconds(900))
        val newer = alert("newer", createdAt = base)
        val older = alert("older", createdAt = base.minusSeconds(60))
        val closed = alert("closed", EarlyAlertStatus.CLOSED)
        assertEquals(
            listOf("older", "newer", "checked"),
            listOf(checked, newer, closed, older).openSorted().map { it.id },
        )
    }

    @Test
    fun `first waiting is the first unchecked alert in list order`() {
        val checked = alert("checked", EarlyAlertStatus.CHECKED, checkedAt = base)
        val waitingA = alert("waiting-a")
        val waitingB = alert("waiting-b")
        assertEquals("waiting-a", listOf(checked, waitingA, waitingB).firstWaiting()?.id)
    }

    @Test
    fun `first waiting is null when every alert is checked`() {
        val checked = alert("checked", EarlyAlertStatus.CHECKED, checkedAt = base)
        assertNull(listOf(checked).firstWaiting())
        assertNull(emptyList<EarlyAlert>().firstWaiting())
    }

    @Test
    fun `alert disappears once its patient has an active code blue`() {
        val inCodeBlue = alert("a-in-cb").copy(patient = AlertPatient("p-1", "SIM Pasien A", "ICU Bed 01"))
        val other = alert("a-other").copy(patient = AlertPatient("p-2", "SIM Pasien B", "ICU Bed 02"))
        val active = incident(patientId = "p-1", status = IncidentStatus.ACKNOWLEDGED)
        assertEquals(listOf("a-other"), listOf(inCodeBlue, other).outsideActiveCodeBlue(listOf(active)).map { it.id })
    }

    @Test
    fun `alert stays when its patient's code blue is already handled`() {
        val alert = alert("a").copy(patient = AlertPatient("p-1", "SIM Pasien A", "ICU Bed 01"))
        val awaitingDocs = incident(patientId = "p-1", status = IncidentStatus.DOCUMENTATION_PENDING)
        val completed = incident(patientId = "p-1", status = IncidentStatus.COMPLETED)
        assertEquals(listOf("a"), listOf(alert).outsideActiveCodeBlue(listOf(awaitingDocs, completed)).map { it.id })
    }

    @Test
    fun `filter keeps the incoming order`() {
        val first = alert("first").copy(patient = AlertPatient("p-2", "SIM Pasien B", "ICU Bed 02"))
        val second = alert("second").copy(patient = AlertPatient("p-3", "SIM Pasien C", "ICU Bed 03"))
        assertEquals(listOf("first", "second"), listOf(first, second).outsideActiveCodeBlue(emptyList()).map { it.id })
    }

    private fun incident(patientId: String, status: IncidentStatus) = Incident(
        id = "i-$patientId-$status",
        number = "CB-1",
        status = status,
        location = IncidentLocation("ICU Bed 01"),
        patient = IncidentPatient(patientId, "SIM Pasien", "SIM-PATIENT-001"),
        triggerSummary = "",
        createdAt = base,
        acknowledgedAt = if (status == IncidentStatus.ACKNOWLEDGED) base else null,
    )
}
