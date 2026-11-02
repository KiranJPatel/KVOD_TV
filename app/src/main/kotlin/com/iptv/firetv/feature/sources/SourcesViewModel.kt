package com.iptv.firetv.feature.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.firetv.core.database.ImportJobEntity
import com.iptv.firetv.core.database.SourceEntity
import com.iptv.firetv.data.AppSettings
import com.iptv.firetv.data.SettingKeys
import com.iptv.firetv.data.SettingsRepository
import com.iptv.firetv.data.SourceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import javax.inject.Inject

@HiltViewModel
class SourcesViewModel @Inject constructor(private val repo: SourceRepository, private val settings: SettingsRepository) : ViewModel() {
    val sources: StateFlow<List<SourceEntity>> = repo.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val busy = repo.busy
    val appSettings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<String?>(null)
    val history = MutableStateFlow<List<ImportJobEntity>>(emptyList())
    val categories = MutableStateFlow<List<com.iptv.firetv.data.CategoryType>>(emptyList())
    fun loadCategories(id: Long) { viewModelScope.launch { categories.value = repo.categoryTypes(id) } }
    fun cycleCategory(id: Long, c: com.iptv.firetv.data.CategoryType) { viewModelScope.launch {
        val next = when (c.override) { null -> "LIVE"; "LIVE" -> "MOVIE"; "MOVIE" -> "SERIES"; else -> null }
        repo.setCategoryType(id, c.norm, c.name, next); categories.value = repo.categoryTypes(id)
    } }

    fun refresh(id: Long) { viewModelScope.launch {
        repo.refresh(id).onSuccess { message.value = "Refresh finished." }.onFailure { message.value = (it as? com.iptv.firetv.core.common.AppException)?.userMessage ?: "Refresh failed." }
    } }
    fun rename(id: Long, name: String) { viewModelScope.launch { repo.rename(id, name) } }
    fun setEnabled(id: Long, on: Boolean) { viewModelScope.launch { repo.setEnabled(id, on) } }
    fun setRefreshHours(id: Long, h: Int) { viewModelScope.launch { repo.setRefreshHours(id, h) } }
    fun setActive(id: Long) { viewModelScope.launch { repo.setActive(id) } }
    fun move(id: Long, d: Int) { viewModelScope.launch { repo.move(id, d) } }
    fun remove(id: Long, purge: Boolean) { viewModelScope.launch { repo.remove(id, purge) } }
    fun loadHistory(id: Long) { viewModelScope.launch { history.value = repo.history(id) } }
    fun setMerge(on: Boolean) { viewModelScope.launch { settings.set(SettingKeys.MERGE, on) } }
    fun addSample() { viewModelScope.launch { repo.addSample(); message.value = "Sample data added (fictitious demo content)." } }
    fun addLocal(uri: Uri, title: String) { viewModelScope.launch { repo.addLocalMedia(uri, title) } }
    fun finishOnboarding() { viewModelScope.launch { settings.set(SettingKeys.ONBOARDING, true) } }
}
