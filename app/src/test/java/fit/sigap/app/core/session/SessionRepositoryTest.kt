package fit.sigap.app.core.session

import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.apiCall
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import fit.sigap.app.support.tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SessionRepositoryTest {
    private val server = MockWebServer()
    private val refreshCalls = AtomicInteger()
    private val store = FakeTokenStore()
    private lateinit var session: SessionRepository
    private val shiftOk = envelope("""{"shift":null}""")
    private val me = envelope("""{"id":"u1","full_name":"Budi","roles":["HEALTHCARE_WORKER"]}""")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun build() {
        session = SessionRepository(createAuthApi(server.url("/").toString()), store)
    }

    private fun api() = createSigapApi(server.url("/").toString(), session)

    @Test
    fun `401 serentak hanya memicu satu refresh dan token hasil rotasi tersimpan`() = runBlocking {
        store.refreshToken = "r0"
        server.dispatcher = Routes { request ->
            val path = request.url.encodedPath
            val bearer = request.headers["Authorization"]
            when {
                path.endsWith("auth/refresh") -> {
                    val call = refreshCalls.incrementAndGet()
                    Thread.sleep(100)
                    envelope(tokens("a$call", "r$call"))
                }
                path.endsWith("auth/me") -> me
                bearer == "Bearer a1" -> shiftOk
                else -> error(401, "AUTHENTICATION_REQUIRED", "x")
            }
        }
        build()
        val api = api()

        (1..8).map { async(Dispatchers.IO) { api.myShift() } }.awaitAll()

        assertEquals(1, refreshCalls.get())
        assertEquals("r1", store.refreshToken)
        assertEquals("a1", session.accessToken)
    }

    @Test
    fun `server menolak refresh mengakhiri sesi dan meminta pasang ulang`() = runBlocking {
        store.refreshToken = "r0"
        server.dispatcher = Routes { error(401, "AUTHENTICATION_REQUIRED", "Sesi berakhir.") }
        build()

        session.restore()

        assertEquals(SessionState.None(ended = true), session.state.value)
        assertNull(store.refreshToken)
        assertTrue(store.ended)
    }

    @Test
    fun `galat jaringan saat refresh mempertahankan sesi`() = runBlocking {
        store.refreshToken = "r0"
        server.dispatcher = Routes {
            MockResponse.Builder().onRequestStart(SocketEffect.ShutdownConnection).build()
        }
        build()

        session.restore()

        assertTrue(session.state.value is SessionState.Unreachable)
        assertEquals("r0", store.refreshToken)
        assertFalse(store.ended)
    }

    @Test
    fun `server sibuk saat refresh tidak mengakhiri sesi`() = runBlocking {
        store.refreshToken = "r0"
        server.dispatcher = Routes { error(429, "RATE_LIMITED", "Terlalu banyak.") }
        build()

        session.restore()

        assertTrue(session.state.value is SessionState.Unreachable)
        assertEquals("r0", store.refreshToken)
    }

    @Test
    fun `refresh yang gagal jaringan saat bertugas muncul sebagai galat jaringan`() = runBlocking {
        store.refreshToken = "r0"
        server.dispatcher = Routes { request ->
            if (request.url.encodedPath.endsWith("auth/refresh")) {
                MockResponse.Builder().onRequestStart(SocketEffect.ShutdownConnection).build()
            } else {
                error(401, "AUTHENTICATION_REQUIRED", "x")
            }
        }
        build()
        val api = api()

        val failure = runCatching { apiCall { api.myShift() } }.exceptionOrNull() as ApiException

        assertTrue(failure.isNetwork)
        assertEquals("r0", store.refreshToken)
    }

    @Test
    fun `pair yang ditolak memakai pesan server dan tidak mencoba refresh`() = runBlocking {
        server.dispatcher = Routes { request ->
            if (request.url.encodedPath.endsWith("auth/refresh")) refreshCalls.incrementAndGet()
            error(401, "AUTHENTICATION_REQUIRED", "Link pairing tidak berlaku.")
        }
        build()

        val failure = assertThrows(ApiException::class.java) { runBlocking { session.pair("token-sekali-pakai-123") } }

        assertEquals("Link pairing tidak berlaku.", failure.message)
        assertEquals(0, refreshCalls.get())
        assertNull(store.refreshToken)
    }

    @Test
    fun `pair berhasil menyimpan refresh token dan menentukan peran dari akun`() = runBlocking {
        store.ended = true
        server.dispatcher = Routes { request ->
            if (request.url.encodedPath.endsWith("auth/me")) me else envelope(tokens("a1", "r1"))
        }
        build()

        val user = session.pair("token-sekali-pakai-123")

        assertEquals(DutyRole.NURSE, user.role)
        assertEquals("r1", store.refreshToken)
        assertFalse(store.ended)
        assertEquals(SessionState.Active(user), session.state.value)
    }

    @Test
    fun `pair tetap sah bila auth me gagal karena token QR sudah terpakai`() = runBlocking {
        server.dispatcher = Routes { request ->
            if (request.url.encodedPath.endsWith("auth/me")) {
                error(500, "INTERNAL_ERROR", "x")
            } else {
                envelope(tokens("a1", "r1"))
            }
        }
        build()

        val user = session.pair("token-sekali-pakai-123")

        assertEquals("Budi", user.name)
        assertEquals(DutyRole.CODE_BLUE_TEAM, user.role)
        assertTrue(session.state.value is SessionState.Active)
    }

    private val loggedOut = envelope("""{"logged_out":true}""")

    private suspend fun activeSession(onLogout: (bearer: String?) -> MockResponse) {
        store.refreshToken = "r0"
        server.dispatcher = Routes { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("auth/refresh") -> {
                    val call = refreshCalls.incrementAndGet()
                    envelope(tokens("a$call", "r$call"))
                }
                path.endsWith("auth/me") -> me
                path.endsWith("auth/logout") -> onLogout(request.headers["Authorization"])
                else -> error(401, "AUTHENTICATION_REQUIRED", "x")
            }
        }
        build()
        session.restore()
        assertTrue(session.state.value is SessionState.Active)
    }

    @Test
    fun `logout mencabut sesi di server lalu membuang sesi lokal tanpa menandainya berakhir`() = runBlocking {
        val bearers = mutableListOf<String?>()
        activeSession { bearer ->
            bearers += bearer
            loggedOut
        }

        session.logout()

        assertEquals(listOf("Bearer a1"), bearers)
        assertEquals(SessionState.None(ended = false), session.state.value)
        assertNull(store.refreshToken)
        assertNull(session.accessToken)
        assertFalse(store.ended)
    }

    @Test
    fun `access token kedaluwarsa diperbarui sekali lalu logout diulang`() = runBlocking {
        val bearers = mutableListOf<String?>()
        activeSession { bearer ->
            bearers += bearer
            if (bearer == "Bearer a1") error(401, "AUTHENTICATION_REQUIRED", "x") else loggedOut
        }

        session.logout()

        assertEquals(listOf("Bearer a1", "Bearer a2"), bearers)
        assertEquals(SessionState.None(ended = false), session.state.value)
        assertNull(store.refreshToken)
    }

    @Test
    fun `sesi yang sudah dicabut server tetap selesai sebagai keluar biasa`() = runBlocking {
        activeSession { error(401, "AUTHENTICATION_REQUIRED", "x") }
        server.dispatcher = Routes { error(401, "AUTHENTICATION_REQUIRED", "Sesi berakhir.") }

        session.logout()

        assertEquals(SessionState.None(ended = false), session.state.value)
        assertNull(store.refreshToken)
        assertFalse(store.ended)
    }

    @Test
    fun `logout tanpa jawaban server mempertahankan sesi`() = runBlocking {
        activeSession { loggedOut }
        server.close()

        val failure = assertThrows(ApiException::class.java) { runBlocking { session.logout() } }

        assertTrue(failure.isNetwork)
        assertTrue(session.state.value is SessionState.Active)
        assertEquals("r1", store.refreshToken)
        assertEquals("a1", session.accessToken)
    }

    @Test
    fun `logout yang ditolak server selain 401 mempertahankan sesi`() = runBlocking {
        activeSession { error(500, "INTERNAL_ERROR", "Gagal.") }

        val failure = assertThrows(ApiException::class.java) { runBlocking { session.logout() } }

        assertEquals(500, failure.status)
        assertTrue(session.state.value is SessionState.Active)
        assertEquals("r1", store.refreshToken)
    }

    @Test
    fun `polling yang tertinggal setelah logout tidak menandai sesi berakhir`() = runBlocking {
        activeSession { loggedOut }
        val api = api()

        session.logout()
        runCatching { apiCall { api.myShift() } }

        assertEquals(SessionState.None(ended = false), session.state.value)
        assertFalse(store.ended)
    }

    @Test
    fun `tanpa token sesi kosong bukan sesi berakhir`() = runBlocking {
        build()

        session.restore()

        assertEquals(SessionState.None(ended = false), session.state.value)
    }
}
