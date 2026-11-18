package fit.sigap.app.core.model

import java.time.Instant

data class VitalReading(val label: String, val unit: String, val value: Double?, val recordedAt: Instant?)

data class PatientVitals(val readings: List<VitalReading>, val latestInvalid: Boolean = false)
