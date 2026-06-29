package com.beezlist.tv.ui.player

import android.view.LayoutInflater
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beezlist.tv.R
import com.beezlist.tv.data.Channel

@Composable
fun PlayerScreen(channels: List<Channel>, initialStreamUrl: String) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var currentIndex by remember {
        mutableStateOf(channels.indexOfFirst { it.streamUrl == initialStreamUrl }.coerceAtLeast(0))
    }
    val currentChannel = channels.getOrNull(currentIndex)
    val streamUrl = currentChannel?.streamUrl ?: initialStreamUrl

    var hasError by remember(streamUrl) { mutableStateOf(false) }

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown || channels.isEmpty()) {
                    false
                } else {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            currentIndex = (currentIndex - 1 + channels.size) % channels.size
                            true
                        }
                        Key.DirectionDown -> {
                            currentIndex = (currentIndex + 1) % channels.size
                            true
                        }
                        else -> false
                    }
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (LayoutInflater.from(context).inflate(R.layout.player_view, null) as PlayerView).apply {
                    player = exoPlayer
                }
            },
            update = { playerView -> playerView.player = exoPlayer },
        )

        if (currentChannel != null) {
            Text(
                text = currentChannel.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp),
            )
        }

        if (hasError) {
            Text(
                text = stringResource(R.string.player_error),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
