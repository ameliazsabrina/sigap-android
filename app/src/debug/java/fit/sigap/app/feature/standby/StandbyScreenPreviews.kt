package fit.sigap.app.feature.standby

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

private object NoNurseActions : NurseStandbyActions {
    override fun onCheckFirst(id: String) = Unit
    override fun onOpenAlert(id: String) = Unit
    override fun onOpenIncident(id: String) = Unit
    override fun onOpenReadiness() = Unit
    override fun onRetry() = Unit
    override fun onSelectTab(tab: DutyTab) = Unit
}

private object NoTeamActions : TeamStandbyActions {
    override fun onOpenIncident(id: String) = Unit
    override fun onAcknowledge(id: String) = Unit
    override fun onArrive(id: String) = Unit
    override fun onResolve(id: String) = Unit
    override fun onDocument(id: String) = Unit
    override fun onOpenAlert(id: String) = Unit
    override fun onOpenReadiness() = Unit
    override fun onRetry() = Unit
    override fun onSelectTab(tab: DutyTab) = Unit
}

private val quietNurse = NurseStandbyUiState(
    userName = PreviewData.NURSE,
    shift = PreviewData.shift,
    connection = ConnectionState.LIVE,
    readiness = PreviewData.readyPhone,
    alerts = emptyList(),
    incidents = emptyList(),
    patientCount = 14,
    monitorDisconnected = 0,
)

private val quietTeam = TeamStandbyUiState(
    userName = PreviewData.RESPONDER,
    shift = PreviewData.shift,
    connection = ConnectionState.LIVE,
    readiness = PreviewData.readyPhone,
    incidents = emptyList(),
    awaitingDocumentation = emptyList(),
    alerts = emptyList(),
    overdueIds = emptySet(),
    phases = emptyMap(),
)

@Composable
private fun Nurse(state: NurseStandbyUiState) = PreviewTheme {
    NurseStandbyScreen(state = state, now = PreviewNow, actions = NoNurseActions)
}

@Composable
private fun Team(state: TeamStandbyUiState) = PreviewTheme {
    TeamStandbyScreen(state = state, now = PreviewNow, actions = NoTeamActions)
}

@SigapPreviews
@Composable
private fun NurseQuiet() = Nurse(quietNurse)

@SigapPreviews
@Composable
private fun NurseWaiting() = Nurse(
    quietNurse.copy(
        alerts = listOf(PreviewData.uncheckedAlert, PreviewData.secondUncheckedAlert, PreviewData.checkedAlert),
        monitorDisconnected = 1,
    ),
)

@SigapPreviews
@Composable
private fun NurseCheckFailed() = Nurse(
    quietNurse.copy(
        alerts = listOf(PreviewData.uncheckedAlert),
        checkPhase = CommandPhase.Failed("Tidak dapat menghubungi server."),
    ),
)

@SigapPreviews
@Composable
private fun NurseCodeBlueRunning() = Nurse(
    quietNurse.copy(
        alerts = listOf(PreviewData.escalatedAlert),
        incidents = listOf(PreviewData.acknowledgedByOther),
    ),
)

@SigapPreviews
@Composable
private fun NurseNotificationsBlocked() = Nurse(
    quietNurse.copy(readiness = PreviewData.notificationsBlocked, connection = ConnectionState.POLLING),
)

@SigapPreviews
@Composable
private fun NurseLoading() = Nurse(quietNurse.copy(loading = true))

@SigapPreviews
@Composable
private fun NurseError() = Nurse(quietNurse.copy(error = "Tidak dapat menghubungi server. Periksa koneksi HP."))

@SigapPreviews
@Composable
private fun TeamReady() = Team(quietTeam)

@SigapPreviews
@Composable
private fun TeamReadyWithWarnings() = Team(quietTeam.copy(readiness = PreviewData.warnings))

@SigapPreviews
@Composable
private fun TeamPushMissing() = Team(quietTeam.copy(readiness = PreviewData.pushMissing))

@SigapPreviews
@Composable
private fun TeamAlarm() = Team(
    quietTeam.copy(
        incidents = listOf(PreviewData.unacknowledgedIncident),
        alerts = listOf(PreviewData.checkedAlert),
    ),
)

@SigapPreviews
@Composable
private fun TeamAcknowledgeSending() = Team(
    quietTeam.copy(
        incidents = listOf(PreviewData.unacknowledgedIncident),
        phases = mapOf(PreviewData.unacknowledgedIncident.id to CommandPhase.Sending),
    ),
)

@SigapPreviews
@Composable
private fun TeamOverdue() = Team(
    quietTeam.copy(
        incidents = listOf(PreviewData.acknowledgedByMe),
        overdueIds = setOf(PreviewData.acknowledgedByMe.id),
    ),
)

@SigapPreviews
@Composable
private fun TeamAfterResponse() = Team(
    quietTeam.copy(
        incidents = listOf(PreviewData.arrived),
        awaitingDocumentation = listOf(PreviewData.awaitingDocumentation),
        alerts = listOf(PreviewData.uncheckedAlert, PreviewData.testAlert),
    ),
)

@SigapPreviews
@Composable
private fun TeamError() = Team(quietTeam.copy(error = "Tidak dapat menghubungi server. Periksa koneksi HP."))
