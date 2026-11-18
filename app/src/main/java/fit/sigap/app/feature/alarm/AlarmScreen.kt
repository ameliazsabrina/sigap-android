package fit.sigap.app.feature.alarm

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.ButtonSize
import fit.sigap.app.core.designsystem.component.ButtonVariant
import fit.sigap.app.core.designsystem.component.InlineError
import fit.sigap.app.core.designsystem.component.SigapButton
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.format.formatRelative
import fit.sigap.app.feature.common.CommandPhase
import java.time.Instant

data class AlarmUiState(
    val kind: AlarmKind,
    val headline: String,
    val location: String,
    val detail: String,
    val createdAt: Instant,
    val waitingLine: String,
    val primaryLabel: String,
    val busyLabel: String,
    val phase: CommandPhase = CommandPhase.Idle,
    val stoppedLine: String? = null,
)

enum class AlarmKind { CODE_BLUE, EARLY_ALERT }

@Composable
fun AlarmScreen(
    state: AlarmUiState,
    now: Instant,
    onPrimary: () -> Unit,
    onDetail: () -> Unit,
) {
    val colors = SigapTheme.colors
    val stopped = state.stoppedLine != null
    LightSystemBarIcons()
    val background: Color = when {
        stopped -> colors.ink950
        state.kind == AlarmKind.CODE_BLUE -> colors.signalRed
        else -> colors.signalAmber
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(Space.x6),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AlarmSummary(state, now, stopped)
            AlarmButtons(state, stopped, onPrimary, onDetail)
        }
    }
}

@Composable
private fun LightSystemBarIcons() {
    if (LocalInspectionMode.current) return
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }
}

@Composable
private fun AlarmSummary(state: AlarmUiState, now: Instant, stopped: Boolean) {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier.padding(top = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        Icon(
            painterResource(
                when {
                    stopped -> R.drawable.ic_circle_check
                    state.kind == AlarmKind.CODE_BLUE -> R.drawable.ic_siren
                    else -> R.drawable.ic_triangle_alert
                },
            ),
            contentDescription = null,
            tint = colors.paper0,
            modifier = Modifier.size(48.dp),
        )
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Assertive
            },
            verticalArrangement = Arrangement.spacedBy(Space.x3),
        ) {
            Text(
                text = state.headline,
                style = MaterialTheme.typography.titleMedium,
                color = colors.paper0,
                modifier = Modifier.semantics { heading() },
            )
            Text(state.location, style = MaterialTheme.typography.displayLarge, color = colors.paper0)
            if (state.detail.isNotEmpty()) {
                Text(state.detail, style = MaterialTheme.typography.bodyLarge, color = colors.paper0)
            }
            Text(
                text = state.stoppedLine ?: state.waitingLine,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.paper0,
            )
        }
        Text(
            text = formatRelative(state.createdAt, now),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.paper0,
        )
    }
}

@Composable
private fun AlarmButtons(state: AlarmUiState, stopped: Boolean, onPrimary: () -> Unit, onDetail: () -> Unit) {
    val colors = SigapTheme.colors
    Column(
        modifier = Modifier.padding(top = Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        if (!stopped) {
            SigapButton(
                text = if (state.phase.isBusy) state.busyLabel else state.primaryLabel,
                onClick = onPrimary,
                variant = if (state.kind == AlarmKind.CODE_BLUE) ButtonVariant.OnSignal else ButtonVariant.InkOnSignal,
                size = ButtonSize.Alarm,
                fullWidth = true,
                busy = state.phase.isBusy,
                icon = R.drawable.ic_check,
            )
            when (val phase = state.phase) {
                CommandPhase.Opening -> PhaseNote("Mencatat bahwa alert sudah dibuka. Tindakan Anda belum tercatat.")
                CommandPhase.Sending -> PhaseNote("Mengirim ke server. Alarm berhenti setelah server mencatatnya.")
                is CommandPhase.Failed -> InlineError(
                    "${phase.message} Alarm tetap berbunyi. Menekan tombol lagi mengulang permintaan yang sama.",
                    color = colors.paper0,
                    prominent = true,
                )
                CommandPhase.Idle -> Unit
            }
        }
        SigapButton(
            text = "Lihat detail",
            onClick = onDetail,
            variant = ButtonVariant.OutlineOnSignal,
            size = ButtonSize.Large,
            fullWidth = true,
        )
    }
}

@Composable
private fun PhaseNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = SigapTheme.colors.paper0)
}
