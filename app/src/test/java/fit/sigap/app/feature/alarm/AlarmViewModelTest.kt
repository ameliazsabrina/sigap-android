package fit.sigap.app.feature.alarm

import fit.sigap.app.core.alarm.ActiveAlarm
import fit.sigap.app.core.alarm.AlarmRegistry
import fit.sigap.app.core.data.DutyRepository
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.push.AlarmTarget
import fit.sigap.app.core.session.SessionRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmViewModelTest {
    private val server = MockWebServer()
    private val commands = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val settled = Collections.synchronizedList(mutableListOf<String>())
    private val registry = AlarmRegistry()
    private var incident = incidentJson(status = "NOTIFICATION_SENT")
    private var onCommand: (RecordedRequest) -> MockResponse = { error(500, "INTERNAL_ERROR", "belum diatur") }
    private lateinit var repository: DutyRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = Routes { request ->
            when {
                request.method == "POST" -> {
                    commands += request
                    onCommand(request)
                }
                request.url.encodedPath.endsWith("/incidents/i1") -> envelope(incident)
                else -> error(404, "RESOURCE_NOT_FOUND", request.url.encodedPath)
            }
        }
        server.start()
        val base = server.url("/").toString()
        repository = DutyRepository(createSigapApi(base, SessionRepository(createAuthApi(base), FakeTokenStore())))
        registry.ring(ActiveAlarm(AlarmTarget.IncidentTarget("i1"), "CODE BLUE (SIMULASI) · ICU Bed 01", "Simulasi SIGAP.", Instant.EPOCH))
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = AlarmViewModel(registry, repository, userId = { "u1" }, onSettled = { settled += it })

    private suspend fun AlarmViewModel.await(predicate: (AlarmUiState) -> Boolean): AlarmUiState =
        withTimeout(5_000) { state.first { it != null && predicate(it) }!! }

    private fun acknowledged() = envelope(
        incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u1"),
    )

    @Test
    fun `detail lokasi dibaca dari server dan alarm tetap berbunyi`() = runBlocking {
        val vm = viewModel()

        val state = vm.await { it.location == "ICU Bed 01" }

        assertEquals(AlarmKind.CODE_BLUE, state.kind)
        assertNull(state.stoppedLine)
        assertEquals(1, registry.alarms.value.size)
    }

    @Test
    fun `acknowledge membuka lebih dulu lalu alarm berhenti setelah server mencatatnya`() = runBlocking {
        onCommand = { request ->
            if (request.url.encodedPath.endsWith("/open")) envelope(incidentJson(status = "OPENED")) else acknowledged()
        }
        val vm = viewModel()

        vm.primary()
        val state = vm.await { it.stoppedLine != null }

        assertEquals(listOf("open", "acknowledge"), commands.map { it.url.encodedPath.substringAfterLast('/') })
        assertEquals(1, commands.map { it.headers["Idempotency-Key"] }.toSet().size)
        assertTrue(registry.alarms.value.isEmpty())
        assertEquals(listOf("i1"), settled.toList())
        assertTrue(state.stoppedLine!!.startsWith("Tercatat menuju lokasi"))
    }

    @Test
    fun `acknowledge yang gagal membiarkan alarm berbunyi`() = runBlocking {
        incident = incidentJson(status = "OPENED")
        onCommand = { error(500, "INTERNAL_ERROR", "Gagal menyimpan.") }
        val vm = viewModel()

        vm.primary()
        val state = vm.await { it.phase is CommandPhase.Failed }

        assertEquals("Gagal menyimpan.", (state.phase as CommandPhase.Failed).message)
        assertEquals(1, registry.alarms.value.size)
        assertTrue(settled.isEmpty())
    }

    @Test
    fun `incident yang sudah disanggupi orang lain menghentikan alarm saat layar dibuka`() = runBlocking {
        incident = incidentJson(status = "ACKNOWLEDGED", acknowledgedAt = "2026-11-05T08:01:00.000Z", acknowledgedBy = "u2")
        val vm = viewModel()

        val state = vm.await { it.stoppedLine != null }

        assertTrue(registry.alarms.value.isEmpty())
        assertTrue(state.stoppedLine!!.startsWith("Server menyatakan"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `alarm baru menunggu sampai alarm yang tampil berhenti`() = runBlocking {
        val vm = viewModel()
        vm.await { it.location == "ICU Bed 01" }
        registry.ring(ActiveAlarm(AlarmTarget.EarlyAlertTarget("a1"), "Early Alert kritis", "SpO2 turun", Instant.EPOCH))
        assertEquals(AlarmKind.CODE_BLUE, vm.state.value?.kind)

        registry.settle(setOf("i1"))
        val state = vm.await { it.kind == AlarmKind.EARLY_ALERT }

        assertNull(state.stoppedLine)
        assertEquals("Early Alert kritis", state.location)
    }
}
