package fit.sigap.app.feature.resus

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusDrug
import fit.sigap.app.core.model.ResusEntry
import fit.sigap.app.core.model.ResusLogSnapshot
import fit.sigap.app.core.model.ResusOutcome
import fit.sigap.app.core.model.ResusProcedure
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews
import fit.sigap.app.preview.minutesAgo

private const val ME = "u-fajar"

private fun entry(id: String, minutes: Long, action: ResusAction, seq: Long? = id.hashCode().toLong(), by: String = ME) =
    ResusEntry(id, "i-1", seq, action, by, if (by == ME) PreviewData.RESPONDER else "Ns. Rina", minutesAgo(minutes))

private val Entries = listOf(
    entry("1", 9, ResusAction.CprCycleStarted),
    entry("2", 8, ResusAction.RhythmChecked(Rhythm.VF, startsCycle = false)),
    entry("3", 8, ResusAction.ShockDelivered),
    entry("4", 7, ResusAction.ProcedureDone(ResusProcedure.IV_IO_ACCESS), by = "u-rina"),
    entry("5", 6, ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)),
    entry("6", 6, ResusAction.DrugGiven(ResusDrug.EPINEPHRINE)),
    entry("7", 6, ResusAction.Correction("6")),
    entry("8", 5, ResusAction.CprCycleStarted),
    entry("9", 3, ResusAction.RhythmChecked(Rhythm.PEA, startsCycle = false)),
    entry("10", 1, ResusAction.DrugGiven(ResusDrug.EPINEPHRINE), seq = null),
)

@Composable
private fun Render(
    access: ResusAccess = ResusAccess.Write,
    entries: List<ResusEntry> = Entries,
    prompt: ResusPrompt? = null,
    syncError: String? = null,
    finish: ResusFinish? = ResusFinish.RESOLVE,
) = PreviewTheme {
    ResusLogScreen(
        state = ResusLogUiState(
            locationName = PreviewData.acknowledgedByMe.location.name,
            t0 = minutesAgo(11),
            log = ResusLogState(
                log = ResusLogSnapshot(entries = entries),
                synced = true,
                syncError = syncError,
                prompt = prompt,
                undo = UndoOffer("10", "Epinefrin", PreviewNow.plusSeconds(4)),
            ),
            access = access,
            canMarkArrived = true,
            finish = finish,
        ),
        now = PreviewNow,
        actions = object : ResusLogActions {
            override fun onBack() = Unit
            override fun onRhythm(rhythm: Rhythm) = Unit
            override fun onRecord(action: ResusAction) = Unit
            override fun onConfirmPrompt() = Unit
            override fun onDismissPrompt() = Unit
            override fun onUndo() = Unit
            override fun onRequestVoid(entryId: String) = Unit
            override fun onArrive() = Unit
            override fun onResolve() = Unit
            override fun onDocument() = Unit
            override fun onRetry() = Unit
        },
    )
}

@SigapPreviews
@Composable
private fun ResusRecording() = Render()

@SigapPreviews
@Composable
private fun ResusSyncError() = Render(syncError = "Tidak dapat terhubung ke server.")

@SigapPreviews
@Composable
private fun ResusEmpty() = Render(entries = emptyList())

@SigapPreviews
@Composable
private fun ResusNurseReadOnly() = Render(
    finish = null,
    access = ResusAccess.ReadOnly("Hak perawat menulis log belum ditetapkan. Log hanya dapat dibaca."),
)

@SigapPreviews
@Composable
private fun ResusShockConflict() = Render(prompt = ResusPrompt.RhythmConflict(ResusAction.ShockDelivered, Rhythm.PEA))

@SigapPreviews
@Composable
private fun ResusOutcomeRecorded() = Render(
    entries = Entries + entry("11", 0, ResusAction.OutcomeRecorded(ResusOutcome.ROSC)),
)
