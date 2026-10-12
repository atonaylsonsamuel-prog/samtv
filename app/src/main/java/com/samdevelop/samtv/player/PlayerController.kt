package com.samdevelop.samtv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import com.samdevelop.samtv.core.model.StreamKind
import com.samdevelop.samtv.core.model.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

enum class PlaybackPhase { IDLE, BUFFERING, READY, ENDED, ERROR }

/** Modos de proporção. Os valores mapeiam para AspectRatioFrameLayout na camada de UI. */
enum class ResizeMode(val label: String) { FIT("Ajustar"), ZOOM("Preencher"), STRETCH("Esticar") }

data class TrackOption(val id: String, val label: String, val selected: Boolean)

data class PlayerError(val message: String, val retryable: Boolean)

data class PlayerUiState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val isPlaying: Boolean = false,
    /** O stream é ao vivo segundo o próprio player (não por suposição). */
    val isLive: Boolean = false,
    /** Só mostramos barra de progresso quando o player confirma que dá para buscar. */
    val isSeekable: Boolean = false,
    val durationMs: Long = C.TIME_UNSET,
    val positionMs: Long = 0,
    val bufferedMs: Long = 0,
    val audioTracks: List<TrackOption> = emptyList(),
    val textTracks: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val error: PlayerError? = null,
    /** Tentativa atual de reconexão automática (1..max) ou null. */
    val reconnectAttempt: Int? = null,
    val maxReconnectAttempts: Int = 0,
)

/**
 * Camada de integração com o Media3. Vive dentro do PlayerViewModel: o player NÃO é recriado
 * em recomposições nem em rotação de tela, e é liberado em [release] (onCleared do ViewModel).
 */
@OptIn(UnstableApi::class)
class PlayerController(handle: PlayerHandle, private val scope: CoroutineScope) {

    val player = handle.player
    private val configureFor = handle.configureFor

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var source: StreamSource? = null
    private var attempts = 0
    private var reconnectJob: Job? = null
    private val trackGroups = HashMap<String, Pair<Tracks.Group, Int>>()
    private var released = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                attempts = 0
                _state.update { it.copy(reconnectAttempt = null, error = null) }
            }
            refresh()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = refresh()
        override fun onTracksChanged(tracks: Tracks) = refreshTracks(tracks)
        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private val ticker: Job = scope.launch {
        while (isActive) {
            if (!released && player.playbackState != Player.STATE_IDLE) {
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0),
                        bufferedMs = player.bufferedPosition.coerceAtLeast(0),
                        durationMs = player.duration,
                    )
                }
            }
            delay(500)
        }
    }

    init {
        player.addListener(listener)
    }

    fun play(stream: StreamSource, startPositionMs: Long = 0) {
        if (released) return
        reconnectJob?.cancel()
        source = stream
        attempts = 0
        _state.update { PlayerUiState(resizeMode = it.resizeMode, maxReconnectAttempts = maxAttempts(stream)) }
        configureFor(stream)
        player.setMediaItem(mediaItemFor(stream), startPositionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    fun pause() = player.pause()
    fun resume() { if (player.playbackState != Player.STATE_IDLE) player.play() }

    fun seekTo(positionMs: Long) {
        if (_state.value.isSeekable) player.seekTo(positionMs.coerceIn(0, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
    }

    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun cycleResizeMode() = _state.update {
        val next = ResizeMode.entries[(it.resizeMode.ordinal + 1) % ResizeMode.entries.size]
        it.copy(resizeMode = next)
    }

    fun selectAudio(id: String) {
        val (group, index) = trackGroups[id] ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    /** [id] null desativa as legendas. */
    fun selectText(id: String?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (id == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val (group, index) = trackGroups[id] ?: return
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Nova tentativa manual após erro. */
    fun retry() {
        val s = source ?: return
        attempts = 0
        reconnectJob?.cancel()
        _state.update { it.copy(error = null, reconnectAttempt = null) }
        restart(s)
    }

    fun release() {
        if (released) return
        released = true
        reconnectJob?.cancel()
        ticker.cancel()
        player.removeListener(listener)
        player.release()
    }

    // ------------------------------------------------------------------ interno

    private fun restart(s: StreamSource) {
        val resumeAt = if (s.kind == StreamKind.VOD) player.currentPosition.coerceAtLeast(0) else 0L
        configureFor(s)
        player.setMediaItem(mediaItemFor(s), resumeAt)
        player.prepare()
        player.playWhenReady = true
    }

    private fun refresh() {
        if (released) return
        val phase = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackPhase.BUFFERING
            Player.STATE_READY -> PlaybackPhase.READY
            Player.STATE_ENDED -> PlaybackPhase.ENDED
            else -> if (_state.value.error != null) PlaybackPhase.ERROR else PlaybackPhase.IDLE
        }
        _state.update {
            it.copy(
                phase = if (it.error != null && it.reconnectAttempt == null) PlaybackPhase.ERROR else phase,
                isPlaying = player.isPlaying,
                isLive = player.isCurrentMediaItemLive,
                isSeekable = player.isCurrentMediaItemSeekable && player.duration != C.TIME_UNSET && !player.isCurrentMediaItemLive,
                durationMs = player.duration,
            )
        }
    }

    private fun refreshTracks(tracks: Tracks) {
        trackGroups.clear()
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { g, group ->
            val type = group.type
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val id = "$type:$g:$i"
                trackGroups[id] = group to i
                val format = group.getTrackFormat(i)
                val base = format.label
                    ?: format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
                        ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("pt-BR")).replaceFirstChar(Char::uppercase) }
                val list = if (type == C.TRACK_TYPE_AUDIO) audio else text
                val label = base ?: "Faixa ${list.size + 1}"
                val extra = if (type == C.TRACK_TYPE_AUDIO && format.channelCount > 0) " (${format.channelCount}ch)" else ""
                list += TrackOption(id, label + extra, group.isTrackSelected(i))
            }
        }
        _state.update {
            it.copy(
                audioTracks = audio,
                textTracks = text,
                textEnabled = text.any(TrackOption::selected),
            )
        }
    }

    private fun handleError(error: PlaybackException) {
        val s = source
        val info = describe(error)
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val max = s?.let(::maxAttempts) ?: 0
        if (s != null && info.retryable && attempts < max) {
            attempts++
            val attempt = attempts
            _state.update { it.copy(phase = PlaybackPhase.BUFFERING, reconnectAttempt = attempt, error = info) }
            reconnectJob = scope.launch {
                delay(1_000L shl (attempt - 1)) // 1s, 2s, 4s
                if (!released) restart(s)
            }
        } else {
            _state.update { it.copy(phase = PlaybackPhase.ERROR, error = info, reconnectAttempt = null, isPlaying = false) }
        }
    }

    private fun maxAttempts(s: StreamSource) = if (s.kind == StreamKind.LIVE) 3 else 2

    private fun mediaItemFor(s: StreamSource): MediaItem {
        val builder = MediaItem.Builder().setUri(s.url)
        when (s.formatHint) {
            "hls" -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            "dash" -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        if (s.kind == StreamKind.LIVE) {
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(8_000).build())
        }
        return builder.build()
    }

    companion object {
        /** Traduz o erro do player para uma mensagem útil, sem expor a URL. */
        fun describe(error: PlaybackException): PlayerError {
            val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            return when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                    PlayerError("Falha de conexão com o servidor do stream.", true)
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                    PlayerError("O servidor demorou para responder.", true)
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> when (httpCode) {
                    401, 403 -> PlayerError("Acesso ao stream negado pelo servidor (HTTP $httpCode). Verifique sua conta ou o limite de conexões.", false)
                    404 -> PlayerError("Conteúdo não encontrado no servidor (HTTP 404).", false)
                    null -> PlayerError("O servidor respondeu com erro.", true)
                    else -> PlayerError("O servidor respondeu com erro (HTTP $httpCode).", (httpCode ?: 0) >= 500)
                }
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                    PlayerError("Conexão sem criptografia bloqueada.", false)
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                    PlayerError("O stream enviou dados inválidos.", true)
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                    PlayerError("Formato de stream não suportado. Para TV ao vivo Xtream, tente outro formato em Configurações.", false)
                in 4000..4999 -> // ERROR_CODE_DECODER_* / ERROR_CODE_DECODING_*
                    PlayerError("Este aparelho não suporta o codec deste conteúdo.", false)
                in 6000..6999 -> // ERROR_CODE_DRM_*
                    PlayerError("Conteúdo protegido por DRM não suportado por este aplicativo.", false)
                else -> PlayerError("Não foi possível reproduzir (código ${error.errorCodeName}).", true)
            }
        }
    }
}
