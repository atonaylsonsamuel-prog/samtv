package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.net.SecretRedactor
import com.samdevelop.samtv.core.net.UrlRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlAndSecurityTest {

    private fun norm(s: String) = (UrlRules.normalizeXtreamBase(s) as AppResult.Success).value.value
    private fun normErr(s: String) = (UrlRules.normalizeXtreamBase(s) as AppResult.Failure).error

    @Test fun `normaliza endereco xtream`() {
        assertEquals("https://painel.example.test", norm("painel.example.test"))
        assertEquals("http://painel.example.test:8080", norm(" http://Painel.Example.TEST:8080/ "))
        assertEquals("http://painel.example.test:8080", norm("http://painel.example.test:8080/player_api.php?username=a&password=b"))
        assertEquals("http://painel.example.test:8080", norm("http://painel.example.test:8080/get.php?username=a&password=b&type=m3u_plus"))
        assertEquals("https://painel.example.test/prefixo/xc", norm("https://painel.example.test/prefixo/xc/"))
    }

    @Test fun `rejeita enderecos invalidos`() {
        assertTrue(normErr("") is AppError.InvalidInput)
        assertTrue(normErr("ftp://painel.example.test") is AppError.InvalidInput)
        assertTrue(normErr("https://usuario:senha@painel.example.test") is AppError.InvalidInput)
        assertTrue(normErr("https://pai nel.example.test") is AppError.InvalidInput)
        assertTrue(normErr("https://") is AppError.InvalidInput)
    }

    @Test fun `extrai partes de URL get php`() {
        val p = UrlRules.extractXtreamParts("http://painel.example.test:8080/get.php?username=usr&password=p%40ss&type=m3u_plus")!!
        assertEquals("http://painel.example.test:8080", p.baseUrl)
        assertEquals("usr", p.username)
        assertEquals("p@ss", p.password)
        assertNull(UrlRules.extractXtreamParts("https://lista.example.test/lista.m3u"))
    }

    @Test fun `politica de redirecionamento`() {
        val src = "https://painel.example.test/player_api.php?username=a&password=b"
        assertTrue(UrlRules.isRedirectAllowed(src, "https://painel.example.test/outro", true, false))
        assertFalse("outro host com credenciais", UrlRules.isRedirectAllowed(src, "https://outro.example.test/x", true, false))
        assertTrue("outro host sem credenciais", UrlRules.isRedirectAllowed(src, "https://cdn.example.test/x", false, false))
        assertFalse("rebaixamento https→http", UrlRules.isRedirectAllowed(src, "http://painel.example.test/x", true, true))
        assertFalse("http sem consentimento", UrlRules.isRedirectAllowed("http://a.example.test/", "http://a.example.test/b", false, false))
        assertFalse("esquema estranho", UrlRules.isRedirectAllowed(src, "file:///etc/passwd", false, true))
    }

    @Test fun `redacao de credenciais`() {
        val u = "http://usr:pwd@host.example.test/get.php?username=joao&password=123&type=m3u&token=zz"
        val r = SecretRedactor.redactUrl(u)
        assertFalse(r.contains("joao")); assertFalse(r.contains("123")); assertFalse(r.contains("pwd")); assertFalse(r.contains("zz"))
        assertTrue(r.contains("type=m3u"))
        val stream = SecretRedactor.redactUrl("https://host.example.test/live/joao/123/55.ts")
        assertEquals("https://host.example.test/live/***/***/55.ts", stream)
        assertEquals("erro *** aqui", SecretRedactor.redactText("erro segredo aqui", listOf("segredo")))
    }

    @Test fun `endereco de exibicao de playlist omite query`() {
        assertEquals("https://lista.example.test/get.php", UrlRules.displayAddress("https://lista.example.test/get.php?username=a&password=b"))
    }
}
