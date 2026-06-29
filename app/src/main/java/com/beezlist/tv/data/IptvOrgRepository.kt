package com.beezlist.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

private const val CHANNELS_URL = "https://raw.githubusercontent.com/iptv-org/api/gh-pages/channels.json"
private const val LOGOS_URL = "https://raw.githubusercontent.com/iptv-org/api/gh-pages/logos.json"
private const val CACHE_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
private val PREFERRED_LOGO_FORMATS = setOf("PNG", "JPEG", "JPG", "WEBP")

private fun normalize(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/**
 * Logo lookup built from the iptv-org/api public channel database (raw.githubusercontent.com
 * mirror of its gh-pages JSON, since iptv-org.github.io itself isn't reachable from every network).
 * Matches by tvg-id first, falling back to a normalized channel-name match.
 */
class IptvOrgLogoIndex(
    private val idToLogo: Map<String, String>,
    private val idToLogoLower: Map<String, String>,
    private val nameToId: Map<String, String>,
) {
    fun lookup(tvgId: String?, name: String): String? {
        if (!tvgId.isNullOrBlank()) {
            idToLogo[tvgId]?.let { return it }
            idToLogoLower[tvgId.lowercase()]?.let { return it }
        }
        val id = nameToId[normalize(name)] ?: return null
        return idToLogo[id]
    }
}

class IptvOrgRepository(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedIndex: IptvOrgLogoIndex? = null

    suspend fun loadIndex(): IptvOrgLogoIndex? = withContext(Dispatchers.IO) {
        cachedIndex?.let { return@withContext it }
        try {
            val channelsJson = readCachedOrFetch("iptv_org_channels.json", CHANNELS_URL)
            val logosJson = readCachedOrFetch("iptv_org_logos.json", LOGOS_URL)
            buildIndex(channelsJson, logosJson).also { cachedIndex = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun readCachedOrFetch(fileName: String, url: String): String {
        val file = File(context.cacheDir, fileName)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < CACHE_TTL_MILLIS) {
            return file.readText()
        }
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (file.exists()) return file.readText()
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("Empty response body")
            file.writeText(body)
            return body
        }
    }

    private fun buildIndex(channelsJson: String, logosJson: String): IptvOrgLogoIndex {
        val nameToId = HashMap<String, String>()
        val channelsArray = JSONArray(channelsJson)
        for (i in 0 until channelsArray.length()) {
            val entry = channelsArray.getJSONObject(i)
            val id = entry.getString("id")
            nameToId[normalize(entry.getString("name"))] = id
            entry.optJSONArray("alt_names")?.let { altNames ->
                for (j in 0 until altNames.length()) {
                    nameToId[normalize(altNames.getString(j))] = id
                }
            }
        }

        val idToLogo = HashMap<String, String>()
        val logosArray = JSONArray(logosJson)
        for (i in 0 until logosArray.length()) {
            val entry = logosArray.getJSONObject(i)
            if (entry.isNull("channel")) continue
            val channelId = entry.getString("channel")
            if (idToLogo.containsKey(channelId)) continue
            if (!entry.optBoolean("in_use", true)) continue
            val format = entry.optString("format", "").uppercase()
            if (format !in PREFERRED_LOGO_FORMATS) continue
            idToLogo[channelId] = entry.getString("url")
        }
        val idToLogoLower = idToLogo.entries.associate { it.key.lowercase() to it.value }

        return IptvOrgLogoIndex(idToLogo, idToLogoLower, nameToId)
    }
}

fun enrichChannelLogos(channels: List<Channel>, index: IptvOrgLogoIndex): List<Channel> =
    channels.map { channel ->
        if (!channel.logoUrl.isNullOrBlank()) {
            channel
        } else {
            val logo = index.lookup(channel.tvgId, channel.name)
            if (logo != null) channel.copy(logoUrl = logo) else channel
        }
    }
