package com.samdevelop.samtv

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.ui.common.ErrorMessages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tratamento de erros: toda falha de domínio vira uma mensagem clara, sem dados sensíveis. */
class ErrorMessagesTest {
    private val all: List<AppError> = listOf(
        AppError.InvalidInput(AppError.Field.SERVER_URL, "empty"),
        AppError.InvalidInput(AppError.Field.PLAYLIST_URL, "syntax"),
        AppError.InvalidInput(AppError.Field.USERNAME),
        AppError.InvalidInput(AppError.Field.PASSWORD),
        AppError.InvalidInput(AppError.Field.NAME),
        AppError.Timeout,
        AppError.Network("https://x.example.test/player_api.php?username=joao&password=segredo"),
        AppError.Tls("cert"),
        AppError.Http(404), AppError.Http(503), AppError.Http(418),
        AppError.Unauthorized,
        AppError.AccountExpired(1_600_000_000), AppError.AccountExpired(null),
        AppError.AccountDisabled("Banned"),
        AppError.InsecureConnectionNotAllowed,
        AppError.RedirectBlocked("x"),
        AppError.InvalidResponse("x"),
        AppError.PlaylistParse("x"),
        AppError.EmptyPlaylist,
        AppError.UnsupportedFeature,
        AppError.NotFound,
        AppError.NoActiveSource,
        AppError.Storage("x"),
        AppError.Unknown("x"),
    )

    @Test fun `todas as falhas tem mensagem e nenhuma vaza detalhes`() {
        for (e in all) {
            val msg = ErrorMessages.of(e)
            assertTrue("mensagem vazia para $e", msg.length > 10)
            assertFalse("vazou credencial em $e", msg.contains("segredo") || msg.contains("joao"))
        }
    }

    @Test fun `expiracao mostra data formatada`() {
        assertTrue(ErrorMessages.of(AppError.AccountExpired(1_600_000_000)).contains("/2020"))
    }
}
