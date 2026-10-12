package com.samdevelop.samtv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "samtv_prefs")

/** Formato preferido para TV ao vivo em fontes Xtream. */
enum class LiveFormat(val extension: String?) { AUTO(null), HLS("m3u8"), TS("ts") }

/** Preferências simples (DataStore). Nada sensível é guardado aqui. */
class AppPreferences(context: Context) {
    private val store = context.applicationContext.dataStore

    val activeProfileId: Flow<String?> = store.data.map { it[ACTIVE_PROFILE] }
    val liveFormat: Flow<LiveFormat> = store.data.map { p ->
        p[LIVE_FORMAT]?.let { runCatching { LiveFormat.valueOf(it) }.getOrNull() } ?: LiveFormat.AUTO
    }

    suspend fun activeProfileIdNow(): String? = activeProfileId.first()

    suspend fun setActiveProfile(id: String?) {
        store.edit { if (id == null) it.remove(ACTIVE_PROFILE) else it[ACTIVE_PROFILE] = id }
    }

    suspend fun setLiveFormat(format: LiveFormat) {
        store.edit { it[LIVE_FORMAT] = format.name }
    }

    private companion object {
        val ACTIVE_PROFILE = stringPreferencesKey("active_profile_id")
        val LIVE_FORMAT = stringPreferencesKey("live_format")
    }
}
