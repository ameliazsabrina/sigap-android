package fit.sigap.app.core.data

import fit.sigap.app.core.model.AlertLevel
import fit.sigap.app.core.model.AlertSource
import fit.sigap.app.core.model.IncidentStatus
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.ApiJson
import fit.sigap.app.core.network.EarlyAlertDto
import fit.sigap.app.core.network.Envelope
import fit.sigap.app.core.network.IncidentDto
import fit.sigap.app.core.network.VitalObservationDto
import fit.sigap.app.support.PATIENT_JSON
import fit.sigap.app.support.incidentJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MappersTest {
    private fun incident(json: String) = ApiJson.decodeFromString<IncidentDto>(json)

    @Test
    fun `incident dipetakan dari bentuk jawaban API`() {
        val mapped = incident(
            incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u1"),
        ).toDomain("u1")

        assertEquals(IncidentStatus.ACKNOWLEDGED, mapped.status)
        assertEquals("CB-1", mapped.number)
        assertEquals("Dipanggil perawat", mapped.triggerSummary)
        assertEquals("w1", mapped.patient.wardId)
        assertEquals(Instant.parse("2026-11-05T08:01:00Z"), mapped.acknowledgedAt)
        assertTrue(mapped.acknowledgedByMe)
    }

    @Test
    fun `disanggupi orang lain bukan milik saya`() {
        val mapped = incident(
            incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u2"),
        ).toDomain("u1")

        assertFalse(mapped.acknowledgedByMe)
    }

    @Test
    fun `status yang tidak dikenali menjadi galat, bukan disembunyikan`() {
        val dto = incident(incidentJson(status = "STATUS_BARU"))

        assertThrows(ApiException::class.java) { dto.toDomain("u1") }
    }

    @Test
    fun `alert memuat identitas dan menyaring alergi tidak ada`() {
        val dto = ApiJson.decodeFromString<EarlyAlertDto>(
            """{"id":"a1","source":"NURSE_CALL","patient":$PATIENT_JSON,"alert_status":"SENT",
            "reason":{"alert_level":"HIGH","alert_message":"RR 24"},"critical_indication":true,"test":false,
            "checked_at":null,"created_at":"2026-11-05T08:00:00.000Z","closed_at":null}""",
        )

        val mapped = dto.toDomain(today = LocalDate.parse("2026-11-05"))

        assertEquals(AlertSource.NURSE_CALL, mapped.source)
        assertEquals(AlertLevel.HIGH, mapped.level)
        assertEquals("Perempuan · 58 th · IGD", mapped.patient.identityLine)
        assertNull(mapped.patient.allergy)
        assertTrue(mapped.patient.dnr)
        assertTrue(mapped.patient.oxygen)
        assertTrue(mapped.patient.spo2Scale2)
        assertTrue(mapped.isUnchecked)
    }

    @Test
    fun `nilai vital pecahan dari server terbaca apa adanya`() {
        val json = """{"data":[{"id":"o1","heart_rate":88.5,"respiratory_rate":18,"spo2":97.25,"systolic_bp":null,
            "diastolic_bp":80,"recorded_at":"2026-11-05T08:01:00.000Z","data_quality":"VALID"}]}"""

        val vitals = ApiJson.decodeFromString<Envelope<List<VitalObservationDto>>>(json).data.toPatientVitals()

        assertEquals(88.5, vitals.readings.first { it.label == "Denyut jantung" }.value)
        assertEquals(18.0, vitals.readings.first { it.label == "Laju napas" }.value)
        assertEquals(97.25, vitals.readings.first { it.label == "Saturasi oksigen" }.value)
        assertNull(vitals.readings.first { it.label == "Tekanan darah sistolik" }.value)
    }

    @Test
    fun `observasi invalid tidak menimpa angka terakhir yang valid`() {
        val vitals = listOf(
            VitalObservationDto(heartRate = 200.0, recordedAt = "2026-11-05T08:02:00Z", dataQuality = "INVALID"),
            VitalObservationDto(heartRate = 90.0, spo2 = 97.0, recordedAt = "2026-11-05T08:01:00Z", dataQuality = "VALID"),
        ).toPatientVitals()

        assertTrue(vitals.latestInvalid)
        assertEquals(90.0, vitals.readings.first { it.label == "Denyut jantung" }.value)
        assertEquals(Instant.parse("2026-11-05T08:01:00Z"), vitals.readings.first().recordedAt)
        assertNull(vitals.readings.first { it.label == "Laju napas" }.value)
    }
}
