package fit.sigap.app.feature.incident

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatTime
import fit.sigap.app.core.model.Incident
import fit.sigap.app.feature.common.CommandPhase
import java.time.Instant

@Composable
fun IncidentActionArea(
    incident: Incident,
    phase: CommandPhase,
    onAcknowledge: () -> Unit,
    onArrive: () -> Unit,
    onResolve: () -> Unit,
    onDocument: () -> Unit,
    modifier: Modifier = Modifier,
    showNotes: Boolean = true,
) {
    when {
        incident.awaitsDocumentation -> SigapButton(
            text = "Isi dokumentasi (wajib)",
            onClick = onDocument,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            fullWidth = true,
            icon = R.drawable.ic_file_text,
            modifier = modifier,
        )

        !incident.isAcknowledged -> AcknowledgeAction(phase, onAcknowledge, modifier, showNotes)

        incident.arrivedAt == null -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            if (incident.acknowledgedByMe) incident.acknowledgedAt?.let { AcknowledgeReceipt(it) }
            ResponseAction(
                label = "Tiba di lokasi",
                note = "Jam kedatangan dicatat server saat tombol ditekan dan tidak dapat diubah.",
                phase = phase,
                onClick = onArrive,
                showNote = showNotes,
                modifier = Modifier,
            )
        }

        !incident.isClosed -> ResponseAction(
            label = "Nyatakan penanganan selesai",
            note = "Menutup tahap respons dan membuka dokumentasi. Bukan pernyataan tentang kondisi pasien.",
            phase = phase,
            onClick = onResolve,
            showNote = showNotes,
            modifier = modifier,
        )
    }
}

@Composable
private fun AcknowledgeReceipt(at: Instant) {
    val green = SigapTheme.colors.signalGreen
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(painterResource(R.drawable.ic_circle_check), null, tint = green, modifier = Modifier.size(20.dp))
        Text(
            text = "Tercatat menuju lokasi ${formatTime(at)}, waktu server.",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = green,
        )
    }
}

@Composable
private fun AcknowledgeAction(
    phase: CommandPhase,
    onAcknowledge: () -> Unit,
    modifier: Modifier,
    showNotes: Boolean,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SigapButton(
            text = if (phase.isBusy) "Memproses…" else "Saya menuju lokasi",
            onClick = onAcknowledge,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            fullWidth = true,
            busy = phase.isBusy,
            icon = R.drawable.ic_check,
        )
        when (phase) {
            CommandPhase.Opening -> PhaseText("Mencatat bahwa panggilan sudah dibuka. Anda belum tercatat menuju lokasi.")
            CommandPhase.Sending -> PhaseText("Mengirim ke server. Anda tercatat menuju lokasi setelah server menjawab.")
            is CommandPhase.Failed -> InlineError(
                "${phase.message} Anda belum tercatat menuju lokasi. Menekan tombol lagi " +
                    "mengulang permintaan yang sama, bukan mencatat dua kali.",
            )
            CommandPhase.Idle -> if (showNotes) {
                PhaseText("Satu ketukan, tanpa dialog konfirmasi. Waktu dan identitas diambil server dari sesi Anda.")
            }
        }
    }
}

@Composable
private fun ResponseAction(
    label: String,
    note: String,
    phase: CommandPhase,
    onClick: () -> Unit,
    showNote: Boolean,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        SigapButton(
            text = if (phase.isBusy) "Memproses…" else label,
            onClick = onClick,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            fullWidth = true,
            busy = phase.isBusy,
        )
        if (phase is CommandPhase.Failed) {
            InlineError("${phase.message} Menekan tombol lagi mengulang permintaan yang sama.")
        }
        if (showNote) PhaseText(note)
    }
}

@Composable
private fun PhaseText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SigapTheme.colors.ink700,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
