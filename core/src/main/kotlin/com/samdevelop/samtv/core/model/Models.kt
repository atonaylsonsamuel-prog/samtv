package com.samdevelop.samtv.core.model

/*
 * Modelos de domínio independentes de plataforma.
 *
 * Regras:
 *  - Nenhuma classe aqui depende de Android, ExoPlayer, Room ou Compose.
 *  - São dados + regras, nunca componentes de interface.
 *  - Os mesmos campos estão documentados em docs/ROKU-CONTRACTS.md para a
 *    futura implementação em BrightScript/SceneGraph.
 *  - Senhas e URLs com credenciais NUNCA ficam nestes modelos persistíveis;
 *    elas vivem em [SourceSecrets], que é guardado criptografado no dispositivo.
 */

enum class SourceType { XTREAM, M3U }

/** Tipo de catálogo ao qual uma categoria ou item pertence. */
enum class CatalogKind { LIVE, MOVIE, SERIES }

/** Tipo de conteúdo usado por favoritos, histórico e reprodução. */
enum class ContentType { CHANNEL, MOVIE, SERIES, EPISODE }

/**
 * Perfil de conexão cadastrado pelo usuário. Não contém segredos.
 *
 * @property address Xtream: endereço-base normalizado (ex.: https://servidor:8080).
 *                   M3U: URL da playlist SEM parâmetros sensíveis (apenas para exibição).
 * @property username Usuário Xtream (null para M3U).
 * @property allowInsecureHttp Consentimento explícito do usuário para usar HTTP sem TLS.
 */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val type: SourceType,
    val address: String,
    val username: String?,
    val allowInsecureHttp: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** Segredos de uma fonte. Guardados apenas de forma criptografada. */
data class SourceSecrets(
    /** Senha Xtream. */
    val password: String? = null,
    /** URL completa da playlist M3U (frequentemente contém usuário/senha na query). */
    val playlistUrl: String? = null,
) {
    override fun toString(): String = "SourceSecrets(***)"
}

enum class AccountStatus { ACTIVE, EXPIRED, BANNED, DISABLED, UNKNOWN }

/** O que uma fonte declara ou demonstrou suportar. */
data class SourceCapabilities(
    val live: Boolean,
    val movies: Boolean,
    val series: Boolean,
    val epg: Boolean,
)

data class AuthenticationResult(
    val status: AccountStatus,
    /** Expiração informada pelo servidor (epoch em segundos) ou null se não informada/ilimitada. */
    val expiresAtEpochSeconds: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val allowedOutputFormats: List<String>,
    val serverTimezone: String?,
    val capabilities: SourceCapabilities,
    /** Mensagem opcional retornada pelo servidor. */
    val serverMessage: String?,
    /** Quantidade de entradas válidas (M3U). */
    val itemCount: Int? = null,
)

data class ChannelCategory(
    val id: String,
    val sourceId: String,
    val name: String,
    val kind: CatalogKind,
    /** Ordem original da fonte. */
    val position: Int,
)

/**
 * Canal de TV.
 * @property streamKey Xtream: stream_id. M3U: URL de reprodução.
 */
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val number: Int?,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val hasArchive: Boolean,
    val streamKey: String,
    val containerExtension: String?,
    /** Cabeçalhos HTTP declarados pela playlist (#EXTVLCOPT http-user-agent / http-referrer). */
    val httpHeaders: Map<String, String> = emptyMap(),
    val position: Int,
)

data class Movie(
    val id: String,
    val sourceId: String,
    val name: String,
    val posterUrl: String?,
    val categoryId: String?,
    val rating: String?,
    val year: String?,
    val containerExtension: String?,
    val addedEpochSeconds: Long?,
    val position: Int,
)

data class MovieDetails(
    val movie: Movie,
    val plot: String?,
    val genre: String?,
    val cast: String?,
    val director: String?,
    val releaseDate: String?,
    val durationSeconds: Long?,
    val backdropUrl: String?,
)

data class Series(
    val id: String,
    val sourceId: String,
    val name: String,
    val coverUrl: String?,
    val categoryId: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val position: Int,
)

data class Season(
    val number: Int,
    val name: String,
    val coverUrl: String?,
    val episodeCount: Int?,
)

data class Episode(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int?,
    val title: String,
    val plot: String?,
    val durationSeconds: Long?,
    val imageUrl: String?,
    val containerExtension: String?,
)

data class SeriesDetails(
    val series: Series,
    val seasons: List<Season>,
    val episodes: List<Episode>,
) {
    fun episodesOf(season: Int): List<Episode> =
        episodes.filter { it.seasonNumber == season }.sortedWith(compareBy({ it.episodeNumber ?: Int.MAX_VALUE }, { it.title }))
}

/** Entrada de guia de programação, apenas quando a fonte fornece EPG. */
data class ElectronicProgramGuideEntry(
    val channelEpgId: String?,
    val title: String,
    val description: String?,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

enum class StreamKind { LIVE, VOD }

/**
 * Fonte de reprodução já resolvida. Contém a URL real (possivelmente com credenciais),
 * por isso NUNCA deve ser persistida nem registrada em log sem redação.
 */
data class StreamSource(
    val url: String,
    val kind: StreamKind,
    /** Dica de formato: "hls", "dash", "progressive" ou null quando desconhecido. */
    val formatHint: String?,
    val httpHeaders: Map<String, String>,
) {
    override fun toString(): String = "StreamSource(kind=$kind, formatHint=$formatHint, url=<redacted>)"
}

/** Pedido de reprodução — só identificadores, sem credenciais. */
data class PlaybackRequest(
    val sourceId: String,
    val contentId: String,
    val type: ContentType,
    val title: String,
    val imageUrl: String?,
    val streamKey: String,
    val containerExtension: String?,
    val httpHeaders: Map<String, String> = emptyMap(),
    /** Para episódios: id da série (usado no histórico). */
    val parentId: String? = null,
)

data class Favorite(
    val sourceId: String,
    val contentId: String,
    val type: ContentType,
    val title: String,
    val imageUrl: String?,
    val addedAtMillis: Long,
)

data class PlaybackHistory(
    val sourceId: String,
    val contentId: String,
    val type: ContentType,
    val title: String,
    val imageUrl: String?,
    val parentId: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val lastWatchedAtMillis: Long,
)

data class LiveCatalog(val categories: List<ChannelCategory>, val channels: List<Channel>)
data class MovieCatalog(val categories: List<ChannelCategory>, val movies: List<Movie>)
data class SeriesCatalog(val categories: List<ChannelCategory>, val series: List<Series>)
