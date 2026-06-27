package com.beezlist.tv.data

import java.io.BufferedReader

/**
 * Parses standard M3U/M3U8 IPTV playlists:
 *   #EXTM3U
 *   #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Channel Name
 *   http://stream-url
 */
object M3uParser {

    private val attrRegex = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    fun parse(reader: BufferedReader): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaIndex = line.indexOf(',')
                    val attrPart = if (commaIndex >= 0) line.substring(0, commaIndex) else line
                    pendingName = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else null

                    val attrs = attrRegex.findAll(attrPart).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    pendingLogo = attrs["tvg-logo"]
                    pendingGroup = attrs["group-title"]
                    pendingTvgId = attrs["tvg-id"]
                }
                line.startsWith("#") -> {
                    // Other directives (#EXTM3U, #EXTGRP, #EXTVLCOPT, ...) are ignored.
                }
                else -> {
                    val name = pendingName?.takeIf { it.isNotBlank() } ?: line
                    channels += Channel(
                        name = name,
                        streamUrl = line,
                        logoUrl = pendingLogo,
                        groupTitle = pendingGroup ?: "",
                        tvgId = pendingTvgId,
                    )
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                    pendingTvgId = null
                }
            }
        }

        return channels
    }
}
