package fit.sigap.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.model.Readiness

@Composable
fun ReadinessBlocker(readiness: Readiness, subject: String, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    val (title, fix) = when {
        !readiness.notificationsAllowed -> "Notifikasi diblokir" to
            "Izinkan notifikasi untuk SIGAP. Tanpa izin ini, $subject tidak sampai ke HP ini."
        !readiness.batteryUnrestricted -> "Baterai masih dibatasi" to
            "Izinkan SIGAP berjalan tanpa batas baterai. Tanpa izin ini, Android dapat menunda alarm."
        else -> "Push belum aktif" to
            "HP ini belum menerima push. $subject hanya terlihat selama aplikasi terbuka di layar ini."
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.signalRed)
            .padding(horizontal = Space.x5, vertical = Space.x6),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.paper0,
                modifier = Modifier.semantics { heading() },
            )
            Text(fix, style = MaterialTheme.typography.bodyLarge, color = colors.paper0)
        }
        SigapButton("Lihat kesiapan HP", onFix, variant = ButtonVariant.OnSignal, size = ButtonSize.Large, fullWidth = true)
    }
}
