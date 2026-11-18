package fit.sigap.app.feature.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fit.sigap.app.R
import fit.sigap.app.core.designsystem.component.NoticeStrip
import fit.sigap.app.core.designsystem.theme.SigapTheme
import fit.sigap.app.core.designsystem.theme.Space
import fit.sigap.app.core.designsystem.theme.tint
import fit.sigap.app.core.format.formatClock
import fit.sigap.app.core.model.ConnectionState
import fit.sigap.app.core.model.Shift

enum class DutyTab(val label: String, @param:DrawableRes val icon: Int) {
    STANDBY("Siaga", R.drawable.ic_bell_ring),
    ACCOUNT("Akun", R.drawable.ic_user_round),
}

@Composable
fun DutyTabBar(current: DutyTab, onSelect: (DutyTab) -> Unit, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    NavigationBar(modifier = modifier, containerColor = colors.paper0, tonalElevation = 0.dp) {
        DutyTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.brand,
                    selectedTextColor = colors.brand,
                    indicatorColor = colors.brand.tint(),
                    unselectedIconColor = colors.ink700,
                    unselectedTextColor = colors.ink700,
                ),
            )
        }
    }
}

@Composable
fun DutyHeader(name: String, shift: Shift, connection: ConnectionState, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.x3)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink950,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ConnectionChip(connection, modifier = Modifier.padding(top = 2.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colors.brand)
                .padding(horizontal = Space.x4, vertical = Space.x3)
                .semantics(mergeDescendants = true) {},
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_map_pin), null, tint = colors.paper0, modifier = Modifier.size(14.dp))
                    Text("WILAYAH TUGAS", style = MaterialTheme.typography.labelSmall, color = colors.paper0)
                }
                Text("sejak ${formatClock(shift.startedAt)}", style = SigapTheme.data.xs, color = colors.paper0)
            }
            Text(
                text = shift.wardName,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = colors.paper0,
                modifier = Modifier.padding(top = Space.x1),
            )
        }
        if (connection == ConnectionState.POLLING) {
            NoticeStrip("Koneksi terputus · layar diperbarui tiap 10 detik", colors.signalAmber)
        }
    }
}

@Composable
private fun ConnectionChip(connection: ConnectionState, modifier: Modifier = Modifier) {
    val colors = SigapTheme.colors
    val live = connection == ConnectionState.LIVE
    val dot = if (live) colors.signalGreen else colors.signalAmber
    Row(
        modifier = modifier
            .background(if (live) colors.signalGreen.tint() else colors.signalAmber.tint(0.15f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            text = if (live) "Terhubung" else "Terputus",
            style = MaterialTheme.typography.labelSmall,
            color = if (live) colors.signalGreen else colors.ink950,
        )
    }
}
