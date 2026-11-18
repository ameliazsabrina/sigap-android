package fit.sigap.app.core.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fit.sigap.app.MainActivity
import fit.sigap.app.R
import fit.sigap.app.core.alarm.ActiveAlarm
import fit.sigap.app.feature.alarm.AlarmActivity

class AlertNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarm = NotificationChannel(ALARM_CHANNEL, "Code Blue dan alert kritis", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Berbunyi dengan suara alarm sampai alert ditangani."
            setSound(alarmSound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            enableVibration(true)
            vibrationPattern = VIBRATION
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alarmService = NotificationChannel(ALARM_SERVICE_CHANNEL, "Alarm berbunyi", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tampil selama alarm berbunyi, sampai server mencatat ada yang menangani."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val alerts = NotificationChannel(ALERT_CHANNEL, "Alert dan pengingat", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Early Alert, pengingat, dan notifikasi uji."
            enableVibration(true)
            vibrationPattern = VIBRATION
        }
        manager.createNotificationChannels(listOf(alarm, alarmService, alerts))
    }

    fun canShow(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(alert: PushMessage.Alert): Boolean {
        val channelId = if (alert.alarm) ALARM_CHANNEL else ALERT_CHANNEL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!canShow(channelId)) return false

        val open = PendingIntent.getActivity(
            context,
            alert.tag.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (alert.alarm) R.drawable.ic_siren else R.drawable.ic_bell_ring)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (alert.alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .build()
        if (alert.alarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT

        NotificationManagerCompat.from(context).notify(alert.tag, NOTIFICATION_ID, notification)
        return true
    }

    fun alarmServiceNotification(alarms: List<ActiveAlarm>): Notification {
        val latest = alarms.lastOrNull()
        val alarmScreen = PendingIntent.getActivity(
            context,
            0,
            AlarmActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, ALARM_SERVICE_CHANNEL)
            .setSmallIcon(R.drawable.ic_siren)
            .setContentTitle(latest?.title ?: "SIGAP · Alarm")
            .setContentText(latest?.body.orEmpty())
            .setSubText(if (alarms.size > 1) "${alarms.size} alarm berbunyi" else null)
            .setContentIntent(alarmScreen)
            .setFullScreenIntent(alarmScreen, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun cancel(tag: String) = manager.cancel(tag, NOTIFICATION_ID)

    fun cancelAll() = manager.cancelAll()

    fun cancelSettled(ids: Set<String>) {
        if (ids.isEmpty()) return
        manager.activeNotifications
            .filter { it.id == NOTIFICATION_ID && it.tag in ids }
            .forEach { manager.cancel(it.tag, NOTIFICATION_ID) }
    }

    companion object {
        const val ALARM_CHANNEL = "alarm_v1"
        const val ALARM_SERVICE_CHANNEL = "alarm_service_v1"
        const val ALERT_CHANNEL = "alert_v1"
        private const val NOTIFICATION_ID = 1
        private val VIBRATION = longArrayOf(0, 600, 200, 600, 200, 600)
    }
}
