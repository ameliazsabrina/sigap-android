package fit.sigap.app.core.data

import fit.sigap.app.core.model.EarlyAlert
import fit.sigap.app.core.model.Incident
import fit.sigap.app.core.model.PatientVitals
import fit.sigap.app.core.model.Shift
import fit.sigap.app.core.network.AcknowledgeRequest
import fit.sigap.app.core.network.CheckInRequest
import fit.sigap.app.core.network.ReasonRequest
import fit.sigap.app.core.network.SigapApi
import fit.sigap.app.core.network.apiCall
import java.time.Duration

data class WardPatients(val total: Int, val monitorDisconnected: Int)

class DutyRepository(private val api: SigapApi) {
    suspend fun myShift(): Shift? = apiCall { api.myShift() }.data.shift?.toDomain()

    suspend fun checkIn(code: String): Shift = apiCall { api.checkIn(CheckInRequest(code)) }.data.toDomain()

    suspend fun checkOut() {
        apiCall { api.checkOut() }
    }

    suspend fun arrivalLimit(): Duration? = apiCall { api.responseSettings() }.data
        .firstOrNull { it.key == "arrival_reminder_seconds" }
        ?.let { Duration.ofSeconds(it.value) }

    suspend fun wardPatients(wardId: String): WardPatients {
        val rows = apiCall { api.patients() }.data.filter { it.location.wardId == wardId }
        return WardPatients(rows.size, rows.count { !it.monitor.connected })
    }

    suspend fun vitals(patientId: String): PatientVitals =
        apiCall { api.vitals(patientId) }.data.toPatientVitals()

    suspend fun incidents(userId: String): List<Incident> =
        apiCall { api.incidents() }.data.map { it.toDomain(userId) }

    suspend fun earlyAlerts(): List<EarlyAlert> = apiCall { api.earlyAlerts() }.data.map { it.toDomain() }

    suspend fun incident(id: String, userId: String): Incident = apiCall { api.incident(id) }.data.toDomain(userId)

    suspend fun earlyAlert(id: String): EarlyAlert = apiCall { api.earlyAlert(id) }.data.toDomain()

    suspend fun openIncident(id: String, key: String, userId: String): Incident =
        apiCall { api.openIncident(id, key) }.data.toDomain(userId)

    suspend fun acknowledgeIncident(id: String, key: String, userId: String): Incident =
        apiCall { api.acknowledgeIncident(id, key, AcknowledgeRequest(key)) }.data.toDomain(userId)

    suspend fun arriveIncident(id: String, key: String, userId: String): Incident =
        apiCall { api.arriveIncident(id, key) }.data.toDomain(userId)

    suspend fun resolveIncident(id: String, key: String, userId: String): Incident =
        apiCall { api.resolveIncident(id, key) }.data.toDomain(userId)

    suspend fun checkAlert(id: String, key: String): EarlyAlert = apiCall { api.checkAlert(id, key) }.data.toDomain()

    suspend fun closeAlert(id: String, key: String, reason: String) {
        apiCall { api.closeAlert(id, key, ReasonRequest(reason)) }
    }

    suspend fun escalateAlert(id: String, key: String, reason: String, userId: String): Incident =
        apiCall { api.escalateAlert(id, key, ReasonRequest(reason)) }.data.toDomain(userId)
}
