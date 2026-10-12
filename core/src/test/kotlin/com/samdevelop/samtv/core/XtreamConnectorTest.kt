package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.xtream.XtreamConnector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamConnectorTest {
    private val fake = FakeHttpClient()
    private val base = "https://painel.example.test:8443"

    private fun connector(allowInsecure: Boolean = false, url: String = base, password: String = "s3nh@ teste&x") =
        XtreamConnector("src1", url, "usuario teste", password, allowInsecure, fake, nowEpochSeconds = { 1_760_000_000 })

    private fun <T> AppResult<T>.ok(): T = (this as? AppResult.Success)?.value ?: throw AssertionError("esperava sucesso, veio $this")
    private fun AppResult<*>.err(): AppError = (this as? AppResult.Failure)?.error ?: throw AssertionError("esperava falha, veio $this")

    @Test fun `login valido le user_info e formatos`() = runBlocking<Unit> {
        fake.on("auth", body = fixture("xtream/auth_ok.json"))
        val r = connector().authenticate().ok()
        assertEquals(AccountStatus.ACTIVE, r.status)
        assertEquals(1893456000L, r.expiresAtEpochSeconds)
        assertEquals(2, r.maxConnections)
        assertEquals(listOf("m3u8", "ts", "rtmp"), r.allowedOutputFormats)
        assertEquals("America/Sao_Paulo", r.serverTimezone)
    }

    @Test fun `parametros sao codificados e enviados so ao host configurado`() = runBlocking<Unit> {
        fake.on("auth", body = fixture("xtream/auth_ok.json"))
        connector().authenticate()
        val req = fake.requests.single()
        assertTrue(req.url.startsWith("$base/player_api.php?"))
        assertTrue(req.url.contains("username=usuario+teste"))
        assertTrue(req.url.contains("password=s3nh%40+teste%26x"))
        assertTrue(req.containsCredentials)
        assertFalse("server_info.url não pode ser usado", req.url.contains("outro-host"))
    }

    @Test fun `credenciais invalidas`() = runBlocking<Unit> {
        fake.on("auth", body = """{"user_info":{"auth":0}}""")
        assertEquals(AppError.Unauthorized, connector().authenticate().err())
        fake.on("auth", code = 401, body = "")
        assertEquals(AppError.Unauthorized, connector().authenticate().err())
        fake.on("auth", code = 403, body = "Forbidden")
        assertEquals(AppError.Unauthorized, connector().authenticate().err())
    }

    @Test fun `conta expirada e desativada`() = runBlocking<Unit> {
        fake.on("auth", body = """{"user_info":{"auth":1,"status":"Expired","exp_date":"1600000000"}}""")
        assertEquals(AppError.AccountExpired(1600000000), connector().authenticate().err())
        fake.on("auth", body = """{"user_info":{"auth":"1","status":"Banned"}}""")
        assertTrue(connector().authenticate().err() is AppError.AccountDisabled)
    }

    @Test fun `HTTP 200 sem estrutura esperada nao e conexao valida`() = runBlocking<Unit> {
        fake.on("auth", body = """{"ok":true}""")
        assertTrue(connector().authenticate().err() is AppError.InvalidResponse)
        fake.on("auth", body = "<html>Painel</html>")
        assertTrue(connector().authenticate().err() is AppError.InvalidResponse)
        fake.on("auth", body = "")
        assertTrue(connector().authenticate().err() is AppError.InvalidResponse)
        fake.on("auth", body = """{"user_info":{"status":"Active"}}""")
        assertTrue("sem flag auth", connector().authenticate().err() is AppError.InvalidResponse)
    }

    @Test fun `JSON malformado`() = runBlocking<Unit> {
        fake.on("auth", body = """{"user_info": {"auth": 1,""")
        assertTrue(connector().authenticate().err() is AppError.InvalidResponse)
        fake.on("get_live_categories", body = "[{]")
        assertTrue(connector().loadLiveCatalog().err() is AppError.InvalidResponse)
    }

    @Test fun `timeout e erro de rede`() = runBlocking<Unit> {
        fake.fail("auth", HttpFailure.Timeout())
        assertEquals(AppError.Timeout, connector().authenticate().err())
        fake.fail("auth", HttpFailure.Connection("falha em https://painel.example.test/player_api.php?username=usuario%20teste&password=x"))
        val e = connector().authenticate().err() as AppError.Network
        assertFalse(e.detail!!.contains("password=x"))
        fake.fail("auth", HttpFailure.Tls("certificado inválido"))
        assertTrue(connector().authenticate().err() is AppError.Tls)
    }

    @Test fun `erro HTTP`() = runBlocking<Unit> {
        fake.on("auth", code = 500, body = "erro")
        assertEquals(AppError.Http(500), connector().authenticate().err())
        fake.on("auth", code = 404, body = "")
        assertEquals(AppError.Http(404), connector().authenticate().err())
    }

    @Test fun `HTTP sem consentimento nao envia credenciais`() = runBlocking<Unit> {
        val r = connector(allowInsecure = false, url = "http://painel.example.test:8080").authenticate()
        assertEquals(AppError.InsecureConnectionNotAllowed, r.err())
        assertTrue(fake.requests.isEmpty())
        fake.on("auth", body = fixture("xtream/auth_ok.json"))
        connector(allowInsecure = true, url = "http://painel.example.test:8080").authenticate().ok()
    }

    @Test fun `catalogo ao vivo com campos ausentes e tipos variados`() = runBlocking<Unit> {
        fake.on("get_live_categories", body = fixture("xtream/live_categories.json"))
        fake.on("get_live_streams", body = fixture("xtream/live_streams.json"))
        val cat = connector().loadLiveCatalog().ok()
        assertEquals(3, cat.channels.size) // ignora item sem nome, item sem id e o elemento não-objeto
        assertEquals(listOf("Categoria Exemplo A", "Categoria Exemplo B"), cat.categories.map { it.name }) // vazia removida
        val c2 = cat.channels.first { it.streamKey == "102" }
        assertEquals(2, c2.number)
        assertNull("ícone vazio vira null", c2.logoUrl)
        assertTrue(c2.hasArchive)
        assertEquals(cat.categories[0].id, c2.categoryId)
        assertEquals("src1:live:101", cat.channels[0].id)
    }

    @Test fun `categorias vazias e respostas vazias em formatos variados`() = runBlocking<Unit> {
        fake.on("get_vod_categories", body = "[]")
        fake.on("get_vod_streams", body = "{}")
        val m = connector().loadMovieCatalog().ok()
        assertTrue(m.categories.isEmpty() && m.movies.isEmpty())
        fake.on("get_series_categories", body = "null")
        fake.on("get_series", body = "[]")
        assertTrue(connector().loadSeriesCatalog().ok().series.isEmpty())
    }

    @Test fun `resposta inesperada em listagem`() = runBlocking<Unit> {
        fake.on("get_live_categories", body = "\"texto\"")
        assertTrue(connector().loadLiveCatalog().err() is AppError.InvalidResponse)
        fake.on("get_live_categories", body = """{"user_info":{"auth":0}}""")
        assertEquals(AppError.Unauthorized, connector().loadLiveCatalog().err())
    }

    @Test fun `detalhes de serie com temporadas e episodios`() = runBlocking<Unit> {
        fake.on("get_series_info", body = fixture("xtream/series_info.json"))
        val d = connector().seriesDetails("src1:series:77").ok()
        assertEquals("Série Exemplo", d.series.name)
        assertEquals(listOf(1, 2), d.seasons.map { it.number }) // temporada 3 sem episódios não aparece
        assertEquals(listOf("S01E01 Exemplo", "S01E02 Exemplo"), d.episodesOf(1).map { it.title })
        assertEquals(1, d.episodesOf(2).single().episodeNumber)
        assertEquals("77", fake.requests.single().url.substringAfter("series_id="))
    }

    @Test fun `episodios em formato de array`() = runBlocking<Unit> {
        fake.on("get_series_info", body = """{"info":{"name":"X"},"episodes":[[{"id":"1","season":1,"episode_num":1,"title":"A"}],[{"id":"2","season":2,"title":"B"}]]}""")
        val d = connector().seriesDetails("77").ok()
        assertEquals(2, d.episodes.size)
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
    }

    @Test fun `detalhes de filme`() = runBlocking<Unit> {
        fake.on("get_vod_info", body = fixture("xtream/vod_info.json"))
        val d = connector().movieDetails("src1:vod:555").ok()
        assertEquals("Filme Exemplo", d.movie.name)
        assertEquals("mkv", d.movie.containerExtension)
        assertEquals(5400L, d.durationSeconds)
        assertEquals("https://img.example.test/fundo.jpg", d.backdropUrl)
        fake.on("get_vod_info", body = "[]")
        assertTrue(connector().movieDetails("1").err() is AppError.InvalidResponse)
    }

    @Test fun `EPG curto decodifica base64 e mantem texto comum`() = runBlocking<Unit> {
        fake.on(
            "get_short_epg",
            body = """{"epg_listings":[
                {"title":"UHJvZ3JhbWEgRXhlbXBsbw==","description":"RGVzY3JpY2FvIGV4ZW1wbG8=","start_timestamp":"1760000100","stop_timestamp":"1760003700"},
                {"title":"News","start_timestamp":"1760003700","stop_timestamp":"1760007300"},
                {"title":"QW50aWdv","start_timestamp":"1750000000","stop_timestamp":"1750003600"}
            ]}""",
        )
        fake.on("get_live_categories", body = fixture("xtream/live_categories.json"))
        fake.on("get_live_streams", body = fixture("xtream/live_streams.json"))
        val ch = connector().loadLiveCatalog().ok().channels.first()
        val epg = connector().shortEpg(ch, 4).ok()
        assertEquals(listOf("Programa Exemplo", "News"), epg.map { it.title }) // passado é descartado
        assertEquals("Descricao exemplo", epg[0].description)
    }

    @Test fun `URLs de reproducao usam host configurado e codificam credenciais`() = runBlocking<Unit> {
        fake.on("auth", body = fixture("xtream/auth_ok.json"))
        val c = connector(password = "a/b c")
        c.authenticate().ok()
        fun req(type: ContentType, ext: String?) = PlaybackRequest("src1", "id", type, "t", null, "101", ext)
        assertEquals("$base/live/usuario%20teste/a%2Fb%20c/101.m3u8", c.resolveStream(req(ContentType.CHANNEL, null)).ok().url)
        assertEquals("$base/live/usuario%20teste/a%2Fb%20c/101.ts", c.resolveStream(req(ContentType.CHANNEL, "ts")).ok().url)
        assertEquals("$base/movie/usuario%20teste/a%2Fb%20c/101.mkv", c.resolveStream(req(ContentType.MOVIE, "mkv")).ok().url)
        assertEquals("$base/series/usuario%20teste/a%2Fb%20c/101.mp4", c.resolveStream(req(ContentType.EPISODE, null)).ok().url)
        assertTrue("extensão maliciosa é ignorada", c.resolveStream(req(ContentType.MOVIE, "mp4/../x")).ok().url.endsWith(".mp4"))
        assertTrue(c.resolveStream(req(ContentType.CHANNEL, null)).ok().toString().contains("redacted"))
    }
}
