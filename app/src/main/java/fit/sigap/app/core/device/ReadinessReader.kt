package fit.sigap.app.core.device

import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import fit.sigap.app.core.model.Readiness

class ReadinessReader(private val context: Context) {
    fun read(pushConfirmed: Boolean): Readiness {
        val power = context.getSystemService(PowerManager::class.java)
        val audio = context.getSystemService(AudioManager::class.java)
        return Readiness(
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName),
            pushConfirmed = pushConfirmed,
            fullScreenAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
            alarmVolumeAudible = audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0,
            vendorGuidance = vendorGuidance(Build.MANUFACTURER),
        )
    }
}

internal fun vendorGuidance(manufacturer: String): String? {
    val brand = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> "Xiaomi"
        "oppo", "realme" -> "Oppo"
        "vivo" -> "Vivo"
        else -> return null
    }
    return "Di HP $brand, aktifkan Mulai otomatis untuk SIGAP dan pilih \"Tanpa batasan\" pada pengaturan baterai."
}

fun Context.notificationSettingsIntent(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)

fun Context.batteryExemptionIntent(): Intent =
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:$packageName".toUri())

fun Context.fullScreenSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:$packageName".toUri())
    } else {
        notificationSettingsIntent()
    }

fun Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(appSettingsIntent())
    }
}
