package fit.sigap.app.feature.alarm

import androidx.compose.runtime.Composable
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews
import fit.sigap.app.preview.minutesAgo

private val codeBlue = AlarmUiState(
    kind = AlarmKind.CODE_BLUE,
    headline = "Code Blue",
    location = "Rawat Inap Melati",
    detail = "Gedung A · Lantai 3 · Ruang 301 · Bed MLT-301A",
    createdAt = minutesAgo(1),
    waitingLine = "Belum ada yang menuju lokasi",
    primaryLabel = "Saya menuju lokasi",
    busyLabel = "Memproses…",
)

private val earlyAlert = AlarmUiState(
    kind = AlarmKind.EARLY_ALERT,
    headline = "Early alert kritis",
    location = "MLT-301A",
    detail = "Ny. Siti Aminah · Single Red · Laju napas 8 /menit",
    createdAt = PreviewNow.minusSeconds(25),
    waitingLine = "Menunggu pengecekan",
    primaryLabel = "Cek pasien",
    busyLabel = "Mencatat…",
)

@Composable
private fun Alarm(state: AlarmUiState) = PreviewTheme {
    AlarmScreen(state = state, now = PreviewNow, onPrimary = {}, onDetail = {})
}

@SigapPreviews
@Composable
private fun CodeBlueWaiting() = Alarm(codeBlue)

@SigapPreviews
@Composable
private fun CodeBlueSending() = Alarm(codeBlue.copy(phase = CommandPhase.Sending))

@SigapPreviews
@Composable
private fun CodeBlueFailed() = Alarm(
    codeBlue.copy(phase = CommandPhase.Failed("Tidak dapat menghubungi server.")),
)

@SigapPreviews
@Composable
private fun EarlyAlertWaiting() = Alarm(earlyAlert)

@SigapPreviews
@Composable
private fun AlarmStopped() = Alarm(
    codeBlue.copy(stoppedLine = "Tercatat menuju lokasi. Alarm berhenti."),
)
