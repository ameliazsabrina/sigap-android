package fit.sigap.app.core.data

import fit.sigap.app.core.model.ResusAction
import fit.sigap.app.core.model.ResusDelivery
import fit.sigap.app.core.model.ResusDrug
import fit.sigap.app.core.model.Rhythm
import fit.sigap.app.core.network.ApiException
import fit.sigap.app.core.network.ServerClock
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.support.FakeResusDao
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import fit.sigap.app.support.resusEntryJson
import fit.sigap.app.support.resusLogJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections

class ResusLogRepositoryTest {
    private val server = MockWebServer()
    private val requests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val dao = FakeResusDao()
    private var respond: (RecordedRequest) -> MockResponse = { envelope(resusLogJson(emptyList(), null)) }
    private var ids = 0
    private lateinit var repository: ResusLogRepository

    @Before
    fun setUp() {
        server.dispatcher = Routes { request ->
            requests += request
            respond(request)
        }
        server.start()
        val base = server.url("/").toString()
        val session = SessionRepository(createAuthApi(base), FakeTokenStore())
        val clock = ServerClock { 1_795_000_000_000 }
        repository = ResusLogRepository(createSigapApi(base, session, clock), dao, clock) { "local-${++ids}" }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun echo(request: RecordedRequest, startSeq: Long = 10): MockResponse {
        val body = request.body!!.utf8()
        val submitted = Regex("\"id\":\"(local-\\d+)\"").findAll(body).map { it.groupValues[1] }.toList()
        val stored = submitted.mapIndexed { i, id -> resusEntryJson(id, startSeq + i, actor = "u1", actorName = "Budi") }
        return envelope(resusLogJson(stored, lastSeq = 99))
    }

    @Test
    fun `ketukan tersimpan di HP tanpa menyentuh jaringan`() = runBlocking {
        val entry = repository.record("i1", "u1", "Budi", ResusAction.DrugGiven(ResusDrug.EPINEPHRINE))

        assertEquals(0, requests.size)
        assertEquals(ResusDelivery.PENDING, entry.delivery)
        assertEquals(listOf("local-1"), repository.pending("u1").first().map { it.id })
    }

    @Test
    fun `kiriman memakai id HP sebagai idempotency key dan bentuk snake case`() = runBlocking {
        repository.record("i1", "u1", "Budi", ResusAction.RhythmChecked(Rhythm.PEA, startsCycle = false))
        respond = { echo(it) }

        repository.send("i1", repository.pending("u1").first())

        val body = requests.single().body!!.utf8()
        assertTrue(body, body.contains("\"id\":\"local-1\""))
        assertTrue(body, body.contains("\"entry_type\":\"RHYTHM_CHECKED\""))
        assertTrue(body, body.contains("\"payload\":{\"rhythm\":\"PEA\",\"starts_cycle\":false}"))
        assertTrue(body, body.contains("\"tapped_at\":\"2026-11-18T"))
        assertTrue(body, body.contains("\"clock_offset_ms\":0"))
        assertEquals("/incidents/i1/resus-log", requests.single().url.encodedPath)
        assertTrue(repository.pending("u1").first().isEmpty())
    }

    @Test
    fun `jawaban POST tidak memajukan kursor polling`() = runBlocking {
        repository.record("i1", "u1", "Budi", ResusAction.ShockDelivered)
        respond = { echo(it) }

        repository.send("i1", repository.pending("u1").first())

        assertNull(repository.observe("i1").first().lastSeq)
    }

    @Test
    fun `polling memakai after_seq terakhir dari GET`() = runBlocking {
        respond = { envelope(resusLogJson(listOf(resusEntryJson("s1", 4), resusEntryJson("s2", 5)), lastSeq = 5)) }
        repository.refresh("i1")
        respond = { envelope(resusLogJson(emptyList(), lastSeq = 5)) }
        repository.refresh("i1")

        assertEquals(null, requests[0].url.queryParameter("after_seq"))
        assertEquals("5", requests[1].url.queryParameter("after_seq"))
        val log = repository.observe("i1").first()
        assertEquals(2, log.entries.size)
    }

    @Test
    fun `entri yang ditolak sendiri ditandai, entri lain tetap terkirim`() = runBlocking {
        repository.record("i1", "u1", "Budi", ResusAction.ShockDelivered)
        repository.record("i1", "u1", "Budi", ResusAction.NoteAdded("bad"))
        respond = { request ->
            val body = request.body!!.utf8()
            when {
                body.contains("bad") -> error(422, "VALIDATION_ERROR", "Catatan terlalu panjang.")
                else -> echo(request)
            }
        }

        repository.send("i1", repository.pending("u1").first())

        val log = repository.observe("i1").first().entries.associateBy { it.id }
        assertEquals(ResusDelivery.SENT, log.getValue("local-1").delivery)
        assertEquals(ResusDelivery.REJECTED, log.getValue("local-2").delivery)
        assertEquals("Catatan terlalu panjang.", log.getValue("local-2").rejectedReason)
        assertEquals(3, requests.size)
    }

    @Test
    fun `gangguan server tetap mengantre dan dilempar untuk diulang`() = runBlocking {
        repository.record("i1", "u1", "Budi", ResusAction.ShockDelivered)
        respond = { error(503, "UNAVAILABLE", "Server sibuk.") }

        try {
            repository.send("i1", repository.pending("u1").first())
            fail("expected a retryable failure")
        } catch (cause: ApiException) {
            assertEquals(503, cause.status)
        }

        assertEquals(1, repository.pending("u1").first().size)
    }

    @Test
    fun `jenis entri yang tidak dikenal tampil sebagai tidak dikenali`() = runBlocking {
        respond = { envelope(resusLogJson(listOf(resusEntryJson("x", 1, type = "VITALS_TAKEN", payload = "{}")), 1)) }
        repository.refresh("i1")

        val action = repository.observe("i1").first().entries.single().action
        assertEquals(ResusAction.Unrecognized("VITALS_TAKEN"), action)
    }
}
