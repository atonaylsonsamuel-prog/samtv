package com.samdevelop.samtv.domain.usecase

import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AuthenticationResult
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.repository.AuthenticationRepository
import com.samdevelop.samtv.data.repository.CatalogRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ConnectionStatus {
    data object NoSource : ConnectionStatus
    data object Checking : ConnectionStatus
    data class Online(val auth: AuthenticationResult) : ConnectionStatus
    data class Offline(val error: AppError) : ConnectionStatus
}

data class SyncState(
    val running: Boolean = false,
    val currentKind: CatalogKind? = null,
    /** Resultado da última sincronização por tipo de catálogo (ausente = não tentado). */
    val results: Map<CatalogKind, AppResult<Int>> = emptyMap(),
)

/**
 * Estado da sessão em nível de aplicativo: fonte ativa, estado da conexão e sincronização do catálogo.
 * Ao trocar de fonte: revalida a conexão e, se não houver cache, baixa o catálogo em segundo plano.
 */
class SessionManager(
    private val auth: AuthenticationRepository,
    private val catalog: CatalogRepositoryImpl,
    private val scope: CoroutineScope,
) {
    val activeProfile: StateFlow<ConnectionProfile?> =
        auth.activeProfile.stateIn(scope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Checking)
    val connection: StateFlow<ConnectionStatus> = _connection.asStateFlow()

    private val _sync = MutableStateFlow(SyncState())
    val sync: StateFlow<SyncState> = _sync.asStateFlow()

    private var checkJob: Job? = null
    private var syncJob: Job? = null

    init {
        scope.launch {
            auth.activeProfile.distinctUntilChangedBy { it?.id to it?.updatedAtMillis }.collect { profile ->
                syncJob?.cancel()
                _sync.value = SyncState()
                if (profile == null) {
                    _connection.value = ConnectionStatus.NoSource
                } else {
                    recheckConnection()
                    if (!catalog.hasCache(profile.id)) refreshCatalog(profile)
                }
            }
        }
    }

    fun recheckConnection() {
        checkJob?.cancel()
        checkJob = scope.launch {
            _connection.value = ConnectionStatus.Checking
            _connection.value = when (val r = auth.checkActive()) {
                is AppResult.Success -> ConnectionStatus.Online(r.value)
                is AppResult.Failure -> if (r.error == AppError.NoActiveSource) ConnectionStatus.NoSource else ConnectionStatus.Offline(r.error)
            }
        }
    }

    /** Atualiza TV ao vivo e, quando a fonte suporta, filmes e séries. Falhas parciais não apagam o que funcionou. */
    fun refreshCatalog(target: ConnectionProfile? = activeProfile.value) {
        val profile = target ?: return
        if (_sync.value.running) return
        syncJob = scope.launch {
            val kinds = buildList {
                add(CatalogKind.LIVE)
                if (profile.type == SourceType.XTREAM) { add(CatalogKind.MOVIE); add(CatalogKind.SERIES) }
            }
            val results = LinkedHashMap<CatalogKind, AppResult<Int>>()
            _sync.value = SyncState(running = true, currentKind = kinds.first())
            for (kind in kinds) {
                _sync.value = SyncState(running = true, currentKind = kind, results = results.toMap())
                results[kind] = when (kind) {
                    CatalogKind.LIVE -> catalog.refresh(profile.id)
                    CatalogKind.MOVIE -> catalog.refreshMovies(profile.id)
                    CatalogKind.SERIES -> catalog.refreshSeries(profile.id)
                }
            }
            _sync.value = SyncState(running = false, currentKind = null, results = results.toMap())
        }
    }

    suspend fun clearCache() {
        val profile = activeProfile.value ?: return
        syncJob?.cancel()
        catalog.clearCache(profile.id)
        _sync.value = SyncState()
    }
}
