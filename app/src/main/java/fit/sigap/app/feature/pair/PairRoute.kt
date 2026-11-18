package fit.sigap.app.feature.pair

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.sigap.app.core.device.batteryExemptionIntent
import fit.sigap.app.core.device.notificationSettingsIntent
import fit.sigap.app.core.device.openSettings

@Composable
fun PairRoute(viewModel: PairViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onNotificationsResult(granted)
    }
    val battery = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.finishSetup()
    }

    LifecycleResumeEffect(state.step, state.notificationsDenied) {
        if (state.notificationsDenied && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            viewModel.onNotificationsResult(true)
        }
        onPauseOrDispose {}
    }

    val actions = remember(viewModel) {
        object : PairActions {
            override fun onScan() = viewModel.startScan()
            override fun onCancelScan() = viewModel.cancelScan()
            override fun onScanned(raw: String) = viewModel.onScanned(raw)
            override fun onRetry() = viewModel.retry()
            override fun onAllowNotifications() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationsResult(NotificationManagerCompat.from(context).areNotificationsEnabled())
                }
            }
            override fun onOpenNotificationSettings() = context.openSettings(context.notificationSettingsIntent())
            override fun onSkipNotifications() = viewModel.skipNotifications()
            override fun onOpenBatterySettings() {
                val power = context.getSystemService(PowerManager::class.java)
                if (power.isIgnoringBatteryOptimizations(context.packageName)) {
                    viewModel.finishSetup()
                    return
                }
                try {
                    battery.launch(context.batteryExemptionIntent())
                } catch (_: ActivityNotFoundException) {
                    viewModel.finishSetup()
                }
            }
        }
    }
    PairScreen(state, actions)
}
