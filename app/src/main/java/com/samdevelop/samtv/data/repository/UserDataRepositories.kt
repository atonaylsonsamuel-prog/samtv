package com.samdevelop.samtv.data.repository

import com.samdevelop.samtv.core.catalog.HistoryPolicy
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.common.flatMap
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.core.model.StreamSource
import com.samdevelop.samtv.core.repository.FavoritesRepository
import com.samdevelop.samtv.core.repository.HistoryRepository
import com.samdevelop.samtv.core.repository.PlaybackSourceProvider
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.local.AppPreferences
import com.samdevelop.samtv.data.local.HistoryEntity
import com.samdevelop.samtv.data.local.toDomain
import com.samdevelop.samtv.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class FavoritesRepositoryImpl(private val db: AppDatabase) : FavoritesRepository {
    override fun favorites(sourceId: String): Flow<List<Favorite>> =
        db.favorites().observe(sourceId).map { list -> list.map { it.toDomain() } }

    override fun isFavorite(sourceId: String, contentId: String, type: ContentType): Flow<Boolean> =
        db.favorites().observeExists(sourceId, contentId, type.name)

    /** Alterna o favorito. Retorna true se ficou favoritado. */
    override suspend fun toggle(favorite: Favorite): Boolean {
        val dao = db.favorites()
        return if (dao.exists(favorite.sourceId, favorite.contentId, favorite.type.name)) {
            dao.delete(favorite.sourceId, favorite.contentId, favorite.type.name)
            false
        } else {
            dao.upsert(favorite.toEntity())
            true
        }
    }

    override suspend fun remove(sourceId: String, contentId: String, type: ContentType) =
        db.favorites().delete(sourceId, contentId, type.name)
}

class HistoryRepositoryImpl(private val db: AppDatabase) : HistoryRepository {
    override fun history(sourceId: String): Flow<List<PlaybackHistory>> =
        db.history().observe(sourceId).map { list -> list.map { it.toDomain() } }

    /** Aplica [HistoryPolicy]: sem duplicação (episódios de uma série ocupam uma linha) e com limite. */
    override suspend fun record(entry: PlaybackHistory) {
        db.history().upsert(
            HistoryEntity(
                sourceId = entry.sourceId,
                dedupeKey = HistoryPolicy.dedupeKey(entry),
                contentId = entry.contentId,
                type = entry.type.name,
                title = entry.title,
                imageUrl = entry.imageUrl,
                parentId = entry.parentId,
                positionMs = entry.positionMs,
                durationMs = entry.durationMs,
                lastWatchedAt = entry.lastWatchedAtMillis,
            ),
        )
        db.history().trim(entry.sourceId, HistoryPolicy.MAX_ENTRIES)
    }

    suspend fun find(sourceId: String, contentId: String, type: ContentType): PlaybackHistory? =
        db.history().find(sourceId, contentId, type.name)?.toDomain()

    override suspend fun remove(sourceId: String, contentId: String, type: ContentType) =
        db.history().delete(sourceId, contentId, type.name)

    override suspend fun clear(sourceId: String?) {
        if (sourceId == null) db.history().clearAll() else db.history().clear(sourceId)
    }
}

/** Resolve a URL real de reprodução no momento de tocar — nunca persistida. */
class PlaybackSourceProviderImpl(
    private val connectors: ConnectorProvider,
    private val prefs: AppPreferences,
) : PlaybackSourceProvider {
    override suspend fun resolve(request: PlaybackRequest): AppResult<StreamSource> {
        val effective = if (request.type == ContentType.CHANNEL && request.containerExtension == null) {
            request.copy(containerExtension = prefs.liveFormat.first().extension)
        } else request
        return connectors.forProfile(request.sourceId).flatMap { it.resolveStream(effective) }
    }
}
