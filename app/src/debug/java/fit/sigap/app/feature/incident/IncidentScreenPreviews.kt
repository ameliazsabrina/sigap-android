package fit.sigap.app.feature.incident

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.Incident
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

@Composable
private fun Render(
    incident: Incident,
    overdue: Boolean = false,
    canRespond: Boolean = true,
    phase: CommandPhase = CommandPhase.Idle,
) = PreviewTheme {
    IncidentScreen(
        incident = incident,
        now = PreviewNow,
        overdue = overdue,
        canRespond = canRespond,
        phase = phase,
        onBack = {},
        onAcknowledge = {},
        onArrive = {},
        onResolve = {},
        onDocument = {},
    )
}

@SigapPreviews
@Composable
private fun IncidentUnacknowledged() = Render(PreviewData.unacknowledgedIncident)

@SigapPreviews
@Composable
private fun IncidentAcknowledgeFailed() = Render(
    PreviewData.unacknowledgedIncident,
    phase = CommandPhase.Failed("Tidak dapat menghubungi server."),
)

@SigapPreviews
@Composable
private fun IncidentOnTheWay() = Render(PreviewData.acknowledgedByMe)

@SigapPreviews
@Composable
private fun IncidentOverdue() = Render(PreviewData.acknowledgedByMe, overdue = true)

@SigapPreviews
@Composable
private fun IncidentArrived() = Render(PreviewData.arrived)

@SigapPreviews
@Composable
private fun IncidentAwaitingDocumentation() = Render(PreviewData.awaitingDocumentation)

@SigapPreviews
@Composable
private fun IncidentCompleted() = Render(PreviewData.completed)

@SigapPreviews
@Composable
private fun IncidentViewOnly() = Render(PreviewData.acknowledgedByOther, canRespond = false)
