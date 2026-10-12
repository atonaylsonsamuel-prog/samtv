package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.catalog.M3uCatalogMapper
import com.samdevelop.samtv.core.m3u.M3uIssueKind
import com.samdevelop.samtv.core.m3u.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    private val parser = M3uParser()

    @Test fun `playlist valida extrai atributos e URLs`() {
        val r = parser.parse(fixture("m3u/valid.m3u"))
        assertTrue(r.hasHeader)
        assertEquals(3, r.entries.size)
        val first = r.entries[0]
        assertEquals("Exemplo Um", first.title)
        assertEquals("exemplo.um", first.tvgId)
        assertEquals("Exemplo Um", first.tvgName)
        assertEquals("https://img.example.test/um.png", first.tvgLogo)
        assertEquals("Exemplos A", first.groupTitle)
        assertEquals(-1, first.durationSeconds)
        assertEquals("https://stream.example.test/live/um.m3u8", first.url)
        assertEquals(7, r.entries[2].channelNumber)
        assertEquals(0, r.issueCount)
    }

    @Test fun `URL com parametros e preservada integralmente`() {
        val r = parser.parse(fixture("m3u/valid.m3u"))
        assertEquals("https://stream.example.test/live/tres.m3u8?token=abc123&quality=hd", r.entries[2].url)
    }

    @Test fun `playlist vazia nao gera entradas nem erro`() {
        val r = parser.parse("")
        assertTrue(r.entries.isEmpty())
        assertFalse(r.hasHeader)
        val onlyHeader = parser.parse("#EXTM3U\n\n")
        assertTrue(onlyHeader.hasHeader)
        assertTrue(onlyHeader.entries.isEmpty())
    }

    @Test fun `comentarios e diretivas desconhecidas sao ignorados`() {
        val text = "#EXTM3U\n# comentario\n#EXT-X-VERSION:3\n#PLAYLIST:Nome\n#EXTINF:-1,Canal\nhttps://x.example.test/a.m3u8\n# fim"
        val r = parser.parse(text)
        assertEquals(1, r.entries.size)
        assertEquals("Canal", r.entries[0].title)
        assertEquals(0, r.issueCount)
    }

    @Test fun `atributos com aspas simples, sem aspas e virgula dentro de aspas`() {
        val r = parser.parse(fixture("m3u/quirks.m3u"))
        val a = r.entries.first { it.url.endsWith("/a.m3u8") }
        assertEquals("aspas.simples", a.tvgId)
        assertEquals("Grupo, com vírgula", a.groupTitle)
        assertEquals("Canal Aspas Simples", a.title)
        val b = r.entries.first { it.url.endsWith("/b.m3u8") }
        assertEquals("SemAspas", b.groupTitle)
        assertEquals("Nome via atributo", b.title) // título vazio → tvg-name
    }

    @Test fun `atributos ausentes resultam em null`() {
        val r = parser.parse(fixture("m3u/quirks.m3u"))
        val c = r.entries.first { it.url.endsWith("/c.m3u8") }
        assertEquals("Somente Titulo", c.title)
        assertNull(c.tvgId)
        assertNull(c.tvgLogo)
        assertNull(c.groupTitle)
    }

    @Test fun `linhas invalidas e entradas incompletas sao reportadas`() {
        val r = parser.parse(fixture("m3u/quirks.m3u"))
        val kinds = r.issues.map { it.kind }
        assertEquals(2, kinds.count { it == M3uIssueKind.MISSING_URL }) // "Entrada sem URL" e a última
        assertEquals(2, kinds.count { it == M3uIssueKind.UNSUPPORTED_URL }) // ftp:// e "linha solta inválida"
        assertTrue(r.entries.none { it.title == "Entrada sem URL" })
        assertTrue(r.entries.any { it.title == "Depois da entrada incompleta" })
    }

    @Test fun `EXTGRP e EXTVLCOPT sao aplicados a proxima entrada`() {
        val r = parser.parse(fixture("m3u/quirks.m3u"))
        val f = r.entries.first { it.url.endsWith("/f.m3u8") }
        assertEquals("Grupo via EXTGRP", f.groupTitle)
        assertEquals("ExemploAgent/1.0", f.httpHeaders["User-Agent"])
        assertEquals("https://ref.example.test/", f.httpHeaders["Referer"])
        val g = r.entries.first { it.url.endsWith("/g.mp4") }
        assertTrue("cabeçalhos não vazam para a entrada seguinte", g.httpHeaders.isEmpty())
        assertEquals(10, g.durationSeconds)
    }

    @Test fun `duplicatas com mesmo identificador e URL sao removidas`() {
        val r = parser.parse(fixture("m3u/quirks.m3u"))
        assertEquals(1, r.duplicatesRemoved)
        assertEquals(1, r.entries.count { it.url.endsWith("/d.m3u8") })
        // mesma URL com títulos diferentes NÃO é duplicata (não há identificador confiável em comum)
        val two = parser.parse("#EXTINF:-1,A\nhttps://x.example.test/s\n#EXTINF:-1,B\nhttps://x.example.test/s")
        assertEquals(2, two.entries.size)
    }

    @Test fun `CRLF, BOM e espacos extras`() {
        val text = "﻿#EXTM3U\r\n   #EXTINF:-1   tvg-id=\"x\"  ,  Canal CRLF  \r\n  https://x.example.test/a.m3u8  \r\n"
        val r = parser.parse(text)
        assertTrue(r.hasHeader)
        assertEquals(1, r.entries.size)
        assertEquals("Canal CRLF", r.entries[0].title)
        assertEquals("https://x.example.test/a.m3u8", r.entries[0].url)
    }

    @Test fun `ordem original e preservada`() {
        val r = parser.parse(fixture("m3u/valid.m3u"))
        assertEquals(listOf("Exemplo Um", "Exemplo Dois", "Exemplo Três"), r.entries.map { it.title })
    }

    @Test fun `playlist grande e processada de forma incremental`() {
        val n = 100_000
        val lines = sequence {
            yield("#EXTM3U")
            for (i in 0 until n) {
                yield("#EXTINF:-1 tvg-id=\"id$i\" group-title=\"Grupo ${i % 50}\",Canal $i")
                yield("https://stream.example.test/live/$i.ts")
            }
        }
        val start = System.nanoTime()
        val r = parser.parse(lines)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(n, r.entries.size)
        assertEquals(0, r.issueCount)
        val catalog = M3uCatalogMapper.toLiveCatalog("src", r.entries)
        assertEquals(50, catalog.categories.size)
        assertEquals(n, catalog.channels.map { it.id }.toSet().size)
        assertTrue("parsing de $n entradas levou ${ms}ms", ms < 15_000)
    }
}
