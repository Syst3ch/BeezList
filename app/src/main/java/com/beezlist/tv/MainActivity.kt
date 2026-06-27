package com.beezlist.tv

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.Surface
import com.beezlist.tv.data.Channel
import com.beezlist.tv.ui.channels.ChannelListScreen
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
    const val PLAYER = "player/{streamUrl}"

    fun player(streamUrl: String) = "player/${Uri.encode(streamUrl)}"
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeezListTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BeezListApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun BeezListApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val playlistState by viewModel.playlistState.collectAsState()
    val lastUrl by viewModel.lastUrl.collectAsState()

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
            val channels: List<Channel> = (state as? PlaylistState.Loaded)?.channels.orEmpty()
            ChannelListScreen(
                channels = channels,
                onChannelClick = { channel -> navController.navigate(Routes.player(channel.streamUrl)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                currentUrl = lastUrl,
                playlistState = playlistState,
                onLoadPlaylist = { url -> viewModel.loadPlaylist(url) },
                onScanQr = { navController.navigate(Routes.QR_PAIRING) },
                onBack = { navController.popBackStack() },
            )

            LaunchedEffect(playlistState) {
                val state = playlistState
                if (state is PlaylistState.Loaded && state.channels.isNotEmpty()) {
                    navController.popBackStack(Routes.CHANNELS, inclusive = false)
                }
            }
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("streamUrl") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("streamUrl").orEmpty()
            PlayerScreen(streamUrl = Uri.decode(encodedUrl))
        }
    }
}
