package com.samdevelop.samtv.core.common

/** Resultado de operações que podem falhar de forma esperada (rede, autenticação, parsing). */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(value)
    is AppResult.Failure -> this
}

/**
 * Erros de domínio. As mensagens para o usuário ficam na camada de interface
 * (app: ui/common/ErrorMessages.kt). Os campos [detail] nunca devem conter
 * credenciais — use [com.samdevelop.samtv.core.net.SecretRedactor].
 */
sealed class AppError(open val detail: String? = null) {
    data class InvalidInput(val field: Field, override val detail: String? = null) : AppError(detail)
    data object Timeout : AppError()
    data class Network(override val detail: String? = null) : AppError(detail)
    data class Tls(override val detail: String? = null) : AppError(detail)
    data class Http(val code: Int) : AppError("HTTP $code")
    data object Unauthorized : AppError()
    data class AccountExpired(val expiresAtEpochSeconds: Long?) : AppError()
    data class AccountDisabled(val status: String) : AppError(status)
    data object InsecureConnectionNotAllowed : AppError()
    data class RedirectBlocked(override val detail: String? = null) : AppError(detail)
    data class InvalidResponse(override val detail: String? = null) : AppError(detail)
    data class PlaylistParse(override val detail: String? = null) : AppError(detail)
    data object EmptyPlaylist : AppError()
    data object UnsupportedFeature : AppError()
    data object NotFound : AppError()
    data object NoActiveSource : AppError()
    data class Storage(override val detail: String? = null) : AppError(detail)
    data class Unknown(override val detail: String? = null) : AppError(detail)

    enum class Field { NAME, SERVER_URL, USERNAME, PASSWORD, PLAYLIST_URL }
}
