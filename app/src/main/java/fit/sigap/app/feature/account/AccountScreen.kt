package fit.sigap.app.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint
import fit.sigap.app.core.format.formatClock
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.model.Shift
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.feature.common.DutyTab
import fit.sigap.app.feature.common.DutyTabBar

@Composable
fun AccountScreen(
    userName: String,
    role: DutyRole,
    shift: Shift,
    uncheckedAlerts: Int,
    phase: CommandPhase,
    onFinish: () -> Unit,
    onOpenReadiness: () -> Unit,
    onSelectTab: (DutyTab) -> Unit,
) {
    val colors = SigapTheme.colors
    var confirming by rememberSaveable { mutableStateOf(false) }
    AppScreen(bottomBar = { DutyTabBar(DutyTab.ACCOUNT, onSelectTab) }) {
        Text(
            text = "Akun",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.ink950,
            modifier = Modifier.semantics { heading() },
        )

        SigapPanel {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colors.brand.tint(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userName.split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.first().uppercase() },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        color = colors.brand,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.ink950,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${role.label} · HP terpasang", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
                }
            }
        }

        SigapPanel(onClick = onOpenReadiness, onClickLabel = "Buka kesiapan HP") {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                Icon(painterResource(R.drawable.ic_smartphone), null, tint = colors.ink700, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kesiapan HP", style = MaterialTheme.typography.titleMedium, color = colors.ink950)
                    Text(
                        "Notifikasi, baterai, dan suara alarm",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink700,
                    )
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = colors.ink700, modifier = Modifier.size(20.dp))
            }
        }

        SigapPanel {
            Column {
                Text("Sedang bertugas", style = MaterialTheme.typography.labelSmall, color = colors.ink700)
                Text(
                    text = shift.wardName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.ink950,
                    modifier = Modifier.padding(top = Space.x1),
                )
                Text("Sejak ${formatClock(shift.startedAt)}", style = MaterialTheme.typography.bodyMedium, color = colors.ink700)
            }
            SigapButton(
                text = if (phase.isBusy) "Mengakhiri…" else "Selesai bertugas",
                onClick = { if (uncheckedAlerts > 0) confirming = true else onFinish() },
                size = ButtonSize.Large,
                fullWidth = true,
                busy = phase.isBusy,
                icon = R.drawable.ic_log_out,
            )
            Text(
                text = "Setelah selesai, alert tidak lagi dikirim ke HP ini. HP tetap terpasang; absen lagi saat shift berikutnya, atau keluar dari HP ini setelah selesai.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink700,
            )
            if (phase is CommandPhase.Failed) InlineError(phase.message)
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { if (!phase.isBusy) confirming = false },
            containerColor = colors.paper0,
            shape = MaterialTheme.shapes.extraLarge,
            title = {
                Text("Masih ada pasien belum dicek", style = MaterialTheme.typography.titleLarge, color = colors.ink950)
            },
            text = {
                Text(
                    text = "$uncheckedAlerts alert di ${shift.wardName} belum dicek. Pastikan sudah diserahterimakan sebelum selesai bertugas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink700,
                )
            },
            confirmButton = {
                SigapButton(
                    text = if (phase.isBusy) "Mengakhiri…" else "Tetap selesai",
                    onClick = onFinish,
                    variant = ButtonVariant.Danger,
                    busy = phase.isBusy,
                )
            },
            dismissButton = {
                SigapButton("Batal", { confirming = false }, enabled = !phase.isBusy)
            },
        )
    }
}
