package com.samdevelop.samtv.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Perfil de fonte. NÃO contém senha nem URL completa da playlist (essas ficam no CredentialStore). */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val address: String,
    val username: String?,
    val allowInsecureHttp: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["sourceId", "kind", "position"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val kind: String,
    val name: String,
    val position: Int,
)

/**
 * Cache de canais. Para fontes M3U, [streamKey] é a URL do stream (pode conter token do provedor);
 * a tabela fica no armazenamento privado do app e nunca é registrada em log nem copiada para o histórico.
 */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["sourceId", "categoryId", "position"]),
        Index(value = ["sourceId", "position"]),
        Index(value = ["sourceId", "searchName"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val name: String,
    val searchName: String,
    val number: Int?,
    val logoUrl: String?,
    val categoryId: String?,
    val epgChannelId: String?,
    val hasArchive: Boolean,
    val streamKey: String,
    val containerExtension: String?,
    val headersJson: String?,
    val position: Int,
)

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["sourceId", "categoryId", "position"]),
        Index(value = ["sourceId", "position"]),
        Index(value = ["sourceId", "searchName"]),
    ],
)
data class MovieEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val name: String,
    val searchName: String,
    val posterUrl: String?,
    val categoryId: String?,
    val rating: String?,
    val year: String?,
    val containerExtension: String?,
    val addedEpochSeconds: Long?,
    val position: Int,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["sourceId", "categoryId", "position"]),
        Index(value = ["sourceId", "position"]),
        Index(value = ["sourceId", "searchName"]),
    ],
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val name: String,
    val searchName: String,
    val coverUrl: String?,
    val categoryId: String?,
    val plot: String?,
    val genre: String?,
    val releaseDate: String?,
    val rating: String?,
    val position: Int,
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["sourceId", "contentId", "type"],
    indices = [Index(value = ["sourceId", "addedAt"])],
)
data class FavoriteEntity(
    val sourceId: String,
    val contentId: String,
    val type: String,
    val title: String,
    val imageUrl: String?,
    val addedAt: Long,
)

/** Histórico: guarda só identificadores e metadados de exibição — nunca URLs de reprodução. */
@Entity(
    tableName = "history",
    primaryKeys = ["sourceId", "dedupeKey"],
    indices = [Index(value = ["sourceId", "lastWatchedAt"])],
)
data class HistoryEntity(
    val sourceId: String,
    val dedupeKey: String,
    val contentId: String,
    val type: String,
    val title: String,
    val imageUrl: String?,
    val parentId: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val lastWatchedAt: Long,
)

/** Quando cada catálogo foi sincronizado pela última vez. */
@Entity(tableName = "sync_state", primaryKeys = ["sourceId", "kind"])
data class SyncStateEntity(
    val sourceId: String,
    val kind: String,
    val refreshedAt: Long,
    val itemCount: Int,
)
