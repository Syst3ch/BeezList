package com.beezlist.tv.ui.player

import android.view.LayoutInflater
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text as M3Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.beezlist.tv.R
import com.beezlist.tv.data.Channel
import com.beezlist.tv.data.EpgProgram
import kotlinx.coroutines.delay

private val TopGradient = Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
private val BottomGradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
private const val CONTROLS_TIMEOUT_MS = 4_000L

private enum class AspectMode(val resizeMode: Int, val labelRes: Int) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, R.string.player_aspect_fit),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, R.string.player_aspect_fill),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, R.string.player_aspect_zoom),
}

@Composable
fun PlayerScreen(
    channels: List<Channel>,
    initialStreamUrl: String,
    epgPrograms: Map<String, List<EpgProgram>> = emptyMap(),
    favorites: Set<String> = emptySet(),
    onWatching: (Channel) -> Unit = {},
    onWatchTime: (Channel, Long) -> Unit = { _, _ -> },
    onToggleFavorite: (Channel) -> Unit = {},
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var currentIndex by remember {
        mutableStateOf(channels.indexOfFirst { it.streamUrl == initialStreamUrl }.coerceAtLeast(0))
    }
    val currentChannel = channels.getOrNull(currentIndex)
    val streamUrl = currentChannel?.streamUrl ?: initialStreamUrl

    // Watch-time tracking (carried over)
    LaunchedEffect(streamUrl) { currentChannel?.let(onWatching) }
    DisposableEffect(streamUrl) {
        val watched = currentChannel
        val start = System.currentTimeMillis()
        onDispose { if (watched != null) onWatchTime(watched, System.currentTimeMillis() - start) }
    }

    // Per-stream state (auto-reset on channel change via remember key)
    var hasError by remember(streamUrl) { mutableStateOf(false) }
    var isBuffering by remember(streamUrl) { mutableStateOf(true) }
    var playerTracks by remember(streamUrl) { mutableStateOf<Tracks?>(null) }
    var retryKey by remember(streamUrl) { mutableStateOf(0) }

    // Persistent overlay state
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var controlsResetKey by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsResetKey) {
        controlsVisible = true
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    val exoPlayer = remember(streamUrl, retryKey) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { hasError = true }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onTracksChanged(tracks: Tracks) { playerTracks = tracks }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Derived data
    val audioGroups = playerTracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO } ?: emptyList()
    val subtitleGroups = playerTracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT } ?: emptyList()

    val now = System.currentTimeMillis()
    val currentProgram = currentChannel?.tvgId?.let { id ->
        epgPrograms[id]?.firstOrNull { now in it.startMillis until it.stopMillis }
    }
    val programProgress = currentProgram?.let { prog ->
        ((now - prog.startMillis).toFloat() / (prog.stopMillis - prog.startMillis).toFloat())
            .coerceIn(0f, 1f)
    }

    val isFavorite = currentChannel?.streamUrl in favorites

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                controlsResetKey++
                when (event.key) {
                    Key.DirectionUp -> {
                        if (channels.isNotEmpty()) currentIndex = (currentIndex - 1 + channels.size) % channels.size
                        true
                    }
                    Key.DirectionDown -> {
                        if (channels.isNotEmpty()) currentIndex = (currentIndex + 1) % channels.size
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Video surface
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).apply {
                    useController = false
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = aspectMode.resizeMode
            },
        )

        // Buffering spinner
        if (isBuffering && !hasError) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Error state with retry
        if (hasError) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_error),
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { retryKey++; hasError = false; isBuffering = true }) {
                    M3Text(stringResource(R.string.player_retry))
                }
            }
        }

        // Controls HUD
        AnimatedVisibility(
            visible = controlsVisible && !hasError,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top info bar
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(TopGradient)
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (!currentChannel?.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = currentChannel!!.logoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (currentChannel != null) {
                                Text(
                                    text = currentChannel.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                )
                            }
                            if (currentProgram != null) {
                                Text(
                                    text = currentProgram.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        M3Text(
                            text = "● ${stringResource(R.string.epg_live)}",
                            color = Color(0xFFFF4444),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // Bottom controls bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(BottomGradient)
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // EPG progress bar
                    if (currentProgram != null && programProgress != null) {
                        LinearProgressIndicator(
                            progress = { programProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Audio track picker
                        Button(
                            onClick = { controlsResetKey++; showAudioPicker = true },
                            enabled = audioGroups.isNotEmpty(),
                        ) {
                            M3Text(
                                stringResource(R.string.player_audio_track) +
                                    if (audioGroups.size > 1) " (${audioGroups.size})" else "",
                            )
                        }

                        // Subtitle picker
                        Button(
                            onClick = { controlsResetKey++; showSubtitlePicker = true },
                            enabled = subtitleGroups.isNotEmpty(),
                        ) {
                            M3Text(
                                stringResource(R.string.player_subtitles) +
                                    if (subtitleGroups.isNotEmpty()) " (${subtitleGroups.size})" else "",
                            )
                        }

                        // Aspect ratio cycle
                        Button(onClick = {
                            controlsResetKey++
                            val modes = AspectMode.values()
                            aspectMode = modes[(modes.indexOf(aspectMode) + 1) % modes.size]
                        }) {
                            M3Text(stringResource(aspectMode.labelRes))
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Favorite toggle
                        Button(
                            onClick = { currentChannel?.let(onToggleFavorite); controlsResetKey++ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            M3Text(
                                text = if (isFavorite) "★" else "☆",
                                color = if (isFavorite) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // Track pickers
        if (showAudioPicker) {
            TrackPickerDialog(
                title = stringResource(R.string.player_audio_track),
                groups = audioGroups,
                trackType = C.TRACK_TYPE_AUDIO,
                player = exoPlayer,
                onDismiss = { showAudioPicker = false },
            )
        }
        if (showSubtitlePicker) {
            TrackPickerDialog(
                title = stringResource(R.string.player_subtitles),
                groups = subtitleGroups,
                trackType = C.TRACK_TYPE_TEXT,
                player = exoPlayer,
                onDismiss = { showSubtitlePicker = false },
            )
        }
    }
}

@Composable
private fun TrackPickerDialog(
    title: String,
    groups: List<Tracks.Group>,
    trackType: Int,
    player: ExoPlayer,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { M3Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(trackType)
                            .build()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    M3Text(stringResource(R.string.player_track_auto))
                }
                groups.forEachIndexed { index, group ->
                    val format = group.getTrackFormat(0)
                    val label = format.language?.uppercase()
                        ?: format.label
                        ?: "$title ${index + 1}"
                    Button(
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(trackType)
                                .addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        M3Text(label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { M3Text(stringResource(R.string.parental_pin_cancel)) }
        },
    )
}
