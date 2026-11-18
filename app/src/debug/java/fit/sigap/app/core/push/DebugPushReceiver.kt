package fit.sigap.app.core.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fit.sigap.app.SigapApplication
import kotlin.concurrent.thread

class DebugPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras ?: return
        val data = extras.keySet()
            .filter { it != PRIORITY }
            .mapNotNull { key -> extras.getString(key)?.let { key to it } }
            .toMap()
        val highPriority = extras.getString(PRIORITY) != "normal"
        val container = (context.applicationContext as SigapApplication).container
        val pending = goAsync()
        thread {
            try {
                PushHandler(context.applicationContext, container).handle(data, highPriority)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val PRIORITY = "priority"
    }
}
