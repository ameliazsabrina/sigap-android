package fit.sigap.app.core.format

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val Indonesian: Locale = Locale.forLanguageTag("id-ID")
private val ClockSeconds = DateTimeFormatter.ofPattern("HH.mm.ss", Indonesian)
private val Clock = DateTimeFormatter.ofPattern("HH.mm", Indonesian)
private val DateTime = DateTimeFormatter.ofPattern("dd MMM, HH.mm", Indonesian)

fun formatTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = ClockSeconds.format(at.atZone(zone))

fun formatClock(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = Clock.format(at.atZone(zone))

fun formatDateTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = DateTime.format(at.atZone(zone))

fun formatRelative(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val seconds = (Duration.between(at, now).toMillis() / 1000.0).roundToLong()
    if (seconds < 10) return "baru saja"
    if (seconds < 60) return "$seconds detik lalu"
    val minutes = (seconds / 60.0).roundToLong()
    if (minutes < 60) return "$minutes menit lalu"
    val hours = (minutes / 60.0).roundToLong()
    if (hours < 24) return "$hours jam lalu"
    return formatDateTime(at, zone)
}

fun formatVital(value: Double): String = BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

fun formatDuration(from: Instant?, to: Instant?): String {
    if (from == null || to == null) return "tidak tercatat"
    val total = Duration.between(from, to).seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "$hours j $minutes m" else "%02d:%02d".format(minutes, seconds)
}
