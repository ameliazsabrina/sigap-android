package fit.sigap.app.feature.duty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

@Composable
private fun Render(
    phase: CommandPhase = CommandPhase.Idle,
    role: DutyRole = DutyRole.NURSE,
) = PreviewTheme {
    StartDutyScreen(
        userName = PreviewData.NURSE,
        role = role,
        phase = phase,
        onSubmitCode = {},
        onClearError = {},
        signOutPhase = CommandPhase.Idle,
        onSignOut = {},
        onDismissSignOut = {},
    )
}

@SigapPreviews
@Composable
private fun StartDutyNurse() = Render()

@SigapPreviews
@Composable
private fun StartDutyTeam() = Render(role = DutyRole.CODE_BLUE_TEAM)

@SigapPreviews
@Composable
private fun StartDutyFailed() = Render(CommandPhase.Failed("Kode absen sudah kedaluwarsa. Minta kode baru di nurse station."))

@Composable
private fun Scanner(phase: CommandPhase) = PreviewTheme {
    Column(Modifier.background(SigapTheme.colors.paper50).statusBarsPadding().padding(Space.x4)) {
        ScannerPanel(phase = phase, onSubmitCode = {}, onClearError = {}, onCancel = {})
    }
}

@SigapPreviews
@Composable
private fun ScannerCamera() = Scanner(CommandPhase.Idle)

@SigapPreviews
@Composable
private fun ScannerSending() = Scanner(CommandPhase.Sending)

@SigapPreviews
@Composable
private fun ScannerFailed() = Scanner(CommandPhase.Failed("Kode absen sudah kedaluwarsa. Minta kode baru di nurse station."))
