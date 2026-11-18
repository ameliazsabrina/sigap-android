package fit.sigap.app.core.push

import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.PATIENT_JSON
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import fit.sigap.app.support.incidentJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushRepositoryTest {
    private val server = MockWebServer()
    private var respond: (RecordedRequest) -> MockResponse = { error(500, "INTERNAL_ERROR", "belum diatur") }
    private var tokens = PushTokenSource { "fcm-token-0123456789abcdef" }
    private lateinit var push: PushRepository

    @Before
    fun setUp() {
        server.dispatcher = Routes { respond(it) }
        server.start()
        val base = server.url("/").toString()
        val api = createSigapApi(base, SessionRepository(createAuthApi(base), FakeTokenStore()))
        push = PushRepository(api) { tokens.token() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun alertJson(checkedAt: String?, status: String) =
        """{"id":"a1","source":"DETECTION","patient":$PATIENT_JSON,"alert_status":"$status",
        "checked_at":${checkedAt?.let { "\"$it\"" } ?: "null"},"created_at":"2026-11-05T08:00:00.000Z"}"""

    @Test
    fun `token yang gagal didapat tidak dikirim ke server`() = runBlocking {
        tokens = PushTokenSource { throw IllegalStateException("SERVICE_NOT_AVAILABLE") }

        val failure = assertThrows(ApiException::class.java) { runBlocking { push.register() } }

        assertEquals(PushRepository.CODE_TOKEN_UNAVAILABLE, failure.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `alarm incident berhenti hanya bila server menyatakan sudah disanggupi`() = runBlocking {
        respond = { envelope(incidentJson(status = "NOTIFICATION_SENT")) }
        assertFalse(push.alarmSettled(AlarmTarget.IncidentTarget("i1")))

        respond = { envelope(incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u2")) }
        assertTrue(push.alarmSettled(AlarmTarget.IncidentTarget("i1")))

        respond = { envelope(incidentJson(status = "CANCELLED")) }
        assertTrue(push.alarmSettled(AlarmTarget.IncidentTarget("i1")))
    }

    @Test
    fun `alarm Early Alert berhenti setelah dicek`() = runBlocking {
        respond = { envelope(alertJson(checkedAt = null, status = "SENT")) }
        assertFalse(push.alarmSettled(AlarmTarget.EarlyAlertTarget("a1")))

        respond = { envelope(alertJson(checkedAt = "2026-11-05T08:01:00.000Z", status = "CHECKED")) }
        assertTrue(push.alarmSettled(AlarmTarget.EarlyAlertTarget("a1")))
    }

    @Test
    fun `404 menghentikan alarm tetapi galat lain tidak`() = runBlocking {
        respond = { error(404, "RESOURCE_NOT_FOUND", "Incident tidak ditemukan.") }
        assertTrue(push.alarmSettled(AlarmTarget.IncidentTarget("i1")))

        respond = { error(500, "INTERNAL_ERROR", "Gagal.") }
        assertThrows(ApiException::class.java) { runBlocking { push.alarmSettled(AlarmTarget.IncidentTarget("i1")) } }
        Unit
    }

    @Test
    fun `tanda terima dikirim ke notifikasi yang disebut`() = runBlocking {
        respond = { envelope("""{"id":"n1","delivery_status":"DELIVERED"}""") }

        push.markReceived("n1")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.url.encodedPath.endsWith("/notifications/n1/received"))
    }
}
