package com.samdevelop.samtv.core.m3u

import com.samdevelop.samtv.core.catalog.M3uCatalogMapper
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.LiveCatalog
import com.samdevelop.samtv.core.model.MovieCatalog
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.model.SeriesCatalog
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.model.SourceCapabilities
import com.samdevelop.samtv.core.model.StreamKind
import com.samdevelop.samtv.core.model.StreamSource
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.SecretRedactor
import com.samdevelop.samtv.core.source.SourceConnector
import kotlinx.coroutines.CancellationException

/**
 * Conector para playlists M3U por URL.
 *
 * M3U não possui autenticação própria nem catálogo de filmes/séries estruturado:
 * "autenticar" significa baixar e validar a playlist. Todas as entradas são tratadas
 * como canais — nada é reclassificado por suposição.
 */
class M3uConnector(
    override val sourceId: String,
    private val playlistUrl: String,
    private val allowInsecureHttp: Boolean,
    private val http: HttpClient,
    private val parser: M3uParser = M3uParser(),
    private val clock: () -> Long = System::currentTimeMillis,
) : SourceConnector {

    override val declaredCapabilities = SourceCapabilities(live = true, movies = false, series = false, epg = false)

    private var cached: Pair<Long, LiveCatalog>? = null
    private var lastParse: M3uParseResult? = null

    /** Último resultado de parsing (para exibir avisos: linhas inválidas, duplicatas...). */
    val lastParseResult: M3uParseResult? get() = lastParse

    override suspend fun authenticate(): AppResult<AuthenticationResult> {
        return when (val r = fetch(force = true)) {
            is AppResult.Failure -> r
            is AppResult.Success -> AppResult.Success(
                AuthenticationResult(
                    status = AccountStatus.UNKNOWN, // playlists não informam estado de conta
                    expiresAtEpochSeconds = null,
                    maxConnections = null,
                    activeConnections = null,
                    allowedOutputFormats = emptyList(),
                    serverTimezone = null,
                    capabilities = declaredCapabilities,
                    serverMessage = null,
                    itemCount = r.value.channels.size,
                ),
            )
        }
    }

    override suspend fun loadLiveCatalog(): AppResult<LiveCatalog> = fetch(force = false)

    private suspend fun fetch(force: Boolean): AppResult<LiveCatalog> {
        val now = clock()
        cached?.let { (at, catalog) -> if (!force && now - at < CACHE_MS) return AppResult.Success(catalog) }
        if (!playlistUrl.startsWith("https://", true) && !allowInsecureHttp) {
            return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
        }
        val (code, parsed) = try {
            http.getLines(HttpRequest(playlistUrl, containsCredentials = true, allowInsecureHttp = allowInsecureHttp)) { lines ->
                parser.parse(lines)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpFailure) {
            return AppResult.Failure(
                when (e) {
                    is HttpFailure.Timeout -> AppError.Timeout
                    is HttpFailure.Tls -> AppError.Tls(e.message)
                    is HttpFailure.RedirectBlocked -> AppError.RedirectBlocked(e.message)
                    is HttpFailure.InsecureNotAllowed -> AppError.InsecureConnectionNotAllowed
                    is HttpFailure.TooLarge -> AppError.PlaylistParse("too large")
                    is HttpFailure.Connection -> AppError.Network(SecretRedactor.redactText(e.message ?: "", listOf(playlistUrl)))
                },
            )
        } catch (e: Exception) {
            return AppResult.Failure(AppError.PlaylistParse(SecretRedactor.redactText(e.message ?: e.javaClass.simpleName, listOf(playlistUrl))))
        }
        if (code == 401 || code == 403) return AppResult.Failure(AppError.Unauthorized)
        if (code !in 200..299 || parsed == null) return AppResult.Failure(AppError.Http(code))
        lastParse = parsed
        if (parsed.entries.isEmpty()) {
            return AppResult.Failure(if (parsed.hasHeader) AppError.EmptyPlaylist else AppError.PlaylistParse("not a playlist"))
        }
        val catalog = M3uCatalogMapper.toLiveCatalog(sourceId, parsed.entries)
        cached = now to catalog
        return AppResult.Success(catalog)
    }

    override suspend fun loadMovieCatalog(): AppResult<MovieCatalog> = AppResult.Failure(AppError.UnsupportedFeature)
    override suspend fun loadSeriesCatalog(): AppResult<SeriesCatalog> = AppResult.Failure(AppError.UnsupportedFeature)
    override suspend fun movieDetails(movieId: String): AppResult<MovieDetails> = AppResult.Failure(AppError.UnsupportedFeature)
    override suspend fun seriesDetails(seriesId: String): AppResult<SeriesDetails> = AppResult.Failure(AppError.UnsupportedFeature)
    override suspend fun shortEpg(channel: Channel, limit: Int): AppResult<List<ElectronicProgramGuideEntry>> = AppResult.Success(emptyList())

    override fun resolveStream(request: PlaybackRequest): AppResult<StreamSource> {
        if (request.type != ContentType.CHANNEL) return AppResult.Failure(AppError.UnsupportedFeature)
        val url = request.streamKey
        val scheme = url.substringBefore("://", "").lowercase()
        if (scheme == "http" && !allowInsecureHttp) return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
        if (scheme !in setOf("http", "https", "rtsp")) return AppResult.Failure(AppError.UnsupportedFeature)
        val path = url.substringBefore('?').lowercase()
        val hint = when {
            path.endsWith(".m3u8") -> "hls"
            path.endsWith(".mpd") -> "dash"
            scheme == "rtsp" -> "rtsp"
            else -> null
        }
        return AppResult.Success(StreamSource(url, StreamKind.LIVE, hint, request.httpHeaders))
    }

    private companion object {
        const val CACHE_MS = 60_000L
    }
}
