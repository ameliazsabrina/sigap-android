package fit.sigap.app.feature.duty

import fit.sigap.app.core.data.DutyRepository
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.model.Readiness
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.push.PushRepository
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.core.session.SessionUser
import fit.sigap.app.feature.common.CommandPhase
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import fit.sigap.app.support.incidentJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class DutyViewModelTest {
    private val server = MockWebServer()
    private val commands = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var onCommand: (RecordedRequest) -> MockResponse = { error(500, "INTERNAL_ERROR", "belum diatur") }
    private var shift: String = """{"shift":{"ward":{"id":"w1","name":"ICU"},"started_at":"2026-11-05T07:00:00.000Z"}}"""
    private var incidents: String = "[${incidentJson()}]"
    private var pushStatus: String = """{"subscribed":false,"confirmed":false}"""
    private val cancelled = Collections.synchronizedList(mutableListOf<Set<String>>())
    private lateinit var viewModel: DutyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = Routes { request ->
            val path = request.url.encodedPath
            when {
                request.method == "POST" -> {
                    commands += request
                    onCommand(request)
                }
                path.endsWith("shifts/me") -> envelope(shift)
                path.endsWith("/incidents") -> envelope(incidents)
                path.endsWith("/early-alerts") -> envelope("[]")
                path.endsWith("auth/push/status") -> envelope(pushStatus)
                path.endsWith("settings/response") -> envelope("""[{"key":"arrival_reminder_seconds","value":300}]""")
                else -> error(404, "RESOURCE_NOT_FOUND", path)
            }
        }
        server.start()
        val base = server.url("/").toString()
        val session = SessionRepository(createAuthApi(base), FakeTokenStore())
        val api = createSigapApi(base, session)
        viewModel = DutyViewModel(
            user = SessionUser("u1", "Budi", DutyRole.CODE_BLUE_TEAM),
            repository = DutyRepository(api),
            push = PushRepository(api) { FCM_TOKEN },
            cancelSettledAlarms = { cancelled += it },
            logout = {
                logoutCalls++
                logoutFailure?.let { throw it }
            },
        ) { confirmed -> Readiness(pushConfirmed = confirmed) }
    }

    private var logoutCalls = 0
    private var logoutFailure: ApiException? = null

    @Test
    fun `keluar ditolak selama masih bertugas tanpa menghubungi server`() = runBlocking {
        started()

        viewModel.signOut()
        val state = awaitState { it.signOutPhase is CommandPhase.Failed }

        assertEquals("Selesai bertugas dulu sebelum keluar dari HP ini.", (state.signOutPhase as CommandPhase.Failed).message)
        assertEquals(0, logoutCalls)
    }

    @Test
    fun `keluar setelah selesai bertugas memanggil logout`() = runBlocking {
        shift = """{"shift":null}"""
        viewModel.startPolling()
        awaitState { it.shiftKnown }

        viewModel.signOut()
        awaitState { it.signOutPhase == CommandPhase.Idle && logoutCalls == 1 }

        assertEquals(1, logoutCalls)
    }

    @Test
    fun `logout gagal tampil sebagai galat dan dapat diulang`() = runBlocking {
        shift = """{"shift":null}"""
        viewModel.startPolling()
        awaitState { it.shiftKnown }
        logoutFailure = ApiException(ApiException.CODE_NETWORK, "Tidak dapat terhubung ke server.")

        viewModel.signOut()
        val failed = awaitState { it.signOutPhase is CommandPhase.Failed }
        assertEquals("Tidak dapat terhubung ke server.", (failed.signOutPhase as CommandPhase.Failed).message)

        logoutFailure = null
        viewModel.signOut()
        awaitState { it.signOutPhase == CommandPhase.Idle }
        assertEquals(2, logoutCalls)
    }

    @After
    fun tearDown() {
        viewModel.stopPolling()
        server.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(predicate: (DutyState) -> Boolean): DutyState =
        withTimeout(5_000) { viewModel.state.first(predicate) }

    private suspend fun started(): DutyState {
        viewModel.startPolling()
        return awaitState { it.loaded && it.arrivalLimit != null }
    }

    private fun acknowledged() = envelope(
        incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u1"),
    )

    @Test
    fun `sebelum absen layar tidak memuat data dan alert tidak tampil`() = runBlocking {
        shift = """{"shift":null}"""

        viewModel.startPolling()
        val state = awaitState { it.shiftKnown }

        assertNull(state.shift)
        assertTrue(state.incidents.isEmpty())
    }

    @Test
    fun `batas tiba dan status push dibaca dari server`() = runBlocking {
        val state = started()

        assertEquals(300L, state.arrivalLimit?.seconds)
        assertEquals(false, state.readiness.pushConfirmed)
        assertTrue(state.readiness.blocked)
    }

    @Test
    fun `acknowledge membuka lebih dulu dengan kunci yang sama`() = runBlocking {
        started()
        onCommand = { request ->
            if (request.url.encodedPath.endsWith("/open")) envelope(incidentJson(status = "OPENED")) else acknowledged()
        }

        viewModel.acknowledge("i1")
        val state = awaitState { it.incidents.single().isAcknowledged && it.phases["i1"] == CommandPhase.Idle }

        assertTrue(state.incidents.single().acknowledgedByMe)
        assertEquals(listOf("/open", "/acknowledge"), commands.map { "/" + it.url.encodedPath.substringAfterLast('/') })
        assertEquals(1, commands.map { it.headers["Idempotency-Key"] }.toSet().size)
        assertNotNull(commands.first().headers["Idempotency-Key"])
    }

    @Test
    fun `gagal tidak terlihat seperti berhasil dan percobaan ulang memakai kunci yang sama`() = runBlocking {
        started()
        onCommand = { error(500, "INTERNAL_ERROR", "Gagal menyimpan.") }

        viewModel.acknowledge("i1")
        val failed = awaitState { it.phases["i1"] is CommandPhase.Failed }
        assertEquals("Gagal menyimpan.", (failed.phases["i1"] as CommandPhase.Failed).message)
        assertTrue(!failed.incidents.single().isAcknowledged)
        val firstKey = commands.single().headers["Idempotency-Key"]

        commands.clear()
        onCommand = { request ->
            if (request.url.encodedPath.endsWith("/open")) envelope(incidentJson(status = "OPENED")) else acknowledged()
        }
        viewModel.acknowledge("i1")
        awaitState { it.incidents.single().isAcknowledged && it.phases["i1"] == CommandPhase.Idle }

        assertEquals(setOf(firstKey), commands.map { it.headers["Idempotency-Key"] }.toSet())
    }

    @Test
    fun `jawaban tanpa waktu acknowledgement dianggap gagal`() = runBlocking {
        started()
        onCommand = { envelope(incidentJson(status = "OPENED")) }

        viewModel.acknowledge("i1")
        val state = awaitState { it.phases["i1"] is CommandPhase.Failed }

        assertTrue(!state.incidents.single().isAcknowledged)
    }

    @Test
    fun `incident yang sudah OPENED tidak dibuka lagi`() = runBlocking {
        incidents = "[${incidentJson(status = "OPENED")}]"
        started()
        onCommand = { acknowledged() }

        viewModel.acknowledge("i1")
        awaitState { it.incidents.single().isAcknowledged && it.phases["i1"] == CommandPhase.Idle }

        assertEquals(1, commands.size)
        assertTrue(commands.single().url.encodedPath.endsWith("/acknowledge"))
    }

    @Test
    fun `QR pairing di layar absen ditolak tanpa memanggil server`() = runBlocking {
        shift = """{"shift":null}"""
        viewModel.startPolling()
        awaitState { it.shiftKnown }

        viewModel.checkIn("https://app-staging.sigap.fit/pair/abcDEF123_-abcDEF123")

        assertTrue(viewModel.state.value.dutyPhase is CommandPhase.Failed)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `isi QR absen dan kode enam angka dikirim apa adanya`() = runBlocking {
        shift = """{"shift":null}"""
        viewModel.startPolling()
        awaitState { it.shiftKnown }
        onCommand = { envelope("""{"ward":{"id":"w1","name":"ICU"},"started_at":"2026-11-05T07:00:00.000Z"}""") }

        viewModel.checkIn("SIGAP-ABSEN:SIM-ICU-01:482913")
        val state = awaitState { it.shift != null && it.dutyPhase == CommandPhase.Idle }

        assertEquals("ICU", state.shift?.wardName)
        assertTrue(commands.single().body!!.utf8().contains("SIGAP-ABSEN:SIM-ICU-01:482913"))
    }

    private fun pushRoutes(sent: Boolean = true, reason: String? = null): (RecordedRequest) -> MockResponse = { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("native-registrations") -> envelope("""{"id":"s1","confirmed":false}""")
            path.endsWith("push/test") -> envelope("""{"sent":$sent,"reason":${reason?.let { "\"$it\"" } ?: "null"}}""")
            path.endsWith("push/test/confirm") -> envelope("""{"confirmed":true}""")
            else -> error(404, "RESOURCE_NOT_FOUND", path)
        }
    }

    @Test
    fun `notifikasi uji mengunggah token lebih dulu dan tidak menganggap HP terkonfirmasi`() = runBlocking {
        started()
        onCommand = pushRoutes()

        viewModel.sendPushTest()
        val state = awaitState { it.pushTestSent && it.pushTestPhase == CommandPhase.Idle }

        assertEquals(listOf("native-registrations", "test"), commands.map { it.url.encodedPath.substringAfterLast('/') })
        assertTrue(commands.first().body!!.utf8().contains("\"registration_id\":\"$FCM_TOKEN\""))
        assertEquals(false, state.readiness.pushConfirmed)
    }

    @Test
    fun `tes yang ditolak server tampil sebagai gagal dengan alasannya`() = runBlocking {
        started()
        onCommand = pushRoutes(sent = false, reason = "FCM tidak dapat dihubungi.")

        viewModel.sendPushTest()
        val state = awaitState { it.pushTestPhase is CommandPhase.Failed }

        assertEquals("FCM tidak dapat dihubungi.", (state.pushTestPhase as CommandPhase.Failed).message)
        assertTrue(!state.pushTestSent)
    }

    @Test
    fun `konfirmasi baru berlaku setelah status server menyatakannya`() = runBlocking {
        started()
        onCommand = pushRoutes()
        viewModel.sendPushTest()
        awaitState { it.pushTestSent }

        pushStatus = """{"subscribed":true,"confirmed":true}"""
        viewModel.confirmPushTest()
        val state = awaitState { it.readiness.pushConfirmed }

        assertTrue(!state.pushTestSent)
        assertTrue(commands.last().url.encodedPath.endsWith("push/test/confirm"))
    }

    @Test
    fun `konfirmasi yang belum tercatat server tetap gagal`() = runBlocking {
        started()
        onCommand = pushRoutes()
        viewModel.sendPushTest()
        awaitState { it.pushTestSent }

        viewModel.confirmPushTest()
        val state = awaitState { it.pushTestPhase is CommandPhase.Failed }

        assertEquals(false, state.readiness.pushConfirmed)
    }

    @Test
    fun `alarm dibatalkan hanya untuk incident yang sudah disanggupi`() = runBlocking {
        incidents = "[${incidentJson(id = "i1")},${incidentJson(id = "i2", status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z")}]"

        started()

        val settled = cancelled.flatten().toSet()
        assertTrue("i2" in settled)
        assertTrue("i1" !in settled)
    }

    @Test
    fun `acknowledge yang berhasil membatalkan alarm incident itu`() = runBlocking {
        incidents = "[${incidentJson(status = "OPENED")}]"
        started()
        assertTrue("i1" !in cancelled.flatten())
        onCommand = { acknowledged() }

        viewModel.acknowledge("i1")
        awaitState { it.incidents.single().isAcknowledged }

        assertTrue("i1" in cancelled.flatten())
    }

    private companion object {
        const val FCM_TOKEN = "fcm-token-0123456789abcdef"
    }
}
