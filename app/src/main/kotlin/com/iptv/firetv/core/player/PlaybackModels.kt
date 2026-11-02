package com.iptv.firetv.core.player

import androidx.media3.common.PlaybackException

enum class PlaybackStatus { IDLE, LOADING, BUFFERING, READY, ENDED, ERROR }

/** Recoverable errors are retried automatically (bounded); the rest show actions: Retry, Next channel, Change source, Back. */
data class PlaybackIssue(val code: String, val message: String, val recoverable: Boolean, val diagnostic: String)

/** What is actually on screen: decoded size, bitrate (kbit/s) and frame rate when the stream reports them. */
data class VideoInfo(val width: Int, val height: Int, val bitrateKbps: Int?, val frameRate: Float?, val mime: String?) {
    val label get() = "${height}p"
}

/** Pure helper: the height the quality policy will end up with, given the variants a stream offers. Null if none fits the cap. */
/** Counts rebuffering events (READY back to BUFFERING) inside a sliding window; true once [threshold] stalls happen within [windowMs]. */
class StallDetector(private val windowMs: Long = 90_000, private val threshold: Int = 3) {
    private val stalls = ArrayDeque<Long>()
    fun onStall(nowMs: Long): Boolean {
        stalls.addLast(nowMs)
        while (stalls.isNotEmpty() && nowMs - stalls.first() > windowMs) stalls.removeFirst()
        return stalls.size >= threshold
    }
    fun reset() = stalls.clear()
}

object QualityPolicy {
    fun pick(offered: List<Int>, mode: com.iptv.firetv.core.model.QualityMode): Int? =
        offered.filter { it <= mode.maxHeight }.maxOrNull()
}

data class TrackOption(val id: String, val label: String, val selected: Boolean)

data class PlayRequest(
    val itemId: String, val title: String, val url: String, val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = false, val startPositionMs: Long = 0,
    /** True for the muted Live TV preview; lets screens stop only their own preview, never a real playback. */
    val preview: Boolean = false,
    /** External subtitle files the user explicitly picked for this item (content:// URIs only). */
    val subtitles: List<SubtitleSource> = emptyList(),
    /** Widevine licence server from the user's own source, if the stream is protected. */
    val drmLicenseUrl: String? = null,
    /** Duplicate entries of the same channel, tried in order when this stream does not start or fails. */
    val alternates: List<StreamAlt> = emptyList()
)

@kotlinx.serialization.Serializable
data class SubtitleSource(val uri: String, val label: String, val mime: String)

object SubtitleFiles {
    /** Formats Media3 can render as side-loaded text tracks. Unknown extensions are refused instead of guessed. */
    fun mimeFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "srt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
        "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
        "ass", "ssa" -> androidx.media3.common.MimeTypes.TEXT_SSA
        "ttml", "dfxp", "xml" -> androidx.media3.common.MimeTypes.APPLICATION_TTML
        "sup" -> androidx.media3.common.MimeTypes.APPLICATION_PGS
        else -> null
    }
    fun isSafeUri(uri: String) = uri.startsWith("content://")
}

data class PlayerUiState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val isLive: Boolean = false,
    val canSeek: Boolean = false,
    val issue: PlaybackIssue? = null,
    val retryAttempt: Int = 0,
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    val video: VideoInfo? = null,
    /** Distinct decodable video heights the stream offers, highest first (diagnostics and the quality panel). */
    val offeredHeights: List<Int> = emptyList(),
    val quality: com.iptv.firetv.core.model.QualityMode = com.iptv.firetv.core.model.QualityMode.LOCK_1080,
    /** Locked 1080p keeps stalling: hint the viewer to try Auto. Cleared when the quality mode changes. */
    val suggestAuto: Boolean = false,
    /** Which copy of the channel is playing (0 = the one in the list) and how many exist. */
    val streamIndex: Int = 0,
    val streamCount: Int = 1,
    /** Milliseconds from tune-in to the first picture, -1 until it appears. */
    val firstFrameMs: Long = -1
)

/** Bounded exponential backoff: 1s, 2s, 4s ... capped at 8s; stops after [maxRetries]. */
class RetryPolicy(val maxRetries: Int = 3, private val baseMs: Long = 1000, private val capMs: Long = 8000) {
    fun delayFor(attempt: Int): Long? = if (attempt >= maxRetries) null else minOf(capMs, baseMs shl attempt)
}

/** Result of feeding a playback error to the [RetryController]. */
sealed interface RetryDecision {
    data class Retry(val delayMs: Long, val attempt: Int) : RetryDecision
    data object GiveUp : RetryDecision
}

/**
 * Pure retry state machine (unit-testable without a player): recoverable errors are retried with bounded exponential
 * backoff, non-recoverable errors and exhausted budgets give up; a successful start resets the budget.
 */
class RetryController(private val policy: RetryPolicy = RetryPolicy()) {
    var attempts = 0
        private set
    fun onError(issue: PlaybackIssue): RetryDecision {
        if (!issue.recoverable) return RetryDecision.GiveUp
        val wait = policy.delayFor(attempts) ?: return RetryDecision.GiveUp
        attempts++
        return RetryDecision.Retry(wait, attempts)
    }
    fun reset() { attempts = 0 }
}

object PlaybackErrorMapper {
    /** [httpStatus] is the HTTP response code when the failure was an invalid HTTP response, else null. */
    fun map(errorCode: Int, httpStatus: Int?): PlaybackIssue = when {
        httpStatus == 401 || httpStatus == 403 -> PlaybackIssue("P_AUTH", "The provider rejected this stream ($httpStatus). Check your account or credentials.", false, "HTTP $httpStatus")
        httpStatus == 404 || httpStatus == 410 -> PlaybackIssue("P_GONE", "This stream was not found. The link may have expired.", false, "HTTP $httpStatus")
        httpStatus != null && httpStatus >= 500 -> PlaybackIssue("P_SERVER", "The provider server had a problem ($httpStatus).", true, "HTTP $httpStatus")
        httpStatus != null -> PlaybackIssue("P_HTTP", "The server refused the stream ($httpStatus).", false, "HTTP $httpStatus")
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            errorCode == PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackIssue("P_NET", "Network problem while loading the stream.", true, "code $errorCode")
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackIssue("P_HTTP", "The server refused the stream.", false, "code $errorCode")
        errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED || errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackIssue("P_IO", "The stream or file could not be opened.", errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED, "code $errorCode")
        errorCode in 3000..3999 -> PlaybackIssue("P_FORMAT", "This stream format is not supported on this device.", false, "code $errorCode")
        errorCode in 4000..4999 -> PlaybackIssue("P_DECODE", "This device cannot decode the stream (codec or resolution).", false, "code $errorCode")
        errorCode in 6000..6999 -> PlaybackIssue("P_DRM", "Protected content could not be played (DRM).", false, "code $errorCode")
        else -> PlaybackIssue("P_UNKNOWN", "Playback failed.", true, "code $errorCode")
    }
}
