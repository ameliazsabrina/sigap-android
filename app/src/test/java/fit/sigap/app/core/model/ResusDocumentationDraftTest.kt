package fit.sigap.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ResusDocumentationDraftTest {
    private val wib = ZoneId.of("Asia/Jakarta")
    private val t0 = Instant.parse("2026-11-23T08:00:00Z")

    private fun entry(id: String, second: Long, action: ResusAction, rejected: String? = null) =
        ResusEntry(id, "i1", second, action, "u1", "Sari", t0.plusSeconds(second), rejectedReason = rejected)

    private val log = listOf(
        entry("r1", 0, ResusAction.RhythmChecked(Rhythm.VF, startsCycle = false)),
        entry("c1", 10, ResusAction.CprCycleStarted),
        entry("e1", 60, ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)),
        entry("e2", 70, ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)),
        entry("u1", 72, ResusAction.Correction("e2")),
        entry("s1", 120, ResusAction.ShockDelivered, rejected = "Log sudah ditutup"),
        entry("r2", 180, ResusAction.RhythmChecked(Rhythm.ASYSTOLE, startsCycle = false)),
        entry("o1", 300, ResusAction.OutcomeRecorded(ResusOutcome.ROSC)),
    )

    @Test
    fun `draf berisi ketukan yang berlaku, tanpa yang dibatalkan atau ditolak`() {
        val form = CodeBlueForm().draftFromResusLog(log.toSheet(), wib)

        assertEquals(setOf("VF", "ASYSTOLE"), form.rhythm)
        assertEquals("15:05", form.cprStoppedAt)
        assertEquals("ROSC", form.outcome)
        assertEquals(
            listOf(
                CodeBlueFormRow(time = "15:00", rhythm = "VF"),
                CodeBlueFormRow(time = "15:00", note = "Siklus CPR dimulai"),
                CodeBlueFormRow(time = "15:01", drugName = "Epinefrin"),
                CodeBlueFormRow(time = "15:03", rhythm = "Asystole"),
            ),
            form.rows,
        )
    }

    @Test
    fun `dosis, joule, dan isian manual tetap kosong`() {
        val form = CodeBlueForm().draftFromResusLog(log.toSheet(), wib)

        assertEquals(true, form.rows.all { it.dose.isEmpty() && it.joule.isEmpty() })
        assertEquals("", form.place)
        assertEquals("", form.primaryDiagnosis)
        assertEquals("", form.cprStopReason)
        assertEquals("", form.verifiedBy)
    }

    @Test
    fun `formulir yang sudah diisi tidak ditimpa`() {
        val typed = CodeBlueForm(place = "IGD")
        assertSame(typed, typed.draftFromResusLog(log.toSheet(), wib))
    }

    @Test
    fun `log kosong tidak mengubah formulir`() {
        assertEquals(CodeBlueForm(), CodeBlueForm().draftFromResusLog(emptyList<ResusEntry>().toSheet(), wib))
    }
}
