package com.beezlist.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.EpgProgram
import com.beezlist.tv.data.IptvOrgRepository
import com.beezlist.tv.data.PlaylistRepository
import com.beezlist.tv.data.enrichChannelLogos
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistState {
    data object Idle : PlaylistState
    data object Loading : PlaylistState
    data class Loaded(val channels: List<Channel>) : PlaylistState
    data class Error(val message: String) : PlaylistState
}

sealed interface EpgState {
    data object Idle : EpgState
    data object Loading : EpgState
    data object Loaded : EpgState
    data class Error(val message: String) : EpgState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)
    private val iptvOrgRepository = IptvOrgRepository(application)

    private val _playlistState = MutableStateFlow<PlaylistState>(PlaylistState.Idle)
    val playlistState: StateFlow<PlaylistState> = _playlistState

    private val _lastUrl = MutableStateFlow<String?>(null)
    val lastUrl: StateFlow<String?> = _lastUrl

    private val _playlistUrls = MutableStateFlow<List<String>>(emptyList())
    val playlistUrls: StateFlow<List<String>> = _playlistUrls

    private val _profiles = MutableStateFlow<List<String>>(emptyList())
    val profiles: StateFlow<List<String>> = _profiles

    private val _activeProfile = MutableStateFlow("")
    val activeProfile: StateFlow<String> = _activeProfile

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _epgUrl = MutableStateFlow<String?>(null)
    val epgUrl: StateFlow<String?> = _epgUrl

    private val _epgState = MutableStateFlow<EpgState>(EpgState.Idle)
    val epgState: StateFlow<EpgState> = _epgState

    private val _epgPrograms = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epgPrograms: StateFlow<Map<String, List<EpgProgram>>> = _epgPrograms

    private val _recentlyWatched = MutableStateFlow<List<String>>(emptyList())
    val recentlyWatched: StateFlow<List<String>> = _recentlyWatched

    private val _hiddenGroupTitles = MutableStateFlow<Set<String>>(emptySet())
    val hiddenGroupTitles: StateFlow<Set<String>> = _hiddenGroupTitles

    private val _lockedGroupTitles = MutableStateFlow<Set<String>>(emptySet())
    val lockedGroupTitles: StateFlow<Set<String>> = _lockedGroupTitles

    private val _parentalPin = MutableStateFlow<String?>(null)
    val parentalPin: StateFlow<String?> = _parentalPin

    private val _newChannelUrls = MutableStateFlow<Set<String>>(emptySet())
    val newChannelUrls: StateFlow<Set<String>> = _newChannelUrls

    private val _channelOrder = MutableStateFlow<List<String>>(emptyList())
    val channelOrder: StateFlow<List<String>> = _channelOrder

    private val _watchTimeTotals = MutableStateFlow<Map<String, Long>>(emptyMap())
    val watchTimeTotals: StateFlow<Map<String, Long>> = _watchTimeTotals

    init {
        viewModelScope.launch {
            repository.migrateLegacyDataIfNeeded()
            refetchAll()
        }
        viewModelScope.launch {
            repository.playlistUrls.collect { urls ->
                _playlistUrls.update { urls }
                _lastUrl.update { urls.lastOrNull() }
            }
        }
        viewModelScope.launch {
            repository.profiles.collect { names ->
                _profiles.update { names }
            }
        }
        viewModelScope.launch {
            repository.activeProfile.collect { name ->
                _activeProfile.update { name }
            }
        }
        viewModelScope.launch {
            repository.favoriteChannelUrls.collect { urls ->
                _favorites.update { urls }
            }
        }
        viewModelScope.launch {
            repository.epgUrl.collect { url ->
                _epgUrl.update { url }
            }
        }
        viewModelScope.launch {
            val savedEpgUrl = repository.epgUrl.first()
            if (!savedEpgUrl.isNullOrBlank()) {
                fetchAndApplyEpg(savedEpgUrl)
            }
        }
        viewModelScope.launch {
            repository.recentlyWatchedUrls.collect { urls ->
                _recentlyWatched.update { urls }
            }
        }
        viewModelScope.launch {
            repository.hiddenGroupTitles.collect { groups ->
                _hiddenGroupTitles.update { groups }
            }
        }
        viewModelScope.launch {
            repository.lockedGroupTitles.collect { groups ->
                _lockedGroupTitles.update { groups }
            }
        }
        viewModelScope.launch {
            repository.parentalPin.collect { pin ->
                _parentalPin.update { pin }
            }
        }
        viewModelScope.launch {
            repository.newChannelUrls.collect { urls ->
                _newChannelUrls.update { urls }
            }
        }
        viewModelScope.launch {
            repository.channelOrder.collect { order ->
                _channelOrder.update { order }
            }
        }
        viewModelScope.launch {
            repository.watchTimeTotals.collect { totals ->
                _watchTimeTotals.update { totals }
            }
        }
    }

    fun loadPlaylist(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.addPlaylistUrl(trimmed)
            refetchAll()
        }
    }

    fun removePlaylistUrl(url: String) {
        viewModelScope.launch {
            repository.removePlaylistUrl(url)
            refetchAll()
        }
    }

    private suspend fun refetchAll() {
        val urls = repository.playlistUrls.first()
        if (urls.isEmpty()) {
            _playlistState.update { PlaylistState.Idle }
            return
        }

        _playlistState.update { PlaylistState.Loading }
        val merged = LinkedHashMap<String, Channel>()
        var lastError: String? = null
        for (url in urls) {
            try {
                val channels = repository.fetchFromUrl(url)
                for (channel in channels) {
                    merged.putIfAbsent(channel.streamUrl, channel)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
            }
        }

        if (merged.isEmpty()) {
            _playlistState.update { PlaylistState.Error(lastError ?: "Unknown error") }
            return
        }

        val channels = merged.values.toList()
        repository.updateKnownChannels(channels)
        _playlistState.update { PlaylistState.Loaded(channels) }
        enrichLogos(channels)
    }

    private fun enrichLogos(originalChannels: List<Channel>) {
        viewModelScope.launch {
            val index = iptvOrgRepository.loadIndex() ?: return@launch
            val enriched = enrichChannelLogos(originalChannels, index)
            val current = _playlistState.value
            if (current is PlaylistState.Loaded && current.channels === originalChannels) {
                _playlistState.update { PlaylistState.Loaded(enriched) }
            }
        }
    }

    fun reset() {
        _playlistState.update { PlaylistState.Idle }
    }

    fun toggleFavorite(streamUrl: String) {
        viewModelScope.launch {
            repository.toggleFavorite(streamUrl)
        }
    }

    fun addProfile(name: String) {
        viewModelScope.launch {
            repository.addProfile(name)
        }
    }

    fun removeProfile(name: String) {
        viewModelScope.launch {
            repository.removeProfile(name)
        }
    }

    fun setActiveProfile(name: String) {
        viewModelScope.launch {
            repository.setActiveProfile(name)
        }
    }

    fun loadEpg(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            repository.saveEpgUrl(trimmed)
            fetchAndApplyEpg(trimmed)
        }
    }

    fun setGroupHidden(groupTitle: String, hidden: Boolean) {
        viewModelScope.launch {
            repository.setGroupHidden(groupTitle, hidden)
        }
    }

    fun setGroupLocked(groupTitle: String, locked: Boolean) {
        viewModelScope.launch {
            repository.setGroupLocked(groupTitle, locked)
        }
    }

    fun setParentalPin(pin: String?) {
        viewModelScope.launch {
            repository.setParentalPin(pin)
        }
    }

    fun recordWatched(streamUrl: String) {
        viewModelScope.launch {
            repository.recordWatched(streamUrl)
        }
    }

    fun swapChannelOrder(streamUrlA: String, streamUrlB: String, allKnownStreamUrls: List<String>) {
        viewModelScope.launch {
            repository.swapChannelOrder(streamUrlA, streamUrlB, allKnownStreamUrls)
        }
    }

    fun addWatchTime(streamUrl: String, deltaMillis: Long) {
        viewModelScope.launch {
            repository.addWatchTime(streamUrl, deltaMillis)
        }
    }

    fun exportSettings(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repository.exportSettingsJson())
        }
    }

    fun importSettings(json: String) {
        viewModelScope.launch {
            repository.importSettingsJson(json)
            refetchAll()
        }
    }

    private suspend fun fetchAndApplyEpg(url: String) {
        _epgState.update { EpgState.Loading }
        try {
            val programs = repository.fetchEpg(url)
            _epgPrograms.update { programs.groupBy { it.channelId } }
            _epgState.update { EpgState.Loaded }
        } catch (e: Exception) {
            _epgState.update { EpgState.Error(e.message ?: "Unknown error") }
        }
    }
}
