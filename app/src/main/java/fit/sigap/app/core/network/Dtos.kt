package fit.sigap.app.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Envelope<T>(val data: T)

@Serializable
data class PairRequest(val token: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UserDto(
    val id: String,
    val fullName: String,
    val roles: List<String> = emptyList(),
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class WardDto(val id: String, val name: String)

@Serializable
data class ShiftDto(val ward: WardDto, val startedAt: String)

@Serializable
data class MyShiftDto(val shift: ShiftDto? = null)

@Serializable
data class CheckInRequest(val code: String)

@Serializable
class EmptyRequest

@Serializable
data class LocationDto(
    val name: String,
    val building: String? = null,
    val floor: String? = null,
    val room: String? = null,
    val bed: String? = null,
    val wardId: String? = null,
)

@Serializable
data class PatientProfileDto(
    val birthDate: String? = null,
    val sex: String? = null,
    val admissionSource: String? = null,
    val oxygenSupport: String? = null,
    val spo2Scale2: Boolean = false,
    val allergies: String? = null,
    val resuscitationStatus: String? = null,
)

@Serializable
data class MonitorDto(val connected: Boolean = false)

@Serializable
data class PatientDto(
    val id: String,
    val simulationCode: String,
    val displayName: String,
    val location: LocationDto,
    val monitor: MonitorDto = MonitorDto(),
    val profile: PatientProfileDto = PatientProfileDto(),
)

@Serializable
data class TriggerReasonDto(val summary: String = "")

@Serializable
data class IncidentDto(
    val id: String,
    val incidentNumber: String,
    val patient: PatientDto,
    val location: LocationDto,
    val incidentStatus: String,
    val triggerReason: TriggerReasonDto = TriggerReasonDto(),
    val acknowledgedBy: String? = null,
    val acknowledgedAt: String? = null,
    val arrivedAt: String? = null,
    val resolvedAt: String? = null,
    val createdAt: String,
)

@Serializable
data class AcknowledgeRequest(val clientEventId: String)

@Serializable
data class AlertReasonDto(val alertLevel: String? = null, val alertMessage: String? = null)

@Serializable
data class EarlyAlertDto(
    val id: String,
    val source: String,
    val patient: PatientDto,
    val alertStatus: String,
    val reason: AlertReasonDto = AlertReasonDto(),
    val criticalIndication: Boolean = false,
    val test: Boolean = false,
    val checkedAt: String? = null,
    val createdAt: String,
    val closedAt: String? = null,
)

@Serializable
data class ReasonRequest(val reason: String)

@Serializable
data class ResponseSettingDto(val key: String, val value: Long)

@Serializable
data class PushStatusDto(val subscribed: Boolean = false, val confirmed: Boolean = false)

@Serializable
data class NativeRegistrationRequest(val registrationId: String)

@Serializable
data class PushRegistrationDto(val id: String, val confirmed: Boolean)

@Serializable
data class PushTestDto(val sent: Boolean, val reason: String? = null)

@Serializable
data class VitalObservationDto(
    val heartRate: Double? = null,
    val respiratoryRate: Double? = null,
    val spo2: Double? = null,
    val systolicBp: Double? = null,
    val recordedAt: String,
    val dataQuality: String,
)

@Serializable
data class DocumentationDto(
    val id: String,
    val documentationStatus: String,
    val userConfirmedInformation: JsonObject? = null,
    val unknownInformation: List<String> = emptyList(),
    val notes: String? = null,
    val completedAt: String? = null,
    val version: Int,
)

@Serializable
data class DocumentationRequest(
    val userConfirmedInformation: JsonObject,
    val unknownInformation: List<String>,
    val notes: String,
    val expectedVersion: Int? = null,
)
