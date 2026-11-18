package fit.sigap.app.feature.account

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

@Composable
private fun Render(
    role: DutyRole = DutyRole.NURSE,
    uncheckedAlerts: Int = 0,
    phase: CommandPhase = CommandPhase.Idle,
) = PreviewTheme {
    AccountScreen(
        userName = if (role == DutyRole.NURSE) PreviewData.NURSE else PreviewData.RESPONDER,
        role = role,
        shift = PreviewData.shift,
        uncheckedAlerts = uncheckedAlerts,
        phase = phase,
        onFinish = {},
        onOpenReadiness = {},
        onSelectTab = {},
    )
}

@SigapPreviews
@Composable
private fun AccountNurse() = Render()

@SigapPreviews
@Composable
private fun AccountTeam() = Render(role = DutyRole.CODE_BLUE_TEAM)

@SigapPreviews
@Composable
private fun AccountFinishFailed() = Render(phase = CommandPhase.Failed("Tidak dapat menghubungi server."))
