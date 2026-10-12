package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.AccountStatus
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.model.PlaybackHistory
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.core.repository.FavoritesRepository
import com.samdevelop.samtv.core.repository.HistoryRepository
import com.samdevelop.samtv.core.repository.LiveTvRepository
import com.samdevelop.samtv.domain.usecase.ConnectionStatus
import com.samdevelop.samtv.domain.usecase.SessionManager
import com.samdevelop.samtv.domain.usecase.SyncState
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.BrandMark
import com.samdevelop.samtv.ui.components.EmptyView
import com.samdevelop.samtv.ui.components.InlineBanner
import com.samdevelop.samtv.ui.components.PosterCard
import com.samdevelop.samtv.ui.components.SamChip
import com.samdevelop.samtv.ui.components.SectionTitle
import com.samdevelop.samtv.ui.components.ShortcutCard
import com.samdevelop.samtv.ui.theme.SamColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val profile: ConnectionProfile? = null,
    val connection: ConnectionStatus = ConnectionStatus.Checking,
    val sync: SyncState = SyncState(),
    val categories: List<ChannelCategory> = emptyList(),
    val favorites: List<Favorite> = emptyList(),
    val recent: List<PlaybackHistory> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val session: SessionManager,
    live: LiveTvRepository,
    favorites: FavoritesRepository,
    history: HistoryRepository,
) : ViewModel() {

    private val perSource = session.activeProfile.flatMapLatest { p ->
        if (p == null) flowOf(Triple(emptyList<ChannelCategory>(), emptyList<Favorite>(), emptyList<PlaybackHistory>()))
        else combine(live.categories(p.id), favorites.favorites(p.id), history.history(p.id)) { c, f, h ->
            Triple(c.take(30), f.take(15), h.take(15))
        }
    }

    val state: StateFlow<HomeUiState> =
        combine(session.activeProfile, session.connection, session.sync, perSource) { p, conn, sync, (c, f, h) ->
            HomeUiState(p, conn, sync, c, f, h)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        session.refreshCatalog()
        session.recheckConnection()
    }

    companion object {
        val Factory = containerFactory { c, _ -> HomeViewModel(c.session, c.catalogRepository, c.favoritesRepository, c.historyRepository) }
    }
}

@Composable
fun HomeScreen(
    onOpenLive: (String?) -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    onSearch: () -> Unit,
    onFavorites: () -> Unit,
    onHistory: () -> Unit,
    onOpenItem: (ContentType, String, String, String?) -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val isXtream = state.profile?.type == SourceType.XTREAM

    LazyColumn(Modifier.fillMaxSize().background(SamColors.Background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark(Modifier.weight(1f))
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Buscar") }
                IconButton(onClick = onFavorites) { Icon(Icons.Filled.Favorite, contentDescription = "Favoritos") }
                IconButton(onClick = onHistory) { Icon(Icons.Filled.History, contentDescription = "Histórico") }
            }
        }
        item { ConnectionStatusRow(state, onRefresh = vm::refresh) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShortcutCard("TV ao vivo", Icons.Outlined.LiveTv, true, { onOpenLive(null) }, Modifier.weight(1f))
                ShortcutCard(
                    "Filmes", Icons.Outlined.Movie, isXtream && state.sync.results[CatalogKind.MOVIE] !is AppResult.Failure,
                    onOpenMovies, Modifier.weight(1f), note = if (!isXtream) "Indisponível em M3U" else null,
                )
                ShortcutCard(
                    "Séries", Icons.Outlined.VideoLibrary, isXtream && state.sync.results[CatalogKind.SERIES] !is AppResult.Failure,
                    onOpenSeries, Modifier.weight(1f), note = if (!isXtream) "Indisponível em M3U" else null,
                )
            }
        }

        if (state.categories.isNotEmpty()) {
            item { SectionTitle("Categorias de TV") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { c -> SamChip(c.name, false) { onOpenLive(c.id) } }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item { SectionTitle("Assistidos recentemente", action = { TextButton(onClick = onHistory) { Text("Ver tudo") } }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(state.recent, key = { "${it.type}:${it.contentId}" }) { h ->
                        PosterCard(
                            h.title, h.imageUrl, iconFor(h.type),
                            onClick = { onOpenItem(h.type, h.contentId, h.title, h.parentId) },
                            modifier = Modifier.width(112.dp),
                            caption = labelFor(h.type),
                        )
                    }
                }
            }
        }

        if (state.favorites.isNotEmpty()) {
            item { SectionTitle("Favoritos", action = { TextButton(onClick = onFavorites) { Text("Ver tudo") } }) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(state.favorites, key = { "${it.type}:${it.contentId}" }) { f ->
                        PosterCard(
                            f.title, f.imageUrl, iconFor(f.type),
                            onClick = { onOpenItem(f.type, f.contentId, f.title, null) },
                            modifier = Modifier.width(112.dp),
                            caption = labelFor(f.type),
                        )
                    }
                }
            }
        }

        val catalogEmpty = state.categories.isEmpty() && !state.sync.running && state.profile != null
        if (catalogEmpty && state.sync.results[CatalogKind.LIVE] !is AppResult.Failure && state.sync.results.isNotEmpty()) {
            item {
                Box(Modifier.height(320.dp)) {
                    EmptyView(Icons.Outlined.LiveTv, "Nenhuma categoria", "A fonte não informou categorias. Abra a TV ao vivo para ver todos os canais.")
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusRow(state: HomeUiState, onRefresh: () -> Unit) {
    val (color, text) = when (val c = state.connection) {
        ConnectionStatus.NoSource -> SamColors.TextSecondary to "Nenhuma fonte ativa"
        ConnectionStatus.Checking -> SamColors.TextSecondary to "Verificando conexão…"
        is ConnectionStatus.Online -> {
            val exp = c.auth.expiresAtEpochSeconds?.let { " · vence em ${ErrorMessages.formatDate(it)}" }.orEmpty()
            val status = if (c.auth.status == AccountStatus.UNKNOWN) "Conectado" else "Conta ativa"
            SamColors.Success to "$status$exp"
        }
        is ConnectionStatus.Offline -> SamColors.Error to "Sem conexão com a fonte — exibindo dados salvos"
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            (state.profile?.name?.let { "$it · " } ?: "") + text,
            style = MaterialTheme.typography.bodySmall,
            color = SamColors.TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        IconButton(onClick = onRefresh, enabled = !state.sync.running) {
            Icon(Icons.Filled.Refresh, contentDescription = "Atualizar catálogo", tint = if (state.sync.running) Color.Gray else SamColors.TextSecondary)
        }
    }
    if (state.sync.running) {
        val label = when (state.sync.currentKind) {
            CatalogKind.LIVE -> "Baixando canais…"
            CatalogKind.MOVIE -> "Baixando filmes…"
            CatalogKind.SERIES -> "Baixando séries…"
            null -> "Atualizando…"
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = SamColors.TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = SamColors.Purple)
    }
    (state.connection as? ConnectionStatus.Offline)?.let { InlineBanner(ErrorMessages.of(it.error), isError = true) }
    (state.sync.results[CatalogKind.LIVE] as? AppResult.Failure)?.let {
        InlineBanner("Falha ao atualizar canais: ${ErrorMessages.of(it.error)}", isError = true)
    }
}

fun iconFor(type: ContentType) = when (type) {
    ContentType.CHANNEL -> Icons.Outlined.LiveTv
    ContentType.MOVIE -> Icons.Outlined.Movie
    ContentType.SERIES, ContentType.EPISODE -> Icons.Outlined.VideoLibrary
}

fun labelFor(type: ContentType) = when (type) {
    ContentType.CHANNEL -> "Canal"
    ContentType.MOVIE -> "Filme"
    ContentType.SERIES -> "Série"
    ContentType.EPISODE -> "Episódio"
}
