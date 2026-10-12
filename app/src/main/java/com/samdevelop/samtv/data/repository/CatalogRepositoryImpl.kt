package com.samdevelop.samtv.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.samdevelop.samtv.core.catalog.SearchText
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.common.flatMap
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.repository.LiveTvRepository
import com.samdevelop.samtv.core.repository.Page
import com.samdevelop.samtv.core.repository.VODRepository
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.local.SyncStateEntity
import com.samdevelop.samtv.data.local.toDomain
import com.samdevelop.samtv.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Catálogo com cache local em Room. A UI sempre lê do banco (paginado), e a rede só é usada
 * em [refresh]/[refreshMovies]/[refreshSeries] e nos detalhes sob demanda. Assim a interface
 * continua utilizável sem conexão, com o último catálogo baixado.
 */
class CatalogRepositoryImpl(
    private val db: AppDatabase,
    private val connectors: ConnectorProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : LiveTvRepository, VODRepository {

    // ------------------------------------------------------------------ TV ao vivo

    override fun categories(sourceId: String): Flow<List<ChannelCategory>> = categoriesOf(sourceId, CatalogKind.LIVE)

    override suspend fun channels(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Channel> {
        val items = db.channels().query(pageQuery("channels", sourceId, categoryId, query, offset, limit)).map { it.toDomain() }
        return Page(items, offset, items.size < limit)
    }

    override suspend fun channel(sourceId: String, channelId: String): Channel? = db.channels().get(sourceId, channelId)?.toDomain()

    override suspend fun neighbours(sourceId: String, channelId: String, categoryId: String?): Pair<Channel?, Channel?> {
        val current = db.channels().get(sourceId, channelId) ?: return null to null
        val catFilter = if (categoryId != null) " AND categoryId = ?" else ""
        fun q(cmp: String, order: String): SimpleSQLiteQuery {
            val args = mutableListOf<Any>(sourceId, current.position)
            if (categoryId != null) args += categoryId
            return SimpleSQLiteQuery("SELECT * FROM channels WHERE sourceId = ? AND position $cmp ?$catFilter ORDER BY position $order LIMIT 1", args.toTypedArray())
        }
        val prev = db.channels().query(q("<", "DESC")).firstOrNull()?.toDomain()
        val next = db.channels().query(q(">", "ASC")).firstOrNull()?.toDomain()
        return prev to next
    }

    override suspend fun refresh(sourceId: String): AppResult<Int> =
        connectors.forProfile(sourceId).flatMap { it.loadLiveCatalog() }.flatMap { catalog ->
            db.withTransaction {
                db.channels().deleteSource(sourceId)
                db.categories().deleteFor(sourceId, CatalogKind.LIVE.name)
                db.categories().insertAll(catalog.categories.map { it.toEntity() })
                catalog.channels.chunked(CHUNK).forEach { chunk -> db.channels().insertAll(chunk.map { it.toEntity() }) }
                db.syncState().upsert(SyncStateEntity(sourceId, CatalogKind.LIVE.name, clock(), catalog.channels.size))
            }
            AppResult.Success(catalog.channels.size)
        }

    override suspend fun shortEpg(sourceId: String, channel: Channel): AppResult<List<ElectronicProgramGuideEntry>> =
        connectors.forProfile(sourceId).flatMap { it.shortEpg(channel) }

    override suspend fun hasCache(sourceId: String): Boolean = db.syncState().get(sourceId, CatalogKind.LIVE.name) != null

    // ------------------------------------------------------------------ Filmes e séries

    override fun movieCategories(sourceId: String) = categoriesOf(sourceId, CatalogKind.MOVIE)
    override fun seriesCategories(sourceId: String) = categoriesOf(sourceId, CatalogKind.SERIES)

    override suspend fun movies(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Movie> {
        val items = db.movies().query(pageQuery("movies", sourceId, categoryId, query, offset, limit)).map { it.toDomain() }
        return Page(items, offset, items.size < limit)
    }

    override suspend fun series(sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): Page<Series> {
        val items = db.series().query(pageQuery("series", sourceId, categoryId, query, offset, limit)).map { it.toDomain() }
        return Page(items, offset, items.size < limit)
    }

    suspend fun cachedMovie(sourceId: String, id: String): Movie? = db.movies().get(sourceId, id)?.toDomain()
    suspend fun cachedSeries(sourceId: String, id: String): Series? = db.series().get(sourceId, id)?.toDomain()

    override suspend fun movieDetails(sourceId: String, movieId: String): AppResult<MovieDetails> =
        connectors.forProfile(sourceId).flatMap { it.movieDetails(movieId) }

    override suspend fun seriesDetails(sourceId: String, seriesId: String): AppResult<SeriesDetails> =
        connectors.forProfile(sourceId).flatMap { it.seriesDetails(seriesId) }

    override suspend fun refreshMovies(sourceId: String): AppResult<Int> =
        connectors.forProfile(sourceId).flatMap { it.loadMovieCatalog() }.flatMap { catalog ->
            db.withTransaction {
                db.movies().deleteSource(sourceId)
                db.categories().deleteFor(sourceId, CatalogKind.MOVIE.name)
                db.categories().insertAll(catalog.categories.map { it.toEntity() })
                catalog.movies.chunked(CHUNK).forEach { chunk -> db.movies().insertAll(chunk.map { it.toEntity() }) }
                db.syncState().upsert(SyncStateEntity(sourceId, CatalogKind.MOVIE.name, clock(), catalog.movies.size))
            }
            AppResult.Success(catalog.movies.size)
        }

    override suspend fun refreshSeries(sourceId: String): AppResult<Int> =
        connectors.forProfile(sourceId).flatMap { it.loadSeriesCatalog() }.flatMap { catalog ->
            db.withTransaction {
                db.series().deleteSource(sourceId)
                db.categories().deleteFor(sourceId, CatalogKind.SERIES.name)
                db.categories().insertAll(catalog.categories.map { it.toEntity() })
                catalog.series.chunked(CHUNK).forEach { chunk -> db.series().insertAll(chunk.map { it.toEntity() }) }
                db.syncState().upsert(SyncStateEntity(sourceId, CatalogKind.SERIES.name, clock(), catalog.series.size))
            }
            AppResult.Success(catalog.series.size)
        }

    /** Limpa o cache de catálogo (mantém perfis, favoritos e histórico). */
    suspend fun clearCache(sourceId: String) {
        db.withTransaction {
            db.channels().deleteSource(sourceId)
            db.movies().deleteSource(sourceId)
            db.series().deleteSource(sourceId)
            db.categories().deleteSource(sourceId)
            db.syncState().deleteSource(sourceId)
        }
        connectors.invalidate(sourceId)
    }

    fun syncStates(sourceId: String): Flow<List<SyncStateEntity>> = db.syncState().observe(sourceId)

    // ------------------------------------------------------------------ auxiliares

    private fun categoriesOf(sourceId: String, kind: CatalogKind): Flow<List<ChannelCategory>> =
        db.categories().observe(sourceId, kind.name).map { list -> list.map { it.toDomain() } }

    /**
     * Consulta paginada. A busca usa o nome normalizado (sem acento/pontuação, só [a-z0-9 ]),
     * com um LIKE por termo — por isso não há caracteres especiais a escapar.
     */
    private fun pageQuery(table: String, sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int): SimpleSQLiteQuery {
        val sql = StringBuilder("SELECT * FROM ").append(table).append(" WHERE sourceId = ?")
        val args = mutableListOf<Any>(sourceId)
        if (categoryId != null) {
            sql.append(" AND categoryId = ?"); args += categoryId
        }
        SearchText.normalize(query.orEmpty()).split(' ').filter { it.isNotEmpty() }.take(MAX_TERMS).forEach { term ->
            sql.append(" AND searchName LIKE ?"); args += "%$term%"
        }
        sql.append(" ORDER BY position LIMIT ? OFFSET ?")
        args += limit; args += offset
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }

    private companion object {
        const val CHUNK = 500
        const val MAX_TERMS = 6
    }
}
