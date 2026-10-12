package com.samdevelop.samtv.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.samdevelop.samtv.AppContainer
import com.samdevelop.samtv.SamTvApplication

/** Estados padrão de tela. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String) : UiState<Nothing>
    data class Error(val message: String, val canRetry: Boolean = true) : UiState<Nothing>
}

/** Lista paginada carregada sob demanda. */
data class PagedList<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val initialLoaded: Boolean = false,
)

/** Fábrica de ViewModels com acesso ao [AppContainer] e aos argumentos de navegação. */
inline fun <reified VM : ViewModel> containerFactory(crossinline create: (AppContainer, SavedStateHandle) -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            val app = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as SamTvApplication
            return create(app.container, extras.createSavedStateHandle()) as T
        }
    }
