package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Episode
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.MovieDetails
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SeriesDetails
import com.samdevelop.samtv.core.xtream.XtreamConnector
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.Artwork
import com.samdevelop.samtv.ui.components.ErrorView
import com.samdevelop.samtv.ui.components.FavoriteButton
import com.samdevelop.samtv.ui.components.InlineBanner
import com.samdevelop.samtv.ui.components.LoadingView
import com.samdevelop.samtv.ui.components.SamChip
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ====================================================================== Filme

data class MovieDetailsUiState(
    val loading: Boolean = true,
    val movie: Movie? = null,
    val details: MovieDetails? = null,
    val error: String? = null,
    val resume: PlaybackHistory? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailsViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val movieId: String = checkNotNull(handle.get<String>("id"))
    private val sourceId get() = c.session.activeProfile.value?.id

    private val _state = MutableStateFlow(MovieDetailsUiState())
    val state: StateFlow<MovieDetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(false) else c.favoritesRepository.isFavorite(p.id, movieId, ContentType.MOVIE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            val sid = sourceId ?: c.authRepository.activeProfile.first()?.id
            if (sid == null) { _state.value = MovieDetailsUiState(loading = false, error = "Nenhuma fonte ativa."); return@launch }
            val cached = c.catalogRepository.cachedMovie(sid, movieId)
            val resume = c.historyRepository.find(sid, movieId, ContentType.MOVIE)
            _state.update { it.copy(loading = true, movie = cached ?: it.movie, resume = resume, error = null) }
            when (val r = c.catalogRepository.movieDetails(sid, movieId)) {
                is AppResult.Success -> _state.update { it.copy(loading = false, details = r.value, movie = it.movie ?: r.value.movie) }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = ErrorMessages.of(r.error)) }
            }
        }
    }

    fun toggleFavorite() {
        val sid = sourceId ?: return
        val m = _state.value.movie ?: return
        viewModelScope.launch { c.favoritesRepository.toggle(Favorite(sid, m.id, ContentType.MOVIE, m.name, m.posterUrl, System.currentTimeMillis())) }
    }

    companion object {
        val Factory = containerFactory { c, h -> MovieDetailsViewModel(c, h) }
    }
}

@Composable
fun MovieDetailsScreen(
    onBack: () -> Unit,
    onPlay: (movie: Movie, ext: String?, resumeMs: Long) -> Unit,
    vm: MovieDetailsViewModel = viewModel(factory = MovieDetailsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fav by vm.isFavorite.collectAsStateWithLifecycle()
    val movie = state.movie ?: state.details?.movie

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader(movie?.name ?: "Filme", onBack = onBack) {
            if (movie != null) FavoriteButton(fav, vm::toggleFavorite)
        }
        when {
            movie == null && state.loading -> LoadingView()
            movie == null -> ErrorView(state.error ?: "Filme não encontrado.", onRetry = vm::load)
            else -> {
                val d = state.details
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        Row(Modifier.padding(16.dp)) {
                            Artwork(
                                movie.posterUrl ?: d?.movie?.posterUrl, Icons.Outlined.Movie,
                                Modifier.width(120.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(movie.name, style = MaterialTheme.typography.titleLarge)
                                val meta = listOfNotNull(
                                    (d?.releaseDate?.take(4) ?: movie.year),
                                    d?.durationSeconds?.takeIf { it > 0 }?.let { "${it / 60} min" },
                                    (movie.rating ?: d?.movie?.rating)?.let { "★ $it" },
                                ).joinToString(" · ")
                                if (meta.isNotEmpty()) Text(meta, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                d?.genre?.let { Text(it, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    item {
                        val ext = d?.movie?.containerExtension ?: movie.containerExtension
                        val resume = state.resume?.takeIf { it.positionMs > 10_000 && (it.durationMs == null || it.positionMs < it.durationMs!! - 30_000) }
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (resume != null) {
                                Button(onClick = { onPlay(movie, ext, resume.positionMs) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.PlayArrow, null); Text("  Continuar de ${formatMs(resume.positionMs)}")
                                }
                                OutlinedButton(onClick = { onPlay(movie, ext, 0) }, modifier = Modifier.fillMaxWidth()) { Text("Assistir do início") }
                            } else {
                                Button(onClick = { onPlay(movie, ext, 0) }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Filled.PlayArrow, null); Text("  Assistir")
                                }
                            }
                        }
                    }
                    if (state.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    state.error?.let { item { InlineBanner("Não foi possível carregar todos os detalhes: $it", isError = true) } }
                    d?.plot?.let { item { InfoBlock("Sinopse", it) } }
                    d?.cast?.let { item { InfoBlock("Elenco", it) } }
                    d?.director?.let { item { InfoBlock("Direção", it) } }
                }
            }
        }
    }
}

// ====================================================================== Série

data class SeriesDetailsUiState(
    val loading: Boolean = true,
    val series: Series? = null,
    val details: SeriesDetails? = null,
    val selectedSeason: Int? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailsViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val seriesId: String = checkNotNull(handle.get<String>("id"))
    private val sourceId get() = c.session.activeProfile.value?.id

    private val _state = MutableStateFlow(SeriesDetailsUiState())
    val state: StateFlow<SeriesDetailsUiState> = _state.asStateFlow()

    val isFavorite: StateFlow<Boolean> = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(false) else c.favoritesRepository.isFavorite(p.id, seriesId, ContentType.SERIES)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Último episódio assistido desta série (o histórico guarda um por série). */
    val lastWatched: StateFlow<PlaybackHistory?> = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(null) else c.historyRepository.history(p.id).map { list -> list.firstOrNull { it.parentId == seriesId } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { load() }

    fun load() {
        viewModelScope.launch {
            val sid = sourceId ?: c.authRepository.activeProfile.first()?.id
            if (sid == null) { _state.value = SeriesDetailsUiState(loading = false, error = "Nenhuma fonte ativa."); return@launch }
            val cached = c.catalogRepository.cachedSeries(sid, seriesId)
            _state.update { it.copy(loading = true, series = cached ?: it.series, error = null) }
            when (val r = c.catalogRepository.seriesDetails(sid, seriesId)) {
                is AppResult.Success -> _state.update {
                    it.copy(
                        loading = false,
                        details = r.value,
                        series = it.series ?: r.value.series,
                        selectedSeason = it.selectedSeason ?: r.value.seasons.firstOrNull()?.number,
                    )
                }
                is AppResult.Failure -> _state.update { it.copy(loading = false, error = ErrorMessages.of(r.error)) }
            }
        }
    }

    fun selectSeason(n: Int) = _state.update { it.copy(selectedSeason = n) }

    fun toggleFavorite() {
        val sid = sourceId ?: return
        val s = _state.value.series ?: return
        viewModelScope.launch { c.favoritesRepository.toggle(Favorite(sid, s.id, ContentType.SERIES, s.name, s.coverUrl, System.currentTimeMillis())) }
    }

    companion object {
        val Factory = containerFactory { c, h -> SeriesDetailsViewModel(c, h) }
    }
}

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onPlayEpisode: (series: Series, episode: Episode, resumeMs: Long) -> Unit,
    vm: SeriesDetailsViewModel = viewModel(factory = SeriesDetailsViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fav by vm.isFavorite.collectAsStateWithLifecycle()
    val last by vm.lastWatched.collectAsStateWithLifecycle()
    val series = state.series ?: state.details?.series

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader(series?.name?.ifBlank { null } ?: "Série", onBack = onBack) {
            if (series != null) FavoriteButton(fav, vm::toggleFavorite)
        }
        when {
            series == null && state.loading -> LoadingView()
            series == null -> ErrorView(state.error ?: "Série não encontrada.", onRetry = vm::load)
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    Row(Modifier.padding(16.dp)) {
                        Artwork(series.coverUrl, Icons.Outlined.VideoLibrary, Modifier.width(110.dp).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small))
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(series.name, style = MaterialTheme.typography.titleLarge)
                            val meta = listOfNotNull(series.releaseDate?.take(4), series.rating?.let { "★ $it" }, series.genre).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, color = SamColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            last?.let { Text("Último assistido: ${it.title}", color = SamColors.PurpleAccent, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                        }
                    }
                }
                (state.details?.series?.plot ?: series.plot)?.let { item { InfoBlock("Sinopse", it) } }
                if (state.loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                state.error?.let { item { InlineBanner("Não foi possível carregar os episódios: $it", isError = true) } }

                val d = state.details
                if (d != null) {
                    if (d.seasons.isEmpty()) {
                        item { InlineBanner("O servidor não informou episódios para esta série.") }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(d.seasons, key = { it.number }) { s ->
                                    SamChip(s.name.ifBlank { "Temporada ${s.number}" }, state.selectedSeason == s.number) { vm.selectSeason(s.number) }
                                }
                            }
                        }
                        val episodes = d.episodesOf(state.selectedSeason ?: d.seasons.first().number)
                        items(episodes, key = { it.id }) { ep ->
                            EpisodeRow(ep, isLast = last?.contentId == ep.id) {
                                val resume = last?.takeIf { it.contentId == ep.id && it.positionMs > 10_000 }?.positionMs ?: 0L
                                onPlayEpisode(series, ep, resume)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, isLast: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Assistir episódio", onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(ep.imageUrl, Icons.Filled.PlayArrow, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val prefix = ep.episodeNumber?.let { "E$it · " }.orEmpty()
            Text(prefix + ep.title.ifBlank { "Episódio" }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(ep.durationSeconds?.takeIf { it > 0 }?.let { "${it / 60} min" }, if (isLast) "Último assistido" else null).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelSmall, color = if (isLast) SamColors.PurpleAccent else SamColors.TextSecondary)
        }
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = SamColors.TextSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun InfoBlock(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SamColors.TextSecondary)
    }
}

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Id do servidor (sem prefixos) usado na URL de reprodução de filmes/episódios Xtream. */
fun serverKeyOf(id: String): String = XtreamConnector.rawId(id)
