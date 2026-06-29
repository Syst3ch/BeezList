package com.beezlist.tv

import android.Manifest
import android.app.PictureInPictureParams
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Surface
import com.beezlist.tv.data.Channel
import com.beezlist.tv.ui.channels.ChannelListScreen
import com.beezlist.tv.ui.epg.EpgScreen
import com.beezlist.tv.ui.input.PlaylistInputScreen
import com.beezlist.tv.ui.pairing.QrPairingScreen
import com.beezlist.tv.ui.player.PlayerScreen
import com.beezlist.tv.ui.settings.SettingsScreen
import com.beezlist.tv.ui.theme.BeezListTheme

private object Routes {
    const val INPUT = "input"
    const val QR_PAIRING = "qr_pairing"
    const val CHANNELS = "channels"
    const val SETTINGS = "settings"
    const val EPG = "epg"
    const val PLAYER = "player/{streamUrl}"

    fun player(streamUrl: String) = "player/${Uri.encode(streamUrl)}"
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @Volatile
    private var isPlayerActive = false

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BeezListTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BeezListApp(
                            viewModel = viewModel,
                            onPlayerActiveChanged = { active -> isPlayerActive = active },
                        )
                    }
                }
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlayerActive) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
                // Picture-in-picture support varies across Android TV OEMs/launchers.
            }
        }
    }
}

@Composable
private fun BeezListApp(viewModel: MainViewModel, onPlayerActiveChanged: (Boolean) -> Unit) {
    val navController = rememberNavController()
    val playlistState by viewModel.playlistState.collectAsState()
    val lastUrl by viewModel.lastUrl.collectAsState()
    val playlistUrls by viewModel.playlistUrls.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val epgUrl by viewModel.epgUrl.collectAsState()
    val epgState by viewModel.epgState.collectAsState()
    val epgPrograms by viewModel.epgPrograms.collectAsState()
    val recentlyWatched by viewModel.recentlyWatched.collectAsState()
    val hiddenGroupTitles by viewModel.hiddenGroupTitles.collectAsState()
    val lockedGroupTitles by viewModel.lockedGroupTitles.collectAsState()
    val parentalPin by viewModel.parentalPin.collectAsState()
    val newChannelUrls by viewModel.newChannelUrls.collectAsState()
    val channelOrder by viewModel.channelOrder.collectAsState()
    val watchTimeTotals by viewModel.watchTimeTotals.collectAsState()

    fun visibleChannels(channels: List<Channel>): List<Channel> {
        val visible = channels.filter { it.groupTitle !in hiddenGroupTitles }
        if (channelOrder.isEmpty()) return visible
        val orderIndex = channelOrder.withIndex().associate { (index, url) -> url to index }
        return visible.sortedBy { orderIndex[it.streamUrl] ?: Int.MAX_VALUE }
    }

    NavHost(navController = navController, startDestination = Routes.INPUT) {
        composable(Routes.INPUT) {
            PlaylistInputScreen(
                lastUrl = lastUrl,
                playlistState = playlistState,
                onLoadPlaylist = { url -> viewModel.loadPlaylist(url) },
                onScanQr = { navController.navigate(Routes.QR_PAIRING) },
            )

            LaunchedEffect(playlistState) {
                val state = playlistState
                if (state is PlaylistState.Loaded && state.channels.isNotEmpty()) {
                    navController.navigate(Routes.CHANNELS)
                }
            }
        }
        composable(Routes.QR_PAIRING) {
            QrPairingScreen(
                onUrlReceived = { url ->
                    viewModel.loadPlaylist(url)
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(Routes.CHANNELS) {
            val state = playlistState
            val allChannels: List<Channel> = (state as? PlaylistState.Loaded)?.channels.orEmpty()
            val channels: List<Channel> = visibleChannels(allChannels)
            ChannelListScreen(
                channels = channels,
                favorites = favorites,
                epgPrograms = epgPrograms,
                recentlyWatched = recentlyWatched,
                lockedGroupTitles = lockedGroupTitles,
                parentalPin = parentalPin,
                newChannelUrls = newChannelUrls,
                watchTimeTotals = watchTimeTotals,
                onChannelClick = { channel -> navController.navigate(Routes.player(channel.streamUrl)) },
                onToggleFavorite = { channel -> viewModel.toggleFavorite(channel.streamUrl) },
                onMoveChannel = { a, b ->
                    viewModel.swapChannelOrder(a.streamUrl, b.streamUrl, allChannels.map { it.streamUrl })
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenEpg = { navController.navigate(Routes.EPG) },
            )
        }
        composable(Routes.EPG) {
            val state = playlistState
            val channels: List<Channel> = visibleChannels((state as? PlaylistState.Loaded)?.channels.orEmpty())
            EpgScreen(
                channels = channels,
                epgPrograms = epgPrograms,
                onChannelClick = { channel -> navController.navigate(Routes.player(channel.streamUrl)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val state = playlistState
            val allGroupTitles: List<String> = (state as? PlaylistState.Loaded)?.channels
                .orEmpty()
                .map { it.groupTitle }
                .distinct()
                .sorted()
            SettingsScreen(
                playlistUrls = playlistUrls,
                playlistState = playlistState,
                epgUrl = epgUrl,
                epgState = epgState,
                allGroupTitles = allGroupTitles,
                hiddenGroupTitles = hiddenGroupTitles,
                lockedGroupTitles = lockedGroupTitles,
                parentalPin = parentalPin,
                profiles = profiles,
                activeProfile = activeProfile,
                onAddPlaylist = { url -> viewModel.loadPlaylist(url) },
                onRemovePlaylist = { url -> viewModel.removePlaylistUrl(url) },
                onLoadEpg = { url -> viewModel.loadEpg(url) },
                onToggleGroupHidden = { group, hidden -> viewModel.setGroupHidden(group, hidden) },
                onToggleGroupLocked = { group, locked -> viewModel.setGroupLocked(group, locked) },
                onSetParentalPin = { pin -> viewModel.setParentalPin(pin) },
                onSelectProfile = { name -> viewModel.setActiveProfile(name) },
                onAddProfile = { name -> viewModel.addProfile(name) },
                onRemoveProfile = { name -> viewModel.removeProfile(name) },
                onExportSettings = { onResult -> viewModel.exportSettings(onResult) },
                onImportSettings = { json -> viewModel.importSettings(json) },
                onScanQr = { navController.navigate(Routes.QR_PAIRING) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("streamUrl") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("streamUrl").orEmpty()
            val state = playlistState
            val channels: List<Channel> = visibleChannels((state as? PlaylistState.Loaded)?.channels.orEmpty())

            DisposableEffect(Unit) {
                onPlayerActiveChanged(true)
                onDispose { onPlayerActiveChanged(false) }
            }

            PlayerScreen(
                channels = channels,
                initialStreamUrl = Uri.decode(encodedUrl),
                onWatching = { channel -> viewModel.recordWatched(channel.streamUrl) },
                onWatchTime = { channel, deltaMillis -> viewModel.addWatchTime(channel.streamUrl, deltaMillis) },
            )
        }
    }
}
