package com.iptv.firetv.core.player

import androidx.media3.exoplayer.analytics.AnalyticsListener
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Real measurements of the current playback taken from the player's own analytics events (decoder actually created, frames
 * actually dropped, audio underruns, measured bandwidth). Nothing here is inferred from codec names or documentation.
 */
class PlaybackDiagnostics : AnalyticsListener {
    @Volatile var videoDecoder: String? = null; private set
    @Volatile var audioDecoder: String? = null; private set
    @Volatile var bandwidthBps: Long = 0; private set
    private val dropped = AtomicLong()
    private val underruns = AtomicInteger()
    val droppedFrames: Long get() = dropped.get()
    val audioUnderruns: Int get() = underruns.get()

    fun reset() { videoDecoder = null; audioDecoder = null; bandwidthBps = 0; dropped.set(0); underruns.set(0) }

    override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { videoDecoder = decoderName }
    override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) { audioDecoder = decoderName }
    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) { dropped.addAndGet(droppedFrames.toLong()) }
    override fun onAudioUnderrun(eventTime: AnalyticsListener.EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) { underruns.incrementAndGet() }
    override fun onBandwidthEstimate(eventTime: AnalyticsListener.EventTime, totalLoadTimeMs: Int, totalBytesLoaded: Long, bitrateEstimate: Long) { bandwidthBps = bitrateEstimate }

    companion object {
        /** Name-based hint used only when the platform cannot say (API < 29); the capability report prefers MediaCodecInfo.isSoftwareOnly. */
        fun looksSoftware(decoderName: String?): Boolean {
            val n = decoderName?.lowercase() ?: return false
            return n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("c2.google.") || n.contains(".sw.") || n.contains("ffmpeg") || n.contains("libgav1") || n.contains("libvpx") || n.contains("dav1d")
        }
    }
}
