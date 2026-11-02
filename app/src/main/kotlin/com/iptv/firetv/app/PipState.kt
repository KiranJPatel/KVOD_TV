package com.iptv.firetv.app

import kotlinx.coroutines.flow.MutableStateFlow

/** Shared between the activity (PiP callbacks) and the player screen. */
object PipState {
    /** True while the player screen is on screen; only then does pressing Home enter Picture-in-picture. */
    val playerVisible = MutableStateFlow(false)
    val active = MutableStateFlow(false)
}

/** A search phrase handed over by the system (ACTION_SEARCH / voice assistants) until the Search screen consumes it. */
object SearchInbox { val pendingQuery = MutableStateFlow<String?>(null) }

/** Lets other screens ask the Library to open on a given tab (for example "My playlists" from the menu). */
object LibraryInbox {
    const val TAB_LIVE = 0
    const val TAB_MOVIES = 1
    const val TAB_SERIES = 2
    const val TAB_PLAYLISTS = 3
    const val TAB_HISTORY = 4
    val pendingTab = MutableStateFlow<Int?>(null)
}
