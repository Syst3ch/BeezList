package com.beezlist.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * Many IPTV logo CDNs reject requests carrying the default OkHttp/Coil user agent.
 * Providing a browser-like one here fixes channel logos that otherwise silently fail to load.
 */
class BeezListApplication : Application(), ImageLoaderFactory {

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
