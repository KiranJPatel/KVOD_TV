package com.iptv.firetv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.player.PlayerRepository
import com.iptv.firetv.data.AppSettings
import com.iptv.firetv.data.EpgRepository
import com.iptv.firetv.data.ProfileRepository
import com.iptv.firetv.data.UserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.iptv.firetv.core.player.PlaybackStatus
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Holds a URL shared into the app (ACTION_SEND) until the Add Source screen consumes it. */
object ShareInbox { val pendingUrl = MutableStateFlow<String?>(null) }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
    private val profiles: ProfileRepository,
    private val sources: SourceRepository,
    private val player: PlayerRepository,
    user: UserDataRepository,
    epg: EpgRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /**
     * True while a stream is playing (or starting / rebuffering) and the user wants the TV kept awake. The activity turns this into
     * FLAG_KEEP_SCREEN_ON, which stops the Fire TV screensaver and sleep timer from interrupting a channel that is on screen.
     */
    val keepAwake: StateFlow<Boolean> = combine(player.state, settings) { st, cfg ->
        (cfg?.keepScreenAwake != false) && (st.isPlaying || st.status == PlaybackStatus.LOADING || st.status == PlaybackStatus.BUFFERING)
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** True when at least one favourite channel has a programme on air (shows the red dot on the Live TV rail icon). */
    val liveDot: StateFlow<Boolean> = user.favoriteChannels().mapLatest { list -> epg.nowNext(list.take(100)).values.any { it.now != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    /** First letter of the active profile's name for the top-bar avatar. */
    val profileInitial: StateFlow<String> = profiles.activeProfile.mapLatest { it?.name?.trim()?.firstOrNull()?.uppercase() ?: "?" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "?")
    private val _start = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _start
    private val _lock = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockEvents: SharedFlow<Unit> = _lock
    @Volatile private var lastTouch = System.currentTimeMillis()

    init {
        viewModelScope.launch {
            profiles.ensureDefault()
            val s = settingsRepo.settings.first()
            val count = profiles.profiles.first().size
            val hasSources = sources.sources.first().isNotEmpty()
            _start.value = when {
                count > 1 -> Routes.PROFILE_CHOOSE
                !hasSources && !s.onboardingDone -> Routes.WELCOME
                else -> Routes.HOME
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val s = settingsRepo.settings.first()
                val hasPin = profiles.activeProfile.first()?.pinHash != null
                val idle = System.currentTimeMillis() - lastTouch
                // Watching counts as activity, so the lock never interrupts playback.
                if (s.inactivityLockMin > 0 && hasPin && !player.state.value.isPlaying && idle > s.inactivityLockMin * 60_000L) { _lock.tryEmit(Unit); lastTouch = System.currentTimeMillis() }
            }
        }
    }

    fun touch() { lastTouch = System.currentTimeMillis() }
    fun releasePlayer() = player.release()
}
