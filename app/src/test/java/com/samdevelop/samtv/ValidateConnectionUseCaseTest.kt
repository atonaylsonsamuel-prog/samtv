package com.samdevelop.samtv

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.domain.usecase.ConnectionForm
import com.samdevelop.samtv.domain.usecase.ValidateConnectionUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cadastro de conexão: validação e normalização do formulário (sem rede). */
class ValidateConnectionUseCaseTest {
    private val validate = ValidateConnectionUseCase(clock = { 1000L }, newId = { "id-1" })

    private fun ok(form: ConnectionForm) = (validate(form) as AppResult.Success).value
    private fun err(form: ConnectionForm) = (validate(form) as AppResult.Failure).error

    @Test fun `xtream valido separa perfil e segredo`() {
        val (profile, secrets) = ok(ConnectionForm(SourceType.XTREAM, server = "painel.example.test:8443/", username = " usuario ", password = "senha"))
        assertEquals("https://painel.example.test:8443", profile.address)
        assertEquals("usuario", profile.username)
        assertEquals("painel.example.test", profile.name) // nome padrão = host
        assertEquals("senha", secrets.password)
        assertFalse("perfil nunca carrega senha", profile.toString().contains("senha"))
        assertEquals("SourceSecrets(***)", secrets.toString())
    }

    @Test fun `campos obrigatorios`() {
        assertEquals(AppError.Field.SERVER_URL, (err(ConnectionForm(SourceType.XTREAM, username = "u", password = "p")) as AppError.InvalidInput).field)
        assertEquals(AppError.Field.USERNAME, (err(ConnectionForm(SourceType.XTREAM, server = "https://a.example.test", password = "p")) as AppError.InvalidInput).field)
        assertEquals(AppError.Field.PASSWORD, (err(ConnectionForm(SourceType.XTREAM, server = "https://a.example.test", username = "u")) as AppError.InvalidInput).field)
        assertEquals(AppError.Field.PLAYLIST_URL, (err(ConnectionForm(SourceType.M3U)) as AppError.InvalidInput).field)
        assertEquals(AppError.Field.NAME, (err(ConnectionForm(SourceType.M3U, name = "x".repeat(61), playlistUrl = "https://a.example.test/l.m3u")) as AppError.InvalidInput).field)
    }

    @Test fun `http exige consentimento explicito`() {
        val form = ConnectionForm(SourceType.XTREAM, server = "http://a.example.test:8080", username = "u", password = "p")
        assertEquals(AppError.InsecureConnectionNotAllowed, err(form))
        assertTrue(ok(form.copy(allowInsecureHttp = true)).first.allowInsecureHttp)
        // https nunca fica marcado como inseguro, mesmo se o usuário marcou a caixa
        assertFalse(ok(form.copy(server = "https://a.example.test", allowInsecureHttp = true)).first.allowInsecureHttp)
    }

    @Test fun `m3u guarda URL completa como segredo e exibe so o caminho`() {
        val (profile, secrets) = ok(ConnectionForm(SourceType.M3U, playlistUrl = "https://lista.example.test/get.php?username=a&password=b&type=m3u"))
        assertEquals("https://lista.example.test/get.php", profile.address)
        assertTrue(secrets.playlistUrl!!.contains("password=b"))
        assertFalse(profile.address.contains("password"))
    }

    @Test fun `edicao mantem senha salva quando o campo fica vazio`() {
        val (existing, _) = ok(ConnectionForm(SourceType.XTREAM, server = "https://a.example.test", username = "u", password = "antiga"))
        val (edited, secrets) = (validate(
            ConnectionForm(SourceType.XTREAM, name = "Novo nome", server = "https://a.example.test", username = "u", password = ""),
            existing, SourceSecrets(password = "antiga"),
        ) as AppResult.Success).value
        assertEquals(existing.id, edited.id)
        assertEquals("antiga", secrets.password)
        assertEquals("Novo nome", edited.name)
    }
}
