package fit.sigap.app.core.push

sealed interface AlarmTarget {
    val id: String

    data class IncidentTarget(override val id: String) : AlarmTarget
    data class EarlyAlertTarget(override val id: String) : AlarmTarget
}

sealed interface PushMessage {
    data class Alert(
        val kind: String,
        val title: String,
        val body: String,
        val notificationId: String?,
        val incidentId: String?,
        val earlyAlertId: String?,
    ) : PushMessage {
        val tag: String get() = incidentId ?: earlyAlertId ?: notificationId ?: kind

        val alarm: Boolean get() = kind in ALARM_KINDS

        val alarmTarget: AlarmTarget?
            get() = incidentId?.let(AlarmTarget::IncidentTarget) ?: earlyAlertId?.let(AlarmTarget::EarlyAlertTarget)
    }

    data class AlarmStop(val target: AlarmTarget) : PushMessage

    companion object {
        private val ALARM_KINDS = setOf("CODE_BLUE", "EARLY_ALERT_CRITICAL")

        fun parse(data: Map<String, String>): PushMessage? {
            val kind = data["kind"]?.takeIf { it.isNotBlank() } ?: return null
            val incidentId = data["incident_id"]?.takeIf { it.isNotBlank() }
            val earlyAlertId = data["early_alert_id"]?.takeIf { it.isNotBlank() }
            if (kind == "ALARM_STOP") {
                val target = incidentId?.let(AlarmTarget::IncidentTarget)
                    ?: earlyAlertId?.let(AlarmTarget::EarlyAlertTarget)
                    ?: return null
                return AlarmStop(target)
            }
            return Alert(
                kind = kind,
                title = data["title"]?.takeIf { it.isNotBlank() } ?: "SIGAP",
                body = data["body"].orEmpty(),
                notificationId = data["notification_id"]?.takeIf { it.isNotBlank() },
                incidentId = incidentId,
                earlyAlertId = earlyAlertId,
            )
        }
    }
}
