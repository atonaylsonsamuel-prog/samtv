package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.catalog.HistoryPolicy
import com.samdevelop.samtv.core.catalog.M3uCatalogMapper
import com.samdevelop.samtv.core.catalog.SearchText
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.m3u.M3uConnector
import com.samdevelop.samtv.core.m3u.M3uParser
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.PlaybackRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRulesTest {

    @Test fun `busca ignora acentos, caixa e ordem dos termos`() {
        assertTrue(SearchText.matches("sao paulo", "Canal São Paulo HD"))
        assertTrue(SearchText.matches("HD paulo", "Canal São Paulo HD"))
        assertFalse(SearchText.matches("rio", "Canal São Paulo HD"))
        assertTrue(SearchText.matches("  ", "qualquer"))
    }

    @Test fun `playlist sem grupos usa organizacao alternativa por letra`() {
        val entries = M3uParser().parse("#EXTINF:-1,beta\nhttps://x.example.test/1\n#EXTINF:-1,Álfa\nhttps://x.example.test/2\n#EXTINF:-1,123\nhttps://x.example.test/3").entries
        val cat = M3uCatalogMapper.toLiveCatalog("s", entries)
        assertEquals(listOf("A", "B", "#"), cat.categories.map { it.name })
        assertEquals("A", cat.categories.first { it.id == cat.channels[1].categoryId }.name)
    }

    @Test fun `playlist mista agrupa itens sem grupo em Sem categoria`() {
        val entries = M3uParser().parse("#EXTINF:-1 group-title=\"G\",a\nhttps://x.example.test/1\n#EXTINF:-1,b\nhttps://x.example.test/2").entries
        val cat = M3uCatalogMapper.toLiveCatalog("s", entries)
        assertEquals(listOf("G", M3uCatalogMapper.UNCATEGORIZED_NAME), cat.categories.map { it.name })
    }

    @Test fun `ids de canais M3U sao estaveis entre atualizacoes mesmo se a URL mudar`() {
        val a = M3uParser().parse("#EXTINF:-1 tvg-id=\"x\",Canal\nhttps://x.example.test/1?token=AAA").entries
        val b = M3uParser().parse("#EXTINF:-1 tvg-id=\"x\",Canal\nhttps://x.example.test/1?token=BBB").entries
        assertEquals(M3uCatalogMapper.toLiveCatalog("s", a).channels[0].id, M3uCatalogMapper.toLiveCatalog("s", b).channels[0].id)
    }

    @Test fun `historico sem duplicacao e com limite`() {
        fun h(id: String, t: Long, parent: String? = null) =
            PlaybackHistory("s", id, if (parent != null) ContentType.EPISODE else ContentType.CHANNEL, id, null, parent, 0, null, t)
        var list = emptyList<PlaybackHistory>()
        list = HistoryPolicy.merge(list, h("c1", 1))
        list = HistoryPolicy.merge(list, h("c2", 2))
        list = HistoryPolicy.merge(list, h("c1", 3))
        assertEquals(listOf("c1", "c2"), list.map { it.contentId })
        list = HistoryPolicy.merge(list, h("e1", 4, parent = "serieX"))
        list = HistoryPolicy.merge(list, h("e2", 5, parent = "serieX"))
        assertEquals(listOf("e2", "c1", "c2"), list.map { it.contentId })
        repeat(300) { list = HistoryPolicy.merge(list, h("n$it", 10L + it)) }
        assertEquals(HistoryPolicy.MAX_ENTRIES, list.size)
    }

    @Test fun `conector M3U valida, organiza e protege HTTP`() = runBlocking<Unit> {
        val fake = FakeHttpClient()
        val url = "https://lista.example.test/get.php?username=a&password=b&type=m3u_plus"
        fake.on(url, body = fixture("m3u/valid.m3u"))
        val c = M3uConnector("s", url, allowInsecureHttp = false, http = fake)
        val auth = (c.authenticate() as AppResult.Success).value
        assertEquals(3, auth.itemCount)
        val catalog = (c.loadLiveCatalog() as AppResult.Success).value
        assertEquals(1, fake.requests.size) // usou cache em memória após autenticar
        assertEquals(listOf("Exemplos A", "Exemplos B"), catalog.categories.map { it.name })
        assertTrue((c.loadMovieCatalog() as AppResult.Failure).error is AppError.UnsupportedFeature)

        val insecureStream = PlaybackRequest("s", "id", ContentType.CHANNEL, "t", null, "http://x.example.test/a.ts", null)
        assertEquals(AppError.InsecureConnectionNotAllowed, (c.resolveStream(insecureStream) as AppResult.Failure).error)

        val httpPlaylist = M3uConnector("s", "http://lista.example.test/a.m3u", allowInsecureHttp = false, http = fake)
        assertEquals(AppError.InsecureConnectionNotAllowed, (httpPlaylist.authenticate() as AppResult.Failure).error)
    }

    @Test fun `conector M3U trata playlist vazia e erro HTTP`() = runBlocking<Unit> {
        val fake = FakeHttpClient()
        fake.on("https://l.example.test/vazia.m3u", body = "#EXTM3U\n")
        assertEquals(AppError.EmptyPlaylist, (M3uConnector("s", "https://l.example.test/vazia.m3u", false, fake).authenticate() as AppResult.Failure).error)
        fake.on("https://l.example.test/html", body = "<html><body>login</body></html>")
        assertTrue((M3uConnector("s", "https://l.example.test/html", false, fake).authenticate() as AppResult.Failure).error is AppError.PlaylistParse)
        fake.on("https://l.example.test/500", code = 500, body = "")
        assertEquals(AppError.Http(500), (M3uConnector("s", "https://l.example.test/500", false, fake).authenticate() as AppResult.Failure).error)
    }
}
