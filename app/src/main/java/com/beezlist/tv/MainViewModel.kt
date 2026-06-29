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

    init {
        viewModelScope.launch {
            repository.lastPlaylistUrl.collect { url ->
                _lastUrl.update { url }
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
    }

    fun loadPlaylist(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            _playlistState.update { PlaylistState.Loading }
            try {
                val channels = repository.fetchFromUrl(trimmed)
                repository.saveLastPlaylistUrl(trimmed)
                _playlistState.update { PlaylistState.Loaded(channels) }
                enrichLogos(channels)
            } catch (e: Exception) {
                _playlistState.update { PlaylistState.Error(e.message ?: "Unknown error") }
            }
        }
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

    fun recordWatched(streamUrl: String) {
        viewModelScope.launch {
            repository.recordWatched(streamUrl)
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
