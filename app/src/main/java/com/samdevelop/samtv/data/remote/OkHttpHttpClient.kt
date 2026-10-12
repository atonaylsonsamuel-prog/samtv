package com.samdevelop.samtv.data.remote

import com.samdevelop.samtv.BuildConfig
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.HttpResponse
import com.samdevelop.samtv.core.net.SecretRedactor
import com.samdevelop.samtv.core.net.UrlRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementação OkHttp do [HttpClient] do core.
 *
 * - Redirecionamentos automáticos DESLIGADOS: cada salto passa por [UrlRules.isRedirectAllowed]
 *   (bloqueia https→http, http sem consentimento e troca de host quando há credenciais na URL).
 * - Verificação TLS padrão do sistema (nunca desativada).
 * - Mensagens de erro passam por [SecretRedactor].
 */
class OkHttpHttpClient(
    base: OkHttpClient = defaultClient(),
    private val maxBodyBytes: Long = 150L * 1024 * 1024,
) : HttpClient {

    private val client = base.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun get(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        execute(request) { response ->
            val body = response.body ?: return@execute HttpResponse(response.code, "")
            val length = body.contentLength()
            if (length > maxBodyBytes) throw HttpFailure.TooLarge("body $length bytes")
            HttpResponse(response.code, body.string())
        }
    }

    override suspend fun <T> getLines(request: HttpRequest, onLines: (Sequence<String>) -> T): Pair<Int, T?> =
        withContext(Dispatchers.IO) {
            execute(request) { response ->
                if (!response.isSuccessful) return@execute response.code to null
                val body = response.body ?: return@execute response.code to onLines(emptySequence())
                body.charStream().buffered().use { reader -> response.code to onLines(reader.lineSequence()) }
            }
        }

    private suspend fun <T> execute(request: HttpRequest, read: (Response) -> T): T {
        var url = request.url
        ensureSchemeAllowed(url, request.allowInsecureHttp)
        repeat(MAX_REDIRECTS + 1) {
            val okRequest = Request.Builder().url(url).get()
                .header("User-Agent", USER_AGENT)
                .apply { request.headers.forEach { (k, v) -> header(k, v) } }
                .build()
            val response = await(client.newCall(okRequest))
            if (!response.isRedirect) return response.use(read)
            val location = response.header("Location")
            response.close()
            val next = location?.let { okRequest.url.resolve(it)?.toString() }
                ?: throw HttpFailure.Connection("redirect without location")
            if (!UrlRules.isRedirectAllowed(url, next, request.containsCredentials, request.allowInsecureHttp)) {
                throw HttpFailure.RedirectBlocked("redirect to ${SecretRedactor.redactUrl(next)} blocked")
            }
            url = next
        }
        throw HttpFailure.Connection("too many redirects")
    }

    private fun ensureSchemeAllowed(url: String, allowInsecure: Boolean) {
        if (url.startsWith("http://", ignoreCase = true) && !allowInsecure) throw HttpFailure.InsecureNotAllowed()
    }

    /** Executa a chamada cancelando-a se a coroutine for cancelada. */
    private suspend fun await(call: Call): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        try {
            val response = call.execute()
            cont.resume(response)
        } catch (e: IOException) {
            cont.resumeWithException(mapIo(e))
        }
    }

    private fun mapIo(e: IOException): IOException = when (e) {
        is HttpFailure -> e
        is SocketTimeoutException -> HttpFailure.Timeout()
        is InterruptedIOException -> if (e.message?.contains("timeout", true) == true) HttpFailure.Timeout() else HttpFailure.Connection("interrupted")
        is SSLException -> HttpFailure.Tls(SecretRedactor.redactText(e.message ?: "tls"))
        is UnknownHostException -> HttpFailure.Connection("unknown host")
        else -> HttpFailure.Connection(SecretRedactor.redactText(e.message ?: e.javaClass.simpleName))
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        val USER_AGENT = "SamTV/${BuildConfig.VERSION_NAME} (Android)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
