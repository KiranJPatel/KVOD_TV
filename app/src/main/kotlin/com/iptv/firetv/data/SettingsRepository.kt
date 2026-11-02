package com.iptv.firetv.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.iptv.firetv.core.model.AccentColor
import com.iptv.firetv.core.model.AppTheme
import com.iptv.firetv.core.model.BufferProfile
import com.iptv.firetv.core.model.QualityMode
import com.iptv.firetv.core.model.ThemeMode
import com.iptv.firetv.core.player.SubBackground
import com.iptv.firetv.core.player.SubColor
import com.iptv.firetv.core.player.SubEdge
import com.iptv.firetv.core.player.SubFont
import com.iptv.firetv.core.player.SubPosition
import com.iptv.firetv.core.player.SubSize
import com.iptv.firetv.core.player.SubtitleStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore("settings")

/** Non-sensitive preferences only. Credentials never go through DataStore. */
data class AppSettings(
    val theme: AppTheme = AppTheme.AURORA,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentColor = AccentColor.THEME,
    val textScale: Float = 1f,
    /** Scales the whole interface (not just text) to fit the TV; 1 = standard. */
    val uiScale: Float = 1f,
    val activeProfileId: Long = 0,
    val activeSourceId: Long = -1,
    val mergeDuplicates: Boolean = false,
    val autoplayNext: Boolean = true,
    val confirmExit: Boolean = false,
    val overlaySeconds: Int = 5,
    val dpadZapping: Boolean = true,
    val buffer: BufferProfile = BufferProfile.BALANCED,
    val quality: QualityMode = QualityMode.LOCK_1080,
    val maxRetries: Int = 3,
    val epgZone: String = "",
    val epgNameMatching: Boolean = false,
    val channelSort: Int = 0,
    val inactivityLockMin: Int = 0,
    val onboardingDone: Boolean = false,
    val lastChannelId: String = "",
    val resizeMode: Int = 0,
    val livePreview: Boolean = true,
    val metadataEnabled: Boolean = false,
    /** "" = follow system; otherwise a language code (see I18n.languages). */
    val language: String = "",
    val subtitle: SubtitleStyle = SubtitleStyle(),
    /** Top-level tabs the user can hide when they are not needed. */
    val showMoviesTab: Boolean = true,
    val showSeriesTab: Boolean = true,
    /** The whole details/preview pane on the right of Live TV (the muted video inside it is [livePreview]). */
    val showPreviewPane: Boolean = true,
    /** In-app player volume (0..1) and picture brightness (0.2..1, implemented as a dimmer over the video). */
    val playerVolume: Float = 1f,
    val playerBrightness: Float = 1f,
    /** Keep the TV awake (no screensaver / sleep) while a stream is playing. */
    val keepScreenAwake: Boolean = true
)

object SettingKeys {
    val THEME = stringPreferencesKey("theme")
    val ACCENT = stringPreferencesKey("accent")
    val TEXT_SCALE = floatPreferencesKey("text_scale")
    val UI_SCALE = floatPreferencesKey("ui_scale")
    val ACTIVE_PROFILE = longPreferencesKey("active_profile")
    val ACTIVE_SOURCE = longPreferencesKey("active_source")
    val MERGE = booleanPreferencesKey("merge_duplicates")
    val AUTOPLAY_NEXT = booleanPreferencesKey("autoplay_next")
    val CONFIRM_EXIT = booleanPreferencesKey("confirm_exit")
    val OVERLAY_SECONDS = intPreferencesKey("overlay_seconds")
    val DPAD_ZAP = booleanPreferencesKey("dpad_zap")
    val BUFFER = stringPreferencesKey("buffer")
    val QUALITY = stringPreferencesKey("quality")
    val MAX_RETRIES = intPreferencesKey("max_retries")
    val EPG_ZONE = stringPreferencesKey("epg_zone")
    val EPG_NAME_MATCH = booleanPreferencesKey("epg_name_match")
    val CHANNEL_SORT = intPreferencesKey("channel_sort")
    val INACTIVITY_LOCK = intPreferencesKey("inactivity_lock_min")
    val ONBOARDING = booleanPreferencesKey("onboarding_done")
    val LAST_CHANNEL = stringPreferencesKey("last_channel")
    val RESIZE_MODE = intPreferencesKey("resize_mode")
    val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
    val METADATA_ENABLED = booleanPreferencesKey("metadata_enabled")
    val LANGUAGE = stringPreferencesKey("language")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val SUB_COLOR = stringPreferencesKey("sub_color")
    val SUB_BG = stringPreferencesKey("sub_bg")
    val SUB_SIZE = stringPreferencesKey("sub_size")
    val SUB_FONT = stringPreferencesKey("sub_font")
    val SUB_EDGE = stringPreferencesKey("sub_edge")
    val SUB_POS = stringPreferencesKey("sub_pos")
    val SHOW_MOVIES = booleanPreferencesKey("show_movies_tab")
    val SHOW_SERIES = booleanPreferencesKey("show_series_tab")
    val SHOW_PREVIEW_PANE = booleanPreferencesKey("show_preview_pane")
    val PLAYER_VOLUME = floatPreferencesKey("player_volume")
    val PLAYER_BRIGHTNESS = floatPreferencesKey("player_brightness")
    val KEEP_AWAKE = booleanPreferencesKey("keep_screen_awake")
    /** AES-GCM ciphertext (Android Keystore) of the user's own metadata API key; never the plain key. */
    val METADATA_KEY_ENC = stringPreferencesKey("metadata_key_enc")
}

private inline fun <reified E : Enum<E>> parseEnum(s: String?, default: E): E =
    s?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    val settings: Flow<AppSettings> = ctx.settingsStore.data.map { p ->
        val d = AppSettings()
        // Older builds stored MIDNIGHT / MATERIAL_DARK / MATERIAL_GRAY / LIGHT; fromStored maps them to a theme + mode.
        val (storedTheme, legacyMode) = AppTheme.fromStored(p[SettingKeys.THEME])
        AppSettings(
            theme = storedTheme,
            themeMode = p[SettingKeys.THEME_MODE]?.let { n -> ThemeMode.entries.firstOrNull { it.name == n } } ?: legacyMode ?: d.themeMode,
            accent = parseEnum(p[SettingKeys.ACCENT], d.accent),
            textScale = p[SettingKeys.TEXT_SCALE] ?: d.textScale,
            uiScale = p[SettingKeys.UI_SCALE] ?: d.uiScale,
            activeProfileId = p[SettingKeys.ACTIVE_PROFILE] ?: d.activeProfileId,
            activeSourceId = p[SettingKeys.ACTIVE_SOURCE] ?: d.activeSourceId,
            mergeDuplicates = p[SettingKeys.MERGE] ?: d.mergeDuplicates,
            autoplayNext = p[SettingKeys.AUTOPLAY_NEXT] ?: d.autoplayNext,
            confirmExit = p[SettingKeys.CONFIRM_EXIT] ?: d.confirmExit,
            overlaySeconds = p[SettingKeys.OVERLAY_SECONDS] ?: d.overlaySeconds,
            dpadZapping = p[SettingKeys.DPAD_ZAP] ?: d.dpadZapping,
            buffer = parseEnum(p[SettingKeys.BUFFER], d.buffer),
            quality = parseEnum(p[SettingKeys.QUALITY], d.quality),
            maxRetries = (p[SettingKeys.MAX_RETRIES] ?: d.maxRetries).coerceIn(0, 5),
            epgZone = p[SettingKeys.EPG_ZONE] ?: d.epgZone,
            epgNameMatching = p[SettingKeys.EPG_NAME_MATCH] ?: d.epgNameMatching,
            channelSort = p[SettingKeys.CHANNEL_SORT] ?: d.channelSort,
            inactivityLockMin = p[SettingKeys.INACTIVITY_LOCK] ?: d.inactivityLockMin,
            onboardingDone = p[SettingKeys.ONBOARDING] ?: d.onboardingDone,
            lastChannelId = p[SettingKeys.LAST_CHANNEL] ?: d.lastChannelId,
            resizeMode = p[SettingKeys.RESIZE_MODE] ?: d.resizeMode,
            livePreview = p[SettingKeys.LIVE_PREVIEW] ?: d.livePreview,
            metadataEnabled = p[SettingKeys.METADATA_ENABLED] ?: d.metadataEnabled,
            language = p[SettingKeys.LANGUAGE] ?: d.language,
            subtitle = SubtitleStyle(
                color = parseEnum(p[SettingKeys.SUB_COLOR], d.subtitle.color),
                background = parseEnum(p[SettingKeys.SUB_BG], d.subtitle.background),
                size = parseEnum(p[SettingKeys.SUB_SIZE], d.subtitle.size),
                font = parseEnum(p[SettingKeys.SUB_FONT], d.subtitle.font),
                edge = parseEnum(p[SettingKeys.SUB_EDGE], d.subtitle.edge),
                position = parseEnum(p[SettingKeys.SUB_POS], d.subtitle.position)
            ),
            showMoviesTab = p[SettingKeys.SHOW_MOVIES] ?: d.showMoviesTab,
            showSeriesTab = p[SettingKeys.SHOW_SERIES] ?: d.showSeriesTab,
            showPreviewPane = p[SettingKeys.SHOW_PREVIEW_PANE] ?: d.showPreviewPane,
            playerVolume = (p[SettingKeys.PLAYER_VOLUME] ?: d.playerVolume).coerceIn(0f, 1f),
            playerBrightness = (p[SettingKeys.PLAYER_BRIGHTNESS] ?: d.playerBrightness).coerceIn(0.2f, 1f),
            keepScreenAwake = p[SettingKeys.KEEP_AWAKE] ?: d.keepScreenAwake
        )
    }

    suspend fun current(): AppSettings = settings.first()
    suspend fun metadataKeyCipher(): String? = ctx.settingsStore.data.first()[SettingKeys.METADATA_KEY_ENC]
    suspend fun setMetadataKeyCipher(c: String?) { ctx.settingsStore.edit { if (c == null) it.remove(SettingKeys.METADATA_KEY_ENC) else it[SettingKeys.METADATA_KEY_ENC] = c } }

    // ---- per-item external subtitle (explicitly chosen by the user); bounded to the 200 most recent items
    private val subJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    suspend fun subtitleFor(itemId: String): com.iptv.firetv.core.player.SubtitleSource? =
        ctx.settingsStore.data.first()[stringPreferencesKey("sub_$itemId")]?.let { runCatching { subJson.decodeFromString<com.iptv.firetv.core.player.SubtitleSource>(it) }.getOrNull() }
    suspend fun setSubtitle(itemId: String, sub: com.iptv.firetv.core.player.SubtitleSource?) {
        ctx.settingsStore.edit { p ->
            val key = stringPreferencesKey("sub_$itemId")
            if (sub == null) p.remove(key) else p[key] = subJson.encodeToString(com.iptv.firetv.core.player.SubtitleSource.serializer(), sub)
            val all = p.asMap().keys.filter { it.name.startsWith("sub_") }
            if (all.size > 200) @Suppress("UNCHECKED_CAST") all.filter { it != key }.take(all.size - 200).forEach { p.remove(it as Preferences.Key<Any>) }
        }
    }
    suspend fun <T> set(key: Preferences.Key<T>, value: T) { ctx.settingsStore.edit { it[key] = value } }
    /** Saves all subtitle appearance choices in one transaction. */
    suspend fun setSubtitleStyle(st: SubtitleStyle) {
        ctx.settingsStore.edit {
            it[SettingKeys.SUB_COLOR] = st.color.name; it[SettingKeys.SUB_BG] = st.background.name; it[SettingKeys.SUB_SIZE] = st.size.name
            it[SettingKeys.SUB_FONT] = st.font.name; it[SettingKeys.SUB_EDGE] = st.edge.name; it[SettingKeys.SUB_POS] = st.position.name
        }
    }
    /** Resets appearance/playback settings but keeps the active profile selection. */
    suspend fun resetAll() { ctx.settingsStore.edit { p -> val keep = p[SettingKeys.ACTIVE_PROFILE]; p.clear(); keep?.let { p[SettingKeys.ACTIVE_PROFILE] = it }; p[SettingKeys.ONBOARDING] = true } }
}
