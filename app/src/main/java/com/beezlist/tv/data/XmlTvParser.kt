package com.beezlist.tv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale

data class EpgProgram(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val stopMillis: Long,
)

/**
 * Parses XMLTV documents:
 *   <programme start="20240101120000 +0000" stop="20240101130000 +0000" channel="...">
 *     <title>...</title>
 *   </programme>
 */
object XmlTvParser {

    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(reader: Reader): List<EpgProgram> {
        val programs = mutableListOf<EpgProgram>()
        val parser = Xml.newPullParser()
        parser.setInput(reader)

        var inProgramme = false
        var channelId: String? = null
        var start: Long? = null
        var stop: Long? = null
        var title: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        inProgramme = true
                        channelId = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        stop = parseTime(parser.getAttributeValue(null, "stop"))
                        title = null
                    }
                    "title" -> if (inProgramme) {
                        title = parser.nextText()
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "programme") {
                    val cId = channelId
                    val s = start
                    val e = stop
                    val t = title
                    if (cId != null && s != null && e != null && t != null) {
                        programs += EpgProgram(cId, t, s, e)
                    }
                    inProgramme = false
                }
            }
            eventType = parser.next()
        }
        return programs
    }

    private fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            dateFormat.parse(raw)?.time
        } catch (e: Exception) {
            null
        }
    }
}
