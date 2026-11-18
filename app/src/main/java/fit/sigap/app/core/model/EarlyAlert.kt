package fit.sigap.app.core.model

import java.time.Instant

enum class EarlyAlertStatus { CREATED, CHECKED, SENT, DELIVERED, OPENED, ASSESSED, CLOSED, ESCALATED, FAILED, UNKNOWN }

enum class AlertSource(val label: String) {
    DETECTION("Deteksi"),
    NURSE_CALL("Nurse call"),
    DATA_MONITOR("Data monitor"),
}

enum class AlertLevel(val label: String) {
    SINGLE_RED("Single Red"),
    MEDIUM("Medium"),
    HIGH("High"),
}

data class AlertPatient(
    val id: String,
    val displayName: String,
    val locationName: String,
    val identityLine: String? = null,
    val dnr: Boolean = false,
    val allergy: String? = null,
    val oxygen: Boolean = false,
    val spo2Scale2: Boolean = false,
    val wardId: String? = null,
)

data class EarlyAlert(
    val id: String,
    val status: EarlyAlertStatus,
    val patient: AlertPatient,
    val source: AlertSource,
    val level: AlertLevel? = null,
    val message: String? = null,
    val test: Boolean = false,
    val criticalIndication: Boolean = false,
    val createdAt: Instant,
    val checkedAt: Instant? = null,
    val closedAt: Instant? = null,
) {
    val isFinished: Boolean
        get() = status == EarlyAlertStatus.CLOSED || status == EarlyAlertStatus.ESCALATED

    val isUnchecked: Boolean get() = checkedAt == null && !isFinished

    val silencesAlarm: Boolean get() = !isUnchecked

    val isDecidable: Boolean
        get() = status == EarlyAlertStatus.CHECKED || status == EarlyAlertStatus.ASSESSED
}

fun List<EarlyAlert>.openSorted(): List<EarlyAlert> =
    filterNot { it.isFinished }.sortedWith(compareByDescending<EarlyAlert> { it.isUnchecked }.thenBy { it.createdAt })

fun List<EarlyAlert>.firstWaiting(): EarlyAlert? = firstOrNull { it.isUnchecked }

fun List<EarlyAlert>.outsideActiveCodeBlue(incidents: List<Incident>): List<EarlyAlert> {
    val inCodeBlue = incidents.filterNot { it.isHandled }.map { it.patient.id }.toSet()
    return filterNot { it.patient.id in inCodeBlue }
}
