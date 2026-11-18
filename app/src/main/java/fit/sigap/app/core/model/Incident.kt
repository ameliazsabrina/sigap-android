package fit.sigap.app.core.model

import java.time.Duration
import java.time.Instant

enum class IncidentStatus {
    ACTIVE,
    NOTIFICATION_PENDING,
    NOTIFICATION_SENT,
    DELIVERED,
    FAILED,
    UNACKNOWLEDGED,
    OPENED,
    ACKNOWLEDGED,
    RESOLVED,
    DOCUMENTATION_PENDING,
    COMPLETED,
    CANCELLED,
}

data class IncidentLocation(
    val name: String,
    val building: String? = null,
    val floor: String? = null,
    val room: String? = null,
    val bed: String? = null,
) {
    val detailLine: String
        get() = listOfNotNull(
            building?.let { if (it.startsWith("Gedung", ignoreCase = true)) it else "Gedung $it" },
            floor?.let { "Lantai $it" },
            room?.let { "Ruang $it" },
            bed?.let { "Bed $it" },
        ).joinToString(" · ")
}

data class IncidentPatient(
    val id: String,
    val displayName: String,
    val simulationCode: String,
    val wardId: String? = null,
)

data class Incident(
    val id: String,
    val number: String,
    val status: IncidentStatus,
    val location: IncidentLocation,
    val patient: IncidentPatient,
    val triggerSummary: String,
    val createdAt: Instant,
    val acknowledgedAt: Instant? = null,
    val acknowledgedByMe: Boolean = false,
    val arrivedAt: Instant? = null,
    val resolvedAt: Instant? = null,
) {
    val isAcknowledged: Boolean get() = acknowledgedAt != null

    val awaitsDocumentation: Boolean
        get() = status == IncidentStatus.RESOLVED || status == IncidentStatus.DOCUMENTATION_PENDING

    val isClosed: Boolean
        get() = status == IncidentStatus.COMPLETED || status == IncidentStatus.CANCELLED

    val isHandled: Boolean get() = awaitsDocumentation || isClosed

    val silencesAlarm: Boolean get() = isAcknowledged || isHandled

    val attentionRank: Int
        get() = when {
            isClosed -> 2
            isAcknowledged -> 1
            else -> 0
        }

    fun isArrivalOverdue(now: Instant, arrivalLimit: Duration?): Boolean {
        if (arrivalLimit == null || !acknowledgedByMe || arrivedAt != null) return false
        if (status != IncidentStatus.ACKNOWLEDGED) return false
        val acknowledged = acknowledgedAt ?: return false
        return Duration.between(acknowledged, now) >= arrivalLimit
    }

    fun cardStatusLine(overdue: Boolean): String = when {
        awaitsDocumentation -> "respons selesai · dokumentasi belum lengkap"
        !isAcknowledged -> "belum ada yang menuju lokasi"
        arrivedAt != null -> "tim sudah tiba"
        overdue -> "Anda belum tiba · batas waktu lewat"
        else -> "sudah ada yang menuju lokasi"
    }

    val teamStatusLine: String
        get() = when {
            resolvedAt != null -> "Respons dinyatakan selesai"
            arrivedAt != null -> "Tim sudah tiba di lokasi"
            acknowledgedAt != null -> "Sudah ada yang menuju lokasi, belum tiba"
            else -> "Belum ada yang menuju lokasi"
        }
}

fun List<Incident>.activeSorted(): List<Incident> =
    filterNot { it.isHandled }.sortedWith(compareBy({ it.attentionRank }, { it.createdAt }))
