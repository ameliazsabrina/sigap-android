package fit.sigap.app.feature.pair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapLogo
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.scanner.QrScanner
import fit.sigap.app.feature.common.AppScreen

enum class PairStep(val number: Int?) {
    SCAN(null),
    NOTIFY(1),
    BATTERY(2),
    SESSION_ENDED(null),
}

private const val TotalSteps = 2

data class PairUiState(
    val step: PairStep,
    val userName: String = "",
    val roleLabel: String = "",
    val scanning: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val retryToken: String? = null,
    val notificationsDenied: Boolean = false,
    val inSetup: Boolean = false,
)

interface PairActions {
    fun onScan()
    fun onCancelScan()
    fun onScanned(raw: String)
    fun onRetry()
    fun onAllowNotifications()
    fun onOpenNotificationSettings()
    fun onSkipNotifications()
    fun onOpenBatterySettings()
}

@Composable
fun PairScreen(state: PairUiState, actions: PairActions) {
    AppScreen(gap = Space.x6) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.x10),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SigapLogo(size = Space.x10, withName = true)
        }
        SigapPanel(padding = Space.x6) {
            state.step.number?.let { number ->
                Text(
                    text = "LANGKAH $number DARI $TotalSteps",
                    style = SigapTheme.data.xs,
                    color = SigapTheme.colors.ink700,
                )
            }
            when (state.step) {
                PairStep.SCAN -> StepBody(
                    title = "Pasangkan HP ini",
                    body = "Pindai QR pairing pribadi Anda dari Administrator. HP ini langsung tertaut ke akun Anda dan mencatat tindakan atas nama Anda.",
                ) {
                    ScanActions(state, actions)
                }

                PairStep.NOTIFY -> StepBody(
                    title = "Terpasang untuk ${state.userName}",
                    body = "${state.roleLabel}. Tanpa notifikasi, panggilan tidak akan sampai ke HP ini saat layar terkunci.",
                ) {
                    if (state.notificationsDenied) {
                        InlineError("Notifikasi belum diizinkan. Izinkan di pengaturan HP, lalu kembali ke sini.")
                        PrimaryAction("Buka pengaturan notifikasi", "Membuka…", false, actions::onOpenNotificationSettings)
                        SigapButton("Lewati untuk sekarang", actions::onSkipNotifications, variant = ButtonVariant.Ghost, fullWidth = true)
                    } else {
                        PrimaryAction("Izinkan notifikasi", "Mengaktifkan…", false, actions::onAllowNotifications)
                    }
                }

                PairStep.BATTERY -> StepBody(
                    title = "Izinkan tanpa batas baterai",
                    body = "Tanpa izin ini, Android dapat menunda alarm saat HP lama tidak dipakai. Pilih \"Izinkan\" pada dialog berikutnya.",
                ) {
                    PrimaryAction("Buka pengaturan baterai", "Membuka…", false, actions::onOpenBatterySettings, R.drawable.ic_battery_charging)
                }

                PairStep.SESSION_ENDED -> StepBody(
                    title = "Sesi HP ini berakhir",
                    body = "HP ini tidak lagi terpasang. Minta QR pairing baru dari Administrator, lalu pasangkan ulang.",
                ) {
                    ScanActions(state, actions, scanLabel = "Pasangkan ulang")
                }
            }
            state.error?.let { InlineError(it) }
        }
    }
}

@Composable
private fun ScanActions(state: PairUiState, actions: PairActions, scanLabel: String = "Pindai QR pairing") {
    when {
        state.scanning -> {
            QrScanner(description = "Jendela kamera untuk memindai QR pairing", onResult = actions::onScanned)
            SigapButton("Batal", actions::onCancelScan, variant = ButtonVariant.Ghost, fullWidth = true)
        }
        state.retryToken != null -> {
            PrimaryAction("Coba lagi", "Memasangkan…", state.busy, actions::onRetry)
            SigapButton("Pindai QR lain", actions::onScan, variant = ButtonVariant.Ghost, enabled = !state.busy, fullWidth = true)
        }
        else -> PrimaryAction(scanLabel, "Memasangkan…", state.busy, actions::onScan, R.drawable.ic_scan_line)
    }
}

@Composable
private fun StepBody(title: String, body: String, actions: @Composable () -> Unit) {
    val colors = SigapTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink950,
            modifier = Modifier.semantics { heading() },
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
    }
    Column(
        modifier = Modifier.padding(top = Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        actions()
    }
}

@Composable
private fun PrimaryAction(label: String, busyLabel: String, busy: Boolean, onClick: () -> Unit, icon: Int? = null) {
    SigapButton(
        text = if (busy) busyLabel else label,
        onClick = onClick,
        variant = ButtonVariant.Primary,
        size = ButtonSize.Large,
        fullWidth = true,
        busy = busy,
        icon = icon,
    )
}
