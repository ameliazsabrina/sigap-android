package fit.sigap.app.feature.pair

import androidx.compose.runtime.Composable
import fit.sigap.app.preview.PreviewData
import fit.sigap.app.preview.PreviewTheme
import fit.sigap.app.preview.SigapPreviews

private object NoPairActions : PairActions {
    override fun onScan() = Unit
    override fun onCancelScan() = Unit
    override fun onScanned(raw: String) = Unit
    override fun onRetry() = Unit
    override fun onAllowNotifications() = Unit
    override fun onOpenNotificationSettings() = Unit
    override fun onSkipNotifications() = Unit
    override fun onOpenBatterySettings() = Unit
}

private val paired = PairUiState(
    step = PairStep.NOTIFY,
    userName = PreviewData.NURSE,
    roleLabel = "Perawat",
    inSetup = true,
)

@Composable
private fun Render(state: PairUiState) = PreviewTheme { PairScreen(state = state, actions = NoPairActions) }

@SigapPreviews
@Composable
private fun PairStart() = Render(PairUiState(step = PairStep.SCAN))

@SigapPreviews
@Composable
private fun PairScanning() = Render(PairUiState(step = PairStep.SCAN, scanning = true))

@SigapPreviews
@Composable
private fun PairNetworkError() = Render(
    PairUiState(
        step = PairStep.SCAN,
        error = "Tidak dapat menghubungi server. QR belum tentu terpakai, jadi boleh dicoba lagi.",
        retryToken = "preview",
    ),
)

@SigapPreviews
@Composable
private fun PairNotify() = Render(paired)

@SigapPreviews
@Composable
private fun PairNotifyDenied() = Render(paired.copy(notificationsDenied = true))

@SigapPreviews
@Composable
private fun PairBattery() = Render(paired.copy(step = PairStep.BATTERY))

@SigapPreviews
@Composable
private fun PairSessionEnded() = Render(PairUiState(step = PairStep.SESSION_ENDED))
