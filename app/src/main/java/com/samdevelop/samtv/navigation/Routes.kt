package com.samdevelop.samtv.navigation

import android.net.Uri
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.SourceType

/** Rotas de navegação. Argumentos de texto sempre codificados com [Uri.encode]. */
object Routes {
    const val STARTUP = "startup"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val LIVE = "live?categoryId={categoryId}"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val CONNECTION = "connection?type={type}&profileId={profileId}"
    const val MOVIE_DETAILS = "movie/{id}"
    const val SERIES_DETAILS = "seriesDetails/{id}"
    const val PLAYER = "player/{type}?id={id}&key={key}&ext={ext}&title={title}&image={image}&parent={parent}&cat={cat}&start={start}"

    val topLevel = setOf(HOME, LIVE, MOVIES, SERIES, SETTINGS)

    fun live(categoryId: String? = null) = if (categoryId == null) "live" else "live?categoryId=${Uri.encode(categoryId)}"
    fun connection(type: SourceType, profileId: String? = null) =
        "connection?type=${type.name}" + (profileId?.let { "&profileId=${Uri.encode(it)}" } ?: "")
    fun movie(id: String) = "movie/${Uri.encode(id)}"
    fun series(id: String) = "seriesDetails/${Uri.encode(id)}"

    /**
     * Abre o player. Para canais, só o id é passado (a URL — que pode conter credenciais — é lida do
     * cache local). Para filmes e episódios, [key] é o id numérico do servidor, sem credenciais.
     */
    fun player(
        type: ContentType,
        id: String,
        title: String,
        key: String? = null,
        ext: String? = null,
        image: String? = null,
        parent: String? = null,
        categoryId: String? = null,
        startMs: Long = 0,
    ): String = buildString {
        append("player/").append(type.name).append("?id=").append(Uri.encode(id))
        append("&title=").append(Uri.encode(title))
        key?.let { append("&key=").append(Uri.encode(it)) }
        ext?.let { append("&ext=").append(Uri.encode(it)) }
        image?.let { append("&image=").append(Uri.encode(it)) }
        parent?.let { append("&parent=").append(Uri.encode(it)) }
        categoryId?.let { append("&cat=").append(Uri.encode(it)) }
        if (startMs > 0) append("&start=").append(startMs)
    }
}
