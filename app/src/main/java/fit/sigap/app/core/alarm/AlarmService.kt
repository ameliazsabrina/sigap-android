package fit.sigap.app.core.alarm

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import fit.sigap.app.BuildConfig
import fit.sigap.app.SigapApplication
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.push.AlertNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private val container get() = (application as SigapApplication).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var watching: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            container.alerts.alarmServiceNotification(container.alarms.alarms.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        if (container.alarms.alarms.value.isEmpty()) {
            stop()
            return START_NOT_STICKY
        }
        startSound()
        startVibration()
        if (watching == null) watching = scope.launch { watch() }
        return START_NOT_STICKY
    }

    private suspend fun watch() {
        scope.launch {
            container.alarms.alarms.collect { alarms ->
                if (alarms.isEmpty()) stop() else notifyLatest(alarms)
            }
        }
        while (scope.isActive) {
            checkServer()
            delay(CHECK_INTERVAL_MS)
        }
    }

    private suspend fun checkServer() {
        for (alarm in container.alarms.alarms.value) {
            try {
                if (container.push.alarmSettled(alarm.target)) {
                    container.alarms.settle(setOf(alarm.target.id))
                    container.alerts.cancel(alarm.target.id)
                }
            } catch (cause: ApiException) {
                if (cause.status == 401) {
                    container.alarms.settleAll()
                    return
                }
                if (BuildConfig.DEBUG) Log.w(TAG, "alarm check failed: ${cause.code}")
            }
        }
    }

    private fun notifyLatest(alarms: List<ActiveAlarm>) {
        if (container.alerts.canShow(AlertNotifier.ALARM_SERVICE_CHANNEL)) {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, container.alerts.alarmServiceNotification(alarms))
        }
    }

    private fun startSound() {
        if (player != null) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                prepare()
                start()
            }
        } catch (cause: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "alarm sound unavailable", cause)
            null
        }
    }

    private fun startVibration() {
        if (vibrator != null) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (!device.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            device.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            device.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        vibrator = device
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        player?.run {
            stop()
            release()
        }
        player = null
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SigapAlarm"
        private const val NOTIFICATION_ID = 2
        private const val CHECK_INTERVAL_MS = 10_000L
        private val VIBRATION = longArrayOf(0, 800, 400, 800, 400, 800, 1_200)

        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
            true
        } catch (cause: IllegalStateException) {
            if (BuildConfig.DEBUG) {
                val reason = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cause is ForegroundServiceStartNotAllowedException) "not allowed" else "illegal state"
                Log.w(TAG, "alarm service refused: $reason")
            }
            false
        }
    }
}
