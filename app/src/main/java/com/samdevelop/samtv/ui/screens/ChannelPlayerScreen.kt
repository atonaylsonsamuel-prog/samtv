package com.samdevelop.samtv.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.ElectronicProgramGuideEntry
import com.samdevelop.samtv.player.PlaybackPhase
import com.samdevelop.samtv.player.PlayerUiState
import com.samdevelop.samtv.player.ResizeMode
import com.samdevelop.samtv.player.TrackOption
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun ChannelPlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory)) {
    val screen by vm.state.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableStateOf(0) }
    var trackDialog by remember { mutableStateOf<String?>(null) } // "audio" | "text"
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }

    // Ciclo de vida: pausa em segundo plano, retoma ao voltar. O player é liberado no onCleared do ViewModel.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> vm.onStop()
                Lifecycle.Event.ON_START -> vm.onStart()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tela cheia: barras do sistema ocultas no player; orientação restaurada ao sair.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(landscape, fullscreenRequested) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (landscape || fullscreenRequested) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
    fun setFullscreen(on: Boolean) {
        fullscreenRequested = on
        activity?.requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    BackHandler(enabled = fullscreenRequested) { setFullscreen(false) }

    // Oculta os controles após alguns segundos de reprodução sem interação.
    LaunchedEffect(controlsVisible, interaction, player.isPlaying) {
        if (controlsVisible && player.isPlaying) {
            delay(4_000)
            controlsVisible = false
        }
    }

    val fullscreen = landscape || fullscreenRequested
    val videoModifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            videoModifier
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Mostrar controles") {
                    controlsVisible = !controlsVisible
                    interaction++
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        this.player = vm.controller.player
                    }
                },
                update = { view ->
                    view.resizeMode = when (player.resizeMode) {
                        ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        ResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    view.keepScreenOn = player.isPlaying || player.phase == PlaybackPhase.BUFFERING
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier.fillMaxSize(),
            )

            // Estados: resolvendo, carregando, reconectando, erro
            val setupError = screen.setupError
            val playError = player.error.takeIf { player.phase == PlaybackPhase.ERROR }
            when {
                setupError != null -> ErrorOverlay(setupError, onRetry = if (screen.nowPlaying?.channel != null) vm::retry else null, onBack = onBack)
                playError != null -> ErrorOverlay(playError.message, onRetry = vm::retry, onBack = onBack)
                screen.resolving || player.phase == PlaybackPhase.BUFFERING || player.phase == PlaybackPhase.IDLE -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SamColors.PurpleAccent)
                        player.reconnectAttempt?.let {
                            Spacer(Modifier.height(10.dp))
                            Text("Reconectando ($it/${player.maxReconnectAttempts})…", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Crossfade(targetState = controlsVisible && setupError == null, label = "controles") { visible ->
                if (visible) ControlsOverlay(
                    title = screen.nowPlaying?.title.orEmpty(),
                    state = player,
                    isChannel = screen.nowPlaying?.type == ContentType.CHANNEL,
                    canFavorite = screen.nowPlaying != null && screen.nowPlaying?.type != ContentType.EPISODE,
                    favorite = favorite,
                    hasPrevious = screen.hasPrevious,
                    hasNext = screen.hasNext,
                    fullscreen = fullscreen,
                    onBack = { if (fullscreenRequested) setFullscreen(false) else onBack() },
                    onInteraction = { interaction++ },
                    onPlayPause = vm.controller::togglePlayPause,
                    onSeekTo = vm.controller::seekTo,
                    onSeekBy = vm.controller::seekBy,
                    onPrevious = { vm.previousChannel() },
                    onNext = { vm.nextChannel() },
                    onFavorite = vm::toggleFavorite,
                    onResize = vm.controller::cycleResizeMode,
                    onAudio = { trackDialog = "audio" },
                    onText = { trackDialog = "text" },
                    onFullscreen = { setFullscreen(!fullscreen) },
                )
            }
        }

        if (!fullscreen) {
            InfoPanel(screen, player)
        }
    }

    when (trackDialog) {
        "audio" -> TrackDialog("Áudio", player.audioTracks, allowOff = false, offSelected = false, onSelect = { id -> id?.let(vm.controller::selectAudio) }, onDismiss = { trackDialog = null })
        "text" -> TrackDialog("Legendas", player.textTracks, allowOff = true, offSelected = !player.textEnabled, onSelect = vm.controller::selectText, onDismiss = { trackDialog = null })
    }
}

@Composable
private fun ControlsOverlay(
    title: String,
    state: PlayerUiState,
    isChannel: Boolean,
    canFavorite: Boolean,
    favorite: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onInteraction: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onResize: () -> Unit,
    onAudio: () -> Unit,
    onText: () -> Unit,
    onFullscreen: () -> Unit,
) {
    fun act(block: () -> Unit) = { onInteraction(); block() }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent, Color.Black.copy(alpha = 0.75f))),
        ),
    ) {
        // Topo
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopStart).then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (state.isLive) LiveBadge()
            if (canFavorite) IconButton(onClick = act(onFavorite)) {
                Icon(if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = if (favorite) "Remover dos favoritos" else "Adicionar aos favoritos", tint = if (favorite) SamColors.PurpleAccent else Color.White)
            }
        }

        // Centro: só mostra o que o conteúdo realmente suporta
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (isChannel) {
                IconButton(onClick = act(onPrevious), enabled = hasPrevious) { Icon(Icons.Filled.SkipPrevious, "Canal anterior", tint = if (hasPrevious) Color.White else Color.Gray, modifier = Modifier.size(36.dp)) }
            } else if (state.isSeekable) {
                IconButton(onClick = act { onSeekBy(-10_000) }) { Icon(Icons.Filled.Replay10, "Voltar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
            if (state.phase == PlaybackPhase.READY || state.phase == PlaybackPhase.ENDED || state.isPlaying) {
                IconButton(onClick = act(onPlayPause), modifier = Modifier.size(64.dp).background(SamColors.Purple.copy(alpha = 0.85f), RoundedCornerShape(50))) {
                    Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pausar" else "Reproduzir", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            if (isChannel) {
                IconButton(onClick = act(onNext), enabled = hasNext) { Icon(Icons.Filled.SkipNext, "Próximo canal", tint = if (hasNext) Color.White else Color.Gray, modifier = Modifier.size(36.dp)) }
            } else if (state.isSeekable) {
                IconButton(onClick = act { onSeekBy(10_000) }) { Icon(Icons.Filled.Forward10, "Avançar 10 segundos", tint = Color.White, modifier = Modifier.size(36.dp)) }
            }
        }

        // Base
        Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).then(if (fullscreen) Modifier.safeDrawingPadding() else Modifier).padding(horizontal = 8.dp)) {
            if (state.isSeekable && state.durationMs > 0) {
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                val progress = if (dragging) dragValue else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatMs(if (dragging) (dragValue * state.durationMs).toLong() else state.positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = progress,
                        onValueChange = { dragging = true; dragValue = it; onInteraction() },
                        onValueChangeFinished = { onSeekTo((dragValue * state.durationMs).toLong()); dragging = false },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = SamColors.PurpleAccent, activeTrackColor = SamColors.Purple),
                    )
                    Text(formatMs(state.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (state.audioTracks.size > 1) IconButton(onClick = act(onAudio)) { Icon(Icons.Filled.Audiotrack, "Faixas de áudio", tint = Color.White) }
                if (state.textTracks.isNotEmpty()) IconButton(onClick = act(onText)) { Icon(Icons.Filled.ClosedCaption, "Legendas", tint = Color.White) }
                IconButton(onClick = act(onResize)) { Icon(Icons.Filled.AspectRatio, "Proporção: ${state.resizeMode.label}", tint = Color.White) }
                IconButton(onClick = act(onFullscreen)) { Icon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, if (fullscreen) "Sair da tela cheia" else "Tela cheia", tint = Color.White) }
            }
        }
    }
}

/** Selo exibido somente quando o player confirma que o stream é ao vivo. */
@Composable
private fun LiveBadge() {
    Text(
        "AO VIVO",
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(horizontal = 6.dp).background(SamColors.Error, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ErrorOverlay(message: String, onRetry: (() -> Unit)?, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text("Voltar") }
                if (onRetry != null) Button(onClick = onRetry) { Text("Tentar novamente") }
            }
        }
    }
}

@Composable
private fun InfoPanel(screen: PlayerScreenState, state: PlayerUiState) {
    val np = screen.nowPlaying ?: return
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }
    Column(Modifier.fillMaxSize().background(SamColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(np.title, style = MaterialTheme.typography.titleLarge)
        val info = listOfNotNull(
            labelFor(np.type),
            if (state.isLive) "Ao vivo" else null,
            if (state.audioTracks.size > 1) "${state.audioTracks.size} faixas de áudio" else null,
            if (state.textTracks.isNotEmpty()) "Legendas disponíveis" else null,
        ).joinToString(" · ")
        Text(info, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        if (screen.epg.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Programação", style = MaterialTheme.typography.titleSmall)
            screen.epg.forEach { EpgRow(it, fmt) }
        }
    }
}

@Composable
private fun EpgRow(e: ElectronicProgramGuideEntry, fmt: SimpleDateFormat) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "${fmt.format(Date(e.startEpochSeconds * 1000))}–${fmt.format(Date(e.endEpochSeconds * 1000))}",
            color = SamColors.PurpleAccent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium)
            e.description?.let { Text(it, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TrackDialog(title: String, options: List<TrackOption>, allowOff: Boolean, offSelected: Boolean, onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (allowOff) TrackRow("Desativadas", offSelected) { onSelect(null); onDismiss() }
                options.forEach { o -> TrackRow(o.label, o.selected && !offSelected) { onSelect(o.id); onDismiss() } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        containerColor = SamColors.Surface,
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
