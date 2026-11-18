package fit.sigap.app.feature.duty

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapLogo
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.component.SigapTextField
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.core.scanner.QrScanner

@Composable
fun StartDutyScreen(
    userName: String,
    role: DutyRole,
    phase: CommandPhase,
    onSubmitCode: (String) -> Unit,
    onClearError: () -> Unit,
    signOutPhase: CommandPhase,
    onSignOut: () -> Unit,
    onDismissSignOut: () -> Unit,
) {
    val colors = SigapTheme.colors
    var scanning by rememberSaveable { mutableStateOf(false) }
    var confirmingSignOut by rememberSaveable { mutableStateOf(false) }
    AppScreen(gap = Space.x8) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.x6),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            SigapLogo()
            Text("Halo, $userName", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            Text(
                text = "Mulai tugas",
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink950,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Pindai QR di layar nurse station. " + when (role) {
                    DutyRole.NURSE -> "Alert pasien baru dikirim ke HP ini setelah Anda bertugas."
                    DutyRole.CODE_BLUE_TEAM -> "Panggilan Code Blue baru dikirim ke HP ini setelah Anda bertugas."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink700,
                textAlign = TextAlign.Center,
            )
        }

        if (scanning) {
            ScannerPanel(
                phase = phase,
                onSubmitCode = onSubmitCode,
                onClearError = onClearError,
                onCancel = {
                    scanning = false
                    onClearError()
                },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                SigapButton(
                    text = "Pindai QR absen",
                    onClick = { scanning = true },
                    variant = ButtonVariant.Primary,
                    size = ButtonSize.Alarm,
                    fullWidth = true,
                    icon = R.drawable.ic_scan_line,
                )
                Text(
                    text = "Kamera tidak bisa dipakai? Setelah menekan tombol, kode 6 angka di bawah QR dapat diketik.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink700,
                    textAlign = TextAlign.Center,
                )
                if (phase is CommandPhase.Failed) InlineError(phase.message)
            }
            SigapButton(
                text = "Keluar dari HP ini",
                onClick = { confirmingSignOut = true },
                variant = ButtonVariant.Ghost,
                fullWidth = true,
                icon = R.drawable.ic_log_out,
            )
        }
    }

    if (confirmingSignOut) {
        val dismiss = {
            confirmingSignOut = false
            onDismissSignOut()
        }
        AlertDialog(
            onDismissRequest = { if (!signOutPhase.isBusy) dismiss() },
            containerColor = colors.paper0,
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text("Keluar dari HP ini?", style = MaterialTheme.typography.titleLarge, color = colors.ink950)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    Text(
                        text = "Sesi HP ini diakhiri di server. Untuk memakainya lagi, minta QR pairing baru dari Administrator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink700,
                    )
                    if (signOutPhase is CommandPhase.Failed) InlineError(signOutPhase.message)
                }
            },
            confirmButton = {
                SigapButton(
                    text = if (signOutPhase.isBusy) "Keluar…" else "Keluar",
                    onClick = onSignOut,
                    variant = ButtonVariant.Danger,
                    busy = signOutPhase.isBusy,
                )
            },
            dismissButton = {
                SigapButton("Batal", dismiss, enabled = !signOutPhase.isBusy)
            },
        )
    }
}

@Composable
internal fun ScannerPanel(
    phase: CommandPhase,
    onSubmitCode: (String) -> Unit,
    onClearError: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = SigapTheme.colors
    var code by rememberSaveable { mutableStateOf("") }
    SigapPanel(padding = Space.x4) {
        when (phase) {
            is CommandPhase.Failed -> {
                InlineError(phase.message)
                SigapButton("Pindai lagi", onClearError, variant = ButtonVariant.Secondary, fullWidth = true)
            }
            CommandPhase.Idle -> QrScanner(description = "Jendela kamera untuk memindai QR absen", onResult = onSubmitCode)
            else -> Text("Mencatat absen…", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
            verticalAlignment = Alignment.Bottom,
        ) {
            SigapTextField(
                label = "Atau ketik kode 6 angka",
                value = code,
                onValueChange = { next -> code = next.filter(Char::isDigit) },
                numeric = true,
                maxLength = 6,
                keyboardType = KeyboardType.NumberPassword,
                modifier = Modifier.weight(1f),
            )
            SigapButton(
                text = "Absen",
                onClick = { onSubmitCode(code) },
                enabled = code.length == 6,
                busy = phase.isBusy,
                size = ButtonSize.Large,
            )
        }
        SigapButton(
            text = "Batal",
            onClick = onCancel,
            variant = ButtonVariant.Ghost,
            enabled = !phase.isBusy,
            fullWidth = true,
        )
    }
}
