package com.iptv.firetv.core.player

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import android.os.PowerManager
import android.view.Display
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

@Serializable data class VideoDecoderInfo(
    val mime: String, val decoder: String, val hardware: Boolean, val secure: Boolean,
    val profiles: List<String>, val fullHd: Boolean, val uhd4k: Boolean, val uhd8k: Boolean, val fullHd60: Boolean, val uhd4k60: Boolean
)
@Serializable data class AudioDecoderInfo(val mime: String, val decoder: String, val hardware: Boolean, val maxChannels: Int)
@Serializable data class AudioOutputInfo(val type: String, val product: String, val encodings: List<String>, val channelCounts: List<Int>, val sampleRates: List<Int>)
@Serializable data class DisplayInfo(val hdrTypes: List<String>, val modes: List<String>)
@Serializable data class DeviceCapabilityReport(
    val generatedAt: String, val device: String, val androidVersion: String, val apiLevel: Int, val abis: List<String>, val totalRamMb: Long, val thermalStatus: String,
    val video: List<VideoDecoderInfo>, val audioDecoders: List<AudioDecoderInfo>, val audioOutputs: List<AudioOutputInfo>,
    /** Result of AudioTrack.isDirectPlaybackSupported per encoding (API 29+). Empty map = the platform cannot answer; see notes. */
    val directPlayback: Map<String, String>, val display: DisplayInfo, val widevineSecurityLevel: String,
    val streaming: Map<String, String>, val notes: List<String>
) {
    fun toJson(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(this)

    fun saveTo(ctx: Context): File = File(ctx.filesDir, "reports").apply { mkdirs() }.resolve("DEVICE_CAPABILITIES.json").also { it.writeText(toJson()) }

    fun toText(): String = buildString {
        appendLine("DEVICE MULTIMEDIA CAPABILITY REPORT"); appendLine(generatedAt)
        appendLine("Device: $device"); appendLine("Android $androidVersion (API $apiLevel), ${abis.joinToString()}, RAM $totalRamMb MB, thermal: $thermalStatus")
        appendLine(); appendLine("VIDEO DECODERS (from MediaCodecList)")
        val byMime = video.groupBy { it.mime }
        if (byMime.isEmpty()) appendLine("  none reported")
        byMime.forEach { (mime, list) ->
            val best = list.firstOrNull { it.hardware } ?: list.first()
            appendLine("  ${mime.removePrefix("video/")}: ${if (list.any { it.hardware }) "hardware" else "software only"}; 1080p ${yn(best.fullHd)}, 1080p60 ${yn(best.fullHd60)}, 4K ${yn(best.uhd4k)}, 4K60 ${yn(best.uhd4k60)}, 8K ${yn(best.uhd8k)}")
            if (best.profiles.isNotEmpty()) appendLine("    profiles: ${best.profiles.joinToString()}")
            appendLine("    decoder: ${best.decoder}${if (list.any { it.secure }) " (secure variant present)" else ""}")
        }
        appendLine(); appendLine("DISPLAY"); appendLine("  HDR: ${display.hdrTypes.ifEmpty { listOf("none reported") }.joinToString()}")
        appendLine("  Modes: ${display.modes.take(8).joinToString()}")
        appendLine(); appendLine("AUDIO DECODERS"); audioDecoders.groupBy { it.mime }.forEach { (m, l) -> appendLine("  ${m.removePrefix("audio/")}: ${if (l.any { it.hardware }) "hardware" else "software"} (max ${l.maxOf { it.maxChannels }} ch)") }
        appendLine(); appendLine("AUDIO OUTPUT ROUTES")
        if (audioOutputs.isEmpty()) appendLine("  none reported")
        audioOutputs.forEach { appendLine("  ${it.type} ${it.product}: encodings ${it.encodings.ifEmpty { listOf("unspecified") }.joinToString()}; channels ${it.channelCounts.joinToString()}; rates ${it.sampleRates.joinToString()}") }
        appendLine(); appendLine("PASSTHROUGH (AudioTrack.isDirectPlaybackSupported)")
        if (directPlayback.isEmpty()) appendLine("  not answerable on this Android version") else directPlayback.forEach { (k, v) -> appendLine("  $k: $v") }
        appendLine(); appendLine("DRM"); appendLine("  Widevine: $widevineSecurityLevel")
        appendLine(); appendLine("STREAMING (what the build contains; not proof it plays)"); streaming.forEach { (k, v) -> appendLine("  $k: $v") }
        appendLine(); appendLine("NOTES"); notes.forEach { appendLine("  - $it") }
    }
    private fun yn(b: Boolean) = if (b) "yes" else "no"
}

/**
 * Reads what this device actually reports: MediaCodecList for decoders (hardware/software, size and frame-rate limits, profiles),
 * AudioManager for output routes, AudioTrack for passthrough, Display for HDR and MediaDrm for the Widevine level.
 * The report says what the platform claims; whether a given stream plays is only proven by playing it.
 */
class DeviceCapabilityDetector(private val ctx: Context) {
    fun collect(): DeviceCapabilityReport {
        val infos = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder } }.getOrDefault(emptyList())
        val video = ArrayList<VideoDecoderInfo>(); val audio = ArrayList<AudioDecoderInfo>()
        for (info in infos) for (type in info.supportedTypes) {
            val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: continue
            val hw = isHardware(info)
            if (type.startsWith("video/")) {
                val v = caps.videoCapabilities ?: continue
                fun size(w: Int, h: Int) = runCatching { v.isSizeSupported(w, h) }.getOrDefault(false)
                fun rate(w: Int, h: Int, fps: Double) = runCatching { v.areSizeAndRateSupported(w, h, fps) }.getOrDefault(false)
                video.add(VideoDecoderInfo(type, info.name, hw, caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_SecurePlayback),
                    caps.profileLevels.mapNotNull { profileName(type, it.profile) }.distinct(),
                    size(1920, 1080), size(3840, 2160), size(7680, 4320), rate(1920, 1080, 60.0), rate(3840, 2160, 60.0)))
            } else if (type.startsWith("audio/")) {
                audio.add(AudioDecoderInfo(type, info.name, hw, runCatching { caps.audioCapabilities.maxInputChannelCount }.getOrDefault(0)))
            }
        }
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val mem = ActivityManager.MemoryInfo().also { (ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(it) }
        val direct = directPlayback()
        return DeviceCapabilityReport(
            generatedAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(java.util.Date()),
            device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})", androidVersion = Build.VERSION.RELEASE, apiLevel = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(), totalRamMb = mem.totalMem / (1024 * 1024),
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) thermalName(pm?.currentThermalStatus) else "not available before Android 10",
            video = video.sortedWith(compareBy({ it.mime }, { !it.hardware })), audioDecoders = audio.sortedBy { it.mime },
            audioOutputs = audioOutputs(), directPlayback = direct, display = display(), widevineSecurityLevel = widevine(),
            streaming = streaming(),
            notes = buildList {
                add("Hardware flag uses MediaCodecInfo.isHardwareAccelerated on Android 10+, otherwise the decoder name (OMX.google./c2.android. = software).")
                add("Size and frame-rate flags come from MediaCodec's own limits for that decoder; they are upper bounds, not a playback test.")
                if (direct.isEmpty()) add("Passthrough support cannot be queried on this Android version; ExoPlayer detects HDMI audio capabilities itself at playback time.")
                add("Dolby Atmos, DTS:X and TrueHD output also depend on the connected receiver or soundbar and the HDMI link; the TV or AVR must report them.")
            }
        )
    }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated && !info.isSoftwareOnly else !PlaybackDiagnostics.looksSoftware(info.name)

    private fun profileName(mime: String, p: Int): String? {
        return when (mime) {
            "video/avc" -> when (p) { MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"; MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"; MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"; MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High10"; else -> null }
            "video/hevc" -> when (p) { MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"; MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main10"; MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main10 HDR10"; MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main10 HDR10+"; else -> null }
            "video/x-vnd.on2.vp9" -> when (p) { MediaCodecInfo.CodecProfileLevel.VP9Profile0 -> "Profile 0"; MediaCodecInfo.CodecProfileLevel.VP9Profile2 -> "Profile 2"; MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR -> "Profile 2 HDR"; MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus -> "Profile 2 HDR10+"; else -> null }
            "video/av01" -> when (p) { MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8 -> "Main 8-bit"; MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 -> "Main 10-bit"; MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 -> "Main 10 HDR10"; MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus -> "Main 10 HDR10+"; else -> null }
            else -> null
        }
    }

    private fun audioOutputs(): List<AudioOutputInfo> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
            AudioOutputInfo(deviceType(d.type), d.productName?.toString().orEmpty(), d.encodings.map { encodingName(it) }, d.channelCounts.toList(), d.sampleRates.toList())
        }
    }

    private fun directPlayback(): Map<String, String> {
        if (Build.VERSION.SDK_INT < 29) return emptyMap()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build()
        fun check(enc: Int, mask: Int, rate: Int = 48000) = runCatching {
            AudioTrack.isDirectPlaybackSupported(AudioFormat.Builder().setEncoding(enc).setSampleRate(rate).setChannelMask(mask).build(), attrs)
        }.getOrDefault(false)
        val out = LinkedHashMap<String, String>()
        fun put(name: String, ok: Boolean) { out[name] = if (ok) "supported by the current output" else "not reported by the current output" }
        put("Dolby Digital (AC-3) 5.1", check(AudioFormat.ENCODING_AC3, AudioFormat.CHANNEL_OUT_5POINT1))
        put("Dolby Digital Plus (E-AC-3) 5.1", check(AudioFormat.ENCODING_E_AC3, AudioFormat.CHANNEL_OUT_5POINT1))
        if (Build.VERSION.SDK_INT >= 28) put("Dolby Digital Plus JOC (Atmos) 7.1", check(AudioFormat.ENCODING_E_AC3_JOC, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND))
        put("DTS 5.1", check(AudioFormat.ENCODING_DTS, AudioFormat.CHANNEL_OUT_5POINT1))
        put("DTS-HD 7.1", check(AudioFormat.ENCODING_DTS_HD, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 192000))
        put("Dolby TrueHD 7.1", check(AudioFormat.ENCODING_DOLBY_TRUEHD, AudioFormat.CHANNEL_OUT_7POINT1_SURROUND, 192000))
        return out
    }

    @Suppress("DEPRECATION")
    private fun display(): DisplayInfo {
        val d: Display? = (ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        val hdr = runCatching { d?.hdrCapabilities?.supportedHdrTypes?.map { hdrName(it) } }.getOrNull().orEmpty()
        val modes = runCatching { d?.supportedModes?.map { "${it.physicalWidth}x${it.physicalHeight}@${"%.0f".format(Locale.US, it.refreshRate)}" }?.distinct() }.getOrNull().orEmpty()
        return DisplayInfo(hdr, modes)
    }

    private fun widevine(): String = runCatching {
        val drm = MediaDrm(androidx.media3.common.C.WIDEVINE_UUID)
        try { "available, security level ${drm.getPropertyString("securityLevel")}" } finally { if (Build.VERSION.SDK_INT >= 28) drm.close() else @Suppress("DEPRECATION") drm.release() }
    }.getOrDefault("not available")

    /** Which protocols are compiled into the app. This says nothing about whether a particular stream plays. */
    private fun streaming() = linkedMapOf(
        "HLS (incl. fMP4/CMAF, AES-128, LL-HLS)" to "Media3 HLS module in build",
        "MPEG-DASH" to "Media3 DASH module in build",
        "MPEG-TS over HTTP/HTTPS" to "Media3 progressive source + TS extractor",
        "RTSP" to "Media3 RTSP module in build",
        "UDP multicast MPEG-TS (udp://)" to "Media3 UdpDataSource via DefaultDataSource; network must allow multicast",
        "RTMP / SRT / RIST / RTP" to "not included in this build",
        "Widevine DRM" to "Media3 DRM; needs a licence server from the user's own provider"
    )

    private fun thermalName(s: Int?) = when (s) { 0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"; 4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"; else -> "unknown" }
    private fun hdrName(t: Int) = when (t) { Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"; Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"; Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"; Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"; else -> "type $t" }
    private fun deviceType(t: Int) = when (t) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "built-in speaker"; AudioDeviceInfo.TYPE_HDMI -> "HDMI"; AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired headphones"; AudioDeviceInfo.TYPE_WIRED_HEADSET -> "wired headset"; AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"; AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"; AudioDeviceInfo.TYPE_LINE_ANALOG -> "line out"; AudioDeviceInfo.TYPE_LINE_DIGITAL -> "digital out"
        else -> "type $t"
    }
    private fun encodingName(e: Int) = when (e) {
        AudioFormat.ENCODING_PCM_16BIT -> "PCM 16"; AudioFormat.ENCODING_PCM_FLOAT -> "PCM float"; AudioFormat.ENCODING_AC3 -> "AC-3"; AudioFormat.ENCODING_E_AC3 -> "E-AC-3"
        AudioFormat.ENCODING_DTS -> "DTS"; AudioFormat.ENCODING_DTS_HD -> "DTS-HD"; AudioFormat.ENCODING_DOLBY_TRUEHD -> "TrueHD"; AudioFormat.ENCODING_MP3 -> "MP3"
        AudioFormat.ENCODING_AAC_LC -> "AAC-LC"; AudioFormat.ENCODING_E_AC3_JOC -> "E-AC-3 JOC"; AudioFormat.ENCODING_AC4 -> "AC-4"; else -> "encoding $e"
    }
}
