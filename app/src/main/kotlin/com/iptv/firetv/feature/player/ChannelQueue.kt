package com.iptv.firetv.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ordered channel ids of the list the user started playback from; drives channel up/down and "channel list".
 * [shuffle] switches channel up/down (and "next channel") from list order to random picks from the same list. It belongs to the
 * current viewing session: it is switched on by the random-play buttons and off whenever playback is started from a list.
 */
@Singleton
class ChannelQueue @Inject constructor() {
    @Volatile var ids: List<String> = emptyList()
        private set
    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle
    private val recent = ArrayDeque<String>()

    fun set(list: List<String>) { ids = list; recent.clear() }
    fun setShuffle(on: Boolean) { _shuffle.value = on; if (!on) recent.clear() }

    fun neighbor(current: String, delta: Int): String? {
        if (ids.isEmpty()) return null
        val i = ids.indexOf(current); if (i < 0) return ids.firstOrNull()
        return ids[(i + delta).mod(ids.size)]
    }

    /**
     * A random channel that is not [current] and, while the list is long enough, not one of the last few picks, so quick
     * successive presses do not bounce between the same two channels. [random] is injectable for tests.
     */
    fun randomPick(current: String?, random: kotlin.random.Random = kotlin.random.Random.Default): String? {
        if (ids.isEmpty()) return null
        if (ids.size == 1) return ids.first()
        val avoid = HashSet<String>()
        current?.let { avoid += it }
        val window = minOf(RECENT_WINDOW, ids.size / 2)
        recent.takeLast(window).forEach { avoid += it }
        val pool = ids.filter { it !in avoid }.ifEmpty { ids.filter { it != current } }
        val pick = pool.random(random)
        recent.addLast(pick); while (recent.size > RECENT_WINDOW) recent.removeFirst()
        return pick
    }

    /** Next channel for up/down/CH+/CH-: random while [shuffle] is on, otherwise the neighbour in list order. */
    fun next(current: String, delta: Int): String? = if (_shuffle.value) randomPick(current) else neighbor(current, delta)

    private companion object { const val RECENT_WINDOW = 8 }
}
