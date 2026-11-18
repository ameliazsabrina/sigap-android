package fit.sigap.app.core.push

import android.content.Context
import android.util.Log
import fit.sigap.app.AppContainer
import fit.sigap.app.BuildConfig
import fit.sigap.app.core.alarm.ActiveAlarm
import fit.sigap.app.core.alarm.AlarmService
import fit.sigap.app.core.network.ApiException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

class PushHandler(private val context: Context, private val container: AppContainer) {
    fun handle(data: Map<String, String>, highPriority: Boolean) {
        when (val parsed = PushMessage.parse(data)) {
            is PushMessage.Alert -> {
                val shown = ringAlarm(parsed, highPriority) ?: container.alerts.show(parsed)
                val notificationId = parsed.notificationId
                if (shown && notificationId != null) sendReceipt(notificationId)
            }
            is PushMessage.AlarmStop -> stopIfSettled(parsed.target)
            null -> if (BuildConfig.DEBUG) Log.w(TAG, "message without usable kind ignored")
        }
    }

    private fun ringAlarm(alert: PushMessage.Alert, highPriority: Boolean): Boolean? {
        val target = alert.alarmTarget
        if (!alert.alarm || target == null || !highPriority) return null
        container.alarms.ring(ActiveAlarm(target, alert.title, alert.body, Instant.now()))
        if (!AlarmService.start(context)) {
            container.alarms.settle(setOf(target.id))
            return null
        }
        return container.alerts.canShow(AlertNotifier.ALARM_SERVICE_CHANNEL)
    }

    private fun sendReceipt(notificationId: String) = runBlocking {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            try {
                container.push.markReceived(notificationId)
            } catch (cause: ApiException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "receipt failed: ${cause.code}")
            }
        }
    }

    private fun stopIfSettled(target: AlarmTarget) = runBlocking {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            try {
                if (container.push.alarmSettled(target)) {
                    container.alerts.cancel(target.id)
                    container.alarms.settle(setOf(target.id))
                }
            } catch (cause: ApiException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "alarm stop not confirmed: ${cause.code}")
            }
        }
    }

    private companion object {
        const val TAG = SigapMessagingService.TAG
        const val NETWORK_TIMEOUT_MS = 10_000L
    }
}
