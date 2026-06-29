package com.beezlist.tv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.beezlist.tv.notifications.EPG_REMINDER_CHANNEL_ID
import com.beezlist.tv.notifications.EpgReminderWorker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val EPG_REMINDER_WORK_NAME = "epg_reminder"

/**
 * Many IPTV logo CDNs reject requests carrying the default OkHttp/Coil user agent.
 * Providing a browser-like one here fixes channel logos that otherwise silently fail to load.
 */
class BeezListApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        schedulePeriodicEpgReminder()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            EPG_REMINDER_CHANNEL_ID,
            getString(R.string.epg_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun schedulePeriodicEpgReminder() {
        val request = PeriodicWorkRequestBuilder<EpgReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EPG_REMINDER_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", BROWSER_USER_AGENT)
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .build()
}
