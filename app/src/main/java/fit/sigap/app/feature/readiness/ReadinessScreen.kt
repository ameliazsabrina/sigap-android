package fit.sigap.app.feature.readiness

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.BackLink
import fit.sigap.app.core.designsystem.component.BadgeTone
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.IconTile
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapBadge
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.component.SigapPanel
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.feature.common.AppScreen
import fit.sigap.app.feature.common.CommandPhase

data class PushTestUiState(val phase: CommandPhase = CommandPhase.Idle, val sent: Boolean = false)

private data class ReadinessItem(
    @param:DrawableRes val icon: Int,
    val title: String,
    val okLine: String,
    val problemLine: String,
    val ok: Boolean,
    val fixLabel: String?,
    val fix: () -> Unit,
    val fixContent: (@Composable () -> Unit)? = null,
)

@Composable
fun ReadinessScreen(
    readiness: Readiness,
    pushTest: PushTestUiState,
    onSendPushTest: () -> Unit,
    onConfirmPushTest: () -> Unit,
    onBack: () -> Unit,
    onFixNotifications: () -> Unit,
    onFixBattery: () -> Unit,
    onFixFullScreen: () -> Unit,
    onFixVolume: () -> Unit,
) {
    val colors = SigapTheme.colors
    val required = listOf(
        ReadinessItem(
            R.drawable.ic_bell_ring,
            "Notifikasi",
            "Diizinkan. Alert dapat sampai saat layar terkunci.",
            "Diblokir. Code Blue tidak akan sampai ke HP ini.",
            readiness.notificationsAllowed,
            "Izinkan notifikasi",
            onFixNotifications,
        ),
        ReadinessItem(
            R.drawable.ic_battery_charging,
            "Baterai tanpa batas",
            "Android tidak membatasi SIGAP.",
            "Android dapat menunda alarm saat HP lama tidak dipakai.",
            readiness.batteryUnrestricted,
            "Buka pengaturan baterai",
            onFixBattery,
        ),
        ReadinessItem(
            R.drawable.ic_smartphone,
            "Push ke HP ini",
            "Notifikasi uji sudah dikonfirmasi.",
            "Belum dikonfirmasi. Sampai dikonfirmasi, alert hanya terlihat saat aplikasi terbuka.",
            readiness.pushConfirmed,
            null,
            {},
            fixContent = { PushTestControls(pushTest, readiness.notificationsAllowed, onSendPushTest, onConfirmPushTest) },
        ),
    )
    val recommended = listOf(
        ReadinessItem(
            R.drawable.ic_maximize,
            "Alert layar penuh",
            "Alarm dapat tampil di atas lock screen.",
            "Alarm tampil sebagai notifikasi biasa, bukan layar penuh.",
            readiness.fullScreenAllowed,
            "Izinkan layar penuh",
            onFixFullScreen,
        ),
        ReadinessItem(
            R.drawable.ic_volume_2,
            "Volume alarm",
            "Volume alarm terdengar.",
            "Volume alarm 0. Alarm tidak akan terdengar.",
            readiness.alarmVolumeAudible,
            "Naikkan volume",
            onFixVolume,
        ),
    )
    AppScreen {
        BackLink("Kembali", onBack)
        Column(verticalArrangement = Arrangement.spacedBy(Space.x1)) {
            Text(
                text = "Kesiapan HP",
                style = MaterialTheme.typography.displaySmall,
                color = colors.ink950,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = when {
                    readiness.blocked -> "HP ini belum dapat dihubungi. Perbaiki syarat wajib di bawah."
                    readiness.warningCount > 0 -> "HP ini dapat dihubungi, tetapi alarm bisa tidak terlihat atau tidak terdengar."
                    else -> "HP ini dapat dihubungi."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink700,
            )
        }
        ReadinessGroup("Wajib", "Layar siaga menampilkan peringatan sampai semuanya aktif.", required, requiredGroup = true)
        ReadinessGroup("Disarankan", "Tidak memblokir siaga, tetapi menentukan apakah alarm terlihat dan terdengar.", recommended, requiredGroup = false)
        readiness.vendorGuidance?.let { guidance ->
            SigapPanel(title = "Pengaturan khusus HP ini", description = guidance) {}
        }
        Text(
            text = "Bila aplikasi dihentikan paksa dari pengaturan, Android tidak mengirim notifikasi sampai SIGAP dibuka lagi.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink700,
        )
    }
}

@Composable
private fun ReadinessGroup(title: String, description: String, items: List<ReadinessItem>, requiredGroup: Boolean) {
    val colors = SigapTheme.colors
    SigapPanel(title = title, description = description) {
        items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = colors.line200)
            Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                    verticalAlignment = Alignment.Top,
                ) {
                    IconTile(item.icon, if (item.ok) colors.signalGreen else if (requiredGroup) colors.signalRed else colors.signalAmber)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.x2),
                        ) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink950,
                                modifier = Modifier.weight(1f),
                            )
                            if (item.ok) {
                                SigapBadge("Aktif", tone = BadgeTone.Green, icon = R.drawable.ic_check)
                            } else {
                                SigapBadge(
                                    text = if (requiredGroup) "Belum aktif" else "Perlu dicek",
                                    tone = if (requiredGroup) BadgeTone.Red else BadgeTone.Amber,
                                    icon = R.drawable.ic_circle_alert,
                                )
                            }
                        }
                        Text(
                            text = if (item.ok) item.okLine else item.problemLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink700,
                        )
                    }
                }
                if (!item.ok && item.fixContent != null) {
                    item.fixContent.invoke()
                } else if (!item.ok && item.fixLabel != null) {
                    SigapButton(
                        text = item.fixLabel,
                        onClick = item.fix,
                        variant = if (requiredGroup) ButtonVariant.Primary else ButtonVariant.Secondary,
                        size = ButtonSize.Medium,
                        fullWidth = true,
                        modifier = Modifier.padding(start = 0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PushTestControls(
    state: PushTestUiState,
    notificationsAllowed: Boolean,
    onSend: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = SigapTheme.colors
    val busy = state.phase.isBusy
    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
        if (!notificationsAllowed) {
            Text(
                "Izinkan notifikasi lebih dulu. Tanpa izin, notifikasi uji tidak akan tampil.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink700,
            )
        }
        if (state.sent) {
            Text(
                "Server sudah mengirim notifikasi uji. Tekan tombol di bawah hanya bila notifikasinya benar-benar tampil di HP ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink700,
            )
            SigapButton(
                text = "Saya menerimanya",
                onClick = onConfirm,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Medium,
                busy = busy,
                enabled = !busy,
                fullWidth = true,
            )
            SigapButton(
                text = "Tidak tampil, kirim ulang",
                onClick = onSend,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Medium,
                enabled = !busy,
                fullWidth = true,
            )
        } else {
            SigapButton(
                text = "Kirim notifikasi uji",
                onClick = onSend,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Medium,
                busy = busy,
                enabled = !busy,
                fullWidth = true,
            )
        }
        val phase = state.phase
        if (phase is CommandPhase.Failed) InlineError(phase.message)
    }
}
