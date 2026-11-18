package fit.sigap.app.feature.standby

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.BadgeTone
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.ErrorState
import fit.sigap.app.core.designsystem.component.Eyebrow
import fit.sigap.app.core.designsystem.component.IconTile
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.LoadingState
import fit.sigap.app.core.designsystem.component.OutlinedCard
import fit.sigap.app.core.designsystem.component.SectionHeading
import fit.sigap.app.core.designsystem.component.SigapBadge
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.core.model.AlertLevel
import fit.sigap.app.core.model.AlertSource
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.core.model.Shift
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyHeader
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.feature.common.DutyTabBar
import fit.sigap.app.feature.common.ReadinessBlocker
import fit.sigap.app.feature.incident.IncidentActionArea
import java.time.Instant

data class TeamStandbyUiState(
    val userName: String,
    val shift: Shift,
    val connection: ConnectionState,
    val readiness: Readiness,
    val incidents: List<Incident>,
    val awaitingDocumentation: List<Incident>,
    val alerts: List<EarlyAlert>,
    val overdueIds: Set<String>,
    val phases: Map<String, CommandPhase>,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val unacknowledged: List<Incident> get() = incidents.filterNot { it.isAcknowledged }
    val alarmIncident: Incident?
        get() = unacknowledged.firstOrNull() ?: incidents.firstOrNull { it.id in overdueIds }
}

interface TeamStandbyActions {
    fun onOpenIncident(id: String)
    fun onAcknowledge(id: String)
    fun onArrive(id: String)
    fun onResolve(id: String)
    fun onDocument(id: String)
    fun onOpenAlert(id: String)
    fun onOpenReadiness()
    fun onRetry()
    fun onSelectTab(tab: DutyTab)
}

@Composable
fun TeamStandbyScreen(state: TeamStandbyUiState, now: Instant, actions: TeamStandbyActions) {
    val alarm = state.alarmIncident
    AppScreen(bottomBar = { DutyTabBar(DutyTab.STANDBY, actions::onSelectTab) }) {
        DutyHeader(state.userName, state.shift, state.connection)

        when {
            alarm != null -> AlarmBanner(state, alarm, now, actions)
            state.readiness.blocked -> ReadinessBlocker(state.readiness, "Code Blue", actions::onOpenReadiness)
            else -> ReadyCard(state.readiness, state.connection, state.incidents.size, actions::onOpenReadiness)
        }

        if (state.loading) {
            LoadingState(label = "Memeriksa Code Blue aktif")
        } else if (state.error != null) {
            ErrorState(state.error, onRetry = actions::onRetry)
        }

        val listed = state.incidents.filterNot { it.id == alarm?.id }
        if (listed.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeading(if (alarm != null) "Code Blue lainnya · ${listed.size}" else "Code Blue aktif · ${listed.size}")
                listed.forEach { incident ->
                    IncidentCard(
                        incident = incident,
                        now = now,
                        overdue = incident.id in state.overdueIds,
                        phase = state.phases[incident.id] ?: CommandPhase.Idle,
                        actions = actions,
                    )
                }
            }
        }

        if (state.awaitingDocumentation.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeading(
                    title = "Menunggu dokumentasi · ${state.awaitingDocumentation.size}",
                    description = "Penanganan selesai. Lengkapi dokumentasinya.",
                )
                state.awaitingDocumentation.forEach { incident ->
                    DocumentationLink(incident) { actions.onDocument(incident.id) }
                }
            }
        }

        if (state.alerts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                SectionHeading(
                    title = "Waspada · ${state.alerts.size}",
                    description = "Pasien yang sedang dinilai perawat. Bersiap bila menjadi Code Blue.",
                )
                state.alerts.forEach { alert ->
                    WatchAlertCard(alert, now) { actions.onOpenAlert(alert.id) }
                }
            }
        }
    }
}

@Composable
private fun AlarmBanner(state: TeamStandbyUiState, alarm: Incident, now: Instant, actions: TeamStandbyActions) {
    val colors = SigapTheme.colors
    val overdue = alarm.id in state.overdueIds && alarm.isAcknowledged
    val othersWaiting = state.unacknowledged.count { it.id != alarm.id }
    val phase = state.phases[alarm.id] ?: CommandPhase.Idle
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.signalRed)
            .padding(horizontal = Space.x5, vertical = Space.x6),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
                verticalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                Text(
                    text = if (overdue) "BELUM TIBA" else "CODE BLUE",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.paper0,
                    modifier = Modifier.semantics { heading() },
                )
                Text(alarm.location.name, style = MaterialTheme.typography.displaySmall, color = colors.paper0)
                if (alarm.location.detailLine.isNotEmpty()) {
                    Text(alarm.location.detailLine, style = MaterialTheme.typography.bodyLarge, color = colors.paper0)
                }
                Text(
                    text = if (overdue) {
                        "Batas waktu tiba sudah lewat. Tekan \"Tiba di lokasi\" saat sampai untuk menghentikan alarm."
                    } else {
                        "Belum ada yang menuju lokasi."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.paper0,
                    modifier = Modifier.padding(top = Space.x2),
                )
            }
            Text(formatRelative(alarm.createdAt, now), style = MaterialTheme.typography.bodyMedium, color = colors.paper0)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            SigapButton(
                text = when {
                    phase.isBusy -> "Memproses…"
                    overdue -> "Tiba di lokasi"
                    else -> "Saya menuju lokasi"
                },
                onClick = { if (overdue) actions.onArrive(alarm.id) else actions.onAcknowledge(alarm.id) },
                variant = ButtonVariant.OnSignal,
                size = ButtonSize.Alarm,
                fullWidth = true,
                busy = phase.isBusy,
                icon = if (overdue) R.drawable.ic_map_pin else R.drawable.ic_check,
            )
            when (phase) {
                CommandPhase.Opening -> BannerNote("Mencatat bahwa panggilan sudah dibuka. Anda belum tercatat menuju lokasi.")
                CommandPhase.Sending -> BannerNote("Mengirim ke server. Alarm berhenti setelah server mencatatnya.")
                is CommandPhase.Failed -> InlineError(
                    "${phase.message} Alarm tetap berbunyi. Menekan tombol lagi mengulang permintaan yang sama.",
                    color = colors.paper0,
                    prominent = true,
                )
                CommandPhase.Idle -> Unit
            }
            SigapButton(
                text = "Lihat detail",
                onClick = { actions.onOpenIncident(alarm.id) },
                variant = ButtonVariant.OutlineOnSignal,
                size = ButtonSize.Large,
                fullWidth = true,
            )
        }

        if (othersWaiting > 0) {
            BannerNote("$othersWaiting Code Blue lain juga belum ada yang menuju lokasi. Daftarnya di bawah.")
        }
    }
}

@Composable
private fun BannerNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = SigapTheme.colors.paper0,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ReadyCard(readiness: Readiness, connection: ConnectionState, activeCount: Int, onOpen: () -> Unit) {
    val colors = SigapTheme.colors
    val needsCheck = readiness.warningCount > 0
    SigapPanel(padding = Space.x5, onClick = onOpen, onClickLabel = "Buka kesiapan HP") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x4),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        ) {
            IconTile(
                icon = if (needsCheck) R.drawable.ic_triangle_alert else R.drawable.ic_shield_check,
                color = if (needsCheck) colors.signalAmber else colors.signalGreen,
                size = 56.dp,
                iconSize = 32.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        activeCount > 0 -> "Sedang menangani Code Blue"
                        needsCheck -> "Periksa kesiapan HP"
                        else -> "Siap menerima Code Blue"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink950,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (needsCheck) {
                        "${readiness.warningCount} pengaturan perlu dicek. Alarm bisa tidak terlihat atau tidak terdengar."
                    } else {
                        "HP berbunyi keras saat ada panggilan di wilayah Anda."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink700,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            val live = connection == ConnectionState.LIVE
            ReadinessChip(R.drawable.ic_bell_ring, "Notifikasi", "aktif", readiness.notificationsAllowed, Modifier.weight(1f))
            ReadinessChip(
                icon = R.drawable.ic_volume_2,
                label = "Alarm",
                hint = if (readiness.alarmVolumeAudible) "siap" else "volume 0",
                ok = readiness.alarmVolumeAudible,
                modifier = Modifier.weight(1f),
            )
            ReadinessChip(R.drawable.ic_wifi, "Koneksi", if (live) "terhubung" else "terputus", live, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReadinessChip(@DrawableRes icon: Int, label: String, hint: String, ok: Boolean, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (ok) colors.paper50 else colors.signalAmber.tint(0.15f))
            .padding(horizontal = Space.x2, vertical = Space.x3)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (ok) colors.signalGreen else colors.signalAmber,
            modifier = Modifier.size(20.dp),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink950, textAlign = TextAlign.Center)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.ink700, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IncidentCard(
    incident: Incident,
    now: Instant,
    overdue: Boolean,
    phase: CommandPhase,
    actions: TeamStandbyActions,
) {
    val colors = SigapTheme.colors
    val urgent = !incident.isAcknowledged || overdue
    OutlinedCard(borderColor = if (urgent) colors.signalRed else colors.line200) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Buka detail incident") { actions.onOpenIncident(incident.id) }
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Eyebrow("Code Blue", color = if (urgent) colors.signalRed else colors.ink700)
            Text(incident.location.name, style = MaterialTheme.typography.displaySmall, color = colors.ink950)
            if (incident.location.detailLine.isNotEmpty()) {
                Text(incident.location.detailLine, style = MaterialTheme.typography.bodyLarge, color = colors.ink950)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatRelative(incident.createdAt, now)} · ${incident.cardStatusLine(overdue)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (overdue) colors.signalRed else colors.ink700,
                    modifier = Modifier.weight(1f),
                )
                Text("Detail", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.ink700, modifier = Modifier.size(20.dp))
            }
        }
        IncidentActionArea(
            incident = incident,
            phase = phase,
            onAcknowledge = { actions.onAcknowledge(incident.id) },
            onArrive = { actions.onArrive(incident.id) },
            onResolve = { actions.onResolve(incident.id) },
            onDocument = { actions.onDocument(incident.id) },
            showNotes = false,
            modifier = Modifier.padding(top = Space.x1),
        )
    }
}

@Composable
private fun DocumentationLink(incident: Incident, onClick: () -> Unit) {
    val colors = SigapTheme.colors
    OutlinedCard(borderColor = colors.signalViolet.tint(0.3f), borderWidth = 1.dp, onClick = onClick) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(R.drawable.ic_file_text, colors.signalViolet)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = incident.location.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink950,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${incident.number} · ${incident.patient.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink700,
                )
            }
            Text("Isi", style = MaterialTheme.typography.labelMedium, color = colors.signalViolet)
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.ink700, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchAlertCard(alert: EarlyAlert, now: Instant, onClick: () -> Unit) {
    val colors = SigapTheme.colors
    OutlinedCard(borderColor = colors.signalAmber.tint(0.4f), borderWidth = 1.dp, onClick = onClick) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                SigapBadge(
                    text = if (alert.source == AlertSource.DETECTION) "Waspada" else alert.source.label,
                    tone = BadgeTone.Amber,
                    icon = R.drawable.ic_triangle_alert,
                )
                if (alert.test) SigapBadge("UJI", tone = BadgeTone.Violet)
                alert.level?.let { SigapBadge(it.label, tone = if (it == AlertLevel.HIGH) BadgeTone.Red else BadgeTone.Amber) }
                if (alert.patient.dnr) SigapBadge("DNR", tone = BadgeTone.Red, solid = true)
            }
            Column {
                Text(alert.patient.locationName, style = MaterialTheme.typography.headlineMedium, color = colors.ink950)
                Text(alert.patient.displayName, style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            }
            alert.message?.let { Text(it, style = SigapTheme.data.sm, color = colors.ink950) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatRelative(alert.createdAt, now)} · " +
                        if (alert.checkedAt != null) "Perawat sudah di pasien" else "Menunggu perawat",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink700,
                    modifier = Modifier.weight(1f),
                )
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.ink700, modifier = Modifier.size(16.dp))
            }
        }
    }
}
