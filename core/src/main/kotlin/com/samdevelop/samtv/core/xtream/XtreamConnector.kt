package com.samdevelop.samtv.core.xtream

import com.samdevelop.samtv.core.catalog.StableIds
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.common.flatMap
import com.samdevelop.samtv.core.common.map
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.Episode
import com.samdevelop.samtv.core.model.LiveCatalog
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.MovieCatalog
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.model.Season
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SeriesCatalog
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.model.SourceCapabilities
import com.samdevelop.samtv.core.model.StreamKind
import com.samdevelop.samtv.core.model.StreamSource
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.core.net.HttpFailure
import com.samdevelop.samtv.core.net.HttpRequest
import com.samdevelop.samtv.core.net.SecretRedactor
import com.samdevelop.samtv.core.net.UrlRules
import com.samdevelop.samtv.core.source.SourceConnector
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/**
 * Conector Xtream Codes (player_api.php).
 *
 * Endpoints utilizados (todos via GET em {base}/player_api.php):
 *  - (sem action)              → user_info / server_info (autenticação)
 *  - get_live_categories / get_live_streams
 *  - get_vod_categories  / get_vod_streams / get_vod_info&vod_id=
 *  - get_series_categories / get_series / get_series_info&series_id=
 *  - get_short_epg&stream_id=&limit=
 *
 * Regras de segurança:
 *  - credenciais só são enviadas ao [baseUrl] configurado (server_info.url é ignorado para isso);
 *  - HTTP sem TLS só é usado com consentimento explícito ([allowInsecureHttp]);
 *  - nada com credenciais vai para mensagens de erro (ver [SecretRedactor]).
 */
class XtreamConnector(
    override val sourceId: String,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val allowInsecureHttp: Boolean,
    private val http: HttpClient,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : SourceConnector {

    override val declaredCapabilities = SourceCapabilities(live = true, movies = true, series = true, epg = true)

    @Volatile
    private var allowedFormats: List<String> = emptyList()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ---------------------------------------------------------------- autenticação

    override suspend fun authenticate(): AppResult<AuthenticationResult> =
        request(action = null).flatMap { root ->
            val obj = root as? JsonObject ?: return@flatMap invalid("auth: not an object")
            val user = obj.obj("user_info") ?: return@flatMap invalid("auth: user_info missing")
            val authFlag = user.str("auth")
            if (authFlag == null) return@flatMap invalid("auth: auth flag missing")
            if (authFlag != "1" && authFlag.lowercase() != "true") return@flatMap AppResult.Failure(AppError.Unauthorized)

            val statusRaw = user.str("status")
            val status = when (statusRaw?.lowercase()) {
                "active" -> AccountStatus.ACTIVE
                "expired" -> AccountStatus.EXPIRED
                "banned" -> AccountStatus.BANNED
                "disabled" -> AccountStatus.DISABLED
                else -> AccountStatus.UNKNOWN
            }
            val expires = user.long("exp_date")?.takeIf { it > 0 }
            when (status) {
                AccountStatus.EXPIRED -> return@flatMap AppResult.Failure(AppError.AccountExpired(expires))
                AccountStatus.BANNED, AccountStatus.DISABLED -> return@flatMap AppResult.Failure(AppError.AccountDisabled(statusRaw ?: status.name))
                else -> Unit
            }
            val formats = user.strList("allowed_output_formats").map { it.lowercase() }
            allowedFormats = formats
            val server = obj.obj("server_info")
            AppResult.Success(
                AuthenticationResult(
                    status = status,
                    expiresAtEpochSeconds = expires,
                    maxConnections = user.int("max_connections"),
                    activeConnections = user.int("active_cons"),
                    allowedOutputFormats = formats,
                    serverTimezone = server?.str("timezone"),
                    capabilities = declaredCapabilities,
                    serverMessage = user.str("message"),
                ),
            )
        }

    // ---------------------------------------------------------------- TV ao vivo

    override suspend fun loadLiveCatalog(): AppResult<LiveCatalog> =
        categories("get_live_categories", CatalogKind.LIVE).flatMap { cats ->
            requestArray("get_live_streams").map { items ->
                val channels = items.mapIndexedNotNull { index, o ->
                    val streamId = o.str("stream_id") ?: return@mapIndexedNotNull null
                    val name = o.str("name") ?: return@mapIndexedNotNull null
                    Channel(
                        id = "$sourceId:live:$streamId",
                        sourceId = sourceId,
                        name = name,
                        number = o.int("num"),
                        logoUrl = o.firstUrl("stream_icon"),
                        categoryId = o.str("category_id")?.let { StableIds.categoryId(sourceId, CatalogKind.LIVE, it) },
                        epgChannelId = o.str("epg_channel_id"),
                        hasArchive = o.bool("tv_archive"),
                        streamKey = streamId,
                        containerExtension = null,
                        position = index,
                    )
                }
                LiveCatalog(withOnlyUsed(cats, channels.mapNotNull { it.categoryId }), channels)
            }
        }

    // ---------------------------------------------------------------- Filmes

    override suspend fun loadMovieCatalog(): AppResult<MovieCatalog> =
        categories("get_vod_categories", CatalogKind.MOVIE).flatMap { cats ->
            requestArray("get_vod_streams").map { items ->
                val movies = items.mapIndexedNotNull { index, o -> mapMovie(o, index) }
                MovieCatalog(withOnlyUsed(cats, movies.mapNotNull { it.categoryId }), movies)
            }
        }

    private fun mapMovie(o: JsonObject, index: Int): Movie? {
        val streamId = o.str("stream_id") ?: return null
        val name = o.str("name") ?: return null
        return Movie(
            id = "$sourceId:vod:$streamId",
            sourceId = sourceId,
            name = name,
            posterUrl = o.firstUrl("stream_icon", "cover"),
            categoryId = o.str("category_id")?.let { StableIds.categoryId(sourceId, CatalogKind.MOVIE, it) },
            rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
            year = o.str("year") ?: o.str("releaseDate")?.take(4),
            containerExtension = o.str("container_extension"),
            addedEpochSeconds = o.long("added"),
            position = index,
        )
    }

    override suspend fun movieDetails(movieId: String): AppResult<MovieDetails> {
        val raw = rawId(movieId)
        return request("get_vod_info", mapOf("vod_id" to raw)).flatMap { root ->
            val obj = root as? JsonObject ?: return@flatMap invalid("vod_info: not an object")
            val info = obj.obj("info")
            val data = obj.obj("movie_data")
            if (info == null && data == null) return@flatMap AppResult.Failure(AppError.NotFound)
            val name = data?.str("name") ?: info?.str("name") ?: info?.str("title") ?: return@flatMap invalid("vod_info: name missing")
            val movie = Movie(
                id = "$sourceId:vod:$raw",
                sourceId = sourceId,
                name = name,
                posterUrl = info?.firstUrl("movie_image", "cover_big"),
                categoryId = data?.str("category_id")?.let { StableIds.categoryId(sourceId, CatalogKind.MOVIE, it) },
                rating = info?.str("rating")?.takeIf { it != "0" && it != "0.0" },
                year = info?.str("releasedate")?.take(4),
                containerExtension = data?.str("container_extension"),
                addedEpochSeconds = data?.long("added"),
                position = 0,
            )
            AppResult.Success(
                MovieDetails(
                    movie = movie,
                    plot = info?.str("plot") ?: info?.str("description"),
                    genre = info?.str("genre"),
                    cast = info?.str("cast") ?: info?.str("actors"),
                    director = info?.str("director"),
                    releaseDate = info?.str("releasedate"),
                    durationSeconds = info?.long("duration_secs"),
                    backdropUrl = info?.firstUrl("backdrop_path"),
                ),
            )
        }
    }

    // ---------------------------------------------------------------- Séries

    override suspend fun loadSeriesCatalog(): AppResult<SeriesCatalog> =
        categories("get_series_categories", CatalogKind.SERIES).flatMap { cats ->
            requestArray("get_series").map { items ->
                val series = items.mapIndexedNotNull { index, o -> mapSeries(o, o.str("series_id"), index) }
                SeriesCatalog(withOnlyUsed(cats, series.mapNotNull { it.categoryId }), series)
            }
        }

    private fun mapSeries(o: JsonObject, rawId: String?, index: Int): Series? {
        val id = rawId ?: return null
        val name = o.str("name") ?: o.str("title") ?: return null
        return Series(
            id = "$sourceId:series:$id",
            sourceId = sourceId,
            name = name,
            coverUrl = o.firstUrl("cover", "cover_big", "movie_image"),
            categoryId = o.str("category_id")?.let { StableIds.categoryId(sourceId, CatalogKind.SERIES, it) },
            plot = o.str("plot"),
            genre = o.str("genre"),
            releaseDate = o.str("releaseDate") ?: o.str("release_date"),
            rating = o.str("rating")?.takeIf { it != "0" && it != "0.0" },
            position = index,
        )
    }

    override suspend fun seriesDetails(seriesId: String): AppResult<SeriesDetails> {
        val raw = rawId(seriesId)
        return request("get_series_info", mapOf("series_id" to raw)).flatMap { root ->
            val obj = root as? JsonObject ?: return@flatMap invalid("series_info: not an object")
            val info = obj.obj("info") ?: JsonObject(emptyMap())
            val series = mapSeries(info, raw, 0) ?: Series(
                id = "$sourceId:series:$raw", sourceId = sourceId, name = "", coverUrl = null, categoryId = null,
                plot = null, genre = null, releaseDate = null, rating = null, position = 0,
            )
            val episodes = parseEpisodes(obj["episodes"], series.id)
            val declaredSeasons = obj.arr("seasons")?.objects().orEmpty().mapNotNull { s ->
                val n = s.int("season_number") ?: return@mapNotNull null
                Season(n, s.str("name") ?: "", s.firstUrl("cover", "cover_big"), s.int("episode_count"))
            }.associateBy { it.number }
            val seasonNumbers = (episodes.map { it.seasonNumber } + declaredSeasons.keys).toSortedSet()
            // Só exibimos temporadas que tenham episódios reais; as declaradas sem episódios são descartadas.
            val seasons = seasonNumbers.filter { n -> episodes.any { it.seasonNumber == n } }.map { n ->
                val declared = declaredSeasons[n]
                Season(n, declared?.name.orEmpty(), declared?.coverUrl, episodes.count { it.seasonNumber == n })
            }
            if (series.name.isEmpty() && episodes.isEmpty()) return@flatMap AppResult.Failure(AppError.NotFound)
            AppResult.Success(SeriesDetails(series, seasons, episodes))
        }
    }

    /** "episodes" pode vir como objeto {"1":[...]} ou como array de arrays/objetos. */
    private fun parseEpisodes(el: JsonElement?, seriesId: String): List<Episode> {
        val groups: List<Pair<Int?, List<JsonObject>>> = when (el) {
            is JsonObject -> el.entries.map { (k, v) -> k.toIntOrNull() to v.objects() }
            is JsonArray -> el.map { item ->
                when (item) {
                    is JsonArray -> null to item.objects()
                    is JsonObject -> null to listOf(item)
                    else -> null to emptyList()
                }
            }
            else -> emptyList()
        }
        val list = ArrayList<Episode>()
        for ((seasonKey, items) in groups) for (o in items) {
            val id = o.str("id") ?: continue
            val season = o.int("season") ?: seasonKey ?: continue
            val info = o.obj("info")
            list += Episode(
                id = "$sourceId:ep:$id",
                seriesId = seriesId,
                seasonNumber = season,
                episodeNumber = o.int("episode_num"),
                title = o.str("title") ?: "",
                plot = info?.str("plot"),
                durationSeconds = info?.long("duration_secs"),
                imageUrl = info?.firstUrl("movie_image", "cover_big"),
                containerExtension = o.str("container_extension"),
            )
        }
        return list.distinctBy { it.id }
    }

    // ---------------------------------------------------------------- EPG

    override suspend fun shortEpg(channel: Channel, limit: Int): AppResult<List<ElectronicProgramGuideEntry>> =
        request("get_short_epg", mapOf("stream_id" to channel.streamKey, "limit" to limit.toString())).flatMap { root ->
            val listings = (root as? JsonObject)?.arr("epg_listings") ?: return@flatMap AppResult.Success(emptyList())
            AppResult.Success(
                listings.objects().mapNotNull { o ->
                    val start = o.long("start_timestamp") ?: return@mapNotNull null
                    val end = o.long("stop_timestamp") ?: return@mapNotNull null
                    val title = decodeMaybeBase64(o.str("title")) ?: return@mapNotNull null
                    ElectronicProgramGuideEntry(channel.epgChannelId, title, decodeMaybeBase64(o.str("description")), start, end)
                }.filter { it.endEpochSeconds > nowEpochSeconds() }.sortedBy { it.startEpochSeconds },
            )
        }

    // ---------------------------------------------------------------- reprodução

    override fun resolveStream(request: PlaybackRequest): AppResult<StreamSource> {
        if (!secureEnough()) return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
        val u = UrlRules.encodePathSegment(username)
        val p = UrlRules.encodePathSegment(password)
        val id = UrlRules.encodePathSegment(request.streamKey)
        return when (request.type) {
            ContentType.CHANNEL -> {
                val ext = liveExtension(request.containerExtension)
                AppResult.Success(StreamSource("$baseUrl/live/$u/$p/$id.$ext", StreamKind.LIVE, if (ext == "m3u8") "hls" else "progressive", request.httpHeaders))
            }
            ContentType.MOVIE -> {
                val ext = safeExt(request.containerExtension) ?: "mp4"
                AppResult.Success(StreamSource("$baseUrl/movie/$u/$p/$id.$ext", StreamKind.VOD, formatFor(ext), request.httpHeaders))
            }
            ContentType.EPISODE -> {
                val ext = safeExt(request.containerExtension) ?: "mp4"
                AppResult.Success(StreamSource("$baseUrl/series/$u/$p/$id.$ext", StreamKind.VOD, formatFor(ext), request.httpHeaders))
            }
            ContentType.SERIES -> AppResult.Failure(AppError.UnsupportedFeature)
        }
    }

    /**
     * Extensão para TV ao vivo: preferência do usuário (se permitida), senão m3u8 quando o servidor
     * permite, senão ts. "auto" ou null = automático.
     */
    internal fun liveExtension(preference: String?): String {
        val pref = safeExt(preference)?.takeIf { it != "auto" }
        val allowed = allowedFormats
        if (pref != null && (allowed.isEmpty() || pref in allowed)) return pref
        return when {
            "m3u8" in allowed -> "m3u8"
            "ts" in allowed -> "ts"
            allowed.isNotEmpty() -> safeExt(allowed.first()) ?: "ts"
            else -> "ts"
        }
    }

    private fun formatFor(ext: String) = when (ext) {
        "m3u8" -> "hls"
        "mpd" -> "dash"
        else -> "progressive"
    }

    private fun safeExt(ext: String?): String? = ext?.lowercase()?.trim()?.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }

    // ---------------------------------------------------------------- infraestrutura

    private fun secureEnough() = baseUrl.startsWith("https://", ignoreCase = true) || allowInsecureHttp

    private suspend fun categories(action: String, kind: CatalogKind): AppResult<List<ChannelCategory>> =
        requestArray(action).map { items ->
            items.mapIndexedNotNull { index, o ->
                val id = o.str("category_id") ?: return@mapIndexedNotNull null
                val name = o.str("category_name") ?: return@mapIndexedNotNull null
                ChannelCategory(StableIds.categoryId(sourceId, kind, id), sourceId, name, kind, index)
            }
        }

    /** Remove categorias sem itens, mas mantém a ordem original. */
    private fun withOnlyUsed(categories: List<ChannelCategory>, usedIds: List<String>): List<ChannelCategory> {
        val used = usedIds.toHashSet()
        return categories.filter { it.id in used }
    }

    /**
     * Ações de listagem devem retornar array. Alguns servidores retornam objeto vazio `{}`
     * ou `null` quando não há itens — tratamos como lista vazia. Qualquer outra coisa é inválida.
     */
    private suspend fun requestArray(action: String): AppResult<List<JsonObject>> =
        request(action).flatMap { el ->
            when {
                el is JsonArray -> AppResult.Success(el.objects())
                el is JsonObject && el.isEmpty() -> AppResult.Success(emptyList())
                el is kotlinx.serialization.json.JsonNull -> AppResult.Success(emptyList())
                el is JsonObject && el.obj("user_info") != null -> AppResult.Failure(AppError.Unauthorized)
                else -> invalid("$action: unexpected shape")
            }
        }

    private suspend fun request(action: String?, extra: Map<String, String> = emptyMap()): AppResult<JsonElement> {
        if (!secureEnough()) return AppResult.Failure(AppError.InsecureConnectionNotAllowed)
        val url = buildString {
            append(baseUrl).append("/player_api.php?username=").append(UrlRules.encodeQuery(username))
            append("&password=").append(UrlRules.encodeQuery(password))
            if (action != null) append("&action=").append(UrlRules.encodeQuery(action))
            extra.forEach { (k, v) -> append('&').append(UrlRules.encodeQuery(k)).append('=').append(UrlRules.encodeQuery(v)) }
        }
        val response = try {
            http.get(HttpRequest(url, containsCredentials = true, allowInsecureHttp = allowInsecureHttp))
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpFailure) {
            return AppResult.Failure(mapFailure(e))
        } catch (e: Exception) {
            return AppResult.Failure(AppError.Network(SecretRedactor.redactText(e.message ?: e.javaClass.simpleName, listOf(username, password))))
        }
        when (response.code) {
            in 200..299 -> Unit
            401, 403 -> return AppResult.Failure(AppError.Unauthorized)
            else -> return AppResult.Failure(AppError.Http(response.code))
        }
        val body = response.body.trim()
        if (body.isEmpty()) return invalid("${action ?: "auth"}: empty body")
        if (body.first() != '{' && body.first() != '[' && body != "null") return invalid("${action ?: "auth"}: not json")
        return try {
            AppResult.Success(json.parseToJsonElement(body))
        } catch (e: Exception) {
            invalid("${action ?: "auth"}: malformed json")
        }
    }

    private fun invalid(detail: String) = AppResult.Failure(AppError.InvalidResponse(detail))

    private fun mapFailure(e: HttpFailure): AppError = when (e) {
        is HttpFailure.Timeout -> AppError.Timeout
        is HttpFailure.Tls -> AppError.Tls(e.message)
        is HttpFailure.RedirectBlocked -> AppError.RedirectBlocked(e.message)
        is HttpFailure.InsecureNotAllowed -> AppError.InsecureConnectionNotAllowed
        is HttpFailure.Connection -> AppError.Network(SecretRedactor.redactText(e.message ?: "", listOf(username, password)))
        is HttpFailure.TooLarge -> AppError.InvalidResponse("too large")
    }

    private fun decodeMaybeBase64(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (!value.matches(Regex("^[A-Za-z0-9+/=\\s]+$")) || value.length % 4 != 0) return value
        return try {
            val bytes = Base64.getDecoder().decode(value.replace(Regex("\\s"), ""))
            val decoded = Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            // Texto comum que "parece" base64 decodifica para lixo/controle: nesse caso mantemos o original.
            if (decoded.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) value else decoded.trim().ifEmpty { null }
        } catch (_: Exception) {
            value
        }
    }

    companion object {
        /** Extrai o id original do servidor de um id composto ("fonte:vod:123" → "123"). */
        fun rawId(id: String): String = id.substringAfterLast(':')
    }
}
