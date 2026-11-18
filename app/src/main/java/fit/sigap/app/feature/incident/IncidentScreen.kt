package fit.sigap.app.feature.incident

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.DefinitionRow
import fit.sigap.app.core.designsystem.component.Eyebrow
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.theme.LocalReducedMotion
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.core.format.formatTime
import fit.sigap.app.core.model.Incident
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import java.time.Instant

@Composable
fun IncidentScreen(
    incident: Incident,
    now: Instant,
    overdue: Boolean,
    canRespond: Boolean,
    phase: CommandPhase,
    onBack: () -> Unit,
    onAcknowledge: () -> Unit,
    onArrive: () -> Unit,
    onResolve: () -> Unit,
    onDocument: () -> Unit,
) {
    val colors = SigapTheme.colors
    AppScreen {
        BackLink("Siaga", onBack)

        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Eyebrow("Code Blue", color = colors.signalRed)
            Text(
                text = incident.location.name,
                style = MaterialTheme.typography.displayLarge,
                color = colors.ink950,
                modifier = Modifier.semantics { heading() },
            )
            if (incident.location.detailLine.isNotEmpty()) {
                Text(incident.location.detailLine, style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = colors.ink950)
            }
            Text(
                text = incident.teamStatusLine,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink950,
                modifier = Modifier
                    .padding(top = Space.x2)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(formatRelative(incident.createdAt, now), style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            TeamTimes(incident)
        }

        if (overdue) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.signalRed)
                    .padding(Space.x5)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                Text("Belum tiba", style = MaterialTheme.typography.headlineMedium, color = colors.paper0)
                Text(
                    text = "Batas waktu tiba sudah lewat. Tekan \"Tiba di lokasi\" saat sampai untuk menghentikan alarm.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.paper0,
                )
            }
        }

        if (canRespond) {
            IncidentActionArea(
                incident = incident,
                phase = phase,
                onAcknowledge = onAcknowledge,
                onArrive = onArrive,
                onResolve = onResolve,
                onDocument = onDocument,
            )
        } else if (!incident.isHandled) {
            Text(
                text = "Panggilan ini ditangani Tim Code Blue. Tombol respons tidak ditampilkan untuk peran Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink700,
            )
        }

        if (incident.isClosed) {
            SigapPanel(
                title = "Dokumentasi pascakejadian",
                description = "Dokumentasi selesai",
            ) {
                SigapButton("Lihat dokumentasi", onDocument, size = ButtonSize.Medium, variant = ButtonVariant.Secondary)
            }
        }

        PatientDetail(incident)
    }
}

@Composable
private fun TeamTimes(incident: Incident) {
    val acknowledged = incident.acknowledgedAt ?: return
    val line = buildString {
        append("Menuju lokasi ${formatTime(acknowledged)}")
        incident.arrivedAt?.let { append(" · tiba ${formatTime(it)}") }
        incident.resolvedAt?.let { append(" · selesai ${formatTime(it)}") }
    }
    Text(line, style = SigapTheme.data.xs, color = SigapTheme.colors.ink700)
}

@Composable
private fun PatientDetail(incident: Incident) {
    val colors = SigapTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.paper0),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Terbuka" else "Tertutup" }
                .padding(horizontal = Space.x4, vertical = Space.x3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Detail pasien", style = MaterialTheme.typography.bodyMedium, color = colors.ink950)
            Icon(
                painterResource(R.drawable.ic_chevron_down),
                contentDescription = null,
                tint = colors.ink700,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
        ) {
            Column {
                HorizontalDivider(color = colors.line200)
                Column(
                    modifier = Modifier.padding(Space.x4),
                    verticalArrangement = Arrangement.spacedBy(Space.x2),
                ) {
                    DefinitionRow("Nama tampilan", incident.patient.displayName)
                    DefinitionRow("Kode pasien", incident.patient.simulationCode, dataValue = true)
                    DefinitionRow("Nomor incident", incident.number, dataValue = true)
                    DefinitionRow("Alasan pemicu", incident.triggerSummary.ifBlank { "Tidak dirinci" })
                }
            }
        }
    }
}
