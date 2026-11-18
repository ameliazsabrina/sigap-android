package fit.sigap.app.core.data

import fit.sigap.app.core.model.AlertLevel
import fit.sigap.app.core.model.AlertPatient
import fit.sigap.app.core.model.AlertSource
import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.EarlyAlertStatus
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.IncidentLocation
import fit.sigap.app.core.model.IncidentPatient
import fit.sigap.app.core.model.IncidentStatus
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.core.model.Shift
import fit.sigap.app.core.model.VitalReading
import fit.sigap.app.core.network.EarlyAlertDto
import fit.sigap.app.core.network.IncidentDto
import fit.sigap.app.core.network.LocationDto
import fit.sigap.app.core.network.PatientDto
import fit.sigap.app.core.network.ShiftDto
import fit.sigap.app.core.network.VitalObservationDto
import fit.sigap.app.core.network.contractError
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException

private val NoAllergy = Regex("^tidak ada$", RegexOption.IGNORE_CASE)

private fun instant(value: String): Instant = try {
    Instant.parse(value)
} catch (_: DateTimeParseException) {
    throw contractError()
}

private inline fun <reified T : Enum<T>> enumOf(value: String): T =
    enumValues<T>().firstOrNull { it.name == value } ?: throw contractError()

private fun LocationDto.toDomain() = IncidentLocation(name, building, floor, room, bed)

fun ShiftDto.toDomain() = Shift(ward.id, ward.name, instant(startedAt))

fun IncidentDto.toDomain(currentUserId: String) = Incident(
    id = id,
    number = incidentNumber,
    status = enumOf<IncidentStatus>(incidentStatus),
    location = location.toDomain(),
    patient = IncidentPatient(patient.id, patient.displayName, patient.simulationCode, patient.location.wardId),
    triggerSummary = triggerReason.summary,
    createdAt = instant(createdAt),
    acknowledgedAt = acknowledgedAt?.let(::instant),
    acknowledgedByMe = acknowledgedBy != null && acknowledgedBy == currentUserId,
    arrivedAt = arrivedAt?.let(::instant),
    resolvedAt = resolvedAt?.let(::instant),
)

fun EarlyAlertDto.toDomain(today: LocalDate = LocalDate.now()) = EarlyAlert(
    id = id,
    status = enumOf<EarlyAlertStatus>(alertStatus),
    patient = patient.toAlertPatient(today),
    source = enumOf<AlertSource>(source),
    level = reason.alertLevel?.let { enumOf<AlertLevel>(it) },
    message = reason.alertMessage,
    test = test,
    criticalIndication = criticalIndication,
    createdAt = instant(createdAt),
    checkedAt = checkedAt?.let(::instant),
    closedAt = closedAt?.let(::instant),
)

private fun PatientDto.toAlertPatient(today: LocalDate): AlertPatient {
    val allergy = profile.allergies?.trim()?.takeUnless { it.isEmpty() || NoAllergy.matches(it) }
    return AlertPatient(
        id = id,
        displayName = displayName,
        locationName = location.name,
        identityLine = listOfNotNull(
            when (profile.sex) {
                "MALE" -> "Laki-laki"
                "FEMALE" -> "Perempuan"
                else -> null
            },
            ageFrom(profile.birthDate, today)?.let { "$it th" },
            when (profile.admissionSource) {
                "EMERGENCY" -> "IGD"
                "OUTPATIENT" -> "Poliklinik"
                "TRANSFER" -> "Pindahan ruang"
                "POST_OPERATIVE" -> "Pasca operasi"
                else -> null
            },
        ).joinToString(" · ").ifEmpty { null },
        dnr = profile.resuscitationStatus == "DNR",
        allergy = allergy,
        oxygen = profile.oxygenSupport == "OXYGEN",
        spo2Scale2 = profile.spo2Scale2,
        wardId = location.wardId,
    )
}

private fun ageFrom(birthDate: String?, today: LocalDate): Int? {
    val birth = birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return Period.between(birth, today).years.takeIf { !birth.isAfter(today) }
}

fun List<VitalObservationDto>.toPatientVitals(): PatientVitals {
    fun reading(label: String, unit: String, value: (VitalObservationDto) -> Double?): VitalReading {
        val found = firstOrNull { it.dataQuality == VALID && value(it) != null }
        return VitalReading(label, unit, found?.let(value), found?.let { instant(it.recordedAt) })
    }
    return PatientVitals(
        readings = listOf(
            reading("Denyut jantung", "bpm") { it.heartRate },
            reading("Laju napas", "/menit") { it.respiratoryRate },
            reading("Saturasi oksigen", "%") { it.spo2 },
            reading("Tekanan darah sistolik", "mmHg") { it.systolicBp },
        ),
        latestInvalid = firstOrNull()?.let { it.dataQuality != VALID } ?: false,
    )
}

private const val VALID = "VALID"
