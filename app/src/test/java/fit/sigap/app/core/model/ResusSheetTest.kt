package fit.sigap.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ResusSheetTest {
    private val t0 = Instant.parse("2026-11-23T08:00:00Z")
    private var clock = 0L

    private fun entry(
        action: ResusAction,
        id: String = "e${clock}",
        atSecond: Long = clock,
        seq: Long? = atSecond,
        rejected: String? = null,
        actor: String = "u1",
    ): ResusEntry {
        clock = atSecond + 1
        return ResusEntry(id, "i1", seq, action, actor, "Sari", t0.plusSeconds(atSecond), rejectedReason = rejected)
    }

    @Test
    fun `hitungan hanya dari entri yang tidak dibatalkan atau ditolak`() {
        val epi = entry(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), id = "epi1")
        val log = listOf(
            entry(ResusAction.CprCycleStarted),
            epi,
            entry(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)),
            entry(ResusAction.Correction("epi1")),
            entry(ResusAction.ShockDelivered),
            entry(ResusAction.ShockDelivered, rejected = "Incident sudah selesai"),
            entry(ResusAction.DrugGiven(ResusDrug.AMIODARONE), seq = null),
        )

        val sheet = log.toSheet()

        assertEquals(1, sheet.cycleCount)
        assertEquals(1, sheet.epinephrineCount)
        assertEquals(1, sheet.shockCount)
        assertEquals("a queued tap counts before the server has it", 1, sheet.amiodaroneCount)
        assertTrue(sheet.entries.first { it.entry.id == "epi1" }.voided)
        assertEquals("voided and rejected entries are still listed", log.size, sheet.entries.size)
    }

    @Test
    fun `membatalkan koreksi mengembalikan entri asli`() {
        val log = listOf(
            entry(ResusAction.ShockDelivered, id = "shock"),
            entry(ResusAction.Correction("shock"), id = "undo"),
            entry(ResusAction.Correction("undo"), id = "redo"),
        )

        val sheet = log.toSheet()

        assertEquals(1, sheet.shockCount)
        assertFalse(sheet.entries.first { it.entry.id == "shock" }.voided)
        assertTrue(sheet.entries.first { it.entry.id == "undo" }.voided)
    }

    @Test
    fun `urutan mengikuti jam ketukan, bukan jam terima`() {
        val late = entry(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), id = "queued", atSecond = 10, seq = 9)
        val early = entry(ResusAction.ShockDelivered, id = "first", atSecond = 5, seq = 8)
        val sheet = listOf(late, early, entry(ResusAction.CprCycleStarted, id = "later", atSecond = 7, seq = 5)).toSheet()

        assertEquals(listOf("first", "later", "queued"), sheet.entries.map { it.entry.id })
    }

    @Test
    fun `ganti irama di tengah siklus tidak memulai siklus baru`() {
        val sheet = listOf(
            entry(ResusAction.CprCycleStarted, atSecond = 0),
            entry(ResusAction.RhythmChecked(Rhythm.VF, startsCycle = false), atSecond = 30),
            entry(ResusAction.RhythmChecked(Rhythm.VF, startsCycle = false), atSecond = 40),
            entry(ResusAction.RhythmChecked(Rhythm.PEA, startsCycle = false), atSecond = 60),
        ).toSheet()

        assertEquals(1, sheet.cycleCount)
        assertEquals(Rhythm.PEA, sheet.rhythm)
        assertEquals(listOf(Rhythm.VF, Rhythm.PEA), sheet.rhythmSequence)
        assertEquals(Duration.ofSeconds(90), sheet.cycleElapsed(t0.plusSeconds(90)))
    }

    @Test
    fun `status akhir menghentikan timer dan dapat dikoreksi`() {
        val outcome = entry(ResusAction.OutcomeRecorded(ResusOutcome.ROSC), id = "out", atSecond = 600)
        val log = listOf(entry(ResusAction.CprCycleStarted, atSecond = 480), outcome)
        val later = t0.plusSeconds(900)

        val stopped = log.toSheet()
        assertEquals(Duration.ofSeconds(600), stopped.totalElapsed(t0, later))
        assertEquals(Duration.ofSeconds(120), stopped.cycleElapsed(later))

        val reopened = (log + entry(ResusAction.Correction("out"), atSecond = 620)).toSheet()
        assertNull(reopened.outcome)
        assertEquals(Duration.ofSeconds(900), reopened.totalElapsed(t0, later))
    }

    @Test
    fun `peringatan ganda hanya untuk tindakan sama dalam jendela`() {
        val log = listOf(
            entry(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), id = "epi", atSecond = 100, actor = "u2"),
            entry(ResusAction.ShockDelivered, id = "shock", atSecond = 110),
        )
        val window = Duration.ofSeconds(15)

        assertEquals("epi", log.recentSame(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), t0.plusSeconds(105), window)?.id)
        assertNull(log.recentSame(ResusAction.DrugGiven(ResusDrug.AMIODARONE), t0.plusSeconds(105), window))
        assertNull(log.recentSame(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), t0.plusSeconds(130), window))
        assertNull("free-text actions never warn", log.recentSame(ResusAction.NoteAdded("x"), t0.plusSeconds(111), window))
    }

    @Test
    fun `entri yang dibatalkan tidak memicu peringatan ganda`() {
        val log = listOf(
            entry(ResusAction.ShockDelivered, id = "shock", atSecond = 100),
            entry(ResusAction.Correction("shock"), atSecond = 101),
        )
        assertNull(log.recentSame(ResusAction.ShockDelivered, t0.plusSeconds(102), Duration.ofSeconds(15)))
    }

    @Test
    fun `defibrilasi dan amiodarone hanya bentrok dengan irama non shockable`() {
        assertTrue(ResusAction.ShockDelivered.conflictsWith(Rhythm.ASYSTOLE))
        assertTrue(ResusAction.DrugGiven(ResusDrug.AMIODARONE).conflictsWith(Rhythm.PEA))
        assertFalse(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE).conflictsWith(Rhythm.PEA))
        assertFalse(ResusAction.ShockDelivered.conflictsWith(Rhythm.VF))
        assertFalse(ResusAction.ShockDelivered.conflictsWith(null))
    }

    @Test
    fun `entri tidak dikenali tampil tetapi tidak dihitung`() {
        val sheet = listOf(entry(ResusAction.Unrecognized("VITALS_TAKEN"))).toSheet()
        assertEquals(1, sheet.entries.size)
        assertEquals(0, sheet.cycleCount + sheet.shockCount + sheet.epinephrineCount)
    }
}
