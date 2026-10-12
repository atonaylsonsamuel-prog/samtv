package com.samdevelop.samtv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun categories(): CategoryDao
    abstract fun channels(): ChannelDao
    abstract fun movies(): MovieDao
    abstract fun series(): SeriesDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun syncState(): SyncStateDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "samtv.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java).build()
    }
}
