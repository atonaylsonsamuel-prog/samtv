package com.samdevelop.samtv.domain.usecase

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.net.UrlRules
import java.util.UUID

/** Dados brutos digitados no formulário de conexão. */
data class ConnectionForm(
    val type: SourceType,
    val name: String = "",
    val server: String = "",
    val username: String = "",
    /** Em edição, senha vazia significa "manter a atual". */
    val password: String = "",
    val playlistUrl: String = "",
    val allowInsecureHttp: Boolean = false,
)

/**
 * Valida e normaliza o formulário, produzindo o perfil (sem segredos) e os segredos separados.
 * Não acessa rede nem armazenamento — é testável em JVM puro.
 */
class ValidateConnectionUseCase(
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    operator fun invoke(
        form: ConnectionForm,
        existing: ConnectionProfile? = null,
        existingSecrets: SourceSecrets? = null,
    ): AppResult<Pair<ConnectionProfile, SourceSecrets>> {
        if (form.name.length > 60) return fail(AppError.Field.NAME, "too long")
        val now = clock()
        return when (form.type) {
            SourceType.XTREAM -> {
                val base = when (val r = UrlRules.normalizeXtreamBase(form.server)) {
                    is AppResult.Failure -> return r
                    is AppResult.Success -> r.value
                }
                val user = form.username.trim()
                if (user.isEmpty()) return fail(AppError.Field.USERNAME, "empty")
                val password = form.password.ifEmpty { existingSecrets?.password.orEmpty() }
                if (password.isEmpty()) return fail(AppError.Field.PASSWORD, "empty")
                if (!base.isHttps && !form.allowInsecureHttp) return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
                val profile = ConnectionProfile(
                    id = existing?.id ?: newId(),
                    name = form.name.trim().ifEmpty { base.host },
                    type = SourceType.XTREAM,
                    address = base.value,
                    username = user,
                    allowInsecureHttp = form.allowInsecureHttp && !base.isHttps,
                    createdAtMillis = existing?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                )
                AppResult.Success(profile to SourceSecrets(password = password))
            }
            SourceType.M3U -> {
                val raw = form.playlistUrl.trim().ifEmpty { existingSecrets?.playlistUrl.orEmpty() }
                val url = when (val r = UrlRules.validatePlaylistUrl(raw)) {
                    is AppResult.Failure -> return r
                    is AppResult.Success -> r.value
                }
                if (!url.isHttps && !form.allowInsecureHttp) return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
                val profile = ConnectionProfile(
                    id = existing?.id ?: newId(),
                    name = form.name.trim().ifEmpty { url.host },
                    type = SourceType.M3U,
                    address = UrlRules.displayAddress(url.value),
                    username = null,
                    allowInsecureHttp = form.allowInsecureHttp,
                    createdAtMillis = existing?.createdAtMillis ?: now,
                    updatedAtMillis = now,
                )
                AppResult.Success(profile to SourceSecrets(playlistUrl = url.value))
            }
        }
    }

    private fun fail(field: AppError.Field, detail: String) = AppResult.Failure(AppError.InvalidInput(field, detail))
}
