package fit.sigap.app.core.model

import java.time.Instant

enum class DutyRole(val label: String) {
    CODE_BLUE_TEAM("Tim Code Blue"),
    NURSE("Perawat"),
}

data class Shift(val wardId: String, val wardName: String, val startedAt: Instant)

enum class ConnectionState { LIVE, POLLING }

data class Readiness(
    val notificationsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = true,
    val pushConfirmed: Boolean = false,
    val fullScreenAllowed: Boolean = true,
    val alarmVolumeAudible: Boolean = true,
    val vendorGuidance: String? = null,
) {
    val blocked: Boolean get() = !notificationsAllowed || !batteryUnrestricted || !pushConfirmed
    val warningCount: Int
        get() = listOf(fullScreenAllowed, alarmVolumeAudible).count { !it }
}
