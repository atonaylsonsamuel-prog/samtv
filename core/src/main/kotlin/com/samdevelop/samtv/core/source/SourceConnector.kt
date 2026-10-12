package com.samdevelop.samtv.core.source

import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.LiveCatalog
import com.samdevelop.samtv.core.model.MovieCatalog
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.model.SeriesCatalog
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.model.SourceCapabilities
import com.samdevelop.samtv.core.model.StreamSource

/**
 * Interface comum dos conectores de fonte. Xtream Codes e M3U são implementações
 * independentes. Uma futura API própria da SAM TV seria uma terceira implementação,
 * sem alterar a interface (nenhum endpoint é presumido aqui).
 */
interface SourceConnector {
    val sourceId: String

    /** O que este tipo de fonte pode oferecer em princípio. */
    val declaredCapabilities: SourceCapabilities

    suspend fun authenticate(): AppResult<AuthenticationResult>
    suspend fun loadLiveCatalog(): AppResult<LiveCatalog>
    suspend fun loadMovieCatalog(): AppResult<MovieCatalog>
    suspend fun loadSeriesCatalog(): AppResult<SeriesCatalog>
    suspend fun movieDetails(movieId: String): AppResult<MovieDetails>
    suspend fun seriesDetails(seriesId: String): AppResult<SeriesDetails>
    suspend fun shortEpg(channel: Channel, limit: Int = 4): AppResult<List<ElectronicProgramGuideEntry>>

    /** Monta a URL de reprodução. Não faz requisição de rede. */
    fun resolveStream(request: PlaybackRequest): AppResult<StreamSource>
}
