package fit.sigap.app.core.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import fit.sigap.app.BuildConfig
import fit.sigap.app.SigapApplication

class SigapMessagingService : FirebaseMessagingService() {
    private val container get() = (application as SigapApplication).container

    override fun onMessageReceived(message: RemoteMessage) {
        logReceived(message)
        PushHandler(this, container).handle(message.data, highPriority = message.priority == RemoteMessage.PRIORITY_HIGH)
    }

    override fun onNewToken(token: String) {
        container.pushRegistrar.onNewToken()
    }

    private fun logReceived(message: RemoteMessage) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "received kind=${message.data["kind"]} keys=${message.data.keys.sorted()} " +
                "priority=${message.priority.label()} original=${message.originalPriority.label()} " +
                "collapse=${message.collapseKey} sentAt=${message.sentTime} latencyMs=${System.currentTimeMillis() - message.sentTime}",
        )
    }

    private fun Int.label() = when (this) {
        RemoteMessage.PRIORITY_HIGH -> "HIGH"
        RemoteMessage.PRIORITY_NORMAL -> "NORMAL"
        else -> "UNKNOWN($this)"
    }

    companion object {
        const val TAG = "SigapPush"
    }
}
