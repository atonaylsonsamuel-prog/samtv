package com.samdevelop.samtv

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.samdevelop.samtv.core.model.StreamKind
import com.samdevelop.samtv.core.model.StreamSource
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.data.remote.OkHttpHttpClient
import com.samdevelop.samtv.player.PlaybackPhase
import com.samdevelop.samtv.player.PlayerController
import com.samdevelop.samtv.player.PlayerFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NetworkAndPlayerTest {
    private lateinit var fake: FakeXtreamServer

    @Before fun setUp() { fake = FakeXtreamServer().start() }
    @After fun tearDown() { fake.shutdown() }

    // ---------------------------------------------------------------- cliente HTTP

    @Test fun httpSemConsentimentoNaoSaiDoAparelho() = runBlocking {
        val client = OkHttpHttpClient()
        try {
            client.get(HttpRequest("${fake.baseUrl}/player_api.php", containsCredentials = true, allowInsecureHttp = false))
            fail("deveria bloquear")
        } catch (e: HttpFailure.InsecureNotAllowed) { /* esperado */ }
        assertEquals(0, fake.server.requestCount)
    }

    @Test fun redirecionamentoParaOutroHostComCredenciaisEBloqueado() = runBlocking {
        val origin = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://outro-host.invalid/coleta"))
            start()
        }
        try {
            OkHttpHttpClient().get(HttpRequest(origin.url("/player_api.php?username=a&password=b").toString(), containsCredentials = true, allowInsecureHttp = true))
            fail("deveria bloquear")
        } catch (e: HttpFailure.RedirectBlocked) {
            assertFalse("mensagem não pode conter a senha", e.message.orEmpty().contains("password=b"))
        } finally {
            assertEquals(1, origin.requestCount)
            origin.shutdown()
        }
    }

    @Test fun timeoutViraFalhaControlada() = runBlocking {
        val slow = MockWebServer().apply {
            enqueue(MockResponse().setBody("{}").setHeadersDelay(3, TimeUnit.SECONDS))
            start()
        }
        val client = OkHttpHttpClient(OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build())
        try {
            client.get(HttpRequest(slow.url("/").toString(), containsCredentials = false, allowInsecureHttp = true))
            fail("deveria expirar")
        } catch (e: HttpFailure.Timeout) { /* esperado */ } finally { slow.shutdown() }
    }

    // ---------------------------------------------------------------- player

    @Test fun playerReproduzArquivoDeTesteELiberaRecursos() = runBlocking {
        val scope = MainScope()
        val factory = PlayerFactory(InstrumentationRegistry.getInstrumentation().targetContext, OkHttpHttpClient.defaultClient())
        val controller = withContext(Dispatchers.Main) { PlayerController(factory.create(), scope) }
        val source = StreamSource(
            "${fake.baseUrl}/movie/${fake.user}/${fake.pass}/555.mp4", StreamKind.VOD, "progressive",
            mapOf("Referer" to "https://ref.example.test/"),
        )
        withContext(Dispatchers.Main) { controller.play(source) }
        val ready = withTimeout(20_000) { controller.state.first { it.phase == PlaybackPhase.READY || it.phase == PlaybackPhase.ERROR } }
        assertEquals(PlaybackPhase.READY, ready.phase)

        val recorded = fake.server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("https://ref.example.test/", recorded.getHeader("Referer"))
        assertTrue(recorded.getHeader("User-Agent")!!.startsWith("SamTV/"))

        withContext(Dispatchers.Main) {
            controller.togglePlayPause()
            controller.release()
            controller.release() // idempotente
        }
        scope.cancel()
    }

    @Test fun erro404NoStreamGeraMensagemSemNovasTentativas() = runBlocking {
        val scope = MainScope()
        val factory = PlayerFactory(InstrumentationRegistry.getInstrumentation().targetContext, OkHttpHttpClient.defaultClient())
        val controller = withContext(Dispatchers.Main) { PlayerController(factory.create(), scope) }
        withContext(Dispatchers.Main) {
            controller.play(StreamSource("${fake.baseUrl}/nao-existe.mp4", StreamKind.VOD, null, emptyMap()))
        }
        val err = withTimeout(20_000) { controller.state.first { it.phase == PlaybackPhase.ERROR } }
        assertFalse(err.error!!.retryable)
        assertTrue(err.error!!.message.contains("404"))
        withContext(Dispatchers.Main) { controller.release() }
        scope.cancel()
    }
}
