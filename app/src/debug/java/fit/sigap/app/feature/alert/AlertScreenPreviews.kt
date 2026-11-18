package fit.sigap.app.feature.alert

import androidx.compose.runtime.Composable
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewNow
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

private object NoAlertActions : AlertActions {
    override fun onBack() = Unit
    override fun onCheck() = Unit
    override fun onClose(reason: String) = Unit
    override fun onEscalate(reason: String) = Unit
}

private val unchecked = AlertUiState(
    alert = PreviewData.uncheckedAlert,
    incident = null,
    vitals = PreviewData.vitals,
    canDecide = true,
)

@Composable
private fun Alert(state: AlertUiState) = PreviewTheme {
    AlertScreen(state = state, now = PreviewNow, actions = NoAlertActions)
}

@SigapPreviews
@Composable
private fun AlertUnchecked() = Alert(unchecked)

@SigapPreviews
@Composable
private fun AlertCheckFailed() = Alert(unchecked.copy(phase = CommandPhase.Failed("Tidak dapat menghubungi server.")))

@SigapPreviews
@Composable
private fun AlertDecidable() = Alert(unchecked.copy(alert = PreviewData.checkedAlert))

@SigapPreviews
@Composable
private fun AlertEscalated() = Alert(
    unchecked.copy(alert = PreviewData.escalatedAlert, incident = PreviewData.acknowledgedByOther),
)

@SigapPreviews
@Composable
private fun AlertClosedStaleVitals() = Alert(
    unchecked.copy(alert = PreviewData.closedAlert, vitals = PreviewData.staleVitals),
)

@SigapPreviews
@Composable
private fun AlertViewOnly() = Alert(unchecked.copy(alert = PreviewData.checkedAlert, canDecide = false))
