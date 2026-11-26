package fit.sigap.app.feature.resus

import fit.sigap.app.core.data.ResusLogRepository
import fit.sigap.app.core.model.DutyRole
import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusDrug
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.network.ServerClock
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.core.session.SessionUser
import fit.sigap.app.support.FakeResusDao
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
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
class ResusLogViewModelTest {
    private val server = MockWebServer()
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val dao = FakeResusDao()
    private var respond: (RecordedRequest) -> MockResponse = { envelope("[]") }
    private var nowMillis = Instant.parse("2026-11-23T08:10:00Z").toEpochMilli()
    private lateinit var viewModel: ResusLogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = Routes { request ->
            requests += request
            respond(request)
        }
        server.start()
        val base = server.url("/").toString()
        val session = SessionRepository(createAuthApi(base), FakeTokenStore())
        val clock = ServerClock { nowMillis }
        var ids = 0
        val repository = ResusLogRepository(createSigapApi(base, session), dao, clock) { "e${++ids}" }
        viewModel = ResusLogViewModel("i1", SessionUser("u1", "Budi", DutyRole.CODE_BLUE_TEAM), repository) {
            Instant.ofEpochMilli(nowMillis)
        }
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private suspend fun await(predicate: (ResusLogState) -> Boolean) =
        withTimeout(5_000) { viewModel.state.first(predicate) }

    @Test
    fun `ketukan langsung tampil dan menawarkan urungkan`() = runBlocking<Unit> {
        viewModel.record(ResusAction.DrugGiven(ResusDrug.EPINEPHRINE))

        val state = await { it.log.entries.isNotEmpty() && it.undo != null }
        assertEquals(1, state.sheet.epinephrineCount)
        assertEquals("Epinefrin", state.undo?.label)
    }

    @Test
    fun `tindakan sama dalam jendela meminta konfirmasi, bukan menolak`() = runBlocking<Unit> {
        viewModel.record(ResusAction.ShockDelivered)
        await { it.sheet.shockCount == 1 }
        nowMillis += 5_000

        viewModel.record(ResusAction.ShockDelivered)
        val prompt = await { it.prompt != null }.prompt
        assertTrue(prompt is ResusPrompt.Duplicate)
        assertEquals(1, viewModel.state.value.sheet.shockCount)

        viewModel.confirmPrompt()
        await { it.sheet.shockCount == 2 && it.prompt == null }
    }

    @Test
    fun `defibrilasi saat irama non shockable diredupkan lalu tetap bisa dicatat`() = runBlocking<Unit> {
        viewModel.recordRhythm(Rhythm.ASYSTOLE)
        await { it.sheet.rhythm == Rhythm.ASYSTOLE }

        viewModel.record(ResusAction.ShockDelivered)
        assertTrue(await { it.prompt != null }.prompt is ResusPrompt.RhythmConflict)

        viewModel.confirmPrompt()
        await { it.sheet.shockCount == 1 }
    }

    @Test
    fun `cek irama tidak pernah memulai siklus sendiri`() = runBlocking<Unit> {
        viewModel.recordRhythm(Rhythm.VF)
        val state = await { it.sheet.rhythm == Rhythm.VF }
        assertEquals(0, state.sheet.cycleCount)
    }

    @Test
    fun `urungkan adalah koreksi, entri asli tetap ada dan dicoret`() = runBlocking<Unit> {
        viewModel.record(ResusAction.ShockDelivered)
        await { it.undo != null }

        viewModel.undo()

        val state = await { it.log.entries.size == 2 }
        assertNull(state.undo)
        assertEquals(0, state.sheet.shockCount)
        assertTrue(state.sheet.entries.first { it.entry.id == "e1" }.voided)
    }
}
