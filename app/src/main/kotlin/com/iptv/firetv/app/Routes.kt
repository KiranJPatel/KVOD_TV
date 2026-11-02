package com.iptv.firetv.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

object Routes {
    const val WELCOME = "welcome"
    const val PROFILE_CHOOSE = "profiles/choose"
    const val PROFILE_MANAGE = "profiles/manage"
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val EPG = "epg"
    const val FAVORITES = "favorites"
    const val SOURCES = "sources"
    const val LOCAL = "local"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val ADD_CHOOSER = "sources/new"
    const val ADD_SOURCE = "sources/add/{type}/{editId}"
    const val PLAYER = "player/{kind}/{id}"
    const val MOVIE_DETAIL = "movie/{id}"
    const val SERIES_DETAIL = "series/{id}"

    fun addSource(type: String, editId: Long = -1) = "sources/add/$type/$editId"
    fun player(kind: String, id: String) = "player/$kind/$id"
    fun movie(id: String) = "movie/$id"
    fun series(id: String) = "series/$id"
}

data class RailItem(val route: String, val label: String, val icon: ImageVector)

val railItems = listOf(
    RailItem(Routes.HOME, "Home", Icons.Filled.Home),
    RailItem(Routes.SEARCH, "Search", Icons.Filled.Search),
    RailItem(Routes.LIVE, "Live TV", Icons.Filled.LiveTv),
    RailItem(Routes.MOVIES, "Movies", Icons.Filled.Movie),
    RailItem(Routes.SERIES, "Series", Icons.Filled.VideoLibrary),
    RailItem(Routes.EPG, "EPG Guide", Icons.Filled.CalendarMonth),
    RailItem(Routes.FAVORITES, "Favorites", Icons.Filled.Star),
    RailItem(Routes.SOURCES, "Playlists / Sources", Icons.Filled.PlaylistAdd),
    RailItem(Routes.LOCAL, "Local media", Icons.Filled.Folder),
    RailItem(Routes.PROFILE_CHOOSE, "Profiles", Icons.Filled.Person),
    RailItem(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

val topLevelRoutes = setOf(Routes.HOME, Routes.SEARCH, Routes.LIVE, Routes.MOVIES, Routes.SERIES, Routes.EPG, Routes.FAVORITES, Routes.SOURCES, Routes.LOCAL, Routes.PROFILE_MANAGE, Routes.SETTINGS)
