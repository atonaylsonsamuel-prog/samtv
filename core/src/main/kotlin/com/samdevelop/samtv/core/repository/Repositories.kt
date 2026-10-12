package com.samdevelop.samtv.core.repository

import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.model.SourceSecrets
import com.samdevelop.samtv.core.model.StreamSource
import kotlinx.coroutines.flow.Flow

/*
 * Contratos dos repositórios. A implementação Android fica em app/data/repository.
 * A implementação Roku deverá reproduzir o mesmo comportamento (docs/ROKU-CONTRACTS.md).
 */

/** Gerencia fontes cadastradas e valida credenciais junto ao servidor. */
interface AuthenticationRepository {
    val profiles: Flow<List<ConnectionProfile>>
    val activeProfile: Flow<ConnectionProfile?>

    /** Testa a conexão sem salvar nada. */
    suspend fun test(profile: ConnectionProfile, secrets: SourceSecrets): AppResult<AuthenticationResult>

    /** Testa e, em caso de sucesso, salva o perfil (segredos criptografados) e o torna ativo. */
    suspend fun saveAndActivate(profile: ConnectionProfile, secrets: SourceSecrets): AppResult<AuthenticationResult>

    /** Revalida a fonte ativa (ex.: tela de configurações → estado da conexão). */
    suspend fun checkActive(): AppResult<AuthenticationResult>

    suspend fun setActive(profileId: String)
    suspend fun remove(profileId: String)
    suspend fun secretsFor(profileId: String): SourceSecrets?
}

data class Page<T>(val items: List<T>, val offset: Int, val endReached: Boolean)

interface LiveTvRepository {
    fun categories(sourceId: String): Flow<List<ChannelCategory>>
    suspend fun channels(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Channel>
    suspend fun channel(sourceId: String, channelId: String): Channel?
    /** Canais vizinhos para troca de canal no player. */
    suspend fun neighbours(sourceId: String, channelId: String, categoryId: String?): Pair<Channel?, Channel?>
    suspend fun refresh(sourceId: String): AppResult<Int>
    suspend fun shortEpg(sourceId: String, channel: Channel): AppResult<List<ElectronicProgramGuideEntry>>
    suspend fun hasCache(sourceId: String): Boolean
}

interface VODRepository {
    fun movieCategories(sourceId: String): Flow<List<ChannelCategory>>
    fun seriesCategories(sourceId: String): Flow<List<ChannelCategory>>
    suspend fun movies(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Movie>
    suspend fun series(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Series>
    suspend fun movieDetails(sourceId: String, movieId: String): AppResult<MovieDetails>
    suspend fun seriesDetails(sourceId: String, seriesId: String): AppResult<SeriesDetails>
    suspend fun refreshMovies(sourceId: String): AppResult<Int>
    suspend fun refreshSeries(sourceId: String): AppResult<Int>
}

interface FavoritesRepository {
    fun favorites(sourceId: String): Flow<List<Favorite>>
    fun isFavorite(sourceId: String, contentId: String, type: ContentType): Flow<Boolean>
    suspend fun toggle(favorite: Favorite): Boolean
    suspend fun remove(sourceId: String, contentId: String, type: ContentType)
}

interface HistoryRepository {
    fun history(sourceId: String): Flow<List<PlaybackHistory>>
    suspend fun record(entry: PlaybackHistory)
    suspend fun remove(sourceId: String, contentId: String, type: ContentType)
    suspend fun clear(sourceId: String?)
}

/** Resolve um pedido de reprodução para a URL real, aplicando as regras de segurança. */
interface PlaybackSourceProvider {
    suspend fun resolve(request: PlaybackRequest): AppResult<StreamSource>
}
