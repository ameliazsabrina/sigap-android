package fit.sigap.app.core.data

import fit.sigap.app.core.model.ResusAction
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ResusOutboxTest {
    private val server = MockWebServer()
    private val dao = FakeResusDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val posts = AtomicInteger()
    private val failuresLeft = AtomicInteger(0)
    private val pauses = Collections.synchronizedList(mutableListOf<Long>())
    private val user = MutableStateFlow<String?>("u1")
    private lateinit var repository: ResusLogRepository

    @Before
    fun setUp() {
        server.dispatcher = Routes { request ->
            posts.incrementAndGet()
            if (failuresLeft.getAndDecrement() > 0) return@Routes error(503, "UNAVAILABLE", "Server sibuk.")
            val ids = Regex("\"id\":\"([^\"]+)\"").findAll(request.body!!.utf8()).map { it.groupValues[1] }.toList()
            envelope(resusLogJson(ids.mapIndexed { i, id -> resusEntryJson(id, i + 1L, actor = "u1") }, lastSeq = ids.size.toLong()))
        }
        server.start()
        val base = server.url("/").toString()
        val clock = ServerClock()
        var ids = 0
        repository = ResusLogRepository(
            createSigapApi(base, SessionRepository(createAuthApi(base), FakeTokenStore()), clock),
            dao,
            clock,
        ) { "e${++ids}" }
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun outbox() = ResusOutbox(repository, user, scope, pause = { pauses += it })

    @Test
    fun `antrean dikirim ulang dengan jeda bertambah sampai berhasil`() = runBlocking {
        failuresLeft.set(3)
        repository.record("i1", "u1", "Budi", ResusAction.ShockDelivered)

        outbox().start()

        withTimeout(5_000) { repository.pending("u1").first { it.isEmpty() } }
        assertEquals(listOf(1_000L, 2_000L, 4_000L), pauses.toList())
        assertEquals(4, posts.get())
    }

    @Test
    fun `entri milik pengguna lain tidak dikirim dengan sesi ini`() = runBlocking {
        repository.record("i1", "u2", "Sari", ResusAction.ShockDelivered)
        repository.record("i1", "u1", "Budi", ResusAction.CprCycleStarted)

        outbox().start()

        withTimeout(5_000) { repository.pending("u1").first { it.isEmpty() } }
        assertEquals(1, repository.pending("u2").first().size)
        assertTrue(posts.get() >= 1)
    }
}
