package com.samdevelop.samtv.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.core.model.ConnectionProfile
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.Favorite
import com.samdevelop.samtv.core.repository.FavoritesRepository
import com.samdevelop.samtv.core.repository.Page
import com.samdevelop.samtv.domain.usecase.SessionManager
import com.samdevelop.samtv.domain.usecase.SyncState
import com.samdevelop.samtv.ui.common.PagedList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base para listas de catálogo (TV, filmes, séries): categoria selecionada, busca com debounce,
 * paginação sob demanda a partir do cache local e favoritos.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
open class CatalogListViewModel<T : Any>(
    protected val session: SessionManager,
    private val favorites: FavoritesRepository,
    private val favoriteType: ContentType,
    initialCategory: String?,
    private val categoriesOf: (sourceId: String) -> Flow<List<ChannelCategory>>,
    private val page: suspend (sourceId: String, categoryId: String?, query: String?, offset: Int, limit: Int) -> Page<T>,
    val idOf: (T) -> String,
) : ViewModel() {

    val profile: StateFlow<ConnectionProfile?> = session.activeProfile
    val sync: StateFlow<SyncState> = session.sync

    private val _selectedCategory = MutableStateFlow(initialCategory)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _list = MutableStateFlow(PagedList<T>())
    val list: StateFlow<PagedList<T>> = _list.asStateFlow()

    val categories: StateFlow<List<ChannelCategory>> = profile.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else categoriesOf(p.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = profile.flatMapLatest { p ->
        if (p == null) flowOf(emptySet()) else favorites.favorites(p.id).map { list -> list.filter { it.type == favoriteType }.map { it.contentId }.toSet() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var loadJob: Job? = null

    init {
        // Recarrega do início quando muda fonte, categoria, busca (com debounce) ou termina uma sincronização.
        viewModelScope.launch {
            combine(
                profile.map { it?.id }.distinctUntilChanged(),
                _selectedCategory,
                _query.debounce { if (it.isEmpty()) 0L else 300L }.map { it.trim() }.distinctUntilChanged(),
            ) { a, b, c -> Triple(a, b, c) }.collect { reload() }
        }
        viewModelScope.launch {
            session.sync.map { it.running }.distinctUntilChanged().drop(1).filter { !it }.collect { reload() }
        }
    }

    fun selectCategory(id: String?) { _selectedCategory.value = id }
    fun setQuery(q: String) { _query.value = q }

    fun reload() {
        loadJob?.cancel()
        _list.value = PagedList(loading = true)
        loadMore(force = true)
    }

    fun loadMore(force: Boolean = false) {
        val current = _list.value
        if (!force && (current.loading || current.endReached)) return
        val sourceId = profile.value?.id ?: run { _list.value = PagedList(initialLoaded = true, endReached = true); return }
        _list.update { it.copy(loading = true) }
        loadJob = viewModelScope.launch {
            val offset = if (force) 0 else current.items.size
            val result = page(sourceId, _selectedCategory.value, _query.value.trim().ifEmpty { null }, offset, PAGE_SIZE)
            _list.update {
                val base = if (force) emptyList() else it.items
                PagedList(items = base + result.items, loading = false, endReached = result.endReached, initialLoaded = true)
            }
        }
    }

    fun toggleFavorite(item: T, title: String, image: String?) {
        val sourceId = profile.value?.id ?: return
        viewModelScope.launch {
            favorites.toggle(Favorite(sourceId, idOf(item), favoriteType, title, image, System.currentTimeMillis()))
        }
    }

    fun refreshCatalog() = session.refreshCatalog()

    companion object {
        const val PAGE_SIZE = 120
    }
}
