package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.Channel
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.CategoryChips
import com.samdevelop.samtv.ui.components.ChannelRow
import com.samdevelop.samtv.ui.components.EmptyView
import com.samdevelop.samtv.ui.components.ErrorView
import com.samdevelop.samtv.ui.components.LoadMoreEffect
import com.samdevelop.samtv.ui.components.LoadingView
import com.samdevelop.samtv.ui.components.SearchField
import com.samdevelop.samtv.ui.theme.SamColors

class LiveTvViewModel(
    container: com.samdevelop.samtv.AppContainer,
    initialCategory: String?,
) : CatalogListViewModel<Channel>(
    session = container.session,
    favorites = container.favoritesRepository,
    favoriteType = ContentType.CHANNEL,
    initialCategory = initialCategory,
    categoriesOf = { container.catalogRepository.categories(it) },
    page = { s, c, q, o, l -> container.catalogRepository.channels(s, c, q, o, l) },
    idOf = { it.id },
) {
    companion object {
        val Factory = containerFactory { c, h -> LiveTvViewModel(c, h.get<String>("categoryId")) }
    }
}

@Composable
fun LiveTvScreen(
    initialCategory: String?,
    onPlay: (Channel, String?) -> Unit,
    vm: LiveTvViewModel = viewModel(factory = LiveTvViewModel.Factory),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Ao vir da Home por uma categoria específica, aplica a seleção.
    LaunchedEffect(initialCategory) { if (initialCategory != null) vm.selectCategory(initialCategory) }
    LaunchedEffect(selected, query) { listState.scrollToItem(0) }
    LoadMoreEffect(listState) { vm.loadMore() }

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader("TV ao vivo")
        SearchField(query, vm::setQuery, "Buscar canal")
        CategoryChips(categories, selected, vm::selectCategory)

        val syncError = sync.results[CatalogKind.LIVE] as? AppResult.Failure
        Box(Modifier.fillMaxSize()) {
            when {
                !list.initialLoaded || (list.items.isEmpty() && sync.running) ->
                    LoadingView(if (sync.running) "Baixando lista de canais…" else "Carregando…")
                list.items.isEmpty() && query.isNotBlank() ->
                    EmptyView(Icons.Filled.SearchOff, "Nada encontrado", "Nenhum canal corresponde a \"$query\".")
                list.items.isEmpty() && syncError != null ->
                    ErrorView(ErrorMessages.of(syncError.error), onRetry = vm::refreshCatalog)
                list.items.isEmpty() ->
                    EmptyView(
                        Icons.Outlined.LiveTv, "Nenhum canal",
                        "A lista ainda não foi baixada ou a fonte não possui canais nesta categoria.",
                        actionLabel = "Atualizar catálogo", onAction = vm::refreshCatalog,
                    )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(list.items, key = { it.id }) { ch ->
                        ChannelRow(
                            name = ch.name,
                            number = ch.number,
                            logoUrl = ch.logoUrl,
                            isFavorite = ch.id in favorites,
                            onClick = { onPlay(ch, selected) },
                            onToggleFavorite = { vm.toggleFavorite(ch, ch.name, ch.logoUrl) },
                        )
                        HorizontalDivider(color = SamColors.Outline.copy(alpha = 0.4f), modifier = Modifier.padding(start = 92.dp))
                    }
                    if (list.loading) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
