package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.ChannelRow
import com.samdevelop.samtv.ui.components.ConfirmDialog
import com.samdevelop.samtv.ui.components.EmptyView
import com.samdevelop.samtv.ui.components.LoadingView
import com.samdevelop.samtv.ui.components.SearchField
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ====================================================================== Busca

data class SearchResults(
    val query: String = "",
    val searching: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val isXtream: StateFlow<Boolean> = c.session.activeProfile.map { it?.type == SourceType.XTREAM }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Busca local (cache) em canais, filmes e séries, com debounce de 300 ms. */
    val results: StateFlow<SearchResults> =
        combine(_query.debounce(300).map { it.trim() }.distinctUntilChanged(), c.session.activeProfile) { q, p -> q to p }
            .flatMapLatest { (q, p) ->
                if (p == null || q.length < 2) flowOf(SearchResults(q))
                else flow {
                    emit(SearchResults(q, searching = true))
                    val ch = c.catalogRepository.channels(p.id, null, q, 0, LIMIT).items
                    val mv = if (p.type == SourceType.XTREAM) c.catalogRepository.movies(p.id, null, q, 0, LIMIT).items else emptyList()
                    val se = if (p.type == SourceType.XTREAM) c.catalogRepository.series(p.id, null, q, 0, LIMIT).items else emptyList()
                    emit(SearchResults(q, false, ch, mv, se))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    companion object {
        const val LIMIT = 60
        val Factory = containerFactory { c, _ -> SearchViewModel(c) }
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    vm: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val isXtream by vm.isXtream.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabs = if (isXtream) listOf("Canais (${results.channels.size})", "Filmes (${results.movies.size})", "Séries (${results.series.size})")
    else listOf("Canais (${results.channels.size})")
    if (tab >= tabs.size) tab = 0

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader("Buscar", onBack = onBack)
        SearchField(query, vm::setQuery, "Canais, filmes ou séries")
        when {
            query.trim().length < 2 -> EmptyView(Icons.Filled.Search, "Buscar", "Digite pelo menos 2 letras. A busca ignora acentos e maiúsculas.")
            results.searching -> LoadingView("Buscando…")
            results.channels.isEmpty() && results.movies.isEmpty() && results.series.isEmpty() ->
                EmptyView(Icons.Filled.SearchOff, "Nada encontrado", "Nenhum resultado para \"${results.query}\" no catálogo baixado.")
            else -> {
                if (tabs.size > 1) SecondaryTabRow(selectedTabIndex = tab, containerColor = SamColors.Background) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    when (tab) {
                        0 -> items(results.channels, key = { it.id }) { ch ->
                            ChannelRow(ch.name, ch.number, ch.logoUrl, null, onClick = { onPlayChannel(ch) }, onToggleFavorite = null)
                        }
                        1 -> items(results.movies, key = { it.id }) { m ->
                            ChannelRow(m.name, null, m.posterUrl, null, onClick = { onOpenMovie(m) }, onToggleFavorite = null, subtitle = m.year)
                        }
                        else -> items(results.series, key = { it.id }) { s ->
                            ChannelRow(s.name, null, s.coverUrl, null, onClick = { onOpenSeries(s) }, onToggleFavorite = null, subtitle = s.releaseDate?.take(4))
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================== Favoritos

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModel(private val c: AppContainer) : ViewModel() {
    val favorites: StateFlow<List<Favorite>?> = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else c.favoritesRepository.favorites(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(f: Favorite) = viewModelScope.launch { c.favoritesRepository.remove(f.sourceId, f.contentId, f.type) }

    companion object {
        val Factory = containerFactory { c, _ -> FavoritesViewModel(c) }
    }
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpen: (ContentType, String, String) -> Unit,
    vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory),
) {
    val list by vm.favorites.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader("Favoritos", onBack = onBack)
        val items = list
        when {
            items == null -> LoadingView()
            items.isEmpty() -> EmptyView(Icons.Filled.Favorite, "Sem favoritos", "Toque no coração de um canal, filme ou série para adicioná-lo aqui.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { "${it.type}:${it.contentId}" }) { f ->
                    ChannelRow(
                        name = f.title, number = null, logoUrl = f.imageUrl, isFavorite = null,
                        onClick = { onOpen(f.type, f.contentId, f.title) }, onToggleFavorite = null, subtitle = labelFor(f.type),
                        trailing = {
                            IconButton(onClick = { vm.remove(f) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover ${f.title} dos favoritos", tint = SamColors.TextSecondary)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ====================================================================== Histórico

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val c: AppContainer) : ViewModel() {
    val history: StateFlow<List<PlaybackHistory>?> = c.session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else c.historyRepository.history(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(h: PlaybackHistory) = viewModelScope.launch { c.historyRepository.remove(h.sourceId, h.contentId, h.type) }
    fun clear() = viewModelScope.launch { c.historyRepository.clear(c.session.activeProfile.value?.id) }

    companion object {
        val Factory = containerFactory { c, _ -> HistoryViewModel(c) }
    }
}

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpen: (PlaybackHistory) -> Unit,
    vm: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val list by vm.history.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")) }

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader("Histórico", onBack = onBack) {
            if (!list.isNullOrEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Limpar histórico") }
        }
        val items = list
        when {
            items == null -> LoadingView()
            items.isEmpty() -> EmptyView(Icons.Filled.History, "Histórico vazio", "Os conteúdos que você assistir aparecerão aqui.")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { "${it.type}:${it.contentId}" }) { h ->
                    val progress = if (h.type != ContentType.CHANNEL && h.positionMs > 0) " · parou em ${formatMs(h.positionMs)}" else ""
                    ChannelRow(
                        name = h.title, number = null, logoUrl = h.imageUrl, isFavorite = null,
                        onClick = { onOpen(h) }, onToggleFavorite = null,
                        subtitle = "${labelFor(h.type)} · ${fmt.format(Date(h.lastWatchedAtMillis))}$progress",
                        trailing = {
                            IconButton(onClick = { vm.remove(h) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remover ${h.title} do histórico", tint = SamColors.TextSecondary)
                            }
                        },
                    )
                }
            }
        }
    }
    if (confirm) ConfirmDialog(
        title = "Limpar histórico?",
        message = "Todo o histórico desta fonte será apagado deste aparelho.",
        confirmLabel = "Limpar",
        onConfirm = { vm.clear() },
        onDismiss = { confirm = false },
    )
}
