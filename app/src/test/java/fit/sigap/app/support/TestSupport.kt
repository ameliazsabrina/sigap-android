package fit.sigap.app.support

import fit.sigap.app.core.session.TokenStore
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest

class FakeTokenStore(var refreshToken: String? = null, var ended: Boolean = false) : TokenStore {
    override suspend fun readRefreshToken() = refreshToken
    override suspend fun writeRefreshToken(token: String) {
        refreshToken = token
    }
    override suspend fun clearRefreshToken() {
        refreshToken = null
    }
    override suspend fun readSessionEnded() = ended
    override suspend fun writeSessionEnded(ended: Boolean) {
        this.ended = ended
    }
}

fun json(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

fun envelope(data: String, code: Int = 200): MockResponse = json("""{"data":$data,"meta":{},"request_id":"r"}""", code)

fun error(code: Int, errorCode: String, message: String): MockResponse =
    json("""{"error":{"code":"$errorCode","message":"$message"},"request_id":"r"}""", code)

fun tokens(access: String, refresh: String): String =
    """{"access_token":"$access","refresh_token":"$refresh","expires_in":900,"user":{"id":"u1","full_name":"Budi","roles":["CODE_BLUE_TEAM"]}}"""

class Routes(private val handler: (RecordedRequest) -> MockResponse) : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse = handler(request)
}

const val PATIENT_JSON = """{"id":"p1","simulation_code":"SIM-1","display_name":"SIM Pasien A",
    "location":{"id":"l1","code":"B1","name":"ICU Bed 01","building":"A","floor":"3","room":"ICU","bed":"01","ward_id":"w1"},
    "monitor":{"connected":true},
    "profile":{"birth_date":"1968-01-15","sex":"FEMALE","admission_source":"EMERGENCY","oxygen_support":"OXYGEN",
    "spo2_scale2":true,"allergies":"Tidak ada","resuscitation_status":"DNR"}}"""

fun incidentJson(
    id: String = "i1",
    status: String = "NOTIFICATION_SENT",
    acknowledgedAt: String? = null,
    acknowledgedBy: String? = null,
): String = """{"id":"$id","incident_number":"CB-1","patient":$PATIENT_JSON,
    "location":{"name":"ICU Bed 01","ward_id":"w1"},"incident_status":"$status",
    "trigger_reason":{"source":"MANUAL","summary":"Dipanggil perawat"},
    "acknowledged_by":${acknowledgedBy?.let { "\"$it\"" } ?: "null"},
    "acknowledged_at":${acknowledgedAt?.let { "\"$it\"" } ?: "null"},
    "arrived_at":null,"resolved_at":null,"notifications":[],"created_at":"2026-11-05T08:00:00.000Z"}"""
