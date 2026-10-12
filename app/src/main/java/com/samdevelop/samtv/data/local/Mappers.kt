package com.samdevelop.samtv.data.local

import com.samdevelop.samtv.core.catalog.SearchText
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SourceType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun ProfileEntity.toDomain() = ConnectionProfile(
    id = id, name = name, type = SourceType.valueOf(type), address = address, username = username,
    allowInsecureHttp = allowInsecureHttp, createdAtMillis = createdAt, updatedAtMillis = updatedAt,
)

fun ConnectionProfile.toEntity() = ProfileEntity(
    id = id, name = name, type = type.name, address = address, username = username,
    allowInsecureHttp = allowInsecureHttp, createdAt = createdAtMillis, updatedAt = updatedAtMillis,
)

fun CategoryEntity.toDomain() = ChannelCategory(id, sourceId, name, CatalogKind.valueOf(kind), position)
fun ChannelCategory.toEntity() = CategoryEntity(id, sourceId, kind.name, name, position)

fun ChannelEntity.toDomain() = Channel(
    id = id, sourceId = sourceId, name = name, number = number, logoUrl = logoUrl, categoryId = categoryId,
    epgChannelId = epgChannelId, hasArchive = hasArchive, streamKey = streamKey, containerExtension = containerExtension,
    httpHeaders = decodeHeaders(headersJson), position = position,
)

fun Channel.toEntity() = ChannelEntity(
    id = id, sourceId = sourceId, name = name, searchName = SearchText.normalize(name), number = number, logoUrl = logoUrl,
    categoryId = categoryId, epgChannelId = epgChannelId, hasArchive = hasArchive, streamKey = streamKey,
    containerExtension = containerExtension, headersJson = encodeHeaders(httpHeaders), position = position,
)

fun MovieEntity.toDomain() = Movie(id, sourceId, name, posterUrl, categoryId, rating, year, containerExtension, addedEpochSeconds, position)
fun Movie.toEntity() = MovieEntity(
    id, sourceId, name, SearchText.normalize(name), posterUrl, categoryId, rating, year, containerExtension, addedEpochSeconds, position,
)

fun SeriesEntity.toDomain() = Series(id, sourceId, name, coverUrl, categoryId, plot, genre, releaseDate, rating, position)
fun Series.toEntity() = SeriesEntity(
    id, sourceId, name, SearchText.normalize(name), coverUrl, categoryId, plot, genre, releaseDate, rating, position,
)

fun FavoriteEntity.toDomain() = Favorite(sourceId, contentId, ContentType.valueOf(type), title, imageUrl, addedAt)
fun Favorite.toEntity() = FavoriteEntity(sourceId, contentId, type.name, title, imageUrl, addedAtMillis)

fun HistoryEntity.toDomain() = PlaybackHistory(
    sourceId, contentId, ContentType.valueOf(type), title, imageUrl, parentId, positionMs, durationMs, lastWatchedAt,
)

private fun encodeHeaders(headers: Map<String, String>): String? =
    if (headers.isEmpty()) null else JsonObject(headers.mapValues { JsonPrimitive(it.value) }).toString()

private fun decodeHeaders(json: String?): Map<String, String> {
    if (json.isNullOrEmpty()) return emptyMap()
    return runCatching {
        Json.parseToJsonElement(json).jsonObject.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())
}
