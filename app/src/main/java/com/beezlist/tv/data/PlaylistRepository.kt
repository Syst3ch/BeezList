package com.beezlist.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

private val Context.playlistDataStore by preferencesDataStore(name = "beezlist_playlist")
private val LAST_PLAYLIST_URL = stringPreferencesKey("last_playlist_url")

class PlaylistRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val lastPlaylistUrl: Flow<String?> =
        context.playlistDataStore.data.map { it[LAST_PLAYLIST_URL] }

    suspend fun saveLastPlaylistUrl(url: String) {
        context.playlistDataStore.edit { it[LAST_PLAYLIST_URL] = url }
    }

    suspend fun fetchFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            body.byteStream().bufferedReader().use { reader: BufferedReader ->
                M3uParser.parse(reader)
            }
        }
    }
}
