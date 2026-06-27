package com.beezlist.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PlaylistState {
    data object Idle : PlaylistState
    data object Loading : PlaylistState
    data class Loaded(val channels: List<Channel>) : PlaylistState
    data class Error(val message: String) : PlaylistState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository(application)

    private val _playlistState = MutableStateFlow<PlaylistState>(PlaylistState.Idle)
    val playlistState: StateFlow<PlaylistState> = _playlistState

    private val _lastUrl = MutableStateFlow<String?>(null)
    val lastUrl: StateFlow<String?> = _lastUrl

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

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
            } catch (e: Exception) {
                _playlistState.update { PlaylistState.Error(e.message ?: "Unknown error") }
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
}
