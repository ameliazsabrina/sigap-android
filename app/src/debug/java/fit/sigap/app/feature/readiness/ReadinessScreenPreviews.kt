package fit.sigap.app.feature.readiness

import androidx.compose.runtime.Composable
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

@Composable
private fun Render(readiness: Readiness, pushTest: PushTestUiState = PushTestUiState()) = PreviewTheme {
    ReadinessScreen(
        readiness = readiness,
        pushTest = pushTest,
        onSendPushTest = {},
        onConfirmPushTest = {},
        onBack = {},
        onFixNotifications = {},
        onFixBattery = {},
        onFixFullScreen = {},
        onFixVolume = {},
    )
}

@SigapPreviews
@Composable
private fun ReadinessAllReady() = Render(PreviewData.readyPhone)

@SigapPreviews
@Composable
private fun ReadinessNotificationsBlocked() = Render(PreviewData.notificationsBlocked)

@SigapPreviews
@Composable
private fun ReadinessWarnings() = Render(PreviewData.warnings)

@SigapPreviews
@Composable
private fun ReadinessPushTestSent() = Render(PreviewData.pushMissing, PushTestUiState(sent = true))

@SigapPreviews
@Composable
private fun ReadinessPushTestFailed() = Render(
    PreviewData.pushMissing,
    PushTestUiState(phase = CommandPhase.Failed("Tidak dapat menghubungi server.")),
)

@SigapPreviews
@Composable
private fun ReadinessVendorGuidance() = Render(
    Readiness(
        batteryUnrestricted = false,
        pushConfirmed = true,
        vendorGuidance = "Di HP Xiaomi, aktifkan juga Autostart untuk SIGAP.",
    ),
)
