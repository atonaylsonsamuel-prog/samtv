package com.samdevelop.samtv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.core.common.AppError
import com.samdevelop.samtv.core.common.AppResult
import com.samdevelop.samtv.core.model.CatalogKind
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Movie
import com.samdevelop.samtv.core.model.Series
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.ui.common.ErrorMessages
import com.samdevelop.samtv.ui.common.containerFactory
import com.samdevelop.samtv.ui.components.CategoryChips
import com.samdevelop.samtv.ui.components.EmptyView
import com.samdevelop.samtv.ui.components.ErrorView
import com.samdevelop.samtv.ui.components.LoadMoreEffect
import com.samdevelop.samtv.ui.components.LoadingView
import com.samdevelop.samtv.ui.components.PosterCard
import com.samdevelop.samtv.ui.components.SearchField
import com.samdevelop.samtv.ui.theme.SamColors

class MoviesViewModel(c: AppContainer) : CatalogListViewModel<Movie>(
    session = c.session,
    favorites = c.favoritesRepository,
    favoriteType = ContentType.MOVIE,
    initialCategory = null,
    categoriesOf = { c.catalogRepository.movieCategories(it) },
    page = { s, cat, q, o, l -> c.catalogRepository.movies(s, cat, q, o, l) },
    idOf = { it.id },
) {
    companion object {
        val Factory = containerFactory { c, _ -> MoviesViewModel(c) }
    }
}

class SeriesViewModel(c: AppContainer) : CatalogListViewModel<Series>(
    session = c.session,
    favorites = c.favoritesRepository,
    favoriteType = ContentType.SERIES,
    initialCategory = null,
    categoriesOf = { c.catalogRepository.seriesCategories(it) },
    page = { s, cat, q, o, l -> c.catalogRepository.series(s, cat, q, o, l) },
    idOf = { it.id },
) {
    companion object {
        val Factory = containerFactory { c, _ -> SeriesViewModel(c) }
    }
}

@Composable
fun MoviesScreen(onOpen: (Movie) -> Unit, vm: MoviesViewModel = viewModel(factory = MoviesViewModel.Factory)) {
    PosterCatalog(
        title = "Filmes",
        searchHint = "Buscar filme",
        kind = CatalogKind.MOVIE,
        fallback = Icons.Outlined.Movie,
        vm = vm,
        titleOf = { it.name },
        imageOf = { it.posterUrl },
        captionOf = { listOfNotNull(it.year, it.rating?.let { r -> "★ $r" }).joinToString(" · ").ifEmpty { null } },
        onOpen = onOpen,
    )
}

@Composable
fun SeriesScreen(onOpen: (Series) -> Unit, vm: SeriesViewModel = viewModel(factory = SeriesViewModel.Factory)) {
    PosterCatalog(
        title = "Séries",
        searchHint = "Buscar série",
        kind = CatalogKind.SERIES,
        fallback = Icons.Outlined.VideoLibrary,
        vm = vm,
        titleOf = { it.name },
        imageOf = { it.coverUrl },
        captionOf = { it.releaseDate?.take(4) },
        onOpen = onOpen,
    )
}

@Composable
private fun <T : Any> PosterCatalog(
    title: String,
    searchHint: String,
    kind: CatalogKind,
    fallback: ImageVector,
    vm: CatalogListViewModel<T>,
    titleOf: (T) -> String,
    imageOf: (T) -> String?,
    captionOf: (T) -> String?,
    onOpen: (T) -> Unit,
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selectedCategory.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    LaunchedEffect(selected, query) { gridState.scrollToItem(0) }
    LoadMoreEffect(gridState) { vm.loadMore() }

    Column(Modifier.fillMaxSize().background(SamColors.Background)) {
        ScreenHeader(title)
        if (profile?.type == SourceType.M3U) {
            EmptyView(
                fallback, "Indisponível nesta fonte",
                "Playlists M3U não oferecem catálogo estruturado de ${title.lowercase()}. Use uma conexão Xtream Codes para acessar este recurso.",
            )
            return@Column
        }
        SearchField(query, vm::setQuery, searchHint)
        CategoryChips(categories, selected, vm::selectCategory)
        val syncError = (sync.results[kind] as? AppResult.Failure)?.error
        Box(Modifier.fillMaxSize()) {
            when {
                !list.initialLoaded || (list.items.isEmpty() && sync.running) ->
                    LoadingView(if (sync.running) "Baixando catálogo…" else "Carregando…")
                list.items.isEmpty() && query.isNotBlank() ->
                    EmptyView(Icons.Filled.SearchOff, "Nada encontrado", "Nenhum título corresponde a \"$query\".")
                list.items.isEmpty() && syncError != null && syncError != AppError.UnsupportedFeature ->
                    ErrorView(ErrorMessages.of(syncError), onRetry = vm::refreshCatalog)
                list.items.isEmpty() ->
                    EmptyView(
                        fallback, "Catálogo vazio",
                        "A fonte não disponibilizou ${title.lowercase()} ou o catálogo ainda não foi baixado.",
                        actionLabel = "Atualizar catálogo", onAction = vm::refreshCatalog,
                    )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(list.items, key = { vm.idOf(it) }) { item ->
                        PosterCard(titleOf(item), imageOf(item), fallback, onClick = { onOpen(item) }, caption = captionOf(item))
                    }
                    if (list.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
