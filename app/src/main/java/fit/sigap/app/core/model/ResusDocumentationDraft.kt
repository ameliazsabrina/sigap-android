package fit.sigap.app.core.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HourMinute = DateTimeFormatter.ofPattern("HH:mm")

fun CodeBlueForm.draftFromResusLog(sheet: ResusSheet, zone: ZoneId): CodeBlueForm {
    if (this != CodeBlueForm()) return this
    val live = sheet.entries.filter { !it.voided && it.entry.delivery != ResusDelivery.REJECTED }.map { it.entry }
    if (live.isEmpty()) return this

    val rows = live.mapNotNull { it.toFormRow(zone) }.take(CodeBlueFormLimits.MAX_ROWS)
    return copy(
        rhythm = sheet.rhythmSequence.mapTo(linkedSetOf()) { it.name },
        rows = rows,
        cprStoppedAt = sheet.outcomeAt?.let { clock(it, zone) }.orEmpty(),
        outcome = sheet.outcome?.name,
    )
}

private fun ResusEntry.toFormRow(zone: ZoneId): CodeBlueFormRow? {
    val time = clock(tappedAt, zone)
    return when (val action = action) {
        is ResusAction.RhythmChecked -> CodeBlueFormRow(time = time, rhythm = action.rhythm.label)
        ResusAction.CprCycleStarted -> CodeBlueFormRow(time = time, note = "Siklus CPR dimulai")
        is ResusAction.DrugGiven -> CodeBlueFormRow(time = time, drugName = action.drug.label)
        ResusAction.ShockDelivered -> CodeBlueFormRow(time = time, note = "Defibrilasi")
        is ResusAction.ProcedureDone -> CodeBlueFormRow(time = time, note = action.label.cell())
        is ResusAction.NoteAdded -> CodeBlueFormRow(time = time, note = action.text.cell())
        is ResusAction.OutcomeRecorded, is ResusAction.Correction, is ResusAction.Unrecognized -> null
    }
}

private fun clock(at: Instant, zone: ZoneId): String = at.atZone(zone).format(HourMinute)

private fun String.cell(): String = take(CodeBlueFormLimits.MAX_CELL)
