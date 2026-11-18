package fit.sigap.app.feature.documentation

import fit.sigap.app.core.data.DocumentationRepository
import fit.sigap.app.core.model.DocumentationValues
import fit.sigap.app.core.model.ResponseAnswer
import fit.sigap.app.core.network.createAuthApi
import fit.sigap.app.core.network.createSigapApi
import fit.sigap.app.core.session.SessionRepository
import fit.sigap.app.support.FakeTokenStore
import fit.sigap.app.support.Routes
import fit.sigap.app.support.envelope
import fit.sigap.app.support.error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentationViewModelTest {
    private val server = MockWebServer()
    private val writes = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private var serverVersion = 1
    private var serverCompletes = 0
    private lateinit var viewModel: DocumentationViewModel
    private val typed = DocumentationValues(answer = ResponseAnswer.YES, notes = "ketikan saya")

    private fun doc(version: Int, status: String = "DRAFT", notes: String = "dari server") =
        """{"id":"d1","documentation_status":"$status","version":$version,"notes":"$notes","unknown_information":[],"user_confirmed_information":{}}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = Routes { request ->
            when {
                request.method == "GET" -> envelope("[${doc(serverVersion)}]")
                request.url.encodedPath.endsWith("/complete") -> {
                    writes += request
                    serverCompletes++
                    envelope(doc(serverVersion + 1, "COMPLETED"))
                }
                else -> {
                    writes += request
                    val sent = Regex("\"expected_version\":(\\d+)").find(request.body!!.utf8())?.groupValues?.get(1)?.toInt()
                    if (sent != serverVersion) {
                        error(409, "VERSION_CONFLICT", "Versi bergeser.")
                    } else {
                        serverVersion++
                        envelope(doc(serverVersion))
                    }
                }
            }
        }
        server.start()
        val base = server.url("/").toString()
        val session = SessionRepository(createAuthApi(base), FakeTokenStore())
        viewModel = DocumentationViewModel("i1", DocumentationRepository(createSigapApi(base, session))) {}
    }

    @After
    fun tearDown() {
        server.close()
        Dispatchers.resetMain()
    }

    private suspend fun await(predicate: (DocumentationState) -> Boolean) =
        withTimeout(5_000) { viewModel.state.first(predicate) }

    private suspend fun conflicted() {
        await { !it.loading }
        serverVersion = 5
        viewModel.save(typed)
        await { it.conflict && it.pending == null }
    }

    @Test
    fun `konflik versi tidak menimpa dan tidak mengganti form sendiri`() = runBlocking {
        conflicted()

        assertEquals(1, viewModel.state.value.latest?.version)
        assertEquals(0, viewModel.state.value.generation)
    }

    @Test
    fun `memuat versi terbaru hanya mengganti form setelah dipilih`() = runBlocking {
        conflicted()

        viewModel.reloadAfterConflict()
        val state = await { !it.conflict && it.generation == 1 }

        assertEquals(5, state.latest?.version)
        assertEquals("dari server", state.latest?.values?.notes)
    }

    @Test
    fun `menimpa memakai versi server terbaru dan menyatakannya`() = runBlocking {
        conflicted()
        writes.clear()

        viewModel.overwriteAfterConflict(typed)
        val state = await { !it.conflict && it.pending == null && it.message != null }

        assertTrue(writes.single().body!!.utf8().contains("\"expected_version\":5"))
        assertTrue(writes.single().body!!.utf8().contains("ketikan saya"))
        assertEquals("Draft tersimpan dan menimpa perubahan orang lain.", state.message)
        assertEquals(0, state.generation)
    }

    @Test
    fun `selesai hanya dinyatakan dari jawaban server`() = runBlocking {
        await { !it.loading }

        viewModel.complete(typed)
        val state = await { it.latest?.completed == true && it.pending == null }

        assertEquals(1, serverCompletes)
        assertFalse(state.conflict)
    }
}
