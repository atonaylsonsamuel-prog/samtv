package com.samdevelop.samtv

import android.content.Context
import com.samdevelop.samtv.core.net.HttpClient
import com.samdevelop.samtv.data.local.AppDatabase
import com.samdevelop.samtv.data.local.AppPreferences
import com.samdevelop.samtv.data.remote.OkHttpHttpClient
import com.samdevelop.samtv.data.repository.AuthenticationRepositoryImpl
import com.samdevelop.samtv.data.repository.CatalogRepositoryImpl
import com.samdevelop.samtv.data.repository.ConnectorProvider
import com.samdevelop.samtv.data.repository.FavoritesRepositoryImpl
import com.samdevelop.samtv.data.repository.HistoryRepositoryImpl
import com.samdevelop.samtv.data.repository.PlaybackSourceProviderImpl
import com.samdevelop.samtv.domain.usecase.SessionManager
import com.samdevelop.samtv.player.PlayerFactory
import com.samdevelop.samtv.security.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Injeção de dependências manual (sem framework) — o grafo é pequeno e explícito.
 * Uma única instância vive na [SamTvApplication].
 */
class AppContainer(
    context: Context,
    val database: AppDatabase = AppDatabase.build(context),
    baseOkHttp: OkHttpClient = OkHttpHttpClient.defaultClient(),
) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = AppPreferences(appContext)
    val credentials = CredentialStore(appContext)
    val httpClient: HttpClient = OkHttpHttpClient(baseOkHttp)

    val connectors = ConnectorProvider(database.profiles(), credentials, httpClient)
    val authRepository = AuthenticationRepositoryImpl(database, preferences, credentials, connectors)
    val catalogRepository = CatalogRepositoryImpl(database, connectors)
    val favoritesRepository = FavoritesRepositoryImpl(database)
    val historyRepository = HistoryRepositoryImpl(database)
    val playbackSourceProvider = PlaybackSourceProviderImpl(connectors, preferences)
    val playerFactory = PlayerFactory(appContext, baseOkHttp)

    val session: SessionManager by lazy { SessionManager(authRepository, catalogRepository, appScope) }
}
