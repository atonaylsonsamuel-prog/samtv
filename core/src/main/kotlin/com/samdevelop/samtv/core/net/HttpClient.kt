package com.samdevelop.samtv.core.net

import java.io.IOException

/**
 * Abstração mínima de HTTP usada pelos conectores. A implementação Android usa OkHttp
 * (app/data/remote/OkHttpHttpClient.kt); os testes usam um cliente simulado.
 */
interface HttpClient {
    /** GET com corpo completo em memória (respostas JSON). */
    suspend fun get(request: HttpRequest): HttpResponse

    /**
     * GET com leitura incremental linha a linha (playlists grandes).
     * [onLines] é chamado em thread de I/O com a sequência de linhas do corpo.
     * Retorna o código HTTP e o resultado de [onLines] (null se o código não for 2xx).
     */
    suspend fun <T> getLines(request: HttpRequest, onLines: (Sequence<String>) -> T): Pair<Int, T?>
}

data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** A URL contém credenciais: redirecionamentos para outro host serão bloqueados. */
    val containsCredentials: Boolean,
    /** O usuário consentiu com HTTP sem criptografia para esta fonte. */
    val allowInsecureHttp: Boolean,
) {
    override fun toString(): String = "HttpRequest(url=${SecretRedactor.redactUrl(url)})"
}

data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
    override fun toString(): String = "HttpResponse(code=$code, bodyLength=${body.length})"
}

/** Falhas de transporte. Mensagens sempre sem credenciais. */
sealed class HttpFailure(message: String) : IOException(message) {
    class Timeout(message: String = "timeout") : HttpFailure(message)
    class Connection(message: String) : HttpFailure(message)
    class Tls(message: String) : HttpFailure(message)
    class RedirectBlocked(message: String) : HttpFailure(message)
    class InsecureNotAllowed(message: String = "cleartext not allowed") : HttpFailure(message)
    class TooLarge(message: String) : HttpFailure(message)
}
