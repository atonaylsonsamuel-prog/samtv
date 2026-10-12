package com.samdevelop.samtv

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

/**
 * Servidor Xtream SIMULADO para testes instrumentados. Todos os dados são exemplos
 * identificados como tal; o vídeo é um clipe sintético gerado para os testes (barras + tom de 440 Hz).
 * Credenciais de teste fictícias: usuario_teste / senha_teste.
 */
class FakeXtreamServer {
    val server = MockWebServer()
    val user = "usuario_teste"
    val pass = "senha_teste"

    private val clip: ByteArray by lazy {
        InstrumentationRegistry.getInstrumentation().context.assets.open("samtv_test_clip.mp4").use { it.readBytes() }
    }

    fun start(): FakeXtreamServer {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/player_api.php") -> api(request)
                    path.startsWith("/live/$user/$pass/") || path.startsWith("/movie/$user/$pass/") ->
                        MockResponse().setHeader("Content-Type", "video/mp4").setBody(Buffer().write(clip))
                    path.startsWith("/playlist.m3u") -> MockResponse().setBody(playlist())
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        return this
    }

    val baseUrl: String get() = server.url("/").toString().trimEnd('/')

    private fun api(r: RecordedRequest): MockResponse {
        val url = r.requestUrl ?: return MockResponse().setResponseCode(400)
        if (url.queryParameter("username") != user || url.queryParameter("password") != pass) {
            return json("""{"user_info":{"auth":0}}""")
        }
        return when (url.queryParameter("action")) {
            null -> json("""{"user_info":{"auth":1,"status":"Active","exp_date":"1893456000","max_connections":"1","active_cons":"0","allowed_output_formats":["ts"]},"server_info":{"timezone":"UTC"}}""")
            "get_live_categories" -> json("""[{"category_id":"1","category_name":"Exemplos (teste)"},{"category_id":"2","category_name":"Outros (teste)"}]""")
            "get_live_streams" -> json(
                """[{"num":1,"name":"Canal Exemplo Alfa","stream_id":101,"category_id":"1"},
                    {"num":2,"name":"Canal Exemplo Beta","stream_id":102,"category_id":"1"},
                    {"num":3,"name":"Canal Exemplo Gama","stream_id":103,"category_id":"2"}]""",
            )
            "get_vod_categories" -> json("""[{"category_id":"9","category_name":"Filmes (teste)"}]""")
            "get_vod_streams" -> json("""[{"name":"Filme Exemplo","stream_id":555,"category_id":"9","container_extension":"mp4"}]""")
            "get_vod_info" -> json("""{"info":{"plot":"Sinopse de exemplo.","duration_secs":3},"movie_data":{"stream_id":555,"name":"Filme Exemplo","container_extension":"mp4"}}""")
            "get_series_categories", "get_series" -> json("[]")
            "get_short_epg" -> json("""{"epg_listings":[]}""")
            else -> MockResponse().setResponseCode(404)
        }
    }

    private fun playlist() = """
        #EXTM3U
        #EXTINF:-1 tvg-id="ex1" group-title="Exemplos (teste)",Canal M3U Exemplo
        ${baseUrl}/live/$user/$pass/101.ts
    """.trimIndent()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    fun shutdown() = server.shutdown()
}
