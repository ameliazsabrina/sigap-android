package fit.sigap.app.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fit.sigap.app.core.designsystem.theme.SigapAppTheme
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
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.core.model.Shift
import fit.sigap.app.core.model.VitalReading
import java.time.Instant

internal val PreviewNow: Instant = Instant.parse("2026-11-17T14:30:00Z")

internal fun minutesAgo(minutes: Long): Instant = PreviewNow.minusSeconds(minutes * 60)

@Preview(name = "Phone", device = "spec:width=411dp,height=914dp", showBackground = true)
@Preview(name = "Font 1.5x", device = "spec:width=411dp,height=914dp", fontScale = 1.5f, showBackground = true)
annotation class SigapPreviews

@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) = SigapAppTheme(content)

internal object PreviewData {
    const val NURSE = "Ns. Dewi Anggraini"
    const val RESPONDER = "Ns. Fajar Hidayat"

    val shift = Shift(wardId = "ward-melati", wardName = "Rawat Inap Melati", startedAt = minutesAgo(190))

    val readyPhone = Readiness(pushConfirmed = true)
    val notificationsBlocked = Readiness(notificationsAllowed = false, pushConfirmed = true)
    val pushMissing = Readiness(pushConfirmed = false)
    val warnings = Readiness(pushConfirmed = true, alarmVolumeAudible = false, fullScreenAllowed = false)

    private val siti = AlertPatient(
        id = "p-1",
        displayName = "Ny. Siti Aminah",
        locationName = "MLT-301A",
        identityLine = "Perempuan · 67 th · Pasca operasi",
        dnr = false,
        allergy = "Penisilin",
        oxygen = true,
    )
    private val ratna = AlertPatient(
        id = "p-2",
        displayName = "Ny. Ratna Sari",
        locationName = "MLT-302A",
        identityLine = "Perempuan · 54 th · IGD",
    )
    private val endang = AlertPatient(
        id = "p-3",
        displayName = "Ny. Endang Wahyuni",
        locationName = "MLT-304B",
        identityLine = "Perempuan · 81 th · Pindahan ruang",
        dnr = true,
        spo2Scale2 = true,
    )

    val uncheckedAlert = EarlyAlert(
        id = "a-1",
        status = EarlyAlertStatus.DELIVERED,
        patient = siti,
        source = AlertSource.DETECTION,
        level = AlertLevel.SINGLE_RED,
        message = "Single Red · Laju napas 8 /menit",
        createdAt = minutesAgo(2),
    )
    val secondUncheckedAlert = EarlyAlert(
        id = "a-2",
        status = EarlyAlertStatus.SENT,
        patient = ratna,
        source = AlertSource.NURSE_CALL,
        createdAt = minutesAgo(1),
    )
    val checkedAlert = EarlyAlert(
        id = "a-3",
        status = EarlyAlertStatus.CHECKED,
        patient = endang,
        source = AlertSource.DETECTION,
        level = AlertLevel.HIGH,
        message = "High · Skor agregat 7 (4/7 parameter)",
        createdAt = minutesAgo(9),
        checkedAt = minutesAgo(6),
    )
    val closedAlert = checkedAlert.copy(
        id = "a-4",
        status = EarlyAlertStatus.CLOSED,
        closedAt = minutesAgo(3),
    )
    val escalatedAlert = checkedAlert.copy(
        id = "a-5",
        status = EarlyAlertStatus.ESCALATED,
        closedAt = minutesAgo(4),
    )
    val testAlert = secondUncheckedAlert.copy(id = "a-6", test = true, source = AlertSource.DATA_MONITOR)

    val vitals = PatientVitals(
        readings = listOf(
            VitalReading("Denyut jantung", "bpm", 118.0, minutesAgo(1)),
            VitalReading("Laju napas", "/menit", 8.0, minutesAgo(1)),
            VitalReading("Saturasi oksigen", "%", 91.0, minutesAgo(1)),
            VitalReading("Tekanan darah sistolik", "mmHg", 96.0, minutesAgo(12)),
        ),
    )
    val staleVitals = PatientVitals(
        readings = listOf(
            VitalReading("Denyut jantung", "bpm", 102.0, minutesAgo(48)),
            VitalReading("Laju napas", "/menit", null, null),
            VitalReading("Saturasi oksigen", "%", 94.0, minutesAgo(48)),
            VitalReading("Tekanan darah sistolik", "mmHg", null, null),
        ),
        latestInvalid = true,
    )

    private val incidentLocation = IncidentLocation(
        name = "Rawat Inap Melati",
        building = "Gedung A",
        floor = "3",
        room = "301",
        bed = "MLT-301A",
    )

    val unacknowledgedIncident = Incident(
        id = "i-1",
        number = "CB-20261117-0007",
        status = IncidentStatus.DELIVERED,
        location = incidentLocation,
        patient = IncidentPatient("p-1", "Ny. Siti Aminah", "RM-SIM-000101"),
        triggerSummary = "Dipanggil perawat dari early alert",
        createdAt = minutesAgo(1),
    )
    val acknowledgedByMe = unacknowledgedIncident.copy(
        id = "i-2",
        status = IncidentStatus.ACKNOWLEDGED,
        acknowledgedAt = minutesAgo(1),
        acknowledgedByMe = true,
    )
    val acknowledgedByOther = acknowledgedByMe.copy(id = "i-3", acknowledgedByMe = false)
    val arrived = acknowledgedByMe.copy(id = "i-4", arrivedAt = PreviewNow.minusSeconds(20))
    val awaitingDocumentation = arrived.copy(
        id = "i-5",
        number = "CB-20261117-0005",
        status = IncidentStatus.DOCUMENTATION_PENDING,
        location = IncidentLocation(name = "Intensive Care Unit", building = "Gedung B", bed = "ICU-02"),
        patient = IncidentPatient("p-9", "Ny. Maya Puspita", "RM-SIM-000109"),
        createdAt = minutesAgo(75),
        acknowledgedAt = minutesAgo(74),
        arrivedAt = minutesAgo(70),
        resolvedAt = minutesAgo(40),
    )
    val completed = awaitingDocumentation.copy(id = "i-6", status = IncidentStatus.COMPLETED)
}
