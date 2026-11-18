package fit.sigap.app.core.alarm

import fit.sigap.app.core.push.AlarmTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AlarmRegistryTest {
    private val registry = AlarmRegistry()

    private fun alarm(id: String, title: String = "CODE BLUE (SIMULASI) · $id") =
        ActiveAlarm(AlarmTarget.IncidentTarget(id), title, "Simulasi SIGAP.", Instant.parse("2026-11-12T08:00:00Z"))

    @Test
    fun `pengulangan untuk sasaran yang sama menggantikan entri lama dan menjadi terbaru`() {
        registry.ring(alarm("i1"))
        registry.ring(alarm("i2"))
        registry.ring(alarm("i1", title = "ulang"))

        assertEquals(listOf("i2", "i1"), registry.alarms.value.map { it.target.id })
        assertEquals("ulang", registry.latest?.title)
    }

    @Test
    fun `alarm hanya keluar lewat settle`() {
        registry.ring(alarm("i1"))
        registry.ring(ActiveAlarm(AlarmTarget.EarlyAlertTarget("a1"), "Early Alert", "", Instant.EPOCH))

        registry.settle(setOf("i1", "tidak-ada"))
        assertEquals(listOf("a1"), registry.alarms.value.map { it.target.id })

        registry.settleAll()
        assertTrue(registry.alarms.value.isEmpty())
        assertNull(registry.latest)
    }
}
