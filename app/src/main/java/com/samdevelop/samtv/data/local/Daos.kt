package com.samdevelop.samtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun get(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    suspend fun all(): List<ProfileEntity>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE sourceId = :sourceId AND kind = :kind ORDER BY position")
    fun observe(sourceId: String, kind: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId AND kind = :kind")
    suspend fun deleteFor(sourceId: String, kind: String)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface ChannelDao {
    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId AND id = :id")
    suspend fun get(sourceId: String, id: String): ChannelEntity?

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun count(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface MovieDao {
    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<MovieEntity>

    @Query("SELECT * FROM movies WHERE sourceId = :sourceId AND id = :id")
    suspend fun get(sourceId: String, id: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface SeriesDao {
    @RawQuery
    suspend fun query(query: SupportSQLiteQuery): List<SeriesEntity>

    @Query("SELECT * FROM series WHERE sourceId = :sourceId AND id = :id")
    suspend fun get(sourceId: String, id: String): SeriesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId ORDER BY addedAt DESC")
    fun observe(sourceId: String): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND contentId = :contentId AND type = :type)")
    fun observeExists(sourceId: String, contentId: String, type: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND contentId = :contentId AND type = :type)")
    suspend fun exists(sourceId: String, contentId: String, type: String): Boolean

    @Upsert
    suspend fun upsert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND contentId = :contentId AND type = :type")
    suspend fun delete(sourceId: String, contentId: String, type: String)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE sourceId = :sourceId ORDER BY lastWatchedAt DESC")
    fun observe(sourceId: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE sourceId = :sourceId AND contentId = :contentId AND type = :type LIMIT 1")
    suspend fun find(sourceId: String, contentId: String, type: String): HistoryEntity?

    @Upsert
    suspend fun upsert(item: HistoryEntity)

    @Query("DELETE FROM history WHERE sourceId = :sourceId AND contentId = :contentId AND type = :type")
    suspend fun delete(sourceId: String, contentId: String, type: String)

    @Query("DELETE FROM history WHERE sourceId = :sourceId")
    suspend fun clear(sourceId: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    /** Mantém apenas as [keep] entradas mais recentes da fonte. */
    @Query(
        """DELETE FROM history WHERE sourceId = :sourceId AND dedupeKey NOT IN (
           SELECT dedupeKey FROM history WHERE sourceId = :sourceId ORDER BY lastWatchedAt DESC LIMIT :keep)""",
    )
    suspend fun trim(sourceId: String, keep: Int)
}

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE sourceId = :sourceId")
    fun observe(sourceId: String): Flow<List<SyncStateEntity>>

    @Query("SELECT * FROM sync_state WHERE sourceId = :sourceId AND kind = :kind")
    suspend fun get(sourceId: String, kind: String): SyncStateEntity?

    @Upsert
    suspend fun upsert(item: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE sourceId = :sourceId")
    suspend fun deleteSource(sourceId: String)
}
