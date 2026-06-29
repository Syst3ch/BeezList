package com.beezlist.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

private val HEBREW_FALLBACK_CHARSET = Charset.forName("windows-1255")

/**
 * Many Hebrew IPTV panels serve M3U playlists without declaring a charset, encoded in
 * windows-1255 rather than UTF-8. Decoding strictly as UTF-8 first and falling back on
 * failure avoids mojibake channel names without breaking the common UTF-8 case.
 */
private fun decodeText(bytes: ByteArray): String {
    val strictUtf8 = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        strictUtf8.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        String(bytes, HEBREW_FALLBACK_CHARSET)
    }
}

private val Context.playlistDataStore by preferencesDataStore(name = "beezlist_playlist")
private val LAST_PLAYLIST_URL = stringPreferencesKey("last_playlist_url")
private val FAVORITE_CHANNEL_URLS = stringSetPreferencesKey("favorite_channel_urls")
private val HIDDEN_GROUP_TITLES = stringSetPreferencesKey("hidden_group_titles")
private val EPG_URL = stringPreferencesKey("epg_url")
private val RECENTLY_WATCHED = stringPreferencesKey("recently_watched")
private const val RECENTLY_WATCHED_LIMIT = 10
private const val RECENTLY_WATCHED_DELIMITER = "\n"

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

    val favoriteChannelUrls: Flow<Set<String>> =
        context.playlistDataStore.data.map { it[FAVORITE_CHANNEL_URLS].orEmpty() }

    suspend fun toggleFavorite(streamUrl: String) {
        context.playlistDataStore.edit { prefs ->
            val current = prefs[FAVORITE_CHANNEL_URLS].orEmpty()
            prefs[FAVORITE_CHANNEL_URLS] = if (streamUrl in current) {
                current - streamUrl
            } else {
                current + streamUrl
            }
        }
    }

    val hiddenGroupTitles: Flow<Set<String>> =
        context.playlistDataStore.data.map { it[HIDDEN_GROUP_TITLES].orEmpty() }

    suspend fun setGroupHidden(groupTitle: String, hidden: Boolean) {
        context.playlistDataStore.edit { prefs ->
            val current = prefs[HIDDEN_GROUP_TITLES].orEmpty()
            prefs[HIDDEN_GROUP_TITLES] = if (hidden) current + groupTitle else current - groupTitle
        }
    }

    suspend fun fetchFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            M3uParser.parse(decodeText(body.bytes()).reader().buffered())
        }
    }

    val epgUrl: Flow<String?> =
        context.playlistDataStore.data.map { it[EPG_URL] }

    suspend fun saveEpgUrl(url: String) {
        context.playlistDataStore.edit { it[EPG_URL] = url }
    }

    suspend fun fetchEpg(url: String): List<EpgProgram> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Empty response body")
            body.byteStream().use { stream -> XmlTvParser.parse(stream) }
        }
    }

    val recentlyWatchedUrls: Flow<List<String>> =
        context.playlistDataStore.data.map { prefs ->
            prefs[RECENTLY_WATCHED]?.split(RECENTLY_WATCHED_DELIMITER)?.filter { it.isNotBlank() }.orEmpty()
        }

    suspend fun recordWatched(streamUrl: String) {
        context.playlistDataStore.edit { prefs ->
            val current = prefs[RECENTLY_WATCHED]?.split(RECENTLY_WATCHED_DELIMITER)?.filter { it.isNotBlank() }.orEmpty()
            val updated = (listOf(streamUrl) + current.filterNot { it == streamUrl }).take(RECENTLY_WATCHED_LIMIT)
            prefs[RECENTLY_WATCHED] = updated.joinToString(RECENTLY_WATCHED_DELIMITER)
        }
    }
}
