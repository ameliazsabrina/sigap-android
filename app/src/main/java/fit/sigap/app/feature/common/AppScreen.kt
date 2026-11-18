package fit.sigap.app.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import kotlinx.coroutines.delay
import java.time.Instant

private val MaxContentWidth = 560.dp

@Composable
fun AppScreen(
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    gap: androidx.compose.ui.unit.Dp = Space.x5,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = SigapTheme.colors.paper50,
        contentColor = SigapTheme.colors.ink950,
        bottomBar = bottomBar,
    ) { padding: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = Space.x4, vertical = Space.x4),
                verticalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
    }
}

@Composable
fun rememberNow(intervalMillis: Long = 5_000): State<Instant> = produceState(Instant.now()) {
    while (true) {
        delay(intervalMillis)
        value = Instant.now()
    }
}
