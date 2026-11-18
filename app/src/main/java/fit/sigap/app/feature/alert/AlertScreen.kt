package fit.sigap.app.feature.alert

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.BadgeTone
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.NoticeStrip
import fit.sigap.app.core.designsystem.component.SigapBadge
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.component.SigapTextField
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.core.format.formatTime
import fit.sigap.app.core.model.AlertLevel
import fit.sigap.app.core.model.AlertSource
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.EarlyAlertStatus
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.VitalsGrid
import java.time.Instant

data class AlertUiState(
    val alert: EarlyAlert,
    val incident: Incident?,
    val vitals: PatientVitals,
    val canDecide: Boolean,
    val phase: CommandPhase = CommandPhase.Idle,
)

interface AlertActions {
    fun onBack()
    fun onCheck()
    fun onClose(reason: String)
    fun onEscalate(reason: String)
}

private enum class Decision { CLOSE, ESCALATE }

private const val DefaultClose = "Pasien sudah diperiksa dan dinilai tidak kritis."
private const val DefaultEscalate = "Perawat memanggil Code Blue setelah memeriksa pasien."

@Composable
fun AlertScreen(state: AlertUiState, now: Instant, actions: AlertActions) {
    val colors = SigapTheme.colors
    AppScreen {
        BackLink("Siaga", actions::onBack)
        AlertHeader(state.alert, now)

        SigapPanel(padding = Space.x4) {
            AlertSteps(state.alert, state.incident)
            if (state.canDecide) AlertDecisions(state, actions)
            if (state.alert.status == EarlyAlertStatus.CLOSED) {
                Text(
                    "Alert ditutup: pasien dinilai tidak kritis.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink700,
                )
            }
        }

        state.incident?.let { incident -> CodeBlueStatus(incident) }

        SigapPanel(title = "Tanda vital terbaru", padding = Space.x4) {
            VitalsGrid(state.vitals, now)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlertBadges(alert: EarlyAlert, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        if (alert.test) SigapBadge("UJI", tone = BadgeTone.Violet)
        SourceBadge(alert.source)
        alert.level?.let { SigapBadge(it.label, tone = if (it == AlertLevel.HIGH) BadgeTone.Red else BadgeTone.Amber) }
        if (alert.patient.dnr) SigapBadge("DNR", tone = BadgeTone.Red, solid = true)
        alert.patient.allergy?.let { SigapBadge("Alergi: $it", tone = BadgeTone.Amber) }
    }
}

@Composable
private fun SourceBadge(source: AlertSource) {
    when (source) {
        AlertSource.DATA_MONITOR -> SigapBadge(source.label, icon = R.drawable.ic_unplug)
        AlertSource.NURSE_CALL -> SigapBadge(source.label, tone = BadgeTone.Violet, icon = R.drawable.ic_bell_ring)
        AlertSource.DETECTION -> SigapBadge(source.label, icon = R.drawable.ic_cpu)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertHeader(alert: EarlyAlert, now: Instant) {
    val colors = SigapTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalArrangement = Arrangement.spacedBy(Space.x1),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (alert.test) SigapBadge("UJI", tone = BadgeTone.Violet)
            SourceBadge(alert.source)
            alert.level?.let { SigapBadge(it.label, tone = if (it == AlertLevel.HIGH) BadgeTone.Red else BadgeTone.Amber) }
            Text(formatRelative(alert.createdAt, now), style = MaterialTheme.typography.bodySmall, color = colors.ink700)
        }
        Column {
            Text(
                text = alert.patient.locationName,
                style = MaterialTheme.typography.displayLarge,
                color = colors.ink950,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = alert.patient.displayName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = colors.ink950,
                modifier = Modifier.padding(top = Space.x1),
            )
            alert.patient.identityLine?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            }
        }
        alert.message?.let { message ->
            Text(
                text = message,
                style = SigapTheme.data.md,
                color = colors.ink950,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.signalAmber.tint(0.15f))
                    .padding(horizontal = Space.x4, vertical = Space.x3),
            )
        }
        val patient = alert.patient
        if (patient.dnr || patient.allergy != null || patient.oxygen || patient.spo2Scale2) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                if (patient.dnr) SigapBadge("DNR", tone = BadgeTone.Red, solid = true)
                patient.allergy?.let { SigapBadge("Alergi: $it", tone = BadgeTone.Amber) }
                if (patient.oxygen) SigapBadge("Oksigen")
                if (patient.spo2Scale2) SigapBadge("Target SpO₂ khusus")
            }
        }
    }
}

@Composable
private fun AlertSteps(alert: EarlyAlert, incident: Incident?) {
    val decided = alert.isFinished
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        StepRow("Alert terbit", true, alert.createdAt, SigapTheme.colors.ink950)
        StepRow("Pasien dicek", alert.checkedAt != null, alert.checkedAt, SigapTheme.colors.ink950)
        StepRow(
            label = when (alert.status) {
                EarlyAlertStatus.ESCALATED -> "Code Blue dipanggil"
                EarlyAlertStatus.CLOSED -> "Tidak kritis"
                else -> "Keputusan"
            },
            done = decided,
            at = alert.closedAt ?: incident?.createdAt,
            doneColor = SigapTheme.colors.ink950,
        )
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, at: Instant?, doneColor: Color, doneIconColor: Color = SigapTheme.colors.signalGreen) {
    val colors = SigapTheme.colors
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            painterResource(if (done) R.drawable.ic_check else R.drawable.ic_circle),
            contentDescription = if (done) "Selesai" else "Belum",
            tint = if (done) doneIconColor else colors.ink700.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) doneColor else colors.ink700,
            modifier = Modifier.weight(1f),
        )
        if (done && at != null) {
            Text(formatTime(at), style = SigapTheme.data.xs, color = colors.ink700)
        }
    }
}

@Composable
private fun AlertDecisions(state: AlertUiState, actions: AlertActions) {
    val alert = state.alert
    var decision by rememberSaveable { mutableStateOf<Decision?>(null) }
    var reason by rememberSaveable { mutableStateOf("") }
    val failed = state.phase as? CommandPhase.Failed

    when {
        alert.isUnchecked -> SigapButton(
            text = if (state.phase.isBusy) "Mencatat…" else "Cek pasien",
            onClick = actions::onCheck,
            variant = ButtonVariant.Primary,
            size = ButtonSize.Large,
            fullWidth = true,
            busy = state.phase.isBusy,
        )

        alert.isDecidable -> Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            SigapButton(
                text = "Panggil Code Blue",
                onClick = {
                    reason = DefaultEscalate
                    decision = Decision.ESCALATE
                },
                variant = ButtonVariant.Danger,
                size = ButtonSize.Large,
                fullWidth = true,
                enabled = !state.phase.isBusy,
            )
            SigapButton(
                text = "Tidak kritis",
                onClick = {
                    reason = DefaultClose
                    decision = Decision.CLOSE
                },
                size = ButtonSize.Large,
                fullWidth = true,
                enabled = !state.phase.isBusy,
            )
        }
    }

    if (failed != null && decision == null) InlineError(failed.message)

    val open = decision
    if (open != null) {
        DecisionDialog(
            decision = open,
            locationName = alert.patient.locationName,
            reason = reason,
            onReason = { reason = it },
            phase = state.phase,
            onDismiss = { if (!state.phase.isBusy) decision = null },
            onConfirm = {
                val text = reason.trim()
                if (open == Decision.ESCALATE) actions.onEscalate(text) else actions.onClose(text)
            },
        )
    }
    LaunchedEffect(alert.isDecidable) {
        if (!alert.isDecidable) decision = null
    }
}

@Composable
private fun DecisionDialog(
    decision: Decision,
    locationName: String,
    reason: String,
    onReason: (String) -> Unit,
    phase: CommandPhase,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SigapTheme.colors
    val escalate = decision == Decision.ESCALATE
    val tooShort = reason.trim().length < 3
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.paper0,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = if (escalate) "Panggil Code Blue?" else "Nyatakan tidak kritis?",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink950,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text(
                    text = if (escalate) {
                        "HP Tim Code Blue akan berbunyi untuk $locationName."
                    } else {
                        "Early alert ditutup dan masuk riwayat."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink700,
                )
                SigapTextField(
                    label = "Alasan",
                    value = reason,
                    onValueChange = onReason,
                    hint = "Tersimpan di audit bersama nama Anda dan waktunya.",
                    error = if (tooShort) "Alasan minimal 3 karakter." else null,
                    singleLine = false,
                    minLines = 3,
                    maxLength = 500,
                )
                if (phase is CommandPhase.Failed) InlineError(phase.message)
            }
        },
        confirmButton = {
            SigapButton(
                text = when {
                    phase.isBusy -> "Mengirim…"
                    escalate -> "Panggil sekarang"
                    else -> "Tutup alert"
                },
                onClick = onConfirm,
                variant = if (escalate) ButtonVariant.Danger else ButtonVariant.Primary,
                enabled = !tooShort,
                busy = phase.isBusy,
            )
        },
        dismissButton = {
            SigapButton("Batal", onDismiss, enabled = !phase.isBusy)
        },
    )
}

@Composable
private fun CodeBlueStatus(incident: Incident) {
    val colors = SigapTheme.colors
    val finished = incident.isHandled
    val content = if (finished) colors.ink950 else colors.paper0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (finished) colors.paper0 else colors.signalRed)
            .padding(Space.x4),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Text(
            text = "Code Blue ${incident.number}" + if (finished) " · selesai ditangani" else "",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            color = content,
            modifier = Modifier.semantics { heading() },
        )
        TeamRow("Tim menerima", incident.acknowledgedAt, content)
        TeamRow("Tim tiba di lokasi", incident.arrivedAt, content)
    }
}

@Composable
private fun TeamRow(label: String, at: Instant?, content: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Icon(
            painterResource(if (at != null) R.drawable.ic_check else R.drawable.ic_circle),
            contentDescription = null,
            tint = if (at != null) content else content.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
        Text(at?.let(::formatTime) ?: "menunggu", style = SigapTheme.data.xs, color = content)
    }
}
