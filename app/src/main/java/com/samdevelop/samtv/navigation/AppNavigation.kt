package com.samdevelop.samtv.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.samdevelop.samtv.core.model.ContentType
import com.samdevelop.samtv.core.model.SourceType
import com.samdevelop.samtv.ui.screens.ChannelPlayerScreen
import com.samdevelop.samtv.ui.screens.ConnectionScreen
import com.samdevelop.samtv.ui.screens.FavoritesScreen
import com.samdevelop.samtv.ui.screens.HistoryScreen
import com.samdevelop.samtv.ui.screens.HomeScreen
import com.samdevelop.samtv.ui.screens.LiveTvScreen
import com.samdevelop.samtv.ui.screens.MovieDetailsScreen
import com.samdevelop.samtv.ui.screens.MoviesScreen
import com.samdevelop.samtv.ui.screens.SearchScreen
import com.samdevelop.samtv.ui.screens.SeriesDetailsScreen
import com.samdevelop.samtv.ui.screens.SeriesScreen
import com.samdevelop.samtv.ui.screens.SettingsScreen
import com.samdevelop.samtv.ui.screens.StartupScreen
import com.samdevelop.samtv.ui.screens.WelcomeScreen
import com.samdevelop.samtv.ui.screens.serverKeyOf
import com.samdevelop.samtv.ui.theme.SamColors

private data class TopItem(val route: String, val target: String, val label: String, val icon: ImageVector)

private val topItems = listOf(
    TopItem(Routes.HOME, Routes.HOME, "Início", Icons.Filled.Home),
    TopItem(Routes.LIVE, Routes.live(), "TV", Icons.Outlined.LiveTv),
    TopItem(Routes.MOVIES, Routes.MOVIES, "Filmes", Icons.Outlined.Movie),
    TopItem(Routes.SERIES, Routes.SERIES, "Séries", Icons.Outlined.VideoLibrary),
    TopItem(Routes.SETTINGS, Routes.SETTINGS, "Ajustes", Icons.Filled.Settings),
)

private fun optionalString(name: String) = navArgument(name) { type = NavType.StringType; nullable = true; defaultValue = null }

@Composable
fun AppNavigation(nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel

    Scaffold(
        containerColor = SamColors.Background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = SamColors.Surface) {
                    topItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                nav.navigate(item.target) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SamColors.TextPrimary,
                                selectedTextColor = SamColors.TextPrimary,
                                indicatorColor = SamColors.Purple,
                                unselectedIconColor = SamColors.TextSecondary,
                                unselectedTextColor = SamColors.TextSecondary,
                            ),
                        )
                    }
                }
            }
        },
        // As telas tratam os próprios insets (status bar); o Scaffold só reserva a barra inferior.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val bottomInset = if (!showBottomBar && currentRoute != Routes.PLAYER) Modifier.navigationBarsPadding() else Modifier
        Box(Modifier.fillMaxSize().padding(padding).then(bottomInset)) {
            NavGraph(nav)
        }
    }
}

@Composable
private fun NavGraph(nav: NavHostController) {
    fun openItem(type: ContentType, id: String, title: String, parentId: String?) {
        when (type) {
            ContentType.CHANNEL -> nav.navigate(Routes.player(ContentType.CHANNEL, id, title))
            ContentType.MOVIE -> nav.navigate(Routes.movie(id))
            ContentType.SERIES -> nav.navigate(Routes.series(id))
            ContentType.EPISODE -> parentId?.let { nav.navigate(Routes.series(it)) }
        }
    }
    fun goHomeClearing() = nav.navigate(Routes.HOME) { popUpTo(nav.graph.id) { inclusive = true } }
    fun goWelcomeClearing() = nav.navigate(Routes.WELCOME) { popUpTo(nav.graph.id) { inclusive = true } }

    NavHost(nav, startDestination = Routes.STARTUP) {
        composable(Routes.STARTUP) {
            StartupScreen(onHasSource = ::goHomeClearing, onFirstUse = ::goWelcomeClearing)
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(onAdd = { nav.navigate(Routes.connection(it)) })
        }
        composable(Routes.CONNECTION, arguments = listOf(optionalString("type"), optionalString("profileId"))) {
            ConnectionScreen(onBack = { nav.popBackStack() }, onConnected = ::goHomeClearing)
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenLive = { nav.navigate(Routes.live(it)) },
                onOpenMovies = { nav.navigate(Routes.MOVIES) },
                onOpenSeries = { nav.navigate(Routes.SERIES) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onFavorites = { nav.navigate(Routes.FAVORITES) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onOpenItem = ::openItem,
            )
        }
        composable(Routes.LIVE, arguments = listOf(optionalString("categoryId"))) { entry ->
            LiveTvScreen(
                initialCategory = entry.arguments?.getString("categoryId"),
                onPlay = { ch, cat -> nav.navigate(Routes.player(ContentType.CHANNEL, ch.id, ch.name, categoryId = cat)) },
            )
        }
        composable(Routes.MOVIES) {
            MoviesScreen(onOpen = { nav.navigate(Routes.movie(it.id)) })
        }
        composable(Routes.SERIES) {
            SeriesScreen(onOpen = { nav.navigate(Routes.series(it.id)) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onAddSource = { nav.navigate(Routes.connection(it)) },
                onEditSource = { nav.navigate(Routes.connection(it.type, it.id)) },
                onNoSourcesLeft = ::goWelcomeClearing,
            )
        }
        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onPlayChannel = { nav.navigate(Routes.player(ContentType.CHANNEL, it.id, it.name)) },
                onOpenMovie = { nav.navigate(Routes.movie(it.id)) },
                onOpenSeries = { nav.navigate(Routes.series(it.id)) },
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(onBack = { nav.popBackStack() }, onOpen = { type, id, title -> openItem(type, id, title, null) })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { nav.popBackStack() }, onOpen = { h -> openItem(h.type, h.contentId, h.title, h.parentId) })
        }
        composable(Routes.MOVIE_DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            MovieDetailsScreen(
                onBack = { nav.popBackStack() },
                onPlay = { movie, ext, resume ->
                    nav.navigate(
                        Routes.player(ContentType.MOVIE, movie.id, movie.name, key = serverKeyOf(movie.id), ext = ext, image = movie.posterUrl, startMs = resume),
                    )
                },
            )
        }
        composable(Routes.SERIES_DETAILS, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            SeriesDetailsScreen(
                onBack = { nav.popBackStack() },
                onPlayEpisode = { series, ep, resume ->
                    val label = buildString {
                        append(series.name)
                        append(" · T").append(ep.seasonNumber)
                        ep.episodeNumber?.let { append("E").append(it) }
                        if (ep.title.isNotBlank()) append(" · ").append(ep.title)
                    }
                    nav.navigate(
                        Routes.player(
                            ContentType.EPISODE, ep.id, label, key = serverKeyOf(ep.id), ext = ep.containerExtension,
                            image = ep.imageUrl ?: series.coverUrl, parent = series.id, startMs = resume,
                        ),
                    )
                },
            )
        }
        composable(
            Routes.PLAYER,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                optionalString("id"), optionalString("key"), optionalString("ext"), optionalString("title"),
                optionalString("image"), optionalString("parent"), optionalString("cat"), optionalString("start"),
            ),
        ) {
            ChannelPlayerScreen(onBack = { nav.popBackStack() })
        }
    }
}
