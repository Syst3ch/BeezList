package com.beezlist.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
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

private const val LIST_DELIMITER = "\n"
private const val KV_DELIMITER = "\u0001"
private const val DEFAULT_PROFILE = "מועדפים"
private const val RECENTLY_WATCHED_LIMIT = 10
private const val NEW_CHANNELS_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
private const val NOTIFIED_PROGRAM_KEYS_LIMIT = 200

private val LAST_PLAYLIST_URL = stringPreferencesKey("last_playlist_url")
private val PLAYLIST_URLS = stringPreferencesKey("playlist_urls")
private val PROFILE_NAMES = stringPreferencesKey("profile_names")
private val ACTIVE_PROFILE = stringPreferencesKey("active_profile")
private val FAVORITE_CHANNEL_URLS = stringSetPreferencesKey("favorite_channel_urls")
private val HIDDEN_GROUP_TITLES = stringSetPreferencesKey("hidden_group_titles")
private val LOCKED_GROUP_TITLES = stringSetPreferencesKey("locked_group_titles")
private val PARENTAL_PIN = stringPreferencesKey("parental_pin")
private val EPG_URL = stringPreferencesKey("epg_url")
private val RECENTLY_WATCHED = stringPreferencesKey("recently_watched")
private val KNOWN_CHANNEL_URLS = stringSetPreferencesKey("known_channel_urls")
private val NEW_CHANNEL_URLS = stringSetPreferencesKey("new_channel_urls")
private val NEW_CHANNELS_TIMESTAMP = longPreferencesKey("new_channels_timestamp")
private val CHANNEL_ORDER = stringPreferencesKey("channel_order")
private val WATCH_TIME = stringPreferencesKey("watch_time_millis")
private val NOTIFIED_PROGRAM_KEYS = stringSetPreferencesKey("notified_program_keys")

private fun favoritesKey(profile: String) = stringSetPreferencesKey("favorites_$profile")

private fun parseList(raw: String?): List<String> =
    raw?.split(LIST_DELIMITER)?.filter { it.isNotBlank() }.orEmpty()

private fun parseWatchTime(raw: String?): Map<String, Long> =
    parseList(raw).mapNotNull { entry ->
        val parts = entry.split(KV_DELIMITER, limit = 2)
        if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
    }.toMap()

class PlaylistRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Copies data stored under the pre-multi-playlist/profile keys into their successors, once. */
    suspend fun migrateLegacyDataIfNeeded() {
        context.playlistDataStore.edit { prefs ->
            val legacyUrl = prefs[LAST_PLAYLIST_URL]
            if (legacyUrl != null && prefs[PLAYLIST_URLS] == null) {
                prefs[PLAYLIST_URLS] = legacyUrl
            }
            val legacyFavorites = prefs[FAVORITE_CHANNEL_URLS]
            if (legacyFavorites != null && prefs[favoritesKey(DEFAULT_PROFILE)] == null) {
                prefs[favoritesKey(DEFAULT_PROFILE)] = legacyFavorites
            }
        }
    }

    val playlistUrls: Flow<List<String>> =
        context.playlistDataStore.data.map { parseList(it[PLAYLIST_URLS]) }

    suspend fun addPlaylistUrl(url: String) {
        context.playlistDataStore.edit { prefs ->
            val current = parseList(prefs[PLAYLIST_URLS])
            if (url !in current) {
                prefs[PLAYLIST_URLS] = (current + url).joinToString(LIST_DELIMITER)
            }
        }
    }

    suspend fun removePlaylistUrl(url: String) {
        context.playlistDataStore.edit { prefs ->
            prefs[PLAYLIST_URLS] = (parseList(prefs[PLAYLIST_URLS]) - url).joinToString(LIST_DELIMITER)
        }
    }

    val profiles: Flow<List<String>> = context.playlistDataStore.data.map { prefs ->
        parseList(prefs[PROFILE_NAMES]).ifEmpty { listOf(DEFAULT_PROFILE) }
    }

    val activeProfile: Flow<String> =
        context.playlistDataStore.data.map { it[ACTIVE_PROFILE] ?: DEFAULT_PROFILE }

    suspend fun addProfile(name: String) {
        if (name.isBlank()) return
        context.playlistDataStore.edit { prefs ->
            val current = parseList(prefs[PROFILE_NAMES]).ifEmpty { listOf(DEFAULT_PROFILE) }
            if (name !in current) {
                prefs[PROFILE_NAMES] = (current + name).joinToString(LIST_DELIMITER)
            }
        }
    }

    suspend fun removeProfile(name: String) {
        context.playlistDataStore.edit { prefs ->
            val current = parseList(prefs[PROFILE_NAMES]).ifEmpty { listOf(DEFAULT_PROFILE) }
            val remaining = (current - name).ifEmpty { listOf(DEFAULT_PROFILE) }
            prefs[PROFILE_NAMES] = remaining.joinToString(LIST_DELIMITER)
            if (prefs[ACTIVE_PROFILE] == name) {
                prefs[ACTIVE_PROFILE] = remaining.first()
            }
            prefs.remove(favoritesKey(name))
        }
    }

    suspend fun setActiveProfile(name: String) {
        context.playlistDataStore.edit { it[ACTIVE_PROFILE] = name }
    }

    val favoriteChannelUrls: Flow<Set<String>> = context.playlistDataStore.data.map { prefs ->
        val profile = prefs[ACTIVE_PROFILE] ?: DEFAULT_PROFILE
        prefs[favoritesKey(profile)].orEmpty()
    }

    suspend fun toggleFavorite(streamUrl: String) {
        context.playlistDataStore.edit { prefs ->
            val profile = prefs[ACTIVE_PROFILE] ?: DEFAULT_PROFILE
            val key = favoritesKey(profile)
            val current = prefs[key].orEmpty()
            prefs[key] = if (streamUrl in current) current - streamUrl else current + streamUrl
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

    val lockedGroupTitles: Flow<Set<String>> =
        context.playlistDataStore.data.map { it[LOCKED_GROUP_TITLES].orEmpty() }

    suspend fun setGroupLocked(groupTitle: String, locked: Boolean) {
        context.playlistDataStore.edit { prefs ->
            val current = prefs[LOCKED_GROUP_TITLES].orEmpty()
            prefs[LOCKED_GROUP_TITLES] = if (locked) current + groupTitle else current - groupTitle
        }
    }

    val parentalPin: Flow<String?> = context.playlistDataStore.data.map { it[PARENTAL_PIN] }

    suspend fun setParentalPin(pin: String?) {
        context.playlistDataStore.edit { prefs ->
            if (pin.isNullOrBlank()) prefs.remove(PARENTAL_PIN) else prefs[PARENTAL_PIN] = pin
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
        context.playlistDataStore.data.map { parseList(it[RECENTLY_WATCHED]) }

    suspend fun recordWatched(streamUrl: String) {
        context.playlistDataStore.edit { prefs ->
            val current = parseList(prefs[RECENTLY_WATCHED])
            val updated = (listOf(streamUrl) + current.filterNot { it == streamUrl }).take(RECENTLY_WATCHED_LIMIT)
            prefs[RECENTLY_WATCHED] = updated.joinToString(LIST_DELIMITER)
        }
    }

    val newChannelUrls: Flow<Set<String>> = context.playlistDataStore.data.map { prefs ->
        val timestamp = prefs[NEW_CHANNELS_TIMESTAMP] ?: 0L
        if (System.currentTimeMillis() - timestamp > NEW_CHANNELS_TTL_MILLIS) {
            emptySet()
        } else {
            prefs[NEW_CHANNEL_URLS].orEmpty()
        }
    }

    /** Diffs against the previously known channel set so the UI can highlight genuinely new arrivals. */
    suspend fun updateKnownChannels(channels: List<Channel>) {
        val urls = channels.map { it.streamUrl }.toSet()
        context.playlistDataStore.edit { prefs ->
            val known = prefs[KNOWN_CHANNEL_URLS]
            if (known != null) {
                val newOnes = urls - known
                if (newOnes.isNotEmpty()) {
                    prefs[NEW_CHANNEL_URLS] = newOnes
                    prefs[NEW_CHANNELS_TIMESTAMP] = System.currentTimeMillis()
                }
            }
            prefs[KNOWN_CHANNEL_URLS] = urls
        }
    }

    val channelOrder: Flow<List<String>> =
        context.playlistDataStore.data.map { parseList(it[CHANNEL_ORDER]) }

    /** Swaps two channels' relative position in the persisted custom order, used for up/down reordering. */
    suspend fun swapChannelOrder(streamUrlA: String, streamUrlB: String, allKnownStreamUrls: List<String>) {
        context.playlistDataStore.edit { prefs ->
            val order = parseList(prefs[CHANNEL_ORDER]).ifEmpty { allKnownStreamUrls }.toMutableList()
            for (url in allKnownStreamUrls) {
                if (url !in order) order += url
            }
            val indexA = order.indexOf(streamUrlA)
            val indexB = order.indexOf(streamUrlB)
            if (indexA >= 0 && indexB >= 0) {
                val tmp = order[indexA]
                order[indexA] = order[indexB]
                order[indexB] = tmp
            }
            prefs[CHANNEL_ORDER] = order.joinToString(LIST_DELIMITER)
        }
    }

    val watchTimeTotals: Flow<Map<String, Long>> =
        context.playlistDataStore.data.map { parseWatchTime(it[WATCH_TIME]) }

    suspend fun addWatchTime(streamUrl: String, deltaMillis: Long) {
        if (deltaMillis <= 0) return
        context.playlistDataStore.edit { prefs ->
            val current = parseWatchTime(prefs[WATCH_TIME]).toMutableMap()
            current[streamUrl] = (current[streamUrl] ?: 0L) + deltaMillis
            prefs[WATCH_TIME] = current.entries.joinToString(LIST_DELIMITER) { "${it.key}$KV_DELIMITER${it.value}" }
        }
    }

    val notifiedProgramKeys: Flow<Set<String>> =
        context.playlistDataStore.data.map { it[NOTIFIED_PROGRAM_KEYS].orEmpty() }

    suspend fun markProgramsNotified(keys: Set<String>) {
        context.playlistDataStore.edit { prefs ->
            val updated = (prefs[NOTIFIED_PROGRAM_KEYS].orEmpty() + keys)
            prefs[NOTIFIED_PROGRAM_KEYS] = if (updated.size > NOTIFIED_PROGRAM_KEYS_LIMIT) {
                updated.toList().takeLast(NOTIFIED_PROGRAM_KEYS_LIMIT).toSet()
            } else {
                updated
            }
        }
    }

    suspend fun exportSettingsJson(): String = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("playlistUrls", JSONArray(playlistUrls.first()))
        json.put("epgUrl", epgUrl.first().orEmpty())
        json.put("hiddenGroupTitles", JSONArray(hiddenGroupTitles.first().toList()))
        json.put("lockedGroupTitles", JSONArray(lockedGroupTitles.first().toList()))
        json.toString()
    }

    suspend fun importSettingsJson(raw: String) = withContext(Dispatchers.IO) {
        val json = JSONObject(raw)
        json.optJSONArray("playlistUrls")?.let { array ->
            val urls = (0 until array.length()).map { array.getString(it) }
            context.playlistDataStore.edit { prefs -> prefs[PLAYLIST_URLS] = urls.joinToString(LIST_DELIMITER) }
        }
        json.optString("epgUrl").takeIf { it.isNotBlank() }?.let { saveEpgUrl(it) }
        json.optJSONArray("hiddenGroupTitles")?.let { array ->
            val groups = (0 until array.length()).map { array.getString(it) }.toSet()
            context.playlistDataStore.edit { prefs -> prefs[HIDDEN_GROUP_TITLES] = groups }
        }
        json.optJSONArray("lockedGroupTitles")?.let { array ->
            val groups = (0 until array.length()).map { array.getString(it) }.toSet()
            context.playlistDataStore.edit { prefs -> prefs[LOCKED_GROUP_TITLES] = groups }
        }
    }
}
