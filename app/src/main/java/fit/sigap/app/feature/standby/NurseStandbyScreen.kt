package fit.sigap.app.feature.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.ErrorState
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.LoadingState
import fit.sigap.app.core.designsystem.component.OutlinedCard
import fit.sigap.app.core.designsystem.component.SectionHeading
import fit.sigap.app.core.designsystem.component.SigapBadge
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.core.model.Shift
import fit.sigap.app.feature.alert.AlertBadges
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyHeader
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.feature.common.DutyTabBar
import fit.sigap.app.feature.common.ReadinessBlocker
import java.time.Instant

data class NurseStandbyUiState(
    val userName: String,
    val shift: Shift,
    val connection: ConnectionState,
    val readiness: Readiness,
    val alerts: List<EarlyAlert>,
    val incidents: List<Incident>,
    val patientCount: Int,
    val monitorDisconnected: Int,
    val checkPhase: CommandPhase = CommandPhase.Idle,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val waiting: List<EarlyAlert> get() = alerts.filter { it.isUnchecked }
    val patientsWithAlert: Int get() = alerts.map { it.patient.id }.distinct().size
}

interface NurseStandbyActions {
    fun onCheckFirst(id: String)
    fun onOpenAlert(id: String)
    fun onOpenIncident(id: String)
    fun onOpenReadiness()
    fun onRetry()
    fun onSelectTab(tab: DutyTab)
}

@Composable
fun NurseStandbyScreen(state: NurseStandbyUiState, now: Instant, actions: NurseStandbyActions) {
    val colors = SigapTheme.colors
    AppScreen(bottomBar = { DutyTabBar(DutyTab.STANDBY, actions::onSelectTab) }) {
        DutyHeader(state.userName, state.shift, state.connection)

        state.waiting.firstOrNull()?.let { first ->
            CheckBanner(first, state.waiting.size, state.checkPhase, actions)
        }

        if (state.readiness.blocked) {
            ReadinessBlocker(state.readiness, "Alert pasien", actions::onOpenReadiness)
        }

        state.incidents.forEach { incident ->
            CodeBlueLink(incident, now) { actions.onOpenIncident(incident.id) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            SectionHeading("Alert aktif di wilayah Anda")
            when {
                state.loading -> LoadingState(label = "Memeriksa alert")
                state.error != null -> ErrorState(state.error, onRetry = actions::onRetry)
                state.alerts.isEmpty() -> Text(
                    text = "Tidak ada alert aktif",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.ink700,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(colors.paper0)
                        .padding(horizontal = Space.x4, vertical = Space.x5),
                )
                else -> state.alerts.forEach { alert ->
                    NurseAlertCard(alert, now) { actions.onOpenAlert(alert.id) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            SectionHeading("Pasien di wilayah Anda")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                CountTile("Pasien", state.patientCount, colors.ink950, Modifier.weight(1f))
                CountTile(
                    label = "Dengan alert",
                    value = state.patientsWithAlert,
                    color = if (state.patientsWithAlert > 0) colors.signalAmber else colors.ink950,
                    modifier = Modifier.weight(1f),
                )
                CountTile(
                    label = "Monitor lepas",
                    value = state.monitorDisconnected,
                    color = if (state.monitorDisconnected > 0) colors.signalRed else colors.ink950,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CheckBanner(first: EarlyAlert, waiting: Int, phase: CommandPhase, actions: NurseStandbyActions) {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.signalAmber)
            .padding(Space.x5)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Text(
            text = if (waiting == 1) "CEK PASIEN" else "$waiting PASIEN MENUNGGU",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.paper0,
        )
        Text(
            text = buildString {
                append("${first.patient.locationName} · ${first.patient.displayName}")
                first.message?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = colors.paper0,
        )
        Row(
            modifier = Modifier.padding(top = Space.x3),
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            SigapButton(
                text = if (phase.isBusy) "Mencatat…" else "Cek pasien",
                onClick = { actions.onCheckFirst(first.id) },
                variant = ButtonVariant.InkOnSignal,
                size = ButtonSize.Large,
                busy = phase.isBusy,
                modifier = Modifier.weight(1f),
            )
            SigapButton(
                text = "Detail",
                onClick = { actions.onOpenAlert(first.id) },
                variant = ButtonVariant.OnSignal,
                size = ButtonSize.Large,
            )
        }
        if (phase is CommandPhase.Failed) {
            InlineError(phase.message, color = colors.paper0, prominent = true, modifier = Modifier.padding(top = Space.x2))
        }
    }
}

@Composable
private fun CodeBlueLink(incident: Incident, now: Instant, onClick: () -> Unit) {
    val colors = SigapTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.signalRed)
            .clickable(onClick = onClick)
            .padding(Space.x4)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_siren), null, tint = colors.paper0, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Code Blue · ${incident.location.name}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = colors.paper0,
            )
            Text(
                text = "${incident.patient.displayName} · ${formatRelative(incident.createdAt, now)}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.paper0,
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.paper0, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NurseAlertCard(alert: EarlyAlert, now: Instant, onClick: () -> Unit) {
    val colors = SigapTheme.colors
    OutlinedCard(
        borderColor = if (alert.isUnchecked) colors.signalAmber else colors.line200,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                Text(
                    text = alert.patient.locationName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink950,
                    modifier = Modifier.weight(1f),
                )
                if (alert.isUnchecked) {
                    SigapBadge("Belum dicek", solid = true, tone = fit.sigap.app.core.designsystem.component.BadgeTone.Amber)
                } else {
                    SigapBadge("Sudah dicek")
                }
            }
            Text(alert.patient.displayName, style = MaterialTheme.typography.bodyLarge, color = colors.ink950)
            AlertBadges(alert)
            alert.message?.let { Text(it, style = SigapTheme.data.sm, color = colors.ink950) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRelative(alert.createdAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink700,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.ink700, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CountTile(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(colors.paper0)
            .padding(Space.x3)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.ink700)
        Text(value.toString(), style = SigapTheme.data.xl.copy(fontSize = MaterialTheme.typography.displayMedium.fontSize, lineHeight = MaterialTheme.typography.displayMedium.lineHeight), color = color)
    }
}
