package com.samdevelop.samtv.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.PlaybackRequest
import com.samdevelop.samtv.player.PlayerController
import com.samdevelop.samtv.player.PlayerUiState
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** O que está tocando (sem URL). */
data class NowPlaying(
    val type: ContentType,
    val contentId: String,
    val title: String,
    val imageUrl: String?,
    val parentId: String?,
    val channel: Channel? = null,
)

data class PlayerScreenState(
    val nowPlaying: NowPlaying? = null,
    /** Erro antes de o player iniciar (ex.: fonte, credenciais, HTTP não permitido). */
    val setupError: String? = null,
    val resolving: Boolean = true,
    val epg: List<ElectronicProgramGuideEntry> = emptyList(),
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {

    private val type = ContentType.valueOf(checkNotNull(handle.get<String>("type")))
    private val initialId: String = checkNotNull(handle.get<String>("id"))
    private val key: String? = handle.get<String>("key")
    private val ext: String? = handle.get<String>("ext")
    private val initialTitle: String = handle.get<String>("title").orEmpty()
    private val image: String? = handle.get<String>("image")
    private val parent: String? = handle.get<String>("parent")
    private val categoryId: String? = handle.get<String>("cat")
    private val startMs: Long = handle.get<String>("start")?.toLongOrNull() ?: 0L

    /** Criado uma única vez por tela: sobrevive à rotação e não é recriado em recomposições. */
    val controller = PlayerController(c.playerFactory.create(), viewModelScope)
    val playerState: StateFlow<PlayerUiState> = controller.state

    private val _state = MutableStateFlow(PlayerScreenState())
    val state: StateFlow<PlayerScreenState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = _state.flatMapLatest { s ->
        val np = s.nowPlaying
        val sid = c.session.activeProfile.value?.id
        if (np == null || sid == null || np.type == ContentType.EPISODE) flowOf(false)
        else c.favoritesRepository.isFavorite(sid, np.contentId, np.type)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var sourceId: String? = null
    private var neighbours: Pair<Channel?, Channel?> = null to null
    private var saveJob: Job? = null
    private var wasPlayingBeforeStop = false

    init {
        viewModelScope.launch {
            sourceId = c.session.activeProfile.value?.id ?: c.authRepository.activeProfile.first()?.id
            if (type == ContentType.CHANNEL) {
                val ch = sourceId?.let { c.catalogRepository.channel(it, initialId) }
                if (ch == null) _state.update { it.copy(resolving = false, setupError = "Canal não encontrado no catálogo. Atualize o catálogo e tente novamente.") }
                else playChannel(ch)
            } else {
                val np = NowPlaying(type, initialId, initialTitle, image, parent)
                start(np, PlaybackRequest(sourceId.orEmpty(), initialId, type, initialTitle, image, key ?: initialId, ext, parentId = parent), startMs)
            }
        }
    }

    fun previousChannel() = neighbours.first?.let { viewModelScope.launch { playChannel(it) } }
    fun nextChannel() = neighbours.second?.let { viewModelScope.launch { playChannel(it) } }

    fun retry() {
        if (_state.value.setupError != null) {
            val np = _state.value.nowPlaying
            if (np?.channel != null) viewModelScope.launch { playChannel(np.channel) }
        } else controller.retry()
    }

    fun toggleFavorite() {
        val sid = sourceId ?: return
        val np = _state.value.nowPlaying ?: return
        if (np.type == ContentType.EPISODE) return
        viewModelScope.launch { c.favoritesRepository.toggle(Favorite(sid, np.contentId, np.type, np.title, np.imageUrl, System.currentTimeMillis())) }
    }

    /** Ciclo de vida da tela: pausa ao sair do primeiro plano e retoma ao voltar. */
    fun onStop() {
        wasPlayingBeforeStop = controller.player.playWhenReady
        controller.pause()
        saveProgress()
    }

    fun onStart() {
        if (wasPlayingBeforeStop) controller.resume()
    }

    // ------------------------------------------------------------------ interno

    private suspend fun playChannel(ch: Channel) {
        saveProgress()
        val np = NowPlaying(ContentType.CHANNEL, ch.id, ch.name, ch.logoUrl, null, ch)
        val sid = sourceId ?: return
        neighbours = c.catalogRepository.neighbours(sid, ch.id, categoryId)
        _state.update {
            it.copy(hasPrevious = neighbours.first != null, hasNext = neighbours.second != null, epg = emptyList())
        }
        start(np, PlaybackRequest(sid, ch.id, ContentType.CHANNEL, ch.name, ch.logoUrl, ch.streamKey, ch.containerExtension, ch.httpHeaders), 0)
        loadEpg(ch)
    }

    private suspend fun start(np: NowPlaying, request: PlaybackRequest, positionMs: Long) {
        _state.update { it.copy(nowPlaying = np, resolving = true, setupError = null) }
        if (request.sourceId.isEmpty()) {
            _state.update { it.copy(resolving = false, setupError = "Nenhuma fonte ativa.") }
            return
        }
        when (val r = c.playbackSourceProvider.resolve(request)) {
            is AppResult.Failure -> _state.update { it.copy(resolving = false, setupError = ErrorMessages.of(r.error)) }
            is AppResult.Success -> {
                _state.update { it.copy(resolving = false) }
                controller.play(r.value, positionMs)
                record(np, positionMs)
                startPeriodicSave()
            }
        }
    }

    private fun loadEpg(ch: Channel) {
        val sid = sourceId ?: return
        if (ch.epgChannelId == null && c.session.activeProfile.value?.type != com.samdevelop.samtv.core.model.SourceType.XTREAM) return
        viewModelScope.launch {
            val r = c.catalogRepository.shortEpg(sid, ch)
            if (r is AppResult.Success && _state.value.nowPlaying?.contentId == ch.id) _state.update { it.copy(epg = r.value) }
        }
    }

    private fun startPeriodicSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000)
                saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val np = _state.value.nowPlaying ?: return
        if (np.type == ContentType.CHANNEL) return // canais não têm posição
        val pos = controller.player.currentPosition
        if (pos <= 0) return
        record(np, pos)
    }

    private fun record(np: NowPlaying, positionMs: Long) {
        val sid = sourceId ?: return
        val duration = controller.player.duration.takeIf { it > 0 }
        val entry = PlaybackHistory(sid, np.contentId, np.type, np.title, np.imageUrl, np.parentId, positionMs, duration, System.currentTimeMillis())
        c.appScope.launch { c.historyRepository.record(entry) }
    }

    override fun onCleared() {
        saveProgress()
        saveJob?.cancel()
        controller.release()
        super.onCleared()
    }

    companion object {
        val Factory = containerFactory { c, h -> PlayerViewModel(c, h) }
    }
}
