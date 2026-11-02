package com.iptv.firetv.feature.sources

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.app.ShareInbox
import com.iptv.firetv.core.common.AppException
import com.iptv.firetv.core.common.ErrorKind
import com.iptv.firetv.core.model.SourceType
import com.iptv.firetv.core.network.Downloader
import com.iptv.firetv.core.network.UrlCheck
import com.iptv.firetv.core.network.UrlValidator
import com.iptv.firetv.core.network.XtreamAccount
import com.iptv.firetv.core.network.XtreamClient
import com.iptv.firetv.core.network.XtreamConfig
import com.iptv.firetv.core.security.SourceSecrets
import com.iptv.firetv.data.ImportPreview
import com.iptv.firetv.data.ImportRepository
import com.iptv.firetv.data.SourceRepository
import com.iptv.firetv.data.StagedFile
import com.iptv.firetv.data.XmltvPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class Stage { FORM, WORKING, PREVIEW, IMPORTING, DONE }

data class AddState(
    val type: SourceType = SourceType.M3U_URL, val editId: Long = -1,
    val name: String = "", val url: String = "", val username: String = "", val password: String = "", val userAgent: String = "",
    val refreshHours: Int = 24, val liveExt: String = "m3u8", val epgOffsetMin: Int = 0, val consent: Boolean = false, val showSecrets: Boolean = false,
    val stage: Stage = Stage.FORM, val progress: String = "", val preview: ImportPreview? = null, val xmltv: XmltvPreview? = null,
    val account: XtreamAccount? = null, val error: String? = null, val result: String? = null, val epgUrls: List<String> = emptyList(), val pickedName: String? = null
) {
    val isUrlType get() = type == SourceType.M3U_URL || type == SourceType.XMLTV_URL
    val isFileType get() = type == SourceType.M3U_FILE || type == SourceType.XMLTV_FILE
    val httpWarning get() = (isUrlType || type == SourceType.XTREAM) && url.trim().startsWith("http://", true)
}

@HiltViewModel
class AddSourceViewModel @Inject constructor(
    saved: SavedStateHandle, private val sources: SourceRepository, private val imports: ImportRepository,
    private val downloader: Downloader, private val xtream: XtreamClient
) : ViewModel() {
    private val _state = MutableStateFlow(AddState(
        type = runCatching { SourceType.valueOf(saved.get<String>("type").orEmpty()) }.getOrDefault(SourceType.M3U_URL),
        editId = saved.get<Long>("editId") ?: -1
    ))
    val state: StateFlow<AddState> = _state
    private var job: Job? = null
    private var staged: StagedFile? = null

    init {
        ShareInbox.pendingUrl.value?.let { u -> _state.update { it.copy(url = u, name = "Shared playlist") }; ShareInbox.pendingUrl.value = null }
        if (_state.value.editId > 0) viewModelScope.launch {
            val s = sources.get(_state.value.editId) ?: return@launch
            val sec = sources.secretsOf(s.id)
            _state.update { it.copy(name = s.name, url = sec?.url.orEmpty(), username = sec?.username.orEmpty(), userAgent = sec?.userAgent.orEmpty(), refreshHours = s.refreshHours, liveExt = sec?.liveExt ?: "m3u8", epgOffsetMin = sec?.epgOffsetMin ?: 0, consent = true) }
        }
    }

    fun edit(block: (AddState) -> AddState) = _state.update(block)
    fun backToForm() { job?.cancel(); cleanup(); _state.update { it.copy(stage = Stage.FORM, error = null, preview = null, xmltv = null, account = null) } }
    fun cancel() { job?.cancel(); cleanup() }
    /** Deletes the source being edited (and optionally the favourites and progress that point at it), then calls [onDone]. */
    fun remove(purge: Boolean, onDone: () -> Unit) {
        val id = _state.value.editId
        if (id <= 0) return
        viewModelScope.launch { sources.remove(id, purge); onDone() }
    }
    private fun cleanup() { staged?.file?.delete(); staged = null }
    private fun fail(t: Throwable) { _state.update { it.copy(stage = Stage.FORM, error = (t as? AppException)?.userMessage ?: "Something went wrong (${t.javaClass.simpleName}).") } }

    /** Step 1 for URL sources: validate, download (bounded, cancellable) and preview. */
    fun testUrl() {
        val s = _state.value
        val check = UrlValidator.validate(s.url)
        if (check is UrlCheck.Invalid) { _state.update { it.copy(error = check.reason) }; return }
        job?.cancel(); cleanup()
        _state.update { it.copy(stage = Stage.WORKING, progress = "Connecting\u2026", error = null) }
        job = viewModelScope.launch {
            try {
                val file = imports.newCacheFile("stage")
                val size = downloader.download((check as UrlCheck.Ok).url.toString(), file, userAgent = s.userAgent.ifBlank { null }) { b -> _state.update { it.copy(progress = "Downloaded ${b / 1024} KB") } }
                staged = StagedFile(file, check.url.host, size)
                preview(staged!!, check.url.toString())
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { fail(t) }
        }
    }

    fun pickFile(uri: Uri) {
        job?.cancel(); cleanup()
        _state.update { it.copy(stage = Stage.WORKING, progress = "Reading file\u2026", error = null) }
        job = viewModelScope.launch {
            try {
                val f = imports.stageUri(uri) { b -> _state.update { it.copy(progress = "Read ${b / 1024} KB") } }
                staged = f
                val ok = if (_state.value.type == SourceType.M3U_FILE) com.iptv.firetv.core.network.UrlValidator.looksLikePlaylistFile(f.displayName) else com.iptv.firetv.core.network.UrlValidator.looksLikeEpgFile(f.displayName)
                _state.update { it.copy(pickedName = f.displayName, name = it.name.ifBlank { f.displayName.substringBeforeLast('.') }) }
                // Unknown extensions are allowed (content decides); the parser reports clearly if the file is not valid.
                if (!ok) com.iptv.firetv.core.common.DiagnosticsLog.add("import", "Unusual file extension, attempting to parse anyway")
                preview(f, null)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { fail(t) }
        }
    }

    private suspend fun preview(f: StagedFile, base: String?) {
        _state.update { it.copy(progress = "Analysing\u2026") }
        val t = _state.value.type
        if (t == SourceType.XMLTV_URL || t == SourceType.XMLTV_FILE) {
            val p = imports.previewXmltv(f); _state.update { it.copy(stage = Stage.PREVIEW, xmltv = p) }
        } else {
            val p = imports.previewM3u(f, base); _state.update { it.copy(stage = Stage.PREVIEW, preview = p, epgUrls = p.stats.epgUrls.toList()) }
        }
    }

    fun testXtream() {
        val s = _state.value
        val base = xtream.normalizeBase(s.url)
        if (base == null) { _state.update { it.copy(error = "Enter a valid server address, for example https://example.com:8080") }; return }
        if (s.username.isBlank() || (s.password.isBlank() && s.editId < 0)) { _state.update { it.copy(error = "Enter the username and password.") }; return }
        job?.cancel()
        _state.update { it.copy(stage = Stage.WORKING, progress = "Contacting server\u2026", error = null) }
        job = viewModelScope.launch {
            try {
                val pw = s.password.ifBlank { sources.secretsOf(s.editId)?.password.orEmpty() }
                val acc = xtream.account(XtreamConfig(base, s.username.trim(), pw, s.liveExt, s.userAgent.ifBlank { null }))
                _state.update { it.copy(stage = Stage.PREVIEW, account = acc) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { fail(t) }
        }
    }

    /** Final step: persist the source (secrets encrypted) and run the import. */
    fun commit() {
        val s = _state.value
        if (!s.consent) { _state.update { it.copy(error = "Please confirm that you are authorised to use this source.") }; return }
        _state.update { it.copy(stage = Stage.IMPORTING, progress = "Saving\u2026", error = null) }
        job = viewModelScope.launch {
            try {
                val old = if (s.editId > 0) sources.secretsOf(s.editId) else null
                val base = if (s.type == SourceType.XTREAM) xtream.normalizeBase(s.url) else UrlValidator.validate(s.url).let { (it as? UrlCheck.Ok)?.url?.toString() }
                val sec = SourceSecrets(url = base ?: s.url.ifBlank { null }, username = s.username.ifBlank { null }, password = s.password.ifBlank { old?.password }, userAgent = s.userAgent.ifBlank { null }, liveExt = s.liveExt, epgOffsetMin = s.epgOffsetMin.takeIf { it != 0 })
                val id = if (s.editId > 0) { sources.updateSecrets(s.editId, sec); sources.rename(s.editId, s.name); sources.setRefreshHours(s.editId, s.refreshHours); s.editId }
                else sources.create(s.name.ifBlank { s.type.label }, s.type, sec, s.refreshHours, if (s.isFileType) staged?.file else null)
                if (s.isFileType && s.editId > 0) staged?.file?.let { sources.replaceLocalFile(s.editId, it) }
                val res = if (s.type == SourceType.XTREAM) sources.refresh(id).also { r -> _state.update { it.copy(progress = "Done") } }
                else sources.finishStaged(id, s.type, staged!!.file, base) { p -> _state.update { it.copy(progress = p) } }
                res.fold(
                    { sum -> _state.update { it.copy(stage = Stage.DONE, result = sum?.let { x -> "${x.channels} channels, ${x.movies} movies, ${x.series} series imported" + (if (x.duplicates > 0) ", ${x.duplicates} duplicates skipped" else "") } ?: "Source saved and imported.") }; cleanup() },
                    { t -> fail(t) }
                )
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (t: Throwable) { fail(t) }
        }
    }

    fun addEpgSource(url: String) { viewModelScope.launch {
        val id = sources.create("Guide from playlist", SourceType.XMLTV_URL, SourceSecrets(url = url), 12)
        sources.refresh(id)
        _state.update { it.copy(epgUrls = it.epgUrls - url) }
    } }

    override fun onCleared() { cleanup(); super.onCleared() }
}
