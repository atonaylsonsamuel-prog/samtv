package com.samdevelop.samtv.core

import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.HttpResponse
import com.samdevelop.samtv.core.net.UrlRules
import java.net.URI

fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.classLoader.getResource("fixtures/$path")) { "fixture ausente: $path" }.readText()

/**
 * Servidor simulado: responde por "action" do player_api.php (ou "auth" sem action),
 * ou pela URL completa no caso de playlists. Registra as requisições recebidas.
 */
class FakeHttpClient : HttpClient {
    val requests = mutableListOf<HttpRequest>()
    private val routes = mutableMapOf<String, () -> HttpResponse>()

    fun on(key: String, code: Int = 200, body: String) { routes[key] = { HttpResponse(code, body) } }
    fun fail(key: String, failure: HttpFailure) { routes[key] = { throw failure } }

    private fun keyOf(url: String): String {
        val uri = URI(url)
        if (uri.rawPath.endsWith("/player_api.php")) {
            return UrlRules.parseQuery(uri.rawQuery)["action"] ?: "auth"
        }
        return url
    }

    override suspend fun get(request: HttpRequest): HttpResponse {
        requests += request
        val route = routes[keyOf(request.url)] ?: return HttpResponse(404, "")
        return route()
    }

    override suspend fun <T> getLines(request: HttpRequest, onLines: (Sequence<String>) -> T): Pair<Int, T?> {
        val response = get(request)
        return if (response.isSuccessful) response.code to onLines(response.body.lineSequence()) else response.code to null
    }
}
