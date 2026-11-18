package fit.sigap.app.core.push

import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import fit.sigap.app.support.tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PushRegistrarTest {
    private val server = MockWebServer()
    private val store = FakeTokenStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrations = AtomicInteger()
    private val tokenCalls = AtomicInteger()
    private var tokenFailures = 0
    private var registration: (Int) -> MockResponse = { envelope("""{"id":"s1","confirmed":false}""") }
    private lateinit var session: SessionRepository
    private lateinit var registrar: PushRegistrar

    @Before
    fun setUp() {
        server.dispatcher = Routes { request -> route(request) }
        server.start()
        val base = server.url("/").toString()
        session = SessionRepository(createAuthApi(base), store)
        val push = PushRepository(createSigapApi(base, session)) {
            if (tokenCalls.incrementAndGet() <= tokenFailures) error("SERVICE_NOT_AVAILABLE")
            TOKEN
        }
        registrar = PushRegistrar(session, push, scope, retryDelaysMs = listOf(0, 0))
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return when {
            path.endsWith("auth/pair") -> envelope(tokens("a1", "r1"))
            path.endsWith("auth/me") -> envelope("""{"id":"u1","full_name":"Budi","roles":["CODE_BLUE_TEAM"]}""")
            path.endsWith("native-registrations") -> registration(registrations.incrementAndGet())
            else -> error(404, "RESOURCE_NOT_FOUND", path)
        }
    }

    private fun nextRegistration(timeoutMs: Long = 2_000): RecordedRequest? {
        while (true) {
            val request = server.takeRequest(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            if (request.url.encodedPath.endsWith("native-registrations")) return request
        }
    }

    @Test
    fun `sesi yang menjadi aktif mengunggah token dengan access token sesi itu`() = runBlocking {
        registrar.start()
        session.pair("pairing-token")

        val request = nextRegistration()

        assertEquals("Bearer a1", request?.headers?.get("Authorization"))
        assertTrue(request!!.body!!.utf8().contains("\"registration_id\":\"$TOKEN\""))
    }

    @Test
    fun `token baru tanpa sesi tersimpan tidak dikirim`() {
        registrar.onNewToken()

        assertNull(nextRegistration(timeoutMs = 500))
    }

    @Test
    fun `token baru dengan sesi tersimpan dikirim walau layar belum memulihkan sesi`() {
        store.refreshToken = "r0"

        registrar.onNewToken()

        assertTrue(nextRegistration() != null)
    }

    @Test
    fun `token yang belum tersedia diulang sampai terkirim`() {
        store.refreshToken = "r0"
        tokenFailures = 2

        registrar.onNewToken()

        assertEquals(1, settledCount())
        assertEquals(3, tokenCalls.get())
    }

    @Test
    fun `penolakan server tidak diulang`() {
        store.refreshToken = "r0"
        registration = { error(502, "PROVIDER_UNAVAILABLE", "Server ini belum mengirim notifikasi ke aplikasi Android.") }

        registrar.onNewToken()

        assertEquals(1, settledCount())
    }

    private fun settledCount(quietMs: Long = 500, timeoutMs: Long = 5_000): Int {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = -1
        var stableSince = System.currentTimeMillis()
        while (System.currentTimeMillis() < deadline) {
            val now = registrations.get()
            if (now != last) {
                last = now
                stableSince = System.currentTimeMillis()
            } else if (now > 0 && System.currentTimeMillis() - stableSince >= quietMs) {
                return now
            }
            Thread.sleep(20)
        }
        return last
    }

    private companion object {
        const val TOKEN = "fcm-token-0123456789abcdef"
    }
}
