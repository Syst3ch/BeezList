package com.beezlist.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.beezlist.tv.PlaylistState
import com.beezlist.tv.R

private val ErrorRed = Color(0xFFFF6B6B)

@Composable
fun SettingsScreen(
    currentUrl: String?,
    playlistState: PlaylistState,
    onLoadPlaylist: (String) -> Unit,
    onScanQr: () -> Unit,
    onBack: () -> Unit,
) {
    var url by remember(currentUrl) { mutableStateOf(currentUrl.orEmpty()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { M3Text(stringResource(R.string.playlist_url_label)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.width(560.dp),
            )

            Button(onClick = { onLoadPlaylist(url) }) {
                Text(stringResource(R.string.load_playlist))
            }

            Button(onClick = onScanQr) {
                Text(stringResource(R.string.scan_qr_button))
            }

            Button(onClick = onBack) {
                Text(stringResource(R.string.settings_back))
            }

            when (playlistState) {
                is PlaylistState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.loading_playlist))
                    }
                }
                is PlaylistState.Error -> {
                    Text(
                        text = stringResource(R.string.error_loading_playlist),
                        color = ErrorRed,
                    )
                }
                else -> Unit
            }
        }
    }
}
