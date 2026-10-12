package com.samdevelop.samtv.core.net

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Endereço já validado. */
data class ValidatedUrl(val value: String, val scheme: String, val host: String) {
    val isHttps: Boolean get() = scheme == "https"
}

/** Dados extraídos de uma URL get.php / player_api.php colada pelo usuário. */
data class XtreamUrlParts(val baseUrl: String, val username: String?, val password: String?)

object UrlRules {

    private val XTREAM_ENDPOINT_SUFFIXES = listOf("/player_api.php", "/get.php", "/panel_api.php", "/xmltv.php")

    /**
     * Normaliza o endereço-base de um servidor Xtream Codes.
     *  - remove espaços e a barra final;
     *  - assume https:// quando o esquema não é informado;
     *  - remove o nome de endpoints conhecidos (player_api.php, get.php...) e a query;
     *  - preserva prefixos de caminho válidos (ex.: https://host/painel);
     *  - rejeita usuário/senha embutidos na URL, esquemas diferentes de http/https e hosts vazios.
     */
    fun normalizeXtreamBase(raw: String): AppResult<ValidatedUrl> {
        val parsed = parseHttpUrl(raw, AppError.Field.SERVER_URL)
        if (parsed is AppResult.Failure) return parsed
        val uri = (parsed as AppResult.Success).value
        var path = uri.rawPath.orEmpty().trimEnd('/')
        XTREAM_ENDPOINT_SUFFIXES.firstOrNull { path.endsWith(it, ignoreCase = true) }?.let {
            path = path.dropLast(it.length).trimEnd('/')
        }
        val base = buildString {
            append(uri.scheme.lowercase()).append("://").append(uri.host.lowercase())
            if (uri.port != -1) append(':').append(uri.port)
            append(path)
        }
        return AppResult.Success(ValidatedUrl(base, uri.scheme.lowercase(), uri.host.lowercase()))
    }

    /** Valida a URL de uma playlist M3U, preservando caminho e query (que podem conter o token). */
    fun validatePlaylistUrl(raw: String): AppResult<ValidatedUrl> {
        val parsed = parseHttpUrl(raw, AppError.Field.PLAYLIST_URL)
        if (parsed is AppResult.Failure) return parsed
        val uri = (parsed as AppResult.Success).value
        return AppResult.Success(ValidatedUrl(uri.toASCIIString(), uri.scheme.lowercase(), uri.host.lowercase()))
    }

    /**
     * Se o usuário colar uma URL do tipo http://host/get.php?username=a&password=b&type=m3u,
     * extrai base, usuário e senha para preencher o formulário Xtream.
     */
    fun extractXtreamParts(raw: String): XtreamUrlParts? {
        val uri = try { URI(raw.trim()) } catch (_: URISyntaxException) { return null }
        val path = uri.rawPath.orEmpty()
        if (XTREAM_ENDPOINT_SUFFIXES.none { path.endsWith(it, ignoreCase = true) }) return null
        val query = parseQuery(uri.rawQuery)
        val base = normalizeXtreamBase(raw).getOrNull()?.value ?: return null
        return XtreamUrlParts(base, query["username"], query["password"])
    }

    /** Endereço exibível de uma playlist: esquema + host + caminho, sem query nem userinfo. */
    fun displayAddress(url: String): String = try {
        val uri = URI(url)
        buildString {
            append(uri.scheme).append("://").append(uri.host)
            if (uri.port != -1) append(':').append(uri.port)
            append(uri.rawPath.orEmpty())
        }
    } catch (_: Exception) {
        SecretRedactor.redactUrl(url)
    }

    /**
     * Política de redirecionamento.
     *  - nunca permite rebaixar https → http;
     *  - http só é aceito se o usuário consentiu ([allowInsecure]);
     *  - quando a requisição carrega credenciais, só segue redirecionamentos para o mesmo host.
     */
    fun isRedirectAllowed(from: String, to: String, containsCredentials: Boolean, allowInsecure: Boolean): Boolean {
        val src = try { URI(from) } catch (_: Exception) { return false }
        val dst = try { URI(to) } catch (_: Exception) { return false }
        val dstScheme = dst.scheme?.lowercase() ?: return false
        if (dstScheme != "http" && dstScheme != "https") return false
        if (src.scheme.equals("https", true) && dstScheme == "http") return false
        if (dstScheme == "http" && !allowInsecure) return false
        if (containsCredentials && !src.host.equals(dst.host, ignoreCase = true)) return false
        return true
    }

    fun encodeQuery(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    /** Codifica um segmento de caminho (espaço vira %20, não "+"). */
    fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrEmpty()) return emptyMap()
        return rawQuery.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = java.net.URLDecoder.decode(pair.substring(0, idx), "UTF-8")
            val value = java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            key to value
        }.toMap()
    }

    private fun parseHttpUrl(raw: String, field: AppError.Field): AppResult<URI> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return AppResult.Failure(AppError.InvalidInput(field, "empty"))
        if (trimmed.any { it.isWhitespace() }) return AppResult.Failure(AppError.InvalidInput(field, "whitespace"))
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = try { URI(withScheme) } catch (_: URISyntaxException) {
            return AppResult.Failure(AppError.InvalidInput(field, "syntax"))
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return AppResult.Failure(AppError.InvalidInput(field, "scheme"))
        if (uri.host.isNullOrBlank()) return AppResult.Failure(AppError.InvalidInput(field, "host"))
        if (uri.rawUserInfo != null) return AppResult.Failure(AppError.InvalidInput(field, "userinfo"))
        if (uri.port != -1 && uri.port !in 1..65535) return AppResult.Failure(AppError.InvalidInput(field, "port"))
        return AppResult.Success(uri)
    }
}
