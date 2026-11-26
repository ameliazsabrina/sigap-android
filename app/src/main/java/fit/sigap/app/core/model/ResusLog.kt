package fit.sigap.app.core.model

import java.time.Duration
import java.time.Instant

enum class ResusEntryType {
    RHYTHM_CHECKED,
    CPR_CYCLE_STARTED,
    DRUG_GIVEN,
    SHOCK_DELIVERED,
    PROCEDURE_DONE,
    NOTE_ADDED,
    OUTCOME_RECORDED,
    CORRECTION,
}

enum class Rhythm(val label: String, val shockable: Boolean) {
    VF("VF", true),
    VT("VT", true),
    ASYSTOLE("Asystole", false),
    PEA("PEA", false),
}

enum class ResusDrug(val label: String) {
    EPINEPHRINE("Epinefrin"),
    AMIODARONE("Amiodarone"),
}

enum class ResusProcedure(val label: String) {
    ETT("ETT"),
    IV_IO_ACCESS("Akses IV/IO"),
    OTHER("Tindakan lain"),
}

enum class ResusOutcome(val label: String) {
    ROSC("ROSC"),
    MENINGGAL("Meninggal"),
}

sealed interface ResusAction {
    data class RhythmChecked(val rhythm: Rhythm, val startsCycle: Boolean) : ResusAction

    data object CprCycleStarted : ResusAction

    data class DrugGiven(val drug: ResusDrug) : ResusAction

    data object ShockDelivered : ResusAction

    data class ProcedureDone(val procedure: ResusProcedure, val text: String? = null) : ResusAction

    data class NoteAdded(val text: String) : ResusAction

    data class OutcomeRecorded(val outcome: ResusOutcome) : ResusAction

    data class Correction(val voidsEntryId: String, val reason: String? = null) : ResusAction

    data class Unrecognized(val rawType: String) : ResusAction

    val label: String
        get() = when (this) {
            is RhythmChecked -> "Irama ${rhythm.label}"
            CprCycleStarted -> "Siklus CPR dimulai"
            is DrugGiven -> drug.label
            ShockDelivered -> "Defibrilasi"
            is ProcedureDone -> if (procedure == ResusProcedure.OTHER) text.orEmpty().ifBlank { procedure.label } else procedure.label
            is NoteAdded -> text
            is OutcomeRecorded -> "Status akhir: ${outcome.label}"
            is Correction -> "Koreksi"
            is Unrecognized -> "Entri tidak dikenali ($rawType). Perbarui aplikasi."
        }

    val duplicateKey: String?
        get() = when (this) {
            is RhythmChecked -> "rhythm"
            CprCycleStarted -> "cycle"
            is DrugGiven -> "drug:${drug.name}"
            ShockDelivered -> "shock"
            is ProcedureDone -> if (procedure == ResusProcedure.OTHER) null else "procedure:${procedure.name}"
            is OutcomeRecorded -> "outcome"
            is NoteAdded, is Correction, is Unrecognized -> null
        }
}

enum class ResusDelivery { PENDING, SENT, REJECTED }

data class ResusEntry(
    val id: String,
    val incidentId: String,
    val seq: Long?,
    val action: ResusAction,
    val actorUserId: String,
    val actorName: String?,
    val tappedAt: Instant,
    val tappedAtSuspect: Boolean = false,
    val receivedAt: Instant? = null,
    val rejectedReason: String? = null,
) {
    val delivery: ResusDelivery
        get() = when {
            rejectedReason != null -> ResusDelivery.REJECTED
            seq == null -> ResusDelivery.PENDING
            else -> ResusDelivery.SENT
        }
}

data class ResusLogSnapshot(
    val entries: List<ResusEntry> = emptyList(),
    val lastSeq: Long? = null,
)

data class ResusSheetEntry(val entry: ResusEntry, val voided: Boolean)

data class ResusSheet(
    val entries: List<ResusSheetEntry>,
    val rhythm: Rhythm?,
    val rhythmSequence: List<Rhythm>,
    val cycleCount: Int,
    val cycleStartedAt: Instant?,
    val epinephrineCount: Int,
    val amiodaroneCount: Int,
    val shockCount: Int,
    val procedures: List<String>,
    val outcome: ResusOutcome?,
    val outcomeAt: Instant?,
) {
    fun totalElapsed(t0: Instant, now: Instant): Duration = Duration.between(t0, outcomeAt ?: now).coerceAtLeast(Duration.ZERO)

    fun cycleElapsed(now: Instant): Duration? =
        cycleStartedAt?.let { Duration.between(it, outcomeAt ?: now).coerceAtLeast(Duration.ZERO) }
}

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this

private val Chronological = compareBy<ResusEntry>({ it.tappedAt }, { it.seq ?: Long.MAX_VALUE }, { it.id })

fun List<ResusEntry>.toSheet(): ResusSheet {
    val usable = filter { it.delivery != ResusDelivery.REJECTED }
    val voided = voidedIds(usable)
    val ordered = sortedWith(Chronological)
    val live = ordered.filter { it.delivery != ResusDelivery.REJECTED && it.id !in voided }

    var rhythm: Rhythm? = null
    val rhythms = mutableListOf<Rhythm>()
    var cycles = 0
    var cycleStartedAt: Instant? = null
    var epinephrine = 0
    var amiodarone = 0
    var shocks = 0
    val procedures = mutableListOf<String>()
    var outcome: ResusOutcome? = null
    var outcomeAt: Instant? = null

    for (entry in live) {
        when (val action = entry.action) {
            is ResusAction.RhythmChecked -> {
                rhythm = action.rhythm
                if (rhythms.lastOrNull() != action.rhythm) rhythms += action.rhythm
                if (action.startsCycle) {
                    cycles++
                    cycleStartedAt = entry.tappedAt
                }
            }
            ResusAction.CprCycleStarted -> {
                cycles++
                cycleStartedAt = entry.tappedAt
            }
            is ResusAction.DrugGiven -> when (action.drug) {
                ResusDrug.EPINEPHRINE -> epinephrine++
                ResusDrug.AMIODARONE -> amiodarone++
            }
            ResusAction.ShockDelivered -> shocks++
            is ResusAction.ProcedureDone -> procedures += action.label
            is ResusAction.OutcomeRecorded -> {
                outcome = action.outcome
                outcomeAt = entry.tappedAt
            }
            is ResusAction.NoteAdded,
            is ResusAction.Correction,
            is ResusAction.Unrecognized,
            -> Unit
        }
    }

    return ResusSheet(
        entries = ordered.map { ResusSheetEntry(it, it.id in voided) },
        rhythm = rhythm,
        rhythmSequence = rhythms,
        cycleCount = cycles,
        cycleStartedAt = cycleStartedAt,
        epinephrineCount = epinephrine,
        amiodaroneCount = amiodarone,
        shockCount = shocks,
        procedures = procedures,
        outcome = outcome,
        outcomeAt = outcomeAt,
    )
}

private fun voidedIds(entries: List<ResusEntry>): Set<String> {
    val voidsByCorrection = entries.mapNotNull { entry ->
        (entry.action as? ResusAction.Correction)?.let { entry.id to it.voidsEntryId }
    }.toMap()
    val memo = mutableMapOf<String, Boolean>()
    fun isActive(correctionId: String, depth: Int = 0): Boolean = memo.getOrPut(correctionId) {
        if (depth > voidsByCorrection.size) return@getOrPut false
        val voidingMe = voidsByCorrection.filterValues { it == correctionId }.keys
        voidingMe.none { isActive(it, depth + 1) }
    }
    return voidsByCorrection.filterKeys { isActive(it) }.values.toSet()
}

fun List<ResusEntry>.recentSame(action: ResusAction, now: Instant, window: Duration): ResusEntry? {
    val key = action.duplicateKey ?: return null
    val voided = voidedIds(filter { it.delivery != ResusDelivery.REJECTED })
    return filter { it.delivery != ResusDelivery.REJECTED && it.id !in voided && it.action.duplicateKey == key }
        .filter { Duration.between(it.tappedAt, now).abs() <= window }
        .maxByOrNull { it.tappedAt }
}

fun ResusAction.conflictsWith(rhythm: Rhythm?): Boolean = rhythm != null && !rhythm.shockable && when (this) {
    ResusAction.ShockDelivered -> true
    is ResusAction.DrugGiven -> drug == ResusDrug.AMIODARONE
    else -> false
}

data class ResusSettings(
    val duplicateWarning: Duration = Duration.ofSeconds(15),
    val pollInterval: Duration = Duration.ofSeconds(2),
    val undoWindow: Duration = Duration.ofSeconds(5),
)
