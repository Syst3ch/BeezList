package com.beezlist.tv.notifications

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.beezlist.tv.R
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.PlaylistRepository
import kotlinx.coroutines.flow.first

const val EPG_REMINDER_CHANNEL_ID = "epg_reminders"
private const val REMINDER_WINDOW_MILLIS = 15L * 60 * 1000

class EpgReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = PlaylistRepository(applicationContext)

        val epgUrl = repository.epgUrl.first()
        val favorites = repository.favoriteChannelUrls.first()
        val playlistUrls = repository.playlistUrls.first()
        if (epgUrl.isNullOrBlank() || favorites.isEmpty() || playlistUrls.isEmpty()) {
            return Result.success()
        }

        val channels = LinkedHashMap<String, Channel>()
        for (url in playlistUrls) {
            try {
                repository.fetchFromUrl(url).forEach { channel -> channels.putIfAbsent(channel.streamUrl, channel) }
            } catch (e: Exception) {
                // Tolerate individual playlist failures; other playlists may still yield favorites.
            }
        }

        val favoriteChannels = channels.values.filter { it.streamUrl in favorites && !it.tvgId.isNullOrBlank() }
        if (favoriteChannels.isEmpty()) {
            return Result.success()
        }

        val programs = try {
            repository.fetchEpg(epgUrl)
        } catch (e: Exception) {
            return Result.success()
        }
        val programsByChannel = programs.groupBy { it.channelId }

        val now = System.currentTimeMillis()
        val windowEnd = now + REMINDER_WINDOW_MILLIS
        val alreadyNotified = repository.notifiedProgramKeys.first()
        val newlyNotified = mutableSetOf<String>()

        for (channel in favoriteChannels) {
            val tvgId = channel.tvgId ?: continue
            val upcoming = programsByChannel[tvgId]?.filter { it.startMillis in now..windowEnd } ?: continue
            for (program in upcoming) {
                val key = "${tvgId}_${program.startMillis}"
                if (key in alreadyNotified) continue
                postNotification(key, channel.name, program.title)
                newlyNotified += key
            }
        }

        if (newlyNotified.isNotEmpty()) {
            repository.markProgramsNotified(newlyNotified)
        }

        return Result.success()
    }

    private fun postNotification(key: String, channelName: String, programTitle: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(applicationContext, EPG_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(channelName)
            .setContentText(programTitle)
            .setAutoCancel(true)
            .build()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(key.hashCode(), notification)
    }
}
